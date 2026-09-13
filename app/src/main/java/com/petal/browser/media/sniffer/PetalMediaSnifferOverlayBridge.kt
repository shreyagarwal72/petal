package com.petal.browser.media.sniffer

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import com.petal.browser.activity.MediaPlayerActivity
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import androidx.compose.runtime.*

object PetalMediaSnifferOverlayBridge {
    @JvmStatic
    fun bind(view: ComposeView, activity: ComponentActivity) {
        view.setViewTreeLifecycleOwner(activity)
        view.setViewTreeViewModelStoreOwner(activity)
        view.setViewTreeSavedStateRegistryOwner(activity)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            var themeName by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }
            var dynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", true)) }
            var expressive by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
            val darkTheme = if (themeName == "LIGHT") false else if (themeName == "DARK") true else dark
            PetalExpressiveTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamic,
                expressiveColors = expressive,
                appFont = AppFont.fromName(sp.getString("sp_app_font", "PETAL") ?: "PETAL"),
                fontWidth = sp.getFloat("sp_font_width", 92f),
                fontWeight = sp.getInt("sp_font_weight", 750),
                fontRoundness = sp.getFloat("sp_font_roundness", 100f),
                colorStyle = runCatching { ColorStyle.valueOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }.getOrDefault(ColorStyle.TONAL_SPOT),
                paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
            ) {
                PetalMediaSnifferOverlay(activity) { request ->
                    activity.startActivity(Intent(activity, MediaPlayerActivity::class.java).apply {
                        data = android.net.Uri.parse(request.url)
                        type = request.mimeType
                    })
                }
            }
        }
    }
}
