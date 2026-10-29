// Petal Dark Webpages — Smart Dark Theme Injection with Whitelist & Dark-Aware Protection
(function() {
    'use strict';

    const STYLE_ID = 'petal-dark-webpages-style';
    const host = window.location.hostname;

    function isPageAlreadyDark() {
        try {
            const root = document.documentElement;
            if (!root) return false;
            const rootStyle = window.getComputedStyle(root);
            const rootBg = rootStyle ? rootStyle.backgroundColor : '';
            const body = document.body;
            const bodyStyle = body ? window.getComputedStyle(body) : null;
            const bodyBg = bodyStyle ? bodyStyle.backgroundColor : '';

            const bg = (bodyBg && bodyBg !== 'rgba(0, 0, 0, 0)' && bodyBg !== 'transparent') ? bodyBg : rootBg;
            if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
                const rgb = bg.match(/\d+/g);
                if (rgb && rgb.length >= 3) {
                    const r = parseInt(rgb[0], 10);
                    const g = parseInt(rgb[1], 10);
                    const b = parseInt(rgb[2], 10);
                    const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
                    if (lum < 0.38) {
                        return true;
                    }
                }
            }
        } catch (_) {}
        return false;
    }

    function applyDarkStyle() {
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

        style.textContent = `
            html {
                filter: invert(90%) hue-rotate(180deg) contrast(96%) !important;
                background-color: #121212 !important;
            }
            img, video, canvas, svg, [style*="background-image"], picture, iframe, embed, object {
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

    function checkAndApply() {
        try {
            if (typeof browser !== 'undefined' && browser.storage && browser.storage.local) {
                browser.storage.local.get(['whitelist', 'enabled'], function(res) {
                    const isEnabled = res && res.enabled !== undefined ? res.enabled : true;
                    const whitelist = (res && res.whitelist) || [];
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

    if (document.documentElement) {
        checkAndApply();
    } else {
        document.addEventListener('DOMContentLoaded', checkAndApply, { once: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            setTimeout(checkAndApply, 50);
        }, { once: true });
    }

    if (typeof browser !== 'undefined' && browser.storage && browser.storage.onChanged) {
        browser.storage.onChanged.addListener(function(changes, area) {
            if (area === 'local') {
                checkAndApply();
            }
        });
    }
})();
