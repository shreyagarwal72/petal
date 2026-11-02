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
import com.petal.browser.view.PetalGeckoView

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

        val title = controller.title ?: "Article"
        val domain = PetalAndroidUtils.domain(url) ?: ""

        pendingCallbacks[url] = callback

        // Post message to page DOM so the WebExtension content script extracts the article
        val postMessageScript = "window.postMessage({ type: 'PETAL_EXTRACT_ARTICLE' }, '*');"
        when (controller) {
            is PetalGeckoView -> controller.evaluateJavascript(postMessageScript)
            is com.petal.browser.browser.PetalTabViewController -> controller.evaluateJavascript(postMessageScript)
        }

        // 1500ms fallback timeout if content script didn't respond
        mainHandler.postDelayed({
            val timedOutCb = pendingCallbacks.remove(url)
            if (timedOutCb != null) {
                // If WebExtension hasn't extracted yet, return null so it falls back to native Firefox about:reader
                timedOutCb(null)
            }
        }, 1500L)
    }

    /**
     * Toggles native GeckoView Reader View (about:reader?url=...) on a PetalGeckoView or PetalTabViewController instance.
     */
    @JvmStatic
    fun toggleNativeGeckoReader(controller: AlbumController?, active: Boolean) {
        when (controller) {
            is PetalGeckoView -> controller.toggleReaderMode(active)
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
