package com.petal.browser.media.sniffer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalMediaSnifferOverlay(
    context: android.content.Context,
    onPlay: (MediaInterceptor.MediaPlaybackRequest) -> Unit
) {
    val media by PetalMediaSniffer.interceptor.playableMedia.collectAsState()
    var sheetOpen by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(media) { if (media.isNotEmpty()) dismissed = false }

    AnimatedVisibility(
        visible = media.isNotEmpty() && !dismissed,
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = .92f),
        exit = slideOutVertically { -it } + fadeOut() + scaleOut(targetScale = .92f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Media found", style = MaterialTheme.typography.labelLarge)
                    Text("${media.size} source${if (media.size == 1) "" else "s"} on this page", style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = { sheetOpen = true }, label = { Text("View") })
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("Media sources", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Text("Detected without interrupting playback", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(12.dp)) {
                    items(media, key = { it.url }) { item ->
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.quality ?: item.type.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    FilterChip(selected = false, onClick = {}, label = { Text(item.type.name) })
                                }
                                Text(item.title ?: item.url.substringAfterLast('/').substringBefore('?').ifBlank { "Media source" }, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onPlay(item.toPlaybackRequest()); sheetOpen = false }) {
                                        Icon(Icons.Rounded.PlayArrow, null); Text("Play", modifier = Modifier.padding(start = 6.dp))
                                    }
                                    if (item.type != MediaInterceptor.MediaType.HLS && item.type != MediaInterceptor.MediaType.DASH) {
                                        AssistChip(onClick = {
                                            PetalMediaSniffer.download(
                                                context,
                                                item,
                                                onEnqueued = { sheetOpen = false }
                                            )
                                        }, label = { Icon(Icons.Rounded.Download, null); Text("Download", modifier = Modifier.padding(start = 5.dp)) })
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
