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

    fun setAddressBarQuickActions(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAddressBarQuickActions(enabled)
    }
}
