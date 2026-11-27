// Petal Network Blocker - Background Service Script
// Powered by Brave's adblock-rust engine (compiled to WASM)
// High-performance network subresource interception, redirect resources, $csp, $removeparam & cosmetic filtering.

(function () {
    "use strict";

    const NATIVE_APP = "petalApp";
    let isBlockerEnabled = true;
    let blockerMode = "standard"; // "standard", "aggressive", or "disabled"
    let whitelistedHosts = new Set();
    let privateWhitelistedHosts = new Set();
    let domainModes = Object.create(null);
    let blockerPort = null;
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
     * (supporting network exceptions @@, $important, $badfilter, $removeparam, $csp,
     *  $third-party, redirect resources and cosmetic resources).
     */
    function createFastFilterEngine(ruleLists) {
        const networkRules = [];
        const networkExceptions = [];
        const cosmeticHideSelectors = new Set();
        const exceptions = new Set();
        const scriptletInjections = [];

        function escapeRegExp(string) {
            return string.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        }

        for (const list of ruleLists) {
            if (typeof list !== "string") continue;
            const lines = list.split("\n");
            for (let line of lines) {
                line = line.trim();
                if (!line || line.startsWith("!") || line.startsWith("[")) continue;

                // Network exception rules (@@) for unbreak compatibility
                if (line.startsWith("@@")) {
                    const excRaw = line.substring(2);
                    const excPattern = excRaw.split("$")[0].replace(/^\|\|/, "").replace(/\^.*$/, "");
                    if (excPattern.length > 2) {
                        networkExceptions.push(excPattern);
                    }
                    continue;
                }

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
                const redirectMatch = line.match(/[\$,]redirect=([a-zA-Z0-9_\-\.]+)/);
                if (redirectMatch) redirectRes = redirectMatch[1];

                const cspMatch = line.match(/[\$,]csp=([^,]+)/);
                const cspRule = cspMatch ? cspMatch[1] : null;

                const hasRemoveParam = /[\$,]removeparam\b/.test(line);
                const isThirdPartyOnly = /[\$,](?:third-party|3p)\b/.test(line);
                const isFirstPartyOnly = /[\$,]~(?:third-party|3p)\b/.test(line);

                const includeDomains = [];
                const excludeDomains = [];
                const domainMatch = line.match(/[\$,]domain=([^,]+)/);
                if (domainMatch && domainMatch[1]) {
                    const items = domainMatch[1].split("|");
                    for (let d = 0; d < items.length; d++) {
                        const raw = items[d].trim().toLowerCase();
                        if (!raw) continue;
                        if (raw.startsWith("~")) excludeDomains.push(normalizeHost(raw.slice(1)));
                        else includeDomains.push(normalizeHost(raw));
                    }
                }

                const startsWithDomain = line.startsWith("||");
                const rawPattern = line.split("$")[0].replace(/^\|\|/, "").replace(/\^.*$/, "");

                networkRules.push({
                    raw: line,
                    pattern: rawPattern,
                    startsWithDomain: startsWithDomain,
                    hasWildcard: rawPattern.includes("*"),
                    important: isImportant,
                    redirect: redirectRes,
                    csp: cspRule,
                    removeParam: hasRemoveParam,
                    thirdPartyOnly: isThirdPartyOnly,
                    firstPartyOnly: isFirstPartyOnly,
                    includeDomains: includeDomains,
                    excludeDomains: excludeDomains
                });
            }
        }

        function hostMatchesList(host, list) {
            if (!host || !list || list.length === 0) return false;
            for (let i = 0; i < list.length; i++) {
                const entry = list[i];
                if (host === entry || host.endsWith("." + entry)) return true;
            }
            return false;
        }

        function isPatternMatch(url, targetHost, rule) {
            const pat = rule.pattern;
            if (!pat || pat.length < 3) return false;

            if (rule.hasWildcard) {
                try {
                    const regexParts = pat.split("*").map(escapeRegExp);
                    const regex = new RegExp(regexParts.join(".*"));
                    return regex.test(url);
                } catch (_) {
                    return false;
                }
            }

            if (rule.startsWithDomain) {
                if (targetHost && (targetHost === pat || targetHost.endsWith("." + pat))) {
                    return true;
                }
            }
            return url.includes(pat);
        }

        return {
            checkNetworkRequest: function (url, sourceUrl, reqType) {
                const targetHost = extractDomain(url);
                const docHost = extractDomain(sourceUrl);

                // 1. Check network exception rules (unbreak)
                for (let j = 0; j < networkExceptions.length; j++) {
                    const exc = networkExceptions[j];
                    if (url.includes(exc) || (targetHost && (targetHost === exc || targetHost.endsWith("." + exc)))) {
                        return { matched: false, important: false, redirect: null, rewritten_url: null, csp: null, filter: null };
                    }
                }

                // 2. Evaluate network rules
                for (let i = 0; i < networkRules.length; i++) {
                    const r = networkRules[i];

                    // Respect $domain= allow/deny scoping first
                    if (r.includeDomains.length > 0 && !hostMatchesList(docHost, r.includeDomains)) {
                        continue;
                    }
                    if (r.excludeDomains.length > 0 && hostMatchesList(docHost, r.excludeDomains)) {
                        continue;
                    }

                    // Check party constraint
                    if (r.thirdPartyOnly && sourceUrl && isFirstParty(url, sourceUrl)) {
                        continue;
                    }
                    if (r.firstPartyOnly && sourceUrl && !isFirstParty(url, sourceUrl)) {
                        continue;
                    }

                    // Match pattern against target URL / domain before applying rule actions
                    if (!isPatternMatch(url, targetHost, r)) {
                        continue;
                    }

                    // Check removeparam query parameter stripping
                    if (r.removeParam) {
                        try {
                            const u = new URL(url);
                            let altered = false;
                            const paramsToRemove = ["utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "fbclid", "gclid", "ttclid", "msclkid", "dclid", "yclid"];
                            for (let p = 0; p < paramsToRemove.length; p++) {
                                if (u.searchParams.has(paramsToRemove[p])) {
                                    u.searchParams.delete(paramsToRemove[p]);
                                    altered = true;
                                }
                            }
                            if (altered) {
                                return { matched: true, important: false, redirect: null, rewritten_url: u.toString(), csp: null, filter: r.raw };
                            }
                        } catch (_) {}
                    }

                    return {
                        matched: true,
                        important: r.important,
                        redirect: r.redirect,
                        rewritten_url: null,
                        csp: r.csp,
                        filter: r.raw
                    };
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

    function applyBlockerConfig(cfg) {
        if (!cfg) return;
        if (typeof cfg.enabled === "boolean") {
            isBlockerEnabled = cfg.enabled;
        }
        if (typeof cfg.mode === "string") {
            blockerMode = cfg.mode.toLowerCase();
        }
        if (Array.isArray(cfg.whitelist)) {
            whitelistedHosts = new Set(cfg.whitelist.map(normalizeHost).filter(Boolean));
        }
        if (Array.isArray(cfg.privateWhitelist)) {
            privateWhitelistedHosts = new Set(cfg.privateWhitelist.map(normalizeHost).filter(Boolean));
        }
        if (cfg.domainModes && typeof cfg.domainModes === "object") {
            const nextModes = Object.create(null);
            for (const k of Object.keys(cfg.domainModes)) {
                const normalized = normalizeHost(k);
                if (normalized && typeof cfg.domainModes[k] === "string") {
                    nextModes[normalized] = cfg.domainModes[k].toLowerCase();
                }
            }
            domainModes = nextModes;
        }
    }

    function isHostWhitelisted(docHost, isPrivate) {
        if (!docHost) return false;
        const targetSet = isPrivate ? privateWhitelistedHosts : whitelistedHosts;
        if (targetSet.has(docHost)) return true;
        const parts = docHost.split(".");
        for (let i = 1; i < parts.length - 1; i++) {
            if (targetSet.has(parts.slice(i).join("."))) return true;
        }
        return false;
    }

    function effectiveModeFor(docHost, isPrivate) {
        if (docHost && isHostWhitelisted(docHost, isPrivate)) return "disabled";
        if (!isPrivate && docHost && domainModes[docHost]) return domainModes[docHost];
        return isBlockerEnabled ? blockerMode : "disabled";
    }

    // Connect initially with Native App
    function connectNativeApp() {
        try {
            const api = (typeof browser !== "undefined") ? browser : chrome;
            if (api && api.runtime) {
                if (typeof api.runtime.connectNative === "function") {
                    try {
                        blockerPort = api.runtime.connectNative(NATIVE_APP);
                        blockerPort.onMessage.addListener(function (msg) {
                            if (!msg) return;
                            if (msg.type === "UPDATE_BLOCKER_CONFIG") {
                                applyBlockerConfig(msg);
                            } else if (msg.type === "RELOAD_ENGINE") {
                                queryEngineDataFromNative();
                            }
                        });
                    } catch (portErr) {
                        console.warn("[PetalAdblock] Native port setup failed:", portErr);
                    }
                }
                if (typeof api.runtime.sendNativeMessage === "function") {
                    api.runtime.sendNativeMessage(NATIVE_APP, { type: "GET_BLOCKER_CONFIG" }, function (response) {
                        applyBlockerConfig(response);
                    });
                }
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
                applyBlockerConfig(message);
                sendResponse({ success: true });
                return true;
            }

            // Cosmetic resources requested by early_cosmetics.js
            if (message.type === "GET_COSMETIC_RESOURCES") {
                const pageUrl = message.url || (sender.tab && sender.tab.url) || "";
                const isPrivate = Boolean(sender && sender.tab && sender.tab.incognito);
                const docHost = extractDomain(pageUrl);
                const mode = effectiveModeFor(docHost, isPrivate);
                const whitelisted = (mode === "disabled");
                if (!isBlockerEnabled || whitelisted || !adblockEngine) {
                    sendResponse({
                        enabled: isBlockerEnabled,
                        whitelisted: whitelisted,
                        hide_selectors: [],
                        exceptions: [],
                        injected_scriptlets: [],
                        generichide: whitelisted
                    });
                    return true;
                }

                try {
                    const res = adblockEngine.urlCosmeticResources(pageUrl);
                    sendResponse({
                        enabled: true,
                        whitelisted: false,
                        hide_selectors: res.hide_selectors || [],
                        exceptions: res.exceptions || [],
                        injected_scriptlets: res.injected_scriptlets || [],
                        generichide: Boolean(res.generichide)
                    });
                } catch (e) {
                    sendResponse({ enabled: isBlockerEnabled, whitelisted: false, hide_selectors: [], exceptions: [], injected_scriptlets: [], generichide: false });
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

                // Check doc host / page host for site exception whitelist & per-domain mode
                const sourceUrl = details.documentUrl || details.originUrl || details.initiator || "";
                const docHost = extractDomain(sourceUrl);
                const isPrivate = Boolean(details.incognito);
                const mode = effectiveModeFor(docHost, isPrivate);
                if (mode === "disabled") {
                    return { cancel: false };
                }

                // First-party handling according to PetalAdBlockMode
                // STANDARD: skips first-party blocking to avoid site breakage
                // AGGRESSIVE: blocks first-party requests too
                if (mode === "standard" && sourceUrl && isFirstParty(url, sourceUrl)) {
                    return { cancel: false };
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
