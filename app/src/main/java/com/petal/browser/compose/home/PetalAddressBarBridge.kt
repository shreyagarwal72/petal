package com.petal.browser.compose.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.petal.browser.ui.components.PetalAddressBar
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalAddressBarBridge {
    private val _urlState = mutableStateOf("")
    private val _titleState = mutableStateOf("")
    private val _faviconState = mutableStateOf<android.graphics.Bitmap?>(null)
    private val _progressState = mutableFloatStateOf(0f)
    private val _isIncognitoState = mutableStateOf(false)
    private val _isLoadingState = mutableStateOf(false)
    private val _canGoBackState = mutableStateOf(true)
    private val _onBackClickState = mutableStateOf<Runnable?>(null)
    private val _onShareClickState = mutableStateOf<Runnable?>(null)
    private val _onAddressClickState = mutableStateOf<Runnable?>(null)
    private val _onSiteControlsClickState = mutableStateOf<Runnable?>(null)
    private val _onAiResearchClickState = mutableStateOf<Runnable?>(null)
    private val _onSwipeNextTabState = mutableStateOf<Runnable?>(null)
    private val _onSwipePrevTabState = mutableStateOf<Runnable?>(null)
    private val _onPasteAndGoState = mutableStateOf<((String) -> Unit)?>(null)
    private val _onHardRefreshState = mutableStateOf<Runnable?>(null)

    @JvmStatic
    fun updateProgressValue(progress: Int) {
        val frac = (progress.coerceIn(0, 100)) / 100f
        _progressState.floatValue = frac
        _isLoadingState.value = progress < 100
    }

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
        onSiteControlsClick: Runnable? = null,
        onAiResearchClick: Runnable? = null,
        favicon: android.graphics.Bitmap? = null,
        progress: Float = 0f,
        onSwipeNextTab: Runnable? = null,
        onSwipePrevTab: Runnable? = null,
        onPasteAndGo: ((String) -> Unit)? = null,
        onHardRefresh: Runnable? = null
    ) {
        _urlState.value = url
        _titleState.value = title
        _faviconState.value = favicon
        _progressState.floatValue = progress
        _isIncognitoState.value = isIncognito
        _isLoadingState.value = isLoading
        _canGoBackState.value = canGoBack
        _onBackClickState.value = onBackClick
        _onShareClickState.value = onShareClick
        _onAddressClickState.value = onAddressClick
        _onSiteControlsClickState.value = onSiteControlsClick
        _onAiResearchClickState.value = onAiResearchClick
        _onSwipeNextTabState.value = onSwipeNextTab
        _onSwipePrevTabState.value = onSwipePrevTab
        _onPasteAndGoState.value = onPasteAndGo
        _onHardRefreshState.value = onHardRefresh

        if (composeView.tag == "PetalAddressBarBound") {
            return
        }
        composeView.tag = "PetalAddressBarBound"

        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentUrl by _urlState
                val currentTitle by _titleState
                val currentIncognito by _isIncognitoState
                val currentLoading by _isLoadingState
                val currentCanGoBack by _canGoBackState
                val backClick = _onBackClickState.value
                val shareClick = _onShareClickState.value
                val addressClick = _onAddressClickState.value
                val siteControlsClick = _onSiteControlsClickState.value
                val aiResearchClick = _onAiResearchClickState.value

                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                var themeConfigStr by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_theme_config" -> themeConfigStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val darkTheme = remember(themeConfigStr, isSystemDark) {
                    val config = try { com.petal.browser.ui.theme.ThemeConfig.valueOf(themeConfigStr) } catch (e: Exception) { com.petal.browser.ui.theme.ThemeConfig.FOLLOW_SYSTEM }
                    when (config) {
                        com.petal.browser.ui.theme.ThemeConfig.FOLLOW_SYSTEM -> isSystemDark
                        com.petal.browser.ui.theme.ThemeConfig.LIGHT -> false
                        com.petal.browser.ui.theme.ThemeConfig.DARK -> true
                    }
                }
                val appFont = remember(fontName) {
                    AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                val currentFavicon by _faviconState
                val currentProgress by _progressState
                val swipeNextTab = _onSwipeNextTabState.value
                val swipePrevTab = _onSwipePrevTabState.value
                val pasteAndGo = _onPasteAndGoState.value
                val hardRefresh = _onHardRefreshState.value

                PetalExpressiveTheme(
                    darkTheme = darkTheme,
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
                    PetalAddressBar(
                        url = currentUrl,
                        title = currentTitle,
                        favicon = currentFavicon,
                        progress = currentProgress,
                        isIncognito = currentIncognito,
                        isLoading = currentLoading,
                        canGoBack = currentCanGoBack,
                        onBackClick = { backClick?.run() },
                        onShareClick = { shareClick?.run() },
                        onAddressClick = { addressClick?.run() },
                        onSiteControlsClick = { siteControlsClick?.run() },
                        onAiResearchClick = { aiResearchClick?.run() },
                        onSwipeNextTab = { swipeNextTab?.run() },
                        onSwipePrevTab = { swipePrevTab?.run() },
                        onPasteAndGo = { pasteAndGo?.invoke(it) },
                        onHardRefresh = { hardRefresh?.run() }
                    )
                }
            }
        }
    }
}
