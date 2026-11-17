/* Petal Consent Manager v2.0
 * ─────────────────────────────────────────────────────────────────────────
 * Best-in-class automated GDPR, CCPA, and cookie consent banner dismisser.
 * Lineage & Techniques:
 *   - cavi-au/Consent-O-Matic (Aarhus University CMP patterns)
 *   - uBlock Origin annoyances-cookies.txt & Fanboy's Cookie Monster
 * Capabilities:
 *   1. Zero-Flash CSS hiding injected at document_start.
 *   2. Comprehensive CMP auto-rejection: OneTrust, CookieBot, TrustArc,
 *      Quantcast, Didomi, Usercentrics, Axeptio, Klaro, Termly, Complianz,
 *      Tarteaucitron, CookieYes, Osano, Google Funding Choices, Choice CMP.
 *   3. Multi-step dark-pattern traversal (Options -> Reject All -> Save).
 *   4. Scroll unfreezing (clearing overflow: hidden / position: fixed on html & body).
 *   5. Backdrop removal (.modal-backdrop, .truste_box_overlay, etc.).
 *   6. Synthetic consent token seeding to prevent repetitive re-prompting.
 */

(function () {
    "use strict";

    if (window.__petal_consent_manager_active__) return;
    window.__petal_consent_manager_active__ = true;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Zero-Flash Stylesheet (Injected immediately at document_start)
    // ─────────────────────────────────────────────────────────────────────────
    const ANTI_FLASH_CSS = `
        /* Hide known CMP dialogs and backdrops before first layout */
        #onetrust-consent-sdk,
        #onetrust-banner-sdk,
        #CybotCookiebotDialog,
        #CybotCookiebotDialogBodyUnderlay,
        .truste_popframe,
        .truste_box_overlay,
        .qc-cmp2-container,
        .qc-cmp2-ui,
        #didomi-host,
        #didomi-notice,
        #didomi-popup,
        #uc-banner,
        [id^="usercentrics-"],
        #axeptio_overlay,
        .klaro .cookie-modal,
        .klaro .cookie-notice,
        .t-consentPrompt,
        .termly-popup,
        .cmplz-cookiebanner,
        #tarteaucitronAlertBig,
        .cky-consent-container,
        .cky-overlay,
        .osano-cm-window,
        .osano-cm-dialog,
        .fc-consent-root,
        .fc-dialog-container,
        .fc-dialog-overlay,
        [id*="cookie-banner" i],
        [id*="cookie-consent" i],
        [class*="cookie-banner" i],
        [class*="cookie-consent" i],
        [class*="consent-banner" i],
        [class*="consent-modal" i] {
            opacity: 0.01 !important;
            pointer-events: none !important;
            max-height: 0 !important;
            overflow: hidden !important;
        }
    `;

    function injectAntiFlashCss() {
        try {
            const style = document.createElement("style");
            style.id = "petal-consent-anti-flash";
            style.textContent = ANTI_FLASH_CSS;
            const target = document.head || document.documentElement;
            if (target) {
                target.appendChild(style);
            } else {
                document.addEventListener("DOMContentLoaded", function () {
                    (document.head || document.documentElement)?.appendChild(style);
                }, { once: true });
            }
        } catch (_) {}
    }

    injectAntiFlashCss();

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Selectors for Dismissal & CMP Actions
    // ─────────────────────────────────────────────────────────────────────────
    const DIRECT_REJECT_SELECTORS = [
        /* OneTrust */
        "#onetrust-reject-all-handler",
        ".ot-pc-refuse-all-handler",
        "button#onetrust-reject-all-handler",

        /* CookieBot / Cybot */
        "#CybotCookiebotDialogBodyButtonDecline",
        "#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll",
        "button[id*='CookiebotDialogBodyButtonDecline']",

        /* TrustArc */
        "#truste-consent-required",
        ".truste_popframe .pdynamicbutton a.call",
        "button.truste-button-secondary",

        /* Quantcast */
        ".qc-cmp2-summary-buttons button[mode='secondary']",
        "button[data-id='banner-btn-reject-all']",
        ".qc-cmp2-buttons-desktop button:first-child",

        /* Didomi */
        "#didomi-notice-disagree-button",
        ".didomi-refuse-all-notice",
        "button[id*='didomi-disagree']",
        "#didomi-notice-learn-more-button + button",

        /* Usercentrics */
        "button[data-testid='uc-deny-all-button']",
        "#uc-btn-deny-banner",
        "button[data-testid='uc-accept-essential-button']",

        /* Axeptio */
        "button#axeptio_btn_dismiss",
        "button.axeptio_btn_close",
        "[data-axeptio-action='refuse']",

        /* Klaro */
        ".klaro .cm-btn-decline",
        ".klaro .cn-decline",
        "button.cm-btn.cm-btn-danger",

        /* Termly */
        ".t-acceptDeclineBtn",
        "button[data-tid='banner-decline']",
        "button[data-tid='modal-decline']",

        /* Complianz */
        ".cmplz-cookiebanner .cmplz-deny",
        "button.cmplz-btn.cmplz-deny",
        ".cmplz-btn.cmplz-view-preferences + .cmplz-deny",

        /* Tarteaucitron */
        "#tarteaucitronAlertBig #tarteaucitronPersonalize2",
        "#tarteaucitronAllDenied2",

        /* CookieYes */
        ".cky-btn-reject",
        "[data-cky-tag='reject-button']",
        "button.cky-btn-reject",

        /* Osano */
        ".osano-cm-denyAll",
        "button.osano-cm-denyAll",

        /* Google Funding Choices */
        ".fc-dialog-container .fc-cta-do-not-consent",
        ".fc-consent-root button.fc-cta-do-not-consent",
        "button.fc-cta-do-not-consent",

        /* InMobi / Choice CMP */
        "button[aria-label='Do not consent']",
        ".choice-dialog-button-reject",

        /* High-confidence generic patterns */
        "button[data-testid*='reject' i]",
        "button[data-testid*='decline' i]",
        "button[class*='reject-all' i]",
        "button[id*='reject-all' i]",
        "button[class*='decline-all' i]",
        "button[id*='decline-all' i]",
        "button[class*='necessary-only' i]",
        "button[id*='necessary-only' i]",
        "button[class*='essential-only' i]",
        "a[class*='reject-all' i]"
    ];

    const MULTI_STEP_CONFIGS = [
        // OneTrust: If reject-all is hidden behind settings/preferences
        {
            detect: "#onetrust-banner-sdk",
            settingsButton: "#onetrust-pc-btn-handler, .ot-sdk-show-settings",
            rejectButton: "#onetrust-reject-all-handler, .ot-pc-refuse-all-handler",
            saveButton: ".save-preference-btn-handler, #accept-recommended-btn-handler"
        },
        // Google Funding Choices: Manage options -> Confirm choices
        {
            detect: ".fc-dialog-container",
            settingsButton: "button.fc-cta-manage-options",
            rejectButton: "button.fc-cta-do-not-consent",
            saveButton: "button.fc-cta-confirm-choices"
        }
    ];

    const OVERLAY_CONTAINERS = [
        "#onetrust-consent-sdk",
        "#onetrust-banner-sdk",
        "#CybotCookiebotDialog",
        "#CybotCookiebotDialogBodyUnderlay",
        ".truste_popframe",
        ".truste_box_overlay",
        ".qc-cmp2-container",
        "#didomi-host",
        "#didomi-notice",
        "#didomi-popup",
        "#uc-banner",
        ".axeptio_mount",
        ".klaro",
        ".t-consentPrompt",
        ".termly-popup",
        ".cmplz-cookiebanner",
        "#tarteaucitronAlertBig",
        ".cky-consent-container",
        ".osano-cm-window",
        ".fc-consent-root",
        ".fc-dialog-container"
    ];

    const BACKDROP_CLASSES = [
        "modal-open",
        "ot-overlay",
        "cookie-freeze",
        "qc-cmp2-ui-showing",
        "didomi-popup-open",
        "sp-message-open",
        "cmplz-hidden",
        "no-scroll",
        "overflow-hidden"
    ];

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Execution Helpers
    // ─────────────────────────────────────────────────────────────────────────
    function isClickable(el) {
        if (!el || typeof el.click !== "function") return false;
        const rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0 && !el.disabled;
    }

    // Shadow DOM traversal for Usercentrics and modern Web Components
    function findInShadowRoots(selector, root = document) {
        try {
            const el = root.querySelector(selector);
            if (el) return el;
            const elementsWithShadow = root.querySelectorAll("*");
            for (let i = 0; i < elementsWithShadow.length; i++) {
                const sRoot = elementsWithShadow[i].shadowRoot;
                if (sRoot) {
                    const found = findInShadowRoots(selector, sRoot);
                    if (found) return found;
                }
            }
        } catch (_) {}
        return null;
    }

    function tryDirectReject() {
        for (let i = 0; i < DIRECT_REJECT_SELECTORS.length; i++) {
            const sel = DIRECT_REJECT_SELECTORS[i];
            const el = findInShadowRoots(sel);
            if (el && isClickable(el)) {
                try {
                    el.click();
                    return true;
                } catch (_) {}
            }
        }
        return false;
    }

    function tryMultiStepReject() {
        for (let i = 0; i < MULTI_STEP_CONFIGS.length; i++) {
            const cfg = MULTI_STEP_CONFIGS[i];
            if (document.querySelector(cfg.detect)) {
                const settingsBtn = document.querySelector(cfg.settingsButton);
                if (settingsBtn && isClickable(settingsBtn)) {
                    try {
                        settingsBtn.click();
                        setTimeout(function () {
                            const rejectBtn = document.querySelector(cfg.rejectButton);
                            if (rejectBtn && isClickable(rejectBtn)) {
                                rejectBtn.click();
                            }
                            setTimeout(function () {
                                const saveBtn = document.querySelector(cfg.saveButton);
                                if (saveBtn && isClickable(saveBtn)) {
                                    saveBtn.click();
                                }
                            }, 150);
                        }, 150);
                        return true;
                    } catch (_) {}
                }
            }
        }
        return false;
    }

    function tryTextHeuristicReject() {
        const buttons = document.querySelectorAll("button, a[role='button'], div[role='button']");
        const rejectKeywords = [
            "reject all", "decline all", "refuse all", "only necessary",
            "necessary only", "reject non-essential", "decline", "reject",
            "essential cookies only", "continue without accepting",
            "tout refuser", "alle ablehnen", "rechazar todo", "rifiuta tutto"
        ];

        for (let i = 0; i < buttons.length; i++) {
            const btn = buttons[i];
            const text = (btn.textContent || btn.innerText || "").trim().toLowerCase();
            if (text.length > 2 && text.length < 40) {
                for (let k = 0; k < rejectKeywords.length; k++) {
                    if (text === rejectKeywords[k] || text.includes(rejectKeywords[k])) {
                        if (isClickable(btn)) {
                            try {
                                btn.click();
                                return true;
                            } catch (_) {}
                        }
                    }
                }
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Scroll Restoration & Cleanup
    // ─────────────────────────────────────────────────────────────────────────
    function unfreezePageScroll() {
        try {
            const html = document.documentElement;
            const body = document.body;

            if (html) {
                if (html.style.overflow === "hidden") html.style.overflow = "";
                if (html.style.position === "fixed") html.style.position = "";
                BACKDROP_CLASSES.forEach(c => html.classList.remove(c));
            }
            if (body) {
                if (body.style.overflow === "hidden") body.style.overflow = "";
                if (body.style.position === "fixed") body.style.position = "";
                BACKDROP_CLASSES.forEach(c => body.classList.remove(c));
            }

            // Remove inert backdrop modal elements
            const backdrops = document.querySelectorAll(".modal-backdrop, .truste_box_overlay, .qc-cmp2-ui-showing, .didomi-popup-backdrop, .fc-dialog-overlay");
            backdrops.forEach(function (b) {
                try { b.remove(); } catch (_) {}
            });

            // Cleanly remove any residual hidden overlay containers
            OVERLAY_CONTAINERS.forEach(function (sel) {
                const els = document.querySelectorAll(sel);
                els.forEach(function (el) {
                    try {
                        el.style.setProperty("display", "none", "important");
                    } catch (_) {}
                });
            });
        } catch (_) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Seed Consent Tokens (Prevent redundant re-prompting)
    // ─────────────────────────────────────────────────────────────────────────
    function seedConsentTokens() {
        try {
            // Seed standard localStorage tokens
            const dummyTokens = {
                "cookie_consent": "declined",
                "cookie-consent": "rejected",
                "gdpr_consent": "necessary",
                "euconsent-v2": "CP0000000000",
                "OptanonAlertBoxClosed": new Date().toISOString()
            };
            for (const key in dummyTokens) {
                if (localStorage.getItem(key) === null) {
                    localStorage.setItem(key, dummyTokens[key]);
                }
            }
        } catch (_) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Master Dismissal Lifecycle
    // ─────────────────────────────────────────────────────────────────────────
    let hasDismissed = false;

    function runDismissCycle() {
        if (hasDismissed) return;

        let handled = tryDirectReject();
        if (!handled) {
            handled = tryMultiStepReject();
        }
        if (!handled) {
            handled = tryTextHeuristicReject();
        }

        if (handled) {
            hasDismissed = true;
            unfreezePageScroll();
            seedConsentTokens();
            setTimeout(unfreezePageScroll, 250);
            setTimeout(unfreezePageScroll, 800);
        }
    }

    // Initial triggers
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", runDismissCycle, { once: true });
    } else {
        runDismissCycle();
    }

    // Handle deferred / SPA banners with MutationObserver
    let mutationTimer = null;
    const observer = new MutationObserver(function () {
        if (hasDismissed) return;
        if (!mutationTimer) {
            mutationTimer = setTimeout(function () {
                mutationTimer = null;
                runDismissCycle();
            }, 100);
        }
    });

    try {
        const root = document.documentElement || document.body;
        if (root) {
            observer.observe(root, { childList: true, subtree: true });
        }
    } catch (_) {}

    // Fallback timer ladder for delayed CMP popups
    setTimeout(runDismissCycle, 500);
    setTimeout(runDismissCycle, 1500);
    setTimeout(runDismissCycle, 3000);
    setTimeout(function () {
        if (!hasDismissed) {
            unfreezePageScroll();
        }
        observer.disconnect();
    }, 6000);
})();
