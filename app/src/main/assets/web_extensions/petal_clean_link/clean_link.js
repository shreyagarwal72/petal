// Petal Clean Link - Strips tracking query parameters and unpacks redirect debouncers
(function() {
    'use strict';

    const TRACKING_PARAMS = [
        // Google / Analytics
        'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'utm_cid', 'utm_reader',
        'gclid', 'gbraid', 'wbraid', 'dclid', '_ga', '_gl',
        // Facebook / Meta / Instagram
        'fbclid', 'igshid',
        // Microsoft / Bing
        'msclkid',
        // Twitter / X
        'twclid',
        // TikTok
        'ttclid',
        // Yandex
        'yclid', '_openstat',
        // HubSpot & Marketo
        '_hsenc', '_hsmi', 'hsCtsTracking', 'mkt_tok',
        // Mailchimp & Email trackers
        'mc_cid', 'mc_eid', 'vero_id', 'vero_conv',
        // Affiliates & Ad networks
        'zanpid', 'sc_customer', 'sc_channel', 'wickedid', 'rb_clickid', 's_kwcid',
        'irclickid', 'trk_contact', 'trk_msg', 'trk_module', 'trk_sid'
    ];

    function cleanUrl(urlString) {
        try {
            const url = new URL(urlString);
            let modified = false;

            // Redirect debouncing for search / social tracking wrappers
            if (url.hostname.includes('google.') && url.pathname === '/url' && url.searchParams.has('q')) {
                return cleanUrl(url.searchParams.get('q'));
            }
            if (url.hostname.includes('google.') && url.pathname === '/url' && url.searchParams.has('url')) {
                return cleanUrl(url.searchParams.get('url'));
            }

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
