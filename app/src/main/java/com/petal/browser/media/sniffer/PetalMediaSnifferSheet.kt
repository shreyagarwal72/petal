package com.petal.browser.media.sniffer

import android.webkit.CookieManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextButton
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.view.PetalToast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.petal.browser.ui.components.ExpressiveSplitButton
import com.petal.browser.ui.components.SplitButtonVariant
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.media.ytdlp.PetalSocialDownloadService
import com.petal.browser.media.ytdlp.PetalYtDlpEngine
import com.petal.browser.media.ytdlp.SupportedPlatforms
import com.petal.browser.media.ytdlp.YtDlpFormat
import com.petal.browser.media.ytdlp.YtDlpMediaInfo
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

// ── Social downloader state machine ───────────────────────────────────────────
private sealed class SocialState {
    object Idle : SocialState()
    object Loading : SocialState()
    data class Ready(val info: YtDlpMediaInfo, val selected: YtDlpFormat) : SocialState()
    data class Failed(val message: String) : SocialState()
    object Done : SocialState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalMediaSnifferOverlay(
    context: android.content.Context,
    currentPageUrl: String = "",
    onPlay: (MediaInterceptor.MediaPlaybackRequest) -> Unit
) {
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val detectBackground = remember { sp.getBoolean("sp_media_detect_background", true) }
    val autoOpenPanel = remember { sp.getBoolean("sp_media_auto_open_panel", false) }

    val media by PetalMediaSniffer.interceptor.playableMedia.collectAsState()
    val platform = remember(currentPageUrl) { SupportedPlatforms.getPlatform(currentPageUrl) }
    val socialOnly = platform != null
    var sheetOpen by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val isSearchOrInternal = remember(currentPageUrl) {
        PetalMediaSniffer.interceptor.isSearchEngineOrInternalUrl(currentPageUrl)
    }

    val forceOpen by PetalMediaSnifferOverlayBridge.isSheetForcedOpen
    LaunchedEffect(forceOpen) {
        if (forceOpen) {
            sheetOpen = true
            PetalMediaSnifferOverlayBridge.isSheetForcedOpen.value = false
        }
    }

    LaunchedEffect(currentPageUrl, media, socialOnly) {
        dismissed = false
        if (autoOpenPanel && !isSearchOrInternal && (media.isNotEmpty() || socialOnly)) {
            sheetOpen = true
        }
    }

    val isVisible = detectBackground && !isSearchOrInternal && (media.isNotEmpty() || socialOnly) && !dismissed

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = .92f),
        exit  = slideOutVertically { -it } + fadeOut() + scaleOut(targetScale = .92f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (media.isNotEmpty()) "Media found" else "Social download",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        when {
                            media.isNotEmpty() && platform != null -> "${media.size} source${if (media.size == 1) "" else "s"} • ${platform.displayName}"
                            media.isNotEmpty() -> "${media.size} source${if (media.size == 1) "" else "s"} on this page"
                            platform != null -> platform.displayName
                            else -> "Media available"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                AssistChip(onClick = { sheetOpen = true }, label = { Text(stringResource(R.string.ui_view)) })
                IconButton(onClick = { dismissed = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, "Dismiss", modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (sheetOpen) {
        PetalMediaSheet(
            media = media,
            currentPageUrl = currentPageUrl,
            context = context,
            onPlay = onPlay,
            onDismiss = { sheetOpen = false }
        )
    }
}

private fun formatMediaSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PetalMediaSheet(
    media: List<MediaInterceptor.DetectedMedia>,
    currentPageUrl: String,
    context: android.content.Context,
    onPlay: (MediaInterceptor.MediaPlaybackRequest) -> Unit,
    onDismiss: () -> Unit
) {
    val scope    = rememberCoroutineScope()
    val platform = remember(currentPageUrl) { SupportedPlatforms.getPlatform(currentPageUrl) }

    var socialState    by remember { mutableStateOf<SocialState>(SocialState.Idle) }
    var formatMenuOpen by remember { mutableStateOf(false) }
    var isUpdatingExtractor by remember { mutableStateOf(false) }

    com.petal.browser.ui.containment.PetalSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Section 1: Passive sniffer streams ────────────────────────
            if (media.isNotEmpty()) {
                item {
                    val directItems = remember(media) {
                        media.filter { it.type != MediaInterceptor.MediaType.HLS && it.type != MediaInterceptor.MediaType.DASH }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.ui_media_sources),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.ui_detected_without_interrupting_playback),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (directItems.size > 1) {
                            FilledTonalButton(
                                onClick = {
                                    directItems.forEach { directItem ->
                                        PetalMediaSniffer.download(context, directItem)
                                    }
                                    PetalToast.show(context, "Queued ${directItems.size} downloads")
                                    onDismiss()
                                },
                                shape = com.petal.browser.ui.containment.PetalContainmentShapes.Pill,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.ui_all, directItems.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        media.forEachIndexed { index, item ->
                            val position = com.petal.browser.ui.containment.petalGroupPositionFor(index, media.size)
                            val isAudio = item.type == MediaInterceptor.MediaType.AUDIO
                            val isStream = item.type == MediaInterceptor.MediaType.HLS || item.type == MediaInterceptor.MediaType.DASH
                            val sizeStr = formatMediaSize(item.sizeBytes)

                            Surface(
                                shape = com.petal.browser.ui.containment.petalGroupShape(position),
                                color = com.petal.browser.ui.containment.petalGroupSurfaceColor(),
                                tonalElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                                            icon = if (isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.VideoLibrary,
                                            container = if (isAudio) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            tint = if (isAudio) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                            size = 42.dp,
                                            iconSize = 22.dp
                                        )

                                        Column(Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    item.quality ?: item.type.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                item.title ?: item.url
                                                    .substringAfterLast('/').substringBefore('?')
                                                    .ifBlank { "Media stream" },
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = com.petal.browser.ui.containment.PetalContainmentShapes.Pill,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                text = item.type.name + (if (sizeStr != null) " • $sizeStr" else ""),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    // Action bar with responsive buttons and dedicated copy icon button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { onPlay(item.toPlaybackRequest()); onDismiss() },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.ui_play), maxLines = 1)
                                        }

                                        if (!isStream) {
                                            FilledTonalButton(
                                                onClick = {
                                                    PetalMediaSniffer.download(context, item, onEnqueued = { onDismiss() })
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.ui_download), maxLines = 1)
                                            }
                                        }

                                        FilledTonalIconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = ClipData.newPlainText("Media URL", item.url)
                                                clipboard?.setPrimaryClip(clip)
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                                PetalToast.show(context, "Media link copied")
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.ui_copy), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Section 2: Social Downloader (yt-dlp) ────────────────────
            if (platform != null && currentPageUrl.isNotBlank()) {
                item {
                    com.petal.browser.ui.containment.PetalSectionLabel(
                        stringResource(R.string.ui_social_download) + " • " + platform.displayName
                    )

                    com.petal.browser.ui.containment.PetalHeroCard(
                        shape = com.petal.browser.ui.containment.PetalContainmentShapes.HeroInner,
                        containerColor = com.petal.browser.ui.containment.petalGroupSurfaceColor()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                com.petal.browser.ui.containment.PetalGroupIconBadge(
                                    icon = Icons.Rounded.Public,
                                    container = MaterialTheme.colorScheme.secondaryContainer,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    size = 44.dp,
                                    iconSize = 22.dp
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        platform.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        stringResource(R.string.ui_fetch_the_available_media_formats),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (!isUpdatingExtractor) {
                                            isUpdatingExtractor = true
                                            scope.launch {
                                                PetalToast.show(context, context.getString(R.string.ui_updating_extractor))
                                                val res = PetalYtDlpEngine.updateEngine(context)
                                                isUpdatingExtractor = false
                                                if (res.isSuccess) {
                                                    PetalToast.show(context, context.getString(R.string.ui_extractor_updated))
                                                } else {
                                                    PetalToast.show(context, context.getString(R.string.ui_extractor_update_failed))
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isUpdatingExtractor
                                ) {
                                    if (isUpdatingExtractor) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.ui_update_extractor),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                }
                            }

                            when (val state = socialState) {
                                SocialState.Idle -> {
                                    Button(
                                        onClick = {
                                            socialState = SocialState.Loading
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

                                                socialState = if (
                                                    info != null && info.formats.isNotEmpty()
                                                ) {
                                                    SocialState.Ready(info, info.formats.first())
                                                } else {
                                                    SocialState.Failed(
                                                        "Couldn't fetch media information. " +
                                                            "The site may require an updated " +
                                                            "extractor or a signed-in session."
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.ui_fetch_media_info))
                                    }
                                }

                                SocialState.Loading -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            stringResource(R.string.ui_fetching_media_information),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                is SocialState.Ready -> {
                                    val info = state.info
                                    val selFmt = state.selected

                                    if (info.thumbnailUrl != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(176.dp)
                                                .clip(MaterialTheme.shapes.large)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                )
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
                                        info.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val meta = listOfNotNull(
                                        info.uploader,
                                        info.durationFormatted?.let { "Duration $it" }
                                    ).joinToString("  •  ")

                                    if (meta.isNotBlank()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        ExpressiveSplitButton(
                                            label = stringResource(R.string.ui_download_2, selFmt.label),
                                            onPrimaryClick = {
                                                val cookies = PetalMediaSniffer.getCookiesForUrl(currentPageUrl)
                                                    ?: try {
                                                        CookieManager.getInstance().getCookie(currentPageUrl)
                                                    } catch (_: Exception) {
                                                        null
                                                    }

                                                when {
                                                    selFmt.isDirectUrl -> {
                                                        val mimeType = when {
                                                            selFmt.isAudioOnly -> "audio/mp4"
                                                            selFmt.formatId.contains(".webm", ignoreCase = true) -> "video/webm"
                                                            else -> "video/mp4"
                                                        }
                                                        PetalFetchDownloadBridge.enqueueMediaDownload(
                                                            context = context,
                                                            url = selFmt.formatId,
                                                            fileName = info.title.ifBlank { "Petal media" },
                                                            mimeType = mimeType,
                                                            userAgent = android.webkit.WebSettings.getDefaultUserAgent(context),
                                                            cookie = cookies,
                                                            headers = mapOf("Referer" to currentPageUrl),
                                                            onFailed = { socialState = SocialState.Failed("Petal Download Manager could not queue this media.") }
                                                        )
                                                    }
                                                    else -> {
                                                        PetalSocialDownloadService.enqueue(
                                                            context = context,
                                                            url = currentPageUrl,
                                                            format = selFmt,
                                                            cookies = cookies,
                                                            title = info.title
                                                        )
                                                    }
                                                }

                                                socialState = SocialState.Done
                                                onDismiss()
                                            },
                                            onMenuClick = { formatMenuOpen = !formatMenuOpen },
                                            icon = Icons.Rounded.Download,
                                            isMenuExpanded = formatMenuOpen,
                                            variant = SplitButtonVariant.FILLED,
                                            height = 50.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        com.petal.browser.ui.containment.PetalPopupMenu(
                                            expanded = formatMenuOpen,
                                            onDismissRequest = { formatMenuOpen = false }
                                        ) {
                                            info.formats.forEach { fmt ->
                                                com.petal.browser.ui.containment.PetalPopupMenuItem(
                                                    text = { Text(fmt.label) },
                                                    onClick = {
                                                        socialState = SocialState.Ready(info, fmt)
                                                        formatMenuOpen = false
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            if (fmt.isAudioOnly) Icons.Rounded.Public else Icons.Rounded.VideoLibrary,
                                                            contentDescription = null
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                is SocialState.Failed -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            state.message,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { socialState = SocialState.Idle },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.ui_try_again))
                                    }
                                }

                                SocialState.Done -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            stringResource(R.string.ui_download_started_check_petal_s),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
