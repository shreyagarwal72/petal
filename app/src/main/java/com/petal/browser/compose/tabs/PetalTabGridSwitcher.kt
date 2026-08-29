package com.petal.browser.compose.tabs

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.AnimatedCounterBadge
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalThemedSnackbarHost
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.PetalExpressiveTheme
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
 * Which set of tabs the top segmented pill switcher currently shows. Regular and Incognito
 * tabs are always kept in fully separate views - switching segments changes which subset of
 * [PetalTabItem.isIncognito] feeds the grid/list/empty-state below, it never merges them.
 */
enum class TabCategory {
    REGULAR,
    INCOGNITO
}

/**
 * A single tab as rendered by [PetalTabGridSwitcher]. [previewBitmap] is expected to be a
 * *live* capture of the tab's current WebView frame - callers should source it via
 * `NinjaWebView.capturePreviewBitmapAsync(...)`, which uses `PixelCopy` on API 31+ for a
 * GPU-accurate snapshot (falling back to a software draw on older devices). Passing a stale
 * or null bitmap simply falls back to a favicon/placeholder card, so this composable never
 * needs to know anything about WebView or PixelCopy itself.
 */
data class PetalTabItem(
    val id: String,
    val title: String,
    val url: String,
    val faviconBitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val isIncognito: Boolean = false,
    val isSelected: Boolean = false
)

/**
 * Full-screen, non-swipeable, Chrome-style tab manager.
 *
 * - Top bar: rounded `+` new-tab button, a dynamic animated tab-count badge, a grid/list
 *   layout toggle, and a 3-dot overflow menu, sitting above a real-time search field that
 *   filters the open tabs as the user types.
 * - Body: a 2-column grid of rounded cards showing each tab's live preview thumbnail,
 *   favicon, and title, with an accent border/elevation on the active tab. Cards are closed
 *   only via their explicit close (X) affordance - there is no swipe-to-dismiss gesture
 *   anywhere in this screen.
 * - Closing a tab is optimistic: the card disappears from the grid immediately, while the
 *   actual close is only committed to [onTabClose] after a floating Material 3 snackbar
 *   ("Closed <title>") times out without the user tapping "Undo". Tapping Undo simply
 *   un-hides the card - the underlying tab (and its WebView/session) was never touched.
 * - Empty state: once [tabs] is empty, the grid is replaced with a centered dual-device
 *   badge illustration, "You'll find your tabs here" / "Open tabs to visit different pages
 *   at the same time" copy, and a [BackHandler] that swallows system-back so the user can't
 *   navigate back into a browser viewport with no tab to show - the only way out is the `+`
 *   button, which creates a fresh tab.
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
    var searchQuery by remember { mutableStateOf("") }
    var displayMode by remember { mutableStateOf(TabDisplayMode.GRID) }
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    val initialCategory = remember {
        if (tabs.any { it.isSelected && it.isIncognito }) TabCategory.INCOGNITO else TabCategory.REGULAR
    }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val effectiveOnBack: () -> Unit = remember(onBack, context) {
        onBack ?: {
            (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
            Unit
        }
    }

    // Optimistic-close bookkeeping: ids in here are hidden from the grid immediately, but
    // `tabs` (the source of truth from the caller) hasn't been touched yet. The id is only
    // removed from `tabs` for real - via onTabClose - once the undo snackbar times out.
    val pendingRemovalIds = remember { mutableStateListOf<String>() }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun requestOptimisticClose(tab: PetalTabItem) {
        if (tab.id in pendingRemovalIds) return
        pendingRemovalIds.add(tab.id)
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Closed ${tab.title.ifBlank { "Tab" }}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Undo: the tab was never actually closed, just visually hidden - restore it.
                pendingRemovalIds.remove(tab.id)
            } else if (tab.id in pendingRemovalIds) {
                // Snackbar timed out without Undo: commit the close for real. The
                // membership check guards against double-closing a tab that
                // commitPendingRemovals() already force-closed while this snackbar was
                // still showing (e.g. the user tapped + before the timeout).
                onTabClose(tab)
                pendingRemovalIds.remove(tab.id)
            }
        }
    }

    // Any tab still sitting in its Undo window gets closed for real, right now, instead of
    // waiting out the snackbar timeout - so tapping + always starts from a clean, fully
    // committed tab list rather than one that still has "pending" closes hanging around.
    fun commitPendingRemovals() {
        if (pendingRemovalIds.isEmpty()) return
        val idsToCommit = pendingRemovalIds.toList()
        pendingRemovalIds.clear()
        idsToCommit.forEach { id ->
            tabs.find { it.id == id }?.let { onTabClose(it) }
        }
        // Hide the now-stale Undo snackbar rather than let it linger for a tab that's
        // already permanently gone.
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    // `tabs` is the caller's live source of truth (e.g. a SnapshotStateList mirroring
    // BrowserContainer). We derive visible/filtered lists directly from it on every
    // recomposition so closing/opening tabs elsewhere is reflected immediately.
    // Regular and Incognito tabs are strictly partitioned by the segmented switcher above -
    // the grid/list/empty-state below only ever sees the selected category's tabs.
    val categoryTabs = tabs.filter { it.isIncognito == (selectedCategory == TabCategory.INCOGNITO) }
    val visibleTabs = categoryTabs.filter { it.id !in pendingRemovalIds }
    val filteredTabs = visibleTabs.filter { tab ->
        searchQuery.isBlank() ||
            tab.title.contains(searchQuery, ignoreCase = true) ||
            tab.url.contains(searchQuery, ignoreCase = true)
    }
    val regularTabCount = tabs.count { !it.isIncognito }
    val incognitoTabCount = tabs.count { it.isIncognito }

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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = "tabs_page"
            )

            Column(modifier = Modifier.fillMaxSize()) {
                ExpressiveHeader(
                    title = if (selectedCategory == TabCategory.INCOGNITO) "Incognito Tabs" else "Tab Manager",
                    subtitle = if (selectedCategory == TabCategory.INCOGNITO) "$incognitoTabCount private tabs open" else "$regularTabCount active tabs open",
                    onBack = effectiveOnBack,
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
                                displayMode = if (displayMode == TabDisplayMode.GRID) TabDisplayMode.LIST else TabDisplayMode.GRID
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
                    // ── Regular / Incognito segmented pill switcher ─────────────
                    TabCategorySwitcher(
                        selected = selectedCategory,
                        regularCount = regularTabCount,
                        incognitoCount = incognitoTabCount,
                        accentColor = accentColor,
                        onSelect = { selectedCategory = it }
                    )

                    Spacer(Modifier.height(10.dp))

                    // ── Real-time tab search ────────────────────────────────────
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search open tabs...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

                // ── Body: 2-column grid / list / empty states ───────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 4.dp)
                ) {
                    when {
                        categoryTabs.isEmpty() -> TabManagerEmptyState(
                            accentColor = accentColor,
                            textColor = textColor,
                            isIncognito = selectedCategory == TabCategory.INCOGNITO,
                            title = if (selectedCategory == TabCategory.INCOGNITO)
                                "You've gone Incognito"
                            else
                                "You'll find your tabs here",
                            subtitle = if (selectedCategory == TabCategory.INCOGNITO)
                                "Pages you view here won't be saved to your history, cache, or search suggestions"
                            else
                                "Open tabs to visit different pages at the same time",
                            onNewTab = {
                                commitPendingRemovals()
                                onNewTab(selectedCategory == TabCategory.INCOGNITO)
                            }
                        )

                        filteredTabs.isEmpty() -> TabManagerEmptyState(
                            accentColor = accentColor,
                            textColor = textColor,
                            title = "No matching tabs",
                            subtitle = "Try a different search",
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
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = tab.id !in pendingRemovalIds,
                                    exit = fadeOut() + scaleOut(targetScale = 0.85f),
                                    modifier = Modifier.animateItem()
                                ) {
                                    // Horizontal swipe-to-close, in either direction, alongside
                                    // the card's own explicit close button - both routes land on
                                    // the same optimistic-close + Undo-snackbar flow.
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { dismissValue ->
                                            if (dismissValue != SwipeToDismissBoxValue.Settled) {
                                                requestOptimisticClose(tab)
                                                true
                                            } else false
                                        }
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = true,
                                        enableDismissFromEndToStart = true,
                                        backgroundContent = { SwipeToCloseBackground(dismissState) }
                                    ) {
                                        PetalTabCard(
                                            tab = tab,
                                            accentColor = accentColor,
                                            onTabSelect = {
                                                // Selecting an existing tab is a decisive action - it
                                                // shouldn't leave a still-pending "Undo" close hanging
                                                // around. Commit any pending removals right now so a
                                                // just-closed tab is actually gone instead of only
                                                // disappearing once its snackbar happens to time out.
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
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = tab.id !in pendingRemovalIds,
                                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                                    modifier = Modifier.animateItem()
                                ) {
                                    PetalTabListItem(
                                        tab = tab,
                                        accentColor = accentColor,
                                        onTabSelect = {
                                            // Same reasoning as the grid card above: commit any
                                            // pending "Undo" closes before switching tabs.
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
}

/** Top segmented pill switcher: Regular [N] vs Incognito [N] (mask icon), full-width. */
@Composable
private fun TabCategorySwitcher(
    selected: TabCategory,
    regularCount: Int,
    incognitoCount: Int,
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
                count = regularCount,
                icon = Icons.Rounded.Public,
                selected = selected == TabCategory.REGULAR,
                accentColor = accentColor,
                onClick = { onSelect(TabCategory.REGULAR) },
                modifier = Modifier.weight(1f)
            )
            TabCategoryPill(
                label = "Incognito",
                count = incognitoCount,
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
    count: Int,
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
                text = "$label [$count]",
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
 * Delete-intent background revealed as a grid card is swiped. Only tints/shows the close
 * icon once the swipe has actually crossed into a settle-triggering state - a partial,
 * released swipe shows nothing so it doesn't look committed when it isn't.
 */
@Composable
private fun SwipeToCloseBackground(dismissState: SwipeToDismissBoxState) {
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
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 10.dp, bottomEnd = 24.dp, bottomStart = 10.dp))
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
private fun TabManagerEmptyState(
    accentColor: Color,
    textColor: Color,
    title: String,
    subtitle: String,
    onNewTab: (() -> Unit)?,
    isIncognito: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        com.petal.browser.ui.components.EmptyStateBlob(
            icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                if (isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.TabUnselected
            ),
            title = title,
            description = subtitle,
            fraction = 0.6f
        )
        if (onNewTab != null) {
            Spacer(Modifier.height(16.dp))
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
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface

    var isSelecting by remember { mutableStateOf(false) }
    val scaleAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelecting) 1.05f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
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

    // Active-tab accent outline highlight matching Chrome tab switcher specification
    val borderStroke = if (tab.isSelected) {
        BorderStroke(2.5.dp, accentColor)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    val cardShape = RoundedCornerShape(18.dp)

    Surface(
        shape = cardShape,
        color = cardBg,
        border = borderStroke,
        tonalElevation = if (tab.isSelected) 4.dp else 1.dp,
        shadowElevation = if (tab.isSelected) 6.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f) // Vertical phone screen aspect ratio for tab preview cards
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
                alpha = if (isSelecting) 0.95f else 1.0f
            }
            .bouncyClickable(onClick = {
                isSelecting = true
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
    onTabSelect: () -> Unit,
    onTabClose: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface

    val borderStroke = if (tab.isSelected) {
        BorderStroke(2.dp, accentColor)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .bouncyClickable(onClick = onTabSelect)
            .entrance()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabFavicon(tab = tab, accentColor = accentColor, size = 24.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tab.title.isBlank() || tab.title.equals("about:blank", ignoreCase = true) || tab.title.equals("Petal Start", ignoreCase = true)) "Petal Home" else tab.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
