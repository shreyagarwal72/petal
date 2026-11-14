// Petal Early Cosmetics & Main-World Scriptlet Injector
// Injected at document_start across all frames to prevent ad flash & layout shifts.
// Communicates with adblock-rust WASM background engine for dynamic cosmetic resources & MutationObserver batching.

(function () {
    "use strict";

    if (window.__petal_adblock_cosmetics_injected__) return;
    window.__petal_adblock_cosmetics_injected__ = true;

    const api = (typeof browser !== "undefined") ? browser : chrome;
    if (!api || !api.runtime || !api.runtime.sendMessage) return;

    let cosmeticStyleElement = null;
    let batchStyleElement = null;
    const knownHiddenSelectors = new Set();
    const pendingClasses = new Set();
    const pendingIds = new Set();
    let batchTimer = null;

    function getOrCreateStyle(id) {
        let el = document.getElementById(id);
        if (!el) {
            el = document.createElement("style");
            el.id = id;
            const target = document.head || document.documentElement;
            if (target) {
                target.appendChild(el);
            } else {
                document.addEventListener("DOMContentLoaded", function () {
                    (document.head || document.documentElement)?.appendChild(el);
                }, { once: true });
            }
        }
        return el;
    }

    function applyCssSelectors(selectors, styleElement) {
        if (!selectors || selectors.length === 0) return;
        const newSelectors = [];
        for (let i = 0; i < selectors.length; i++) {
            const sel = selectors[i].trim();
            if (sel && !knownHiddenSelectors.has(sel)) {
                knownHiddenSelectors.add(sel);
                newSelectors.push(sel);
            }
        }
        if (newSelectors.length === 0) return;

        const cssRule = newSelectors.join(", ") + " { display: none !important; }";
        if (styleElement.styleSheet) {
            styleElement.styleSheet.cssText += cssRule;
        } else {
            styleElement.appendChild(document.createTextNode(cssRule));
        }
    }

    /**
     * Injects uBO / Brave scriptlets directly into the page's main world.
     * On GeckoView 159: verifies main-world access; falls back to DOM <script> element injection.
     */
    function injectScriptletIntoMainWorld(scriptContent) {
        if (!scriptContent || typeof scriptContent !== "string") return;

        // 1. Try DOM <script> insertion into document.documentElement (standard webext pattern for main world)
        try {
            const script = document.createElement("script");
            script.type = "text/javascript";
            script.textContent = `(function() { "use strict";\n${scriptContent}\n})();`;
            const container = document.documentElement || document.head;
            if (container) {
                container.appendChild(script);
                script.remove();
                return;
            }
        } catch (_) {}

        // 2. Fallback to GeckoView wrappedJSObject evaluation
        try {
            if (typeof window.wrappedJSObject !== "undefined" && typeof window.wrappedJSObject.eval === "function") {
                window.wrappedJSObject.eval(`(function() { "use strict";\n${scriptContent}\n})();`);
                return;
            }
        } catch (_) {}

        // 3. Fallback on DOM ready if head/documentElement weren't ready
        document.addEventListener("DOMContentLoaded", function () {
            try {
                const script = document.createElement("script");
                script.type = "text/javascript";
                script.textContent = `(function() { "use strict";\n${scriptContent}\n})();`;
                (document.head || document.documentElement)?.appendChild(script);
                script.remove();
            } catch (_) {}
        }, { once: true });
    }

    // 1. Fetch initial cosmetic resources & scriptlets from adblock-rust background engine
    api.runtime.sendMessage({
        type: "GET_COSMETIC_RESOURCES",
        url: location.href
    }, function (response) {
        if (!response) return;

        // Apply hide selectors
        if (Array.isArray(response.hide_selectors) && response.hide_selectors.length > 0) {
            cosmeticStyleElement = getOrCreateStyle("petal-adblock-cosmetics");
            applyCssSelectors(response.hide_selectors, cosmeticStyleElement);
        }

        // Inject scriptlets into the page's main world
        if (Array.isArray(response.injected_scriptlets) && response.injected_scriptlets.length > 0) {
            for (let i = 0; i < response.injected_scriptlets.length; i++) {
                injectScriptletIntoMainWorld(response.injected_scriptlets[i]);
            }
        }
    });

    // 2. Batched DOM MutationObserver for hidden_class_id_selectors
    function flushBatchClassIdQuery() {
        if (pendingClasses.size === 0 && pendingIds.size === 0) return;

        const classes = Array.from(pendingClasses);
        const ids = Array.from(pendingIds);
        pendingClasses.clear();
        pendingIds.clear();

        api.runtime.sendMessage({
            type: "BATCH_CLASS_ID_SELECTORS",
            url: location.href,
            classes: classes,
            ids: ids
        }, function (response) {
            if (response && Array.isArray(response.hide_selectors) && response.hide_selectors.length > 0) {
                batchStyleElement = getOrCreateStyle("petal-adblock-batch-cosmetics");
                applyCssSelectors(response.hide_selectors, batchStyleElement);
            }
        });
    }

    function queueNodeTokens(node) {
        if (!node || node.nodeType !== 1) return; // ELEMENT_NODE only

        if (node.id) {
            pendingIds.add(node.id);
        }
        if (node.classList && node.classList.length > 0) {
            for (let i = 0; i < node.classList.length; i++) {
                pendingClasses.add(node.classList[i]);
            }
        }

        // Sample children
        if (node.children && node.children.length > 0 && node.children.length < 20) {
            for (let i = 0; i < node.children.length; i++) {
                queueNodeTokens(node.children[i]);
            }
        }
    }

    function startMutationObserver() {
        const observer = new MutationObserver(function (mutations) {
            for (let i = 0; i < mutations.length; i++) {
                const m = mutations[i];
                if (m.addedNodes && m.addedNodes.length > 0) {
                    for (let j = 0; j < m.addedNodes.length; j++) {
                        queueNodeTokens(m.addedNodes[j]);
                    }
                }
            }

            if (pendingClasses.size > 0 || pendingIds.size > 0) {
                if (!batchTimer) {
                    batchTimer = setTimeout(function () {
                        batchTimer = null;
                        flushBatchClassIdQuery();
                    }, 50); // 50ms batching window
                }
            }
        });

        const target = document.documentElement || document.body;
        if (target) {
            observer.observe(target, { childList: true, subtree: true });
        } else {
            document.addEventListener("DOMContentLoaded", function () {
                (document.documentElement || document.body)?.let(t => {
                    observer.observe(t, { childList: true, subtree: true });
                });
            }, { once: true });
        }
    }

    startMutationObserver();
})();
