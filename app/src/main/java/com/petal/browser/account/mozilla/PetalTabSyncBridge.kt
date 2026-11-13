package com.petal.browser.account.mozilla

import org.json.JSONArray
import org.json.JSONObject
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

class PetalTabSyncBridge {

    private val _remoteTabsFlow = MutableStateFlow<List<RemoteDeviceTabs>>(emptyList())
    val remoteTabsFlow: StateFlow<List<RemoteDeviceTabs>> = _remoteTabsFlow.asStateFlow()

    private val remoteTabsByDevice = mutableMapOf<String, RemoteDeviceTabs>()

    fun exportToBsoRecord(deviceId: String, deviceName: String, tabs: List<MozTabInfo>): BsoRecord {
        val payload = JSONObject().apply {
            put("id", deviceId)
            put("clientName", deviceName)
            val tabsArray = JSONArray()
            tabs.forEach { t ->
                tabsArray.put(JSONObject().apply {
                    put("title", t.title)
                    val historyArray = JSONArray().apply { put(t.url) }
                    put("urlHistory", historyArray)
                    put("lastUsed", t.lastAccessed / 1000L)
                    t.iconUrl?.let { put("icon", it) }
                })
            }
            put("tabs", tabsArray)
        }

        return BsoRecord(
            id = deviceId,
            modified = System.currentTimeMillis() / 1000.0,
            payload = payload.toString()
        )
    }

    fun parseRemoteDeviceTabs(bsoList: List<BsoRecord>, localDeviceId: String): List<RemoteDeviceTabs> {
        val result = mutableListOf<RemoteDeviceTabs>()
        for (bso in bsoList) {
            if (bso.id == localDeviceId) continue
            try {
                val json = JSONObject(bso.payload)
                val clientName = json.optString("clientName", "Remote Device")
                val tabsArray = json.optJSONArray("tabs") ?: JSONArray()
                val tabList = mutableListOf<MozTabInfo>()

                for (i in 0 until tabsArray.length()) {
                    val tabObj = tabsArray.getJSONObject(i)
                    val title = tabObj.optString("title", "Untitled")
                    val historyArr = tabObj.optJSONArray("urlHistory")
                    val url = if (historyArr != null && historyArr.length() > 0) {
                        historyArr.getString(0)
                    } else {
                        tabObj.optString("url", "")
                    }

                    if (url.isNotBlank() && !url.startsWith("about:") && !url.startsWith("petal://")) {
                        tabList.add(
                            MozTabInfo(
                                title = title,
                                url = url,
                                iconUrl = tabObj.optString("icon", null),
                                lastAccessed = tabObj.optLong("lastUsed", 0L) * 1000L
                            )
                        )
                    }
                }

                if (tabList.isNotEmpty()) {
                    val dev = RemoteDeviceTabs(
                        deviceId = bso.id,
                        deviceName = clientName,
                        lastModified = (bso.modified * 1000.0).toLong(),
                        tabs = tabList
                    )
                    result.add(dev)
                    remoteTabsByDevice[bso.id] = dev
                }
            } catch (_: Exception) {}
        }
        _remoteTabsFlow.value = result
        return result
    }
}
