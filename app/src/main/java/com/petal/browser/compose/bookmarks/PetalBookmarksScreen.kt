/*
 * PetalBookmarksScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome Android-inspired Material 3 Expressive Bookmarks Page for Petal Browser.
 * Features live search/filter, individual bookmark deletion, bookmark creation,
 * share actions, and 60fps smooth animations.
 */

package com.petal.browser.compose.bookmarks

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.RecordUnit
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.petalGroupPositionFor
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
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showReadingListOnly by remember { mutableStateOf(false) }

    // Load bookmarks from SQLite database asynchronously
    var rawBookmarks by remember { mutableStateOf<List<Record>?>(null) }
    
    val reloadBookmarks: () -> Unit = {
        try {
            val action = RecordAction(context)
            action.open(false)
            val list = action.listBookmark(context, false, 0)
            action.close()
            rawBookmarks = list.filter { record ->
                val url = record.url?.trim() ?: ""
                url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true)
            }
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
                    onBack = onDismiss,
                    actions = {
                        HeaderActionIcon(
                            icon = Icons.Rounded.Add,
                            contentDescription = "Add Bookmark",
                            onClick = { showAddDialog = true }
                        )
                        if (!rawBookmarks.isNullOrEmpty()) {
                            Box {
                                HeaderActionIcon(
                                    icon = Icons.Rounded.MoreVert,
                                    contentDescription = "More Options",
                                    onClick = { overflowMenuExpanded = true }
                                )
                                com.petal.browser.ui.containment.PetalPopupMenu(
                                    expanded = overflowMenuExpanded,
                                    onDismissRequest = { overflowMenuExpanded = false }
                                ) {
                                    com.petal.browser.ui.containment.PetalPopupMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.ui_clear_all_bookmarks),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.DeleteSweep,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
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
                OutlinedTextField(
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
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
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
                        modifier = Modifier.graphicsLayer(scaleX = chipScale, scaleY = chipScale)
                    )
                }

                Spacer(Modifier.height(4.dp))

                if (rawBookmarks == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (filteredBookmarks.isEmpty()) {
                    com.petal.browser.ui.components.EmptyStateBlob(
                        illustrationType = com.petal.browser.ui.components.EmptyStateIllustrationType.BOOKMARKS,
                        title = if (searchQuery.isEmpty()) "No Bookmarks Saved Yet" else "No Matching Bookmarks",
                        description = if (searchQuery.isEmpty()) "Tap the star icon on web pages to save them" else "Try searching with a different URL or keyword"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(filteredBookmarks, key = { _, record -> "${record.url}_${record.time}" }) { index, record ->
                            val faviconUrl = remember(record.url) { getFaviconUrl(record.url) }
                            var isFaviconError by remember(record.url) { mutableStateOf(false) }
                            PetalGroupListRow(
                                position = petalGroupPositionFor(index, filteredBookmarks.size),
                                onClick = { onOpenUrl(record.url) },
                                modifier = Modifier.animateItem().entrance(index = index, playKey = "${record.url}_${record.time}"),
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
                                            Icon(if (record.isReadingList) Icons.Filled.AutoStories else Icons.Filled.Bookmark,
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
                                        IconButton(onClick = {
                                    val deletedRecord = record
                                    try {
                                        val action = RecordAction(context)
                                        action.open(true)
                                        action.deleteURL(deletedRecord.url, RecordUnit.TABLE_BOOKMARK)
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
                                                val action = RecordAction(context)
                                                action.open(true)
                                                action.addBookmark(deletedRecord)
                                                action.close()
                                            } catch (_: Exception) {}
                                            reloadBookmarks()
                                        }
                                    }
                                        }) { Icon(Icons.Filled.Close, "Delete Bookmark", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }
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
                        val action = RecordAction(context)
                        action.open(true)
                        action.clearTable(RecordUnit.TABLE_BOOKMARK)
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
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.ui_title)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text(stringResource(R.string.ui_url)) },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showAddDialog = false },
                        shape = RoundedCornerShape(50),
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
                                    val action = RecordAction(context)
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
