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
class ExperimentalSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val appLanguage: StateFlow<String> = settingsRepository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val addressBarPosition: StateFlow<String> = settingsRepository.addressBarPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TOP")



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

    fun setAppLanguage(language: String) = viewModelScope.launch {
        settingsRepository.setAppLanguage(language)
    }

    fun setAddressBarPosition(position: String) = viewModelScope.launch {
        settingsRepository.setAddressBarPosition(position)
    }



    fun setDoubleBackExit(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDoubleBackExit(enabled)
    }

    fun setAppleDuoEnabled(enabled: Boolean) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setEnabled(enabled)
        settingsRepository.setAppleDuoEnabled(enabled)
    }

    fun setAppleDuoWebsites(enabled: Boolean) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setShowInWebsites(enabled)
        settingsRepository.setAppleDuoWebsites(enabled)
    }

    fun setAppleDuoUseSensor(enabled: Boolean) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setUseSensor(enabled)
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
        com.petal.browser.appleduo.AppleDuoManager.setEyeDistance(distance)
        settingsRepository.setAppleDuoEyeDistance(distance)
    }

    fun setAppleDuoBlurSpread(spread: Float) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setBlurSpread(spread)
        settingsRepository.setAppleDuoBlurSpread(spread)
    }

    fun setAppleDuoDarkening(darkening: Float) = viewModelScope.launch {
        com.petal.browser.appleduo.AppleDuoManager.setDarkening(darkening)
        settingsRepository.setAppleDuoDarkening(darkening)
    }

    fun recalibrateAppleDuo() {
        com.petal.browser.appleduo.AppleDuoManager.recalibrate()
    }
}
