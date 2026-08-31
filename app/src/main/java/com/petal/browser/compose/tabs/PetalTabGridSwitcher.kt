package com.petal.browser.compose.tabs

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.petal.browser.ui.components.PetalThemedSnackbarHost
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.launch

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
    val title: String,
    val url: String,
    val faviconBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val isIncognito: Boolean = false,
    val isSelected: Boolean = false,
    val groupId: String? = null,
    val groupTitle: String? = null,
    val groupColorHex: String? = null
)

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
    val initialCategory = remember {
        if (tabs.any { it.isSelected && it.isIncognito }) TabCategory.INCOGNITO else TabCategory.REGULAR
    }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    // Sync tab group definitions
    var tabGroups by remember { mutableStateOf(PetalTabGroupManager.getAllGroups(context)) }
    fun refreshGroups() {
        tabGroups = PetalTabGroupManager.getAllGroups(context)
    }

    LaunchedEffect(tabs) {
        val openTabIds = tabs.map { it.id }.toSet()
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

    val effectiveOnBack: () -> Unit = remember(onBack, context) {
        onBack ?: {
            (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
            Unit
        }
    }

    val pendingRemovalIds = remember { mutableStateListOf<String>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun requestOptimisticClose(tab: PetalTabItem) {
        if (tab.id in pendingRemovalIds) return
        pendingRemovalIds.add(tab.id)
        onTabClose(tab)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = "Closed ${tab.title.ifBlank { "Tab" }}",
                duration = SnackbarDuration.Short
            )
        }
    }

    fun commitPendingRemovals() {
        if (pendingRemovalIds.isEmpty()) return
        val idsToCommit = pendingRemovalIds.toList()
        pendingRemovalIds.clear()
        idsToCommit.forEach { id ->
            tabs.find { it.id == id }?.let { onTabClose(it) }
        }
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    // Tab counts
    val regularTabCount = tabs.count { !it.isIncognito }
    val incognitoTabCount = tabs.count { it.isIncognito }
    val groupsCount = tabGroups.size

    // Category tabs & filtering
    val categoryTabs = when (selectedCategory) {
        TabCategory.REGULAR -> tabs.filter { !it.isIncognito }
        TabCategory.INCOGNITO -> tabs.filter { it.isIncognito }
        TabCategory.GROUPS -> tabs.filter { !it.isIncognito }
    }
    val visibleTabs = categoryTabs.filter { it.id !in pendingRemovalIds }
    val filteredTabs = visibleTabs.filter { tab ->
        searchQuery.isBlank() ||
            tab.title.contains(searchQuery, ignoreCase = true) ||
            tab.url.contains(searchQuery, ignoreCase = true) ||
            tab.groupTitle?.contains(searchQuery, ignoreCase = true) == true
    }

    val filteredGroups = tabGroups.filter { group ->
        if (searchQuery.isBlank()) true
        else {
            group.title.contains(searchQuery, ignoreCase = true) ||
                group.tabIds.any { tid ->
                    val t = tabs.find { it.id == tid }
                    t != null && (t.title.contains(searchQuery, ignoreCase = true) || t.url.contains(searchQuery, ignoreCase = true))
                }
        }
    }

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

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = effectiveOnBack,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = "tabs_page"
            )

            Column(modifier = Modifier.fillMaxSize()) {
                ExpressiveHeader(
                    title = when (selectedCategory) {
                        TabCategory.INCOGNITO -> "Incognito Tabs"
                        TabCategory.GROUPS -> "Tab Groups"
                        TabCategory.REGULAR -> "Tab Manager"
                    },
                    subtitle = when (selectedCategory) {
                        TabCategory.INCOGNITO -> "$incognitoTabCount private tabs open"
                        TabCategory.GROUPS -> if (groupsCount == 1) "1 active group" else "$groupsCount active groups"
                        TabCategory.REGULAR -> "$regularTabCount active tabs open"
                    },
                    enableLiquidGlass = true,
                    actions = {
                        HeaderActionIcon(
                            icon = Icons.Rounded.Add,
                            contentDescription = "New Tab",
                            onClick = {
                                commitPendingRemovals()
                                onNewTab(selectedCategory == TabCategory.INCOGNITO)
                            }
                        )

                        HeaderActionIcon(
                            icon = if (displayMode == TabDisplayMode.GRID) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = "Toggle layout",
                            onClick = {
                                val nextMode = if (displayMode == TabDisplayMode.GRID) TabDisplayMode.LIST else TabDisplayMode.GRID
                                displayMode = nextMode
                                sp.edit().putString("sp_tab_display_mode", nextMode.name).apply()
                            }
                        )

                        Box {
                            HeaderActionIcon(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                onClick = { isOverflowMenuExpanded = true }
                            )

                            DropdownMenu(
                                expanded = isOverflowMenuExpanded,
                                onDismissRequest = { isOverflowMenuExpanded = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Tab") },
                                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = accentColor) },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        selectedCategory = TabCategory.REGULAR
                                        commitPendingRemovals()
                                        onNewTab(false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("New Incognito Tab") },
                                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = accentColor) },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        selectedCategory = TabCategory.INCOGNITO
                                        commitPendingRemovals()
                                        onNewTab(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Group by Domain") },
                                    leadingIcon = { Icon(Icons.Rounded.Workspaces, contentDescription = null, tint = accentColor) },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        val count = PetalTabGroupManager.autoGroupByDomain(context, tabs)
                                        refreshGroups()
                                        selectedCategory = TabCategory.GROUPS
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = if (count > 0) "Organized tabs into $count domain groups" else "No matching domain pairs found to group",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Close All Tabs") },
                                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        commitPendingRemovals()
                                        onCloseAllTabs()
                                    }
                                )
                            }
                        }
                    }
                )

                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                    // ── 3-Segment Switcher: Regular | Groups | Incognito ─────────────
                    TabCategorySwitcher(
                        selected = selectedCategory,
                        accentColor = accentColor,
                        onSelect = { selectedCategory = it }
                    )

                    Spacer(Modifier.height(10.dp))

                    // ── Real-time tab & group search ────────────────────────────
                    OutlinedTextField(
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
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Body: Grid / List / Groups / Empty states ────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 4.dp)
                ) {
                    when {
                        // ── Tab Groups Screen Category ──────────────────────────────
                        selectedCategory == TabCategory.GROUPS -> {
                            if (tabGroups.isEmpty()) {
                                TabManagerEmptyState(
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    isIncognito = false,
                                    title = "No tab groups yet",
                                    subtitle = "Drag and drop tabs onto each other in the Tab Manager to create a group",
                                    onNewTab = {
                                        selectedCategory = TabCategory.REGULAR
                                    }
                                )
                            } else if (filteredGroups.isEmpty()) {
                                TabManagerEmptyState(
                                    accentColor = accentColor,
                                    textColor = textColor,
                                    title = "No matching tab groups",
                                    subtitle = "Try a different search query",
                                    onNewTab = null
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
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
                        categoryTabs.isEmpty() -> TabManagerEmptyState(
                            accentColor = accentColor,
                            textColor = textColor,
                            isIncognito = selectedCategory == TabCategory.INCOGNITO,
                            title = if (selectedCategory == TabCategory.INCOGNITO)
                                "You've gone Incognito"
                            else
                                "You'll find your tabs here",
                            subtitle = if (selectedCategory == TabCategory.INCOGNITO)
                                "Pages you view in incognito tabs won't be saved in your browser history."
                            else
                                "Open tabs to visit different pages at the same time",
                            onNewTab = { onNewTab(selectedCategory == TabCategory.INCOGNITO) }
                        )

                        filteredTabs.isEmpty() -> TabManagerEmptyState(
                            accentColor = accentColor,
                            textColor = textColor,
                            title = "No matching tabs",
                            subtitle = "Try searching for a different title or web address",
                            onNewTab = null
                        )

                        displayMode == TabDisplayMode.GRID -> LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredTabs, key = { it.id }) { tab ->
                                LaunchedEffect(tab.id) { onTabVisible(tab) }
                                val isCurrentDragging = (draggingTabId == tab.id)
                                val isTargetHovered = (hoverTargetTabId == tab.id)

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                            requestOptimisticClose(tab)
                                            true
                                        } else false
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .animateItem()
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
                                                commitPendingRemovals()
                                                onTabSelect(tab)
                                            },
                                            onTabClose = { requestOptimisticClose(tab) }
                                        )
                                    }
                                }
                            }
                        }

                        else -> LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredTabs, key = { it.id }) { tab ->
                                LaunchedEffect(tab.id) { onTabVisible(tab) }
                                val isCurrentDragging = (draggingTabId == tab.id)
                                val isTargetHovered = (hoverTargetTabId == tab.id)

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                            requestOptimisticClose(tab)
                                            true
                                        } else false
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .animateItem()
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
                                                commitPendingRemovals()
                                                onTabSelect(tab)
                                            },
                                            onTabClose = { requestOptimisticClose(tab) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Material 3 Undo snackbar, anchored to the bottom of the screen.
            PetalThemedSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                actionColor = accentColor
            )
        }
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
            }
        )
    }
}

/** Top segmented pill switcher: Regular vs Groups vs Incognito, full-width. */
@Composable
private fun TabCategorySwitcher(
    selected: TabCategory,
    accentColor: Color,
    onSelect: (TabCategory) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabCategoryPill(
                label = "Regular",
                icon = Icons.Rounded.Public,
                selected = selected == TabCategory.REGULAR,
                accentColor = accentColor,
                onClick = { onSelect(TabCategory.REGULAR) },
                modifier = Modifier.weight(1f)
            )
            TabCategoryPill(
                label = "Groups",
                icon = Icons.Rounded.FolderCopy,
                selected = selected == TabCategory.GROUPS,
                accentColor = accentColor,
                onClick = { onSelect(TabCategory.GROUPS) },
                modifier = Modifier.weight(1f)
            )
            TabCategoryPill(
                label = "Incognito",
                icon = Icons.Rounded.VisibilityOff,
                selected = selected == TabCategory.INCOGNITO,
                accentColor = accentColor,
                onClick = { onSelect(TabCategory.INCOGNITO) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabCategoryPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
                contentDescription = "Swipe to close tab",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun RegularTabEmptyIllustration(
    containerColor: Color,
    phoneColor: Color
) {
    Box(
        modifier = Modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back phone / tab outline (offset to bottom-right)
        Canvas(
            modifier = Modifier
                .size(width = 46.dp, height = 66.dp)
                .offset(x = 8.dp, y = 5.dp)
        ) {
            val cornerRadius = CornerRadius(12.dp.toPx())
            val strokeWidth = 3.dp.toPx()
            drawRoundRect(
                color = phoneColor.copy(alpha = 0.55f),
                size = size,
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth)
            )
        }

        // Front phone / tab (main device card)
        Box(
            modifier = Modifier
                .offset(x = (-5).dp, y = (-3).dp)
                .size(width = 44.dp, height = 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .border(3.2.dp, phoneColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.TopCenter
        ) {
            // Speaker / Camera notch at top
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(width = 12.dp, height = 2.5.dp)
                    .clip(CircleShape)
                    .background(phoneColor)
            )
            // Screen inner viewport preview
            Box(
                modifier = Modifier
                    .padding(top = 13.dp, start = 5.dp, end = 5.dp, bottom = 6.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(phoneColor.copy(alpha = 0.2f))
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
    isIncognito: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val badgeColor = if (isIncognito) MaterialTheme.colorScheme.tertiary else accentColor
        Surface(
            shape = PetalMaterialShapes.Bun.toShape(),
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
                        contentDescription = "Incognito",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                } else if (title.contains("group", ignoreCase = true)) {
                    Icon(
                        imageVector = Icons.Rounded.FolderCopy,
                        contentDescription = "Tab Groups",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    RegularTabEmptyIllustration(
                        containerColor = badgeColor,
                        phoneColor = MaterialTheme.colorScheme.onPrimary
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
                onClick = onNewTab,
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
    onTabClose: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface

    var isSelecting by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            isHoveredForMerge -> 1.05f
            isSelecting -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
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
        isHoveredForMerge -> BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary)
        isDragging -> BorderStroke(2.5.dp, accentColor)
        tab.isSelected -> BorderStroke(2.5.dp, groupColor ?: accentColor)
        groupColor != null -> BorderStroke(2.dp, groupColor.copy(alpha = 0.8f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    val cardShape = RoundedCornerShape(18.dp)

    Surface(
        shape = cardShape,
        color = cardBg,
        border = borderStroke,
        tonalElevation = if (tab.isSelected || isDragging || isHoveredForMerge) 6.dp else 1.dp,
        shadowElevation = if (isDragging) 12.dp else if (tab.isSelected || isHoveredForMerge) 6.dp else 1.dp,
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
                    isSelecting = true
                }
            })
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

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onTabClose)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close tab",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
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
                if (tab.previewBitmap != null && !tab.previewBitmap.isRecycled) {
                    Image(
                        bitmap = tab.previewBitmap.asImageBitmap(),
                        contentDescription = "Live preview of ${tab.title}",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PetalHomePreviewCard(tab = tab, accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun PetalHomePreviewCard(
    tab: PetalTabItem,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
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
                    .clip(CircleShape)
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
                            .clip(CircleShape)
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

@Composable
private fun PetalTabListItem(
    tab: PetalTabItem,
    accentColor: Color,
    isDragging: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    isHoveredForMerge: Boolean = false,
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface

    val groupColor = tab.groupColorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
    }

    val borderStroke = when {
        isHoveredForMerge -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.tertiary)
        isDragging -> BorderStroke(2.dp, accentColor)
        tab.isSelected -> BorderStroke(2.dp, groupColor ?: accentColor)
        groupColor != null -> BorderStroke(1.5.dp, groupColor.copy(alpha = 0.8f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                alpha = if (isDragging) 0.85f else 1.0f
            }
            .bouncyClickable(onClick = onTabSelect)
            .entrance()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(54.dp, 48.dp)
                ) {
                    if (tab.previewBitmap != null && !tab.previewBitmap.isRecycled) {
                        Image(
                            bitmap = tab.previewBitmap.asImageBitmap(),
                            contentDescription = "Thumbnail",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            TabFavicon(tab = tab, accentColor = accentColor, size = 22.dp)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (tab.title.isBlank() || tab.title.equals("about:blank", ignoreCase = true) || tab.title.equals("Petal Start", ignoreCase = true)) "Petal Home" else tab.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (tab.groupTitle != null && groupColor != null) {
                            ExpressiveTabGroupPill(
                                groupName = tab.groupTitle,
                                containerColor = groupColor,
                                contentColor = Color.White
                            )
                        }
                    }
                    Text(
                        text = if (tab.url.isBlank() || tab.url.equals("about:blank", ignoreCase = true)) "Petal Home" else tab.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onTabClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.5.dp, groupColor.copy(alpha = 0.6f)),
        tonalElevation = 2.dp,
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
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onDeleteGroup)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dissolve group",
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
                        Text("Empty Group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onRenameGroup: (String) -> Unit
) {
    var isEditingName by remember { mutableStateOf(false) }
    var groupNameInput by remember { mutableStateOf(group.title) }
    val groupColor = group.parseColor()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with rename trigger & close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isEditingName) {
                        OutlinedTextField(
                            value = groupNameInput,
                            onValueChange = { groupNameInput = it },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    onRenameGroup(groupNameInput)
                                    isEditingName = false
                                }) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Save", tint = accentColor)
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
                            Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close dialog")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // List of tabs in this group
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTabSelect(tab) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    TabFavicon(tab = tab, accentColor = accentColor, size = 20.dp)
                                    Column(modifier = Modifier.weight(1f)) {
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
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onUngroupTab(tab) }) {
                                        Icon(Icons.Rounded.FolderOff, contentDescription = "Ungroup tab", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onTabClose(tab) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Close tab", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
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
                .clip(CircleShape)
        )
    } else {
        Surface(
            shape = CircleShape,
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
        onOpenSettingsListener: () -> Unit
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
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
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
                        onOpenSettings = onOpenSettingsListener
                    )
                }
            }
        }
    }
}
