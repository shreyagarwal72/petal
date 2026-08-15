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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.components.LinearRipplingWavyProgressIndicator
import kotlinx.coroutines.delay
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
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
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
    val engineTasksState by PetalDownloadEngine.downloadTasks.collectAsState()

    val downloadList by remember {
        derivedStateOf {
            engineTasksState.values.map { task ->
                DownloadItem(
                    id = task.id,
                    fileName = task.fileName,
                    fileUrl = task.url,
                    progress = if (task.status == DownloadStatus.COMPLETED) 1.0f else task.progressFraction,
                    status = when (task.status) {
                        DownloadStatus.COMPLETED -> DownloadManager.STATUS_SUCCESSFUL
                        DownloadStatus.RUNNING -> DownloadManager.STATUS_RUNNING
                        DownloadStatus.PAUSED -> DownloadManager.STATUS_PAUSED
                        DownloadStatus.PENDING -> DownloadManager.STATUS_PENDING
                        else -> DownloadManager.STATUS_FAILED
                    },
                    bytesDownloaded = task.bytesDownloaded,
                    totalSize = task.totalBytes,
                    speedBytesPerSec = task.speedBps,
                    localUri = task.destinationPath,
                    timestampMs = task.timestampMs
                )
            }.sortedByDescending { it.timestampMs }
        }
    }

    val groupedDownloads = remember(downloadList) {
        downloadList.groupBy { item -> formatDateHeader(item.timestampMs) }
    }

    Scaffold(
        topBar = {
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
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (downloadList.isEmpty()) {
            DownloadsEmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp)
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
                        DownloadRowItem(
                            item = item,
                            onOpenFile = { openDownloadedFile(context, item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            val blobColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawPath(path = createBlobPath(size.width), color = blobColor)
            }
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "You'll find your downloads here",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "You can save files to view offline or share in other apps",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun createBlobPath(size: Float): Path {
    val points = 7
    val radiusVariance = floatArrayOf(1f, 0.86f, 1.04f, 0.9f, 1f, 0.88f, 0.96f)
    val baseRadius = size / 2.15f
    val center = size / 2f

    val vertices = (0 until points).map { i ->
        val angle = (2 * Math.PI / points) * i - Math.PI / 2
        val r = baseRadius * radiusVariance[i % radiusVariance.size]
        Offset(
            x = (center + r * cos(angle)).toFloat(),
            y = (center + r * sin(angle)).toFloat()
        )
    }

    val midPoints = (0 until points).map { i ->
        val p1 = vertices[i]
        val p2 = vertices[(i + 1) % points]
        Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    }

    return Path().apply {
        moveTo(midPoints[0].x, midPoints[0].y)
        for (i in 0 until points) {
            val vertex = vertices[(i + 1) % points]
            val mid = midPoints[(i + 1) % points]
            quadraticBezierTo(vertex.x, vertex.y, mid.x, mid.y)
        }
        close()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRowItem(item: DownloadItem, onOpenFile: () -> Unit) {
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
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.status == DownloadManager.STATUS_SUCCESSFUL,
                onClick = onOpenFile
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading circular avatar/icon container using surfaceContainerHighest color
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getFileTypeIcon(item.fileName),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

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
                                deleteDownloadedFile(context, item)
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
            }
        }
    }
}

private fun getDownloadItems(
    context: Context,
    prevBytesMap: Map<Long, Long>,
    elapsedTimeMs: Long
): List<DownloadItem> {
    val list = mutableListOf<DownloadItem>()
    try {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        val cursor = dm.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val soFarCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val timestampCol = cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)

            do {
                val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Downloaded File" else "Downloaded File"
                val uri = if (uriCol >= 0) cursor.getString(uriCol) ?: "" else ""
                val status = if (statusCol >= 0) cursor.getInt(statusCol) else 0
                val soFar = if (soFarCol >= 0) cursor.getLong(soFarCol) else 0L
                val total = if (totalCol >= 0) cursor.getLong(totalCol) else 0L
                val localUri = if (localUriCol >= 0) cursor.getString(localUriCol) else null
                val timestamp = if (timestampCol >= 0) cursor.getLong(timestampCol) else System.currentTimeMillis()

                val progress = if (total > 0) (soFar.toFloat() / total.toFloat()) else null
                val prevSoFar = prevBytesMap[id] ?: soFar
                val bytesDiff = (soFar - prevSoFar).coerceAtLeast(0L)
                val speed = if (elapsedTimeMs > 0 && bytesDiff > 0) (bytesDiff * 1000L) / elapsedTimeMs else 0L
                val remainingBytes = (total - soFar).coerceAtLeast(0L)
                val eta = if (speed > 0) remainingBytes / speed else 0L

                list.add(DownloadItem(id, title, uri, progress, status, soFar, total, speed, eta, localUri, timestamp))
            } while (cursor.moveToNext())
            cursor.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list.sortedByDescending { it.timestampMs }
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
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(item.id)
        if (item.localUri != null) {
            val uri = Uri.parse(item.localUri)
            val file = java.io.File(uri.path ?: "")
            if (file.exists()) {
                file.delete()
            }
        }
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
        val uri = if (item.localUri != null) Uri.parse(item.localUri) else null
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = context.contentResolver.getType(uri) ?: "*/*"
            } else {
                putExtra(Intent.EXTRA_TEXT, item.fileUrl)
                type = "text/plain"
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share file")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
