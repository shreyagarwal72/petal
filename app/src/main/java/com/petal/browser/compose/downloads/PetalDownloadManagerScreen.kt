/*
 * PetalDownloadManager.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Native Inbuilt Download Manager UI for Petal Browser featuring real-time
 * network velocity (speed), ETA calculation, file opening, pause/resume,
 * and Material 3 Expressive UI components.
 */

package com.petal.browser.compose.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.delay
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
    val localUri: String?
)

object PetalDownloadBridge {
    @JvmStatic
    fun createDownloadView(activity: androidx.activity.ComponentActivity, onBackPress: () -> Unit): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PetalExpressiveTheme {
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
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalDownloadManagerScreen(onBackPress: () -> Unit = {}) {
    val context = LocalContext.current
    var prevBytesMap by remember { mutableStateOf(mapOf<Long, Long>()) }
    var lastCheckTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var downloadList by remember { mutableStateOf(getDownloadItems(context, prevBytesMap, 1000L)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            val currentTime = System.currentTimeMillis()
            val elapsedTime = (currentTime - lastCheckTime).coerceAtLeast(100L)
            val newItems = getDownloadItems(context, prevBytesMap, elapsedTime)
            prevBytesMap = newItems.associate { it.id to it.bytesDownloaded }
            lastCheckTime = currentTime
            downloadList = newItems
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Inbuilt Download Manager",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        downloadList = getDownloadItems(context, prevBytesMap, 1000L)
                    }) {
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
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(downloadList, key = { it.id }) { item ->
                    DownloadCardItem(item = item, onOpenFile = {
                        openDownloadedFile(context, item)
                    })
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

/**
 * Builds an organic, rounded "blob" shape (irregular squircle/hexagon)
 * used as the background behind the empty-state icon.
 */
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
fun PlayStoreDownloadProgress(progress: Float?) {
    if (progress != null && progress in 0f..1f) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "PlayStoreProgress"
        )
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(36.dp),
            stroke = Stroke(width = with(LocalDensity.current) { 3.dp.toPx() })
        )
    } else {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(36.dp),
            stroke = Stroke(width = with(LocalDensity.current) { 3.dp.toPx() })
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DownloadCardItem(item: DownloadItem, onOpenFile: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                    Modifier.clickable(onClick = onOpenFile)
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = when (item.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> Icons.Rounded.FilePresent
                        DownloadManager.STATUS_FAILED -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.Downloading
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.fileUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                    Button(
                        onClick = onOpenFile,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = "Open file",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Open", style = MaterialTheme.typography.labelMedium)
                    }
                } else if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING) {
                    PlayStoreDownloadProgress(progress = item.progress)
                }
            }

            if (item.status == DownloadManager.STATUS_RUNNING) {
                val animatedProgress by animateFloatAsState(
                    targetValue = item.progress ?: 0f,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "Progress"
                )
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalSize)} • ${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatSpeed(item.speedBytesPerSec)} • ETA ${formatEta(item.etaSeconds)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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

            do {
                val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Downloaded File" else "Downloaded File"
                val uri = if (uriCol >= 0) cursor.getString(uriCol) ?: "" else ""
                val status = if (statusCol >= 0) cursor.getInt(statusCol) else 0
                val soFar = if (soFarCol >= 0) cursor.getLong(soFarCol) else 0L
                val total = if (totalCol >= 0) cursor.getLong(totalCol) else 0L
                val localUri = if (localUriCol >= 0) cursor.getString(localUriCol) else null

                val progress = if (total > 0) (soFar.toFloat() / total.toFloat()) else null
                val prevSoFar = prevBytesMap[id] ?: soFar
                val bytesDiff = (soFar - prevSoFar).coerceAtLeast(0L)
                val speed = if (elapsedTimeMs > 0 && bytesDiff > 0) (bytesDiff * 1000L) / elapsedTimeMs else 0L
                val remainingBytes = (total - soFar).coerceAtLeast(0L)
                val eta = if (speed > 0) remainingBytes / speed else 0L

                list.add(DownloadItem(id, title, uri, progress, status, soFar, total, speed, eta, localUri))
            } while (cursor.moveToNext())
            cursor.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list.reversed()
}

private fun openDownloadedFile(context: Context, item: DownloadItem) {
    try {
        val uri = if (item.localUri != null) Uri.parse(item.localUri) else null
        if (uri != null) {
            val contentResolver = context.contentResolver
            var mimeType = contentResolver.getType(uri)
            if (mimeType.isNullOrEmpty()) {
                val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                if (!extension.isNullOrEmpty()) {
                    mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                }
            }
            if (mimeType.isNullOrEmpty()) mimeType = "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Open file with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } else {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileUri = dm.getUriForDownloadedFile(item.id)
            if (fileUri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open file with"))
            }
        }
    } catch (e: Exception) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.openDownloadedFile(item.id)
        } catch (ignored: Exception) {}
    }
}
