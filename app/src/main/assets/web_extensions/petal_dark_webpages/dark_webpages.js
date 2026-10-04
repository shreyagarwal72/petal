// Petal Dark Webpages — Smart Dark Theme Injection with Whitelist Support
(function() {
    'use strict';

    const STYLE_ID = 'petal-dark-webpages-style';
    const host = window.location.hostname;

    function applyDarkStyle() {
        let style = document.getElementById(STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            (document.head || document.documentElement).appendChild(style);
        }

        style.textContent = `
            html {
                filter: invert(92%) hue-rotate(180deg) contrast(96%) !important;
                background-color: #121212 !important;
            }
            img, video, canvas, svg, [style*="background-image"], picture, iframe {
                filter: invert(100%) hue-rotate(180deg) !important;
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

    function checkAndApply() {
        try {
            if (typeof browser !== 'undefined' && browser.storage && browser.storage.local) {
                browser.storage.local.get(['whitelist', 'enabled'], function(res) {
                    const isEnabled = res.enabled !== false;
                    const whitelist = res.whitelist || [];
                    const isWhitelisted = whitelist.some(item => item && (host === item || host.endsWith('.' + item)));

                    if (isEnabled && !isWhitelisted) {
                        applyDarkStyle();
                    } else {
                        removeDarkStyle();
                    }
                });
            } else {
                applyDarkStyle();
            }
        } catch (e) {
            applyDarkStyle();
        }
    }

    checkAndApply();

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', checkAndApply, { once: true });
    }

    if (typeof browser !== 'undefined' && browser.storage && browser.storage.onChanged) {
        browser.storage.onChanged.addListener(function(changes, area) {
            if (area === 'local') {
                checkAndApply();
            }
        });
    }
})();
