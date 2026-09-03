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
class SearchHomeSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val searchEngineIndex: StateFlow<String> = settingsRepository.searchEngineIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    val homepageType: StateFlow<String> = settingsRepository.homepageType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0")

    val customHomepageUrl: StateFlow<String> = settingsRepository.customHomepageUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://google.com")

    val backgroundPlay: StateFlow<Boolean> = settingsRepository.backgroundPlay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoPip: StateFlow<Boolean> = settingsRepository.autoPip
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val forceDarkMode: StateFlow<Boolean> = settingsRepository.forceDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val enableLiveSuggestions: StateFlow<Boolean> = settingsRepository.enableLiveSuggestions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setSearchEngineIndex(index: String) = viewModelScope.launch {
        settingsRepository.setSearchEngineIndex(index)
    }

    fun setHomepageType(type: String) = viewModelScope.launch {
        settingsRepository.setHomepageType(type)
    }

    fun setCustomHomepageUrl(url: String) = viewModelScope.launch {
        settingsRepository.setCustomHomepageUrl(url)
    }

    fun setBackgroundPlay(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBackgroundPlay(enabled)
    }

    fun setAutoPip(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoPip(enabled)
    }

    fun setForceDarkMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setForceDarkMode(enabled)
    }

    fun setEnableLiveSuggestions(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setEnableLiveSuggestions(enabled)
    }
}
