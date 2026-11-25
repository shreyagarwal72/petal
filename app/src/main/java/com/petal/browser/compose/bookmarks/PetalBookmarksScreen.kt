/*
 * PetalBookmarksScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome Android-inspired Material 3 Expressive Bookmarks Page for Petal Browser.
 * Features live search/filter, individual bookmark deletion, bookmark creation,
 * share actions, and 60fps smooth animations.
 */

package com.petal.browser.compose.bookmarks

import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.components.PetalExpressiveScrollBar
import com.petal.browser.ui.components.extractFastScrollGlyph
import com.petal.browser.ui.theme.layout.petalFlingBehavior

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.petal.browser.account.mozilla.FirefoxDataImportManager
import com.petal.browser.view.PetalToast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.petal.browser.ui.containment.PetalSheetHeader
import com.petal.browser.ui.containment.PetalSheetPill
import com.petal.browser.ui.containment.PetalSheetActionRow
import com.petal.browser.ui.containment.PetalSheetTone
import com.petal.browser.ui.containment.petalSheetSurface
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
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil.compose.AsyncImage
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.PetalPageBackground
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.components.PetalSwipeActionRow
import com.petal.browser.ui.components.PetalRowSwipeAction
import com.petal.browser.ui.components.PetalGroupRole
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.components.PetalExpressiveAlertDialog
import com.petal.browser.ui.components.PetalExpressiveDialog
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.compose.home.getFaviconUrl
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

fun interface BookmarkUrlHandler {
    fun open(url: String)
}

fun interface BookmarkActionHandler {
    fun action()
}

object PetalBookmarksBridge {
    @JvmStatic
    fun createBookmarksView(
        activity: ComponentActivity,
        onOpenUrl: BookmarkUrlHandler,
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
                    PetalBookmarksScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onOpenUrl = { url -> onOpenUrl.open(url) },
                        onDismiss = onBackPress
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalBookmarksScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onOpenUrl: (String) -> Unit = {},
    onBookmarksChanged: () -> Unit = {},
    onDismiss: () -> Unit = {},
    isIncognito: Boolean = false
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val recentsKey = "bookmarks"
    var recentSearches by remember { mutableStateOf(com.petal.browser.ui.components.PetalRecentSearches.load(context, recentsKey)) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showReadingListOnly by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<Record?>(null) }
    var optionsRecord by remember { mutableStateOf<Record?>(null) }

    // Load bookmarks from SQLite database asynchronously
    var rawBookmarks by remember { mutableStateOf<List<Record>?>(null) }
    
    val reloadBookmarks: () -> Unit = {
        try {
            val action = HistoryBookmarkRepository(context)
            action.open(false)
            val list = action.listBookmark(context, false, 0)
            action.close()
            rawBookmarks = list.filter { record ->
                val url = record.url?.trim() ?: ""
                url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true)
            }
            onBookmarksChanged()
        } catch (e: Exception) {
            rawBookmarks = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        reloadBookmarks()
    }

    var overflowMenuExpanded by remember { mutableStateOf(false) }

    val filteredBookmarks = remember(searchQuery, rawBookmarks, showReadingListOnly) {
        val list = rawBookmarks ?: emptyList()
        val scoped = if (showReadingListOnly) list.filter { it.isReadingList } else list
        if (searchQuery.isBlank()) {
            scoped
        } else {
            val query = searchQuery.trim().lowercase()
            scoped.filter { record ->
                (record.title?.lowercase()?.contains(query) == true) ||
                (record.url?.lowercase()?.contains(query) == true)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val count = FirefoxDataImportManager.importFile(context, uri)
                    if (count > 0) {
                        PetalToast.show(context, "Imported $count bookmarks")
                        reloadBookmarks()
                    } else {
                        PetalToast.show(context, "No bookmarks found in selected file")
                    }
                } catch (e: Exception) {
                    PetalToast.show(context, "Import failed: ${e.message}")
                }
            }
        }
    }

    val hasSubOverlay = searchExpanded || editingRecord != null || optionsRecord != null || showAddDialog || showClearConfirm || showReadingListOnly || searchQuery.isNotEmpty()
    val handleDismiss: () -> Unit = {
        when {
            searchExpanded -> searchExpanded = false
            optionsRecord != null -> optionsRecord = null
            editingRecord != null -> editingRecord = null
            showAddDialog -> showAddDialog = false
            showClearConfirm -> showClearConfirm = false
            showReadingListOnly -> showReadingListOnly = false
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
            val headerGlassPaneB = com.petal.browser.ui.components.rememberHeaderGlassActive()
            // Search Filter Bar
                val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
                val searchShape = RoundedCornerShape(24.dp)
            val headerExtrasB: @Composable () -> Unit = {
                com.petal.browser.ui.components.PetalSearchPill(
                    query = searchQuery,
                    placeholder = stringResource(R.string.ui_search_bookmarks),
                    onClick = { searchExpanded = true },
                    onClear = { searchQuery = "" }
                )

                Spacer(Modifier.height(8.dp))

                // All / Reading List: connected toggle group (secondary role, glass-aware).
                val readingListCount = remember(rawBookmarks) {
                    rawBookmarks?.count { it.isReadingList } ?: 0
                }
                com.petal.browser.ui.components.PetalChoiceButtonGroup(
                    options = listOf(
                        com.petal.browser.ui.components.PetalButtonGroupItem(false, "All", icon = Icons.Rounded.Bookmarks),
                        com.petal.browser.ui.components.PetalButtonGroupItem(
                            true,
                            if (readingListCount > 0) "Reading List ($readingListCount)" else "Reading List",
                            icon = Icons.Rounded.AutoStories
                        ),
                    ),
                    selected = showReadingListOnly,
                    onSelect = { showReadingListOnly = it },
                    role = com.petal.browser.ui.components.PetalGroupRole.Secondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            com.petal.browser.ui.components.PetalGlassHeaderLayout(
                modifier = Modifier.fillMaxSize(),
                pageBackground = { com.petal.browser.ui.components.PetalPageBackground("bookmarks_page") },
                header = { headerModifier, headerBackdrop ->
                    ExpressiveHeader(
                        modifier = headerModifier,
                        backdrop = headerBackdrop,

                    title = "Bookmarks",
                    subtitle = "${filteredBookmarks.size} saved items",
                    onBack = { if (hasSubOverlay) handleDismiss() else onDismiss() },
                    actions = {
                        HeaderActionIcon(
                            icon = Icons.Rounded.Add,
                            contentDescription = "Add Bookmark",
                            onClick = { showAddDialog = true }
                        )
                        if (!rawBookmarks.isNullOrEmpty()) {
                            val menuRotation by animateFloatAsState(
                                targetValue = if (overflowMenuExpanded) 180f else 0f,
                                animationSpec = PetalSpring.calm(),
                                label = "bookmark_menu_rot"
                            )
                            Box {
                                IconButton(
                                    onClick = { overflowMenuExpanded = true },
                                    modifier = Modifier.graphicsLayer { rotationZ = menuRotation }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "More Options",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                com.petal.browser.ui.containment.PetalPopupMenu(
                                    expanded = overflowMenuExpanded,
                                    onDismissRequest = { overflowMenuExpanded = false }
                                ) {
                                    com.petal.browser.ui.containment.PetalPopupMenuItem(
                                        text = "Import Bookmarks (HTML / JSON)",
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.CloudDownload,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            importLauncher.launch(arrayOf("text/html", "application/json", "*/*"))
                                        }
                                    )
                                    com.petal.browser.ui.containment.PetalPopupMenuItem(
                                        text = "Export Bookmarks (HTML)",
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.CloudUpload,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            val list = rawBookmarks ?: emptyList()
                                            if (list.isEmpty()) {
                                                PetalToast.show(context, "No bookmarks to export")
                                            } else {
                                                val file = PetalBookmarkHtmlExporter.exportToDownloads(context, list)
                                                if (file != null) {
                                                    PetalToast.show(context, "Exported ${list.size} bookmarks to Downloads")
                                                } else {
                                                    PetalToast.show(context, "Failed to export bookmarks")
                                                }
                                            }
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    com.petal.browser.ui.containment.PetalPopupMenuItem(
                                        text = stringResource(R.string.ui_clear_all_bookmarks),
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.DeleteSweep,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        destructive = true,
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showClearConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                        bottomContent = if (headerGlassPaneB) { { headerExtrasB() } } else null
                    )
                }
            ) { topInset ->
                Column(Modifier.fillMaxSize()) {
                    if (!headerGlassPaneB) headerExtrasB()

                Spacer(Modifier.height(4.dp))

                com.petal.browser.ui.components.PetalSkeletonSwitcher(
                    isLoading = rawBookmarks == null,
                    modifier = Modifier.fillMaxSize(),
                    skeleton = {
                        com.petal.browser.ui.components.PetalSkeletonList(
                            modifier = Modifier.fillMaxSize(),
                            rows = 9,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 8.dp)
                        )
                    }
                ) {
                if (filteredBookmarks.isEmpty()) {
                    com.petal.browser.ui.components.EmptyStateBlob(
                        illustrationType = com.petal.browser.ui.components.EmptyStateIllustrationType.BOOKMARKS,
                        title = if (searchQuery.isEmpty()) {
                            if (showReadingListOnly) "Reading List Empty" else "No Bookmarks Saved Yet"
                        } else "No Matching Bookmarks",
                        description = if (searchQuery.isEmpty()) {
                            if (showReadingListOnly) "Add web pages to your reading list to read them later" else "Tap the star icon on web pages to save them"
                        } else "Try searching with a different URL or keyword"
                    )
                } else {
                    val bookmarksListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = bookmarksListState,
                            flingBehavior = petalFlingBehavior(),
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollMotionBlur(bookmarksListState),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                        itemsIndexed(filteredBookmarks, key = { index, record -> "${record.url}_${record.time}_${record.iconColor}_$index" }) { index, record ->
                            val faviconUrl = remember(record.url) { getFaviconUrl(record.url) }
                            var isFaviconError by remember(record.url) { mutableStateOf(false) }
                            val deleteEntry: () -> Unit = {
                                                val deletedRecord = record
                                                try {
                                                    val action = HistoryBookmarkRepository(context)
                                                    action.open(true)
                                                    action.deleteURL(deletedRecord.url, HistoryBookmarkContract.TABLE_BOOKMARK)
                                                    action.close()
                                                } catch (_: Exception) {}
                                                reloadBookmarks()

                                                com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                                                coroutineScope.launch {
                                                    val displayTitle = deletedRecord.title?.takeIf { it.isNotBlank() } ?: deletedRecord.url ?: "Bookmark"
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Deleted \"$displayTitle\"",
                                                        actionLabel = "Undo",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        try {
                                                            val action = HistoryBookmarkRepository(context)
                                                            action.open(true)
                                                            action.addBookmark(deletedRecord)
                                                            action.close()
                                                        } catch (_: Exception) {}
                                                        reloadBookmarks()
                                                    }
                                                }
                            }
                            val swipeEditLabel = stringResource(R.string.ui_swipe_edit)
                            val swipeDeleteLabel = stringResource(R.string.ui_delete)
                            PetalSwipeActionRow(
                                modifier = Modifier.animateItem().entrance(index = index, playKey = "${record.url}_${record.iconColor}"),
                                startActions = listOf(
                                    PetalRowSwipeAction(
                                        label = swipeEditLabel,
                                        icon = Icons.Rounded.Edit,
                                        role = PetalGroupRole.Tertiary,
                                        onTrigger = { optionsRecord = record }
                                    )
                                ),
                                endActions = listOf(
                                    PetalRowSwipeAction(
                                        label = swipeDeleteLabel,
                                        icon = Icons.Rounded.Close,
                                        destructive = true,
                                        onTrigger = deleteEntry
                                    )
                                )
                            ) {
                            PetalGroupListRow(
                                position = petalGroupPositionFor(index, filteredBookmarks.size),
                                onClick = { onOpenUrl(record.url) },
                                leading = {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!faviconUrl.isNullOrEmpty() && !isFaviconError) {
                                            AsyncImage(
                                                model = faviconUrl,
                                                contentDescription = record.title,
                                                onError = { isFaviconError = true },
                                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                        } else {
                                            Icon(if (record.isReadingList) Icons.Rounded.AutoStories else Icons.Rounded.Bookmark,
                                                contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                },
                                content = {
                                    Text(record.title?.ifBlank { record.url } ?: "Bookmark", style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(record.url ?: "", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (record.isReadingList) {
                                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                                            Text(stringResource(R.string.ui_reading_list), style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                        }
                                    }
                                },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { optionsRecord = record },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.MoreVert,
                                                contentDescription = "Bookmark Options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = deleteEntry, modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Delete Bookmark",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            )
                            }
                        }
                    }

                    PetalExpressiveScrollBar(
                        listState = bookmarksListState,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        dragLabelProvider = { index ->
                            filteredBookmarks.getOrNull(index)?.title?.let { extractFastScrollGlyph(it) }
                        }
                    )
                }
            }
                }
                }
            }

        com.petal.browser.ui.components.PetalSearchOverlay(
            visible = searchExpanded,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.ui_search_bookmarks),
            recents = recentSearches,
            resultCount = filteredBookmarks.size,
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

    // Bookmark Item Options Sheet (Material 3 Expressive)
        optionsRecord?.let { rec ->
            com.petal.browser.ui.containment.PetalSheet(
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
                        title = rec.title?.ifBlank { rec.url } ?: "Bookmark",
                        subtitle = rec.url ?: "",
                        leading = {
                            Box(
                                Modifier
                                    .size(80.dp)
                                    .petalSheetSurface(
                                        com.petal.browser.ui.theme.SmoothRoundedShape(24.dp),
                                        MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (rec.isReadingList) Icons.Rounded.AutoStories else Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(36.dp)
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
                            val url = rec.url ?: ""
                            optionsRecord = null
                            (context as? com.petal.browser.activity.MainActivity)?.let { act ->
                                act.addAlbum(rec.title ?: url, url, true)
                                onDismiss()
                            } ?: onOpenUrl(url)
                        }
                    )

                    PetalSheetActionRow {
                        PetalSheetPill(
                            text = "Edit",
                            icon = Icons.Rounded.Edit,
                            tone = PetalSheetTone.Secondary,
                            modifier = Modifier.weight(0.5f),
                            onClick = {
                                editingRecord = rec
                                optionsRecord = null
                            }
                        )
                        PetalSheetPill(
                            text = if (rec.isReadingList) "Unread" else "Read later",
                            icon = if (rec.isReadingList) Icons.Rounded.BookmarkRemove else Icons.Rounded.AutoStories,
                            tone = PetalSheetTone.Tertiary,
                            modifier = Modifier.weight(0.5f),
                            onClick = {
                                optionsRecord = null
                                val newReadingState = !rec.isReadingList
                                try {
                                    val action = HistoryBookmarkRepository(context)
                                    action.open(true)
                                    action.setBookmarkReadingList(rec.url, newReadingState)
                                    action.close()
                                    PetalToast.show(context, if (newReadingState) "Added to Reading List" else "Removed from Reading List")
                                } catch (_: Exception) {}
                                reloadBookmarks()
                            }
                        )
                    }

                    PetalSheetActionRow {
                        PetalSheetPill(
                            text = "Copy link",
                            icon = Icons.Rounded.ContentCopy,
                            tone = PetalSheetTone.Secondary,
                            modifier = Modifier.weight(0.55f),
                            onClick = {
                                optionsRecord = null
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", rec.url))
                                    PetalToast.show(context, "Link copied to clipboard")
                                } catch (_: Exception) {}
                            }
                        )
                        PetalSheetPill(
                            text = "Share",
                            icon = Icons.Rounded.Share,
                            tone = PetalSheetTone.TertiaryStrong,
                            modifier = Modifier.weight(0.45f),
                            onClick = {
                                optionsRecord = null
                                try {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, rec.url)
                                        putExtra(Intent.EXTRA_SUBJECT, rec.title)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Bookmark"))
                                } catch (_: Exception) {}
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Dialog: Edit Bookmark (Material 3 Expressive)
        editingRecord?.let { target ->
            var editTitle by remember(target) { mutableStateOf(target.title ?: "") }
            var editUrl by remember(target) { mutableStateOf(target.url ?: "") }

            PetalExpressiveDialog(onDismissRequest = { editingRecord = null }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Edit Bookmark",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.ui_title)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text(stringResource(R.string.ui_url)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { editingRecord = null },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val origUrl = target.url ?: ""
                            val newUrl = editUrl.trim()
                            val newTitle = editTitle.trim().ifBlank { newUrl }
                            editingRecord = null
                            if (newUrl.isNotBlank() && origUrl.isNotBlank()) {
                                try {
                                    val action = HistoryBookmarkRepository(context)
                                    action.open(true)
                                    action.updateBookmark(origUrl, newTitle, newUrl)
                                    action.close()
                                    PetalToast.show(context, "Bookmark updated")
                                } catch (_: Exception) {}
                                reloadBookmarks()
                            }
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }
        }

        // Dialog: Clear All Bookmarks (Material 3 Expressive)
        if (showClearConfirm) {
            PetalExpressiveAlertDialog(
                onDismissRequest = { showClearConfirm = false },
                icon = Icons.Rounded.DeleteSweep,
                title = stringResource(R.string.ui_clear_all_bookmarks_2),
                message = stringResource(R.string.ui_this_will_permanently_remove_all_2),
                confirmText = stringResource(R.string.ui_clear_all),
                dismissText = stringResource(R.string.ui_cancel),
                destructive = true,
                onConfirm = {
                    showClearConfirm = false
                    try {
                        val action = HistoryBookmarkRepository(context)
                        action.open(true)
                        action.clearTable(HistoryBookmarkContract.TABLE_BOOKMARK)
                        action.close()
                    } catch (_: Exception) {}
                    reloadBookmarks()
                },
                onDismiss = { showClearConfirm = false }
            )
        }

        // Dialog: Add Custom Bookmark (Material 3 Expressive)
        if (showAddDialog) {
            var newTitle by remember { mutableStateOf("") }
            var newUrl by remember { mutableStateOf("") }

            PetalExpressiveDialog(onDismissRequest = { showAddDialog = false }) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.BookmarkAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.ui_add_bookmark),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.ui_title)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text(stringResource(R.string.ui_url)) },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { showAddDialog = false },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showAddDialog = false
                            if (newUrl.isNotBlank()) {
                                val finalUrl = if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) "https://$newUrl" else newUrl
                                val finalTitle = newTitle.ifBlank { Uri.parse(finalUrl).host ?: finalUrl }
                                try {
                                    val action = HistoryBookmarkRepository(context)
                                    action.open(true)
                                    action.addBookmark(Record(finalTitle, finalUrl, 0, 0))
                                    action.close()
                                } catch (_: Exception) {}
                                reloadBookmarks()
                            }
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }
        }
    }
    }
    }

}
