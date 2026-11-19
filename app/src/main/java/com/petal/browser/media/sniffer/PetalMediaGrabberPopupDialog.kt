package com.petal.browser.media.sniffer

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.petal.browser.media.PetalVideoPlayerOverlayBridge
import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.FilterChip
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.petal.browser.R
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.*
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Material 3 Expressive standalone popup interface for Petal Media Grabber Extension.
 *
 * Offers full control:
 * - Active media sniffer streams with direct play, download, PiP, and copy link
 * - High-speed native stream & media capture powered by Petal Media Engine
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalMediaGrabberPopupDialog(
    currentPageUrl: String,
    onDismiss: () -> Unit,
    onPlayMedia: ((MediaInterceptor.MediaPlaybackRequest) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = remember { PetalHapticEngine.getInstance(context) }

    val media by PetalMediaSniffer.interceptor.playableMedia.collectAsStateWithLifecycle()
    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 500.dp)
            .heightIn(max = 680.dp)
    ) {
        com.petal.browser.ui.glass.PetalGlassContainer(
            modifier = Modifier.fillMaxSize(),
            enabled = isGlass,
            live = true
        ) {
            PetalHeroCard(
                shape = PetalContainmentShapes.Hero,
                containerColor = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxSize()
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp)
            ) {
                // ── Header Section ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Petal Media Grabber",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = ExpressiveButtonShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (currentPageUrl.isNotBlank()) currentPageUrl else "Network stream & social downloader",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                // ── Scrollable Body Content ──────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // ── 1. Passive Stream Interceptor Card ───────────────────
                    item {
                        PetalSectionLabel("Detected Page Media (${media.size})")

                        if (media.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "No direct media streams captured yet",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Play a video on the page or use the Social Downloader below.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                media.forEachIndexed { index, item ->
                                    val position = petalGroupPositionFor(index, media.size)
                                    val shape = petalGroupShape(position)
                                    val isAudio = item.type == MediaInterceptor.MediaType.AUDIO
                                    val isStream = item.type == MediaInterceptor.MediaType.HLS || item.type == MediaInterceptor.MediaType.DASH
                                    val sizeStr = formatMediaSize(item.sizeBytes)

                                    Surface(
                                        shape = shape,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                PetalGroupIconBadge(
                                                    icon = if (isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.VideoLibrary,
                                                    container = if (isAudio) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                                    tint = if (isAudio) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    size = 38.dp,
                                                    iconSize = 20.dp
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.quality ?: item.type.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = item.title ?: item.url.substringAfterLast('/').substringBefore('?').ifBlank { "Media stream" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Surface(
                                                    shape = PetalContainmentShapes.Pill,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                ) {
                                                    Text(
                                                        text = item.type.name + (if (sizeStr != null) " • $sizeStr" else ""),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            // Action Buttons
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        haptics.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                                        if (onPlayMedia != null) {
                                                            onPlayMedia(item.toPlaybackRequest())
                                                        } else {
                                                            PetalVideoPlayerOverlayBridge.launchPreviewPlayer(
                                                                context = context,
                                                                mediaUrl = item.url,
                                                                startInPip = false
                                                            )
                                                        }
                                                        onDismiss()
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Play", maxLines = 1)
                                                }

                                                FilledTonalIconButton(
                                                    onClick = {
                                                        haptics.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                                        PetalVideoPlayerOverlayBridge.launchPreviewPlayer(
                                                            context = context,
                                                            mediaUrl = item.url,
                                                            startInPip = true
                                                        )
                                                        onDismiss()
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "PiP", modifier = Modifier.size(18.dp))
                                                }

                                                FilledTonalButton(
                                                    onClick = {
                                                        haptics.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                                        PetalMediaSniffer.download(context, item, onEnqueued = { onDismiss() })
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Download", maxLines = 1)
                                                }

                                                FilledTonalIconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Media URL", item.url))
                                                        haptics.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.5f)
                                                        PetalToast.show(context, "Media link copied")
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
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
    }
}

private fun formatMediaSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
