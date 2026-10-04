// Petal Clean Link - Strips tracking query parameters from clicked links and clipboard
(function() {
    'use strict';

    const TRACKING_PARAMS = [
        'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content',
        'fbclid', 'gclid', 'gbraid', 'wbraid', 'msclkid', 'dclid',
        'twclid', 'igshid', '_hsenc', '_hsmi', 'mc_cid', 'mc_eid',
        'yclid', '_openstat', 'zanpid', 'sc_customer', 'sc_channel'
    ];

    function cleanUrl(urlString) {
        try {
            const url = new URL(urlString);
            let modified = false;
            for (const param of TRACKING_PARAMS) {
                if (url.searchParams.has(param)) {
                    url.searchParams.delete(param);
                    modified = true;
                }
            }
            return modified ? url.toString() : urlString;
        } catch (e) {
            return urlString;
        }
    }

    document.addEventListener('click', function(e) {
        try {
            const anchor = e.target && e.target.closest ? e.target.closest('a[href]') : null;
            if (!anchor) return;
            const originalHref = anchor.getAttribute('href');
            if (!originalHref || originalHref.startsWith('#') || originalHref.startsWith('javascript:')) return;
            const fullUrl = anchor.href;
            if (typeof fullUrl === 'string' && (fullUrl.startsWith('http://') || fullUrl.startsWith('https://'))) {
                const cleaned = cleanUrl(fullUrl);
                if (cleaned !== fullUrl) {
                    anchor.href = cleaned;
                }
            }
        } catch (_) {}
    }, true);
})();
