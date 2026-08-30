/*
 * PetalDownloadBanner.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome-style top in-app download status banner in Jetpack Compose featuring:
 * 1. Positioned directly below the top address bar with slide-in/slide-out animations.
 * 2. Active downloading state: Download icon, "Downloading file...", "See notification for download status", and "Details" action button to open downloads screen.
 * 3. Completion state: Checkmark icon, "File downloaded", formatted file size with source host subtitle, and "Open" action button launching the file via FileProvider Intent.
 * 4. 5-second auto-dismiss timer on completion/error with manual close button.
 * 5. Full Material 3 Expressive design system integration with dynamic palette support.
 */

package com.petal.browser.compose.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.net.URI
import java.util.Locale

enum class BannerState {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class ActiveBannerData(
    val state: BannerState = BannerState.IDLE,
    val fileName: String = "",
    val fileSize: Long = 0L,
    val sourceHost: String = "",
    val localUri: String = "",
    val downloadId: Long = 0L
)

object PetalDownloadBannerBridge {
    private val currentBannerData = mutableStateOf(ActiveBannerData())
    private val dismissedDownloadKeys = mutableSetOf<String>()

    fun dismissCurrentBanner(data: ActiveBannerData) {
        if (data.downloadId > 0L) {
            dismissedDownloadKeys.add("${data.downloadId}")
            dismissedDownloadKeys.add("${data.downloadId}_${data.state.name}")
        }
        if (data.fileName.isNotBlank()) {
            dismissedDownloadKeys.add("${data.fileName}_${data.state.name}")
        }
        currentBannerData.value = ActiveBannerData(state = BannerState.IDLE)
    }

    @JvmStatic
    fun bindDownloadBanner(
        composeView: ComposeView,
        activity: ComponentActivity,
        onOpenDownloads: Runnable
    ) {
        composeView.apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                // Collect live download items from PetalFetchDownloadBridge
                val downloadItems by PetalFetchDownloadBridge.downloadItems.collectAsState()

                LaunchedEffect(downloadItems) {
                    val activeItem: DownloadItem? = downloadItems.maxByOrNull { it.timestampMs }
                    if (activeItem != null) {
                        val host = try {
                            if (!activeItem.fileUrl.isNullOrBlank()) URI(activeItem.fileUrl).host ?: "" else ""
                        } catch (e: Exception) { "" }

                        val safeLocalUri = activeItem.localUri ?: ""
                        val itemKey = "${activeItem.id}_${activeItem.status}"
                        val idKey = "${activeItem.id}"
                        val nameKey = "${activeItem.fileName}_${activeItem.status}"

                        if (!dismissedDownloadKeys.contains(itemKey) && 
                            !dismissedDownloadKeys.contains(idKey) && 
                            !dismissedDownloadKeys.contains(nameKey)) {
                            when (activeItem.status) {
                                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                                    currentBannerData.value = ActiveBannerData(
                                        state = BannerState.DOWNLOADING,
                                        fileName = activeItem.fileName,
                                        fileSize = activeItem.totalSize,
                                        sourceHost = host,
                                        localUri = safeLocalUri,
                                        downloadId = activeItem.id
                                    )
                                }
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    currentBannerData.value = ActiveBannerData(
                                        state = BannerState.COMPLETED,
                                        fileName = activeItem.fileName,
                                        fileSize = activeItem.totalSize,
                                        sourceHost = host,
                                        localUri = safeLocalUri,
                                        downloadId = activeItem.id
                                    )
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    currentBannerData.value = ActiveBannerData(
                                        state = BannerState.FAILED,
                                        fileName = activeItem.fileName,
                                        fileSize = activeItem.totalSize,
                                        sourceHost = host,
                                        localUri = safeLocalUri,
                                        downloadId = activeItem.id
                                    )
                                }
                                else -> {
                                    currentBannerData.value = ActiveBannerData(state = BannerState.IDLE)
                                }
                            }
                        }
                    } else {
                        currentBannerData.value = ActiveBannerData(state = BannerState.IDLE)
                    }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalDownloadBanner(
                        bannerData = currentBannerData.value,
                        onOpenDownloads = { onOpenDownloads.run() },
                        onDismiss = {
                            dismissCurrentBanner(currentBannerData.value)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalDownloadBanner(
    bannerData: ActiveBannerData,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val visible = bannerData.state != BannerState.IDLE

    // Auto-dismiss timeout for 3 seconds on COMPLETED or FAILED state
    LaunchedEffect(bannerData.state, bannerData.downloadId) {
        if (bannerData.state == BannerState.COMPLETED || bannerData.state == BannerState.FAILED) {
            delay(3000L)
            onDismiss()
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(bannerData.downloadId, bannerData.state) {
        if (visible) {
            dismissState.reset()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 350)
        ) + fadeIn(animationSpec = tween(350)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(bannerData.downloadId, bannerData.state) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -12f) {
                            onDismiss()
                        }
                    }
                }
        ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .shadow(8.dp, shape = RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading Icon Container
                Surface(
                    shape = CircleShape,
                    color = when (bannerData.state) {
                        BannerState.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
                        BannerState.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                        BannerState.FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (bannerData.state) {
                                BannerState.DOWNLOADING -> Icons.Rounded.Download
                                BannerState.COMPLETED -> Icons.Rounded.CheckCircle
                                BannerState.FAILED -> Icons.Rounded.Error
                                else -> Icons.Rounded.Download
                            },
                            contentDescription = null,
                            tint = when (bannerData.state) {
                                BannerState.DOWNLOADING -> MaterialTheme.colorScheme.onPrimaryContainer
                                BannerState.COMPLETED -> MaterialTheme.colorScheme.onTertiaryContainer
                                BannerState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                // Title & Subtitle Info Column
                Column(modifier = Modifier.weight(1f)) {
                    val titleText = when (bannerData.state) {
                        BannerState.DOWNLOADING -> "Downloading file..."
                        BannerState.COMPLETED -> "File downloaded"
                        BannerState.FAILED -> "Download failed"
                        else -> ""
                    }

                    val subtitleText = when (bannerData.state) {
                        BannerState.DOWNLOADING -> "See notification for download status"
                        BannerState.COMPLETED -> {
                            val sizeStr = formatFileSize(bannerData.fileSize)
                            if (bannerData.sourceHost.isNotBlank()) "$sizeStr • ${bannerData.sourceHost}" else sizeStr
                        }
                        BannerState.FAILED -> bannerData.fileName.ifBlank { "Could not complete download" }
                        else -> ""
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Action Button (Details vs Open)
                when (bannerData.state) {
                    BannerState.DOWNLOADING -> {
                        Button(
                            onClick = onOpenDownloads,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    BannerState.COMPLETED -> {
                        Button(
                            onClick = {
                                openDownloadedFile(context, bannerData)
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    BannerState.FAILED -> {
                        Button(
                            onClick = onOpenDownloads,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    else -> {}
                }

                Spacer(Modifier.width(4.dp))

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss banner",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun openDownloadedFile(context: Context, data: ActiveBannerData) {
    try {
        var contentUri: Uri? = null
        var mimeType: String? = null

        val localUriString = data.localUri
        if (localUriString.isNotBlank()) {
            val rawUri = Uri.parse(localUriString)
            if (rawUri.scheme == "file" || rawUri.scheme == null) {
                val filePath = rawUri.path ?: localUriString.removePrefix("file://")
                val file = File(filePath)
                if (file.exists()) {
                    try {
                        contentUri = FileProvider.getUriForFile(
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

        if (contentUri == null && data.downloadId > 0L) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                contentUri = dm.getUriForDownloadedFile(data.downloadId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (contentUri != null) {
            val ext = MimeTypeMap.getFileExtensionFromUrl(data.fileName.ifEmpty { contentUri.toString() })
            if (!ext.isNullOrEmpty()) {
                val detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US))
                if (!detected.isNullOrEmpty()) {
                    mimeType = detected
                }
            }
            if (mimeType.isNullOrEmpty()) {
                mimeType = "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open file"))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
