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
}
