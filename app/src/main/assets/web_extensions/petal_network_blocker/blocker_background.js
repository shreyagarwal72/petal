// Petal Network Blocker - Background Service Worker / Script
// High-performance network subresource interception using WebExtension webRequest API.

(function () {
    "use strict";

    const NATIVE_APP = "petalApp";
    let isBlockerEnabled = true;
    let whitelistedHosts = new Set();
    let port = null;

    // High-impact ad, tracker, and telemetry host suffixes
    const BLOCKED_HOST_SUFFIXES = [
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "rubiconproject.com",
        "pubmatic.com",
        "smartadserver.com",
        "amazon-adsystem.com",
        "scorecardresearch.com",
        "propellerads.com",
        "exoclick.com",
        "monetag.com",
        "hilltopads.com",
        "adcash.com",
        "adsterra.com",
        "a-ads.com",
        "mgid.com",
        "revcontent.com",
        "trafficjunky.com",
        "coinhive.com",
        "statcounter.com",
        "hotjar.com",
        "yandex.ru/metrika",
        "popads.net",
        "popcash.net",
        "adform.net",
        "chartbeat.com",
        "quantserve.com",
        "moatads.com",
        "advertising.com",
        "casalemedia.com",
        "openx.net",
        "bidswitch.net",
        "adroll.com",
        "adsrvr.org",
        "zedo.com",
        "innovid.com",
        "serving-sys.com",
        "adtechus.com",
        "2mdn.net",
        "imrworldwide.com",
        "scorecardresearch.com"
    ];

    // Common URL path patterns for ad servers and trackers.
    // Keep these specific: generic file names such as "/popup.js" or "analytics.js" are used by
    // ordinary sites for their own UI code and blocking them breaks pages.
    const BLOCKED_PATH_PATTERNS = [
        "/pagead/",
        "/ad_banner",
        "/popunder",
        "adsbygoogle.js",
        "/api/stats/ads",
        "/pagead/interaction/",
        "/ptracking",
        "adlog.php",
        "/ad-delivery/",
        "/advertisement/",
        "facebook.com/tr/",
        "googletagmanager.com/gtag/js"
    ];

    function normalizeHost(host) {
        if (!host) return "";
        return host.toLowerCase().replace(/^\.+|\.+$/g, "").replace(/^www\./, "");
    }

    function isHostBlocked(host) {
        if (!host) return false;
        const norm = normalizeHost(host);
        for (let i = 0; i < BLOCKED_HOST_SUFFIXES.length; i++) {
            const suffix = BLOCKED_HOST_SUFFIXES[i];
            if (norm === suffix || norm.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    function isUrlBlocked(url) {
        if (!url) return false;
        for (let i = 0; i < BLOCKED_PATH_PATTERNS.length; i++) {
            if (url.includes(BLOCKED_PATH_PATTERNS[i])) {
                return true;
            }
        }
        return false;
    }

    function connectNativeApp() {
        try {
            const api = (typeof browser !== "undefined") ? browser : chrome;
            if (api && api.runtime && typeof api.runtime.sendNativeMessage === "function") {
                api.runtime.sendNativeMessage(NATIVE_APP, { type: "GET_BLOCKER_CONFIG" }, function (response) {
                    if (response) {
                        if (typeof response.enabled === "boolean") {
                            isBlockerEnabled = response.enabled;
                        }
                        if (Array.isArray(response.whitelist)) {
                            whitelistedHosts = new Set(response.whitelist.map(normalizeHost));
                        }
                    }
                });
            }
        } catch (e) {
            console.warn("Failed to query native blocker config:", e);
        }
    }

    // Connect initially
    connectNativeApp();

    // Listen for messages from native app or popup
    const api = (typeof browser !== "undefined") ? browser : chrome;
    if (api && api.runtime && api.runtime.onMessage) {
        api.runtime.onMessage.addListener(function (message, sender, sendResponse) {
            if (message && message.type === "UPDATE_BLOCKER_CONFIG") {
                if (typeof message.enabled === "boolean") {
                    isBlockerEnabled = message.enabled;
                }
                if (Array.isArray(message.whitelist)) {
                    whitelistedHosts = new Set(message.whitelist.map(normalizeHost));
                }
                sendResponse({ success: true });
            }
        });
    }

    // Intercept subresource web requests
    if (api && api.webRequest && api.webRequest.onBeforeRequest) {
        api.webRequest.onBeforeRequest.addListener(
            function (details) {
                if (!isBlockerEnabled) {
                    return { cancel: false };
                }

                // Never cancel the main top-level frame navigation
                if (details.type === "main_frame") {
                    return { cancel: false };
                }

                const url = details.url;
                if (!url || url.startsWith("about:") || url.startsWith("data:") || url.startsWith("moz-extension:") || url.startsWith("chrome:")) {
                    return { cancel: false };
                }

                let reqHost = "";
                try {
                    reqHost = new URL(url).hostname;
                } catch (_) {
                    return { cancel: false };
                }

                // Check doc host / page host for site exception whitelist
                const docUrl = details.documentUrl || details.originUrl || "";
                if (docUrl) {
                    try {
                        const docHost = normalizeHost(new URL(docUrl).hostname);
                        if (whitelistedHosts.has(docHost)) {
                            return { cancel: false };
                        }
                    } catch (_) {}
                }

                // Check if request host or path matches blocklist
                if (isHostBlocked(reqHost) || isUrlBlocked(url)) {
                    // Report block to native Petal app
                    try {
                        api.runtime.sendNativeMessage(NATIVE_APP, {
                            type: "RECORD_BLOCK",
                            url: url,
                            pageUrl: docUrl,
                            reqType: details.type,
                            tabId: details.tabId
                        });
                    } catch (_) {}

                    return { cancel: true };
                }

                return { cancel: false };
            },
            { urls: ["<all_urls>"] },
            ["blocking"]
        );
    }
})();
