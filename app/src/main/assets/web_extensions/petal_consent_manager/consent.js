/* Petal Consent Manager — auto-reject GDPR cookie banners
 * Supports: OneTrust, CookieBot/Cybot, TrustArc, Quantcast, Didomi,
 *           Termly, CookieYes, generic reject/decline/necessary patterns
 */
(function () {
  'use strict';

  var REJECT_SELECTORS = [
    /* OneTrust */
    '#onetrust-reject-all-handler',
    '.ot-sdk-btn.ot-sdk-show-settings',
    /* CookieBot / Cybot */
    '#CybotCookiebotDialogBodyButtonDecline',
    '#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll',
    /* TrustArc */
    '#truste-consent-required',
    '.truste_popframe .pdynamicbutton a.call',
    /* Quantcast */
    '.qc-cmp2-summary-buttons button[mode="secondary"]',
    '[data-id="banner-btn-reject-all"]',
    /* Didomi */
    '#didomi-notice-disagree-button',
    '.didomi-refuse-all-notice',
    /* Termly */
    '.t-acceptDeclineBtn',
    /* CookieYes */
    '.cky-btn-reject',
    '[data-cky-tag="reject-button"]',
    /* PrivacyBee / Osano */
    '.osano-cm-denyAll',
    /* Generic high-confidence patterns */
    'button[data-testid*="reject" i]',
    'button[data-testid*="decline" i]',
    'button[class*="reject-all" i]',
    'button[id*="reject-all" i]',
    'a[class*="reject-all" i]',
    'button[class*="decline-all" i]',
    'button[id*="decline-all" i]',
    /* Necessary only */
    'button[class*="necessary-only" i]',
    'button[id*="necessary-only" i]',
  ];

  var OVERLAY_SELECTORS = [
    '#onetrust-banner-sdk',
    '#onetrust-consent-sdk',
    '#CybotCookiebotDialog',
    '.truste_popframe',
    '.truste_box_overlay',
    '.qc-cmp2-container',
    '#didomi-host',
    '#didomi-notice',
    '.cky-consent-container',
    '.termly-popup',
    '.osano-cm-window',
    '[class*="cookie-banner" i]',
    '[id*="cookie-banner" i]',
    '[class*="cookie-consent" i]',
    '[id*="cookie-consent" i]',
    '[class*="gdpr-consent" i]',
    '[id*="gdpr" i]',
  ];

  function isVisible(el) {
    return el && el.offsetParent !== null && el.getBoundingClientRect().width > 0;
  }

  function hasOverlay() {
    return OVERLAY_SELECTORS.some(function (sel) {
      return !!document.querySelector(sel);
    });
  }

  function tryReject() {
    for (var i = 0; i < REJECT_SELECTORS.length; i++) {
      var el = document.querySelector(REJECT_SELECTORS[i]);
      if (el && isVisible(el)) {
        el.click();
        return true;
      }
    }
    return false;
  }

  function attempt() {
    if (!hasOverlay()) return;
    var rejected = tryReject();
    if (!rejected) {
      setTimeout(tryReject, 800);
      setTimeout(tryReject, 2000);
      setTimeout(tryReject, 4000);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', attempt);
  } else {
    attempt();
  }

  /* Observe DOM for late-injected banners (SPAs, deferred loads) */
  var observer = new MutationObserver(function () {
    if (hasOverlay()) {
      observer.disconnect();
      setTimeout(attempt, 300);
    }
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });
})();
