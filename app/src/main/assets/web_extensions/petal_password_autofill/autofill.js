// Petal Password Manager & Autofill In-Page Content Script
(function() {
    'use strict';

    if (window.__petal_autofill_installed) return;
    window.__petal_autofill_installed = true;

    // Detect password/login fields and observe form submissions
    function setupFormListeners() {
        const forms = document.querySelectorAll('form');
        forms.forEach(form => {
            if (form.__petal_monitored) return;
            form.__petal_monitored = true;

            form.addEventListener('submit', function() {
                const passField = form.querySelector('input[type="password"]');
                if (!passField) return;
                const userField = form.querySelector('input[type="text"], input[type="email"], input[name*="user"], input[name*="login"], input[id*="user"]');
                const username = userField ? userField.value : '';
                const password = passField.value;

                if (password && password.length > 0) {
                    // Dispatch custom DOM event that can be listened to or handled
                    window.dispatchEvent(new CustomEvent('petal:login_submitted', {
                        detail: {
                            url: window.location.href,
                            hostname: window.location.hostname,
                            username: username,
                            password: password
                        }
                    }));
                }
            }, true);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setupFormListeners);
    } else {
        setupFormListeners();
    }

    // Observe dynamically added forms (SPAs)
    const observer = new MutationObserver(function() {
        setupFormListeners();
    });
    observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
})();
