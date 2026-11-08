// Petal Dark Webpages — Smart Dark Theme Injection with Whitelist & Dark-Aware Protection
(function() {
    'use strict';

    const STYLE_ID = 'petal-dark-webpages-style';
    const host = window.location.hostname.toLowerCase();
    let currentContrast = 90;
    let isWhitelisted = false;
    let isExtensionEnabled = true;
    let domReady = false;
    let rafPending = false;
    let bodyObserved = false;
    let observeBodyFn = null;

    // When the engine already reports prefers-color-scheme: dark, sites that support it render
    // dark natively. At document_start their styles are not parsed yet, so inverting now would
    // turn them light (double inversion). Wait for the DOM before deciding in that case.
    function engineIsDark() {
        try { return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches); }
        catch (_) { return false; }
    }

    function scheduleApply() {
        if (rafPending) return;
        rafPending = true;
        const run = function() { rafPending = false; applyDarkStyle(); };
        if (typeof requestAnimationFrame === 'function') requestAnimationFrame(run); else setTimeout(run, 16);
    }

    // Detect if page is natively dark or already styled with dark colors
    function isPageAlreadyDark() {
        try {
            const root = document.documentElement;
            if (!root) return false;

            // 1. Explicit dark theme meta tag or HTML attribute flags (GitHub, Twitter, Discord, Reddit, etc.)
            const colorSchemeMeta = document.querySelector('meta[name="color-scheme"]');
            if (colorSchemeMeta && colorSchemeMeta.content && colorSchemeMeta.content.includes('dark') && !colorSchemeMeta.content.includes('light')) {
                return true;
            }

            const dataTheme = (root.getAttribute('data-theme') || root.getAttribute('data-color-mode') || root.getAttribute('data-bs-theme') || '').toLowerCase();
            if (dataTheme === 'dark' || dataTheme === 'night' || dataTheme === 'black') {
                return true;
            }

            if (root.classList.contains('dark') || root.classList.contains('dark-theme') || root.classList.contains('theme-dark')) {
                return true;
            }

            const body = document.body;
            if (body) {
                const bodyTheme = (body.getAttribute('data-theme') || body.getAttribute('data-color-mode') || '').toLowerCase();
                if (bodyTheme === 'dark' || bodyTheme === 'night' || bodyTheme === 'black') {
                    return true;
                }
                if (body.classList.contains('dark') || body.classList.contains('dark-mode') || body.classList.contains('theme-dark')) {
                    return true;
                }
            }

            // 2. Computed color-scheme CSS property
            const rootStyle = window.getComputedStyle(root);
            if (rootStyle && rootStyle.colorScheme && rootStyle.colorScheme.includes('dark') && !rootStyle.colorScheme.includes('light')) {
                return true;
            }
            if (body) {
                const bodyStyle = window.getComputedStyle(body);
                if (bodyStyle && bodyStyle.colorScheme && bodyStyle.colorScheme.includes('dark') && !bodyStyle.colorScheme.includes('light')) {
                    return true;
                }
            }

            // 3. Computed background and text luminance analysis
            function parseRgb(colorStr) {
                if (!colorStr || colorStr === 'transparent' || colorStr === 'rgba(0, 0, 0, 0)') return null;
                const m = colorStr.match(/\d+/g);
                if (!m || m.length < 3) return null;
                const a = m.length >= 4 ? parseFloat(m[3]) : 1.0;
                if (a < 0.4) return null;
                return { r: parseInt(m[0], 10), g: parseInt(m[1], 10), b: parseInt(m[2], 10) };
            }

            function getLuminance(rgb) {
                if (!rgb) return null;
                // Standard Rec. 709 luminance
                return (0.2126 * rgb.r + 0.7152 * rgb.g + 0.0722 * rgb.b) / 255;
            }

            const rootBgRgb = parseRgb(rootStyle ? rootStyle.backgroundColor : null);
            const bodyStyle = body ? window.getComputedStyle(body) : null;
            const bodyBgRgb = parseRgb(bodyStyle ? bodyStyle.backgroundColor : null);

            const activeBgRgb = bodyBgRgb || rootBgRgb;
            const bgLum = getLuminance(activeBgRgb);

            // Also check text color luminance if available
            const textColorRgb = parseRgb(bodyStyle ? bodyStyle.color : (rootStyle ? rootStyle.color : null));
            const textLum = getLuminance(textColorRgb);

            // If background is dark (luminance < 0.35)
            if (bgLum !== null && bgLum < 0.35) {
                // If text is bright, this is unequivocally a native dark mode website
                if (textLum === null || textLum > 0.45) {
                    return true;
                }
            }

            // Also check main container elements if body/root are transparent
            if (bgLum === null && body) {
                const mainEl = document.querySelector('main, #root, #app, #__next, .main-container');
                if (mainEl) {
                    const mainStyle = window.getComputedStyle(mainEl);
                    const mainBg = parseRgb(mainStyle ? mainStyle.backgroundColor : null);
                    const mainLum = getLuminance(mainBg);
                    if (mainLum !== null && mainLum < 0.35) {
                        return true;
                    }
                }
            }
        } catch (_) {}
        return false;
    }

    function applyDarkStyle() {
        if (!isExtensionEnabled || isWhitelisted) {
            removeDarkStyle();
            return;
        }

        if (!domReady && engineIsDark()) {
            return;
        }

        if (isPageAlreadyDark()) {
            removeDarkStyle();
            return;
        }

        let style = document.getElementById(STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            const target = document.head || document.documentElement;
            if (!target) {
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', applyDarkStyle, { once: true });
                }
                return;
            }
            target.appendChild(style);
        }

        const invertVal = Math.max(75, Math.min(100, currentContrast));
        style.textContent = `
            html {
                filter: invert(${invertVal}%) hue-rotate(180deg) contrast(96%) !important;
                background-color: #121212 !important;
            }
            img, video, canvas, svg:not([role="img"]), [style*="background-image"], picture, iframe, embed, object {
                filter: invert(100%) hue-rotate(180deg) !important;
            }
            :fullscreen, :-webkit-full-screen {
                filter: none !important;
            }
            :fullscreen img, :fullscreen video, :-webkit-full-screen img, :-webkit-full-screen video {
                filter: none !important;
            }
            body {
                text-rendering: optimizeLegibility !important;
                -webkit-font-smoothing: antialiased !important;
            }
        `;
    }

    function removeDarkStyle() {
        const style = document.getElementById(STYLE_ID);
        if (style) style.remove();
    }

    function checkHostAgainstWhitelist(whitelist) {
        if (!host || !Array.isArray(whitelist)) return false;
        return whitelist.some(item => {
            if (!item) return false;
            const clean = item.toLowerCase().trim();
            return host === clean || host.endsWith('.' + clean);
        });
    }

    function updateFromSettings(settings) {
        if (!settings) return;
        isExtensionEnabled = settings.enabled !== false;
        currentContrast = typeof settings.contrast === 'number' ? settings.contrast : 90;
        isWhitelisted = checkHostAgainstWhitelist(settings.whitelist);
        applyDarkStyle();
    }

    // 1. Initial check with background script
    const api = typeof browser !== "undefined" ? browser : chrome;
    try {
        api.runtime.sendMessage({ type: 'GET_DARK_SETTINGS' }, function(response) {
            if (response) {
                updateFromSettings(response);
            }
        });
    } catch (_) {}

    // 2. Document lifecycle observers
    if (document.documentElement) {
        applyDarkStyle();
    } else {
        document.addEventListener('DOMContentLoaded', applyDarkStyle, { once: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            domReady = true;
            observeBody();
            // Re-evaluate once full DOM and styles are available
            setTimeout(applyDarkStyle, 60);
        }, { once: true });
    } else {
        domReady = true;
    }

    window.addEventListener('load', function() {
        domReady = true;
        // Final re-evaluation on window load
        setTimeout(applyDarkStyle, 100);
    });

    // 3. MutationObserver to auto-detect theme changes (SPA theme toggles on YouTube/GitHub/Twitter)
    try {
        const observer = new MutationObserver(function(mutations) {
            for (let i = 0; i < mutations.length; i++) {
                const m = mutations[i];
                if (m.type === 'attributes' && (m.attributeName === 'class' || m.attributeName === 'data-theme' || m.attributeName === 'data-color-mode' || m.attributeName === 'style')) {
                    scheduleApply();
                    break;
                }
            }
        });
        if (document.documentElement) {
            observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'data-theme', 'data-color-mode', 'style'] });
        }
        // Many sites (GitHub, Reddit, YouTube) toggle their theme on <body>, not <html>.
        observeBodyFn = function() {
            if (bodyObserved || !document.body) return;
            bodyObserved = true;
            observer.observe(document.body, { attributes: true, attributeFilter: ['class', 'data-theme', 'data-color-mode'] });
        };
    } catch (_) {}

    function observeBody() { try { if (observeBodyFn) observeBodyFn(); } catch (_) {} }
    observeBody();

    // Follow engine color-scheme changes (theme switched in Petal settings) without a reload.
    try {
        const mq = window.matchMedia('(prefers-color-scheme: dark)');
        const onChange = function() { scheduleApply(); };
        if (mq.addEventListener) mq.addEventListener('change', onChange); else if (mq.addListener) mq.addListener(onChange);
    } catch (_) {}
})();
