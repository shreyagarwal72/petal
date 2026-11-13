package com.petal.browser.compose.tabs

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.components.PetalExpressiveScrollBar
import com.petal.browser.ui.components.PetalNewTabOrigin
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.components.rememberReduceMotion
import com.petal.browser.ui.components.rememberLowEndDevice
import com.petal.browser.ui.theme.layout.petalFlingBehavior
import kotlinx.coroutines.Job

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.petal.browser.ui.components.PetalConfirmSheetContent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.AnimatedCounterBadge
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.ExpressiveTabGroupPill
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.PetalSnackbarHost
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.isLiquidGlassEnabled

import com.petal.browser.ui.containment.PetalContainmentShapes
import com.petal.browser.ui.components.PetalExpressiveMenuItem
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.PetalRecentlyClosedManager
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Layout mode for the tab grid. Toggled from the top bar's layout-toggle icon button.
 * The grid (2-column) is the primary, spec-driven presentation; list is a secondary,
 * denser alternative for users with many open tabs.
 */
enum class TabDisplayMode {
    GRID,
    LIST
}

/**
 * Which set of tabs the top segmented pill switcher currently shows: Regular, Groups, Incognito.
 */
enum class TabCategory {
    REGULAR,
    GROUPS,
    INCOGNITO
}

/**
 * A single tab as rendered by [PetalTabGridSwitcher].
 */
data class PetalTabItem(
    val id: String,
    val thumbnailKey: String = id,
    val title: String,
    val url: String,
    val faviconBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val isIncognito: Boolean = false,
    val isSelected: Boolean = false,
    val isPinned: Boolean = false,
    val isPinnedGlobal: Boolean = true,
    val workspaceId: String? = null,
    val isMuted: Boolean = false,
    val groupId: String? = null,
    val groupTitle: String? = null,
    val groupColorHex: String? = null
)

/**
 * Snapshot record of a closed tab pushed to the LIFO undo stack.
 */
data class ClosedTabEntry(
    val tab: PetalTabItem,
    val originalIndex: Int = -1,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Item union for the tab manager grid/list, supporting collapsible folder headers and individual tab cards.
 */
sealed interface SwitcherGridItem {
    data class FolderHeader(val group: PetalTabGroup, val count: Int, val isCollapsed: Boolean) : SwitcherGridItem
    data class Tab(val item: PetalTabItem) : SwitcherGridItem
}

/**
 * Full-screen, Chrome-style tab manager with drag-to-merge Tab Groups.
 */
@Composable
fun PetalTabGridSwitcher(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    tabs: List<PetalTabItem>,
    onTabSelect: (PetalTabItem) -> Unit,
    onTabClose: (PetalTabItem) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAllTabs: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onTabVisible: (PetalTabItem) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onRestoreTab: ((PetalTabItem) -> Unit)? = null,
    onBookmarkTab: (PetalTabItem) -> Unit = {},
    onShareTab: (PetalTabItem) -> Unit = {},
    onMuteTab: (PetalTabItem, Boolean) -> Unit = { _, _ -> },
    onCreateGroup: (PetalTabItem) -> Unit = {},
    onDuplicateTab: (PetalTabItem) -> Unit = {},
    onCloseOtherTabs: (PetalTabItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var searchQuery by remember { mutableStateOf("") }
    var displayMode by remember {
        val savedMode = sp.getString("sp_tab_display_mode", "GRID") ?: "GRID"
        mutableStateOf(try { TabDisplayMode.valueOf(savedMode) } catch (e: Exception) { TabDisplayMode.GRID })
    }
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuTabId by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedTabIds = remember { mutableStateListOf<String>() }
    val pinnedTabIds = remember {
        mutableStateListOf<String>().apply {
            addAll(sp.getStringSet("sp_pinned_tab_ids", emptySet()) ?: emptySet())
        }
    }

    // ── Zen Browser Workspace and Glance State ──
    val workspaces by PetalWorkspaceManager.workspaces.collectAsState()
    val activeWorkspaceId by PetalWorkspaceManager.activeWorkspaceId.collectAsState()
    var editingWorkspace by remember { mutableStateOf<PetalWorkspace?>(null) }
    var creatingWorkspace by remember { mutableStateOf(false) }
    var glanceTab by remember { mutableStateOf<PetalTabItem?>(null) }

    LaunchedEffect(Unit) {
        PetalWorkspaceManager.init(context)
    }

    // State for the "Add selected tabs to group" dialog
    var showAddToGroupDialog by remember { mutableStateOf(false) }

    // State for in-Compose close-all-tabs confirmation dialog
    var showCloseAllConfirmDialog by remember { mutableStateOf(false) }

    fun persistPinnedTabs() {
        sp.edit().putStringSet("sp_pinned_tab_ids", pinnedTabIds.toSet()).apply()
    }
    fun togglePinned(tab: PetalTabItem) {
        val willPin = !pinnedTabIds.contains(tab.id) && !tab.isPinned
        if (willPin) {
            if (!pinnedTabIds.contains(tab.id)) pinnedTabIds.add(tab.id)
        } else {
            pinnedTabIds.remove(tab.id)
        }
        persistPinnedTabs()
        com.petal.browser.browser.PetalTabMetadataStore.setPinned(context, tab.id, willPin)
        if (tab.thumbnailKey.isNotBlank() && tab.thumbnailKey != tab.id) {
            com.petal.browser.browser.PetalTabMetadataStore.setPinned(context, tab.thumbnailKey, willPin)
        }
    }

    fun moveToWorkspace(tab: PetalTabItem, workspaceId: String) {
        com.petal.browser.browser.PetalTabMetadataStore.setWorkspace(context, tab.id, workspaceId)
        if (tab.thumbnailKey.isNotBlank() && tab.thumbnailKey != tab.id) {
            com.petal.browser.browser.PetalTabMetadataStore.setWorkspace(context, tab.thumbnailKey, workspaceId)
        }
    }

    // Sync tab group definitions
    var tabGroups by remember { mutableStateOf(PetalTabGroupManager.getAllGroups(context)) }
    fun refreshGroups() {
        tabGroups = PetalTabGroupManager.getAllGroups(context)
    }

    fun moveToGroup(tab: PetalTabItem, groupId: String?) {
        if (groupId == null) {
            tab.groupId?.let { gid ->
                PetalTabGroupManager.removeTabFromGroup(context, gid, tab.id)
            }
        } else {
            PetalTabGroupManager.addTabToGroup(context, groupId, tab.id)
        }
        refreshGroups()
    }
    fun enterSelection(tab: PetalTabItem) {
        selectionMode = true
        if (!selectedTabIds.contains(tab.id)) selectedTabIds.add(tab.id)
    }
    fun leaveSelectionMode() {
        selectedTabIds.clear()
        selectionMode = false
    }
    fun selectAll(tabList: List<PetalTabItem>) {
        selectedTabIds.clear()
        selectedTabIds.addAll(tabList.map { it.id })
    }
    // "Biometric incognito tab lock" (sp_biometric_incognito_tab_lock): the private tab list stays
    // hidden until the user authenticates once per switcher visit. Read live from prefs.
    fun incognitoLockRequired(): Boolean =
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("sp_biometric_incognito_tab_lock", false) &&
            com.petal.browser.security.BiometricLockManager.canAuthenticate(context)
    var incognitoUnlocked by remember { mutableStateOf(false) }
    fun withIncognitoAccess(then: () -> Unit) {
        val act = context as? androidx.fragment.app.FragmentActivity
        if (incognitoUnlocked || act == null || !incognitoLockRequired()) {
            incognitoUnlocked = true
            then()
            return
        }
        com.petal.browser.security.BiometricLockManager.authenticate(
            activity = act,
            title = "Unlock Incognito Tabs",
            subtitle = "Verify your identity to view private tabs",
            onSuccess = Runnable {
                incognitoUnlocked = true
                then()
            },
            onError = java.util.function.Consumer { }
        )
    }
    val initialCategory = remember {
        val lockOn = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("sp_biometric_incognito_tab_lock", false)
        if (!lockOn && tabs.any { it.isSelected && it.isIncognito }) TabCategory.INCOGNITO else TabCategory.REGULAR
    }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    LaunchedEffect(tabs) {
        val openTabIds = tabs.map { it.id }.toSet()
        pinnedTabIds.removeAll { it !in openTabIds }
        persistPinnedTabs()
        selectedTabIds.removeAll { it !in openTabIds }
        if (selectedTabIds.isEmpty()) selectionMode = false
        PetalTabGroupManager.syncWithOpenTabs(context, openTabIds)
        refreshGroups()
    }

    // Drag-and-drop tab merging bookkeeping
    var draggingTabId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverTargetTabId by remember { mutableStateOf<String?>(null) }
    val tabCardBounds = remember { mutableStateMapOf<String, Rect>() }

    // Group inspection modal state
    var inspectingGroup by remember { mutableStateOf<PetalTabGroup?>(null) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // ── Long scroll up / pull-down reveal for Closed Tabs Vault ──
    var pullToRevealLockOffset by remember { mutableFloatStateOf(0f) }
    var isVaultVisible by remember { mutableStateOf(false) }
    val maxRevealThreshold = 225f // Reveal point on the damped pull axis
    val maxPullCap = 300f // Hard cap: the pull can never stretch past this, so it feels like a wall

    val vaultNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If pulled down and now user is scrolling back up, consume and reduce offset
                if (pullToRevealLockOffset > 0f && available.y < 0) {
                    val consumed = available.y
                    pullToRevealLockOffset = (pullToRevealLockOffset + consumed).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Check if grid/list is at top
                val isAtTop = if (displayMode == TabDisplayMode.GRID) {
                    gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                } else {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                }

                // Only a deliberate finger drag counts. Flings / momentum that run into the top
                // edge used to fill the pull, which made the vault open from a simple fast scroll.
                if (isAtTop && available.y > 0 && source == NestedScrollSource.UserInput) {
                    // Rubber-band resistance: starts firm and gets heavier as the pull grows,
                    // so reaching the threshold needs a long, intentional drag (~1000px).
                    val progress = (pullToRevealLockOffset / maxPullCap).coerceIn(0f, 1f)
                    val resistance = 0.6f * Math.pow((1f - progress).toDouble(), 1.5).toFloat()
                    val newOffset = (pullToRevealLockOffset + available.y * resistance).coerceAtMost(maxPullCap)
                    if (newOffset >= maxRevealThreshold && pullToRevealLockOffset < maxRevealThreshold) {
                        com.petal.browser.haptics.PetalHapticEngine.getInstance(context).playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
                    }
                    pullToRevealLockOffset = newOffset
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pullToRevealLockOffset >= maxRevealThreshold) {
                    pullToRevealLockOffset = 0f
                    isVaultVisible = true
                } else {
                    pullToRevealLockOffset = 0f
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    val animatedLockPullOffset by animateFloatAsState(
        targetValue = pullToRevealLockOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lockPullOffset"
    )

    val effectiveOnBack: () -> Unit = remember(onBack, context) {
        onBack ?: {
            (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
            Unit
        }
    }

    val pendingRemovalIds = remember { mutableStateListOf<String>() }
    val undoStack = remember { mutableStateListOf<ClosedTabEntry>() }
    var isUndoBannerVisible by remember { mutableStateOf(false) }
    var undoBannerKey by remember { mutableLongStateOf(0L) }
    var newTabLaunchKey by remember { mutableLongStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    val newTabExitProgress by animateFloatAsState(
        targetValue = if (newTabLaunchKey == 0L) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "tabManagerNewTabExit",
    )

    LaunchedEffect(undoBannerKey) {
        if (undoBannerKey > 0L && isUndoBannerVisible) {
            kotlinx.coroutines.delay(4500L)
            isUndoBannerVisible = false
            undoStack.clear()
        }
    }

    fun requestOptimisticClose(tab: PetalTabItem) {
        if (tab.id in pendingRemovalIds) return
        val originalIndex = tabs.indexOfFirst { it.id == tab.id }.takeIf { it >= 0 } ?: tabs.size
        pendingRemovalIds.add(tab.id)
        undoStack.add(ClosedTabEntry(tab = tab, originalIndex = originalIndex))
        undoBannerKey = System.currentTimeMillis()
        isUndoBannerVisible = true
        onTabClose(tab)
    }

    fun undoClosedTabs() {
        if (undoStack.isNotEmpty()) {
            val topEntry = undoStack.removeAt(undoStack.lastIndex)
            pendingRemovalIds.remove(topEntry.tab.id)
            onRestoreTab?.invoke(topEntry.tab)
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).playClick(context)
            if (undoStack.isNotEmpty()) {
                undoBannerKey = System.currentTimeMillis()
                isUndoBannerVisible = true
            } else {
                isUndoBannerVisible = false
            }
        } else {
            PetalRecentlyClosedManager.popLastClosedTab()?.let { rec ->
                val restoredTab = PetalTabItem(
                    id = rec.id,
                    title = rec.title,
                    url = rec.url,
                    isIncognito = rec.isIncognito,
                    groupId = rec.groupId,
                    groupTitle = rec.groupTitle,
                    groupColorHex = rec.groupColorHex
                )
                isUndoBannerVisible = false
                pendingRemovalIds.remove(restoredTab.id)
                onRestoreTab?.invoke(restoredTab)
                com.petal.browser.haptics.PetalHapticEngine.getInstance(context).playClick(context)
            }
        }
    }

    fun commitPendingRemovals() {
        if (pendingRemovalIds.isEmpty()) return
        val idsToCommit = pendingRemovalIds.toList()
        pendingRemovalIds.clear()
        idsToCommit.forEach { id ->
            tabs.find { it.id == id }?.let { onTabClose(it) }
        }
    }

    val reduceMotion = rememberReduceMotion()
    val isLowEnd = rememberLowEndDevice()
    val hero = remember { TabHeroState(startVisible = backgroundSnapshot != null).also { it.image = backgroundSnapshot } }
    var currentHeroJob by remember { mutableStateOf<Job?>(null) }
    var newTabButtonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val closingTabIds = remember { mutableStateListOf<String>() }

    fun initiateTabClose(tab: PetalTabItem) {
        if (closingTabIds.contains(tab.id)) return
        closingTabIds.add(tab.id)
        coroutineScope.launch {
            com.petal.browser.motion.MotionGate.withMotion("tab_close") {
                kotlinx.coroutines.delay(180L)
            }
            requestOptimisticClose(tab)
            closingTabIds.remove(tab.id)
        }
    }

    fun launchNewTab(isIncognito: Boolean, originBounds: androidx.compose.ui.geometry.Rect? = null, originRadius: Float? = null) {
        newTabLaunchKey = System.currentTimeMillis()
        commitPendingRemovals()
        currentHeroJob?.cancel()
        currentHeroJob = coroutineScope.launch {
            val origin = originBounds ?: newTabButtonBounds ?: PetalNewTabOrigin.peek()
            val rad = originRadius ?: PetalNewTabOrigin.getRadius().takeIf { it > 0f } ?: (origin?.height?.div(2f) ?: 24f)
            hero.busy = true
            hero.customOriginBounds = origin
            hero.customOriginRadius = rad
            hero.tabId = null
            hero.image = null
            hero.fillColor = if (isIncognito) androidx.compose.ui.graphics.Color(0xFF1C1D24) else androidx.compose.ui.graphics.Color.White
            hero.visible = true
            hero.progress.snapTo(1f)
            com.petal.browser.motion.MotionGate.withMotion("new_tab_grow") {
                if (reduceMotion) {
                    hero.progress.snapTo(0f)
                } else {
                    hero.progress.animateTo(0f, TabHeroState.spec())
                }
            }
            onNewTab(isIncognito)
            kotlinx.coroutines.delay(700L)
            hero.dock()
        }
    }

    // Tab counts
    val regularTabCount = tabs.count { !it.isIncognito }
    // Header count must match what the grid/workspace chips show for the active workspace.
    val activeWorkspaceTabCount = tabs.count { tab ->
        !tab.isIncognito && (tab.workspaceId == activeWorkspaceId || (tab.workspaceId == null && activeWorkspaceId == PetalWorkspaceManager.DEFAULT_WORKSPACE_ID) || (tab.isPinned && tab.isPinnedGlobal))
    }
    val incognitoTabCount = tabs.count { it.isIncognito }
    val groupsCount = tabGroups.size

    val regularTabs = remember(tabs, pendingRemovalIds) {
        tabs.filter { !it.isIncognito && it.id !in pendingRemovalIds }
    }
    val tabCountsByWorkspace = remember(regularTabs, workspaces) {
        workspaces.associate { ws ->
            ws.id to regularTabs.count { tab ->
                (tab.workspaceId == ws.id) || (tab.workspaceId == null && ws.isDefault)
            }
        }
    }
    val currentPinnedTabs = remember(regularTabs, pinnedTabIds, activeWorkspaceId) {
        regularTabs.filter { tab ->
            (pinnedTabIds.contains(tab.id) || tab.isPinned) &&
            (tab.isPinnedGlobal || tab.workspaceId == activeWorkspaceId || (tab.workspaceId == null && activeWorkspaceId == PetalWorkspaceManager.DEFAULT_WORKSPACE_ID))
        }
    }

    // Category tabs & filtering
    val categoryTabs = when (selectedCategory) {
        TabCategory.REGULAR -> {
            regularTabs.filter { tab ->
                (tab.workspaceId == activeWorkspaceId) ||
                (tab.workspaceId == null && activeWorkspaceId == PetalWorkspaceManager.DEFAULT_WORKSPACE_ID) ||
                (tab.isPinned && tab.isPinnedGlobal)
            }
        }
        TabCategory.INCOGNITO -> tabs.filter { it.isIncognito }
        TabCategory.GROUPS -> regularTabs
    }
    val visibleTabs = categoryTabs.filter { it.id !in pendingRemovalIds }
    val filteredTabs = visibleTabs
        .sortedWith(compareByDescending { pinnedTabIds.contains(it.id) || it.isPinned })
        .filter { tab ->
            searchQuery.isBlank() ||
                tab.title.contains(searchQuery, ignoreCase = true) ||
                tab.url.contains(searchQuery, ignoreCase = true) ||
                tab.groupTitle?.contains(searchQuery, ignoreCase = true) == true
        }
        .distinctBy { it.id }

    val filteredGroups = tabGroups.filter { group ->
        if (searchQuery.isBlank()) true
        else {
            group.title.contains(searchQuery, ignoreCase = true) ||
                group.tabIds.any { tid ->
                    val t = tabs.find { it.id == tid }
                    t != null && (t.title.contains(searchQuery, ignoreCase = true) || t.url.contains(searchQuery, ignoreCase = true))
                }
        }
    }.distinctBy { it.id }


    var hasScrolledToSelected by remember { mutableStateOf(false) }
    LaunchedEffect(filteredTabs) {
        if (!hasScrolledToSelected && filteredTabs.isNotEmpty()) {
            val targetIndex = filteredTabs.indexOfFirst { it.isSelected }
            if (targetIndex >= 0) {
                gridState.scrollToItem(targetIndex)
                listState.scrollToItem(targetIndex)
                hasScrolledToSelected = true
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val topBarColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    BackHandler(enabled = tabs.isEmpty()) {
        effectiveOnBack()
    }

    // ── Chrome-style hero transition (page <-> tile) ─────────────────────────────
    // Seed the page snapshot immediately: the overlay is presented without the generic fade-in now, so
    // the very first frame must already show the page (otherwise it flashes blank before the morph).
    fun heroImageFor(tab: PetalTabItem?): androidx.compose.ui.graphics.ImageBitmap? {
        val thumb = tab?.previewBitmap?.takeIf { !it.isRecycled }?.asImageBitmap()
        return if (tab?.isSelected == true) (backgroundSnapshot ?: thumb) else (thumb ?: backgroundSnapshot)
    }

    // Open: the page shrinks into the selected tile.
    LaunchedEffect(Unit) {
        if (!hero.visible) return@LaunchedEffect
        val current = tabs.firstOrNull { it.isSelected }
        hero.tabId = current?.id
        hero.image = backgroundSnapshot
        // Wait (briefly) for the tile to be laid out so we know where to land.
        kotlinx.coroutines.withTimeoutOrNull(500) {
            snapshotFlow { hero.tabId?.let { tabCardBounds[it] } }
                .filter { it != null }
                .first()
        }
        hero.progress.snapTo(0f)
        com.petal.browser.motion.MotionGate.withMotion("tab_switcher_open") {
            if (reduceMotion) {
                hero.progress.snapTo(1f)
            } else {
                hero.progress.animateTo(1f, TabHeroState.spec())
            }
        }
        hero.visible = false
    }

    // Select: the tapped tile expands to the full page, then the tab is shown.
    val heroSelect: (PetalTabItem) -> Unit = { tab ->
        currentHeroJob?.cancel()
        val image = heroImageFor(tab)
        if (image == null || tabCardBounds[tab.id] == null) {
            onTabSelect(tab)
        } else {
            currentHeroJob = coroutineScope.launch {
                hero.busy = true
                hero.tabId = tab.id
                hero.image = image
                hero.customOriginBounds = null
                hero.customOriginRadius = null
                hero.visible = true
                if (!hero.visible || hero.progress.value >= 1f) {
                    hero.progress.snapTo(1f)
                }
                com.petal.browser.motion.MotionGate.withMotion("tab_switcher_close") {
                    if (reduceMotion) {
                        hero.progress.snapTo(0f)
                    } else {
                        hero.progress.animateTo(0f, TabHeroState.spec())
                    }
                }
                onTabSelect(tab)
                // Safety net: if the host did not tear this screen down, don't leave the overlay stuck.
                kotlinx.coroutines.delay(700)
                hero.dock()
            }
        }
    }

    // Predictive back: the tile grows with the gesture; commit expands fully, cancel docks it back.
    val predictiveBackOn by com.petal.browser.predictive.PetalPredictiveJunction.isPredictiveBackEnabled.collectAsStateWithLifecycle()
    androidx.activity.compose.PredictiveBackHandler(
        enabled = tabs.isNotEmpty()
    ) { progressFlow ->
        currentHeroJob?.cancel()
        val target = tabs.firstOrNull { it.isSelected } ?: tabs.first()
        val image = heroImageFor(target)
        if (!predictiveBackOn || image == null || tabCardBounds[target.id] == null) {
            // No hero available: plain back.
            progressFlow.collect { }
            effectiveOnBack()
            return@PredictiveBackHandler
        }
        hero.tabId = target.id
        hero.image = image
        hero.customOriginBounds = null
        hero.customOriginRadius = null
        hero.visible = true
        hero.progress.snapTo(1f)
        try {
            progressFlow.collect { event ->
                hero.progress.snapTo(1f - 0.45f * event.progress)
            }
            hero.busy = true
            currentHeroJob = coroutineScope.launch {
                com.petal.browser.motion.MotionGate.withMotion("tab_switcher_close") {
                    if (reduceMotion) {
                        hero.progress.snapTo(0f)
                    } else {
                        hero.progress.animateTo(0f, TabHeroState.spec())
                    }
                }
                effectiveOnBack()
                kotlinx.coroutines.delay(700)
                hero.dock()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            currentHeroJob = coroutineScope.launch {
                com.petal.browser.motion.MotionGate.withMotion("tab_switcher_cancel") {
                    if (reduceMotion) {
                        hero.progress.snapTo(1f)
                    } else {
                        hero.progress.animateTo(1f, TabHeroState.spec())
                    }
                }
                hero.dock()
            }
            throw e
        }
    }


    val snackbarHostState = remember { SnackbarHostState() }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = tabs.isEmpty(), // the grid has its own hero-driven predictive back above when tabs are present
        onBack = effectiveOnBack,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { PetalSnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    val hp = hero.progress.value
                    alpha = ((hp - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    // Depth: the grid settles up from slightly behind as the page shrinks into its tile.
                    val gs = 0.94f + 0.06f * hp.coerceIn(0f, 1f)
                    scaleX = gs
                    scaleY = gs
                }
                .padding(innerPadding)
        ) {
            M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = "tabs_page"
            )

            Column(modifier = Modifier.fillMaxSize()) {
                ExpressiveHeader(
                    title = if (selectionMode) "${selectedTabIds.size} selected" else when (selectedCategory) {
                        TabCategory.INCOGNITO -> "Incognito Tabs"
                        TabCategory.GROUPS -> "Tab Groups"
                        TabCategory.REGULAR -> "Tab Manager"
                    },
                    subtitle = if (selectionMode) "Choose an action for selected tabs" else when (selectedCategory) {
                        TabCategory.INCOGNITO -> if (incognitoTabCount == 1) "1 private tab open" else "$incognitoTabCount private tabs open"
                        TabCategory.GROUPS -> if (groupsCount == 1) "1 active group" else "$groupsCount active groups"
                        TabCategory.REGULAR -> if (activeWorkspaceTabCount == 1) "1 active tab open" else "$activeWorkspaceTabCount active tabs open"
                    },
                    enableLiquidGlass = true,
                    actions = {
                        if (selectionMode) {
                            // Select All / Deselect All toggle (Firefox parity)
                            val allSelected = filteredTabs.isNotEmpty() &&
                                filteredTabs.all { selectedTabIds.contains(it.id) }
                            HeaderActionIcon(
                                icon = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = if (allSelected) "Deselect all" else "Select all",
                                onClick = {
                                    if (allSelected) leaveSelectionMode()
                                    else selectAll(filteredTabs)
                                }
                            )
                            HeaderActionIcon(
                                icon = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_close_selected_tabs),
                                onClick = {
                                    val selected = filteredTabs.filter { selectedTabIds.contains(it.id) }
                                    selected.forEach { requestOptimisticClose(it) }
                                    leaveSelectionMode()
                                }
                            )
                            HeaderActionIcon(
                                icon = Icons.Rounded.Done,
                                contentDescription = stringResource(R.string.ui_finish_selection),
                                onClick = { leaveSelectionMode() }
                            )
                        }

                        HeaderActionIcon(
                            icon = if (displayMode == TabDisplayMode.GRID) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.ui_toggle_layout),
                            onClick = {
                                val nextMode = if (displayMode == TabDisplayMode.GRID) TabDisplayMode.LIST else TabDisplayMode.GRID
                                displayMode = nextMode
                                sp.edit().putString("sp_tab_display_mode", nextMode.name).apply()
                            }
                        )

                        val headerMenuRotation by animateFloatAsState(
                            targetValue = if (isOverflowMenuExpanded) 180f else 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "tab_grid_header_menu_rot"
                        )
                        Box {
                            IconButton(
                                onClick = { isOverflowMenuExpanded = true },
                                modifier = Modifier.graphicsLayer { rotationZ = headerMenuRotation }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.ui_more_options),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            com.petal.browser.ui.containment.PetalPopupMenu(
                                expanded = isOverflowMenuExpanded,
                                onDismissRequest = { isOverflowMenuExpanded = false }
                            ) {
                                PetalExpressiveMenuItem(
                                    text = stringResource(R.string.ui_new_tab),
                                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = accentColor) },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        selectedCategory = TabCategory.REGULAR
                                        launchNewTab(false)
                                    }
                                )
                                PetalExpressiveMenuItem(
                                    text = stringResource(R.string.ui_new_incognito_tab),
                                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = accentColor) },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        withIncognitoAccess {
                                            selectedCategory = TabCategory.INCOGNITO
                                            launchNewTab(true)
                                        }
                                    }
                                )
                                com.petal.browser.ui.containment.PetalMenuDivider()
                                PetalExpressiveMenuItem(
                                    text = stringResource(R.string.ui_tab_manager_settings),
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = accentColor) },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        onOpenSettings()
                                    }
                                )
                                com.petal.browser.ui.containment.PetalMenuDivider()
                                PetalExpressiveMenuItem(
                                    text = stringResource(R.string.ui_close_all_tabs),
                                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    destructive = true,
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        val needConfirm = sp.getBoolean("sp_close_tab_confirm", false) || sp.getBoolean("sp_close_browser_confirm", false)
                                        if (needConfirm) {
                                            showCloseAllConfirmDialog = true
                                        } else {
                                            commitPendingRemovals()
                                            onCloseAllTabs()
                                        }
                                    }
                                )
                            }
                        }
                    }
                )

                Column(modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    .graphicsLayer {
                        translationY = 18.dp.toPx() * newTabExitProgress
                        alpha = 1f - 0.12f * newTabExitProgress
                        scaleX = 1f - 0.015f * newTabExitProgress
                        scaleY = 1f - 0.015f * newTabExitProgress
                    }) {
                    // ── 3-Segment Switcher: Regular | Groups | Incognito ──
                    TabCategorySwitcher(
                        selected = selectedCategory,
                        onSelect = { newCat ->
                            if (newCat != selectedCategory) {
                                val applyCategory = {
                                    com.petal.browser.motion.MotionGate.withMotion("tab_category_switch") {
                                        selectedCategory = newCat
                                    }
                                }
                                if (newCat == TabCategory.INCOGNITO) withIncognitoAccess { applyCategory() } else applyCategory()
                            }
                        }
                    )

                    // ── Zen Browser Workspace Carousel (Regular Tabs) ──
                    if (selectedCategory == TabCategory.REGULAR) {
                        Spacer(Modifier.height(8.dp))
                        PetalWorkspaceCarousel(
                            workspaces = workspaces,
                            activeWorkspaceId = activeWorkspaceId,
                            tabCountsByWorkspace = tabCountsByWorkspace,
                            onSelectWorkspace = { wsId ->
                                PetalWorkspaceManager.setActiveWorkspace(context, wsId)
                            },
                            onEditWorkspace = { ws ->
                                editingWorkspace = ws
                            },
                            onCreateWorkspace = {
                                creatingWorkspace = true
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── Real-time tab & group search ─────────────
                    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
                    val tabSearchShape = RoundedCornerShape(24.dp)
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (selectedCategory == TabCategory.GROUPS) "Search tab groups..." else "Search open tabs...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.ui_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.ui_clear_search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = tabSearchShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = if (isGlass) Color.Transparent else accentColor,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassChrome(shape = tabSearchShape, enabled = isGlass, sheenIntensity = 0.45f), glass = false
                    )

                    // ── Zen Essentials Pinned Tabs Shelf ──
                    if (selectedCategory == TabCategory.REGULAR && currentPinnedTabs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        PetalPinnedTabsShelf(
                            pinnedTabs = currentPinnedTabs,
                            selectedTabId = tabs.find { it.isSelected }?.id,
                            onTabSelect = { tab -> heroSelect(tab) },
                            onTabLongClick = { tab -> glanceTab = tab }
                        )
                    }
                }

                // ── Body: Grid / List / Groups / Recently Closed / Empty states ──
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 4.dp)
                        .graphicsLayer {
                            translationY = 18.dp.toPx() * newTabExitProgress
                            alpha = 1f - 0.12f * newTabExitProgress
                            scaleX = 1f - 0.015f * newTabExitProgress
                            scaleY = 1f - 0.015f * newTabExitProgress
                        }
                        .nestedScroll(vaultNestedScrollConnection)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                ) {
                    // ── Pull-to-reveal Vault indicator ──
                    // Drawn above the tabs (zIndex) and centred in the gap the tabs leave when pulled down,
                    // so it can never be hidden behind a tab card.
                    if (animatedLockPullOffset > 4f) {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val indicatorHeightPx = with(density) { 48.dp.toPx() }
                        val gapPx = animatedLockPullOffset * 0.95f
                        val lockProgress = (animatedLockPullOffset / maxRevealThreshold).coerceIn(0f, 1f)
                        val isThresholdReached = animatedLockPullOffset >= maxRevealThreshold
                        // Fade in only once the gap can hold the pill, so it never overlaps a tab card.
                        val fit = ((gapPx - indicatorHeightPx * 0.6f) / (indicatorHeightPx * 0.6f)).coerceIn(0f, 1f)

                        Surface(
                            shape = ExpressiveButtonShape,
                            color = if (isThresholdReached) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isThresholdReached) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            shadowElevation = (lockProgress * 8f).dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(10f)
                                .graphicsLayer {
                                    translationY = ((gapPx - indicatorHeightPx) / 2f).coerceAtLeast(0f)
                                    alpha = fit
                                    val sc = 0.8f + 0.2f * fit
                                    scaleX = sc
                                    scaleY = sc
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isThresholdReached) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                    contentDescription = stringResource(R.string.ui_closed_tabs_vault_lock),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isThresholdReached) "Release to open Vault" else "Pull to reveal Vault",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // ── Tabs Container: Translated downwards when pulling so component is never covered ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = animatedLockPullOffset * 0.95f
                            }
                    ) {
                        val categoryTransition = updateTransition(targetState = selectedCategory, label = "tab_category_slide")
                        DisposableEffect(categoryTransition.isRunning) {
                            val handle = if (categoryTransition.isRunning) com.petal.browser.motion.MotionGate.startTransition("tab_category_switch") else null
                            onDispose {
                                handle?.release()
                            }
                        }
                        categoryTransition.AnimatedContent(
                            transitionSpec = {
                                if (reduceMotion || isLowEnd) {
                                    fadeIn(animationSpec = PetalSpring.fadeThroughIn()) togetherWith
                                        fadeOut(animationSpec = PetalSpring.fadeThroughOut())
                                } else {
                                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                    (slideInHorizontally(
                                        animationSpec = PetalSpring.spatial(),
                                        initialOffsetX = { fullWidth -> direction * (fullWidth / 3) }
                                    ) + fadeIn(animationSpec = PetalSpring.fadeThroughIn())) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = PetalSpring.spatial(),
                                        targetOffsetX = { fullWidth -> -direction * (fullWidth / 3) }
                                    ) + fadeOut(animationSpec = PetalSpring.fadeThroughOut()))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { currentCategory ->
                            val currentCategoryTabs = when (currentCategory) {
                                TabCategory.REGULAR -> {
                                    regularTabs.filter { tab ->
                                        (tab.workspaceId == activeWorkspaceId) ||
                                        (tab.workspaceId == null && activeWorkspaceId == PetalWorkspaceManager.DEFAULT_WORKSPACE_ID) ||
                                        (tab.isPinned && tab.isPinnedGlobal)
                                    }
                                }
                                TabCategory.INCOGNITO -> tabs.filter { it.isIncognito }
                                TabCategory.GROUPS -> regularTabs
                            }
                            val currentVisibleTabs = currentCategoryTabs.filter { it.id !in pendingRemovalIds }
                            val currentFilteredTabs = currentVisibleTabs
                                .sortedWith(compareByDescending { pinnedTabIds.contains(it.id) || it.isPinned })
                                .filter { tab ->
                                    searchQuery.isBlank() ||
                                        tab.title.contains(searchQuery, ignoreCase = true) ||
                                        tab.url.contains(searchQuery, ignoreCase = true)
                                }

                            val currentDisplayItems = remember(currentFilteredTabs, tabGroups, currentCategory, searchQuery) {
                                if (searchQuery.isNotBlank() || currentCategory != TabCategory.REGULAR || tabGroups.isEmpty()) {
                                    currentFilteredTabs.map { SwitcherGridItem.Tab(it) }
                                } else {
                                    val groupedIds = tabGroups.flatMap { it.tabIds }.toSet()
                                    val result = mutableListOf<SwitcherGridItem>()
                                    for (group in tabGroups) {
                                        val memberTabs = currentFilteredTabs.filter { it.groupId == group.id || it.id in group.tabIds }
                                        if (memberTabs.isNotEmpty()) {
                                            result.add(SwitcherGridItem.FolderHeader(group, memberTabs.size, group.isCollapsed))
                                            if (!group.isCollapsed) {
                                                memberTabs.forEach { result.add(SwitcherGridItem.Tab(it)) }
                                            }
                                        }
                                    }
                                    val standaloneTabs = currentFilteredTabs.filter { it.groupId == null && it.id !in groupedIds }
                                    standaloneTabs.forEach { result.add(SwitcherGridItem.Tab(it)) }
                                    result
                                }
                            }

                            when {


                            // ── Tab Groups Screen Category ──────────────────────────────
                            currentCategory == TabCategory.GROUPS -> {
                            if (tabGroups.isEmpty()) {
                                TabManagerEmptyState(
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    isIncognito = false,
                                    title = stringResource(R.string.ui_no_tab_groups_yet),
                                    subtitle = stringResource(R.string.ui_drag_and_drop_tabs_onto),
                                    onNewTab = {
                                        selectedCategory = TabCategory.REGULAR
                                    }
                                )
                            } else if (filteredGroups.isEmpty()) {
                                TabManagerEmptyState(
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    title = stringResource(R.string.ui_no_matching_tab_groups),
                                    subtitle = stringResource(R.string.ui_try_a_different_search_query),
                                    onNewTab = null
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    flingBehavior = petalFlingBehavior(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredGroups, key = { it.id }) { group ->
                                        val memberTabs = group.tabIds.mapNotNull { tid -> tabs.find { it.id == tid } }
                                        PetalTabGroupCard(
                                            group = group,
                                            tabs = memberTabs,
                                            accentColor = accentColor,
                                            onGroupClick = { inspectingGroup = group },
                                            onDeleteGroup = {
                                                PetalTabGroupManager.deleteGroup(context, group.id)
                                                refreshGroups()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // ── Regular / Incognito Tab Grid & List ─────────────────────
                        currentCategoryTabs.isEmpty() -> TabManagerEmptyState(
                            accentColor = accentColor,
                            textColor = textColor,
                            isIncognito = currentCategory == TabCategory.INCOGNITO,
                            title = if (currentCategory == TabCategory.INCOGNITO)
                                stringResource(R.string.ui_tab_gone_incognito)
                            else
                                stringResource(R.string.ui_tab_find_tabs_here),
                            subtitle = if (currentCategory == TabCategory.INCOGNITO)
                                stringResource(R.string.ui_tab_incognito_desc)
                            else
                                stringResource(R.string.ui_tab_regular_desc),
                            onNewTab = { launchNewTab(currentCategory == TabCategory.INCOGNITO) },
                            onPositioned = { newTabButtonBounds = it }
                        )

                        currentFilteredTabs.isEmpty() -> TabManagerEmptyState(
                            accentColor = accentColor,
                            textColor = textColor,
                            title = stringResource(R.string.ui_no_matching_tabs),
                            subtitle = stringResource(R.string.ui_try_searching_for_a_different),
                            onNewTab = null
                        )

                        displayMode == TabDisplayMode.GRID -> LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            flingBehavior = petalFlingBehavior(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollMotionBlur(gridState)
                        ) {
                            items(
                                items = currentDisplayItems,
                                key = { item ->
                                    when (item) {
                                        is SwitcherGridItem.FolderHeader -> "folder_${item.group.id}"
                                        is SwitcherGridItem.Tab -> "tab_${item.item.id}"
                                    }
                                },
                                span = { item ->
                                    when (item) {
                                        is SwitcherGridItem.FolderHeader -> GridItemSpan(2)
                                        is SwitcherGridItem.Tab -> GridItemSpan(1)
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is SwitcherGridItem.FolderHeader -> {
                                        PetalTabFolderHeader(
                                            group = item.group,
                                            isCollapsed = item.isCollapsed,
                                            tabCount = item.count,
                                            onToggleCollapse = {
                                                PetalTabGroupManager.toggleCollapse(context, item.group.id)
                                                refreshGroups()
                                            }
                                        )
                                    }
                                    is SwitcherGridItem.Tab -> {
                                        val tab = item.item
                                        LaunchedEffect(tab.id) { onTabVisible(tab) }
                                        val isCurrentDragging = (draggingTabId == tab.id)
                                        val isTargetHovered = (hoverTargetTabId == tab.id)

                                        val isClosing = closingTabIds.contains(tab.id)
                                        val cardCloseScale by animateFloatAsState(
                                            targetValue = if (isClosing) 0.65f else 1f,
                                            animationSpec = PetalSpring.exit(),
                                            label = "tab_close_scale"
                                        )
                                        val cardCloseAlpha by animateFloatAsState(
                                            targetValue = if (isClosing) 0f else 1f,
                                            animationSpec = PetalSpring.fadeOut(),
                                            label = "tab_close_alpha"
                                        )

                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { dismissValue ->
                                                if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                                    com.petal.browser.motion.MotionGate.withMotion("tab_swipe_close") {
                                                        requestOptimisticClose(tab)
                                                    }
                                                    true
                                                } else false
                                            }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .animateItem(
                                                    fadeInSpec = PetalSpring.fadeIn(),
                                                    fadeOutSpec = PetalSpring.fadeOut(),
                                                    placementSpec = PetalSpring.listReorder()
                                                )
                                                .graphicsLayer {
                                                    scaleX = cardCloseScale
                                                    scaleY = cardCloseScale
                                                    // Real tile fades in as the hero docks onto it.
                                                    alpha = cardCloseAlpha * (if (hero.visible && hero.tabId == tab.id) 1f - hero.heroAlpha() else 1f)
                                                }
                                                .onGloballyPositioned { coordinates ->
                                                    tabCardBounds[tab.id] = coordinates.boundsInWindow()
                                                }
                                                .pointerInput(tab.id) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            draggingTabId = tab.id
                                                            dragOffset = Offset.Zero
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            draggingTabId = tab.id
                                                            dragOffset += dragAmount
                                                            val myBounds = tabCardBounds[tab.id]
                                                            if (myBounds != null) {
                                                                val currentCenter = myBounds.center + dragOffset
                                                                val hovered = tabCardBounds.entries.find { (id, bounds) ->
                                                                    id != tab.id && bounds.contains(currentCenter)
                                                                }
                                                                hoverTargetTabId = hovered?.key
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val targetId = hoverTargetTabId
                                                            if (targetId != null) {
                                                                val targetTab = tabs.find { it.id == targetId }
                                                                if (targetTab != null) {
                                                                    PetalTabGroupManager.createGroupWithTabs(context, tab, targetTab)
                                                                    refreshGroups()
                                                                }
                                                            }
                                                            draggingTabId = null
                                                            hoverTargetTabId = null
                                                            dragOffset = Offset.Zero
                                                        },
                                                        onDragCancel = {
                                                            draggingTabId = null
                                                            hoverTargetTabId = null
                                                            dragOffset = Offset.Zero
                                                        }
                                                    )
                                                }
                                        ) {
                                            SwipeToDismissBox(
                                                state = dismissState,
                                                enableDismissFromStartToEnd = draggingTabId == null,
                                                enableDismissFromEndToStart = draggingTabId == null,
                                                backgroundContent = {
                                                    SwipeToCloseBackground(
                                                        dismissState = dismissState,
                                                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 24.dp, bottomStart = 10.dp)
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                PetalTabCard(
                                                    tab = tab,
                                                    accentColor = accentColor,
                                                    isDragging = isCurrentDragging,
                                                    dragOffset = if (isCurrentDragging) dragOffset else Offset.Zero,
                                                    isHoveredForMerge = isTargetHovered,
                                                    onTabSelect = {
                                                        if (selectionMode) {
                                                            if (selectedTabIds.contains(tab.id)) selectedTabIds.remove(tab.id) else selectedTabIds.add(tab.id)
                                                            if (selectedTabIds.isEmpty()) selectionMode = false
                                                        } else {
                                                            commitPendingRemovals()
                                                            heroSelect(tab)
                                                        }
                                                    },
                                                    onTabClose = { initiateTabClose(tab) },
                                                    onLongPress = { glanceTab = tab },
                                                    contextMenuExpanded = contextMenuTabId == tab.id,
                                                    onDismissContextMenu = { if (contextMenuTabId == tab.id) contextMenuTabId = null },
                                                    onBookmark = { onBookmarkTab(tab) },
                                                    onShare = { onShareTab(tab) },
                                                    onMute = { muted -> onMuteTab(tab, muted) },
                                                    onCreateGroup = { onCreateGroup(tab); refreshGroups() },
                                                    isPinned = pinnedTabIds.contains(tab.id),
                                                    isSelectionMode = selectionMode,
                                                    isSelectedForSelection = selectedTabIds.contains(tab.id),
                                                    onSelectForSelection = { enterSelection(tab) },
                                                    onTogglePin = { togglePinned(tab) },
                                                    onDuplicateTab = { onDuplicateTab(tab) },
                                                    onCloseOtherTabs = { onCloseOtherTabs(tab) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> LazyColumn(
                            state = listState,
                            flingBehavior = petalFlingBehavior(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollMotionBlur(listState)
                        ) {
                            items(
                                items = currentDisplayItems,
                                key = { item ->
                                    when (item) {
                                        is SwitcherGridItem.FolderHeader -> "folder_${item.group.id}"
                                        is SwitcherGridItem.Tab -> "tab_${item.item.id}"
                                    }
                                }
                            ) { item ->
                                when (item) {
                                    is SwitcherGridItem.FolderHeader -> {
                                        PetalTabFolderHeader(
                                            group = item.group,
                                            isCollapsed = item.isCollapsed,
                                            tabCount = item.count,
                                            onToggleCollapse = {
                                                PetalTabGroupManager.toggleCollapse(context, item.group.id)
                                                refreshGroups()
                                            }
                                        )
                                    }
                                    is SwitcherGridItem.Tab -> {
                                        val tab = item.item
                                        LaunchedEffect(tab.id) { onTabVisible(tab) }
                                        val isCurrentDragging = (draggingTabId == tab.id)
                                        val isTargetHovered = (hoverTargetTabId == tab.id)

                                        val isClosing = closingTabIds.contains(tab.id)
                                        val cardCloseScale by animateFloatAsState(
                                            targetValue = if (isClosing) 0.65f else 1f,
                                            animationSpec = PetalSpring.exit(),
                                            label = "tab_close_scale"
                                        )
                                        val cardCloseAlpha by animateFloatAsState(
                                            targetValue = if (isClosing) 0f else 1f,
                                            animationSpec = PetalSpring.fadeOut(),
                                            label = "tab_close_alpha"
                                        )

                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { dismissValue ->
                                                if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                                    com.petal.browser.motion.MotionGate.withMotion("tab_swipe_close") {
                                                        requestOptimisticClose(tab)
                                                    }
                                                    true
                                                } else false
                                            }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .animateItem(
                                                    fadeInSpec = PetalSpring.fadeIn(),
                                                    fadeOutSpec = PetalSpring.fadeOut(),
                                                    placementSpec = PetalSpring.listReorder()
                                                )
                                                .graphicsLayer {
                                                    scaleX = cardCloseScale
                                                    scaleY = cardCloseScale
                                                    // Real tile fades in as the hero docks onto it.
                                                    alpha = cardCloseAlpha * (if (hero.visible && hero.tabId == tab.id) 1f - hero.heroAlpha() else 1f)
                                                }
                                                .onGloballyPositioned { coordinates ->
                                                    tabCardBounds[tab.id] = coordinates.boundsInWindow()
                                                }
                                                .pointerInput(tab.id) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            draggingTabId = tab.id
                                                            dragOffset = Offset.Zero
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            draggingTabId = tab.id
                                                            dragOffset += dragAmount
                                                            val myBounds = tabCardBounds[tab.id]
                                                            if (myBounds != null) {
                                                                val currentCenter = myBounds.center + dragOffset
                                                                val hovered = tabCardBounds.entries.find { (id, bounds) ->
                                                                    id != tab.id && bounds.contains(currentCenter)
                                                                }
                                                                hoverTargetTabId = hovered?.key
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val targetId = hoverTargetTabId
                                                            if (targetId != null) {
                                                                val targetTab = tabs.find { it.id == targetId }
                                                                if (targetTab != null) {
                                                                    PetalTabGroupManager.createGroupWithTabs(context, tab, targetTab)
                                                                    refreshGroups()
                                                                }
                                                            }
                                                            draggingTabId = null
                                                            hoverTargetTabId = null
                                                            dragOffset = Offset.Zero
                                                        },
                                                        onDragCancel = {
                                                            draggingTabId = null
                                                            hoverTargetTabId = null
                                                            dragOffset = Offset.Zero
                                                        }
                                                    )
                                                }
                                        ) {
                                            SwipeToDismissBox(
                                                state = dismissState,
                                                enableDismissFromStartToEnd = draggingTabId == null,
                                                enableDismissFromEndToStart = draggingTabId == null,
                                                backgroundContent = {
                                                    SwipeToCloseBackground(
                                                        dismissState = dismissState,
                                                        shape = RoundedCornerShape(18.dp)
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                PetalTabListItem(
                                                    tab = tab,
                                                    accentColor = accentColor,
                                                    isDragging = isCurrentDragging,
                                                    dragOffset = if (isCurrentDragging) dragOffset else Offset.Zero,
                                                    isHoveredForMerge = isTargetHovered,
                                                    onTabSelect = {
                                                        if (selectionMode) {
                                                            if (selectedTabIds.contains(tab.id)) selectedTabIds.remove(tab.id) else selectedTabIds.add(tab.id)
                                                            if (selectedTabIds.isEmpty()) selectionMode = false
                                                        } else {
                                                            commitPendingRemovals()
                                                            heroSelect(tab)
                                                        }
                                                    },
                                                    onTabClose = { initiateTabClose(tab) },
                                                    onLongPress = { glanceTab = tab },
                                                    contextMenuExpanded = contextMenuTabId == tab.id,
                                                    onDismissContextMenu = { if (contextMenuTabId == tab.id) contextMenuTabId = null },
                                                    onBookmark = { onBookmarkTab(tab) },
                                                    onShare = { onShareTab(tab) },
                                                    onMute = { muted -> onMuteTab(tab, muted) },
                                                    onCreateGroup = { onCreateGroup(tab); refreshGroups() },
                                                    isPinned = pinnedTabIds.contains(tab.id),
                                                    isSelectionMode = selectionMode,
                                                    isSelectedForSelection = selectedTabIds.contains(tab.id),
                                                    onSelectForSelection = { enterSelection(tab) },
                                                    onTogglePin = { togglePinned(tab) },
                                                    onDuplicateTab = { onDuplicateTab(tab) },
                                                    onCloseOtherTabs = { onCloseOtherTabs(tab) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    if (selectedCategory != TabCategory.GROUPS && categoryTabs.isNotEmpty() && filteredTabs.isNotEmpty()) {
                        if (displayMode == TabDisplayMode.GRID) {
                            PetalExpressiveScrollBar(
                                gridState = gridState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        } else {
                            PetalExpressiveScrollBar(
                                listState = listState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }
            }

            // Multi-Select Floating Action Bar (Firefox Parity)
            AnimatedVisibility(
                visible = selectionMode && selectedTabIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                val selectedTabs = filteredTabs.filter { selectedTabIds.contains(it.id) }
                com.petal.browser.ui.containment.PetalFloatingToolbar(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Move / Add to Group
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                if (tabGroups.isNotEmpty()) {
                                    showAddToGroupDialog = true
                                } else {
                                    // Create a new group with the selected tabs
                                    if (selectedTabs.isNotEmpty()) {
                                        val first = selectedTabs[0]
                                        val rest = selectedTabs.drop(1)
                                        val grp = PetalTabGroupManager.createGroupWithTab(context, first)
                                        rest.forEach { PetalTabGroupManager.addTabToGroup(context, grp.id, it.id) }
                                        refreshGroups()
                                    }
                                    leaveSelectionMode()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.FolderCopy, contentDescription = stringResource(R.string.ui_group), tint = accentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(R.string.ui_group), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Bookmark All
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedTabs.forEach { onBookmarkTab(it) }
                                leaveSelectionMode()
                            }
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = stringResource(R.string.ui_bookmark), tint = accentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(R.string.ui_bookmark), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Share All (URLs joined by newline)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val shareText = selectedTabs.joinToString("\n") { it.url }
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share ${selectedTabs.size} Tabs"))
                                leaveSelectionMode()
                            }
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.ui_share), tint = accentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(R.string.ui_share), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }

                        // Close Selected
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                selectedTabs.forEach { requestOptimisticClose(it) }
                                leaveSelectionMode()
                            }
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(R.string.ui_close), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Floating Material 3 Expressive Undo Banner with slide-to-hide gesture and LIFO undo stack
            val currentUndoEntry = undoStack.lastOrNull()
            ExpressiveTabUndoBanner(
                visible = isUndoBannerVisible && currentUndoEntry != null && !selectionMode,
                closedTab = currentUndoEntry?.tab,
                accentColor = accentColor,
                onUndo = { undoClosedTabs() },
                onDismiss = {
                    isUndoBannerVisible = false
                    undoStack.clear()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )

            // ── Closed Tabs Vault: a real full page hosted in this screen (not a Dialog window) ──
            AnimatedVisibility(
                visible = isVaultVisible,
                enter = fadeIn(androidx.compose.material3.MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(androidx.compose.material3.MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 12 },
                exit = fadeOut(androidx.compose.material3.MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(androidx.compose.material3.MaterialTheme.motionScheme.fastSpatialSpec()) { it / 12 },
                modifier = Modifier.fillMaxSize().zIndex(20f)
            ) {
                PetalRecentlyClosedVault(
                    onDismiss = { isVaultVisible = false },
                    onRestoreTab = { rec ->
                        isVaultVisible = false
                        onRestoreTab?.invoke(
                            PetalTabItem(
                                id = rec.id,
                                title = rec.title,
                                url = rec.url,
                                isIncognito = rec.isIncognito,
                                groupId = rec.groupId,
                                groupTitle = rec.groupTitle,
                                groupColorHex = rec.groupColorHex
                            )
                        )
                    },
                    accentColor = accentColor
                )
            }
        }
    }
    TabHeroOverlay(hero = hero, tileBounds = { id -> tabCardBounds[id] })
    }
    }
    }

    // Modal Sheet / Dialog to inspect and manage tabs inside a Group
    inspectingGroup?.let { group ->
        val groupTabs = group.tabIds.mapNotNull { tid -> tabs.find { it.id == tid } }
        PetalTabGroupInspectionDialog(
            group = group,
            tabs = groupTabs,
            accentColor = accentColor,
            onDismiss = { inspectingGroup = null },
            onTabSelect = { tab ->
                inspectingGroup = null
                onTabSelect(tab)
            },
            onTabClose = { tab ->
                PetalTabGroupManager.removeTabFromGroup(context, group.id, tab.id)
                refreshGroups()
                requestOptimisticClose(tab)
            },
            onUngroupTab = { tab ->
                PetalTabGroupManager.removeTabFromGroup(context, group.id, tab.id)
                refreshGroups()
            },
            onRenameGroup = { newTitle ->
                val updated = group.copy(title = newTitle)
                PetalTabGroupManager.updateGroup(context, updated)
                refreshGroups()
                inspectingGroup = updated
            },
            onColorChange = { updated ->
                PetalTabGroupManager.updateGroup(context, updated)
                refreshGroups()
                inspectingGroup = updated
            }
        )
    }

    // Modal dialog to add selected tabs into an existing or new group (Firefox Parity)
    if (showAddToGroupDialog) {
        val selectedTabs = filteredTabs.filter { selectedTabIds.contains(it.id) }
        var newGroupNameInput by remember { mutableStateOf("") }
        var isCreatingNew by remember { mutableStateOf(false) }

        com.petal.browser.ui.containment.PetalDialog(
            onDismissRequest = { showAddToGroupDialog = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.ui_add_tabs_to_group, selectedTabs.size),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { showAddToGroupDialog = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close))
                    }
                }

                if (!isCreatingNew) {
                    Text(
                        text = stringResource(R.string.ui_select_an_existing_group_or),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        flingBehavior = petalFlingBehavior(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 220.dp)
                    ) {
                        items(tabGroups.distinctBy { it.id }, key = { it.id }) { group ->
                            val gColor = group.parseColor()
                            PetalGroupListRow(
                                position = PetalGroupPosition.SINGLE,
                                onClick = {
                                        selectedTabs.forEach {
                                            PetalTabGroupManager.addTabToGroup(context, group.id, it.id)
                                        }
                                        refreshGroups()
                                        showAddToGroupDialog = false
                                        leaveSelectionMode()
                                    },
                                leading = {
                                    PetalGroupIconBadge(
                                        Icons.Rounded.FolderCopy,
                                        container = gColor.copy(alpha = 0.2f),
                                        tint = gColor,
                                    )
                                },
                                content = {
                                    Text(
                                        text = group.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                trailing = {
                                    Text(
                                        text = stringResource(R.string.ui_tabs_2, group.tabIds.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                            )
                        }
                    }

                    Button(
                        onClick = { isCreatingNew = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_new_group))
                    }
                } else {
                    TextField(
                        value = newGroupNameInput,
                        onValueChange = { newGroupNameInput = it },
                        placeholder = { Text(stringResource(R.string.ui_group_name_optional)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = accentColor,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { isCreatingNew = false },
                            shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.ui_back))
                        }
                        Button(
                            onClick = {
                                if (selectedTabs.isNotEmpty()) {
                                    val first = selectedTabs[0]
                                    val rest = selectedTabs.drop(1)
                                    val title = newGroupNameInput.takeIf { it.isNotBlank() }
                                    val grp = PetalTabGroupManager.createGroupWithTab(context, first, title)
                                    rest.forEach { PetalTabGroupManager.addTabToGroup(context, grp.id, it.id) }
                                    refreshGroups()
                                }
                                showAddToGroupDialog = false
                                leaveSelectionMode()
                            },
                            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.ui_create))
                        }
                    }
                }
            }
        }
    }

    // Modal dialog for Close All Tabs confirmation
    if (showCloseAllConfirmDialog) {
        com.petal.browser.ui.containment.PetalAlertDialog(
            onDismissRequest = { showCloseAllConfirmDialog = false },
            title = stringResource(R.string.ui_close_all_tabs_2),
            message = stringResource(R.string.ui_are_you_sure_you_want, tabs.size),
            icon = Icons.Rounded.LayersClear,
            destructive = true,
            confirmText = stringResource(R.string.ui_close_all_2),
            onConfirm = {
                showCloseAllConfirmDialog = false
                commitPendingRemovals()
                onCloseAllTabs()
            },
        )
    }

    // ── Zen Tab Glance Peek Sheet ──
    glanceTab?.let { tab ->
        PetalTabGlanceSheet(
            tab = tab,
            availableWorkspaces = workspaces,
            availableGroups = tabGroups,
            onDismiss = { glanceTab = null },
            onSelectTab = { selectedTab ->
                heroSelect(selectedTab)
            },
            onSplitTab = { splitTab ->
                heroSelect(splitTab)
            },
            onTogglePin = { pinTab ->
                togglePinned(pinTab)
            },
            onMoveToWorkspace = { moveTab, wsId ->
                moveToWorkspace(moveTab, wsId)
            },
            onMoveToGroup = { moveTab, grpId ->
                moveToGroup(moveTab, grpId)
            },
            onToggleMute = { muteTab ->
                onMuteTab(muteTab, !muteTab.isMuted)
            },
            onDuplicateTab = { dupTab ->
                onDuplicateTab(dupTab)
            },
            onCloseTab = { closeTab ->
                initiateTabClose(closeTab)
            }
        )
    }

    // ── Zen Workspace Creation & Editing Sheet ──
    if (creatingWorkspace || editingWorkspace != null) {
        PetalWorkspaceSheet(
            targetWorkspace = editingWorkspace,
            onDismiss = {
                creatingWorkspace = false
                editingWorkspace = null
            },
            onSave = { name, iconName, colorHex ->
                val ws = editingWorkspace
                if (ws != null) {
                    PetalWorkspaceManager.updateWorkspace(context, ws.id, name, iconName, colorHex)
                } else {
                    val created = PetalWorkspaceManager.createWorkspace(context, name, iconName, colorHex)
                    PetalWorkspaceManager.setActiveWorkspace(context, created.id)
                }
                creatingWorkspace = false
                editingWorkspace = null
            },
            onDelete = { wsToDelete ->
                PetalWorkspaceManager.deleteWorkspace(context, wsToDelete.id)
                creatingWorkspace = false
                editingWorkspace = null
            }
        )
    }

}

/** Top segmented pill switcher: Regular vs Groups vs Incognito, full-width. */
@Composable
private fun TabCategorySwitcher(
    selected: TabCategory,
    onSelect: (TabCategory) -> Unit
) {
    com.petal.browser.ui.components.PetalChoiceButtonGroup(
        options = listOf(
            com.petal.browser.ui.components.PetalButtonGroupItem(TabCategory.REGULAR, "Regular", icon = Icons.Rounded.Public),
            com.petal.browser.ui.components.PetalButtonGroupItem(TabCategory.GROUPS, "Groups", icon = Icons.Rounded.FolderCopy),
            com.petal.browser.ui.components.PetalButtonGroupItem(TabCategory.INCOGNITO, "Incognito", icon = Icons.Rounded.VisibilityOff),
        ),
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Delete-intent background revealed as a grid card or list row is swiped.
 */
@Composable
private fun SwipeToCloseBackground(
    dismissState: SwipeToDismissBoxState,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 24.dp, bottomStart = 10.dp)
) {
    val isActive = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd ||
        dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    val alignment = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (isActive) MaterialTheme.colorScheme.errorContainer else Color.Transparent)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (isActive) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.ui_swipe_to_close_tab),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun TabManagerEmptyState(
    accentColor: Color,
    textColor: Color,
    title: String,
    subtitle: String,
    onNewTab: (() -> Unit)?,
    isIncognito: Boolean = false,
    onPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    var launchPressed by remember { mutableStateOf(false) }
    LaunchedEffect(launchPressed) {
        if (launchPressed) {
            kotlinx.coroutines.delay(120L)
            onNewTab?.invoke()
            launchPressed = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val badgeColor = if (isIncognito) MaterialTheme.colorScheme.tertiary else accentColor
        Surface(
            shape = ExpressiveButtonShape,
            modifier = Modifier.size(112.dp),
            color = badgeColor,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isIncognito) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(R.string.ui_incognito),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                } else if (title.contains("group", ignoreCase = true)) {
                    Icon(
                        imageVector = Icons.Rounded.FolderCopy,
                        contentDescription = stringResource(R.string.ui_tab_groups),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (onNewTab != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { launchPressed = true },
                modifier = Modifier
                    .onGloballyPositioned { onPositioned?.invoke(it.boundsInWindow()) }
                    .graphicsLayer {
                        scaleX = if (launchPressed) 0.94f else 1f
                        scaleY = if (launchPressed) 0.94f else 1f
                    },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isIncognito) "New Incognito Tab" else "New Tab", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PetalTabCard(
    tab: PetalTabItem,
    accentColor: Color,
    isDragging: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    isHoveredForMerge: Boolean = false,
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit,
    onLongPress: () -> Unit = {},
    contextMenuExpanded: Boolean = false,
    onDismissContextMenu: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onShare: () -> Unit = {},
    onMute: (Boolean) -> Unit = {},
    onCreateGroup: () -> Unit = {},
    isPinned: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelectedForSelection: Boolean = false,
    onSelectForSelection: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onDuplicateTab: () -> Unit = {},
    onCloseOtherTabs: () -> Unit = {}
) {
    var cachedPreview by remember(tab.id, tab.thumbnailKey, tab.url, tab.isIncognito, tab.previewBitmap) {
        mutableStateOf(tab.previewBitmap?.takeUnless { it.isRecycled })
    }
    LaunchedEffect(tab.id, tab.thumbnailKey, tab.url, tab.isIncognito) {
        cachedPreview = tab.previewBitmap?.takeUnless { it.isRecycled }
        if (cachedPreview == null) {
            com.petal.browser.unit.TabThumbnailCache.loadFirstAsync(arrayOf(tab.thumbnailKey, tab.id), tab.isIncognito) { bitmap ->
                if (bitmap != null && !bitmap.isRecycled) cachedPreview = bitmap
            }
        }
    }
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface

    var isSelecting by remember { mutableStateOf(false) }
    var isMuted by remember(tab.id) { mutableStateOf(tab.isMuted) }
    val scaleAnim by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            isHoveredForMerge -> 1.05f
            isSelecting -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(
            // Keep tab switching responsive without the large overshoot that made
            // the grid feel jumpy on lower-refresh-rate devices.
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        finishedListener = {
            if (isSelecting) {
                onTabSelect()
            }
        },
        label = "tabZoomScale"
    )

    val groupColor = tab.groupColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }

    val borderStroke = when {
        isHoveredForMerge -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        tab.isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }

    PetalHeroCard(
        shape = PetalContainmentShapes.Hero,
        containerColor = if (tab.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = scaleAnim
                scaleY = scaleAnim
                alpha = if (isDragging) 0.85f else if (isSelecting) 0.95f else 1.0f
                shadowElevation = if (isDragging) 30f else 0f
            }
            .bouncyClickable(onClick = {
                if (!isDragging) {
                    onTabSelect()
                }
            })
            .pointerInput(tab.id) {
                detectTapGestures(
                    onTap = {
                        if (!isDragging) {
                            onTabSelect()
                        }
                    },
                    onLongPress = {
                        if (!isDragging) {
                            onLongPress()
                        }
                    }
                )
            }
            .then(if (borderStroke != null) Modifier.border(borderStroke, PetalContainmentShapes.Hero) else Modifier)
            .entrance()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TabFavicon(tab = tab, accentColor = accentColor, size = 16.dp)
                    Text(
                        text = if (tab.title.isBlank()) "New Tab" else tab.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AnimatedVisibility(visible = isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.ui_pinned),
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    AnimatedVisibility(visible = isSelectionMode && isSelectedForSelection) {
                        Surface(
                            shape = ExpressiveButtonShape,
                            color = accentColor,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.ui_selected), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
                        }
                    }
                    if (!isSelectionMode) {
                        IconButton(
                            onClick = onTabClose,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.ui_close_tab),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Group tag pill indicator if tab is grouped
            if (tab.groupTitle != null && groupColor != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    ExpressiveTabGroupPill(
                        groupName = tab.groupTitle,
                        containerColor = groupColor,
                        contentColor = Color.White
                    )
                }
            }

            // Tab WebView preview thumbnail
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = cachedPreview?.takeUnless { it.isRecycled }, label = "tabPreviewLoad") { preview ->
                    if (preview != null) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.ui_live_preview_of, tab.title),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PetalHomePreviewCard(tab = tab, accentColor = accentColor)
                    }
                }
            }
        }

        com.petal.browser.ui.containment.PetalPopupMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = onDismissContextMenu
        ) {
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_add_tab_to_new_group), leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) }, onClick = { onDismissContextMenu(); onCreateGroup() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_duplicate_tab), leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { onDismissContextMenu(); onDuplicateTab() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_add_to_bookmarks), leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, null) }, onClick = { onDismissContextMenu(); onBookmark() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_share), leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { onDismissContextMenu(); onShare() })
            PetalExpressiveMenuItem(text = if (isPinned) "Unpin tab" else "Pin tab", leadingIcon = { Icon(Icons.Rounded.PushPin, null) }, onClick = { onDismissContextMenu(); onTogglePin() })
            PetalExpressiveMenuItem(
                text = if (isMuted) "Unmute Site" else "Mute Site",
                leadingIcon = { Icon(if (isMuted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, null) },
                onClick = {
                    isMuted = !isMuted
                    onDismissContextMenu()
                    onMute(isMuted)
                }
            )
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_select_tab), leadingIcon = { Icon(Icons.Rounded.CheckBoxOutlineBlank, null) }, onClick = { onDismissContextMenu(); onSelectForSelection() })
            com.petal.browser.ui.containment.PetalMenuDivider()
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_close_other_tabs), leadingIcon = { Icon(Icons.Rounded.CloseFullscreen, null, tint = MaterialTheme.colorScheme.error) }, destructive = true, onClick = { onDismissContextMenu(); onCloseOtherTabs() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_close_tab_2), leadingIcon = { Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error) }, destructive = true, onClick = { onDismissContextMenu(); onTabClose() })
        }
    }
}

@Composable
private fun PetalHomePreviewCard(
    tab: PetalTabItem,
    accentColor: Color,
) {
    // Every home form counts (about:home, petal://home, petal_home.html, bundled assets), not just
    // about:blank, otherwise a home tab was drawn as a "Website" card with a bogus domain.
    val isHome = tab.url == "Petal Home" || com.petal.browser.unit.PetalBrowserUtils.isHomePage(tab.url)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        if (!isHome) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        TabFavicon(tab = tab, accentColor = accentColor, size = 32.dp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (tab.title.isBlank() || tab.title == "about:blank") "Website" else tab.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val domain = try {
                    val uri = android.net.Uri.parse(tab.url)
                    uri.host ?: tab.url
                } catch (_: Throwable) {
                    tab.url
                }
                Text(
                    text = domain,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveButtonShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (tab.isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (tab.isIncognito) "Private Search" else "Search or type URL",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(ExpressiveButtonShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }

                Text(
                    text = if (tab.isIncognito) "Incognito Tab" else "Petal Home",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PetalTabListItem(
    tab: PetalTabItem,
    accentColor: Color,
    isDragging: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    isHoveredForMerge: Boolean = false,
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit,
    onLongPress: () -> Unit = {},
    contextMenuExpanded: Boolean = false,
    onDismissContextMenu: () -> Unit = {},
    onBookmark: () -> Unit = {},
    onShare: () -> Unit = {},
    onMute: (Boolean) -> Unit = {},
    onCreateGroup: () -> Unit = {},
    isPinned: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelectedForSelection: Boolean = false,
    onSelectForSelection: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onDuplicateTab: () -> Unit = {},
    onCloseOtherTabs: () -> Unit = {}
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    var cachedPreview by remember(tab.id, tab.thumbnailKey, tab.url, tab.isIncognito, tab.previewBitmap) {
        mutableStateOf(tab.previewBitmap?.takeUnless { it.isRecycled })
    }
    LaunchedEffect(tab.id, tab.thumbnailKey, tab.url, tab.isIncognito) {
        cachedPreview = tab.previewBitmap?.takeUnless { it.isRecycled }
        if (cachedPreview == null) {
            com.petal.browser.unit.TabThumbnailCache.loadFirstAsync(arrayOf(tab.thumbnailKey, tab.id), tab.isIncognito) { bitmap ->
                if (bitmap != null && !bitmap.isRecycled) cachedPreview = bitmap
            }
        }
    }
    var isMuted by remember(tab.id) { mutableStateOf(tab.isMuted) }

    val groupColor = tab.groupColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }
    PetalGroupListRow(
        position = PetalGroupPosition.SINGLE,
        onClick = onTabSelect,
        onLongClick = onLongPress,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                alpha = if (isDragging) 0.85f else 1.0f
            }
            .entrance(),
        selected = isSelectedForSelection || tab.isSelected || isDragging || isHoveredForMerge,
        leading = {
                    Crossfade(targetState = cachedPreview?.takeUnless { it.isRecycled }, label = "tabListThumbnail") { preview ->
                        if (preview != null) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = stringResource(R.string.ui_thumbnail),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            PetalGroupIconBadge(icon = Icons.Rounded.Public, size = 44.dp)
                        }
                    }
        },
        content = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (tab.title.isBlank() || tab.title.equals("about:blank", true) || tab.title.equals("Petal Start", true)) "Petal Home" else tab.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (tab.groupTitle != null && groupColor != null) {
                            ExpressiveTabGroupPill(groupName = tab.groupTitle, containerColor = groupColor, contentColor = Color.White)
                        }
                    }
                    Text(
                        text = if (tab.url.isBlank() || tab.url.equals("about:blank", true)) "Petal Home" else tab.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AnimatedVisibility(visible = isPinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = stringResource(R.string.ui_pinned), tint = accentColor, modifier = Modifier.size(16.dp))
                }
                AnimatedVisibility(visible = isSelectionMode && isSelectedForSelection) {
                    Surface(shape = ExpressiveButtonShape, color = accentColor, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.ui_selected), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
                    }
                }
                if (!isSelectionMode) {
                    IconButton(
                        onClick = onTabClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_close_tab),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
    )

        com.petal.browser.ui.containment.PetalPopupMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = onDismissContextMenu
        ) {
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_add_tab_to_new_group), leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) }, onClick = { onDismissContextMenu(); onCreateGroup() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_duplicate_tab), leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { onDismissContextMenu(); onDuplicateTab() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_add_to_bookmarks), leadingIcon = { Icon(Icons.Rounded.BookmarkAdd, null) }, onClick = { onDismissContextMenu(); onBookmark() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_share), leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { onDismissContextMenu(); onShare() })
            PetalExpressiveMenuItem(text = if (isPinned) "Unpin tab" else "Pin tab", leadingIcon = { Icon(Icons.Rounded.PushPin, null) }, onClick = { onDismissContextMenu(); onTogglePin() })
            PetalExpressiveMenuItem(
                text = if (isMuted) "Unmute Site" else "Mute Site",
                leadingIcon = { Icon(if (isMuted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, null) },
                onClick = { isMuted = !isMuted; onDismissContextMenu(); onMute(isMuted) }
            )
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_select_tab), leadingIcon = { Icon(Icons.Rounded.CheckBoxOutlineBlank, null) }, onClick = { onDismissContextMenu(); onSelectForSelection() })
            HorizontalDivider()
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_close_other_tabs), leadingIcon = { Icon(Icons.Rounded.CloseFullscreen, null) }, onClick = { onDismissContextMenu(); onCloseOtherTabs() })
            PetalExpressiveMenuItem(text = stringResource(R.string.ui_close_tab_2), leadingIcon = { Icon(Icons.Rounded.Close, null) }, onClick = { onDismissContextMenu(); onTabClose() })
        }
}

/**
 * Tab Group card displayed in the Tab Groups screen category.
 */
@Composable
private fun PetalTabGroupCard(
    group: PetalTabGroup,
    tabs: List<PetalTabItem>,
    accentColor: Color,
    onGroupClick: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    val groupColor = group.parseColor()
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh

    PetalHeroCard(
        shape = PetalContainmentShapes.Hero,
        containerColor = cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .bouncyClickable(onClick = onGroupClick)
            .entrance()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ExpressiveTabGroupPill(
                        groupName = group.title,
                        containerColor = groupColor,
                        contentColor = Color.White
                    )
                }

                Surface(
                    shape = ExpressiveButtonShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onDeleteGroup)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_dissolve_group),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 2x2 Collage preview of tabs inside the group
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                if (tabs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.ui_empty_group), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val previewItems = tabs.take(4)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            previewItems.getOrNull(0)?.let { t ->
                                TabCollageCell(tab = t, accentColor = accentColor, modifier = Modifier.weight(1f))
                            } ?: Spacer(Modifier.weight(1f))

                            previewItems.getOrNull(1)?.let { t ->
                                TabCollageCell(tab = t, accentColor = accentColor, modifier = Modifier.weight(1f))
                            } ?: Spacer(Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            previewItems.getOrNull(2)?.let { t ->
                                TabCollageCell(tab = t, accentColor = accentColor, modifier = Modifier.weight(1f))
                            } ?: Spacer(Modifier.weight(1f))

                            previewItems.getOrNull(3)?.let { t ->
                                TabCollageCell(tab = t, accentColor = accentColor, modifier = Modifier.weight(1f))
                            } ?: Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCollageCell(
    tab: PetalTabItem,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxSize()
    ) {
        if (tab.previewBitmap != null && !tab.previewBitmap.isRecycled) {
            Image(
                bitmap = tab.previewBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                TabFavicon(tab = tab, accentColor = accentColor, size = 16.dp)
            }
        }
    }
}

/**
 * Inspection dialog for managing tabs within a specific group (renaming, selecting, closing, ungrouping).
 */
@Composable
private fun PetalTabGroupInspectionDialog(
    group: PetalTabGroup,
    tabs: List<PetalTabItem>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onTabSelect: (PetalTabItem) -> Unit,
    onTabClose: (PetalTabItem) -> Unit,
    onUngroupTab: (PetalTabItem) -> Unit,
    onRenameGroup: (String) -> Unit,
    onColorChange: (PetalTabGroup) -> Unit
) {
    var isEditingName by remember { mutableStateOf(false) }
    var groupNameInput by remember { mutableStateOf(group.title) }
    val groupColor = group.parseColor()

    com.petal.browser.ui.containment.PetalDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.75f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
                // Header with rename trigger & close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isEditingName) {
                        TextField(
                            value = groupNameInput,
                            onValueChange = { groupNameInput = it },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedIndicatorColor = accentColor,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onRenameGroup(groupNameInput)
                                    isEditingName = false
                                }) {
                                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.ui_save), tint = accentColor)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isEditingName = true }
                        ) {
                            ExpressiveTabGroupPill(
                                groupName = group.title,
                                containerColor = groupColor,
                                contentColor = Color.White
                            )
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.ui_rename), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close_dialog))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Color picker row for tab group (Firefox parity)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabGroupColorPresets.forEach { colorHex ->
                        val parsedColor = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (_: Exception) { accentColor }
                        val isSelectedColor = group.colorHex.equals(colorHex, ignoreCase = true)
                        Surface(
                            shape = ExpressiveButtonShape,
                            color = parsedColor,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    onColorChange(group.copy(colorHex = colorHex))
                                }
                        ) {
                            if (isSelectedColor) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.ui_selected_color),
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // List of tabs in this group
                LazyColumn(
                    flingBehavior = petalFlingBehavior(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tabs.distinctBy { it.id }, key = { it.id }) { tab ->
                        PetalGroupListRow(
                            position = PetalGroupPosition.SINGLE,
                            onClick = { onTabSelect(tab) },
                            leading = { TabFavicon(tab = tab, accentColor = accentColor, size = 32.dp) },
                            content = {
                                        Text(
                                            text = tab.title.ifBlank { "New Tab" },
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = tab.url.ifBlank { "about:blank" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onUngroupTab(tab) }) {
                                        Icon(Icons.Rounded.FolderOff, contentDescription = stringResource(R.string.ui_ungroup_tab), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onTabClose(tab) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_close_tab_2), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                        )
                    }
                }
            }
    }
}

/** Favicon badge shared by the grid card and list row, with a lettered fallback. */
@Composable
private fun TabFavicon(tab: PetalTabItem, accentColor: Color, size: androidx.compose.ui.unit.Dp) {
    if (tab.faviconBitmap != null) {
        Image(
            bitmap = tab.faviconBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(ExpressiveButtonShape)
        )
    } else {
        Surface(
            shape = ExpressiveButtonShape,
            color = accentColor.copy(alpha = 0.2f),
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = tab.title.take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
        }
    }
}

object PetalTabGridBridge {
    @JvmStatic
    fun createTabGridSwitcher(
        activity: ComponentActivity,
        tabs: List<PetalTabItem>,
        onTabSelectListener: (PetalTabItem) -> Unit,
        onTabCloseListener: (PetalTabItem) -> Unit,
        onNewTabListener: (Boolean) -> Unit,
        onCloseAllTabsListener: () -> Unit,
        onOpenSettingsListener: () -> Unit,
        onBookmarkTabListener: (PetalTabItem) -> Unit = {},
        onShareTabListener: (PetalTabItem) -> Unit = {},
        onMuteTabListener: (PetalTabItem, Boolean) -> Unit = { _, _ -> },
        onCreateGroupListener: (PetalTabItem) -> Unit = {},
        onDuplicateTabListener: (PetalTabItem) -> Unit = {},
        onCloseOtherTabsListener: (PetalTabItem) -> Unit = {}
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        com.petal.browser.predictive.PetalContentSnapshot.clear()
                    }
                }
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                val appFont = remember(fontName) {
                    com.petal.browser.ui.theme.AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalTabGridSwitcher(
                        backgroundSnapshot = snapshotBitmap,
                        tabs = tabs,
                        onTabSelect = onTabSelectListener,
                        onTabClose = onTabCloseListener,
                        onNewTab = onNewTabListener,
                        onCloseAllTabs = onCloseAllTabsListener,
                        onOpenSettings = onOpenSettingsListener,
                        onBookmarkTab = onBookmarkTabListener,
                        onShareTab = onShareTabListener,
                        onMuteTab = onMuteTabListener,
                        onCreateGroup = onCreateGroupListener,
                        onDuplicateTab = onDuplicateTabListener,
                        onCloseOtherTabs = onCloseOtherTabsListener
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Floating Tab Undo Banner modeled after Chromium.
 * - Displays the most recently closed tab title with clean, compact typography.
 * - Text-only "Undo" button (no icons).
 * - Smooth vertical sliding crossfade between consecutive stacked items.
 * - Liquid Glass container with swipe-to-dismiss gesture physics.
 */
@Composable
fun ExpressiveTabUndoBanner(
    visible: Boolean,
    closedTab: PetalTabItem?,
    accentColor: Color,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "undoBannerOffsetX"
    )

    AnimatedVisibility(
        visible = visible && closedTab != null,
        enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn() + scaleIn(initialScale = 0.92f),
        exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = modifier
    ) {
        val tabTitle = closedTab?.title.orEmpty().ifBlank { "Tab" }
        com.petal.browser.ui.containment.PetalFloatingToolbar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffsetX) > 100.dp.toPx()) {
                                onDismiss()
                            }
                            dragOffsetX = 0f
                        },
                        onDragCancel = {
                            dragOffsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetX += dragAmount
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = closedTab?.id.orEmpty(),
                    transitionSpec = {
                        (slideInVertically { height -> -height } + fadeIn())
                            .togetherWith(slideOutVertically { height -> height } + fadeOut())
                    },
                    label = "tabUndoTextAnimation"
                ) { _ ->
                    Text(
                        text = "Closed $tabTitle",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            TextButton(
                onClick = onUndo,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = accentColor
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_undo),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
