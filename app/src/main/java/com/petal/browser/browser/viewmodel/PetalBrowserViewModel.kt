package com.petal.browser.browser.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.unit.PetalBrowserUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.petal.browser.compose.tabs.PetalTabItem
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.profiling.Profile
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import javax.inject.Inject

/**
 * PetalBrowserViewModel
 * ─────────────────────────────────────────────────────────────────────────────
 * Unidirectional Reactive ViewModel bridging Mozilla's [BrowserStore] into
 * pure Jetpack Compose UI surfaces for Petal Browser.
 *
 * Eliminates the legacy BrowserActivity god-class and manages all tab lifecycles,
 * navigation actions, and sheet states via pure StateFlow streams.
 */
@HiltViewModel
class PetalBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val browserStore: BrowserStore = PetalEngineStore.getStore(context)
    private val tabsUseCases = TabsUseCases(browserStore)
    private val sessionUseCases = SessionUseCases(browserStore)

    // ── Primary Browser State Flow ──────────────────────────────────────────
    private val _browserState = MutableStateFlow(browserStore.state)
    val browserState: StateFlow<BrowserState> = _browserState.asStateFlow()

    private val storeSubscription = browserStore.observeManually { state ->
        _browserState.value = state
    }

    // ── Derived Active Tab State ────────────────────────────────────────────
    val activeTab: StateFlow<TabSessionState?> = browserState.map { state ->
        state.selectedTabId?.let { id -> state.tabs.firstOrNull { it.id == id } }
            ?: state.tabs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tabCount: StateFlow<Int> = browserState.map { it.tabs.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val activeUrl: StateFlow<String> = activeTab.map { it?.content?.url.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val activeTitle: StateFlow<String> = activeTab.map { it?.content?.title.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val loadProgress: StateFlow<Int> = activeTab.map { it?.content?.progress ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isLoading: StateFlow<Boolean> = activeTab.map { it?.content?.loading == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canGoBack: StateFlow<Boolean> = activeTab.map { it?.content?.canGoBack == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canGoForward: StateFlow<Boolean> = activeTab.map { it?.content?.canGoForward == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isIncognito: StateFlow<Boolean> = activeTab.map { it?.content?.private == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val tabItems: StateFlow<List<PetalTabItem>> = browserState.map { state ->
        state.tabs.map { tab ->
            PetalTabItem(
                id = tab.id,
                title = tab.content.title.ifBlank { "New Tab" },
                url = tab.content.url,
                faviconBitmap = tab.content.icon,
                isIncognito = tab.content.private,
                isSelected = tab.id == state.selectedTabId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Overlays & Sheets Navigation State ──────────────────────────────────
    private val _isTabSwitcherOpen = MutableStateFlow(false)
    val isTabSwitcherOpen: StateFlow<Boolean> = _isTabSwitcherOpen.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isBookmarksOpen = MutableStateFlow(false)
    val isBookmarksOpen: StateFlow<Boolean> = _isBookmarksOpen.asStateFlow()

    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen: StateFlow<Boolean> = _isHistoryOpen.asStateFlow()

    private val _isDownloadsOpen = MutableStateFlow(false)
    val isDownloadsOpen: StateFlow<Boolean> = _isDownloadsOpen.asStateFlow()

    private val _isMenuOpen = MutableStateFlow(false)
    val isMenuOpen: StateFlow<Boolean> = _isMenuOpen.asStateFlow()

    private val _isFindInPageOpen = MutableStateFlow(false)
    val isFindInPageOpen: StateFlow<Boolean> = _isFindInPageOpen.asStateFlow()

    private val _isAiSheetOpen = MutableStateFlow(false)
    val isAiSheetOpen: StateFlow<Boolean> = _isAiSheetOpen.asStateFlow()

    private val _findInPageQuery = MutableStateFlow("")
    val findInPageQuery: StateFlow<String> = _findInPageQuery.asStateFlow()

    // ── Core Navigation Actions ─────────────────────────────────────────────

    fun loadUrl(url: String) {
        val targetTab = activeTab.value
        val formattedUrl = PetalBrowserUtils.smartUrlFilter(url.trim())
        if (targetTab != null) {
            sessionUseCases.loadUrl(formattedUrl, targetTab.id)
        } else {
            openNewTab(formattedUrl, selectTab = true)
        }
    }

    fun openNewTab(
        url: String = "about:home",
        selectTab: Boolean = true,
        private: Boolean = false
    ) {
        tabsUseCases.addTab(
            url = url,
            selectTab = selectTab,
            private = private
        )
        if (_isTabSwitcherOpen.value) {
            _isTabSwitcherOpen.value = false
        }
    }

    fun selectTab(tabId: String) {
        tabsUseCases.selectTab(tabId)
        _isTabSwitcherOpen.value = false
    }

    fun closeTab(tabId: String) {
        tabsUseCases.removeTab(tabId)
        if (browserStore.state.tabs.isEmpty()) {
            openNewTab("about:home", selectTab = true)
        }
    }

    fun closeActiveTab() {
        activeTab.value?.id?.let { closeTab(it) }
    }

    fun reload() {
        activeTab.value?.let { sessionUseCases.reload(it.id) }
    }

    fun stopLoading() {
        activeTab.value?.let { sessionUseCases.stopLoading(it.id) }
    }

    fun goBack(): Boolean {
        // Priority 1: If any overlay / sheet is open, close it first
        if (dismissTopOverlay()) {
            return true
        }

        // Priority 2: Navigate back inside Gecko web history
        val tab = activeTab.value ?: return false
        if (tab.content.canGoBack) {
            sessionUseCases.goBack(tab.id)
            return true
        }

        // Priority 3: If not on home page, return to home
        val currentUrl = tab.content.url
        if (currentUrl.isNotBlank() && currentUrl != "about:home" && currentUrl != "about:blank") {
            loadUrl("about:home")
            return true
        }

        return false
    }

    fun goForward() {
        activeTab.value?.let { tab ->
            if (tab.content.canGoForward) {
                sessionUseCases.goForward(tab.id)
            }
        }
    }

    // ── Overlay / Modal Toggles ─────────────────────────────────────────────

    fun toggleTabSwitcher() {
        _isTabSwitcherOpen.value = !_isTabSwitcherOpen.value
    }

    fun setTabSwitcherOpen(open: Boolean) {
        _isTabSwitcherOpen.value = open
    }

    fun toggleMenu() {
        _isMenuOpen.value = !_isMenuOpen.value
    }

    fun setMenuOpen(open: Boolean) {
        _isMenuOpen.value = open
    }

    fun openSettings() {
        _isMenuOpen.value = false
        _isSettingsOpen.value = true
    }

    fun setSettingsOpen(open: Boolean) {
        _isSettingsOpen.value = open
    }

    fun openBookmarks() {
        _isMenuOpen.value = false
        _isBookmarksOpen.value = true
    }

    fun setBookmarksOpen(open: Boolean) {
        _isBookmarksOpen.value = open
    }

    fun openHistory() {
        _isMenuOpen.value = false
        _isHistoryOpen.value = true
    }

    fun setHistoryOpen(open: Boolean) {
        _isHistoryOpen.value = open
    }

    fun openDownloads() {
        _isMenuOpen.value = false
        _isDownloadsOpen.value = true
    }

    fun setDownloadsOpen(open: Boolean) {
        _isDownloadsOpen.value = open
    }

    fun openAiSheet() {
        _isMenuOpen.value = false
        _isAiSheetOpen.value = true
    }

    fun setAiSheetOpen(open: Boolean) {
        _isAiSheetOpen.value = open
    }

    fun openFindInPage() {
        _isMenuOpen.value = false
        _isFindInPageOpen.value = true
    }

    fun setFindInPageOpen(open: Boolean) {
        _isFindInPageOpen.value = open
    }

    fun setFindInPageQuery(query: String) {
        _findInPageQuery.value = query
    }

    fun dismissTopOverlay(): Boolean {
        if (_isMenuOpen.value) { _isMenuOpen.value = false; return true }
        if (_isAiSheetOpen.value) { _isAiSheetOpen.value = false; return true }
        if (_isFindInPageOpen.value) { _isFindInPageOpen.value = false; return true }
        if (_isDownloadsOpen.value) { _isDownloadsOpen.value = false; return true }
        if (_isHistoryOpen.value) { _isHistoryOpen.value = false; return true }
        if (_isBookmarksOpen.value) { _isBookmarksOpen.value = false; return true }
        if (_isSettingsOpen.value) { _isSettingsOpen.value = false; return true }
        if (_isTabSwitcherOpen.value) { _isTabSwitcherOpen.value = false; return true }
        return false
    }

    val hasOpenOverlay: Boolean
        get() = _isTabSwitcherOpen.value || _isSettingsOpen.value ||
                _isBookmarksOpen.value || _isHistoryOpen.value ||
                _isDownloadsOpen.value || _isMenuOpen.value ||
                _isFindInPageOpen.value || _isAiSheetOpen.value

    override fun onCleared() {
        super.onCleared()
        storeSubscription.unsubscribe()
    }
}
