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
    private var _selectedTabState = androidx.compose.runtime.mutableStateOf(PetalNavTab.HOME)
    private var _tabCountState = androidx.compose.runtime.mutableIntStateOf(1)
    private var _isIncognitoState = androidx.compose.runtime.mutableStateOf(false)
    private var _navHandler: PetalBottomNavHandler? = null

    @JvmStatic
    fun bindBottomNav(
        composeView: ComposeView,
        activity: ComponentActivity,
        selectedTab: PetalNavTab,
        tabCount: Int,
        isIncognito: Boolean,
        handler: PetalBottomNavHandler
    ) {
        _selectedTabState.value = selectedTab
        _tabCountState.intValue = tabCount
        _isIncognitoState.value = isIncognito
        _navHandler = handler

        // If ComposeView already has content set, state updates above will trigger recomposition without rebuilding ViewTree
        if (composeView.tag == "PetalBottomNavBound") {
            return
        }
        composeView.tag = "PetalBottomNavBound"

        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentTab by _selectedTabState
                val currentCount by _tabCountState
                val currentIncognito by _isIncognitoState
                val currentHandler = _navHandler

                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
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
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
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
                    AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    expressiveColors = isExpressiveColors,
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
                            selectedTab = currentTab,
                            tabCount = currentCount,
                            isIncognito = currentIncognito,
                            isFloatingStyle = floatingTabBar,
                            onHomeClick = { currentHandler?.onHomeClick() },
                            onNewTabClick = { currentHandler?.onNewTabClick() },
                            onTabsClick = { currentHandler?.onTabsClick() },
                            onMenuClick = { currentHandler?.onMenuClick() }
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
