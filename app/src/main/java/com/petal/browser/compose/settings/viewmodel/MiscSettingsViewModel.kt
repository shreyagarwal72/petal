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
class MiscSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val autoOpenApps: StateFlow<Boolean> = settingsRepository.autoOpenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val checkUpdateOnLaunch: StateFlow<Boolean> = settingsRepository.checkUpdateOnLaunch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadManagerMode: StateFlow<String> = settingsRepository.downloadManagerMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.petal.browser.unit.ExternalDownloadManagerHelper.MODE_IN_APP)

    fun setAutoOpenApps(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoOpenApps(enabled)
    }

    fun setCheckUpdateOnLaunch(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setCheckUpdateOnLaunch(enabled)
    }

    fun setDownloadManagerMode(mode: String) = viewModelScope.launch {
        settingsRepository.setDownloadManagerMode(mode)
    }
}
