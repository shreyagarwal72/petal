package com.petal.browser.account.mozilla

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MozTabInfo(
    val title: String,
    val url: String,
    val iconUrl: String? = null,
    val lastAccessed: Long = System.currentTimeMillis()
)

data class RemoteDeviceTabs(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "desktop",
    val lastModified: Long = System.currentTimeMillis(),
    val tabs: List<MozTabInfo>
)

/** Holds tabs open on the user's other devices (filled from Mozilla's RemoteTabsStorage after each sync). */
class PetalTabSyncBridge {
    private val _remoteTabsFlow = MutableStateFlow<List<RemoteDeviceTabs>>(emptyList())
    val remoteTabsFlow: StateFlow<List<RemoteDeviceTabs>> = _remoteTabsFlow.asStateFlow()

    internal fun publish(list: List<RemoteDeviceTabs>) { _remoteTabsFlow.value = list }
}
