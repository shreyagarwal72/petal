/*
 * PetalHistoryScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome Android-inspired Material 3 Expressive History Page for Petal Browser.
 * Features live search/filter, Firefox-style date groups, individual entry deletion,
 * clear browsing data action, and 60fps smooth animations.
 */

package com.petal.browser.compose.history
import com.petal.browser.ui.components.scrollMotionBlur

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.RecordUnit
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.PetalExpressiveAlertDialog
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.isLiquidGlassEnabled
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

private fun getHistoryDateGroup(timestamp: Long): String {
    if (timestamp <= 0L) return HistoryDateGroup.OLDER.label

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val yesterdayStart = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekStart = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
    val monthStart = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -29) }

    return when {
        timestamp >= todayStart.timeInMillis -> HistoryDateGroup.TODAY.label
        timestamp >= yesterdayStart.timeInMillis -> HistoryDateGroup.YESTERDAY.label
        timestamp >= weekStart.timeInMillis -> HistoryDateGroup.THIS_WEEK.label
        timestamp >= monthStart.timeInMillis -> HistoryDateGroup.THIS_MONTH.label
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

/** Build a flat list of [HistoryListItem]s with group headers inserted, preserving DESC order. */
private fun buildGroupedItems(records: List<Record>): List<HistoryListItem> {
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

    for (record in records) {
        val group = getHistoryDateGroup(record.time)
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
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

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
    onClearBrowsingData: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Load history records from SQLite database asynchronously.
    // listHistory() returns records in DESC order (most recent first) — no reversal needed.
    var rawHistory by remember { mutableStateOf<List<Record>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val action = RecordAction(context)
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

    val filteredHistory = remember(searchQuery, rawHistory) {
        val historyList = rawHistory ?: emptyList()
        if (searchQuery.isBlank()) {
            historyList
        } else {
            val query = searchQuery.trim().lowercase()
            historyList.filter { record ->
                (record.title?.lowercase()?.contains(query) == true) ||
                (record.url?.lowercase()?.contains(query) == true)
            }
        }
    }

    // Build grouped items — date headers inserted when browsing normally.
    // When searching, show flat results without headers for clarity.
    val groupedItems = remember(filteredHistory, searchQuery) {
        if (searchQuery.isBlank()) buildGroupedItems(filteredHistory)
        else filteredHistory.mapIndexed { i, r -> HistoryListItem.Entry(r, i, filteredHistory.size) }
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
                    val action = RecordAction(context)
                    action.open(true)
                    action.clearTable(RecordUnit.TABLE_HISTORY)
                    action.close()
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

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
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
            M3ExpressiveVariableBackground(pageSeed = "history_page")

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
                ExpressiveHeader(
                    title = "History",
                    subtitle = "${filteredHistory.size} items",
                    onBack = onDismiss,
                    actions = {
                        if (rawHistory?.isNotEmpty() == true) {
                            HeaderActionIcon(
                                icon = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear History",
                                onClick = { showClearConfirm = true }
                            )
                        }
                    }
                )

                val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
                val searchShape = RoundedCornerShape(16.dp)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .liquidGlassChrome(shape = searchShape, enabled = isGlass, sheenIntensity = 0.45f),
                    placeholder = { Text(stringResource(R.string.ui_search_history)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = searchShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    if (rawHistory == null) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val historyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyColumn(
                            state = historyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollMotionBlur(historyListState),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item(key = "clear_banner") {
                                PetalGroupRow(
                                    icon = Icons.Filled.CleaningServices,
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
                                            is HistoryListItem.Header -> "header_${item.label}"
                                            is HistoryListItem.Entry -> "${item.record.url}_${item.record.time}"
                                        }
                                    }
                                ) { _, listItem ->
                                    when (listItem) {
                                        is HistoryListItem.Header -> {
                                            // Firefox-style date group label
                                            Text(
                                                text = listItem.label,
                                                style = MaterialTheme.typography.labelMedium,
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
                                            PetalGroupListRow(
                                                position = petalGroupPositionFor(listItem.indexInGroup, listItem.groupSize),
                                                onClick = { record.url?.let(onOpenUrl) },
                                                modifier = Modifier.animateItem(
                                                    fadeInSpec = PetalSpring.fadeIn(),
                                                    fadeOutSpec = PetalSpring.fadeOut(),
                                                    placementSpec = PetalSpring.spatial()
                                                ).entrance(index = listItem.indexInGroup, playKey = "${record.url}_${record.time}"),
                                                leading = {
                                                    Box(
                                                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (faviconUrl != null) {
                                                            SubcomposeAsyncImage(
                                                                model = faviconUrl,
                                                                contentDescription = "Website Icon",
                                                                modifier = Modifier.size(24.dp).clip(CircleShape),
                                                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                                                loading = { Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) },
                                                                error = { Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
                                                            )
                                                        } else Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                                                    }
                                                },
                                                content = {
                                                    Text(record.title?.takeIf { it.isNotBlank() } ?: record.domain ?: "Web Page",
                                                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(record.url ?: "", style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    if (timeFormatted.isNotEmpty()) Text(timeFormatted, style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary)
                                                },
                                                trailing = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(onClick = {
                                                            val deletedRecord = record
                                                            val deletedIndex = rawHistory?.indexOfFirst { it.url == record.url } ?: -1
                                                            try {
                                                                val action = RecordAction(context)
                                                                action.open(true)
                                                                action.deleteURL(deletedRecord.url, RecordUnit.TABLE_HISTORY)
                                                                action.close()
                                                                rawHistory = rawHistory?.filter { it.url != deletedRecord.url } ?: emptyList()
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                            coroutineScope.launch {
                                                                val displayTitle = deletedRecord.title?.takeIf { it.isNotBlank() } ?: deletedRecord.url ?: "History item"
                                                                val result = snackbarHostState.showSnackbar(
                                                                    message = "Deleted \"$displayTitle\"",
                                                                    actionLabel = "Undo",
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                    try {
                                                                        val action = RecordAction(context)
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
                                                        }) {
                                                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_remove_entry), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                        }
                                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }
        }
    }
}
}
}
