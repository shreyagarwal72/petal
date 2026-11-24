package com.petal.browser.compose.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.data.repository.SettingsRepository
import com.petal.browser.ui.components.AddressBarHeight
import com.petal.browser.ui.components.AddressBarPosition
import com.petal.browser.ui.components.AddressBarStyle
import com.petal.browser.ui.components.BarCollapseStyle
import com.petal.browser.ui.components.BarPrefs
import com.petal.browser.ui.components.MergeLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddressBarSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    // The repository keeps raw strings (stored preference values stay unchanged); the UI works in enums.
    val position: StateFlow<AddressBarPosition> = settingsRepository.addressBarPosition
        .map { AddressBarPosition.fromPref(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddressBarPosition.TOP)
    val height: StateFlow<AddressBarHeight> = settingsRepository.addressBarHeight
        .map { AddressBarHeight.fromPref(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddressBarHeight.DEFAULT)
    val action: StateFlow<String> = settingsRepository.addressBarAction
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AI")
    val swipeTabs: StateFlow<Boolean> = settingsRepository.addressBarSwipeTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val quickActions: StateFlow<Boolean> = settingsRepository.addressBarQuickActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val style: StateFlow<AddressBarStyle> = settingsRepository.addressBarStyle
        .map { AddressBarStyle.fromPref(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddressBarStyle.CLASSIC)
    val mergeLeading: StateFlow<String> = settingsRepository.mergeLeading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarPrefs.DEFAULT_MERGE_LEADING)
    val mergeTrailing: StateFlow<String> = settingsRepository.mergeTrailing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarPrefs.DEFAULT_MERGE_TRAILING)
    val mergeLayout: StateFlow<MergeLayout> = settingsRepository.mergeLayout
        .map { MergeLayout.fromPref(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MergeLayout.SINGLE)
    val collapseStyle: StateFlow<BarCollapseStyle> = settingsRepository.barCollapseStyle
        .map { BarCollapseStyle.fromPref(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BarCollapseStyle.PILL)

    fun setPosition(value: AddressBarPosition) = viewModelScope.launch { settingsRepository.setAddressBarPosition(value.pref) }
    fun setHeight(value: AddressBarHeight) = viewModelScope.launch { settingsRepository.setAddressBarHeight(value.pref) }
    fun setAction(value: String) = viewModelScope.launch { settingsRepository.setAddressBarAction(value) }
    fun setSwipeTabs(value: Boolean) = viewModelScope.launch { settingsRepository.setAddressBarSwipeTabs(value) }
    fun setQuickActions(value: Boolean) = viewModelScope.launch { settingsRepository.setAddressBarQuickActions(value) }

    fun setStyle(value: AddressBarStyle) = viewModelScope.launch { settingsRepository.setAddressBarStyle(value.pref) }
    fun setMergeLeading(value: String) = viewModelScope.launch { settingsRepository.setMergeLeading(value) }
    fun setMergeTrailing(value: String) = viewModelScope.launch { settingsRepository.setMergeTrailing(value) }
    fun setMergeLayout(value: MergeLayout) = viewModelScope.launch { settingsRepository.setMergeLayout(value.pref) }
    fun setCollapseStyle(value: BarCollapseStyle) = viewModelScope.launch { settingsRepository.setBarCollapseStyle(value.pref) }
}
