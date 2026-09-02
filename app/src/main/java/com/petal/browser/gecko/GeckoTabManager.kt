/*
 * GeckoTabManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Central multi-tab session manager for the Mozilla GeckoView engine in Petal.
 *
 * Responsibilities:
 *   • Maintains a reactive list of [GeckoTabSession] instances
 *   • Tracks the currently active tab ID with automatic fallback on tab closure
 *   • Provides clean openTab, closeTab, closeAllTabs, selectTab methods
 *   • Converts Gecko tabs into [PetalTabItem]s for [PetalTabGridSwitcher]
 *   • Bridges background tab creation from popups (GeckoNavigationDelegate)
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.gecko

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.petal.browser.compose.tabs.PetalTabItem

private const val TAG = "GeckoTabManager"

/**
 * Thread-safe singleton managing all open Gecko tabs in Petal Browser.
 */
object GeckoTabManager {

    private val _tabs = mutableStateListOf<GeckoTabSession>()
    val tabs: List<GeckoTabSession> get() = _tabs

    var activeTabId: String? by mutableStateOf(null)
        private set

    val activeTab: GeckoTabSession?
        get() = _tabs.find { it.id == activeTabId } ?: _tabs.firstOrNull()

    /**
     * Initializes the tab manager with a default home tab if empty.
     */
    fun ensureInitialTab(initialUrl: String = "https://www.google.com"): GeckoTabSession {
        if (_tabs.isEmpty()) {
            val defaultTab = createTab(initialUrl = initialUrl, isIncognito = false, selectImmediately = true)
            return defaultTab
        }
        val current = activeTab ?: _tabs.first()
        activeTabId = current.id
        return current
    }

    /**
     * Creates and opens a new Gecko tab session.
     */
    fun createTab(
        initialUrl: String = "https://www.google.com",
        isIncognito: Boolean = false,
        selectImmediately: Boolean = true
    ): GeckoTabSession {
        var newTab: GeckoTabSession? = null
        newTab = GeckoTabSession(
            isIncognito = isIncognito,
            initialUrl = initialUrl,
            onNewSessionRequested = { popupUri ->
                createTab(initialUrl = popupUri, isIncognito = isIncognito, selectImmediately = true)
            },
            onCloseRequested = {
                newTab?.let { closeTab(it.id) }
            }
        )

        _tabs.add(newTab)
        if (selectImmediately || activeTabId == null) {
            activeTabId = newTab.id
        }

        if (GeckoRuntimeHolder.isInitialized) {
            newTab.openSafely(GeckoRuntimeHolder.runtime)
        }

        Log.i(TAG, "Created new Gecko tab: ${newTab.id} (total tabs=${_tabs.size}, incognito=$isIncognito)")
        return newTab
    }

    /**
     * Selects an active tab by its ID.
     */
    fun selectTab(tabId: String) {
        val target = _tabs.find { it.id == tabId }
        if (target != null) {
            activeTabId = target.id
            if (GeckoRuntimeHolder.isInitialized && !target.isOpened) {
                target.openSafely(GeckoRuntimeHolder.runtime)
            }
            Log.i(TAG, "Selected Gecko tab: $tabId")
        }
    }

    /**
     * Closes a tab session and cleans up native resources.
     */
    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            val tab = _tabs.removeAt(index)
            tab.closeSafely()
            Log.i(TAG, "Closed Gecko tab: $tabId (remaining=${_tabs.size})")

            // If we closed the active tab, switch to adjacent or fallback to new tab
            if (activeTabId == tabId) {
                activeTabId = when {
                    _tabs.isNotEmpty() -> {
                        val nextIndex = (index - 1).coerceAtLeast(0).coerceAtMost(_tabs.size - 1)
                        _tabs[nextIndex].id
                    }
                    else -> null
                }
            }
        }

        // If all tabs are closed, create a fresh empty tab
        if (_tabs.isEmpty()) {
            createTab("https://www.google.com", isIncognito = false, selectImmediately = true)
        }
    }

    /**
     * Closes all open tabs and creates a fresh blank tab.
     */
    fun closeAllTabs(context: android.content.Context? = null) {
        val copy = _tabs.toList()
        _tabs.clear()
        activeTabId = null
        copy.forEach { it.closeSafely() }
        createTab("https://www.google.com", isIncognito = false, selectImmediately = true)
        Log.i(TAG, "Closed all Gecko tabs and created fresh home tab")
    }

    /**
     * Converts active tabs to PetalTabItem list for PetalTabGridSwitcher.
     */
    fun getPetalTabItems(): List<PetalTabItem> {
        val currentActiveId = activeTabId
        return _tabs.map { tab ->
            val rawTitle = tab.state.pageTitle
            val rawUrl = tab.state.currentUrl
            val displayTitle = when {
                rawTitle.isNotBlank() && !rawTitle.equals("about:blank", ignoreCase = true) -> rawTitle
                rawUrl.isNotBlank() && !rawUrl.equals("about:blank", ignoreCase = true) -> rawUrl
                else -> "Petal Home"
            }
            val displayUrl = if (rawUrl.isBlank() || rawUrl.equals("about:blank", ignoreCase = true)) "Petal Home" else rawUrl

            PetalTabItem(
                id = tab.id,
                title = displayTitle,
                url = displayUrl,
                faviconBitmap = null,
                previewBitmap = tab.previewBitmap,
                isIncognito = tab.isIncognito,
                isSelected = (tab.id == currentActiveId),
                groupId = null,
                groupTitle = null,
                groupColorHex = null
            )
        }
    }
}
