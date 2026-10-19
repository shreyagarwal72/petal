package com.petal.browser.compose.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.wallpaper.PetalAnimatedWallpaperBackground
import com.petal.browser.wallpaper.PetalWallpaperManager

/**
 * Draws the set wallpaper (or the glass background image, or the living Material background)
 * behind the page, full screen. Flat liquid-glass bars (non-floating bottom nav, bottom address
 * bar) point their glass at this view, so they show the wallpaper instead of the web page.
 *
 * The layer choice mirrors the home screen's layer 0 so both look the same.
 */
object PetalGlassBackdropBridge {
    @JvmStatic
    fun bind(composeView: ComposeView, activity: ComponentActivity) {
        if (composeView.tag == "PetalGlassBackdropBound") return
        composeView.tag = "PetalGlassBackdropBound"
        // The home screen normally loads these; do it here too so a web page never sees a blank backdrop.
        try {
            com.petal.browser.profile.PetalProfileManager.init(activity)
            PetalWallpaperManager.init(activity)
        } catch (_: Throwable) {}
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PetalExpressiveTheme {
                    val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                    // Same source of truth the home screen reads.
                    val activeUri = PetalWallpaperManager.wallpaperUri
                    val glassBgMode = sp.getString("sp_liquid_glass_bg_mode", "STRIDE") ?: "STRIDE"
                    val glassImageUri = sp.getString("sp_liquid_glass_bg_image_uri", "").orEmpty()
                    val uri = when {
                        !activeUri.isNullOrBlank() -> activeUri
                        glassBgMode == "IMAGE" && glassImageUri.isNotBlank() -> glassImageUri
                        else -> null
                    }
                    Box(Modifier.fillMaxSize()) {
                        if (!uri.isNullOrBlank()) {
                            val usingManager = !activeUri.isNullOrBlank()
                            PetalAnimatedWallpaperBackground(
                                wallpaperUri = uri,
                                dim = if (usingManager) PetalWallpaperManager.wallpaperDim
                                else sp.getFloat("sp_liquid_glass_bg_dim", 0.35f),
                                blur = if (usingManager) PetalWallpaperManager.wallpaperBlur
                                else sp.getFloat("sp_liquid_glass_bg_blur", 16f),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            M3ExpressiveVariableBackground(
                                modifier = Modifier.fillMaxSize(),
                                pageSeed = "home_page"
                            )
                        }
                    }
                }
            }
        }
    }
}
