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

    val appLockEnabled: StateFlow<Boolean> = settingsRepository.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLockPasscode: StateFlow<String> = settingsRepository.appLockPasscode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

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

    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAppLockEnabled(enabled)
    }

    fun setAppLockPasscode(passcode: String) = viewModelScope.launch {
        settingsRepository.setAppLockPasscode(passcode)
    }

    fun setDoubleBackExit(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDoubleBackExit(enabled)
    }

    fun setAppleDuoEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAppleDuoEnabled(enabled)
    }

    fun setAppleDuoWebsites(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAppleDuoWebsites(enabled)
    }

    fun setAppleDuoUseSensor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAppleDuoUseSensor(enabled)
    }

    fun setAppleDuoManualTilt(tilt: Float) = viewModelScope.launch {
        settingsRepository.setAppleDuoManualTilt(tilt)
        com.petal.browser.appleduo.AppleDuoManager.setManualTilt(tilt)
    }

    fun setAppleDuoAutoRecenter(enabled: Boolean) = viewModelScope.launch {
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
