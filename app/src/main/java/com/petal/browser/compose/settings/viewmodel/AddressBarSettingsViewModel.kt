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
class AddressBarSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val position: StateFlow<String> = settingsRepository.addressBarPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TOP")
    val height: StateFlow<String> = settingsRepository.addressBarHeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "COMPACT")
    val action: StateFlow<String> = settingsRepository.addressBarAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AI")
    val swipeTabs: StateFlow<Boolean> = settingsRepository.addressBarSwipeTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val quickActions: StateFlow<Boolean> = settingsRepository.addressBarQuickActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setPosition(value: String) = viewModelScope.launch { settingsRepository.setAddressBarPosition(value) }
    fun setHeight(value: String) = viewModelScope.launch { settingsRepository.setAddressBarHeight(value) }
    fun setAction(value: String) = viewModelScope.launch { settingsRepository.setAddressBarAction(value) }
    fun setSwipeTabs(value: Boolean) = viewModelScope.launch { settingsRepository.setAddressBarSwipeTabs(value) }
    fun setQuickActions(value: Boolean) = viewModelScope.launch { settingsRepository.setAddressBarQuickActions(value) }
}
