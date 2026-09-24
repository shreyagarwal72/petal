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
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.ReaderModeManager
import com.petal.browser.view.PetalGeckoView

object PetalReaderBridge {

    @JvmStatic
    fun extractArticle(controller: AlbumController?, callback: (ReaderArticleData?) -> Unit) {
        val url = controller?.url
        if (controller == null || url.isNullOrBlank() || url.startsWith("about:") || url.startsWith("petal://")) {
            callback(null)
            return
        }

        val title = controller.title ?: "Article"
        val domain = HelperUnit.domain(url) ?: ""

        // Prefer offline extraction directly via ReaderModeManager
        ReaderModeManager.parseArticle(url) { parsed ->
            if (parsed != null && parsed.contentHtml.isNotBlank()) {
                val cleanText = parsed.contentHtml.replace(Regex("<[^>]*>"), " ").trim()
                callback(ReaderArticleData(parsed.title.ifEmpty { title }, parsed.author, parsed.domain.ifEmpty { domain }, parsed.leadImageUrl, cleanText))
            } else {
                callback(ReaderArticleData(title, "", domain, "", "Unable to extract article text from this page."))
            }
        }
    }

    /**
     * Toggles native GeckoView Reader View (about:reader?url=...) on a PetalGeckoView instance.
     */
    @JvmStatic
    fun toggleNativeGeckoReader(geckoView: PetalGeckoView, active: Boolean) {
        geckoView.toggleReaderMode(active)
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
