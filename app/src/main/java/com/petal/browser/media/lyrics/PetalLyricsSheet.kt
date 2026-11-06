/*
 * PetalLyricsSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Synchronized Karaoke Lyrics Sheet.
 * Faithfully redesigned with UI/UX, physics, and animations from PixelPlayer:
 *   - Synchronized line auto-scrolling with user drag hold-off (3.5s delay)
 *   - Karaoke styling: active line scale pop, vibrant highlight color, dimmed inactive lines
 *   - Interactive tap-to-seek to jump playback directly to any lyric timestamp
 *   - Bottom integrated mini-player strip (Album art, Title/Artist, Play/Pause, Next/Prev)
 *   - Import .lrc companion file picker
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media.lyrics

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.PetalAudioPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalLyricsSheet(
    lyrics: PetalLyrics,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    title: String,
    artist: String,
    artwork: Bitmap?,
    audioPalette: PetalAudioPalette,
    onDismiss: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onImportLrc: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // File picker for .lrc import
    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    onImportLrc(text)
                }
            } catch (_: Exception) {}
        }
    }

    // Active line index computation
    val activeIndex by remember(currentPositionMs, lyrics.synced) {
        derivedStateOf {
            if (!lyrics.isSynced) -1
            else {
                var found = -1
                for (i in lyrics.synced.indices) {
                    if (currentPositionMs >= lyrics.synced[i].timeMs) {
                        found = i
                    } else break
                }
                found
            }
        }
    }

    // Auto-scroll tracking with user drag hold-off
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var lastUserInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            lastUserInteractionTime = System.currentTimeMillis()
        }
    }

    // Smooth auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics.isSynced) {
            val elapsedSinceTouch = System.currentTimeMillis() - lastUserInteractionTime
            if (elapsedSinceTouch > 3500L) {
                // Scroll target item towards center (offset approx 180dp)
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = -220
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = audioPalette.surfaceContainerHighest,
        contentColor = audioPalette.onSurface,
        dragHandle = null,
        modifier = modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // ── Top Header: Title, Artist, "Synced Lyrics" Badge, Dismiss Button ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = audioPalette.onSurface
                        )
                        if (lyrics.isSynced) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = audioPalette.seed.copy(alpha = 0.18f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "SYNCED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = audioPalette.seed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "$title • $artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = audioPalette.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                        onClick = { lrcPickerLauncher.launch(arrayOf("*/*")) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = audioPalette.surfaceContainerHigh,
                            contentColor = audioPalette.onSurface
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FileOpen,
                            contentDescription = "Import LRC",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalIconButton(
                        shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = audioPalette.surfaceContainerHigh,
                            contentColor = audioPalette.onSurface
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                color = audioPalette.onSurface.copy(alpha = 0.08f)
            )

            // ── Lyrics Body: Synced LazyColumn or Plain Text ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (lyrics.isSynced) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(lyrics.synced) { index, line ->
                            val isActive = index == activeIndex
                            val isUpcoming = index == activeIndex + 1

                            val targetColor = when {
                                isActive -> audioPalette.seed
                                isUpcoming -> audioPalette.onSurface.copy(alpha = 0.65f)
                                else -> audioPalette.onSurfaceVariant.copy(alpha = 0.35f)
                            }
                            val animatedColor by animateColorAsState(
                                targetValue = targetColor,
                                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                label = "lyricColor"
                            )

                            val scale by animateFloatAsState(
                                targetValue = if (isActive) 1.05f else 1.0f,
                                animationSpec = tween(durationMillis = 240),
                                label = "lyricScale"
                            )

                            Text(
                                text = line.text.ifBlank { "♪" },
                                fontSize = if (isActive) 23.sp else 18.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = animatedColor,
                                lineHeight = if (isActive) 30.sp else 24.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                        onSeekTo(line.timeMs)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }
                    }
                } else if (lyrics.plain.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(lyrics.plain) { _, line ->
                            Text(
                                text = line.ifBlank { "♪" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Normal,
                                color = audioPalette.onSurface.copy(alpha = 0.85f),
                                lineHeight = 26.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // Empty state: No lyrics found
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = audioPalette.seed.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No lyrics found for this track",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = audioPalette.onSurface
                        )
                        Text(
                            text = "Import a companion .lrc file to view synchronized karaoke lines",
                            style = MaterialTheme.typography.bodySmall,
                            color = audioPalette.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                        FilledTonalButton(
                            onClick = { lrcPickerLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select .lrc File")
                        }
                    }
                }
            }

            // ── Bottom Integrated Mini-Player Strip ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtLeast(8.dp)),
                shape = RoundedCornerShape(24.dp),
                color = audioPalette.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Artwork Thumbnail
                    val artShape = RoundedCornerShape(12.dp)
                    Surface(
                        shape = artShape,
                        color = audioPalette.surfaceContainerHighest,
                        modifier = Modifier
                            .size(44.dp)
                            .aspectRatio(1f)
                            .clip(artShape)
                    ) {
                        if (artwork != null) {
                            Image(
                                bitmap = artwork.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = audioPalette.seed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Track Title & Artist
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = audioPalette.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = audioPalette.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    // Controls: Prev, Play/Pause, Next
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.3f)
                                onPrevious()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = audioPalette.onSurface)
                        }

                        FilledTonalIconButton(
                            shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                onPlayPauseToggle()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = audioPalette.seed,
                                contentColor = audioPalette.onSurface
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.3f)
                                onNext()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = audioPalette.onSurface)
                        }
                    }
                }
            }
        }
    }
}
