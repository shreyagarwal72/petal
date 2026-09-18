/*
 * PetalDownloadBanner.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Persistent animated floating download status card in Jetpack Compose featuring:
 * 1. Docked near the bottom of the screen above bottom navigation with spring entrance/exit.
 * 2. Active downloading state: Download icon, file name, progress/speed/host subtitle,
 *    and slim animated progress bar flush on its bottom edge.
 * 3. Completion state: Checkmark icon, "File downloaded", formatted size with host, and "Open".
 * 4. Auto-dismiss timer on completion/error with manual close button and swipe-to-dismiss.
 * 5. Full Material 3 Expressive design system integration with dynamic palette support.
 */

package com.petal.browser.compose.downloads

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import com.petal.browser.ui.components.PetalCircularWavyProgressIndicator
import com.petal.browser.ui.components.PetalFloatingStatusCard
import com.petal.browser.ui.components.PetalFloatingStatusCardData
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
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
    val downloadId: Long = 0L,
    val progress: Float? = null,
    val speedBytesPerSec: Long = 0L
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
                                        downloadId = activeItem.id,
                                        progress = activeItem.progress,
                                        speedBytesPerSec = activeItem.speedBytesPerSec
                                    )
                                }
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    currentBannerData.value = ActiveBannerData(
                                        state = BannerState.COMPLETED,
                                        fileName = activeItem.fileName,
                                        fileSize = activeItem.totalSize,
                                        sourceHost = host,
                                        localUri = safeLocalUri,
                                        downloadId = activeItem.id,
                                        progress = 1f,
                                        speedBytesPerSec = 0L
                                    )
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    currentBannerData.value = ActiveBannerData(
                                        state = BannerState.FAILED,
                                        fileName = activeItem.fileName,
                                        fileSize = activeItem.totalSize,
                                        sourceHost = host,
                                        localUri = safeLocalUri,
                                        downloadId = activeItem.id,
                                        progress = null,
                                        speedBytesPerSec = 0L
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

@Composable
fun PetalDownloadBanner(
    bannerData: ActiveBannerData,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val visible = bannerData.state != BannerState.IDLE

    // Auto-dismiss timeout for 3.5 seconds on COMPLETED or FAILED state
    LaunchedEffect(bannerData.state, bannerData.downloadId) {
        if (bannerData.state == BannerState.COMPLETED || bannerData.state == BannerState.FAILED) {
            delay(3500L)
            onDismiss()
        }
    }

    val iconVector = when (bannerData.state) {
        BannerState.DOWNLOADING -> Icons.Rounded.Download
        BannerState.COMPLETED -> Icons.Rounded.CheckCircle
        BannerState.FAILED -> Icons.Rounded.Error
        else -> Icons.Rounded.Download
    }

    val iconBg = when (bannerData.state) {
        BannerState.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
        BannerState.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
        BannerState.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val iconTint = when (bannerData.state) {
        BannerState.DOWNLOADING -> MaterialTheme.colorScheme.onPrimaryContainer
        BannerState.COMPLETED -> MaterialTheme.colorScheme.onTertiaryContainer
        BannerState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val titleText = when (bannerData.state) {
        BannerState.DOWNLOADING -> if (bannerData.fileName.isNotBlank()) bannerData.fileName else "Downloading file..."
        BannerState.COMPLETED -> "Download complete"
        BannerState.FAILED -> "Download failed"
        else -> ""
    }

    val subtitleText = when (bannerData.state) {
        BannerState.DOWNLOADING -> {
            val sizeStr = if (bannerData.fileSize > 0L) formatFileSize(bannerData.fileSize) else ""
            val speedStr = if (bannerData.speedBytesPerSec > 0L) "${formatFileSize(bannerData.speedBytesPerSec)}/s" else ""
            val parts = listOfNotNull(
                if (speedStr.isNotBlank()) speedStr else null,
                if (sizeStr.isNotBlank()) sizeStr else null,
                if (bannerData.sourceHost.isNotBlank()) bannerData.sourceHost else null
            )
            if (parts.isNotEmpty()) parts.joinToString(" • ") else "Downloading in background"
        }
        BannerState.COMPLETED -> {
            val sizeStr = formatFileSize(bannerData.fileSize)
            val nameOrHost = bannerData.fileName.ifBlank { bannerData.sourceHost }
            if (nameOrHost.isNotBlank()) "$nameOrHost • $sizeStr" else sizeStr
        }
        BannerState.FAILED -> bannerData.fileName.ifBlank { "Could not complete download" }
        else -> ""
    }

    val cardData = PetalFloatingStatusCardData(
        title = titleText,
        subtitle = subtitleText,
        icon = if (bannerData.state == BannerState.DOWNLOADING) null else iconVector,
        iconContent = if (bannerData.state == BannerState.DOWNLOADING) {
            {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PetalCircularWavyProgressIndicator(
                        progress = if (bannerData.progress != null) { { bannerData.progress } } else null,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        size = 44.dp,
                        strokeWidth = 3.5.dp,
                        wavelength = 18.dp
                    )
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Downloading",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        iconContainerColor = iconBg,
        iconContentColor = iconTint,
        primaryActionLabel = when (bannerData.state) {
            BannerState.DOWNLOADING -> "Details"
            BannerState.COMPLETED -> "Open"
            BannerState.FAILED -> "Details"
            else -> null
        },
        onPrimaryAction = when (bannerData.state) {
            BannerState.DOWNLOADING, BannerState.FAILED -> onOpenDownloads
            BannerState.COMPLETED -> {
                {
                    openDownloadedFile(context, bannerData)
                    onDismiss()
                }
            }
            else -> null
        },
        progress = if (bannerData.state == BannerState.DOWNLOADING) bannerData.progress else null,
        isIndeterminateProgress = bannerData.state == BannerState.DOWNLOADING && bannerData.progress == null,
        progressColor = if (bannerData.state == BannerState.COMPLETED) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }
    )

    PetalFloatingStatusCard(
        visible = visible,
        data = cardData,
        onDismiss = onDismiss,
        modifier = modifier,
        bottomPadding = 8.dp,
        onClick = onOpenDownloads
    )
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
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
