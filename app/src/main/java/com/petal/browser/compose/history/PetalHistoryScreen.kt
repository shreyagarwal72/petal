/*
 * PetalHistoryScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome Android-inspired Material 3 Expressive History Page for Petal Browser.
 * Features live search/filter, Firefox-style date groups, individual entry deletion,
 * clear browsing data action, and 60fps smooth animations.
 */

package com.petal.browser.compose.history
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.components.PetalExpressiveScrollBar
import com.petal.browser.ui.components.extractFastScrollGlyph
import com.petal.browser.ui.theme.layout.petalFlingBehavior

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.FilterChip
import com.petal.browser.ui.kit.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.launch
import coil.compose.SubcomposeAsyncImage
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.components.PetalSwipeActionRow
import com.petal.browser.ui.components.PetalRowSwipeAction
import com.petal.browser.ui.components.PetalGroupRole
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.PetalExpressiveAlertDialog
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.PetalPageBackground
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.PetalSheetHeader
import com.petal.browser.ui.containment.PetalSheetPill
import com.petal.browser.ui.containment.PetalSheetActionRow
import com.petal.browser.ui.containment.PetalSheetTone
import com.petal.browser.ui.containment.petalSheetSurface
import androidx.compose.ui.graphics.Color
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

fun interface HistoryUrlHandler {
    fun open(url: String)
}

fun interface HistoryActionHandler {
    fun action()
}

// ─────────────────────────────────────────────────────────────────────────
// Firefox-style date group labels
// ─────────────────────────────────────────────────────────────────────────

private enum class HistoryDateGroup(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    OLDER("Older")
}

/** Day boundaries computed once per list build (not once per record). */
private class HistoryDateBounds {
    val today: Long
    val yesterday: Long
    val week: Long
    val month: Long

    init {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        today = start.timeInMillis
        yesterday = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        week = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        month = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -29) }.timeInMillis
    }
}

private fun getHistoryDateGroup(timestamp: Long, bounds: HistoryDateBounds): String {
    if (timestamp <= 0L) return HistoryDateGroup.OLDER.label

    return when {
        timestamp >= bounds.today -> HistoryDateGroup.TODAY.label
        timestamp >= bounds.yesterday -> HistoryDateGroup.YESTERDAY.label
        timestamp >= bounds.week -> HistoryDateGroup.THIS_WEEK.label
        timestamp >= bounds.month -> HistoryDateGroup.THIS_MONTH.label
        else -> {
            // Older items grouped by "Month Year" (e.g. "September 2026")
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

/** Sealed list item for the LazyColumn — either a date-group header or a history record. */
@Immutable
private sealed class HistoryListItem {
    @Immutable
    data class Header(val label: String) : HistoryListItem()
    @Immutable
    data class Entry(val record: Record, val indexInGroup: Int, val groupSize: Int) : HistoryListItem()
}

@Immutable
enum class HistorySortOption(val label: String) {
    TIME_DESC("Recent"),
    NAME_ASC("A–Z"),
    NAME_DESC("Z–A"),
    SITE_ASC("By Site"),
    TIME_ASC("Oldest")
}

private fun extractDomain(record: Record): String {
    val rawDomain = record.domain?.takeIf { it.isNotBlank() }
        ?: try { java.net.URI(record.url ?: "").host } catch (_: Exception) { null }
    return rawDomain?.removePrefix("www.")?.ifBlank { "Other" } ?: "Other"
}

/** Build a flat list of [HistoryListItem]s with group headers inserted according to [HistorySortOption]. */
private fun buildGroupedItems(
    records: List<Record>,
    sortOption: HistorySortOption
): List<HistoryListItem> {
    if (records.isEmpty()) return emptyList()

    val sortedRecords = when (sortOption) {
        HistorySortOption.TIME_DESC -> records.sortedByDescending { it.time }
        HistorySortOption.TIME_ASC -> records.sortedBy { it.time }
        HistorySortOption.NAME_ASC -> records.sortedWith(
            compareBy<Record> { (it.title?.trim()?.ifBlank { it.url } ?: "").lowercase(Locale.getDefault()) }
                .thenByDescending { it.time }
        )
        HistorySortOption.NAME_DESC -> records.sortedWith(
            compareByDescending<Record> { (it.title?.trim()?.ifBlank { it.url } ?: "").lowercase(Locale.getDefault()) }
                .thenByDescending { it.time }
        )
        HistorySortOption.SITE_ASC -> records.sortedWith(
            compareBy<Record> { extractDomain(it).lowercase(Locale.getDefault()) }
                .thenByDescending { it.time }
        )
    }

    val dateBounds = HistoryDateBounds()
    val result = mutableListOf<HistoryListItem>()
    var currentGroup: String? = null
    var groupBuffer = mutableListOf<Record>()

    fun flushGroup() {
        if (groupBuffer.isEmpty()) return
        result.add(HistoryListItem.Header(currentGroup!!))
        groupBuffer.forEachIndexed { i, r ->
            result.add(HistoryListItem.Entry(r, i, groupBuffer.size))
        }
        groupBuffer = mutableListOf()
    }

    for (record in sortedRecords) {
        val group = when (sortOption) {
            HistorySortOption.TIME_DESC, HistorySortOption.TIME_ASC -> getHistoryDateGroup(record.time, dateBounds)
            HistorySortOption.NAME_ASC, HistorySortOption.NAME_DESC -> {
                extractFastScrollGlyph(record.title?.ifBlank { record.url }) ?: "#"
            }
            HistorySortOption.SITE_ASC -> extractDomain(record)
        }
        if (group != currentGroup) {
            flushGroup()
            currentGroup = group
        }
        groupBuffer.add(record)
    }
    flushGroup()
    return result
}

object PetalHistoryBridge {
    @JvmStatic
    fun createHistoryView(
        activity: ComponentActivity,
        onOpenUrl: HistoryUrlHandler,
        onClearBrowsingData: HistoryActionHandler,
        onBackPress: () -> Unit
    ): android.view.View {
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
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val fontState = com.petal.browser.ui.theme.rememberFontState()
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    fontState = fontState,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalHistoryScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onOpenUrl = { url -> onOpenUrl.open(url) },
                        onClearBrowsingData = { onClearBrowsingData.action() },
                        onDismiss = onBackPress
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalHistoryScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onOpenUrl: (String) -> Unit = {},
    onOpenUrlInNewTab: ((String) -> Unit)? = null,
    onClearBrowsingData: () -> Unit = {},
    onDismiss: () -> Unit = {},
    isIncognito: Boolean = false
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val recentsKey = "history"
    var recentSearches by remember { mutableStateOf(com.petal.browser.ui.components.PetalRecentSearches.load(context, recentsKey)) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var optionsRecord by remember { mutableStateOf<Record?>(null) }

    // Load history records from SQLite database asynchronously.
    // listHistory() returns records in DESC order (most recent first) — no reversal needed.
    var rawHistory by remember { mutableStateOf<List<Record>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val action = HistoryBookmarkRepository(context)
            action.open(false)
            val list = action.listHistory(context)
            action.close()
            rawHistory = list.filter { record ->
                val url = record.url?.trim() ?: ""
                url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true) && !url.startsWith("about:", ignoreCase = true)
            }
        } catch (e: Exception) {
            rawHistory = emptyList()
        }
    }

    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var sortOption by remember {
        val saved = sp.getString("sp_history_sort_mode", HistorySortOption.TIME_DESC.name)
        mutableStateOf(try { HistorySortOption.valueOf(saved ?: "") } catch (_: Exception) { HistorySortOption.TIME_DESC })
    }

    val filteredHistory = remember(searchQuery, rawHistory) {
        val historyList = rawHistory ?: emptyList()
        if (searchQuery.isBlank()) {
            historyList
        } else {
            val query = searchQuery.trim().lowercase()
            val tokens = query.split("\\s+".toRegex()).filter { it.isNotBlank() }
            historyList.filter { record ->
                val title = record.title?.lowercase() ?: ""
                val url = record.url?.lowercase() ?: ""
                title.contains(query) || url.contains(query) ||
                    (tokens.isNotEmpty() && tokens.all { title.contains(it) || url.contains(it) })
            }
        }
    }

    // Build grouped items — headers inserted when browsing normally according to sortOption.
    // When searching, sort the matching results according to sortOption.
    val groupedItems = remember(filteredHistory, searchQuery, sortOption) {
        if (searchQuery.isBlank()) {
            buildGroupedItems(filteredHistory, sortOption)
        } else {
            val sortedMatches = when (sortOption) {
                HistorySortOption.TIME_DESC -> filteredHistory.sortedByDescending { it.time }
                HistorySortOption.TIME_ASC -> filteredHistory.sortedBy { it.time }
                HistorySortOption.NAME_ASC -> filteredHistory.sortedWith(
                    compareBy<Record> { (it.title?.trim()?.ifBlank { it.url } ?: "").lowercase(Locale.getDefault()) }
                        .thenByDescending { it.time }
                )
                HistorySortOption.NAME_DESC -> filteredHistory.sortedWith(
                    compareByDescending<Record> { (it.title?.trim()?.ifBlank { it.url } ?: "").lowercase(Locale.getDefault()) }
                        .thenByDescending { it.time }
                )
                HistorySortOption.SITE_ASC -> filteredHistory.sortedWith(
                    compareBy<Record> { extractDomain(it).lowercase(Locale.getDefault()) }
                        .thenByDescending { it.time }
                )
            }
            sortedMatches.mapIndexed { i, r -> HistoryListItem.Entry(r, i, sortedMatches.size) }
        }
    }

    if (showClearConfirm) {
        PetalExpressiveAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = Icons.Rounded.DeleteSweep,
            title = stringResource(R.string.ui_clear_all_history),
            message = stringResource(R.string.ui_this_will_permanently_remove_all),
            confirmText = stringResource(R.string.ui_clear_all),
            dismissText = stringResource(R.string.ui_cancel),
            destructive = true,
            onConfirm = {
                try {
                    com.petal.browser.unit.PetalBrowserUtils.clearHistory(context)
                    rawHistory = emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val hasSubOverlay = searchExpanded || showClearConfirm || searchQuery.isNotEmpty()
    val handleDismiss: () -> Unit = {
        when {
            searchExpanded -> searchExpanded = false
            showClearConfirm -> showClearConfirm = false
            searchQuery.isNotEmpty() -> searchQuery = ""
            else -> onDismiss()
        }
    }

    if (hasSubOverlay) {
        androidx.activity.compose.BackHandler(enabled = true) {
            handleDismiss()
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = !hasSubOverlay,
        onBack = onDismiss,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            com.petal.browser.ui.containment.PetalSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {

        val headerGlassPaneH = com.petal.browser.ui.components.rememberHeaderGlassActive()
        val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
                val searchShape = RoundedCornerShape(16.dp)
        val headerExtrasH: @Composable () -> Unit = {
                com.petal.browser.ui.components.PetalSearchPill(
                    query = searchQuery,
                    placeholder = stringResource(R.string.ui_search_history),
                    onClick = { searchExpanded = true },
                    onClear = { searchQuery = "" }
                )

                // ── Sort: connected toggle group (selected option expands, others collapse to icons) ──
                com.petal.browser.ui.components.PetalChoiceButtonGroup(
                    options = HistorySortOption.values().map { option ->
                        com.petal.browser.ui.components.PetalButtonGroupItem(
                            value = option,
                            label = option.label,
                            icon = when (option) {
                                HistorySortOption.TIME_DESC -> Icons.Rounded.Schedule
                                HistorySortOption.NAME_ASC -> Icons.Rounded.SortByAlpha
                                HistorySortOption.NAME_DESC -> Icons.Rounded.Sort
                                HistorySortOption.SITE_ASC -> Icons.Rounded.Public
                                HistorySortOption.TIME_ASC -> Icons.Rounded.History
                            }
                        )
                    },
                    selected = sortOption,
                    onSelect = { option ->
                        sortOption = option
                        sp.edit().putString("sp_history_sort_mode", option.name).apply()
                    },
                    role = com.petal.browser.ui.components.PetalGroupRole.Secondary,
                    collapseUnselected = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
        }
        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            pageBackground = { PetalPageBackground("history_page") },
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,

                    title = "History",
                    subtitle = "${filteredHistory.size} items",
                    onBack = { if (hasSubOverlay) handleDismiss() else onDismiss() },
                    actions = {
                        if (rawHistory?.isNotEmpty() == true) {
                            HeaderActionIcon(
                                icon = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear History",
                                onClick = { showClearConfirm = true }
                            )
                        }
                    },
                    bottomContent = if (headerGlassPaneH) { { headerExtrasH() } } else null
                )
            }
        ) { topInset ->
            Column(Modifier.fillMaxSize()) {
                if (!headerGlassPaneH) headerExtrasH()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    com.petal.browser.ui.components.PetalSkeletonSwitcher(
                        isLoading = rawHistory == null,
                        modifier = Modifier.fillMaxSize(),
                        skeleton = {
                            com.petal.browser.ui.components.PetalSkeletonList(
                                modifier = Modifier.fillMaxSize(),
                                rows = 9,
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topInset + 8.dp)
                            )
                        }
                    ) {
                        val historyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        LazyColumn(
                            state = historyListState,
                            flingBehavior = petalFlingBehavior(),
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollMotionBlur(historyListState),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = topInset + 8.dp,
                                bottom = navBarBottom + 24.dp
                            )
                        ) {
                            item(key = "clear_banner") {
                                PetalGroupRow(
                                    icon = Icons.Rounded.CleaningServices,
                                    title = stringResource(R.string.ui_clear_browsing_data_2),
                                    position = PetalGroupPosition.SINGLE,
                                    onClick = onClearBrowsingData,
                                )
                            }

                            if (groupedItems.isEmpty()) {
                                item(key = "empty_state") {
                                    com.petal.browser.ui.components.EmptyStateBlob(
                                        illustrationType = com.petal.browser.ui.components.EmptyStateIllustrationType.HISTORY,
                                        title = if (searchQuery.isNotEmpty()) "No Matching History" else "No Browsing History Yet",
                                        description = if (searchQuery.isNotEmpty()) "Try searching with a different URL or keyword" else "Websites and pages you visit will appear here"
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    items = groupedItems,
                                    key = { _, item ->
                                        when (item) {
                                            is HistoryListItem.Header -> "header_${sortOption.name}_${item.label}"
                                            is HistoryListItem.Entry -> "${sortOption.name}_${item.record.url}_${item.record.time}_${item.indexInGroup}"
                                        }
                                    }
                                ) { _, listItem ->
                                    when (listItem) {
                                        is HistoryListItem.Header -> {
                                            // Firefox-style date group label
                                            Text(
                                                text = listItem.label,
                                                style = PetalText.sectionHeader,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
                                            )
                                        }
                                        is HistoryListItem.Entry -> {
                                            val record = listItem.record
                                            val timeFormatted = remember(record.time) {
                                                if (record.time > 0L) SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(record.time)) else ""
                                            }
                                            val faviconUrl = remember(record.url) {
                                                val domain = record.domain?.takeIf { it.isNotBlank() }
                                                    ?: try { java.net.URI(record.url ?: "").host } catch (_: Exception) { null }
                                                if (!domain.isNullOrEmpty()) "https://www.google.com/s2/favicons?domain=$domain&sz=32" else null
                                            }
                                            val deleteEntry: () -> Unit = {
                                                            val deletedRecord = record
                                                            val deletedIndex = rawHistory?.indexOfFirst { it.url == record.url } ?: -1
                                                            try {
                                                                val action = HistoryBookmarkRepository(context)
                                                                action.open(true)
                                                                action.deleteURL(deletedRecord.url, HistoryBookmarkContract.TABLE_HISTORY)
                                                                action.close()
                                                                rawHistory = rawHistory?.filter { it.url != deletedRecord.url } ?: emptyList()
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                                                            coroutineScope.launch {
                                                                val displayTitle = deletedRecord.title?.takeIf { it.isNotBlank() } ?: deletedRecord.url ?: "History item"
                                                                val result = snackbarHostState.showSnackbar(
                                                                    message = "Deleted \"$displayTitle\"",
                                                                    actionLabel = "Undo",
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                    try {
                                                                        val action = HistoryBookmarkRepository(context)
                                                                        action.open(true)
                                                                        action.addHistory(deletedRecord)
                                                                        action.close()
                                                                        val current = rawHistory?.toMutableList() ?: mutableListOf()
                                                                        if (deletedIndex in 0..current.size) {
                                                                            current.add(deletedIndex, deletedRecord)
                                                                        } else {
                                                                            current.add(deletedRecord)
                                                                        }
                                                                        rawHistory = current
                                                                    } catch (e: Exception) {
                                                                        e.printStackTrace()
                                                                    }
                                                                }
                                                            }
                                            }
                                            val swipeOpenLabel = stringResource(R.string.ui_new_tab)
                                            val swipeDeleteLabel = stringResource(R.string.ui_delete)
                                            PetalSwipeActionRow(
                                                modifier = Modifier.animateItem(
                                                    fadeInSpec = PetalSpring.calm(),
                                                    fadeOutSpec = PetalSpring.calm(),
                                                    placementSpec = PetalSpring.calm()
                                                ).entrance(index = listItem.indexInGroup, playKey = "${record.url}_${record.time}"),
                                                // One direction only: the right edge belongs to the fast-scroll bar.
                                                startActions = listOf(
                                                    PetalRowSwipeAction(
                                                        label = swipeOpenLabel,
                                                        icon = Icons.Rounded.Public,
                                                        role = PetalGroupRole.Secondary,
                                                        onTrigger = { record.url?.let { u -> (onOpenUrlInNewTab ?: onOpenUrl)(u) } }
                                                    ),
                                                    PetalRowSwipeAction(
                                                        label = swipeDeleteLabel,
                                                        icon = Icons.Rounded.Close,
                                                        destructive = true,
                                                        onTrigger = deleteEntry
                                                    )
                                                )
                                            ) {
                                            PetalGroupListRow(
                                                position = petalGroupPositionFor(listItem.indexInGroup, listItem.groupSize),
                                                onClick = { record.url?.let(onOpenUrl) },
                                                onLongClick = { optionsRecord = record },
                                                leading = {
                                                    Box(
                                                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (faviconUrl != null) {
                                                            SubcomposeAsyncImage(
                                                                model = faviconUrl,
                                                                contentDescription = "Website Icon",
                                                                modifier = Modifier.size(24.dp).clip(ExpressiveButtonShape),
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                                                loading = { Icon(Icons.Rounded.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) },
                                                                error = { Icon(Icons.Rounded.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
                                                            )
                                                        } else Icon(Icons.Rounded.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                                                    }
                                                },
                                                content = {
                                                    Text(record.title?.takeIf { it.isNotBlank() } ?: record.domain ?: "Web Page",
                                                        style = PetalText.listItemTitle)
                                                    Text(record.url ?: "", style = PetalText.listItemMeta,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    if (timeFormatted.isNotEmpty()) Text(timeFormatted, style = PetalText.caption,
                                                        color = MaterialTheme.colorScheme.primary)
                                                },
                                                trailing = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(onClick = deleteEntry) {
                                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_remove_entry), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                        }
                                                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        PetalExpressiveScrollBar(
                            listState = historyListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(top = 8.dp, bottom = navBarBottom + 8.dp),
                            dragLabelProvider = { index ->
                                if (index == 0) null
                                else when (val item = groupedItems.getOrNull(index - 1)) {
                                    is HistoryListItem.Header -> item.label.take(2).uppercase()
                                    is HistoryListItem.Entry -> {
                                        when (sortOption) {
                                            HistorySortOption.NAME_ASC, HistorySortOption.NAME_DESC -> {
                                                extractFastScrollGlyph(item.record.title?.ifBlank { item.record.url })
                                            }
                                            HistorySortOption.SITE_ASC -> {
                                                extractFastScrollGlyph(extractDomain(item.record))
                                            }
                                            else -> extractFastScrollGlyph(item.record.title)
                                        }
                                    }
                                    null -> null
                                }
                            }
                        )
                    }
                }
            }
        }

        com.petal.browser.ui.components.PetalSearchOverlay(
            visible = searchExpanded,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.ui_search_history),
            recents = recentSearches,
            resultCount = filteredHistory.size,
            onSubmit = { q ->
                recentSearches = com.petal.browser.ui.components.PetalRecentSearches.add(context, recentsKey, recentSearches, q, isIncognito)
                searchExpanded = false
            },
            onRemoveRecent = { q -> recentSearches = com.petal.browser.ui.components.PetalRecentSearches.remove(context, recentsKey, recentSearches, q) },
            onClearRecents = {
                com.petal.browser.ui.components.PetalRecentSearches.clear(context, recentsKey)
                recentSearches = emptyList()
            },
            onCollapse = { searchExpanded = false },
            modifier = Modifier.fillMaxSize()
        )
        }
    }

    // History Item Options Sheet (PixelMusic-style Material 3 Expressive Glass)
    optionsRecord?.let { rec ->
        val recordUrl = rec.url ?: ""
        val recordTitle = rec.title?.ifBlank { recordUrl } ?: "History Item"
        PetalSheet(
            onDismissRequest = { optionsRecord = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PetalSheetHeader(
                    title = recordTitle,
                    subtitle = recordUrl,
                    leading = {
                        Box(
                            Modifier
                                .size(64.dp)
                                .petalSheetSurface(
                                    com.petal.browser.ui.theme.SmoothRoundedShape(20.dp),
                                    MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))

                PetalSheetPill(
                    text = "Open in New Tab",
                    icon = Icons.Rounded.OpenInNew,
                    tone = PetalSheetTone.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        optionsRecord = null
                        if (onOpenUrlInNewTab != null) {
                            onOpenUrlInNewTab(recordUrl)
                        } else {
                            (context as? com.petal.browser.activity.MainActivity)?.let { act ->
                                act.addAlbum(recordTitle, recordUrl, true)
                            } ?: onOpenUrl(recordUrl)
                        }
                        onDismiss()
                    }
                )

                PetalSheetActionRow {
                    PetalSheetPill(
                        text = "Copy Link",
                        icon = Icons.Rounded.ContentCopy,
                        tone = PetalSheetTone.Secondary,
                        modifier = Modifier.weight(0.5f),
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", recordUrl))
                            com.petal.browser.view.PetalToast.show(context, "Link copied to clipboard")
                            optionsRecord = null
                        }
                    )
                    PetalSheetPill(
                        text = "Share",
                        icon = Icons.Rounded.Share,
                        tone = PetalSheetTone.Secondary,
                        modifier = Modifier.weight(0.5f),
                        onClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, recordUrl)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Link"))
                            optionsRecord = null
                        }
                    )
                }

                PetalSheetActionRow {
                    PetalSheetPill(
                        text = "Bookmark",
                        icon = Icons.Rounded.BookmarkAdd,
                        tone = PetalSheetTone.Secondary,
                        modifier = Modifier.weight(0.5f),
                        onClick = {
                            try {
                                val action = HistoryBookmarkRepository(context)
                                action.open(true)
                                action.addBookmark(Record(recordTitle, recordUrl, 0L, 0))
                                action.close()
                                com.petal.browser.view.PetalToast.show(context, "Saved to bookmarks")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            optionsRecord = null
                        }
                    )
                    PetalSheetPill(
                        text = "Delete",
                        icon = Icons.Rounded.DeleteOutline,
                        tone = PetalSheetTone.Danger,
                        modifier = Modifier.weight(0.5f),
                        onClick = {
                            val toDelete = rec
                            optionsRecord = null
                            try {
                                val action = HistoryBookmarkRepository(context)
                                action.open(true)
                                action.deleteURL(toDelete.url, HistoryBookmarkContract.TABLE_HISTORY)
                                action.close()
                                rawHistory = rawHistory?.filter { it.url != toDelete.url } ?: emptyList()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
        }
    }
}
}
}
