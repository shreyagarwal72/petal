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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petal.browser.media.ytdlp.PetalSocialDownloadService
import com.petal.browser.media.ytdlp.PetalYtDlpEngine
import com.petal.browser.media.ytdlp.SupportedPlatforms
import com.petal.browser.media.ytdlp.YtDlpFormat
import com.petal.browser.media.ytdlp.YtDlpMediaInfo
import kotlinx.coroutines.launch

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
    val media by PetalMediaSniffer.interceptor.playableMedia.collectAsState()
    var sheetOpen by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(media) { if (media.isNotEmpty()) dismissed = false }

    AnimatedVisibility(
        visible = media.isNotEmpty() && !dismissed,
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
                    Text("Media found", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${media.size} source${if (media.size == 1) "" else "s"} on this page",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                AssistChip(onClick = { sheetOpen = true }, label = { Text("View") })
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // ── Section 1: Passive sniffer streams ────────────────────────
            item {
                Text(
                    "Media sources",
                    style    = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Text(
                    "Detected without interrupting playback",
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
                )
            }

            items(media, key = { it.url }) { item ->
                Surface(
                    shape         = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.quality ?: item.type.name,
                                style    = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = false, onClick = {},
                                label    = { Text(item.type.name) }
                            )
                        }
                        Text(
                            item.title ?: item.url
                                .substringAfterLast('/').substringBefore('?')
                                .ifBlank { "Media source" },
                            maxLines = 2,
                            style    = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onPlay(item.toPlaybackRequest()); onDismiss() }) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Text("Play", modifier = Modifier.padding(start = 6.dp))
                            }
                            if (item.type != MediaInterceptor.MediaType.HLS &&
                                item.type != MediaInterceptor.MediaType.DASH) {
                                AssistChip(
                                    onClick = {
                                        PetalMediaSniffer.download(context, item,
                                            onEnqueued = { onDismiss() })
                                    },
                                    label = {
                                        Icon(Icons.Rounded.Download, null)
                                        Text("Download", modifier = Modifier.padding(start = 5.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ── Section 2: Social Downloader (yt-dlp) ────────────────────
            if (platform != null && currentPageUrl.isNotBlank()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "${platform.emoji} ${platform.displayName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("·", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Social Download",
                            style = MaterialTheme.typography.headlineSmall)
                    }

                    Surface(
                        shape          = MaterialTheme.shapes.large,
                        tonalElevation = 3.dp,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (val state = socialState) {
                                // ── Idle ──────────────────────────────────
                                SocialState.Idle -> {
                                    Text(
                                        "Tap to fetch video info and pick a quality",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            socialState = SocialState.Loading
                                            scope.launch {
                                                val cookies = try {
                                                    CookieManager.getInstance().getCookie(currentPageUrl)
                                                } catch (_: Exception) { null }
                                                val info = PetalYtDlpEngine.fetchInfo(currentPageUrl, cookies)
                                                socialState = if (info != null) {
                                                    SocialState.Ready(info, info.formats.first())
                                                } else {
                                                    SocialState.Failed(
                                                        "Couldn't fetch video info. Check your connection or try again."
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Refresh, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Fetch video info")
                                    }
                                }

                                // ── Loading ───────────────────────────────
                                SocialState.Loading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier    = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text("Fetching video info…",
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                // ── Ready ─────────────────────────────────
                                is SocialState.Ready -> {
                                    val info   = state.info
                                    val selFmt = state.selected

                                    // Thumbnail
                                    if (info.thumbnailUrl != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            AsyncImage(
                                                model            = info.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale     = ContentScale.Crop,
                                                modifier         = Modifier.matchParentSize()
                                            )
                                        }
                                    }

                                    // Title + meta
                                    Text(info.title,
                                        style    = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis)
                                    val meta = listOfNotNull(
                                        info.uploader,
                                        info.durationFormatted?.let { "⏱ $it" }
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    // Format picker
                                    Box {
                                        OutlinedButton(
                                            onClick  = { formatMenuOpen = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(selFmt.label, modifier = Modifier.weight(1f))
                                            Icon(Icons.Rounded.ExpandMore, null,
                                                modifier = Modifier.size(18.dp))
                                        }
                                        DropdownMenu(
                                            expanded          = formatMenuOpen,
                                            onDismissRequest  = { formatMenuOpen = false }
                                        ) {
                                            info.formats.forEach { fmt ->
                                                DropdownMenuItem(
                                                    text    = { Text(fmt.label) },
                                                    onClick = {
                                                        socialState = SocialState.Ready(info, fmt)
                                                        formatMenuOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Download button
                                    Button(
                                        onClick = {
                                            val cookies = try {
                                                CookieManager.getInstance().getCookie(currentPageUrl)
                                            } catch (_: Exception) { null }
                                            PetalSocialDownloadService.enqueue(
                                                context = context,
                                                url     = currentPageUrl,
                                                format  = selFmt,
                                                cookies = cookies,
                                                title   = info.title
                                            )
                                            socialState = SocialState.Done
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Download, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Download · ${selFmt.label}")
                                    }
                                }

                                // ── Failed ────────────────────────────────
                                is SocialState.Failed -> {
                                    Text(
                                        "⚠️ ${state.message}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Button(
                                        onClick  = { socialState = SocialState.Idle },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Try again") }
                                }

                                // ── Done ──────────────────────────────────
                                SocialState.Done -> {
                                    Text(
                                        "✅ Download started — check your notifications.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
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
