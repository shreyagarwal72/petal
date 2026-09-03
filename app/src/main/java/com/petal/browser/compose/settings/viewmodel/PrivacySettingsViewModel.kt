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
class PrivacySettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val adBlockEnabled: StateFlow<Boolean> = settingsRepository.adBlockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockThirdPartyCookies: StateFlow<Boolean> = settingsRepository.blockThirdPartyCookies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val fingerprintProtection: StateFlow<Boolean> = settingsRepository.fingerprintProtection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val webrtcProtection: StateFlow<Boolean> = settingsRepository.webrtcProtection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dntGpc: StateFlow<Boolean> = settingsRepository.dntGpc
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val trimReferrers: StateFlow<Boolean> = settingsRepository.trimReferrers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val webauthnEnabled: StateFlow<Boolean> = settingsRepository.webauthnEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val httpsOnly: StateFlow<Boolean> = settingsRepository.httpsOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val javaScriptEnabled: StateFlow<Boolean> = settingsRepository.javaScriptEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blockPopups: StateFlow<Boolean> = settingsRepository.blockPopups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val privateDnsMode: StateFlow<String> = settingsRepository.privateDnsMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OFF")

    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAdBlockEnabled(enabled)
    }

    fun setBlockThirdPartyCookies(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBlockThirdPartyCookies(enabled)
    }

    fun setFingerprintProtection(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFingerprintProtection(enabled)
    }

    fun setWebrtcProtection(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setWebrtcProtection(enabled)
    }

    fun setDntGpc(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDntGpc(enabled)
    }

    fun setTrimReferrers(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTrimReferrers(enabled)
    }

    fun setWebauthnEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setWebauthnEnabled(enabled)
    }

    fun setHttpsOnly(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setHttpsOnly(enabled)
    }

    fun setJavaScriptEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setJavaScriptEnabled(enabled)
    }

    fun setBlockPopups(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBlockPopups(enabled)
    }

    fun setPrivateDnsMode(mode: String) = viewModelScope.launch {
        settingsRepository.setPrivateDnsMode(mode)
    }
}
