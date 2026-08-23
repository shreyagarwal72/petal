package com.petal.browser.compose.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        isIncognito: Boolean,
        handler: PetalBottomNavHandler
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }
                var floatingTabBar by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                            "sp_floating_tab_bar" -> floatingTabBar = sp.getBoolean("sp_floating_tab_bar", true)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) {
                    try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.PETAL }
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

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
                            isIncognito = isIncognito,
                            isFloatingStyle = floatingTabBar,
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

    @JvmStatic
    fun bindBottomNav(
        composeView: ComposeView,
        activity: ComponentActivity,
        selectedTab: PetalNavTab,
        tabCount: Int,
        handler: PetalBottomNavHandler
    ) {
        bindBottomNav(composeView, activity, selectedTab, tabCount, false, handler)
    }
}
