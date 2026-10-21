/**
 * Petal Scriptlet Defuser
 * Brave-style anti-adblock scriptlets injected before page scripts run.
 * Neutralizes: nowoif, prevent-bab, abort-on-property-write, set-local-storage-item patterns.
 */
(function () {
    'use strict';

    // 1. nowoif — abort window.open if used for pop-unders (common trick)
    try {
        const origOpen = window.open;
        window.open = function (url, name, features) {
            if (!url || url === 'about:blank' || url.startsWith('javascript:')) return null;
            if (name === '_blank' && !features) return null; // pop-under
            return origOpen.apply(this, arguments);
        };
    } catch (e) {}

    // 2. prevent-bab — defuse BlockAdBlock and similar detection libraries
    try {
        // Stub out common detection names before the page scripts define them
        const babNames = [
            'BlockAdBlock', 'FuckAdBlock', 'sniffAdBlock',
            'detectAdBlock', 'adBlockDetected', 'checkAdBlock',
            'adblockDetector', 'AdBlockDetector', 'AdBlocker'
        ];
        babNames.forEach(function (name) {
            if (!(name in window)) {
                Object.defineProperty(window, name, {
                    get: function () { return undefined; },
                    set: function () {},
                    configurable: true
                });
            }
        });
    } catch (e) {}

    // 3. abort-on-property-write — stop scripts that detect adblock via property probes
    try {
        ['canRunAds', 'isAdEnabled', 'ads_enabled', 'adsbygoogle_status'].forEach(function (prop) {
            Object.defineProperty(window, prop, {
                get: function () { return true; },
                set: function () {},
                configurable: true
            });
        });
    } catch (e) {}

    // 4. set-local-storage-item — fake adblock-consent localStorage tokens
    try {
        const fakeTokens = {
            'abb-detected': '0',
            'adblock-consent': 'true',
            'adblock-accepted': 'true',
            'adblock_seen': '1',
            'adblock_dismissed': 'true',
            'disable-adblock-msg': 'true',
            'paywall-bypass': '1'
        };
        const origGetItem = Storage.prototype.getItem;
        Storage.prototype.getItem = function (key) {
            if (fakeTokens.hasOwnProperty(key)) return fakeTokens[key];
            return origGetItem.apply(this, arguments);
        };
    } catch (e) {}

    // 5. Ephemeral localStorage for cross-origin iframes — prevent localStorage tracking
    try {
        if (window.self !== window.top) {
            // We are in an iframe — wrap localStorage with ephemeral in-memory proxy
            const ephemeralStore = {};
            const epStorage = {
                getItem: function (k) { return ephemeralStore[k] !== undefined ? ephemeralStore[k] : null; },
                setItem: function (k, v) { ephemeralStore[k] = String(v); },
                removeItem: function (k) { delete ephemeralStore[k]; },
                clear: function () { Object.keys(ephemeralStore).forEach(function (k) { delete ephemeralStore[k]; }); },
                get length() { return Object.keys(ephemeralStore).length; },
                key: function (i) { return Object.keys(ephemeralStore)[i] || null; }
            };
            try {
                Object.defineProperty(window, 'localStorage', {
                    get: function () { return epStorage; },
                    configurable: true
                });
                Object.defineProperty(window, 'sessionStorage', {
                    get: function () { return epStorage; },
                    configurable: true
                });
            } catch (e) {}
        }
    } catch (e) {}

})();
