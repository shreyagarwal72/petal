/*
 * PetalHistoryScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome Android-inspired Material 3 Expressive History Page for Petal Browser.
 * Features live search/filter, grouped dates, individual entry deletion,
 * clear browsing data action, and 60fps smooth animations.
 */

package com.petal.browser.compose.history

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
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
import coil.compose.SubcomposeAsyncImage
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.RecordUnit
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun interface HistoryUrlHandler {
    fun open(url: String)
}

fun interface HistoryActionHandler {
    fun action()
}

object PetalHistoryBridge {
    @JvmStatic
    fun createHistoryView(
        activity: ComponentActivity,
        onOpenUrl: HistoryUrlHandler,
        onClearBrowsingData: HistoryActionHandler,
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
                    PetalHistoryScreen(
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
    onOpenUrl: (String) -> Unit,
    onClearBrowsingData: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Load history records from SQLite database asynchronously
    var rawHistory by remember { mutableStateOf<List<Record>?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            val action = RecordAction(context)
            action.open(false)
            val list = action.listHistory(context)
            action.close()
            rawHistory = list.reversed().filter { record ->
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently remove all visited web pages from your history records.") },
            confirmButton = {
                Button(
                    onClick = {
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

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onDismiss,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    placeholder = { Text("Search history...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item(key = "clear_banner") {
                                Surface(
                                    onClick = onClearBrowsingData,
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.CleaningServices,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = "Clear browsing data...",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Icon(
                                            Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (filteredHistory.isEmpty()) {
                                item(key = "empty_state") {
                                    com.petal.browser.ui.components.EmptyStateBlob(
                                        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.HistoryToggleOff),
                                        title = if (searchQuery.isNotEmpty()) "No matching history" else "No browsing history yet"
                                    )
                                }
                            } else {
                                itemsIndexed(filteredHistory, key = { idx, item -> "${item.url}_$idx" }) { index, record ->
                                    HistoryCardItem(
                                        record = record,
                                        index = index,
                                        onSelect = { record.url?.let(onOpenUrl) },
                                        onDelete = {
                                            try {
                                                val action = RecordAction(context)
                                                action.open(true)
                                                action.deleteURL(record.url, RecordUnit.TABLE_HISTORY)
                                                action.close()
                                                rawHistory = rawHistory?.filter { it.url != record.url } ?: emptyList()
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
    }
}
}

@Composable
private fun HistoryCardItem(
    record: Record,
    index: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatted = remember(record.time) {
        if (record.time > 0L) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(record.time))
        } else ""
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(scaleDown = 0.95f, onClick = onSelect)
            .entrance(index = index)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val faviconUrl = remember(record.url) {
                val domain = record.domain?.takeIf { it.isNotBlank() }
                    ?: try { java.net.URI(record.url ?: "").host } catch (e: Exception) { null }
                if (!domain.isNullOrEmpty()) "https://www.google.com/s2/favicons?domain=$domain&sz=32" else null
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (faviconUrl != null) {
                        SubcomposeAsyncImage(
                            model = faviconUrl,
                            contentDescription = "Website Icon",
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            loading = {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            error = {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title?.takeIf { it.isNotBlank() } ?: record.domain ?: "Web Page",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.url ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (timeFormatted.isNotEmpty()) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
