package com.petal.browser.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoSession

/**
 * Per-tab back/forward list as last reported by Gecko's
 * [GeckoSession.HistoryDelegate.onHistoryStateChange], plus the open/closed flag of the
 * history popup. Held in memory only; entries are copied out of Gecko's list immediately.
 */
object PetalTabHistoryState {

    data class Entry(val title: String, val uri: String)

    data class TabHistory(val entries: List<Entry>, val currentIndex: Int)

    private val _histories = MutableStateFlow<Map<String, TabHistory>>(emptyMap())
    val histories: StateFlow<Map<String, TabHistory>> = _histories.asStateFlow()

    private val _popupOpen = MutableStateFlow(false)
    val popupOpen: StateFlow<Boolean> = _popupOpen.asStateFlow()

    fun update(tabId: String, list: GeckoSession.HistoryDelegate.HistoryList) {
        val entries = ArrayList<Entry>(list.size)
        for (item in list) {
            entries.add(Entry(title = item.title.orEmpty(), uri = item.uri.orEmpty()))
        }
        _histories.value = _histories.value + (tabId to TabHistory(entries, list.currentIndex))
    }

    fun clear(tabId: String) {
        if (_histories.value.containsKey(tabId)) _histories.value = _histories.value - tabId
    }

    fun requestOpen() { _popupOpen.value = true }

    fun dismiss() { _popupOpen.value = false }
}
