// override.js
(function() {
    'use strict';

    const events = [
        'copy', 'cut', 'selectstart', 'contextmenu'
    ];

    // Intercept with capturing phase to bypass inline handlers that block copy/select
    events.forEach(eventName => {
        document.addEventListener(eventName, function(e) {
            e.stopImmediatePropagation();
        }, true);
    });

    // Clear document event properties that block copy/select
    document.onselectstart = null;
    document.oncopy = null;
    document.oncut = null;
    document.oncontextmenu = null;

    function enableSelection(el) {
        if (!el || !el.style) return;
        el.style.userSelect = 'text';
        el.style.webkitUserSelect = 'text';
    }

    if (document.body) {
        document.body.onselectstart = null;
        document.body.oncontextmenu = null;
        enableSelection(document.body);
    }
    if (document.documentElement) {
        enableSelection(document.documentElement);
    }

    console.log('[PetalCopy] Universal selection forced.');
})();
