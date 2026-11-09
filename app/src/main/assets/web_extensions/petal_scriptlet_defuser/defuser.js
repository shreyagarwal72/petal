/**
 * Petal Scriptlet Defuser & Web Protection Content Script
 * Injected at document_start before page scripts execute.
 *
 * Capabilities:
 * 1. Anti-Adblock Defusers (Brave-style): nowoif, prevent-bab, abort-on-property-write, fake consent tokens.
 * 2. Cross-origin ephemeral localStorage isolation for embedded iframes.
 * 3. Native Port Bridge: Evaluates JavaScript in page context for Petal Browser with real results.
 * 4. Document-Start Privacy Protections: DNT/GPC headers, safe WebRTC wrapper, noise injection.
 * 5. Layout & Usability Overrides: Forced page zoom, forced vertical scroll, and autoplay defense.
 */
(function () {
    'use strict';

    const rawWin = typeof window.wrappedJSObject !== 'undefined' ? window.wrappedJSObject : window;
    const isTopFrame = (window.self === window.top);
    const host = (location.hostname || '').toLowerCase();

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Native Messaging Port Bridge (Real JavaScript Evaluation)
    // ─────────────────────────────────────────────────────────────────────────
    let appPort = null;
    try {
        const api = (typeof browser !== 'undefined' ? browser : (typeof chrome !== 'undefined' ? chrome : null));
        if (api && api.runtime && typeof api.runtime.connectNative === 'function') {
            appPort = api.runtime.connectNative("petalApp");
            appPort.onMessage.addListener(function (msg) {
                if (!msg || msg.type !== "EVAL_JS") return;
                const reqId = msg.id;
                const script = msg.script;
                let evalResult = null;
                let evalError = null;

                try {
                    // Evaluate in the real page context
                    const res = rawWin.eval(script);
                    if (res !== undefined) {
                        evalResult = (typeof res === 'object' && res !== null) ? JSON.stringify(res) : String(res);
                    }
                } catch (err) {
                    evalError = err ? (err.message || String(err)) : "Evaluation error";
                }

                try {
                    appPort.postMessage({
                        type: "EVAL_RESULT",
                        id: reqId,
                        result: evalResult,
                        error: evalError
                    });
                } catch (_) {}
            });

            // Live logging hook: forward console.error, console.warn, and uncaught exceptions to appPort
            try {
                const origError = rawWin.console && rawWin.console.error;
                if (typeof origError === 'function') {
                    rawWin.console.error = function (...args) {
                        origError.apply(rawWin.console, args);
                        try {
                            if (appPort) {
                                const msg = args.map(a => typeof a === 'object' && a !== null ? (a.message || JSON.stringify(a)) : String(a)).join(' ');
                                appPort.postMessage({ type: "LIVE_LOG", level: "ERROR", message: msg, url: location.href });
                            }
                        } catch (_) {}
                    };
                }
                rawWin.addEventListener('error', function (e) {
                    try {
                        if (appPort && e) {
                            const errText = (e.message || String(e)) + (e.filename ? " (" + e.filename + ":" + e.lineno + ")" : "");
                            appPort.postMessage({ type: "LIVE_LOG", level: "ERROR", message: "Uncaught error: " + errText, url: location.href });
                        }
                    } catch (_) {}
                });
                rawWin.addEventListener('unhandledrejection', function (e) {
                    try {
                        if (appPort && e) {
                            const reason = e.reason ? (e.reason.message || String(e.reason)) : "unknown";
                            appPort.postMessage({ type: "LIVE_LOG", level: "WARN", message: "Unhandled promise rejection: " + reason, url: location.href });
                        }
                    } catch (_) {}
                });
            } catch (_) {}
        }
    } catch (_) {}

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Anti-Adblock & Defusers (Brave-Style)
    // ─────────────────────────────────────────────────────────────────────────
    // 2.1 nowoif — prevent window.open abuse for pop-unders
    try {
        const origOpen = rawWin.open;
        if (typeof origOpen === 'function') {
            rawWin.open = function (url, name, features) {
                if (!url || url === 'about:blank' || url.startsWith('javascript:')) return null;
                if (name === '_blank' && !features) return null;
                return origOpen.apply(this, arguments);
            };
        }
    } catch (_) {}

    // 2.2 prevent-bab — defuse BlockAdBlock and similar detection libraries
    try {
        const babNames = [
            'BlockAdBlock', 'FuckAdBlock', 'sniffAdBlock',
            'detectAdBlock', 'adBlockDetected', 'checkAdBlock',
            'adblockDetector', 'AdBlockDetector', 'AdBlocker'
        ];
        babNames.forEach(function (name) {
            if (!(name in rawWin)) {
                Object.defineProperty(rawWin, name, {
                    get: function () { return undefined; },
                    set: function () {},
                    configurable: true
                });
            }
        });
    } catch (_) {}

    // 2.3 abort-on-property-write — neutralize adblock detection property probes
    try {
        ['canRunAds', 'isAdEnabled', 'ads_enabled', 'adsbygoogle_status'].forEach(function (prop) {
            Object.defineProperty(rawWin, prop, {
                get: function () { return true; },
                set: function () {},
                configurable: true
            });
        });
    } catch (_) {}

    // 2.4 set-local-storage-item — fake adblock-consent tokens
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
            if (Object.prototype.hasOwnProperty.call(fakeTokens, key)) return fakeTokens[key];
            return origGetItem.apply(this, arguments);
        };
    } catch (_) {}

    // 2.5 Ephemeral localStorage for cross-origin iframes
    try {
        if (!isTopFrame) {
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
                Object.defineProperty(rawWin, 'localStorage', {
                    get: function () { return epStorage; },
                    configurable: true
                });
                Object.defineProperty(rawWin, 'sessionStorage', {
                    get: function () { return epStorage; },
                    configurable: true
                });
            } catch (_) {}
        }
    } catch (_) {}

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Document-Start Privacy Protections (DNT, WebRTC, Canvas Noise)
    // ─────────────────────────────────────────────────────────────────────────

    // 3.1 DNT & GPC (Global Privacy Control)
    try {
        const nav = rawWin.navigator;
        if (nav) {
            Object.defineProperty(nav, 'doNotTrack', { get: () => '1', configurable: true });
            Object.defineProperty(nav, 'globalPrivacyControl', { get: () => true, configurable: true });
        }
        Object.defineProperty(rawWin, 'doNotTrack', { get: () => '1', configurable: true });
    } catch (_) {}

    // 3.2 Safe WebRTC wrapper: preserves static methods & supports callback form
    try {
        if (rawWin.RTCPeerConnection) {
            const OrigRTCPeerConnection = rawWin.RTCPeerConnection;
            const SafeRTCPeerConnection = function (config, constraints) {
                if (config && config.iceServers) {
                    config.iceCandidatePoolSize = 0;
                }
                const pc = new OrigRTCPeerConnection(config, constraints);

                // Safe createOffer supporting both legacy callback and Promise
                const origCreateOffer = pc.createOffer.bind(pc);
                pc.createOffer = function () {
                    if (typeof arguments[0] === 'function') {
                        const successCb = arguments[0];
                        const failureCb = arguments[1];
                        const options = arguments[2];
                        return origCreateOffer(options).then(function (offer) {
                            if (offer && offer.sdp) {
                                offer.sdp = offer.sdp.replace(/c=IN IP4 .+\r\n/g, 'c=IN IP4 0.0.0.0\r\n');
                            }
                            successCb(offer);
                        }).catch(function (err) {
                            if (typeof failureCb === 'function') failureCb(err);
                        });
                    }
                    const options = arguments[0];
                    return origCreateOffer(options).then(function (offer) {
                        if (offer && offer.sdp) {
                            offer.sdp = offer.sdp.replace(/c=IN IP4 .+\r\n/g, 'c=IN IP4 0.0.0.0\r\n');
                        }
                        return offer;
                    });
                };

                // Safe createAnswer supporting both legacy callback and Promise
                const origCreateAnswer = pc.createAnswer.bind(pc);
                pc.createAnswer = function () {
                    if (typeof arguments[0] === 'function') {
                        const successCb = arguments[0];
                        const failureCb = arguments[1];
                        const options = arguments[2];
                        return origCreateAnswer(options).then(function (answer) {
                            if (answer && answer.sdp) {
                                answer.sdp = answer.sdp.replace(/c=IN IP4 .+\r\n/g, 'c=IN IP4 0.0.0.0\r\n');
                            }
                            successCb(answer);
                        }).catch(function (err) {
                            if (typeof failureCb === 'function') failureCb(err);
                        });
                    }
                    const options = arguments[0];
                    return origCreateAnswer(options).then(function (answer) {
                        if (answer && answer.sdp) {
                            answer.sdp = answer.sdp.replace(/c=IN IP4 .+\r\n/g, 'c=IN IP4 0.0.0.0\r\n');
                        }
                        return answer;
                    });
                };

                return pc;
            };

            // Copy prototype
            SafeRTCPeerConnection.prototype = OrigRTCPeerConnection.prototype;

            // Copy all static properties and methods (e.g. generateCertificate, getDefaultIceServers)
            Object.getOwnPropertyNames(OrigRTCPeerConnection).forEach(function (prop) {
                if (!(prop in SafeRTCPeerConnection)) {
                    try {
                        const desc = Object.getOwnPropertyDescriptor(OrigRTCPeerConnection, prop);
                        if (desc) Object.defineProperty(SafeRTCPeerConnection, prop, desc);
                    } catch (_) {}
                }
            });

            rawWin.RTCPeerConnection = SafeRTCPeerConnection;
        }
    } catch (_) {}

    // 3.3 Anti-Fingerprinting Canvas2D and WebGL Noise
    // Avoid challenge/bot detection breakages on search and captcha domains
    const isSearchOrCaptchaDomain = host.includes('google.') || host.includes('gstatic.') ||
        host.includes('recaptcha') || host.includes('hcaptcha') || host.includes('cloudflare') ||
        host.includes('bing.com') || host.includes('duckduckgo.com');

    if (!isSearchOrCaptchaDomain) {
        try {
            const _pNoise = (function () {
                const seed = Math.floor(Math.random() * 32) - 16;
                const audioShift = (Math.random() * 0.0002) - 0.0001;
                return { seed, audioShift };
            })();

            // Canvas 2D: Limit noise to reads larger than 64 bytes (>4x4 px) to avoid probe detection
            if (rawWin.CanvasRenderingContext2D) {
                const origGetImageData = rawWin.CanvasRenderingContext2D.prototype.getImageData;
                rawWin.CanvasRenderingContext2D.prototype.getImageData = function (...args) {
                    const d = origGetImageData.apply(this, args);
                    if (d && d.data && d.data.length > 64) {
                        d.data[0] = (d.data[0] + _pNoise.seed) & 255;
                        d.data[1] = (d.data[1] + (_pNoise.seed >> 1)) & 255;
                    }
                    return d;
                };
            }

            // WebGL readPixels: Limit noise to buffers larger than 64 bytes
            if (rawWin.WebGLRenderingContext) {
                const origReadPixels = rawWin.WebGLRenderingContext.prototype.readPixels;
                rawWin.WebGLRenderingContext.prototype.readPixels = function (x, y, w, h, fmt, type, buf) {
                    origReadPixels.apply(this, arguments);
                    if (buf && buf.length > 64) {
                        buf[0] = (buf[0] ^ 1) & 255;
                    }
                };
            }

            // AudioContext noise
            if (typeof rawWin.AudioBuffer !== 'undefined') {
                const origGetChannelData = rawWin.AudioBuffer.prototype.getChannelData;
                rawWin.AudioBuffer.prototype.getChannelData = function (channel) {
                    const d = origGetChannelData.apply(this, [channel]);
                    if (d && d.length > 0) d[0] = d[0] + _pNoise.audioShift;
                    return d;
                };
            }
        } catch (_) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Usability Overrides: Page Zoom & Vertical Scroll Enforcement
    // ─────────────────────────────────────────────────────────────────────────
    try {
        if (isTopFrame) {
            // Force user-scalable viewport
            const forceViewport = function () {
                const doc = rawWin.document;
                if (!doc) return;
                const viewports = doc.querySelectorAll('meta[name="viewport"], meta[name="VIEWPORT"]');
                viewports.forEach(function (v) {
                    const content = v.getAttribute('content') || '';
                    if (content.includes('user-scalable=no') || content.includes('maximum-scale=1')) {
                        const fixed = content
                            .replace(/user-scalable\s*=\s*(no|0)/gi, 'user-scalable=yes')
                            .replace(/maximum-scale\s*=\s*1(\.0+)?(\b|\s*,)/gi, 'maximum-scale=10.0$2');
                        v.setAttribute('content', fixed);
                    }
                });
            };

            // Force vertical scrollability if site attempts overflow: hidden locks
            const forceScroll = function () {
                const doc = rawWin.document;
                if (!doc) return;
                const root = doc.documentElement;
                const body = doc.body;
                if (root && root.style && root.style.overflowY === 'hidden') {
                    root.style.setProperty('overflow-y', 'auto', 'important');
                }
                if (body && body.style && body.style.overflowY === 'hidden') {
                    body.style.setProperty('overflow-y', 'auto', 'important');
                }
            };

            const docObserver = new MutationObserver(function () {
                forceViewport();
                forceScroll();
            });

            const initObserver = function () {
                const doc = rawWin.document;
                if (doc && doc.documentElement) {
                    forceViewport();
                    forceScroll();
                    docObserver.observe(doc.documentElement, {
                        attributes: true,
                        attributeFilter: ['content', 'style', 'class'],
                        childList: true
                    });
                }
            };

            if (rawWin.document && rawWin.document.readyState !== 'loading') {
                initObserver();
            } else if (rawWin.document) {
                rawWin.document.addEventListener('DOMContentLoaded', initObserver, { once: true });
            }
        }
    } catch (_) {}

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Blank Page Diagnostic Sentinel
    // ─────────────────────────────────────────────────────────────────────────
    try {
        const checkBlankPage = function () {
            try {
                if (!isTopFrame) return;
                if (!location.href || location.href === 'about:blank') return;
                const doc = rawWin.document;
                if (!doc) return;
                const body = doc.body;
                if (!body) {
                    if (appPort) {
                        appPort.postMessage({
                            type: "BLANK_PAGE_ALERT",
                            reason: "document.body is null after DOMContentLoaded",
                            details: "URL: " + location.href
                        });
                    }
                    return;
                }
                const style = rawWin.getComputedStyle(body);
                if (style.display === "none") {
                    if (appPort) {
                        appPort.postMessage({
                            type: "BLANK_PAGE_ALERT",
                            reason: "document.body has 'display: none !important' (hidden by stylesheet / cosmetic filter)",
                            details: "URL: " + location.href
                        });
                    }
                } else if (style.visibility === "hidden") {
                    if (appPort) {
                        appPort.postMessage({
                            type: "BLANK_PAGE_ALERT",
                            reason: "document.body has 'visibility: hidden'",
                            details: "URL: " + location.href
                        });
                    }
                } else if (parseFloat(style.opacity) === 0) {
                    if (appPort) {
                        appPort.postMessage({
                            type: "BLANK_PAGE_ALERT",
                            reason: "document.body has 'opacity: 0'",
                            details: "URL: " + location.href
                        });
                    }
                } else if (body.children.length === 0 && (!body.textContent || body.textContent.trim().length === 0)) {
                    if (appPort) {
                        appPort.postMessage({
                            type: "BLANK_PAGE_ALERT",
                            reason: "document.body is completely empty with 0 elements and 0 text",
                            details: "URL: " + location.href
                        });
                    }
                }
            } catch (_) {}
        };

        if (rawWin.document && rawWin.document.readyState === 'complete') {
            setTimeout(checkBlankPage, 1500);
        } else if (rawWin.document) {
            rawWin.addEventListener('load', function () {
                setTimeout(checkBlankPage, 1500);
            }, { once: true });
        }
    } catch (_) {}

})();
