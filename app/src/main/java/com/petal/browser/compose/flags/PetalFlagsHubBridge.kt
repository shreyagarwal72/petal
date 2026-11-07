package com.petal.browser.compose.flags

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme

/**
 * PetalFlagsHubBridge
 * ─────────────────────────────────────────────────────────────────────────
 * Dynamic ComposeView bridge presenting PetalFlagsHubScreen inside BrowserActivity.
 */
object PetalFlagsHubBridge {

    @JvmStatic
    @JvmOverloads
    fun createFlagsHubView(
        activity: ComponentActivity,
        onBack: () -> Unit,
        backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        if (backgroundSnapshot == null) {
            com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        }
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val snapshot = backgroundSnapshot ?: remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose {
                        if (backgroundSnapshot == null) {
                            com.petal.browser.predictive.PetalContentSnapshot.clear()
                        }
                    }
                }
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
                    } catch (_: Exception) {
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
                    PetalFlagsHubScreen(
                        onDismiss = onBack,
                        backgroundSnapshot = snapshot
                    )
                }
            }
        }
    }
}
