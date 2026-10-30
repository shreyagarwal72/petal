package com.petal.browser.compose.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.data.repository.SettingsRepository
import com.petal.browser.ui.components.BarPrefs
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

    val style: StateFlow<String> = settingsRepository.addressBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarPrefs.STYLE_CLASSIC)
    val mergeLeading: StateFlow<String> = settingsRepository.mergeLeading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarPrefs.DEFAULT_MERGE_LEADING)
    val mergeTrailing: StateFlow<String> = settingsRepository.mergeTrailing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarPrefs.DEFAULT_MERGE_TRAILING)
    val mergeLayout: StateFlow<String> = settingsRepository.mergeLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarPrefs.LAYOUT_SINGLE)
    val collapseStyle: StateFlow<String> = settingsRepository.barCollapseStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarPrefs.COLLAPSE_PILL)

    fun setPosition(value: String) = viewModelScope.launch { settingsRepository.setAddressBarPosition(value) }
    fun setHeight(value: String) = viewModelScope.launch { settingsRepository.setAddressBarHeight(value) }
    fun setAction(value: String) = viewModelScope.launch { settingsRepository.setAddressBarAction(value) }
    fun setSwipeTabs(value: Boolean) = viewModelScope.launch { settingsRepository.setAddressBarSwipeTabs(value) }
    fun setQuickActions(value: Boolean) = viewModelScope.launch { settingsRepository.setAddressBarQuickActions(value) }

    fun setStyle(value: String) = viewModelScope.launch { settingsRepository.setAddressBarStyle(value) }
    fun setMergeLeading(value: String) = viewModelScope.launch { settingsRepository.setMergeLeading(value) }
    fun setMergeTrailing(value: String) = viewModelScope.launch { settingsRepository.setMergeTrailing(value) }
    fun setMergeLayout(value: String) = viewModelScope.launch { settingsRepository.setMergeLayout(value) }
    fun setCollapseStyle(value: String) = viewModelScope.launch { settingsRepository.setBarCollapseStyle(value) }
}
