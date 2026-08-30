/*
 * PetalAccessibilityEngine.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Comprehensive Accessibility & Browser Engine backend for Petal Browser.
 * Provides:
 * 1. Per-site Zoom Level storage & retrieval via SharedPreferences
 * 2. Text Zoom & Page Zoom integration with WebSettings
 * 3. Viewport Zoom Lock Overrides (Force Zoom via JS & WebSettings)
 * 4. Reader Mode / Simplified View Detection & Bridge
 * 5. Caret Browsing Mode with F7 Keyboard Shortcut
 * 6. System Captioning Settings Launcher
 * 7. Two-finger Touchpad Horizontal Swipe for History Navigation
 */

package com.petal.browser.accessibility

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.preference.PreferenceManager
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.NinjaWebView

object PetalAccessibilityEngine {

    private const val TAG = "PetalAccessibility"
    const val JS_BRIDGE_NAME = "PetalAccessibilityBridge"

    // ── 1. Per-Site Zoom Management ──────────────────────────────────────────

    @JvmStatic
    fun getSiteZoom(context: Context, url: String?): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultScale = sp.getFloat("sp_zoom_level_scale", 1.0f)
        val defaultTextScale = sp.getFloat("sp_font_size_scale", 1.0f)
        val legacyFontSize = try {
            val legacyStr = HelperUnit.getSafeString(sp, "sp_fontSize", "100")
            legacyStr.toInt().toFloat() / 100f
        } catch (e: Exception) {
            1.0f
        }
        val effectiveTextScale = if (sp.contains("sp_font_size_scale")) defaultTextScale else legacyFontSize
        val baseDefaultZoom = (defaultScale * effectiveTextScale * 100).toInt().coerceIn(50, 300)

        val domain = getCleanDomain(url) ?: return baseDefaultZoom
        return sp.getInt("sp_site_zoom_${domain}", baseDefaultZoom)
    }

    @JvmStatic
    fun setSiteZoom(context: Context, url: String?, zoomPercent: Int) {
        val domain = getCleanDomain(url) ?: return
        val clamped = zoomPercent.coerceIn(50, 300)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putInt("sp_site_zoom_${domain}", clamped).apply()
    }

    @JvmStatic
    fun applyZoomToWebView(webView: WebView?, url: String?) {
        if (webView == null) return
        val context = webView.context ?: return
        val zoom = getSiteZoom(context, url ?: webView.url)
        try {
            webView.settings.textZoom = zoom
            webView.settings.setSupportZoom(true)
            webView.settings.builtInZoomControls = true
            webView.settings.displayZoomControls = false
        } catch (e: Exception) {
            Log.w(TAG, "applyZoomToWebView error: ${e.message}")
        }
    }

    private fun getCleanDomain(url: String?): String? {
        if (url.isNullOrBlank() || url.startsWith("about:") || url.startsWith("petal://") || url.startsWith("file://")) {
            return null
        }
        return try {
            HelperUnit.domain(url)
        } catch (e: Exception) {
            null
        }
    }

    // ── 2. Force Viewport Zoom Override ──────────────────────────────────────

    @JvmStatic
    fun isForceZoomEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_force_enable_zoom", true)
    }

    @JvmStatic
    fun applyForceZoom(webView: WebView?) {
        if (webView == null) return
        val context = webView.context ?: return
        if (!isForceZoomEnabled(context)) return

        try {
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.setSupportZoom(true)
            webView.settings.builtInZoomControls = true
            webView.settings.displayZoomControls = false

            val js = """
                (function() {
                    try {
                        var meta = document.querySelector('meta[name="viewport"]');
                        if (meta) {
                            var content = meta.getAttribute('content') || '';
                            var updated = content
                                .replace(/user-scalable\s*=\s*(no|0)/gi, 'user-scalable=yes')
                                .replace(/maximum-scale\s*=\s*(1(\.0+)?)(\b|\s*,)/gi, 'maximum-scale=5.0$3');
                            if (updated !== content) {
                                meta.setAttribute('content', updated);
                            }
                        }
                    } catch(e) {}
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        } catch (e: Exception) {
            Log.w(TAG, "applyForceZoom error: ${e.message}")
        }
    }

    // ── 3. Reader Mode / Simplified View Detection ──────────────────────────

    @JvmStatic
    fun isReaderModeDetectionEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_reader_mode_detection", true)
    }

    @JvmStatic
    fun injectReaderModeDetector(webView: WebView?) {
        if (webView == null) return
        val context = webView.context ?: return
        if (!isReaderModeDetectionEnabled(context)) return

        val js = """
            (function() {
                try {
                    var article = document.querySelector('article, [role="article"], .post-content, .article-body');
                    var paragraphs = document.querySelectorAll('p');
                    var textLength = 0;
                    for (var i = 0; i < paragraphs.length; i++) {
                        textLength += (paragraphs[i].innerText || '').length;
                    }
                    if (article || (paragraphs.length >= 3 && textLength > 350)) {
                        if (window.$JS_BRIDGE_NAME) {
                            window.$JS_BRIDGE_NAME.onReaderModeAvailable(document.title || '');
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ── 4. Caret Browsing (with F7 Keyboard Support) ─────────────────────────

    @JvmStatic
    fun isCaretBrowsingEnabled(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean("sp_caret_browsing", false)
    }

    @JvmStatic
    fun toggleCaretBrowsing(context: Context, webView: WebView?): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val current = sp.getBoolean("sp_caret_browsing", false)
        val newState = !current
        sp.edit().putBoolean("sp_caret_browsing", newState).apply()
        applyCaretBrowsing(webView, newState)
        return newState
    }

    @JvmStatic
    fun setCaretBrowsing(context: Context, webView: WebView?, enabled: Boolean) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean("sp_caret_browsing", enabled).apply()
        applyCaretBrowsing(webView, enabled)
    }

    @JvmStatic
    fun applyCaretBrowsing(webView: WebView?, enabled: Boolean) {
        if (webView == null) return
        if (enabled) {
            val js = """
                (function() {
                    try {
                        document.designMode = 'on';
                        window.__petal_caret_mode = true;
                    } catch(e) {}
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        } else {
            val js = """
                (function() {
                    try {
                        if (window.__petal_caret_mode) {
                            document.designMode = 'off';
                            window.__petal_caret_mode = false;
                        }
                    } catch(e) {}
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    // ── 5. System Caption Settings Launcher ─────────────────────────────────

    @JvmStatic
    fun launchCaptionSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_CAPTIONING_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
            } catch (ignored: Exception) {
                Log.e(TAG, "Cannot launch caption settings: ${ignored.message}")
            }
        }
    }

    // ── 6. Two-Finger Touchpad Swipe for History Navigation ──────────────────

    private var lastSwipeTime = 0L

    @JvmStatic
    fun handleGenericMotion(webView: NinjaWebView, event: MotionEvent): Boolean {
        val context = webView.context ?: return false
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = sp.getBoolean("sp_touchpad_swipe_nav", true)
        if (!enabled) return false

        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            val now = System.currentTimeMillis()
            if (now - lastSwipeTime < 600L) return false

            if (hScroll < -0.65f) {
                // Swipe Left -> Go Back
                if (webView.canGoBack()) {
                    lastSwipeTime = now
                    webView.goBack()
                    return true
                }
            } else if (hScroll > 0.65f) {
                // Swipe Right -> Go Forward
                if (webView.canGoForward()) {
                    lastSwipeTime = now
                    webView.goForward()
                    return true
                }
            }
        }
        return false
    }

    // ── 7. JS Interface for Accessibility ────────────────────────────────────

    class AccessibilityJavascriptInterface(
        private val onReaderAvailable: (String) -> Unit
    ) {
        @JavascriptInterface
        fun onReaderModeAvailable(title: String) {
            onReaderAvailable(title)
        }
    }
}
