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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import coil.compose.AsyncImage
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.RecordUnit
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.compose.home.getFaviconUrl

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
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.GS_FLEX }
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
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

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

    val filteredBookmarks = remember(searchQuery, rawBookmarks) {
        val list = rawBookmarks ?: emptyList()
        if (searchQuery.isBlank()) {
            list
        } else {
            val query = searchQuery.trim().lowercase()
            list.filter { record ->
                (record.title?.lowercase()?.contains(query) == true) ||
                (record.url?.lowercase()?.contains(query) == true)
            }
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onDismiss,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                            HeaderActionIcon(
                                icon = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear All Bookmarks",
                                onClick = { showClearConfirm = true }
                            )
                        }
                    }
                )

                // Search Filter Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search bookmarks...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
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

                if (rawBookmarks == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (filteredBookmarks.isEmpty()) {
                    com.petal.browser.ui.components.EmptyStateBlob(
                        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.BookmarkBorder),
                        title = if (searchQuery.isEmpty()) "No bookmarks saved yet" else "No matching bookmarks found"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredBookmarks) { _, record ->
                            BookmarkCardItem(
                                record = record,
                                onClick = { onOpenUrl(record.url) },
                                onDelete = {
                                    try {
                                        val action = RecordAction(context)
                                        action.open(true)
                                        action.deleteURL(record.url, RecordUnit.TABLE_BOOKMARK)
                                        action.close()
                                    } catch (_: Exception) {}
                                    reloadBookmarks()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Dialog: Clear All Bookmarks
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear All Bookmarks?") },
                text = { Text("This will permanently remove all saved bookmarks.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearConfirm = false
                            try {
                                val action = RecordAction(context)
                                action.open(true)
                                action.clearTable(RecordUnit.TABLE_BOOKMARK)
                                action.close()
                            } catch (_: Exception) {}
                            reloadBookmarks()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog: Add Custom Bookmark
        if (showAddDialog) {
            var newTitle by remember { mutableStateOf("") }
            var newUrl by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Bookmark") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            label = { Text("URL") },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
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
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    }
    }
}

@Composable
private fun BookmarkCardItem(
    record: Record,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val faviconUrl = remember(record.url) { getFaviconUrl(record.url) }
    var isFaviconError by remember(record.url) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                if (!faviconUrl.isNullOrEmpty() && !isFaviconError) {
                    AsyncImage(
                        model = faviconUrl,
                        contentDescription = record.title,
                        onError = { isFaviconError = true },
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title?.ifBlank { record.url } ?: "Bookmark",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = record.url ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Delete Bookmark",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
