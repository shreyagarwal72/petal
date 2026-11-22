package com.petal.browser.engine.gecko

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.graphics.ColorUtils
import com.petal.browser.ui.theme.PetalViewThemeBridge
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.concept.engine.request.ErrorType
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Themes Mozilla's error pages (DNS, certificate, connection refused, blocked content, ...) with the
 * live Petal Material theme (dark, AMOLED, dynamic color), keeping Mozilla's markup, localized text
 * and the certificate "Advanced" details untouched: only a <style> block and a small retry script
 * are injected. Falls back to Mozilla's own page whenever anything goes wrong.
 *
 * The offline game hook runs before this (see PetalEngineStore) and is not affected.
 */
object PetalErrorPageTheme {

    private const val TAG = "PetalErrorPageTheme"

    /** Returns a data: URI for the themed error page of [errorType] / [failedUrl]. */
    fun createErrorPageUri(context: Context, errorType: ErrorType, failedUrl: String?): String {
        val original = ErrorPages.createUrlEncodedErrorPage(context, errorType, failedUrl)
        return try {
            val html = decodeDataUri(original) ?: return original
            val css = buildCss(context) ?: return original
            val themed = inject(html, css, buildRetryScript(failedUrl))
            "data:text/html;charset=utf-8;base64," +
                Base64.encodeToString(themed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not theme error page, using Mozilla's", t)
            original
        }
    }

    private fun decodeDataUri(uri: String): String? {
        val comma = uri.indexOf(',')
        if (!uri.startsWith("data:") || comma < 0) return null
        val header = uri.substring(0, comma)
        val payload = uri.substring(comma + 1)
        return if (header.contains(";base64", ignoreCase = true)) {
            String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
        } else {
            URLDecoder.decode(payload, "UTF-8")
        }
    }

    private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

    private fun buildCss(context: Context): String? {
        fun token(name: String) = PetalViewThemeBridge.tokenColor(context, name)
        val surface = token("surface") ?: return null
        val onSurface = token("onSurface") ?: return null
        val onSurfaceVariant = token("onSurfaceVariant") ?: onSurface
        val primary = token("primary") ?: return null
        val onPrimary = token("onPrimary") ?: return null
        val card = token("surfaceContainerHigh") ?: surface
        val error = token("error") ?: primary
        val dark = ColorUtils.calculateLuminance(surface) < 0.5
        return """
            :root { color-scheme: ${if (dark) "dark" else "light"}; }
            html, body { background: ${hex(surface)} !important; color: ${hex(onSurface)} !important; }
            h1, h2, h3, .title, .title-text { color: ${hex(onSurface)} !important; }
            p, li, span, label, summary, #errorShortDesc, #errorLongDesc, .description { color: ${hex(onSurfaceVariant)} !important; }
            a { color: ${hex(primary)} !important; }
            ul, details, .advanced-panel, #advancedPanel { background: ${hex(card)} !important; border: none !important; border-radius: 16px !important; }
            button, .button, a.button, #refreshButton, #petalRetry, #advancedButton {
                background: ${hex(primary)} !important; color: ${hex(onPrimary)} !important; border: none !important;
                border-radius: 999px !important; min-height: 48px; padding: 0 24px; font-weight: 600;
            }
            svg, img { color: ${hex(error)}; }
        """.trimIndent()
    }

    private fun buildRetryScript(failedUrl: String?): String {
        if (failedUrl.isNullOrBlank() || Uri.parse(failedUrl).scheme.isNullOrBlank()) return ""
        val quoted = JSONObject.quote(failedUrl)
        return """
            (function () {
              var failedUrl = $quoted;
              document.addEventListener('click', function (e) {
                var t = e.target && e.target.closest ? e.target.closest('#refreshButton, #petalRetry') : null;
                if (t) { e.preventDefault(); e.stopImmediatePropagation(); location.href = failedUrl; }
              }, true);
              document.addEventListener('DOMContentLoaded', function () {
                if (document.getElementById('refreshButton') || !document.body) return;
                var b = document.createElement('button');
                b.id = 'petalRetry';
                b.textContent = 'Try again';
                b.style.margin = '16px 0';
                document.body.appendChild(b);
              });
            })();
        """.trimIndent()
    }

    private fun inject(html: String, css: String, script: String): String {
        val style = "<style id=\"petal-theme\">$css</style>"
        val scriptTag = if (script.isEmpty()) "" else "<script>$script</script>"
        val withStyle = Regex("</head>", RegexOption.IGNORE_CASE).find(html)?.let {
            html.substring(0, it.range.first) + style + html.substring(it.range.first)
        } ?: (style + html)
        return Regex("</body>", RegexOption.IGNORE_CASE).find(withStyle)?.let {
            withStyle.substring(0, it.range.first) + scriptTag + withStyle.substring(it.range.first)
        } ?: (withStyle + scriptTag)
    }
}
