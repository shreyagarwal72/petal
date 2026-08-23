/*
 * PetalDownloadManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Native Inbuilt Download Manager UI for Petal Browser featuring Chrome-style
 * grouped downloads, sticky date headers, file type avatars, 2-line title/subtitle
 * columns, overflow dropdown menus, and Material 3 Expressive UI components.
 */

package com.petal.browser.compose.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.components.LinearRipplingWavyProgressIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

data class DownloadItem(
    val id: Long,
    val fileName: String,
    val fileUrl: String,
    val progress: Float?,
    val status: Int,
    val bytesDownloaded: Long,
    val totalSize: Long,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val localUri: String?,
    val timestampMs: Long = System.currentTimeMillis()
)

object PetalDownloadBridge {
    @JvmStatic
    fun createDownloadView(activity: androidx.activity.ComponentActivity, onBackPress: () -> Unit): ComposeView {
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
                    PetalDownloadManagerScreen(onBackPress = onBackPress)
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    return "${formatBytes(bytesPerSec)}/s"
}

fun formatEta(seconds: Long): String {
    if (seconds <= 0) return "--"
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

fun extractDomain(url: String): String {
    if (url.isEmpty()) return ""
    return try {
        val uri = Uri.parse(url)
        val host = uri.host
        if (!host.isNullOrEmpty()) {
            if (host.startsWith("www.")) host.substring(4) else host
        } else url
    } catch (e: Exception) {
        url
    }
}

fun formatDateHeader(timestampMs: Long): String {
    if (timestampMs <= 0) return "Downloads"
    val calItem = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val calToday = Calendar.getInstance()
    val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    val dateFormatFull = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val formattedDate = dateFormatFull.format(calItem.time)

    return when {
        isSameDay(calItem, calToday) -> "Today - $formattedDate"
        isSameDay(calItem, calYesterday) -> "Yesterday"
        else -> formattedDate
    }
}

private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
           c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

fun getFileTypeIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp" -> Icons.Rounded.Image
        "mp4", "mkv", "webm", "avi", "mov", "flv" -> Icons.Rounded.Movie
        "mp3", "wav", "aac", "flac", "ogg", "m4a" -> Icons.Rounded.MusicNote
        "apk" -> Icons.Rounded.Android
        "pdf" -> Icons.Rounded.PictureAsPdf
        "doc", "docx", "txt", "rtf", "odt" -> Icons.Rounded.Description
        "zip", "tar", "gz", "rar", "7z" -> Icons.Rounded.FolderZip
        else -> Icons.Rounded.InsertDriveFile
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PetalDownloadManagerScreen(onBackPress: () -> Unit = {}) {
    val context = LocalContext.current

    // Downloads now flow through Fetch2 (see BrowserUnit.download), which is the only
    // engine here that actually supports pause/resume. PetalFetchDownloadBridge listens
    // to it live, so this list updates instantly on progress/pause/resume/completion
    // instead of being polled from the system DownloadManager (which never reflected a
    // pause and is why the feature looked broken).
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        PetalFetchDownloadBridge.ensureInitialized(context)
        PetalFetchDownloadBridge.refresh(context)
        kotlinx.coroutines.delay(1000L)
        isLoading = false
    }
    val rawDownloadList by PetalFetchDownloadBridge.downloadItems.collectAsState()
    var pendingDeletedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val downloadList = remember(rawDownloadList, pendingDeletedIds) {
        rawDownloadList.filter { !pendingDeletedIds.contains(it.id) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun performStagedDelete(items: List<DownloadItem>) {
        if (items.isEmpty()) return
        val targetIds = items.map { it.id }.toSet()
        pendingDeletedIds = pendingDeletedIds + targetIds

        val message = if (items.size == 1) {
            "Deleted ${items.first().fileName}"
        } else {
            "Deleted ${items.size} items"
        }

        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Restore optimistic deletion
                pendingDeletedIds = pendingDeletedIds - targetIds
            } else {
                // Snackbar dismissed / timed out -> Commit permanent deletion from disk & database
                PetalFetchDownloadBridge.deleteDownloads(context, items)
                pendingDeletedIds = pendingDeletedIds - targetIds
            }
        }
    }

    val groupedDownloads = remember(downloadList) {
        downloadList.groupBy { item -> formatDateHeader(item.timestampMs) }
    }

    // Predictive back gesture (swipe-to-go-back) replaces the plain BackHandler so this
    // screen participates in the shared shrink/dim/blur animation across the app.

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    fun toggleSelectAll() {
        if (selectedIds.size == downloadList.size) {
            selectedIds = emptySet()
        } else {
            selectedIds = downloadList.map { it.id }.toSet()
        }
    }

    fun toggleSelection(id: Long) {
        selectedIds = if (selectedIds.contains(id)) {
            selectedIds - id
        } else {
            selectedIds + id
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBackPress,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper {
    Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        actionColor = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            M3ExpressiveVariableBackground(pageSeed = "downloads_page")

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header inside Surface covering status bar seamlessly!
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Text(
                                "${selectedIds.size} Selected",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel Selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { toggleSelectAll() }) {
                                Icon(
                                    if (selectedIds.size == downloadList.size) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = "Select All"
                                )
                            }
                            IconButton(onClick = {
                                val itemsToShare = downloadList.filter { selectedIds.contains(it.id) }
                                shareMultipleFiles(context, itemsToShare)
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share Selected")
                            }
                            IconButton(onClick = {
                                val itemsToDelete = downloadList.filter { selectedIds.contains(it.id) }
                                performStagedDelete(itemsToDelete)
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                "Downloads",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackPress) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { PetalFetchDownloadBridge.refresh(context) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (isLoading) {
                com.petal.browser.compose.composable.ContainedLoadingIndicator(
                    modifier = Modifier.fillMaxSize()
                )
            } else if (downloadList.isEmpty()) {
                DownloadsEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 24.dp)
                ) {
                    groupedDownloads.forEach { (dateHeader, items) ->
                        stickyHeader(key = dateHeader) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                                )
                            }
                        }

                        items(items, key = { it.id }) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            DownloadRowItem(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onToggleSelect = { toggleSelection(item.id) },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        toggleSelection(item.id)
                                    }
                                },
                                onDeleteItem = { performStagedDelete(listOf(item)) },
                                onOpenFile = { openDownloadedFile(context, item) }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DownloadRowItem(
    item: DownloadItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteItem: () -> Unit,
    onOpenFile: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(item.fileName) }
    val context = LocalContext.current

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    if (renameInput.isNotBlank() && renameInput != item.fileName) {
                        renameDownloadedFile(context, item, renameInput.trim())
                    }
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                        onOpenFile()
                    }
                },
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                // Leading avatar: a Play Store-style circular progress ring with a Chrome-style
                // pause/resume glyph in the center while active, falling back to the plain file
                // type icon once the download is finished/idle.
                DownloadProgressRing(
                    item = item,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    onTogglePauseResume = {
                        when (item.status) {
                            DownloadManager.STATUS_RUNNING -> PetalLiveAlertManager.pauseDownload(context, item.id)
                            DownloadManager.STATUS_PAUSED -> PetalLiveAlertManager.resumeDownload(context, item.id)
                            else -> {}
                        }
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Two-line text column: Title & Subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val domain = remember(item.fileUrl) { extractDomain(item.fileUrl) }
                    val formattedSize = remember(item.totalSize, item.bytesDownloaded) {
                        if (item.totalSize > 0) formatBytes(item.totalSize) else formatBytes(item.bytesDownloaded)
                    }

                    val subtitleText = when {
                        item.status == DownloadManager.STATUS_RUNNING -> {
                            val percentStr = if (item.progress != null) "${(item.progress * 100).toInt()}%" else ""
                            if (domain.isNotEmpty()) {
                                "${formatBytes(item.bytesDownloaded)} of $formattedSize • $percentStr • $domain"
                            } else {
                                "${formatBytes(item.bytesDownloaded)} of $formattedSize • $percentStr"
                            }
                        }
                        item.status == DownloadManager.STATUS_FAILED -> {
                            if (domain.isNotEmpty()) "Failed • $formattedSize • $domain" else "Failed • $formattedSize"
                        }
                        item.status == DownloadManager.STATUS_PAUSED -> {
                            if (domain.isNotEmpty()) "Paused • $formattedSize • $domain" else "Paused • $formattedSize"
                        }
                        else -> {
                            if (domain.isNotEmpty()) "$formattedSize • $domain" else formattedSize
                        }
                    }

                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Far right vertical 3-dot overflow menu button
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (item.status == DownloadManager.STATUS_RUNNING) {
                            DropdownMenuItem(
                                text = { Text("Pause") },
                                leadingIcon = { Icon(Icons.Rounded.Pause, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    PetalLiveAlertManager.pauseDownload(context, item.id)
                                }
                            )
                        }
                        if (item.status == DownloadManager.STATUS_PAUSED) {
                            DropdownMenuItem(
                                text = { Text("Resume") },
                                leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    PetalLiveAlertManager.resumeDownload(context, item.id)
                                }
                            )
                        }
                        if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFile()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    shareDownloadedFile(context, item)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showRenameDialog = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Copy Link") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                copyDownloadLink(context, item.fileUrl)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDeleteItem()
                            }
                        )
                    }
                }
            }

            if (item.status == DownloadManager.STATUS_RUNNING) {
                val animatedProgress by animateFloatAsState(
                    targetValue = item.progress ?: 0f,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "Progress"
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearRipplingWavyProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (item.status == DownloadManager.STATUS_PAUSED) {
                // A frozen, muted bar (no wave motion) makes "paused" visually distinct from
                // an actively downloading file, matching how Chrome dims a paused download.
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = item.progress ?: 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                )
            }
        }
    }
}

/**
 * Chrome/Play Store-style leading control: a circular ring that sweeps around the icon to
 * show progress, with the icon itself doubling as a tappable pause/resume/status glyph.
 * - Running: filled progress arc (or an indeterminate spinning arc while size is unknown),
 *   Pause glyph in the center. Tap to pause.
 * - Paused: a frozen ring at the current progress, Play glyph in the center. Tap to resume.
 * - Failed: a plain error glyph, no ring.
 * - Completed/other: falls back to the original file-type icon, no ring.
 */
@Composable
private fun DownloadProgressRing(
    item: DownloadItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onTogglePauseResume: () -> Unit
) {
    val isRunning = item.status == DownloadManager.STATUS_RUNNING
    val isPaused = item.status == DownloadManager.STATUS_PAUSED
    val isPending = item.status == DownloadManager.STATUS_PENDING
    val isFailed = item.status == DownloadManager.STATUS_FAILED
    val showRing = (isRunning || isPaused || isPending) && !isSelected

    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)

    val animatedProgress by animateFloatAsState(
        targetValue = item.progress ?: 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "RingProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "RingSpin")
    val indeterminateRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing)
        ),
        label = "RingRotation"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing)
        ),
        label = "RingWavePhase"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "RingPulse"
    )

    val avatarShape = remember(item.id) {
        val shapes = listOf(
            com.petal.browser.ui.components.ScallopedShape(lobes = 8, depth = 0.16f),
            com.petal.browser.compose.home.CloverShape,
            com.petal.browser.compose.home.StarburstShape,
            com.petal.browser.compose.home.ArchShape,
            com.petal.browser.compose.home.FlowerShape
        )
        shapes[(item.id.toInt() and 0x7FFFFFFF) % shapes.size]
    }

    Surface(
        shape = if (showRing) CircleShape else avatarShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .size(44.dp)
            .then(
                if (showRing && !isSelectionMode) {
                    Modifier.clickable(
                        onClickLabel = if (isRunning) "Pause download" else "Resume download",
                        onClick = onTogglePauseResume
                    )
                } else Modifier
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (showRing) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val waveAmplitudePx = 2.0.dp.toPx()
                    val strokeWidthPx = waveAmplitudePx * 2f + 2.6.dp.toPx()
                    val diameter = size.minDimension - strokeWidthPx
                    val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Track ring
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                    )

                    if (item.progress != null) {
                        val alpha = if (isPaused) pulseAlpha else 1f
                        val waveCount = 10
                        val sweepDeg = 360f * animatedProgress
                        val startAngleDeg = -90f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = arcSize.width / 2f
                        val path = Path()
                        var first = true
                        var t = 0f
                        while (t <= sweepDeg) {
                            val angleRad = Math.toRadians((startAngleDeg + t).toDouble()).toFloat()
                            val waveOffset = if (isPaused) 0f else sin((t / 360f) * waveCount * 2f * Math.PI.toFloat() + wavePhase) * waveAmplitudePx
                            val r = baseRadius + waveOffset
                            val x = center.x + r * cos(angleRad)
                            val y = center.y + r * sin(angleRad)
                            if (first) {
                                path.moveTo(x, y)
                                first = false
                            } else {
                                path.lineTo(x, y)
                            }
                            t += 1f
                        }
                        drawPath(
                            path = path,
                            color = ringColor.copy(alpha = ringColor.alpha * alpha),
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                        )

                    } else {

                        // Unknown total size: an indeterminate spinning arc instead of a sweep.
                        rotate(indeterminateRotation) {
                            drawArc(
                                color = ringColor,
                                startAngle = 0f,
                                sweepAngle = 100f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            val glyph: ImageVector = when {
                isSelected -> Icons.Rounded.Check
                isRunning -> Icons.Rounded.Pause
                isPaused -> Icons.Rounded.PlayArrow
                isPending -> Icons.Rounded.Schedule
                isFailed -> Icons.Rounded.ErrorOutline
                else -> getFileTypeIcon(item.fileName)
            }
            Icon(
                imageVector = glyph,
                contentDescription = when {
                    isRunning -> "Pause download"
                    isPaused -> "Resume download"
                    else -> null
                },
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (showRing) 20.dp else 24.dp)
            )
        }
    }
}

private fun openDownloadedFile(context: Context, item: DownloadItem) {
    try {
        var contentUri: Uri? = null
        var mimeType: String? = null

        val localUriString = item.localUri
        if (!localUriString.isNullOrEmpty()) {
            val rawUri = Uri.parse(localUriString)
            if (rawUri.scheme == "file" || rawUri.scheme == null) {
                val filePath = rawUri.path ?: localUriString.removePrefix("file://")
                val file = java.io.File(filePath)
                if (file.exists()) {
                    try {
                        contentUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            file
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                contentUri = rawUri
            }
        }

        if (contentUri == null) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                contentUri = dm.getUriForDownloadedFile(item.id)
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (contentUri != null) {
            val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(item.fileName.ifEmpty { contentUri.toString() })
            if (!extension.isNullOrEmpty()) {
                val detectedType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                if (!detectedType.isNullOrEmpty()) {
                    mimeType = detectedType
                }
            }
            if (mimeType.isNullOrEmpty()) mimeType = "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open file with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } else {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.openDownloadedFile(item.id)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.openDownloadedFile(item.id)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

private fun deleteDownloadedFile(context: Context, item: DownloadItem) {
    try {
        PetalFetchDownloadBridge.deleteDownload(context, item)
        android.widget.Toast.makeText(context, "Deleted ${item.fileName}", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun renameDownloadedFile(context: Context, item: DownloadItem, newName: String) {
    try {
        if (item.localUri != null) {
            val uri = Uri.parse(item.localUri)
            val oldFile = java.io.File(uri.path ?: "")
            if (oldFile.exists()) {
                val newFile = java.io.File(oldFile.parent, newName)
                if (oldFile.renameTo(newFile)) {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null, null)
                    android.widget.Toast.makeText(context, "Renamed to $newName", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun copyDownloadLink(context: Context, url: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Download Link", url)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "Link copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareDownloadedFile(context: Context, item: DownloadItem) {
    try {
        var contentUri: Uri? = null
        val localUriString = item.localUri
        if (!localUriString.isNullOrEmpty()) {
            val rawUri = Uri.parse(localUriString)
            if (rawUri.scheme == "file" || rawUri.scheme == null) {
                val filePath = rawUri.path ?: localUriString.removePrefix("file://")
                val file = java.io.File(filePath)
                if (file.exists()) {
                    try {
                        contentUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            file
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                contentUri = rawUri
            }
        }
        if (contentUri == null) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                contentUri = dm.getUriForDownloadedFile(item.id)
            } catch (e: Exception) { e.printStackTrace() }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            if (contentUri != null) {
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = context.contentResolver.getType(contentUri) ?: "*/*"
            } else {
                putExtra(Intent.EXTRA_TEXT, item.fileUrl)
                type = "text/plain"
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share file").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareMultipleFiles(context: Context, items: List<DownloadItem>) {
    if (items.isEmpty()) return
    if (items.size == 1) {
        shareDownloadedFile(context, items.first())
        return
    }
    try {
        val uris = ArrayList<Uri>()
        items.forEach { item ->
            var contentUri: Uri? = null
            val localUriString = item.localUri
            if (!localUriString.isNullOrEmpty()) {
                val rawUri = Uri.parse(localUriString)
                if (rawUri.scheme == "file" || rawUri.scheme == null) {
                    val filePath = rawUri.path ?: localUriString.removePrefix("file://")
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        try {
                            contentUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                file
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    contentUri = rawUri
                }
            }
            if (contentUri == null) {
                try {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    contentUri = dm.getUriForDownloadedFile(item.id)
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (contentUri != null) {
                uris.add(contentUri)
            }
        }
        if (uris.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share ${items.size} files").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } else {
            val linksText = items.joinToString("\n") { it.fileUrl }
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, linksText)
                type = "text/plain"
            }
            val chooser = Intent.createChooser(intent, "Share links").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun deleteMultipleFiles(context: Context, items: List<DownloadItem>) {
    if (items.isEmpty()) return
    try {
        PetalFetchDownloadBridge.deleteDownloads(context, items)
        android.widget.Toast.makeText(context, "Deleted ${items.size} items", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
private fun DownloadsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Downloads Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Files you download will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

