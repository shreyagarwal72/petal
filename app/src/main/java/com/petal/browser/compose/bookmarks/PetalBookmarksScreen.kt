/*
 * PetalBookmarksScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome Android-inspired Material 3 Expressive Bookmarks Page for Petal Browser.
 * Features live search/filter, individual bookmark deletion, bookmark creation,
 * share actions, and 60fps smooth animations.
 */

package com.petal.browser.compose.bookmarks
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
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.PetalGroupListRow
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
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
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

    val hasSubOverlay = editingRecord != null || optionsRecord != null || showAddDialog || showClearConfirm || showReadingListOnly || searchQuery.isNotEmpty()
    val handleDismiss: () -> Unit = {
        when {
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
            M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = "bookmarks_page"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                ExpressiveHeader(
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
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
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
                    }
                )

                // Search Filter Bar
                val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
                val searchShape = RoundedCornerShape(24.dp)
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.ui_search_bookmarks)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                    shape = searchShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .liquidGlassChrome(shape = searchShape, enabled = isGlass, sheenIntensity = 0.45f), glass = false
                )

                Spacer(Modifier.height(8.dp))

                // Reading List filter chip row - Material 3 Expressive FilterChip with a
                // springy scale-in, matching the bouncy selection feel used elsewhere in
                // Petal's Compose surfaces rather than a static Material 3 toggle.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val readingListCount = remember(rawBookmarks) {
                        rawBookmarks?.count { it.isReadingList } ?: 0
                    }
                    val chipScale by animateFloatAsState(
                        targetValue = if (showReadingListOnly) 1f else 0.96f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "readingListChipScale"
                    )
                    FilterChip(
                        selected = showReadingListOnly,
                        onClick = { showReadingListOnly = !showReadingListOnly },
                        label = { Text(if (readingListCount > 0) "Reading List ($readingListCount)" else "Reading List") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null,
                        modifier = Modifier.graphicsLayer(scaleX = chipScale, scaleY = chipScale)
                    )
                }

                Spacer(Modifier.height(4.dp))

                if (rawBookmarks == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        com.petal.browser.compose.composable.PetalContainedLoadingIndicator(size = 48.dp)
                    }
                } else if (filteredBookmarks.isEmpty()) {
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                        itemsIndexed(filteredBookmarks, key = { index, record -> "${record.url}_${record.time}_${record.iconColor}_$index" }) { index, record ->
                            val faviconUrl = remember(record.url) { getFaviconUrl(record.url) }
                            var isFaviconError by remember(record.url) { mutableStateOf(false) }
                            PetalGroupListRow(
                                position = petalGroupPositionFor(index, filteredBookmarks.size),
                                onClick = { onOpenUrl(record.url) },
                                modifier = Modifier.animateItem().entrance(index = index, playKey = "${record.url}_${record.iconColor}"),
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
                                        IconButton(
                                            onClick = {
                                                val deletedRecord = record
                                                try {
                                                    val action = HistoryBookmarkRepository(context)
                                                    action.open(true)
                                                    action.deleteURL(deletedRecord.url, HistoryBookmarkContract.TABLE_BOOKMARK)
                                                    action.close()
                                                } catch (_: Exception) {}
                                                reloadBookmarks()

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
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
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

    // Bookmark Item Options Sheet (Material 3 Expressive)
        optionsRecord?.let { rec ->
            com.petal.browser.ui.containment.PetalSheet(
                onDismissRequest = { optionsRecord = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (rec.isReadingList) Icons.Rounded.AutoStories else Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rec.title?.ifBlank { rec.url } ?: "Bookmark",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = rec.url ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 5) { index, position ->
                        when (index) {
                            0 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.OpenInNew,
                                    title = "Open in New Tab",
                                    position = position,
                                    onClick = {
                                        val url = rec.url ?: ""
                                        optionsRecord = null
                                        (context as? com.petal.browser.activity.MainActivity)?.let { act ->
                                            act.addAlbum(rec.title ?: url, url, true)
                                            onDismiss()
                                        } ?: onOpenUrl(url)
                                    }
                                )
                            }
                            1 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.Edit,
                                    title = "Edit Bookmark",
                                    position = position,
                                    onClick = {
                                        editingRecord = rec
                                        optionsRecord = null
                                    }
                                )
                            }
                            2 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = if (rec.isReadingList) Icons.Rounded.BookmarkRemove else Icons.Rounded.AutoStories,
                                    title = if (rec.isReadingList) "Remove from Reading List" else "Add to Reading List",
                                    position = position,
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
                            3 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.ContentCopy,
                                    title = "Copy Link",
                                    position = position,
                                    onClick = {
                                        optionsRecord = null
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", rec.url))
                                            PetalToast.show(context, "Link copied to clipboard")
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                            4 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.Share,
                                    title = "Share Link",
                                    position = position,
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
                        }
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
