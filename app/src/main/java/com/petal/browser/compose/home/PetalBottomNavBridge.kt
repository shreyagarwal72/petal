package com.petal.browser.compose.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.PetalBottomNavBar
import com.petal.browser.ui.components.PetalNavTab
import com.petal.browser.ui.theme.PetalExpressiveTheme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.preference.PreferenceManager
import androidx.compose.runtime.remember
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle

interface PetalBottomNavHandler {
    fun onHomeClick()
    fun onNewTabClick()
    fun onTabsClick()
    fun onMenuClick()
}

object PetalBottomNavBridge {
    @JvmStatic
    fun bindBottomNav(
        composeView: ComposeView,
        activity: ComponentActivity,
        selectedTab: PetalNavTab,
        tabCount: Int,
        handler: PetalBottomNavHandler
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.SYSTEM }
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        PetalBottomNavBar(
                            selectedTab = selectedTab,
                            tabCount = tabCount,
                            onHomeClick = { handler.onHomeClick() },
                            onNewTabClick = { handler.onNewTabClick() },
                            onTabsClick = { handler.onTabsClick() },
                            onMenuClick = { handler.onMenuClick() }
                        )
                    }
                }
            }
        }
    }
}
