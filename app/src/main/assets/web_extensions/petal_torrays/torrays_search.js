// Petal Torrays Search Integration
// Auto-populates and executes search queries on Torrays (https://torrays.vercel.app/) when opened with ?q=
(function() {
    'use strict';

    function initTorraysAutoSearch() {
        try {
            const url = new URL(window.location.href);
            const query = url.searchParams.get('q');
            if (!query || !query.trim()) return;

            const trimmedQuery = query.trim();

            let attempts = 0;
            const maxAttempts = 20;

            function tryFillAndSubmit() {
                attempts++;
                const input = document.querySelector('input.search-input') || document.querySelector('input[type="text"]');
                const form = document.querySelector('form.search-form') || document.querySelector('form');

                if (input && form) {
                    if (input.dataset.petalSearched === trimmedQuery) {
                        return; // Already executed for this query
                    }

                    // React controlled component setter bypass
                    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
                    if (nativeSetter) {
                        nativeSetter.call(input, trimmedQuery);
                    } else {
                        input.value = trimmedQuery;
                    }

                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));

                    input.dataset.petalSearched = trimmedQuery;

                    setTimeout(() => {
                        const submitButton = form.querySelector('button.search-button') || form.querySelector('button[type="submit"]');
                        if (submitButton && !submitButton.disabled) {
                            submitButton.click();
                        } else if (form.requestSubmit) {
                            form.requestSubmit();
                        } else {
                            form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                        }
                    }, 120);
                } else if (attempts < maxAttempts) {
                    setTimeout(tryFillAndSubmit, 200);
                }
            }

            if (document.readyState === 'complete' || document.readyState === 'interactive') {
                tryFillAndSubmit();
            } else {
                document.addEventListener('DOMContentLoaded', tryFillAndSubmit, { once: true });
                window.addEventListener('load', tryFillAndSubmit, { once: true });
            }
        } catch (e) {
            console.error('[Petal Torrays] Auto-search error:', e);
        }
    }

    initTorraysAutoSearch();
})();
