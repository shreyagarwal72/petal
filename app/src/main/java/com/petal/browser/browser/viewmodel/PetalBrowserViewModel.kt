package com.petal.browser.browser.viewmodel

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petal.browser.R
import com.petal.browser.browser.PetalSessionRestoreManager
import com.petal.browser.browser.PetalTabMetadata
import com.petal.browser.browser.PetalTabMetadataStore
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.compose.incognito.PetalIncognitoSessionManager
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
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import javax.inject.Inject

private const val UNDO_SNAPSHOT_TTL_MS = 10 * 60 * 1000L
private const val UNDO_SNAPSHOT_MAX = 25

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
        state.tabs.filterNot { com.petal.browser.engine.gecko.PetalEngineStore.isEphemeralTabId(it.id) }.map { tab ->
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
                groupColorHex = meta?.tabGroupColorHex,
                isSleeping = com.petal.browser.engine.gecko.PetalTabSuspender.isSleeping(tab)
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

    private val _isDevConsoleOpen = MutableStateFlow(false)
    val isDevConsoleOpen: StateFlow<Boolean> = _isDevConsoleOpen.asStateFlow()

    private val _isAdBlockEnabled = MutableStateFlow(
        com.petal.browser.browser.PetalAdBlockEngine.isAdBlockEnabled(context)
    )
    val isAdBlockEnabled: StateFlow<Boolean> = _isAdBlockEnabled.asStateFlow()

    fun toggleAdBlock(enabled: Boolean? = null) {
        val target = enabled ?: !_isAdBlockEnabled.value
        com.petal.browser.browser.PetalAdBlockEngine.setAdBlockEnabled(context, target)
        _isAdBlockEnabled.value = target
    }

    private val _isDesktopMode = MutableStateFlow(
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_desktop_site", false)
    )
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    private val _bookmarkUpdateTrigger = MutableStateFlow(0L)

    fun notifyBookmarksChanged() {
        _bookmarkUpdateTrigger.value = System.currentTimeMillis()
    }

    val isCurrentUrlBookmarked: StateFlow<Boolean> = combine(activeUrl, _bookmarkUpdateTrigger) { url, _ ->
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

    /** Lets the activity capture the visible tab's thumbnail right before the tab manager opens. */
    @Volatile
    var onBeforeTabSwitcherOpen: (() -> Unit)? = null

    /**
     * Set by the Compose root. Runs on the main thread right BEFORE the tab switcher flips to open,
     * while the page is still on screen, so a real pre-hide snapshot (PixelCopy, which sees the
     * GeckoView surface) can be taken for the page -> tile morph.
     */
    @Volatile
    var beforeTabSwitcherOpen: (() -> Unit)? = null

    // The grid reads previews through tabItems; without this nudge a thumbnail stored after the
    // list was built (capture on leaving a tab, page finished loading) never reached the screen.
    private val thumbnailListener = TabThumbnailCache.ThumbnailListener { _thumbnailRevision.value = System.nanoTime() }

    init {
        TabThumbnailCache.addListener(thumbnailListener)
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
        val rawFormatted = PetalBrowserUtils.queryWrapper(context, trimmed)
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val stripTracking = sp.getBoolean("sp_strip_tracking_parameters", true)
        val formattedUrl = if (stripTracking && (rawFormatted.startsWith("http://", ignoreCase = true) || rawFormatted.startsWith("https://", ignoreCase = true))) {
            PetalBrowserUtils.cleanTrackingParams(rawFormatted) ?: rawFormatted
        } else {
            rawFormatted
        }
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

    fun goHome(isIncognito: Boolean = activeTab.value?.content?.private ?: false) {
        val homeUrl = if (isIncognito) "about:home" else PetalBrowserUtils.getEffectiveHomepageUrl(context)
        loadUrl(homeUrl)
    }

    fun openNewTab(
        url: String? = null,
        selectTab: Boolean = true,
        private: Boolean = false,
        groupId: String? = null,
        groupTitle: String? = null,
        keepSwitcherOpen: Boolean = false,
        parentId: String? = null
    ): String {
        val tabId = java.util.UUID.randomUUID().toString()
        if (!groupId.isNullOrBlank() || !parentId.isNullOrBlank()) {
            PetalTabMetadataStore.put(
                context,
                PetalTabMetadata(
                    tabId = tabId,
                    tabGroupId = groupId,
                    tabGroupTitle = groupTitle ?: if (groupId != null) "Group" else null,
                    parentId = parentId
                )
            )
        }
        val targetUrl = if (url.isNullOrBlank() || url == "about:home") {
            if (private) "about:home" else PetalBrowserUtils.getEffectiveHomepageUrl(context)
        } else {
            url
        }
        val formattedUrl = if (targetUrl.isBlank()) "about:home" else PetalBrowserUtils.queryWrapper(context, targetUrl.trim())
        val tabTitle = if (PetalBrowserUtils.isHomePage(formattedUrl)) "Petal Home" else ""
        if (selectTab) {
            PetalEngineStore.createTabSession(
                context = context,
                tabId = tabId,
                url = formattedUrl,
                title = tabTitle,
                isIncognito = private,
                select = true
            )
        } else {
            // Background tab: no Gecko session until the user opens it (Fenix lazy tab).
            PetalEngineStore.createLazyTab(
                context = context,
                tabId = tabId,
                url = formattedUrl,
                title = tabTitle,
                isIncognito = private
            )
        }
        if (private) {
            PetalIncognitoSessionManager.syncIncognitoState(context)
        }
        // keepSwitcherOpen: a replacement tab created because the user closed the LAST tab from the
        // tab manager must not dismiss the manager (that flashed the home page over it).
        if (_isTabSwitcherOpen.value && !keepSwitcherOpen) {
            _isTabSwitcherOpen.value = false
        }
        return tabId
    }

    /**
     * Home tab that was auto-created when the user closed their last tab from the tab manager.
     * Undo removes it again so restoring the closed tab doesn't leave a stray blank tab behind.
     */
    private var placeholderHomeTabId: String? = null

    private fun openPlaceholderHomeTab() {
        placeholderHomeTabId = openNewTab("about:home", selectTab = true, keepSwitcherOpen = _isTabSwitcherOpen.value)
    }

    private fun dropPlaceholderHomeTab() {
        val id = placeholderHomeTabId ?: return
        placeholderHomeTabId = null
        val tabs = browserStore.state.tabs
        val tab = tabs.firstOrNull { it.id == id } ?: return
        if (tabs.size < 2 || !PetalBrowserUtils.isHomePage(tab.content.url)) return
        browserStore.dispatch(TabListAction.RemoveTabAction(id))
        PetalTabMetadataStore.remove(context, id)
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

    // ── Closed-tab undo ────────────────────────────────────────────────────────────────────
    // Same idea as Mozilla's UndoMiddleware: keep the tab's full state (engine session state with
    // back/forward history and scroll, container, timestamps) plus its thumbnail and sidecar
    // metadata for a short while, so Undo brings back the SAME tab instead of a fresh one that only
    // knows the URL. Private tabs are never kept.
    private class ClosedTabSnapshot(
        val tab: TabSessionState,
        val thumbnail: android.graphics.Bitmap?,
        val metadata: PetalTabMetadata?,
        val closedAtMs: Long
    )

    private val closedSnapshots = com.petal.browser.browser.ClosedTabBuffer<ClosedTabSnapshot>(UNDO_SNAPSHOT_MAX, UNDO_SNAPSHOT_TTL_MS)

    private fun snapshotBeforeClose(tab: TabSessionState) {
        if (tab.content.private) return
        try {
            // Live history state is continuously synchronized to tab.engineState.engineSessionState by Mozilla engine delegates.
            val saved = tab.engineState.engineSessionState
            val thumb = TabThumbnailCache.getMemoryOrDisk(tab.id)?.let { b ->
                if (b.isRecycled) null else try { b.copy(b.config ?: android.graphics.Bitmap.Config.ARGB_8888, false) } catch (_: Throwable) { null }
            }
            val snap = ClosedTabSnapshot(
                tab = tab.copy(engineState = EngineState(engineSessionState = saved)),
                thumbnail = thumb,
                metadata = PetalTabMetadataStore.get(context, tab.id),
                closedAtMs = android.os.SystemClock.elapsedRealtime()
            )
            closedSnapshots.put(tab.id, snap, snap.closedAtMs)
        } catch (t: Throwable) {
            android.util.Log.w("PetalBrowserViewModel", "snapshotBeforeClose failed: ${t.message}")
        }
    }

    /** Re-adds a just-closed tab exactly as it was. Returns false if no snapshot is available. */
    private fun restoreFromSnapshot(tabId: String): Boolean {
        val snap = closedSnapshots.take(tabId) ?: return false
        if (browserStore.state.tabs.any { it.id == tabId }) return true
        browserStore.dispatch(TabListAction.AddTabAction(snap.tab, select = false))
        snap.metadata?.let { PetalTabMetadataStore.put(context, it) }
        snap.thumbnail?.let { TabThumbnailCache.put(tabId, it, false, true) }
        // Undo consumes the vault entry so the tab is not listed as both open and closed.
        PetalRecentlyClosedManager.removeClosedTab(tabId)
        return true
    }

    fun closeTab(tabId: String) {
        val state = browserStore.state
        val tabToClose = state.tabs.firstOrNull { it.id == tabId }
        tabToClose?.let { snapshotBeforeClose(it) }
        val isPrivate = tabToClose?.content?.private == true
        val meta = PetalTabMetadataStore.get(context, tabId)
        if (tabToClose != null && !isPrivate) {
            PetalRecentlyClosedManager.pushClosedTab(
                id = tabToClose.id,
                title = meta?.customTitle ?: tabToClose.content.title,
                url = tabToClose.content.url,
                isIncognito = false,
                groupId = meta?.tabGroupId,
                groupTitle = meta?.tabGroupTitle,
                groupColorHex = meta?.tabGroupColorHex
            )
        }
        val isSelected = state.selectedTabId == tabId
        val parentId = meta?.parentId
        val parentStillOpen = parentId != null && state.tabs.any { it.id == parentId && it.id != tabId }
        if (isSelected && parentStillOpen && parentId != null) {
            tabsUseCases.selectTab(parentId)
        }
        tabsUseCases.removeTab(tabId)
        PetalTabMetadataStore.remove(context, tabId)
        if (isPrivate) {
            PetalIncognitoSessionManager.syncIncognitoState(context)
        }
        if (browserStore.state.tabs.isEmpty()) {
            openPlaceholderHomeTab()
        }
    }

    /** Moves tabs untouched for the configured number of days into Recently Closed. Returns the count. */
    fun archiveStaleTabs(now: Long = System.currentTimeMillis()): Int {
        val days = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getString(com.petal.browser.browser.PetalTabArchiver.PREF_AUTO_ARCHIVE_DAYS, "0")?.toIntOrNull() ?: 0
        val st = browserStore.state
        val ids = com.petal.browser.browser.PetalTabArchiver.idsToArchive(
            st.tabs.filterNot { com.petal.browser.engine.gecko.PetalEngineStore.isEphemeralTabId(it.id) }.map {
                com.petal.browser.browser.HygieneTab(
                    id = it.id, url = it.content.url, isPrivate = it.content.private,
                    isSelected = it.id == st.selectedTabId,
                    isPinned = PetalTabMetadataStore.isPinned(context, it.id),
                    lastAccess = it.lastAccess,
                    hasActiveMedia = com.petal.browser.engine.gecko.PetalTabSuspender.hasActiveMedia(it)
                )
            }, now, days
        )
        if (ids.isNotEmpty()) closeTabs(ids)
        return ids.size
    }

    fun closeTabs(tabIds: Collection<String>) {
        val state = browserStore.state
        var hadPrivate = false
        for (id in tabIds) {
            val tabToClose = state.tabs.firstOrNull { it.id == id }
            if (tabToClose != null) {
                snapshotBeforeClose(tabToClose)
                if (!tabToClose.content.private) {
                    val meta = PetalTabMetadataStore.get(context, id)
                    PetalRecentlyClosedManager.pushClosedTab(
                        id = tabToClose.id,
                        title = meta?.customTitle ?: tabToClose.content.title,
                        url = tabToClose.content.url,
                        isIncognito = false,
                        groupId = meta?.tabGroupId,
                        groupTitle = meta?.tabGroupTitle,
                        groupColorHex = meta?.tabGroupColorHex
                    )
                } else {
                    hadPrivate = true
                }
            }
            PetalTabMetadataStore.remove(context, id)
        }
        browserStore.dispatch(TabListAction.RemoveTabsAction(tabIds.toList()))
        if (hadPrivate) {
            PetalIncognitoSessionManager.syncIncognitoState(context)
        }
        if (browserStore.state.tabs.isEmpty()) {
            openPlaceholderHomeTab()
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
        if (!tab.isIncognito && restoreFromSnapshot(tab.id)) {
            dropPlaceholderHomeTab()
            return
        }
        dropPlaceholderHomeTab()
        val url = if (tab.url.isBlank() || tab.url == "Petal Home" || tab.url.equals("about:blank", ignoreCase = true)) {
            "about:home"
        } else {
            tab.url
        }
        openNewTab(url = url, selectTab = false, private = tab.isIncognito)
    }

    fun duplicateTab(tab: PetalTabItem) {
        val url = if (tab.url.isBlank() || tab.url == "Petal Home") "about:home" else tab.url
        val meta = PetalTabMetadataStore.get(context, tab.id)
        val newTabId = openNewTab(
            url = url,
            selectTab = true,
            private = tab.isIncognito,
            groupId = meta?.tabGroupId,
            groupTitle = meta?.tabGroupTitle,
            parentId = tab.id
        )
        if (meta?.workspaceId != null) {
            PetalTabMetadataStore.setWorkspace(context, newTabId, meta.workspaceId)
        }
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
                _bookmarkUpdateTrigger.value = System.currentTimeMillis()
            } catch (t: Throwable) {
                android.util.Log.w("PetalBrowserViewModel", "Failed to toggle bookmark", t)
            }
        }
    }

    fun copyCleanLink(url: String) {
        if (url.isNotBlank() && !PetalBrowserUtils.isHomePage(url)) {
            val cleanUrl = PetalBrowserUtils.cleanTrackingParams(url)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Clean URL", cleanUrl)
            clipboard?.setPrimaryClip(clip)
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.7f)
            PetalToast.show(context, context.getString(R.string.ui_clean_url_copied))
        }
    }

    fun toggleDesktopMode(enabled: Boolean? = null) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val newTarget = enabled ?: !_isDesktopMode.value
        _isDesktopMode.value = newTarget
        val activeUrl = activeTab.value?.content?.url.orEmpty()
        val host = try { android.net.Uri.parse(activeUrl).host } catch (_: Throwable) { null }
        val editor = sp.edit().putBoolean("sp_desktop_site", newTarget)
        if (!host.isNullOrBlank()) {
            editor.putBoolean("sp_desktop_site_$host", newTarget)
        }
        editor.apply()
        activeTab.value?.engineState?.engineSession?.let { session ->
            try {
                val gs = com.petal.browser.engine.gecko.PetalEngineBridge.extractGeckoSession(session)
                gs?.settings?.let { s ->
                    s.viewportMode = if (newTarget) org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                    s.userAgentMode = if (newTarget) org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                }
            } catch (_: Throwable) {}
            session.toggleDesktopMode(newTarget, reload = true)
        }
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

        val tab = activeTab.value ?: return false
        val currentUrl = tab.content.url
        val effectiveHome = if (tab.content.private) "about:home" else PetalBrowserUtils.getEffectiveHomepageUrl(context)
        val isAlreadyHome = PetalBrowserUtils.isHomePage(currentUrl) ||
                currentUrl.trim().trimEnd('/').equals(effectiveHome.trim().trimEnd('/'), ignoreCase = true)
        val meta = PetalTabMetadataStore.get(context, tab.id)
        val totalTabs = browserStore.state.tabs.size

        val target = com.petal.browser.browser.rules.PetalRootTabBackRules.decide(
            isOverlayShowing = false,
            isFullscreen = false,
            hasDialog = false,
            canGoBack = tab.content.canGoBack,
            isHomePage = isAlreadyHome,
            totalTabs = totalTabs,
            hasOpener = !meta?.parentId.isNullOrBlank(),
            isPinned = meta?.isPinned == true,
            confirmExitEnabled = false
        )

        return when (target) {
            com.petal.browser.browser.rules.PetalBackTarget.WEB_HISTORY -> {
                sessionUseCases.goBack(tab.id)
                true
            }
            com.petal.browser.browser.rules.PetalBackTarget.RETURN_TO_HOME -> {
                loadUrl(effectiveHome)
                true
            }
            com.petal.browser.browser.rules.PetalBackTarget.RETURN_TO_OPENER -> {
                meta?.parentId?.let { selectTab(it); true } ?: false
            }
            com.petal.browser.browser.rules.PetalBackTarget.CLOSE_AND_RETURN_TO_OPENER,
            com.petal.browser.browser.rules.PetalBackTarget.CLOSE_TAB_AND_PREVIOUS -> {
                closeTab(tab.id)
                true
            }
            com.petal.browser.browser.rules.PetalBackTarget.CONFIRM_EXIT_DIALOG,
            com.petal.browser.browser.rules.PetalBackTarget.DELEGATE_TO_SYSTEM -> false
            else -> false
        }
    }

    /** Back found nothing to go back to although the engine said it could: leave the page for Home. */
    fun goHomeFromStuckBack() {
        val tab = activeTab.value ?: return
        val effectiveHome = if (tab.content.private) "about:home" else PetalBrowserUtils.getEffectiveHomepageUrl(context)
        val isAlreadyHome = PetalBrowserUtils.isHomePage(tab.content.url) ||
                tab.content.url.trim().trimEnd('/').equals(effectiveHome.trim().trimEnd('/'), ignoreCase = true)
        if (tab.content.url.isNotBlank() && !isAlreadyHome) loadUrl(effectiveHome)
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
        setTabSwitcherOpen(!_isTabSwitcherOpen.value)
    }

    fun setTabSwitcherOpen(open: Boolean) {
        if (open && !_isTabSwitcherOpen.value) {
            try { beforeTabSwitcherOpen?.invoke() } catch (_: Throwable) {}
            try { onBeforeTabSwitcherOpen?.invoke() } catch (_: Throwable) {}
        }
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

    fun openDevConsole() {
        _isMenuOpen.value = false
        _isDevConsoleOpen.value = true
    }

    fun setDevConsoleOpen(open: Boolean) {
        _isDevConsoleOpen.value = open
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

    private val _omniboxInitialQuery = MutableStateFlow<String?>(null)
    val omniboxInitialQuery: StateFlow<String?> = _omniboxInitialQuery.asStateFlow()

    fun openOmnibox(initialQuery: String? = null) {
        _isMenuOpen.value = false
        _omniboxInitialQuery.value = initialQuery
        _isOmniboxOpen.value = true
    }

    fun setOmniboxOpen(open: Boolean) {
        _isOmniboxOpen.value = open
        if (!open) {
            _omniboxInitialQuery.value = null
        }
    }

    fun dismissTopOverlay(): Boolean {
        if (_isOmniboxOpen.value) { _isOmniboxOpen.value = false; return true }
        if (_isMenuOpen.value) { _isMenuOpen.value = false; return true }
        if (_isDevConsoleOpen.value) { _isDevConsoleOpen.value = false; return true }
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
                _isDevConsoleOpen.value ||
                _isExtensionsOpen.value || _isAccountSyncOpen.value ||
                _isDeleteOpen.value || _isSafeLockerOpen.value

    override fun onCleared() {
        super.onCleared()
        TabThumbnailCache.removeListener(thumbnailListener)
        storeSubscription.unsubscribe()
    }
}
