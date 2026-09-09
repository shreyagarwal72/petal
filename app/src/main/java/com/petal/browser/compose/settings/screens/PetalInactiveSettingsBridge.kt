/*
 * PetalInactiveSettingsBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Java-interop bridge that presents InactiveSettingsScreen directly as a
 * standalone full-screen overlay (rather than nested inside the general
 * Settings hub), so the gear icon on the Inactive Tabs page can deep-link
 * straight into it.
 */

package com.petal.browser.compose.settings.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalInactiveSettingsBridge {

    @JvmStatic
    fun createInactiveSettingsView(
        activity: ComponentActivity,
        onBack: () -> Unit
    ): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId)
                    ?: com.petal.browser.ui.theme.defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)

                val appFont = remember(fontName) {
                    com.petal.browser.ui.theme.AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try {
                        com.petal.browser.ui.theme.ColorStyle.valueOf(styleName)
                    } catch (e: Exception) {
                        com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT
                    }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    InactiveSettingsScreen(onNavigateBack = onBack)
                }
            }
        }
    }
}
