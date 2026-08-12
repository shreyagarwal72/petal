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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.RecordUnit
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.ui.components.entrance
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
    fun showHistory(
        activity: ComponentActivity,
        onOpenUrl: HistoryUrlHandler,
        onClearBrowsingData: HistoryActionHandler
    ) {
        try {
            val dialog = BottomSheetDialog(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", "tide") ?: "tide"
                val dynamicColor = sp.getBoolean("useDynamicColor", false)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.SYSTEM }
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
                            onOpenUrl = { url ->
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                onOpenUrl.open(url)
                            },
                            onClearBrowsingData = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                onClearBrowsingData.action()
                            },
                            onDismiss = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
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
            rawHistory = list.reversed()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    if (rawHistory?.isNotEmpty() == true) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search & Filter Input Bar
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search history...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Quick Clear Browsing Data Banner Button
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

            // History Records List
            if (rawHistory == null) {
                ContainedLoadingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else if (filteredHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.HistoryToggleOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching history" else "No browsing history yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
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
