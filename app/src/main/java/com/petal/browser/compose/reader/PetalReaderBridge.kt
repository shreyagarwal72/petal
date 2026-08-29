/*
 * PetalReaderBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Java-interop bridge and client-side readability extraction engine for
 * Petal Reader Mode.
 */

package com.petal.browser.compose.reader

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.HelperUnit
import org.json.JSONObject

object PetalReaderBridge {

    private const val JS_INTERFACE_NAME = "PetalReaderExtractorBridge"

    @JvmStatic
    fun extractArticle(webView: WebView?, callback: (ReaderArticleData?) -> Unit) {
        if (webView == null || webView.url.isNullOrBlank()) {
            callback(null)
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var callbackExecuted = false

        // Timeout fallback
        mainHandler.postDelayed({
            if (!callbackExecuted) {
                callbackExecuted = true
                val fallbackTitle = webView.title ?: "Article"
                val fallbackDomain = HelperUnit.domain(webView.url) ?: ""
                callback(ReaderArticleData(fallbackTitle, "", fallbackDomain, "", "Unable to extract article text from this page."))
            }
        }, 2000L)

        val jsInterface = object {
            @JavascriptInterface
            fun onArticleExtracted(jsonStr: String) {
                mainHandler.post {
                    if (!callbackExecuted) {
                        callbackExecuted = true
                        try {
                            val json = JSONObject(jsonStr)
                            val title = json.optString("title", webView.title ?: "Article")
                            val author = json.optString("author", "")
                            val domain = json.optString("domain", HelperUnit.domain(webView.url) ?: "")
                            val leadImageUrl = json.optString("leadImageUrl", "")
                            val contentText = json.optString("contentText", "")
                            callback(ReaderArticleData(title, author, domain, leadImageUrl, contentText))
                        } catch (e: Exception) {
                            callback(null)
                        }
                    }
                }
            }
        }

        webView.addJavascriptInterface(jsInterface, JS_INTERFACE_NAME)

        val js = """
            (function() {
                try {
                    // 1. Title
                    var title = document.title || '';
                    var h1 = document.querySelector('h1');
                    if (h1 && h1.innerText && h1.innerText.length > 5) {
                        title = h1.innerText.trim();
                    }

                    // 2. Author / Byline
                    var author = '';
                    var authorMeta = document.querySelector('meta[name="author"], meta[property="article:author"], [rel="author"], .author, .byline');
                    if (authorMeta) {
                        author = authorMeta.getAttribute('content') || authorMeta.innerText || '';
                    }

                    // 3. Domain
                    var domain = window.location.hostname || '';

                    // 4. Lead Image
                    var leadImageUrl = '';
                    var ogImage = document.querySelector('meta[property="og:image"]');
                    if (ogImage) {
                        leadImageUrl = ogImage.getAttribute('content') || '';
                    }
                    if (!leadImageUrl) {
                        var firstImg = document.querySelector('article img, .post img, main img');
                        if (firstImg && firstImg.src && !firstImg.src.endsWith('.svg')) {
                            leadImageUrl = firstImg.src;
                        }
                    }

                    // 5. Clone and clean main content
                    var target = document.querySelector('article, [role="article"], main, .post-content, .article-body, .entry-content');
                    if (!target) {
                        target = document.body;
                    }
                    var clone = target.cloneNode(true);

                    // Strip clutter elements
                    var toRemove = clone.querySelectorAll('script, style, noscript, nav, header, footer, aside, .ad, .ads, .advertisement, .sidebar, .comments, .social-share, form, iframe, button, [role="banner"], [role="navigation"]');
                    for (var i = 0; i < toRemove.length; i++) {
                        toRemove[i].remove();
                    }

                    // Extract text blocks
                    var paragraphs = clone.querySelectorAll('p, h2, h3, h4, blockquote, li');
                    var textBlocks = [];
                    for (var j = 0; j < paragraphs.length; j++) {
                        var pText = (paragraphs[j].innerText || '').trim();
                        if (pText.length > 20) {
                            textBlocks.push(pText);
                        }
                    }

                    var bodyText = textBlocks.join('\n\n');
                    if (bodyText.length < 50) {
                        bodyText = (clone.innerText || '').trim();
                    }

                    var result = {
                        title: title,
                        author: author.trim(),
                        domain: domain,
                        leadImageUrl: leadImageUrl,
                        contentText: bodyText
                    };

                    window.$JS_INTERFACE_NAME.onArticleExtracted(JSON.stringify(result));
                } catch(e) {
                    window.$JS_INTERFACE_NAME.onArticleExtracted(JSON.stringify({
                        title: document.title || '',
                        domain: window.location.hostname || '',
                        contentText: document.body ? document.body.innerText : ''
                    }));
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    @JvmStatic
    fun createReaderView(
        activity: ComponentActivity,
        article: ReaderArticleData,
        onBack: () -> Unit
    ): ComposeView {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: activity.window.decorView
        PetalContentSnapshot.capture(rootView)

        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val snapshotBitmap = remember { PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        PetalContentSnapshot.clear()
                    }
                }

                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)

                val appFont = remember(fontName) {
                    com.petal.browser.ui.theme.AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalReaderScreen(
                        backgroundSnapshot = snapshotBitmap,
                        article = article,
                        onBack = onBack
                    )
                }
            }
        }
    }
}
