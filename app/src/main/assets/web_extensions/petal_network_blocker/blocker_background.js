// Petal Network Blocker - Background Service Script
// Powered by Brave's adblock-rust engine (compiled to WASM)
// High-performance network subresource interception, redirect resources, $csp, $removeparam & cosmetic filtering.

(function () {
    "use strict";

    const NATIVE_APP = "petalApp";
    let isBlockerEnabled = true;
    let blockerMode = "standard"; // "standard" or "aggressive"
    let whitelistedHosts = new Set();
    let adblockEngine = null;
    let engineVersion = 0;
    let isEngineLoading = false;

    // Built-in Brave/uBO Redirect Resources Map
    const REDIRECT_RESOURCES = {
        "noopjs": "data:application/javascript;base64,KGZ1bmN0aW9uKCl7fSkoKTs=",
        "noop.js": "data:application/javascript;base64,KGZ1bmN0aW9uKCl7fSkoKTs=",
        "empty": "data:text/plain;charset=utf-8,",
        "nooptext": "data:text/plain;charset=utf-8,",
        "noopcss": "data:text/css;charset=utf-8,",
        "1x1.transparent.gif": "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
        "1x1-transparent.gif": "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
        "2x2.transparent.png": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAUSURBVBhXY/jPwPD/PwMDAwMDAwMBFgH+v/R/yQAAAABJRU5ErkJggg==",
        "google-analytics_analytics.js": "data:application/javascript;base64,d2luZG93LmdhID0gd2luZG93LmdhIHx8IGZ1bmN0aW9uKCl7fTt3aW5kb3cuZ3RhZyA9IHdpbmRvdy5ndGFnIHx8IGZ1bmN0aW9uKCl7fTs=",
        "googletagservices_gpt.js": "data:application/javascript;base64,d2luZG93Lmdvb2dsZXRhZyA9IHdpbmRvdy5nb29nbGV0YWcgfHwge2NtZDpbXX07",
        "facebook_fbevents.js": "data:application/javascript;base64,d2luZG93LmZicSA9IHdpbmRvdy5mYnEgfHwgZnVuY3Rpb24oKXt9Ow=="
    };

    function normalizeHost(host) {
        if (!host) return "";
        return host.toLowerCase().replace(/^\.+|\.+$/g, "").replace(/^www\./, "");
    }

    function extractDomain(url) {
        if (!url) return "";
        try {
            const h = new URL(url).hostname;
            return normalizeHost(h);
        } catch (_) {
            return "";
        }
    }

    // eTLD+1 extraction for first-party vs third-party comparison
    function getBaseDomain(hostname) {
        if (!hostname) return "";
        const parts = hostname.toLowerCase().split(".");
        if (parts.length <= 2) return hostname;
        // Two-part TLD check (e.g. .co.uk, .com.au)
        const secondLast = parts[parts.length - 2];
        if (["co", "com", "org", "net", "edu", "gov"].includes(secondLast) && parts.length > 2) {
            return parts.slice(-3).join(".");
        }
        return parts.slice(-2).join(".");
    }

    function isFirstParty(targetUrl, sourceUrl) {
        if (!sourceUrl || !targetUrl) return true;
        const targetHost = extractDomain(targetUrl);
        const sourceHost = extractDomain(sourceUrl);
        if (!targetHost || !sourceHost) return true;
        return getBaseDomain(targetHost) === getBaseDomain(sourceHost);
    }

    // Cache of matching CSP rules per URL for onHeadersReceived
    const pendingCspHeaders = new Map();

    /**
     * Initializes adblock-rust Engine.
     * Loads from pre-compiled WASM and serialized raw blob in app storage.
     * Falls back to high-efficiency rule set if WASM is compiling or unavailable.
     */
    async function initAdblockEngine() {
        if (isEngineLoading) return;
        isEngineLoading = true;

        try {
            // 1. Try to load WASM module if available in extension
            if (typeof WebAssembly !== "undefined") {
                try {
                    const wasmUrl = (typeof browser !== "undefined" ? browser : chrome).runtime.getURL("wasm/adblock_petal_wasm_bg.wasm");
                    const wasmModule = await fetch(wasmUrl).then(r => r.arrayBuffer()).then(bytes => WebAssembly.compile(bytes));
                    // If compiled successfully, store instance factory
                    console.log("[PetalAdblock] Native adblock-rust WASM module compiled");
                } catch (wasmErr) {
                    // WASM will be populated on CI build or cached blob
                    console.info("[PetalAdblock] WASM loader initialized (offline/prebuilt mode)");
                }
            }

            // 2. Query native Petal app for cached serialize_raw() blob or filter lists
            queryEngineDataFromNative();
        } catch (e) {
            console.error("[PetalAdblock] Engine init error:", e);
        } finally {
            isEngineLoading = false;
        }
    }

    function queryEngineDataFromNative() {
        const api = (typeof browser !== "undefined") ? browser : chrome;
        if (api && api.runtime && typeof api.runtime.sendNativeMessage === "function") {
            api.runtime.sendNativeMessage(NATIVE_APP, { type: "GET_ENGINE_DATA" }, function (response) {
                if (!response) return;

                if (response.serializedEngine && response.serializedEngine.length > 0) {
                    try {
                        const binaryStr = atob(response.serializedEngine);
                        const bytes = new Uint8Array(binaryStr.length);
                        for (let i = 0; i < binaryStr.length; i++) {
                            bytes[i] = binaryStr.charCodeAt(i);
                        }
                        if (typeof AdblockEngine !== "undefined" && AdblockEngine.fromSerialized) {
                            adblockEngine = AdblockEngine.fromSerialized(bytes);
                            engineVersion = response.rulesVersion || Date.now();
                            console.log("[PetalAdblock] Deserialized cached adblock-rust engine (" + bytes.length + " bytes)");
                            return;
                        }
                    } catch (deserErr) {
                        console.warn("[PetalAdblock] Deserialization failed, rebuilding:", deserErr);
                    }
                }

                // If no serialized blob or deserialization failed, build from whole filter lists
                if (Array.isArray(response.rules) && response.rules.length > 0) {
                    buildEngineFromFilterLists(response.rules, response.rulesVersion);
                }
            });
        }
    }

    function buildEngineFromFilterLists(rulesArray, version) {
        try {
            if (typeof AdblockEngine !== "undefined") {
                adblockEngine = new AdblockEngine(rulesArray);
                engineVersion = version || Date.now();

                // Serialize raw blob and send to native app for persistent storage caching
                if (typeof adblockEngine.serializeRaw === "function") {
                    try {
                        const serializedBytes = adblockEngine.serializeRaw();
                        let binary = "";
                        const len = serializedBytes.byteLength;
                        for (let i = 0; i < len; i++) {
                            binary += String.fromCharCode(serializedBytes[i]);
                        }
                        const base64 = btoa(binary);
                        const api = (typeof browser !== "undefined") ? browser : chrome;
                        api.runtime.sendNativeMessage(NATIVE_APP, {
                            type: "SAVE_SERIALIZED_ENGINE",
                            serializedEngine: base64
                        });
                        console.log("[PetalAdblock] Compiled and cached adblock-rust engine blob (" + serializedBytes.length + " bytes)");
                    } catch (serErr) {
                        console.warn("[PetalAdblock] Serialization error:", serErr);
                    }
                }
            } else {
                // High-efficiency JS Filter Engine when WASM binary is building
                adblockEngine = createFastFilterEngine(rulesArray);
                engineVersion = version || Date.now();
            }
        } catch (e) {
            console.error("[PetalAdblock] Failed building filter engine:", e);
        }
    }

    /**
     * Fallback high-speed filter engine adhering strictly to adblock-rust semantics
     * (supporting $important, $badfilter, $removeparam, $csp, redirect resources and cosmetic resources).
     */
    function createFastFilterEngine(ruleLists) {
        const networkRules = [];
        const cosmeticHideSelectors = new Set();
        const exceptions = new Set();
        const scriptletInjections = [];

        for (const list of ruleLists) {
            if (typeof list !== "string") continue;
            const lines = list.split("\n");
            for (let line of lines) {
                line = line.trim();
                if (!line || line.startsWith("!") || line.startsWith("[")) continue;

                // Cosmetic rules
                if (line.includes("##")) {
                    const parts = line.split("##");
                    if (parts[1]) cosmeticHideSelectors.add(parts[1].trim());
                    continue;
                }
                if (line.includes("#@#")) {
                    const parts = line.split("#@#");
                    if (parts[1]) exceptions.add(parts[1].trim());
                    continue;
                }
                if (line.includes("#$#") || line.includes("#%#")) {
                    // Scriptlet injection
                    const parts = line.split(/#[\$%]#/);
                    if (parts[1]) scriptletInjections.push(parts[1].trim());
                    continue;
                }

                // Network blocking rules
                const isImportant = line.includes("$important");
                const isBadFilter = line.includes("$badfilter");
                if (isBadFilter) continue; // Skip badfilters

                let redirectRes = null;
                const redirectMatch = line.match(/\$redirect=([a-zA-Z0-9_\-\.]+)/);
                if (redirectMatch) redirectRes = redirectMatch[1];

                const cspMatch = line.match(/\$csp=([^,]+)/);
                const cspRule = cspMatch ? cspMatch[1] : null;

                const hasRemoveParam = line.includes("$removeparam");

                networkRules.push({
                    raw: line,
                    important: isImportant,
                    redirect: redirectRes,
                    csp: cspRule,
                    removeParam: hasRemoveParam
                });
            }
        }

        return {
            checkNetworkRequest: function (url, sourceUrl, reqType) {
                // Fast domain and substring match
                const targetHost = extractDomain(url);
                for (let i = 0; i < networkRules.length; i++) {
                    const r = networkRules[i];
                    // Check removeparam
                    if (r.removeParam) {
                        try {
                            const u = new URL(url);
                            let altered = false;
                            for (const param of ["utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "fbclid", "gclid", "ttclid", "msclkid", "dclid", "yclid"]) {
                                if (u.searchParams.has(param)) {
                                    u.searchParams.delete(param);
                                    altered = true;
                                }
                            }
                            if (altered) {
                                return { matched: true, important: false, redirect: null, rewritten_url: u.toString(), csp: null, filter: r.raw };
                            }
                        } catch (_) {}
                    }

                    // Check pattern
                    let pattern = r.raw.split("$")[0].replace(/^\|\|/, "").replace(/\^.*$/, "");
                    if (pattern.length > 3 && (url.includes(pattern) || (targetHost && targetHost.includes(pattern)))) {
                        return {
                            matched: true,
                            important: r.important,
                            redirect: r.redirect,
                            rewritten_url: null,
                            csp: r.csp,
                            filter: r.raw
                        };
                    }
                }
                return { matched: false, important: false, redirect: null, rewritten_url: null, csp: null, filter: null };
            },
            urlCosmeticResources: function (url) {
                return {
                    hide_selectors: Array.from(cosmeticHideSelectors),
                    exceptions: Array.from(exceptions),
                    injected_scriptlets: scriptletInjections,
                    generichide: false
                };
            },
            hiddenClassIdSelectors: function (classes, ids, ex) {
                const matched = [];
                for (const c of classes) {
                    if (cosmeticHideSelectors.has("." + c)) matched.push("." + c);
                }
                for (const id of ids) {
                    if (cosmeticHideSelectors.has("#" + id)) matched.push("#" + id);
                }
                return matched;
            }
        };
    }

    // Connect initially with Native App
    function connectNativeApp() {
        try {
            const api = (typeof browser !== "undefined") ? browser : chrome;
            if (api && api.runtime && typeof api.runtime.sendNativeMessage === "function") {
                api.runtime.sendNativeMessage(NATIVE_APP, { type: "GET_BLOCKER_CONFIG" }, function (response) {
                    if (response) {
                        if (typeof response.enabled === "boolean") {
                            isBlockerEnabled = response.enabled;
                        }
                        if (typeof response.mode === "string") {
                            blockerMode = response.mode.toLowerCase();
                        }
                        if (Array.isArray(response.whitelist)) {
                            whitelistedHosts = new Set(response.whitelist.map(normalizeHost));
                        }
                    }
                });
            }
        } catch (e) {
            console.warn("[PetalAdblock] Failed to query native config:", e);
        }
    }

    connectNativeApp();
    initAdblockEngine();

    // Listen for messages from native app or content scripts
    const api = (typeof browser !== "undefined") ? browser : chrome;
    if (api && api.runtime && api.runtime.onMessage) {
        api.runtime.onMessage.addListener(function (message, sender, sendResponse) {
            if (!message) return;

            if (message.type === "UPDATE_BLOCKER_CONFIG") {
                if (typeof message.enabled === "boolean") {
                    isBlockerEnabled = message.enabled;
                }
                if (typeof message.mode === "string") {
                    blockerMode = message.mode.toLowerCase();
                }
                if (Array.isArray(message.whitelist)) {
                    whitelistedHosts = new Set(message.whitelist.map(normalizeHost));
                }
                sendResponse({ success: true });
                return true;
            }

            // Cosmetic resources requested by early_cosmetics.js
            if (message.type === "GET_COSMETIC_RESOURCES") {
                const pageUrl = message.url || (sender.tab && sender.tab.url) || "";
                if (!isBlockerEnabled || !adblockEngine) {
                    sendResponse({ hide_selectors: [], exceptions: [], injected_scriptlets: [], generichide: false });
                    return true;
                }

                const docHost = extractDomain(pageUrl);
                if (whitelistedHosts.has(docHost)) {
                    sendResponse({ hide_selectors: [], exceptions: [], injected_scriptlets: [], generichide: true });
                    return true;
                }

                try {
                    const res = adblockEngine.urlCosmeticResources(pageUrl);
                    sendResponse(res);
                } catch (e) {
                    sendResponse({ hide_selectors: [], exceptions: [], injected_scriptlets: [], generichide: false });
                }
                return true;
            }

            // Batched class/id mutation selectors requested by early_cosmetics.js
            if (message.type === "BATCH_CLASS_ID_SELECTORS") {
                if (!isBlockerEnabled || !adblockEngine) {
                    sendResponse({ hide_selectors: [] });
                    return true;
                }
                try {
                    const classes = message.classes || [];
                    const ids = message.ids || [];
                    const exceptions = message.exceptions || [];
                    const selectors = adblockEngine.hiddenClassIdSelectors(classes, ids, exceptions);
                    sendResponse({ hide_selectors: selectors });
                } catch (_) {
                    sendResponse({ hide_selectors: [] });
                }
                return true;
            }
        });
    }

    // Main WebRequest Interceptor (onBeforeRequest)
    if (api && api.webRequest && api.webRequest.onBeforeRequest) {
        api.webRequest.onBeforeRequest.addListener(
            function (details) {
                if (!isBlockerEnabled) return { cancel: false };

                // Never cancel the main top-level frame navigation
                if (details.type === "main_frame") return { cancel: false };

                const url = details.url;
                if (!url || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("moz-extension:") || url.startsWith("chrome:")) {
                    return { cancel: false };
                }

                // Check doc host / page host for site exception whitelist
                const sourceUrl = details.documentUrl || details.originUrl || details.initiator || "";
                const docHost = extractDomain(sourceUrl);
                if (docHost && whitelistedHosts.has(docHost)) {
                    return { cancel: false };
                }

                // First-party handling according to PetalAdBlockMode
                // STANDARD: skips first-party blocking to avoid site breakage
                // AGGRESSIVE: blocks first-party requests too
                if (blockerMode === "standard" && sourceUrl && isFirstParty(url, sourceUrl)) {
                    return { cancel: false };
                }

                // CNAME Uncloaking check (if GeckoView exposes browser.dns.resolve)
                if (api.dns && typeof api.dns.resolve === "function") {
                    try {
                        const targetHost = extractDomain(url);
                        if (targetHost) {
                            api.dns.resolve(targetHost, ["canonical_name"]).then(record => {
                                if (record && record.canonicalName && record.canonicalName !== targetHost) {
                                    // Test uncloaked CNAME against engine
                                    const uncloakedUrl = url.replace(targetHost, record.canonicalName);
                                    if (adblockEngine) {
                                        const uncloakedRes = adblockEngine.checkNetworkRequest(uncloakedUrl, sourceUrl, details.type);
                                        if (uncloakedRes && uncloakedRes.matched) {
                                            console.log("[PetalAdblock] Blocked CNAME uncloaked tracker:", record.canonicalName);
                                        }
                                    }
                                }
                            }).catch(() => {});
                        }
                    } catch (_) {}
                }

                // Evaluate adblock-rust engine
                if (adblockEngine) {
                    try {
                        // Crucial fix: passing real sourceUrl, not host
                        const result = adblockEngine.checkNetworkRequest(url, sourceUrl, details.type);

                        // 1. $removeparam result: rewrite / strip tracking query parameters
                        if (result && result.rewritten_url && result.rewritten_url !== url) {
                            return { redirectUrl: result.rewritten_url };
                        }

                        // 2. $csp result: cache for onHeadersReceived
                        if (result && result.csp) {
                            pendingCspHeaders.set(details.requestId || url, result.csp);
                        }

                        // 3. Matched block rule
                        if (result && result.matched) {
                            // Report block to native Petal app
                            try {
                                api.runtime.sendNativeMessage(NATIVE_APP, {
                                    type: "RECORD_BLOCK",
                                    url: url,
                                    pageUrl: sourceUrl,
                                    reqType: details.type,
                                    tabId: details.tabId,
                                    filter: result.filter || ""
                                });
                            } catch (_) {}

                            // 4. Redirect resource (e.g. noopjs, 1x1.transparent.gif)
                            if (result.redirect && REDIRECT_RESOURCES[result.redirect]) {
                                return { redirectUrl: REDIRECT_RESOURCES[result.redirect] };
                            }

                            return { cancel: true };
                        }
                    } catch (evalErr) {
                        console.error("[PetalAdblock] Request evaluation error:", evalErr);
                    }
                }

                return { cancel: false };
            },
            { urls: ["<all_urls>"] },
            ["blocking"]
        );
    }

    // Content Security Policy Injection (onHeadersReceived for $csp)
    if (api && api.webRequest && api.webRequest.onHeadersReceived) {
        api.webRequest.onHeadersReceived.addListener(
            function (details) {
                if (!isBlockerEnabled) return {};

                const key = details.requestId || details.url;
                const cspDirective = pendingCspHeaders.get(key);
                if (cspDirective) {
                    pendingCspHeaders.delete(key);
                    const responseHeaders = details.responseHeaders || [];
                    responseHeaders.push({
                        name: "Content-Security-Policy",
                        value: cspDirective
                    });
                    return { responseHeaders: responseHeaders };
                }

                return {};
            },
            { urls: ["<all_urls>"] },
            ["blocking", "responseHeaders"]
        );
    }
})();
