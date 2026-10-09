package com.petal.browser.compose.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.data.repository.SettingsRepository
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.ThemeConfig
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val appFont: StateFlow<AppFont> = settingsRepository.appFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.PETAL)

    val fontWidth: StateFlow<Float> = settingsRepository.fontWidth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 92f)

    val fontWeight: StateFlow<Float> = settingsRepository.fontWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 750f)

    val fontRoundness: StateFlow<Float> = settingsRepository.fontRoundness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100f)

    val gsFlexPreset: StateFlow<GSFlexPreset> = settingsRepository.gsFlexPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GSFlexPreset.PETAL)

    val colorStyle: StateFlow<ColorStyle> = settingsRepository.colorStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorStyle.TONAL_SPOT)

    val paletteId: StateFlow<String> = settingsRepository.paletteId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultPaletteId)

    val dynamicColor: StateFlow<Boolean> = settingsRepository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), isDynamicColorSupported)

    val amoledMode: StateFlow<Boolean> = settingsRepository.amoledMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLanguage: StateFlow<String> = settingsRepository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val matchWebsiteLanguage: StateFlow<Boolean> = settingsRepository.matchWebsiteLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val themeConfig: StateFlow<ThemeConfig> = settingsRepository.themeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeConfig.FOLLOW_SYSTEM)

    val floatingTabBar: StateFlow<Boolean> = settingsRepository.floatingTabBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val expressiveColors: StateFlow<Boolean> = settingsRepository.expressiveColors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val expressiveBgShapes: StateFlow<Boolean> = settingsRepository.expressiveBgShapes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val bgShapeChangeMode: StateFlow<String> = settingsRepository.bgShapeChangeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALWAYS")

    val bgShapeRotationMin: StateFlow<Int> = settingsRepository.bgShapeRotationMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val highRefreshRate: StateFlow<Boolean> = settingsRepository.highRefreshRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customFontName: StateFlow<String> = settingsRepository.customFontName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No font file selected")

    val launchRippleEnabled: StateFlow<Boolean> = settingsRepository.launchRippleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassUnlocked: StateFlow<Boolean> = settingsRepository.liquidGlassUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val liquidGlassEnabled: StateFlow<Boolean> = settingsRepository.liquidGlassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val liquidGlassAlpha: StateFlow<Float> = settingsRepository.liquidGlassAlpha
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.70f)

    val liquidGlassSheen: StateFlow<Float> = settingsRepository.liquidGlassSheen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.60f)

    val liquidGlassTint: StateFlow<String> = settingsRepository.liquidGlassTint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "FROSTED")

    val liquidGlassContainments: StateFlow<Boolean> = settingsRepository.liquidGlassContainments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassBottomNav: StateFlow<Boolean> = settingsRepository.liquidGlassBottomNav
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassAddressBar: StateFlow<Boolean> = settingsRepository.liquidGlassAddressBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassBgMode: StateFlow<String> = settingsRepository.liquidGlassBgMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MORPHING")

    val liquidGlassBgImageUri: StateFlow<String> = settingsRepository.liquidGlassBgImageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val liquidGlassBgDim: StateFlow<Float> = settingsRepository.liquidGlassBgDim
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.35f)

    val liquidGlassBgBlur: StateFlow<Float> = settingsRepository.liquidGlassBgBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16f)

    fun setAppFont(font: AppFont) = viewModelScope.launch {
        settingsRepository.setAppFont(font)
    }

    fun setFontWidth(width: Float) = viewModelScope.launch {
        settingsRepository.setFontWidth(width)
    }

    fun setFontWeight(weight: Float) = viewModelScope.launch {
        settingsRepository.setFontWeight(weight)
    }

    fun setFontRoundness(roundness: Float) = viewModelScope.launch {
        settingsRepository.setFontRoundness(roundness)
    }

    fun setGsFlexPreset(preset: GSFlexPreset) = viewModelScope.launch {
        settingsRepository.setGsFlexPreset(preset)
    }

    fun setColorStyle(style: ColorStyle) = viewModelScope.launch {
        settingsRepository.setColorStyle(style)
    }

    fun setPaletteId(id: String) = viewModelScope.launch {
        settingsRepository.setPaletteId(id)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setAmoledMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAmoledMode(enabled)
    }

    fun setThemeConfig(config: ThemeConfig) = viewModelScope.launch {
        settingsRepository.setThemeConfig(config)
    }

    fun setAppLanguage(language: String) = viewModelScope.launch {
        settingsRepository.setAppLanguage(language)
    }

    fun setMatchWebsiteLanguage(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setMatchWebsiteLanguage(enabled)
    }

    fun setFloatingTabBar(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFloatingTabBar(enabled)
    }

    fun setExpressiveColors(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setExpressiveColors(enabled)
    }

    fun setExpressiveBgShapes(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setExpressiveBgShapes(enabled)
    }

    fun setBgShapeChangeMode(mode: String) = viewModelScope.launch {
        settingsRepository.setBgShapeChangeMode(mode)
    }

    fun setBgShapeRotationMin(minutes: Int) = viewModelScope.launch {
        settingsRepository.setBgShapeRotationMin(minutes)
    }

    fun setHighRefreshRate(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setHighRefreshRate(enabled)
    }

    fun setCustomFontName(name: String) = viewModelScope.launch {
        settingsRepository.setCustomFontName(name)
    }

    fun setLaunchRippleEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLaunchRippleEnabled(enabled)
    }

    fun setLiquidGlassUnlocked(unlocked: Boolean) = viewModelScope.launch {
        settingsRepository.setLiquidGlassUnlocked(unlocked)
    }

    fun setLiquidGlassEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLiquidGlassEnabled(enabled)
    }

    fun setLiquidGlassAlpha(alpha: Float) = viewModelScope.launch {
        settingsRepository.setLiquidGlassAlpha(alpha)
    }

    fun setLiquidGlassSheen(sheen: Float) = viewModelScope.launch {
        settingsRepository.setLiquidGlassSheen(sheen)
    }

    fun setLiquidGlassTint(tint: String) = viewModelScope.launch {
        settingsRepository.setLiquidGlassTint(tint)
    }

    fun setLiquidGlassContainments(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLiquidGlassContainments(enabled)
    }

    fun setLiquidGlassBottomNav(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLiquidGlassBottomNav(enabled)
    }

    fun setLiquidGlassAddressBar(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLiquidGlassAddressBar(enabled)
    }

    fun setLiquidGlassBgMode(mode: String) = viewModelScope.launch {
        settingsRepository.setLiquidGlassBgMode(mode)
    }

    fun setLiquidGlassBgImageUri(uri: String) = viewModelScope.launch {
        settingsRepository.setLiquidGlassBgImageUri(uri)
    }

    fun setLiquidGlassBgDim(dim: Float) = viewModelScope.launch {
        settingsRepository.setLiquidGlassBgDim(dim)
    }

    fun setLiquidGlassBgBlur(blur: Float) = viewModelScope.launch {
        settingsRepository.setLiquidGlassBgBlur(blur)
    }
}
