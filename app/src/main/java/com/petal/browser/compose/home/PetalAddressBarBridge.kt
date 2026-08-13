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
        isLoading: Boolean = false,
        canGoBack: Boolean = true,
        onBackClick: Runnable,
        onShareClick: Runnable,
        onAddressClick: Runnable,
        onSiteControlsClick: Runnable? = null
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.SYSTEM }
                val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }

                val fontWidthVal = sp.getFloat("sp_font_width", 100f)
                val fontWeightVal = sp.getInt("sp_font_weight", 400)
                val fontRoundnessVal = sp.getFloat("sp_font_roundness", 0f)

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalAddressBar(
                        url = url,
                        title = title,
                        isIncognito = isIncognito,
                        isLoading = isLoading,
                        canGoBack = canGoBack,
                        onBackClick = { onBackClick.run() },
                        onShareClick = { onShareClick.run() },
                        onAddressClick = { onAddressClick.run() },
                        onSiteControlsClick = { onSiteControlsClick?.run() }
                    )
                }
            }
        }
    }
}
