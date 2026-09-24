// Petal Dark Webpages — Smart Dark Theme Injection with Whitelist Support & Contrast Adjustments
(function() {
    'use strict';

    const STYLE_ID = 'petal-dark-webpages-style';
    const host = window.location.hostname;

    function applyDarkStyle(contrast, backgroundBrightness) {
        let style = document.getElementById(STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            (document.head || document.documentElement).appendChild(style);
        }
        const inv = contrast || 90;
        const bg = backgroundBrightness || '#121212';
        style.textContent = `
            html {
                filter: invert(${inv}%) hue-rotate(180deg) !important;
                background-color: ${bg} !important;
            }
            img, video, canvas, svg, [style*="background-image"], picture, iframe {
                filter: invert(100%) hue-rotate(180deg) !important;
            }
            @media (prefers-color-scheme: dark) {
                html {
                    background-color: ${bg} !important;
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
                browser.storage.local.get(['whitelist', 'enabled', 'contrast', 'backgroundBrightness'], function(res) {
                    const isEnabled = res.enabled !== false;
                    const whitelist = res.whitelist || [];
                    const isWhitelisted = whitelist.some(item => item && (host === item || host.endsWith('.' + item)));

                    if (isEnabled && !isWhitelisted) {
                        applyDarkStyle(res.contrast, res.backgroundBrightness);
                    } else {
                        removeDarkStyle();
                    }
                });
            } else {
                applyDarkStyle(90, '#121212');
            }
        } catch (e) {
            applyDarkStyle(90, '#121212');
        }
    }

    checkAndApply();

    if (typeof browser !== 'undefined' && browser.storage && browser.storage.onChanged) {
        browser.storage.onChanged.addListener(function(changes, area) {
            if (area === 'local') {
                checkAndApply();
            }
        });
    }
})();
