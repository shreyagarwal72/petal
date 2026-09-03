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
}
