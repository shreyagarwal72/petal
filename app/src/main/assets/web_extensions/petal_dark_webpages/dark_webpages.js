// Petal Dark Webpages — Smart Dark Theme Injection with Whitelist Support
// Inspired by Firefox & modern dark reader engines: avoids double inverting existing dark sites,
// preserves images/videos/svgs, and operates seamlessly without complex contrast intensity controls.
(function() {
    'use strict';

    const STYLE_ID = 'petal-dark-webpages-style';
    const host = window.location.hostname;

    // Checks if the document already natively renders in dark mode (via CSS prefers-color-scheme or dark background)
    function isAlreadyDark() {
        try {
            if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
                const bodyBg = window.getComputedStyle(document.body).backgroundColor;
                const htmlBg = window.getComputedStyle(document.documentElement).backgroundColor;
                const bg = bodyBg !== 'rgba(0, 0, 0, 0)' && bodyBg !== 'transparent' ? bodyBg : htmlBg;
                const rgb = bg.match(/\d+/g);
                if (rgb && rgb.length >= 3) {
                    const brightness = (parseInt(rgb[0]) * 299 + parseInt(rgb[1]) * 587 + parseInt(rgb[2]) * 114) / 1000;
                    if (brightness < 60) return true;
                }
            }
        } catch (_) {}
        return false;
    }

    function applyDarkStyle() {
        if (isAlreadyDark()) {
            removeDarkStyle();
            return;
        }

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
            /* Preserve text contrast and smooth rendering */
            body {
                text-rendering: optimizeLegibility !important;
                -webkit-font-smoothing: antialiased !important;
            }
            @media (prefers-color-scheme: dark) {
                html {
                    background-color: #121212 !important;
                }
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

    // Re-check once DOM finishes loading to verify if site native dark mode was applied
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
