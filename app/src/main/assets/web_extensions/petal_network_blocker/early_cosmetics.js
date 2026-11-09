// Petal Early Cosmetics & Anti-Ad Scriptlet
// Injected at document_start across all frames to eliminate ad layout shift before initial render.

(function () {
    "use strict";

    if (window.__petal_early_cosmetics_injected__) return;
    window.__petal_early_cosmetics_injected__ = true;

    // 1. Generic cosmetic hiding stylesheet applied immediately at document_start.
    //
    // Selectors match WHOLE class tokens / id prefixes only. Substring selectors such as
    // [class*="ad-container"] also match "head-container", "download-wrapper" or "load-banner" and
    // blanked real page layouts. html/body are never hidden, whatever a site names them.
    const GENERIC_COSMETIC_CSS = `
        :not(html):not(body):is(
            /* Generic ad slots, banners & containers */
            [id^="google_ads_"],
            #banner-ad,
            #advertisement,
            .ad-banner,
            .ad-container,
            .ad-wrapper,
            .sponsored-post,
            .sponsored-content,
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
            #player-ads
        ) {
            display: none !important;
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
                // .ytp-ad-module, #player-ads and .ad-container exist in the player even when no ad
                // is playing, so they must NOT count as "an ad is showing". YouTube marks the player
                // with ad-showing / ad-interrupting only while an ad really plays.
                const player = document.querySelector(".html5-video-player");
                const adShowing = !!(player && (player.classList.contains("ad-showing") || player.classList.contains("ad-interrupting")));
                const skipBtn = document.querySelector(".ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-slot");

                if (adShowing || skipBtn) {
                    const video = (player && player.querySelector("video")) || document.querySelector("video");
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

        setInterval(function(){ if (!document.hidden) handleYouTubeAds(); }, 1000);
    }
})();
