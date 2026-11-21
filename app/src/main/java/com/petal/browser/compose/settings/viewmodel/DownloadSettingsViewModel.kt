package com.petal.browser.compose.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.data.repository.SettingsRepository
import com.petal.browser.unit.ExternalDownloadManagerHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val downloadManagerMode: StateFlow<String> = settingsRepository.downloadManagerMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExternalDownloadManagerHelper.MODE_IN_APP)

    val confirmDownloadDelete: StateFlow<Boolean> = settingsRepository.confirmDownloadDelete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val deleteDownloadFile: StateFlow<Boolean> = settingsRepository.deleteDownloadFile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoPreviewDownloadedImages: StateFlow<Boolean> = settingsRepository.autoPreviewDownloadedImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val liveUpdates: StateFlow<Boolean> = settingsRepository.liveUpdates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val islandMode: StateFlow<String> = settingsRepository.islandMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")

    val islandAnimationStyle: StateFlow<String> = settingsRepository.islandAnimationStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "EXPRESSIVE")

    val islandAnimationSpeed: StateFlow<String> = settingsRepository.islandAnimationSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DEFAULT")

    val islandAnimationBounce: StateFlow<String> = settingsRepository.islandAnimationBounce
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NORMAL")

    val islandWavyProgress: StateFlow<Boolean> = settingsRepository.islandWavyProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val islandOverlay: StateFlow<Boolean> = settingsRepository.islandOverlay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val parallelDownloads: StateFlow<Boolean> = settingsRepository.parallelDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val parallelConnections: StateFlow<Int> = settingsRepository.parallelConnections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)
    val smartDownloadRouting: StateFlow<Boolean> = settingsRepository.smartDownloadRouting
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val maxConcurrentDownloads: StateFlow<Int> = settingsRepository.maxConcurrentDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    val wifiOnlyDownloads: StateFlow<Boolean> = settingsRepository.wifiOnlyDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val downloadSpeedLimitKBps: StateFlow<Int> = settingsRepository.downloadSpeedLimitKBps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val verifyDownloadChecksums: StateFlow<Boolean> = settingsRepository.verifyDownloadChecksums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val postDownloadActionsJson: StateFlow<String> = settingsRepository.postDownloadActionsJson
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val offerClipboardImport: StateFlow<Boolean> = settingsRepository.offerClipboardImport
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setParallelDownloads(enabled: Boolean) = viewModelScope.launch { settingsRepository.setParallelDownloads(enabled) }
    fun setParallelConnections(count: Int) = viewModelScope.launch { settingsRepository.setParallelConnections(count) }
    fun setSmartDownloadRouting(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSmartDownloadRouting(enabled) }
    fun setMaxConcurrentDownloads(count: Int) = viewModelScope.launch { settingsRepository.setMaxConcurrentDownloads(count) }
    fun setWifiOnlyDownloads(enabled: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnlyDownloads(enabled) }
    fun setDownloadSpeedLimitKBps(kbps: Int) = viewModelScope.launch { settingsRepository.setDownloadSpeedLimitKBps(kbps) }
    fun setVerifyDownloadChecksums(enabled: Boolean) = viewModelScope.launch { settingsRepository.setVerifyDownloadChecksums(enabled) }
    fun setPostDownloadActionsJson(json: String) = viewModelScope.launch { settingsRepository.setPostDownloadActionsJson(json) }
    fun setOfferClipboardImport(enabled: Boolean) = viewModelScope.launch { settingsRepository.setOfferClipboardImport(enabled) }

    fun setDownloadManagerMode(mode: String) = viewModelScope.launch {
        settingsRepository.setDownloadManagerMode(mode)
    }

    fun setConfirmDownloadDelete(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setConfirmDownloadDelete(enabled)
    }

    fun setDeleteDownloadFile(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDeleteDownloadFile(enabled)
    }

    fun setAutoPreviewDownloadedImages(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoPreviewDownloadedImages(enabled)
    }

    fun setLiveUpdates(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLiveUpdates(enabled)
    }

    fun setIslandMode(mode: String) = viewModelScope.launch {
        settingsRepository.setIslandMode(mode)
    }

    fun setIslandAnimationStyle(style: String) = viewModelScope.launch {
        settingsRepository.setIslandAnimationStyle(style)
    }

    fun setIslandAnimationSpeed(speed: String) = viewModelScope.launch {
        settingsRepository.setIslandAnimationSpeed(speed)
    }

    fun setIslandAnimationBounce(bounce: String) = viewModelScope.launch {
        settingsRepository.setIslandAnimationBounce(bounce)
    }

    fun setIslandWavyProgress(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setIslandWavyProgress(enabled)
    }

    fun setIslandOverlay(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setIslandOverlay(enabled)
    }
}
