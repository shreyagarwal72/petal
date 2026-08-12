package com.petal.browser.compose.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.PetalAddressBar
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalAddressBarBridge {
    @JvmStatic
    @JvmOverloads
    fun bindAddressBar(
        composeView: ComposeView,
        activity: ComponentActivity,
        url: String,
        title: String,
        isIncognito: Boolean = false,
        onBackClick: Runnable,
        onShareClick: Runnable,
        onAddressClick: Runnable
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", "tide") ?: "tide"
                val dynamicColor = sp.getBoolean("useDynamicColor", false)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.SYSTEM }
                val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalAddressBar(
                        url = url,
                        title = title,
                        isIncognito = isIncognito,
                        onBackClick = { onBackClick.run() },
                        onShareClick = { onShareClick.run() },
                        onAddressClick = { onAddressClick.run() }
                    )
                }
            }
        }
    }
}
