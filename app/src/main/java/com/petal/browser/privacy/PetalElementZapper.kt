package com.petal.browser.privacy

import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.view.PetalGeckoView

/**
 * PetalElementZapper
 * ─────────────────────────────────────────────────────────────────────────
 * Waterfox-grade Interactive Element Picker ("Zapper").
 * Injects a touch-based interactive overlay allowing users to select
 * and permanently zap any element on the current web page.
 */
object PetalElementZapper {

    const val ZAPPER_SCHEME_PREFIX = "petal://zap-element"

    fun getZapperScript(): String {
        return """
            javascript:(function() {
                if (window.__petal_zapper_active__) {
                    const existing = document.getElementById('petal-zapper-bar');
                    if (existing) existing.remove();
                    if (window.__petal_zapper_cleanup__) window.__petal_zapper_cleanup__();
                    window.__petal_zapper_active__ = false;
                    return;
                }
                window.__petal_zapper_active__ = true;

                // Create top floating bar
                const bar = document.createElement('div');
                bar.id = 'petal-zapper-bar';
                bar.style.cssText = 'position:fixed;top:14px;left:14px;right:14px;z-index:2147483647;background:rgba(20,20,30,0.95);backdrop-filter:blur(16px);color:#ffffff;padding:12px 18px;border-radius:20px;box-shadow:0 12px 40px rgba(0,0,0,0.6);font-family:system-ui,-apple-system,sans-serif;font-size:14px;display:flex;align-items:center;justify-content:space-between;border:1px solid rgba(255,255,255,0.2);';
                bar.innerHTML = '<div style="display:flex;align-items:center;gap:10px;"><span style="font-size:18px;">⚡</span><div><div style="font-weight:700;letter-spacing:-0.2px;">Element Zapper</div><div style="font-size:12px;opacity:0.8;">Tap any element on page to zap</div></div></div><button id="petal-zapper-close" style="background:rgba(255,60,60,0.85);color:#fff;border:none;border-radius:12px;padding:8px 14px;font-weight:600;font-size:13px;cursor:pointer;">Cancel</button>';
                (document.body || document.documentElement).appendChild(bar);

                let highlightedEl = null;
                let prevOutline = '';
                let prevBg = '';

                function computeSelector(el) {
                    if (!el || el === document.body || el === document.documentElement) return '';
                    if (el.id && !/^[0-9]/.test(el.id)) {
                        return '#' + CSS.escape(el.id);
                    }
                    if (el.classList && el.classList.length > 0) {
                        const validClasses = Array.from(el.classList).filter(c => !c.startsWith('petal-') && !/^[0-9]/.test(c));
                        if (validClasses.length > 0) {
                            return el.tagName.toLowerCase() + '.' + validClasses.map(c => CSS.escape(c)).join('.');
                        }
                    }
                    const parent = el.parentElement;
                    if (parent && parent !== document.body) {
                        const parentSel = computeSelector(parent);
                        if (parentSel) {
                            const index = Array.from(parent.children).indexOf(el) + 1;
                            return parentSel + ' > ' + el.tagName.toLowerCase() + ':nth-child(' + index + ')';
                        }
                    }
                    return el.tagName.toLowerCase();
                }

                function clearHighlight() {
                    if (highlightedEl) {
                        highlightedEl.style.outline = prevOutline;
                        highlightedEl.style.backgroundColor = prevBg;
                        highlightedEl = null;
                    }
                }

                function highlight(el) {
                    if (!el || el === bar || bar.contains(el)) return;
                    if (el === highlightedEl) return;
                    clearHighlight();
                    highlightedEl = el;
                    prevOutline = el.style.outline;
                    prevBg = el.style.backgroundColor;
                    el.style.outline = '3px dashed #ff0055 !important';
                    el.style.backgroundColor = 'rgba(255, 0, 85, 0.25) !important';
                }

                function handlePointerMove(e) {
                    const target = document.elementFromPoint(e.clientX, e.clientY);
                    highlight(target);
                }

                function handleClick(e) {
                    if (e.target === bar || bar.contains(e.target)) return;
                    e.preventDefault();
                    e.stopPropagation();

                    const target = highlightedEl || e.target;
                    if (!target || target === document.body || target === document.documentElement) return;

                    const selector = computeSelector(target);
                    if (!selector) return;

                    const confirmZap = confirm("Zap and permanently hide this element on " + window.location.hostname + "?\n\nSelector:\n" + selector);
                    if (confirmZap) {
                        target.style.setProperty('display', 'none', 'important');
                        cleanup();
                        window.location.href = "petal://zap-element?domain=" + encodeURIComponent(window.location.hostname) + "&selector=" + encodeURIComponent(selector);
                    }
                }

                const closeBtn = document.getElementById('petal-zapper-close');
                if (closeBtn) {
                    closeBtn.addEventListener('click', (e) => {
                        e.stopPropagation();
                        cleanup();
                    });
                }

                document.addEventListener('pointermove', handlePointerMove, true);
                document.addEventListener('click', handleClick, true);

                function cleanup() {
                    clearHighlight();
                    if (bar.parentNode) bar.parentNode.removeChild(bar);
                    document.removeEventListener('pointermove', handlePointerMove, true);
                    document.removeEventListener('click', handleClick, true);
                    window.__petal_zapper_active__ = false;
                }
                window.__petal_zapper_cleanup__ = cleanup;
            })();
        """.trimIndent()
    }

    fun launch(albumController: AlbumController?) {
        val script = getZapperScript()
        when (albumController) {
            is PetalGeckoView -> albumController.evaluateJavascript(script)
            is PetalTabViewController -> albumController.evaluateJavascript(script)
            else -> {}
        }
    }
}
