package com.petal.browser.media.sniffer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
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
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.ytdlp.PetalSocialDownloadService
import com.petal.browser.media.ytdlp.PetalYtDlpEngine
import com.petal.browser.media.ytdlp.SupportedPlatforms
import com.petal.browser.media.ytdlp.YtDlpFormat
import com.petal.browser.media.ytdlp.YtDlpMediaInfo
import com.petal.browser.ui.containment.*
import com.petal.browser.view.PetalToast
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Material 3 Expressive standalone popup interface for Petal Media Grabber Extension.
 *
 * Offers full control:
 * - Active media sniffer streams with direct play, download, and copy link
 * - Social & video platform extractor (yt-dlp) with thumbnail and multi-format selector
 * - Built-in yt-dlp updater dashboard (inspired by Seal) with version display,
 *   update channel selection (Stable / Nightly), last update time, and one-tap update.
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

    val media by PetalMediaSniffer.interceptor.playableMedia.collectAsState()
    val platform = remember(currentPageUrl) { SupportedPlatforms.getPlatform(currentPageUrl) }

    var ytDlpVersion by remember { mutableStateOf(PetalYtDlpEngine.version(context)) }
    var updateChannel by remember { mutableStateOf(PetalYtDlpEngine.getUpdateChannel(context)) }
    var lastUpdateTime by remember { mutableStateOf(PetalYtDlpEngine.getLastUpdateTime(context)) }
    var isUpdatingExtractor by remember { mutableStateOf(false) }

    var socialState by remember { mutableStateOf<GrabberSocialState>(GrabberSocialState.Idle) }
    var formatMenuOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PetalHeroCard(
            shape = PetalContainmentShapes.Hero,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 500.dp)
                .heightIn(max = 680.dp)
                .padding(16.dp)
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
                                shape = CircleShape,
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
                                                if (onPlayMedia != null) {
                                                    Button(
                                                        onClick = {
                                                            haptics.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                                            onPlayMedia(item.toPlaybackRequest())
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
                                                }

                                                if (!isStream) {
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

                    // ── 2. Social & Web Video Extractor (yt-dlp) ───────────────
                    item {
                        PetalSectionLabel(
                            if (platform != null) "Social Media Extractor • ${platform.displayName}"
                            else "Web Video Extractor (yt-dlp)"
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PetalGroupIconBadge(
                                        icon = Icons.Rounded.Public,
                                        container = MaterialTheme.colorScheme.secondaryContainer,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        size = 40.dp,
                                        iconSize = 20.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = platform?.displayName ?: "Universal Extractor",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Extract video/audio from YouTube, X, IG, TikTok & 1000+ sites",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                when (val state = socialState) {
                                    GrabberSocialState.Idle -> {
                                        Button(
                                            onClick = {
                                                socialState = GrabberSocialState.Loading
                                                scope.launch {
                                                    val cookies = PetalMediaSniffer.getCookiesForUrl(currentPageUrl)
                                                        ?: try {
                                                            CookieManager.getInstance().getCookie(currentPageUrl)
                                                        } catch (_: Exception) {
                                                            null
                                                        }

                                                    val info = PetalYtDlpEngine.fetchInfo(
                                                        context = context,
                                                        url = currentPageUrl,
                                                        cookies = cookies
                                                    )

                                                    socialState = if (info != null && info.formats.isNotEmpty()) {
                                                        GrabberSocialState.Ready(info, info.formats.first())
                                                    } else {
                                                        GrabberSocialState.Failed(
                                                            "Couldn't fetch media information. Check URL or try updating the extractor below."
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Analyze Page Media", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    GrabberSocialState.Loading -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                "Extracting stream formats...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    is GrabberSocialState.Ready -> {
                                        val info = state.info
                                        val selFmt = state.selected

                                        if (info.thumbnailUrl != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(140.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            ) {
                                                AsyncImage(
                                                    model = info.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.matchParentSize()
                                                )
                                            }
                                        }

                                        Text(
                                            text = info.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val meta = listOfNotNull(
                                            info.uploader,
                                            info.durationFormatted?.let { "Duration $it" }
                                        ).joinToString(" • ")

                                        if (meta.isNotBlank()) {
                                            Text(
                                                text = meta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Format selector and download button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = { formatMenuOpen = true },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(selFmt.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(16.dp))
                                            }

                                            Button(
                                                onClick = {
                                                    val cookies = PetalMediaSniffer.getCookiesForUrl(currentPageUrl)
                                                        ?: try {
                                                            CookieManager.getInstance().getCookie(currentPageUrl)
                                                        } catch (_: Exception) {
                                                            null
                                                        }

                                                    if (selFmt.isDirectUrl) {
                                                        val mimeType = if (selFmt.isAudioOnly) "audio/mp4" else "video/mp4"
                                                        PetalFetchDownloadBridge.enqueueMediaDownload(
                                                            context = context,
                                                            url = selFmt.formatId,
                                                            fileName = info.title.ifBlank { "Petal media" },
                                                            mimeType = mimeType,
                                                            userAgent = android.webkit.WebSettings.getDefaultUserAgent(context),
                                                            cookie = cookies,
                                                            headers = mapOf("Referer" to currentPageUrl),
                                                            onFailed = { socialState = GrabberSocialState.Failed("Download failed to queue.") }
                                                        )
                                                    } else {
                                                        PetalSocialDownloadService.enqueue(
                                                            context = context,
                                                            url = currentPageUrl,
                                                            format = selFmt,
                                                            cookies = cookies,
                                                            title = info.title
                                                        )
                                                    }
                                                    socialState = GrabberSocialState.Done
                                                    onDismiss()
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Download")
                                            }
                                        }

                                        PetalPopupMenu(
                                            expanded = formatMenuOpen,
                                            onDismissRequest = { formatMenuOpen = false }
                                        ) {
                                            info.formats.forEach { fmt ->
                                                PetalPopupMenuItem(
                                                    text = { Text(fmt.label) },
                                                    onClick = {
                                                        socialState = GrabberSocialState.Ready(info, fmt)
                                                        formatMenuOpen = false
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            if (fmt.isAudioOnly) Icons.Rounded.Audiotrack else Icons.Rounded.VideoLibrary,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    is GrabberSocialState.Failed -> {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        OutlinedButton(
                                            onClick = { socialState = GrabberSocialState.Idle },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retry")
                                        }
                                    }

                                    GrabberSocialState.Done -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                            Text("Download enqueued successfully", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 3. Built-in yt-dlp Extractor Updater (Seal-inspired) ───
                    item {
                        PetalSectionLabel("yt-dlp Extractor & Engine")

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PetalGroupIconBadge(
                                        icon = Icons.Rounded.SystemUpdate,
                                        container = MaterialTheme.colorScheme.tertiaryContainer,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        size = 40.dp,
                                        iconSize = 20.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Core Version: $ytDlpVersion",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val timeStr = if (lastUpdateTime > 0L) {
                                            val fmt = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                            "Last updated: ${fmt.format(Date(lastUpdateTime))}"
                                        } else {
                                            "Never updated"
                                        }
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Update Channel Selector (Stable vs Nightly like Seal)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Update Channel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (updateChannel == YoutubeDL.UpdateChannel.NIGHTLY) "Nightly (Cutting-edge fixes)" else "Stable (Official release)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterChip(
                                            selected = updateChannel == YoutubeDL.UpdateChannel.STABLE,
                                            onClick = {
                                                updateChannel = YoutubeDL.UpdateChannel.STABLE
                                                PetalYtDlpEngine.setUpdateChannel(context, YoutubeDL.UpdateChannel.STABLE)
                                            },
                                            label = { Text("Stable") }
                                        )
                                        FilterChip(
                                            selected = updateChannel == YoutubeDL.UpdateChannel.NIGHTLY,
                                            onClick = {
                                                updateChannel = YoutubeDL.UpdateChannel.NIGHTLY
                                                PetalYtDlpEngine.setUpdateChannel(context, YoutubeDL.UpdateChannel.NIGHTLY)
                                            },
                                            label = { Text("Nightly") }
                                        )
                                    }
                                }

                                // Update Button
                                Button(
                                    onClick = {
                                        if (!isUpdatingExtractor) {
                                            isUpdatingExtractor = true
                                            haptics.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                            scope.launch {
                                                PetalToast.show(context, "Updating yt-dlp core from GitHub...")
                                                val res = PetalYtDlpEngine.updateEngine(context, updateChannel)
                                                isUpdatingExtractor = false
                                                if (res.isSuccess) {
                                                    ytDlpVersion = PetalYtDlpEngine.version(context)
                                                    lastUpdateTime = PetalYtDlpEngine.getLastUpdateTime(context)
                                                    PetalToast.show(context, "yt-dlp updated to $ytDlpVersion")
                                                } else {
                                                    PetalToast.show(context, "Update check failed: ${res.exceptionOrNull()?.message ?: "Network error"}")
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isUpdatingExtractor,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (isUpdatingExtractor) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Updating yt-dlp...")
                                    } else {
                                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Check & Update yt-dlp Core", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private sealed class GrabberSocialState {
    object Idle : GrabberSocialState()
    object Loading : GrabberSocialState()
    data class Ready(val info: YtDlpMediaInfo, val selected: YtDlpFormat) : GrabberSocialState()
    data class Failed(val message: String) : GrabberSocialState()
    object Done : GrabberSocialState()
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
