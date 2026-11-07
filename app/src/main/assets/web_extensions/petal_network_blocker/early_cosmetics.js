// Petal Early Cosmetics & Anti-Ad Scriptlet
// Injected at document_start across all frames to eliminate ad layout shift before initial render.

(function () {
    "use strict";

    if (window.__petal_early_cosmetics_injected__) return;
    window.__petal_early_cosmetics_injected__ = true;

    // 1. Generic cosmetic hiding stylesheet applied immediately at document_start
    const GENERIC_COSMETIC_CSS = `
        /* Generic ad slots, banners & containers */
        [id*="google_ads_"],
        [id*="banner-ad"],
        [id*="advertisement"],
        [class*="ad-banner"],
        [class*="ad-container"],
        [class*="ad-wrapper"],
        [class*="sponsored-post"],
        [class*="sponsored-content"],
        [data-ad-unit],
        [data-ad-client],
        [data-ad-slot],
        .adsbygoogle,
        .ad-slot,
        .ad_wrapper,
        .ad-zone,
        .advertisement,
        .ad-box,
        .native-ad,
        .outbrain,
        .taboola,
        .trc_related_container,

        /* Video & YouTube ad overlays & companion cards */
        .ytp-ad-overlay-container,
        .ytp-ad-message-container,
        .ytp-ad-action-interstitial,
        ytd-promoted-sparkles-web-renderer,
        ytd-compact-promoted-item-renderer,
        ytd-promoted-video-renderer,
        #masthead-ad,
        rendered-ad-background,
        .ytd-in-feed-ad-layout-renderer,
        ytd-ad-slot-renderer,
        #player-ads {
            display: none !important;
            visibility: hidden !important;
            opacity: 0 !important;
            height: 0 !important;
            min-height: 0 !important;
            max-height: 0 !important;
            pointer-events: none !important;
        }
    `;

    function injectStyle() {
        const style = document.createElement("style");
        style.id = "petal-early-cosmetics-style";
        style.textContent = GENERIC_COSMETIC_CSS;
        const target = document.head || document.documentElement;
        if (target) {
            target.appendChild(style);
        } else {
            document.addEventListener("DOMContentLoaded", function () {
                (document.head || document.documentElement)?.appendChild(style);
            }, { once: true });
        }
    }

    injectStyle();

    // 2. YouTube Mobile Video Ad Auto-Skipper & Fast-Forwarder
    const isYouTube = location.hostname.includes("youtube.com");
    if (isYouTube) {
        function handleYouTubeAds() {
            try {
                const video = document.querySelector("video");
                const adContainer = document.querySelector(".ad-container, .ad-interrupting, .ytp-ad-player-overlay, .ytp-ad-module, ytd-promoted-sparkles-web-renderer, #player-ads");
                const skipBtn = document.querySelector(".ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-slot");

                if (adContainer || skipBtn || (video && document.querySelector(".ytp-ad-text"))) {
                    if (video) {
                        video.muted = true;
                        video.playbackRate = 16.0;
                        if (isFinite(video.duration) && video.duration > 0) {
                            video.currentTime = video.duration - 0.1;
                        }
                    }
                    if (skipBtn) {
                        skipBtn.click();
                    }
                }

                // Remove promo banners & companion ads
                const promoElements = document.querySelectorAll("ytd-compact-promoted-item-renderer, ytd-promoted-video-renderer, #masthead-ad, rendered-ad-background, .ytd-in-feed-ad-layout-renderer, ytd-ad-slot-renderer");
                promoElements.forEach(el => el.remove());
            } catch (_) {}
        }

        setInterval(handleYouTubeAds, 250);
    }
})();
