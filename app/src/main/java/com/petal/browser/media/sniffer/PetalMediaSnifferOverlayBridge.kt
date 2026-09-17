package com.petal.browser.media.sniffer

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import com.petal.browser.activity.MediaPlayerActivity
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalMediaSnifferOverlayBridge {

    /** The URL of the currently visible page — updated by BrowserActivity on every navigation. */
    @JvmStatic
    val currentPageUrl = mutableStateOf("")

    /** Call from BrowserActivity (or tab switch events) to keep the Social Download section current. */
    @JvmStatic
    fun setCurrentPageUrl(url: String) {
        currentPageUrl.value = url
    }

    @JvmStatic
    fun bind(view: ComposeView, activity: ComponentActivity) {
        view.setViewTreeLifecycleOwner(activity)
        view.setViewTreeViewModelStoreOwner(activity)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            var themeName by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }
            var dynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", true)) }
            var expressive by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
            val darkTheme = if (themeName == "LIGHT") false else if (themeName == "DARK") true else dark
            val pageUrl by currentPageUrl
            PetalExpressiveTheme(
                darkTheme        = darkTheme,
                dynamicColor     = dynamic,
                expressiveColors = expressive,
                appFont          = AppFont.fromName(sp.getString("sp_app_font", "PETAL") ?: "PETAL"),
                fontWidth        = sp.getFloat("sp_font_width", 92f),
                fontWeight       = sp.getInt("sp_font_weight", 750),
                fontRoundness    = sp.getFloat("sp_font_roundness", 100f),
                colorStyle       = runCatching {
                    ColorStyle.valueOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT")
                }.getOrDefault(ColorStyle.TONAL_SPOT),
                paletteId        = sp.getString("sp_palette_id",
                    com.petal.browser.ui.theme.defaultPaletteId)
                    ?: com.petal.browser.ui.theme.defaultPaletteId
            ) {
                PetalMediaSnifferOverlay(
                    context        = activity,
                    currentPageUrl = pageUrl
                ) { request ->
                    val act = activity as? com.petal.browser.activity.BrowserActivity
                    val geckoView = act?.currentAlbumController as? com.petal.browser.view.PetalGeckoView
                    geckoView?.captureVideoHandoffState { handoff ->
                        val intent = Intent(activity, MediaPlayerActivity::class.java).apply {
                            data = android.net.Uri.parse(request.url)
                            type = request.mimeType
                            if (handoff != null) {
                                putExtra(com.petal.browser.media.handoff.MediaHandoff.EXTRA_HANDOFF_POSITION_MS, handoff.positionMs)
                                putExtra(com.petal.browser.media.handoff.MediaHandoff.EXTRA_HANDOFF_SPEED, handoff.playbackSpeed)
                                putExtra(com.petal.browser.media.handoff.MediaHandoff.EXTRA_HANDOFF_IS_PAUSED, handoff.isPaused)
                            }
                        }
                        activity.startActivity(intent)
                    } ?: run {
                        activity.startActivity(
                            Intent(activity, MediaPlayerActivity::class.java).apply {
                                data = android.net.Uri.parse(request.url)
                                type = request.mimeType
                            }
                        )
                    }
                }
            }
        }
    }
}
