package com.petal.browser.compose.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisplaySettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val touchHaptics: StateFlow<Boolean> = settingsRepository.touchHaptics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val scrollHaptics: StateFlow<Boolean> = settingsRepository.scrollHaptics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val predictiveBack: StateFlow<Boolean> = settingsRepository.predictiveBack
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val depthBlur: StateFlow<Boolean> = settingsRepository.depthBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fontSizeScale: StateFlow<Float> = settingsRepository.fontSizeScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val zoomLevelScale: StateFlow<Float> = settingsRepository.zoomLevelScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val forceZoom: StateFlow<Boolean> = settingsRepository.forceZoom
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val readerModeDetection: StateFlow<Boolean> = settingsRepository.readerModeDetection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val caretBrowsing: StateFlow<Boolean> = settingsRepository.caretBrowsing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val touchpadSwipeNav: StateFlow<Boolean> = settingsRepository.touchpadSwipeNav
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val addressBarSwipeTabs: StateFlow<Boolean> = settingsRepository.addressBarSwipeTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val addressBarQuickActions: StateFlow<Boolean> = settingsRepository.addressBarQuickActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setTouchHaptics(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTouchHaptics(enabled)
    }

    fun setScrollHaptics(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setScrollHaptics(enabled)
    }

    fun setPredictiveBack(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setPredictiveBack(enabled)
    }

    fun setDepthBlur(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDepthBlur(enabled)
    }

    fun setFontSizeScale(scale: Float) = viewModelScope.launch {
        settingsRepository.setFontSizeScale(scale)
    }

    fun setZoomLevelScale(scale: Float) = viewModelScope.launch {
        settingsRepository.setZoomLevelScale(scale)
    }

    fun setForceZoom(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setForceZoom(enabled)
    }

    fun setReaderModeDetection(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setReaderModeDetection(enabled)
    }

    fun setCaretBrowsing(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setCaretBrowsing(enabled)
    }

    fun setTouchpadSwipeNav(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTouchpadSwipeNav(enabled)
    }

    fun setAddressBarSwipeTabs(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAddressBarSwipeTabs(enabled)
    }

    val doubleBackExit: StateFlow<Boolean> = settingsRepository.doubleBackExit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Apple Duo (BETA)
    val appleDuoEnabled: StateFlow<Boolean> = settingsRepository.appleDuoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appleDuoWebsites: StateFlow<Boolean> = settingsRepository.appleDuoWebsites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val appleDuoUseSensor: StateFlow<Boolean> = settingsRepository.appleDuoUseSensor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val appleDuoManualTilt: StateFlow<Float> = settingsRepository.appleDuoManualTilt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val appleDuoAutoRecenter: StateFlow<Boolean> = settingsRepository.appleDuoAutoRecenter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val appleDuoEyeDistance: StateFlow<Float> = settingsRepository.appleDuoEyeDistance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 450f)

    val appleDuoBlurSpread: StateFlow<Float> = settingsRepository.appleDuoBlurSpread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.12f)

    val appleDuoDarkening: StateFlow<Float> = settingsRepository.appleDuoDarkening
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.015f)

    val appleDuoCurrentTilt: StateFlow<Float> = com.petal.browser.appleduo.AppleDuoManager.currentTilt
    val appleDuoCurrentHinge: StateFlow<Float> = com.petal.browser.appleduo.AppleDuoManager.currentHingeSide
    val appleDuoHasSensor: StateFlow<Boolean> = com.petal.browser.appleduo.AppleDuoManager.hasSensor

    fun setDoubleBackExit(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDoubleBackExit(enabled)
    }

    fun setAppleDuoEnabled(enabled: Boolean) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setEnabled(enabled)
        settingsRepository.setAppleDuoEnabled(enabled)
    }

    fun setAppleDuoWebsites(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAppleDuoWebsites(enabled)
    }

    fun setAppleDuoUseSensor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAppleDuoUseSensor(enabled)
    }

    fun setAppleDuoManualTilt(tilt: Float) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setManualTilt(tilt)
        settingsRepository.setAppleDuoManualTilt(tilt)
    }

    fun setAppleDuoAutoRecenter(enabled: Boolean) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setAutoRecenter(enabled)
        settingsRepository.setAppleDuoAutoRecenter(enabled)
    }

    fun setAppleDuoEyeDistance(distance: Float) = viewModelScope.launch {
        settingsRepository.setAppleDuoEyeDistance(distance)
    }

    fun setAppleDuoBlurSpread(spread: Float) = viewModelScope.launch {
        settingsRepository.setAppleDuoBlurSpread(spread)
    }

    fun setAppleDuoDarkening(darkening: Float) = viewModelScope.launch {
        settingsRepository.setAppleDuoDarkening(darkening)
    }

    fun recalibrateAppleDuo() {
        com.petal.browser.appleduo.AppleDuoManager.recalibrate()
    }
}
