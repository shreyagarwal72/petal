package com.petal.browser.compose.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.data.repository.SettingsRepository
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.CustomFontSettings
import com.petal.browser.ui.theme.FontAccentMode
import com.petal.browser.ui.theme.FontAxes
import com.petal.browser.ui.theme.FontState
import com.petal.browser.ui.theme.FontTier
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.ThemeConfig
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val fontState: StateFlow<FontState> = settingsRepository.fontState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontState())

    val appFont: StateFlow<AppFont> = settingsRepository.appFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.PETAL)

    val gsFlexPreset: StateFlow<GSFlexPreset> = settingsRepository.gsFlexPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GSFlexPreset.PETAL_SIGNATURE)

    val gsFlexSettings: StateFlow<com.petal.browser.ui.theme.GSFlexSettings> = settingsRepository.gsFlexSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.petal.browser.ui.theme.GSFlexSettings())

    val nunitoWeight: StateFlow<Int> = settingsRepository.nunitoWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontAxes.DEFAULT_NUNITO_WEIGHT)

    val customFontSettings: StateFlow<CustomFontSettings> = settingsRepository.customFontSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomFontSettings())

    val fontAccentMode: StateFlow<FontAccentMode> = settingsRepository.fontAccentMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontAccentMode.OFF)

    val readerFollowsAppFont: StateFlow<Boolean> = settingsRepository.readerFollowsAppFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val colorStyle: StateFlow<ColorStyle> = settingsRepository.colorStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorStyle.TONAL_SPOT)

    val paletteId: StateFlow<String> = settingsRepository.paletteId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultPaletteId)

    val dynamicColor: StateFlow<Boolean> = settingsRepository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), isDynamicColorSupported)

    val amoledMode: StateFlow<Boolean> = settingsRepository.amoledMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val forceDarkMode: StateFlow<Boolean> = settingsRepository.forceDarkMode
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

    val motionBlurEnabled: StateFlow<Boolean> = settingsRepository.motionBlurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val motionBlurScale: StateFlow<Float> = settingsRepository.motionBlurScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val liquidGlassUnlocked: StateFlow<Boolean> = settingsRepository.liquidGlassUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val liquidGlassEnabled: StateFlow<Boolean> = settingsRepository.liquidGlassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val liquidGlassAlpha: StateFlow<Float> = settingsRepository.liquidGlassAlpha
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.70f)

    val liquidGlassSheen: StateFlow<Float> = settingsRepository.liquidGlassSheen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.60f)

    val liquidGlassRefraction: StateFlow<Float> = settingsRepository.liquidGlassRefraction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val liquidGlassRefractionWidth: StateFlow<Float> = settingsRepository.liquidGlassRefractionWidth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val liquidGlassContainments: StateFlow<Boolean> = settingsRepository.liquidGlassContainments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassBottomNav: StateFlow<Boolean> = settingsRepository.liquidGlassBottomNav
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassAddressBar: StateFlow<Boolean> = settingsRepository.liquidGlassAddressBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassOverflowBlurBg: StateFlow<Boolean> = settingsRepository.liquidGlassOverflowBlurBg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassWidgets: StateFlow<Boolean> = settingsRepository.liquidGlassWidgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liquidGlassBgMode: StateFlow<String> = settingsRepository.liquidGlassBgMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "STRIDE")

    val liquidGlassBgImageUri: StateFlow<String> = settingsRepository.liquidGlassBgImageUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val liquidGlassBgDim: StateFlow<Float> = settingsRepository.liquidGlassBgDim
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.35f)

    val liquidGlassBgBlur: StateFlow<Float> = settingsRepository.liquidGlassBgBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16f)

    private sealed interface AxisWrite {
        data class GsFlexTier(val tier: FontTier, val axis: FontAxes.Axis, val value: Float) : AxisWrite
        data class GsFlexAll(val axis: FontAxes.Axis, val value: Float) : AxisWrite
        data class CustomTier(val tier: FontTier, val axis: FontAxes.Axis, val value: Float) : AxisWrite
        data class CustomAll(val axis: FontAxes.Axis, val value: Float) : AxisWrite
    }

    private var axisWriteDebounceJob: Job? = null

    private fun scheduleAxisWrite(debounced: Boolean, write: AxisWrite) {
        axisWriteDebounceJob?.cancel()
        if (debounced) {
            axisWriteDebounceJob = viewModelScope.launch {
                delay(110L)
                executeAxisWrite(write)
            }
        } else {
            viewModelScope.launch { executeAxisWrite(write) }
        }
    }

    private suspend fun executeAxisWrite(write: AxisWrite) {
        when (write) {
            is AxisWrite.GsFlexTier -> settingsRepository.setGsFlexAxis(write.tier, write.axis, write.value)
            is AxisWrite.GsFlexAll -> settingsRepository.setAllGsFlexAxes(write.axis, write.value)
            is AxisWrite.CustomTier -> settingsRepository.setCustomFontAxis(write.tier, write.axis, write.value)
            is AxisWrite.CustomAll -> settingsRepository.setAllCustomFontAxes(write.axis, write.value)
        }
    }

    fun setAppFont(font: AppFont) = viewModelScope.launch {
        axisWriteDebounceJob?.cancel()
        settingsRepository.setAppFont(font)
    }

    fun setGsFlexPreset(preset: GSFlexPreset) = viewModelScope.launch {
        axisWriteDebounceJob?.cancel()
        settingsRepository.setGsFlexPreset(preset)
    }

    fun setGsFlexSettings(settings: com.petal.browser.ui.theme.GSFlexSettings) = viewModelScope.launch {
        axisWriteDebounceJob?.cancel()
        settingsRepository.setGsFlexSettings(settings)
    }

    fun setGsFlexAxis(tier: FontTier, axis: FontAxes.Axis, value: Float, debounce: Boolean = false) {
        scheduleAxisWrite(debounce, AxisWrite.GsFlexTier(tier, axis, value))
    }

    fun setAllGsFlexAxes(axis: FontAxes.Axis, value: Float, debounce: Boolean = false) {
        scheduleAxisWrite(debounce, AxisWrite.GsFlexAll(axis, value))
    }

    fun resetGsFlexToPreset(preset: GSFlexPreset) = viewModelScope.launch {
        axisWriteDebounceJob?.cancel()
        settingsRepository.resetGsFlexToPreset(preset)
    }

    fun setNunitoWeight(weight: Int) = viewModelScope.launch {
        axisWriteDebounceJob?.cancel()
        settingsRepository.setNunitoWeight(weight)
    }

    fun setCustomFontAxis(tier: FontTier, axis: FontAxes.Axis, value: Float, debounce: Boolean = false) {
        scheduleAxisWrite(debounce, AxisWrite.CustomTier(tier, axis, value))
    }

    fun setAllCustomFontAxes(axis: FontAxes.Axis, value: Float, debounce: Boolean = false) {
        scheduleAxisWrite(debounce, AxisWrite.CustomAll(axis, value))
    }

    fun setCustomFontSettings(settings: CustomFontSettings) = viewModelScope.launch {
        axisWriteDebounceJob?.cancel()
        settingsRepository.setCustomFontSettings(settings)
    }

    fun setFontAccentMode(mode: FontAccentMode) = viewModelScope.launch {
        settingsRepository.setFontAccentMode(mode)
    }

    fun setReaderFollowsAppFont(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setReaderFollowsAppFont(enabled)
    }

    fun resetAllFontSettings() = viewModelScope.launch {
        settingsRepository.resetAllFontSettings()
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

    fun setForceDarkMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setForceDarkMode(enabled)
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

    fun setMotionBlurEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setMotionBlurEnabled(enabled)
    }

    fun setMotionBlurScale(scale: Float) = viewModelScope.launch {
        settingsRepository.setMotionBlurScale(scale)
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

    fun setLiquidGlassRefraction(strength: Float) = viewModelScope.launch {
        settingsRepository.setLiquidGlassRefraction(strength)
    }

    fun setLiquidGlassRefractionWidth(width: Float) = viewModelScope.launch {
        settingsRepository.setLiquidGlassRefractionWidth(width)
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

    fun setLiquidGlassOverflowBlurBg(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLiquidGlassOverflowBlurBg(enabled)
    }

    fun setLiquidGlassWidgets(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLiquidGlassWidgets(enabled)
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
