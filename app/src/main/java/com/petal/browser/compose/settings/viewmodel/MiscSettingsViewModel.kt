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

    val torrentEngineMode: StateFlow<String> = settingsRepository.torrentEngineMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1DM")

    val autoOpenApps: StateFlow<Boolean> = settingsRepository.autoOpenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val checkUpdateOnLaunch: StateFlow<Boolean> = settingsRepository.checkUpdateOnLaunch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setTorrentEngineMode(mode: String) = viewModelScope.launch {
        settingsRepository.setTorrentEngineMode(mode)
    }

    fun setAutoOpenApps(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoOpenApps(enabled)
    }

    fun setCheckUpdateOnLaunch(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setCheckUpdateOnLaunch(enabled)
    }
}
