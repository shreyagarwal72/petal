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
        let target = e.target;
        while (target && target.tagName !== 'A') {
            target = target.parentElement;
        }
        if (target && target.href) {
            const cleaned = cleanUrl(target.href);
            if (cleaned !== target.href) {
                target.href = cleaned;
            }
        }
    }, true);
})();
