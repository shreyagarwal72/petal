package com.petal.browser.browser.viewmodel

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.R
import com.petal.browser.browser.PetalTabMetadata
import com.petal.browser.browser.PetalTabMetadataStore
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.compose.tabs.PetalTabItem
import com.petal.browser.compose.tabs.PetalWorkspaceManager
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.PetalRecentlyClosedManager
import com.petal.browser.unit.TabThumbnailCache
import com.petal.browser.view.PetalToast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
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
 * Eliminates the legacy MainActivity god-class and manages all tab lifecycles,
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
    }.also { it.resume() }

    // ── Derived Active Tab State ────────────────────────────────────────────
    val activeTab: StateFlow<TabSessionState?> = browserState.map { state ->
        state.selectedTabId?.let { id -> state.tabs.firstOrNull { it.id == id } }
            ?: state.tabs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tabCount: StateFlow<Int> = browserState.map { it.tabs.size.coerceAtLeast(1) }
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

    private val _thumbnailRevision = MutableStateFlow(0L)
    private val _metadataRevision = MutableStateFlow(0L)

    fun notifyMetadataChanged() {
        _metadataRevision.value = System.currentTimeMillis()
    }

    fun notifyThumbnailUpdated() {
        _thumbnailRevision.value = System.currentTimeMillis()
    }

    val tabItems: StateFlow<List<PetalTabItem>> = combine(
        browserState,
        _thumbnailRevision,
        _metadataRevision
    ) { state, _, _ ->
        state.tabs.map { tab ->
            val meta = PetalTabMetadataStore.get(context, tab.id)
            val preview = TabThumbnailCache.getMemoryOnly(tab.id, tab.content.private)
            PetalTabItem(
                id = tab.id,
                thumbnailKey = tab.id,
                title = meta?.customTitle ?: tab.content.title.ifBlank { "New Tab" },
                url = tab.content.url,
                faviconBitmap = tab.content.icon,
                previewBitmap = preview,
                isIncognito = tab.content.private,
                isSelected = tab.id == state.selectedTabId,
                isPinned = meta?.isPinned ?: false,
                isPinnedGlobal = meta?.isPinnedGlobal ?: true,
                workspaceId = meta?.workspaceId ?: PetalWorkspaceManager.DEFAULT_WORKSPACE_ID,
                isMuted = false,
                groupId = meta?.tabGroupId,
                groupTitle = meta?.tabGroupTitle,
                groupColorHex = meta?.tabGroupColorHex
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Overlays & Sheets Navigation State ──────────────────────────────────
    private val _isTabSwitcherOpen = MutableStateFlow(false)
    val isTabSwitcherOpen: StateFlow<Boolean> = _isTabSwitcherOpen.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _settingsCategory = MutableStateFlow(SettingsCategory.OVERVIEW)
    val settingsCategory: StateFlow<SettingsCategory> = _settingsCategory.asStateFlow()

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

    private val _isExtensionsOpen = MutableStateFlow(false)
    val isExtensionsOpen: StateFlow<Boolean> = _isExtensionsOpen.asStateFlow()

    private val _isAccountSyncOpen = MutableStateFlow(false)
    val isAccountSyncOpen: StateFlow<Boolean> = _isAccountSyncOpen.asStateFlow()

    private val _isDeleteOpen = MutableStateFlow(false)
    val isDeleteOpen: StateFlow<Boolean> = _isDeleteOpen.asStateFlow()

    private val _isSafeLockerOpen = MutableStateFlow(false)
    val isSafeLockerOpen: StateFlow<Boolean> = _isSafeLockerOpen.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_desktop_site", false)
    )
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    val isCurrentUrlBookmarked: StateFlow<Boolean> = activeUrl.map { url ->
        if (url.isBlank() || url.startsWith("about:")) false
        else withContext(Dispatchers.IO) {
            try { HistoryBookmarkRepository(context).checkBookmark(url) } catch (_: Throwable) { false }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _findInPageQuery = MutableStateFlow("")
    val findInPageQuery: StateFlow<String> = _findInPageQuery.asStateFlow()

    private val _aiSheetQuery = MutableStateFlow("")
    val aiSheetQuery: StateFlow<String> = _aiSheetQuery.asStateFlow()

    private val _isOmniboxOpen = MutableStateFlow(false)
    val isOmniboxOpen: StateFlow<Boolean> = _isOmniboxOpen.asStateFlow()

    init {
        viewModelScope.launch {
            // Let Compose draw the initial home surface before creating Gecko sessions.
            yield()
            if (browserStore.state.tabs.isEmpty()) {
                openNewTab("about:home", selectTab = true)
            } else {
                val currentTab = activeTab.value
                if (currentTab != null && currentTab.engineState.engineSession == null) {
                    PetalEngineStore.materializeTabSession(
                        context = context,
                        tabId = currentTab.id,
                        fallbackUrl = currentTab.content.url,
                        fallbackTitle = currentTab.content.title,
                        isIncognito = currentTab.content.private,
                        select = true
                    )
                }
            }
        }

        // Automatic Popup Window Adoption & Window Closure (window.open / target="_blank" / window.close)
        viewModelScope.launch {
            browserState.collect { state ->
                for (tab in state.tabs) {
                    val req = tab.content.windowRequest ?: continue
                    try {
                        if (req.type == mozilla.components.concept.engine.window.WindowRequest.Type.OPEN) {
                            val engineSession = req.prepare()
                            val newTabId = "tab_" + System.currentTimeMillis() + "_" + Math.abs(java.util.UUID.randomUUID().hashCode())
                            val isIncognito = tab.content.private
                            val targetUrl = req.url?.takeIf { it.isNotBlank() } ?: "about:blank"
                            PetalEngineStore.adoptPreparedSession(
                                context = context,
                                tabId = newTabId,
                                url = targetUrl,
                                title = "",
                                isIncognito = isIncognito,
                                session = engineSession,
                                select = true
                            )
                        } else if (req.type == mozilla.components.concept.engine.window.WindowRequest.Type.CLOSE) {
                            closeTab(tab.id)
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("PetalBrowserVM", "Failed to handle window request: ${t.message}", t)
                    } finally {
                        PetalEngineStore.consumeWindowRequest(context, tab.id)
                    }
                }
            }
        }
    }

    // ── Core Navigation Actions ─────────────────────────────────────────────

    fun loadUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val formattedUrl = PetalBrowserUtils.queryWrapper(context, trimmed)
        val targetTab = activeTab.value
        if (targetTab != null) {
            val session = targetTab.engineState.engineSession ?: run {
                val pair = PetalEngineStore.materializeTabSession(
                    context = context,
                    tabId = targetTab.id,
                    fallbackUrl = formattedUrl,
                    isIncognito = targetTab.content.private,
                    select = true
                )
                pair.second
            }
            browserStore.dispatch(ContentAction.UpdateUrlAction(targetTab.id, formattedUrl))
            session.loadUrl(formattedUrl)
        } else {
            openNewTab(formattedUrl, selectTab = true)
        }
    }

    fun openNewTab(
        url: String = "about:home",
        selectTab: Boolean = true,
        private: Boolean = false,
        groupId: String? = null,
        groupTitle: String? = null
    ) {
        val tabId = java.util.UUID.randomUUID().toString()
        if (!groupId.isNullOrBlank()) {
            PetalTabMetadataStore.put(
                context,
                PetalTabMetadata(
                    tabId = tabId,
                    tabGroupId = groupId,
                    tabGroupTitle = groupTitle ?: "Group"
                )
            )
        }
        val formattedUrl = if (url.isBlank()) "about:home" else PetalBrowserUtils.queryWrapper(context, url.trim())
        PetalEngineStore.createTabSession(
            context = context,
            tabId = tabId,
            url = formattedUrl,
            title = if (PetalBrowserUtils.isHomePage(formattedUrl)) "Petal Home" else "",
            isIncognito = private,
            select = selectTab
        )
        if (_isTabSwitcherOpen.value) {
            _isTabSwitcherOpen.value = false
        }
    }

    fun selectTab(tabId: String) {
        val tab = browserStore.state.tabs.firstOrNull { it.id == tabId }
        if (tab != null && tab.engineState.engineSession == null) {
            PetalEngineStore.materializeTabSession(
                context = context,
                tabId = tabId,
                fallbackUrl = tab.content.url,
                fallbackTitle = tab.content.title,
                isIncognito = tab.content.private,
                select = true
            )
        } else {
            tabsUseCases.selectTab(tabId)
        }
        _isTabSwitcherOpen.value = false
    }

    fun loadTabThumbnailAsync(tabId: String, isIncognito: Boolean) {
        TabThumbnailCache.loadFirstAsync(arrayOf(tabId), isIncognito) { bitmap ->
            if (bitmap != null) {
                _thumbnailRevision.value = System.currentTimeMillis()
            }
        }
    }

    fun closeTab(tabId: String) {
        val state = browserStore.state
        val tabToClose = state.tabs.firstOrNull { it.id == tabId }
        if (tabToClose != null) {
            val meta = PetalTabMetadataStore.get(context, tabId)
            PetalRecentlyClosedManager.pushClosedTab(
                id = tabToClose.id,
                title = meta?.customTitle ?: tabToClose.content.title,
                url = tabToClose.content.url,
                isIncognito = tabToClose.content.private,
                groupId = meta?.tabGroupId,
                groupTitle = meta?.tabGroupTitle,
                groupColorHex = meta?.tabGroupColorHex
            )
        }
        tabsUseCases.removeTab(tabId)
        PetalTabMetadataStore.remove(context, tabId)
        if (browserStore.state.tabs.isEmpty()) {
            openNewTab("about:home", selectTab = true)
        }
    }

    fun closeTabs(tabIds: Collection<String>) {
        val state = browserStore.state
        for (id in tabIds) {
            val tabToClose = state.tabs.firstOrNull { it.id == id }
            if (tabToClose != null) {
                val meta = PetalTabMetadataStore.get(context, id)
                PetalRecentlyClosedManager.pushClosedTab(
                    id = tabToClose.id,
                    title = meta?.customTitle ?: tabToClose.content.title,
                    url = tabToClose.content.url,
                    isIncognito = tabToClose.content.private,
                    groupId = meta?.tabGroupId,
                    groupTitle = meta?.tabGroupTitle,
                    groupColorHex = meta?.tabGroupColorHex
                )
            }
            PetalTabMetadataStore.remove(context, id)
        }
        browserStore.dispatch(TabListAction.RemoveTabsAction(tabIds.toList()))
        if (browserStore.state.tabs.isEmpty()) {
            openNewTab("about:home", selectTab = true)
        }
    }

    fun closeOtherTabs(retainedTab: PetalTabItem) {
        val otherIds = browserStore.state.tabs
            .filter { it.id != retainedTab.id && it.content.private == retainedTab.isIncognito }
            .map { it.id }
        if (otherIds.isNotEmpty()) {
            closeTabs(otherIds)
        }
    }

    fun closeAllTabs(isIncognitoOnly: Boolean = false) {
        val tabsToClose = if (isIncognitoOnly) {
            browserStore.state.tabs.filter { it.content.private }
        } else {
            browserStore.state.tabs
        }
        val ids = tabsToClose.map { it.id }
        if (ids.isNotEmpty()) {
            closeTabs(ids)
        }
    }

    fun restoreTab(tab: PetalTabItem) {
        val url = if (tab.url.isBlank() || tab.url == "Petal Home" || tab.url.equals("about:blank", ignoreCase = true)) {
            "about:home"
        } else {
            tab.url
        }
        openNewTab(url = url, selectTab = false, private = tab.isIncognito)
    }

    fun duplicateTab(tab: PetalTabItem) {
        val url = if (tab.url.isBlank() || tab.url == "Petal Home") "about:home" else tab.url
        openNewTab(url = url, selectTab = true, private = tab.isIncognito)
    }

    fun saveBookmark(title: String, url: String) {
        if (url.isBlank() || PetalBrowserUtils.isHomePage(url)) {
            PetalToast.show(context, R.string.toast_homepage_cannot_bookmark)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = HistoryBookmarkRepository(context)
                repo.open(true)
                val displayTitle = title.ifBlank { url }
                if (repo.checkBookmark(url)) {
                    repo.updateBookmark(url, displayTitle, url)
                    withContext(Dispatchers.Main) {
                        PetalToast.show(context, R.string.toast_bookmark_updated)
                    }
                } else {
                    repo.addBookmark(Record(displayTitle, url, 0L, 0L))
                    withContext(Dispatchers.Main) {
                        PetalToast.show(context, R.string.app_done)
                    }
                }
                repo.close()
            } catch (t: Throwable) {
                android.util.Log.w("PetalBrowserViewModel", "Failed to save bookmark", t)
            }
        }
    }

    fun toggleBookmark(title: String, url: String) {
        if (url.isBlank() || PetalBrowserUtils.isHomePage(url)) {
            PetalToast.show(context, R.string.toast_homepage_cannot_bookmark)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = HistoryBookmarkRepository(context)
                repo.open(true)
                if (repo.checkBookmark(url)) {
                    repo.deleteBookmark(url)
                    withContext(Dispatchers.Main) {
                        PetalToast.show(context, "Bookmark removed")
                    }
                } else {
                    val displayTitle = title.ifBlank { url }
                    repo.addBookmark(Record(displayTitle, url, 0L, 0L))
                    withContext(Dispatchers.Main) {
                        PetalToast.show(context, R.string.app_done)
                    }
                }
                repo.close()
            } catch (t: Throwable) {
                android.util.Log.w("PetalBrowserViewModel", "Failed to toggle bookmark", t)
            }
        }
    }

    fun toggleDesktopMode(enabled: Boolean? = null) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val newTarget = enabled ?: !_isDesktopMode.value
        _isDesktopMode.value = newTarget
        sp.edit().putBoolean("sp_desktop_site", newTarget).apply()
        activeTab.value?.engineState?.engineSession?.toggleDesktopMode(newTarget, reload = true)
    }

    fun shareLink(title: String, url: String) {
        if (url.isBlank() || PetalBrowserUtils.isHomePage(url)) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, title)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.menu_share_link)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
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

    fun switchToNextTab() {
        val state = browserStore.state
        val tabs = state.tabs
        if (tabs.size <= 1) return
        val currentIndex = tabs.indexOfFirst { it.id == state.selectedTabId }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % tabs.size else 0
        selectTab(tabs[nextIndex].id)
    }

    fun switchToPreviousTab() {
        val state = browserStore.state
        val tabs = state.tabs
        if (tabs.size <= 1) return
        val currentIndex = tabs.indexOfFirst { it.id == state.selectedTabId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else tabs.size - 1
        selectTab(tabs[prevIndex].id)
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

    fun openSettings(category: SettingsCategory = SettingsCategory.OVERVIEW) {
        _settingsCategory.value = category
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

    fun openAiSheet(query: String = "") {
        _isMenuOpen.value = false
        _aiSheetQuery.value = query
        _isAiSheetOpen.value = true
    }

    fun setAiSheetOpen(open: Boolean) {
        _isAiSheetOpen.value = open
    }

    fun openExtensions() {
        _isMenuOpen.value = false
        _isExtensionsOpen.value = true
    }

    fun setExtensionsOpen(open: Boolean) {
        _isExtensionsOpen.value = open
    }

    fun openAccountSync() {
        _isMenuOpen.value = false
        _isAccountSyncOpen.value = true
    }

    fun setAccountSyncOpen(open: Boolean) {
        _isAccountSyncOpen.value = open
    }

    fun openDelete() {
        _isMenuOpen.value = false
        _isDeleteOpen.value = true
    }

    fun setDeleteOpen(open: Boolean) {
        _isDeleteOpen.value = open
    }

    fun openSafeLocker() {
        _isMenuOpen.value = false
        _isSafeLockerOpen.value = true
    }

    fun setSafeLockerOpen(open: Boolean) {
        _isSafeLockerOpen.value = open
    }

    fun openFindInPage() {
        _isMenuOpen.value = false
        _isFindInPageOpen.value = true
    }

    fun setFindInPageOpen(open: Boolean) {
        _isFindInPageOpen.value = open
        if (!open) {
            clearFindMatches()
        }
    }

    fun setFindInPageQuery(query: String) {
        _findInPageQuery.value = query
    }

    fun findAll(query: String) {
        _findInPageQuery.value = query
        activeTab.value?.engineState?.engineSession?.findAll(query)
    }

    fun findNext(forward: Boolean) {
        activeTab.value?.engineState?.engineSession?.findNext(forward)
    }

    fun clearFindMatches() {
        _findInPageQuery.value = ""
        _isFindInPageOpen.value = false
        activeTab.value?.engineState?.engineSession?.clearFindMatches()
    }

    fun openOmnibox() {
        _isMenuOpen.value = false
        _isOmniboxOpen.value = true
    }

    fun setOmniboxOpen(open: Boolean) {
        _isOmniboxOpen.value = open
    }

    fun dismissTopOverlay(): Boolean {
        if (_isOmniboxOpen.value) { _isOmniboxOpen.value = false; return true }
        if (_isMenuOpen.value) { _isMenuOpen.value = false; return true }
        if (_isAiSheetOpen.value) { _isAiSheetOpen.value = false; return true }
        if (_isExtensionsOpen.value) { _isExtensionsOpen.value = false; return true }
        if (_isSafeLockerOpen.value) { _isSafeLockerOpen.value = false; return true }
        if (_isDeleteOpen.value) { _isDeleteOpen.value = false; return true }
        if (_isAccountSyncOpen.value) { _isAccountSyncOpen.value = false; return true }
        if (_isFindInPageOpen.value) { _isFindInPageOpen.value = false; return true }
        if (_isDownloadsOpen.value) { _isDownloadsOpen.value = false; return true }
        if (_isHistoryOpen.value) { _isHistoryOpen.value = false; return true }
        if (_isBookmarksOpen.value) { _isBookmarksOpen.value = false; return true }
        if (_isSettingsOpen.value) { _isSettingsOpen.value = false; return true }
        if (_isTabSwitcherOpen.value) { _isTabSwitcherOpen.value = false; return true }
        return false
    }

    val hasOpenOverlay: Boolean
        get() = _isOmniboxOpen.value || _isTabSwitcherOpen.value || _isSettingsOpen.value ||
                _isBookmarksOpen.value || _isHistoryOpen.value ||
                _isDownloadsOpen.value || _isMenuOpen.value ||
                _isFindInPageOpen.value || _isAiSheetOpen.value ||
                _isExtensionsOpen.value || _isAccountSyncOpen.value ||
                _isDeleteOpen.value || _isSafeLockerOpen.value

    override fun onCleared() {
        super.onCleared()
        storeSubscription.unsubscribe()
    }
}
