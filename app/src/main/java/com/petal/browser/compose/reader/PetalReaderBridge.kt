/*
 * PetalReaderBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Modern bridge and native readability extraction engine for Petal Reader Mode.
 * Supports native GeckoView Reader Mode (about:reader) as well as offline
 * DOM-based article extraction for the native Material 3 Expressive reader view.
 */

package com.petal.browser.compose.reader

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
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
import com.petal.browser.browser.AlbumController
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.PetalAndroidUtils

import java.util.concurrent.ConcurrentHashMap

object PetalReaderBridge {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCallbacks = ConcurrentHashMap<String, (ReaderArticleData?) -> Unit>()
    private val eligibleUrls = ConcurrentHashMap<String, Boolean>()

    @JvmStatic
    @Volatile
    var readerEligibilityListener: ((url: String, isEligible: Boolean) -> Unit)? = null

    @JvmStatic
    fun isUrlEligible(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val clean = url.substringBefore('#').trimEnd('/')
        return eligibleUrls[url] == true || eligibleUrls[clean] == true
    }

    @JvmStatic
    fun onReaderEligibilityDetected(pageUrl: String, isEligible: Boolean) {
        if (pageUrl.isBlank()) return
        val clean = pageUrl.substringBefore('#').trimEnd('/')
        eligibleUrls[pageUrl] = isEligible
        eligibleUrls[clean] = isEligible
        mainHandler.post {
            readerEligibilityListener?.invoke(pageUrl, isEligible)
        }
    }

    @JvmStatic
    fun onArticleReceived(
        pageUrl: String,
        title: String,
        author: String,
        lead: String,
        text: String
    ) {
        val domain = PetalAndroidUtils.domain(pageUrl) ?: ""
        val article = ReaderArticleData(
            title = title.ifBlank { "Article" },
            author = author,
            domain = domain,
            leadImageUrl = lead,
            contentText = text
        )

        val cb = findAndRemoveCallback(pageUrl)
        if (cb != null) {
            mainHandler.post { cb(article) }
        }
    }

    private fun findAndRemoveCallback(pageUrl: String): ((ReaderArticleData?) -> Unit)? {
        pendingCallbacks.remove(pageUrl)?.let { return it }
        val clean = pageUrl.substringBefore('#').trimEnd('/')
        for (entry in pendingCallbacks.entries) {
            val keyClean = entry.key.substringBefore('#').trimEnd('/')
            if (clean == keyClean || clean.startsWith(keyClean) || keyClean.startsWith(clean)) {
                return pendingCallbacks.remove(entry.key)
            }
        }
        if (pendingCallbacks.size == 1) {
            val key = pendingCallbacks.keys.first()
            return pendingCallbacks.remove(key)
        }
        return null
    }

    @JvmStatic
    fun extractArticle(controller: AlbumController?, callback: (ReaderArticleData?) -> Unit) {
        val url = controller?.url
        if (controller == null || url.isNullOrBlank() || url.startsWith("about:") || url.startsWith("petal://")) {
            callback(null)
            return
        }

        val pageTitle = controller.title ?: "Article"
        val domain = PetalAndroidUtils.domain(url) ?: ""

        pendingCallbacks[url] = callback

        val context = (controller as? View)?.context
        val useMozillaReadability = if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_mozilla_readability", true)
        } else {
            true
        }

        // Robust Readability extractor (Mozilla Readability principles):
        // Stage 1 extracts OpenGraph/Twitter/Schema.org metadata (title, author, lead image).
        // Stage 2 clones candidate nodes, strips noise (scripts, styles, ads, nav, sidebars),
        // and collects structured paragraphs, headings, blockquotes, and lists.
        val extractScript = if (useMozillaReadability) {
            """
            (function() {
                try {
                    var getMeta = function(prop, name) {
                        var m = document.querySelector('meta[property="' + prop + '"]') || document.querySelector('meta[name="' + name + '"]');
                        return m ? (m.content || m.getAttribute('content') || '').trim() : '';
                    };

                    var title = getMeta('og:title', 'twitter:title');
                    if (!title) {
                        var h1 = document.querySelector('h1');
                        title = (h1 ? h1.innerText.trim() : document.title.trim()) || '';
                    }

                    var author = getMeta('article:author', 'author');
                    if (!author) {
                        var byEl = document.querySelector('[rel="author"], [itemprop="author"], .author, .byline, .article-author, .story-byline');
                        author = byEl ? byEl.innerText.trim() : '';
                    }

                    var leadImg = getMeta('og:image', 'twitter:image');
                    if (!leadImg) {
                        var firstImg = document.querySelector('article img, [itemprop="articleBody"] img, main img, .post-content img');
                        if (firstImg && firstImg.src && !firstImg.src.startsWith('data:')) {
                            leadImg = firstImg.src;
                        }
                    }

                    var unwanted = 'script, style, noscript, iframe, svg, nav, footer, header, aside, form, button, input, [role="banner"], [role="navigation"], [aria-hidden="true"], .advertisement, .ad-container, .social-share, .comments, .cookie-banner';
                    var sourceNode = document.querySelector('article, [itemprop="articleBody"], .entry-content, .post-content, main, .article-body, #content, .story-body, .article__body, .content') || document.body;
                    var clone = sourceNode.cloneNode(true);

                    var removeList = clone.querySelectorAll(unwanted);
                    for (var i = 0; i < removeList.length; i++) {
                        if (removeList[i].parentNode) {
                            removeList[i].parentNode.removeChild(removeList[i]);
                        }
                    }

                    var blocks = Array.from(clone.querySelectorAll('p, h2, h3, h4, blockquote, li'))
                        .map(function(node) {
                            var txt = (node.innerText || node.textContent || '').trim();
                            if (txt.length < 15) return '';
                            if (/^(share this|follow us|copyright|all rights reserved|sign up for|advertisement)/i.test(txt)) return '';
                            return txt;
                        })
                        .filter(function(txt) { return txt.length > 0; });

                    var cleanBlocks = [];
                    for (var b = 0; b < blocks.length; b++) {
                        if (b === 0 || blocks[b] !== blocks[b - 1]) {
                            cleanBlocks.push(blocks[b]);
                        }
                    }

                    var bodyText = cleanBlocks.length > 0 ? cleanBlocks.join('\n\n') : (clone.innerText || '');
                    var art = { title: title, author: author, lead: leadImg, text: bodyText };

                    if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
                        chrome.runtime.sendMessage({
                            type: 'READER_ARTICLE_DATA',
                            article: art,
                            pageUrl: window.location.href
                        }).catch(function() {});
                    }
                    window.postMessage({ type: 'PETAL_EXTRACT_ARTICLE', article: art }, '*');
                } catch(e) {
                    console.error('Petal Mozilla Readability error:', e);
                }
            })();
            """.trimIndent()
        } else {
            """
            (function() {
                try {
                    var el = document.querySelector('article, [itemprop="articleBody"], .entry-content, .post-content, main, .article-body, #content, .story-body, .article__body, .content') || document.body;
                    var t = (document.querySelector('h1') ? document.querySelector('h1').innerText.trim() : document.title.trim()) || '';
                    var by = (document.querySelector('[rel="author"], .author, .byline, [itemprop="author"], .article-author') ? document.querySelector('[rel="author"], .author, .byline, [itemprop="author"], .article-author').innerText.trim() : '') || '';
                    var img = document.querySelector('article img, [itemprop="image"], meta[property="og:image"]');
                    var lead = img ? (img.src || img.content || '') : '';
                    
                    var paragraphs = Array.from(el.querySelectorAll('p'))
                        .map(function(p) { return p.innerText.trim(); })
                        .filter(function(text) { return text.length > 20; });
                    
                    var bodyText = paragraphs.length > 0 ? paragraphs.join('\n\n') : (el.innerText || '');
                    var art = { title: t, author: by, lead: lead, text: bodyText };

                    if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
                        chrome.runtime.sendMessage({
                            type: 'READER_ARTICLE_DATA',
                            article: art,
                            pageUrl: window.location.href
                        }).catch(function() {});
                    }
                    window.postMessage({ type: 'PETAL_EXTRACT_ARTICLE', article: art }, '*');
                } catch(e) {
                    console.error('Reader extraction error:', e);
                }
            })();
            """.trimIndent()
        }

        when (controller) {
            is com.petal.browser.browser.PetalTabViewController -> controller.evaluateJavascript(extractScript)
        }

        // 1200ms fallback timeout: if WebExtension did not respond, construct a fallback article from page title
        mainHandler.postDelayed({
            val timedOutCb = pendingCallbacks.remove(url)
            if (timedOutCb != null) {
                // Return null so MainActivity triggers native Firefox about:reader?url=... fallback
                timedOutCb(null)
            }
        }, 1200L)
    }

    /**
     * Toggles native GeckoView Reader View (about:reader?url=...) on a PetalTabViewController instance.
     */
    @JvmStatic
    fun toggleNativeGeckoReader(controller: AlbumController?, active: Boolean) {
        when (controller) {
            is com.petal.browser.browser.PetalTabViewController -> controller.toggleReaderMode(active)
        }
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
