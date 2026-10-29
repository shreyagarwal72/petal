/*
 * PetalAudioPlayerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive standalone Audio Player screen.
 * Displays album artwork / animated turntable record disc, track metadata,
 * M3 wavy seekbar, floating play/pause controls, speed selector, and repeat toggle.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

data class AudioTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: Bitmap?,
    val metaBadge: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAudioPlayerScreen(
    mediaUri: Uri,
    displayName: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ── Media3 ExoPlayer Instance ──
    val exoPlayer = remember(mediaUri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.fromUri(mediaUri)
            setMediaItem(item)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var isVinylMode by remember { mutableStateOf(false) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    // Audio Metadata State
    var metadata by remember {
        mutableStateOf(
            AudioTrackMetadata(
                title = displayName.substringBeforeLast("."),
                artist = "Unknown Artist",
                album = "Audio File",
                artwork = null,
                metaBadge = null,
            )
        )
    }

    // Load Audio Metadata asynchronously
    LaunchedEffect(mediaUri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if ("content".equals(mediaUri.scheme, ignoreCase = true)) {
                    retriever.setDataSource(context, mediaUri)
                } else {
                    retriever.setDataSource(mediaUri.path ?: mediaUri.toString())
                }
                val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val extractedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val extractedBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                val extractedMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                val pictureBytes = retriever.embeddedPicture

                val bitrateLabel = extractedBitrate?.let { "${it / 1000} kbps" }
                val uriStr = mediaUri.toString().lowercase()
                val formatLabel = when {
                    extractedMime?.contains("flac", ignoreCase = true) == true || uriStr.endsWith(".flac") -> "FLAC"
                    extractedMime?.contains("wav", ignoreCase = true) == true || uriStr.endsWith(".wav") -> "WAV"
                    extractedMime?.contains("ogg", ignoreCase = true) == true || uriStr.endsWith(".ogg") -> "OGG"
                    extractedMime?.contains("aac", ignoreCase = true) == true || uriStr.endsWith(".aac") || uriStr.endsWith(".m4a") -> "AAC"
                    extractedMime?.contains("opus", ignoreCase = true) == true || uriStr.endsWith(".opus") -> "OPUS"
                    else -> "MP3"
                }
                val badgeText = listOfNotNull(bitrateLabel, formatLabel).joinToString(" • ")

                val artBmp = if (pictureBytes != null) {
                    BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
                } else null

                withContext(Dispatchers.Main) {
                    metadata = AudioTrackMetadata(
                        title = extractedTitle?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast("."),
                        artist = extractedArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                        album = extractedAlbum?.takeIf { it.isNotBlank() } ?: "Audio File",
                        artwork = artBmp,
                        metaBadge = badgeText,
                    )
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    // Synchronize ExoPlayer
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    durationMs = if (dur > 0) dur else 0L
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Progress updater loop
    LaunchedEffect(isPlaying) {
        while (isActive) {
            if (exoPlayer.isPlaying) {
                positionMs = exoPlayer.currentPosition
                val dur = exoPlayer.duration
                if (dur > 0) durationMs = dur
            }
            delay(200)
        }
    }

    // Disc rotation animation
    val discRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            discRotation.animateTo(
                targetValue = discRotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 10_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                )
            )
        } else {
            discRotation.stop()
        }
    }

    val backgroundSnapshot = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onClose
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot, isolateGlassWallpaper = false) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    ),
            ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                        .asPaddingValues().calculateTopPadding().coerceAtLeast(16.dp),
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding().coerceAtLeast(16.dp),
                )
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.ui_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = stringResource(R.string.ui_now_playing),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary,
                )

                IconButton(
                    onClick = { showSpeedDialog = !showSpeedDialog },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = stringResource(R.string.ui_playback_speed),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Speed Selector Dialog / Card
            if (showSpeedDialog) {
                val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
                val speedShape = RoundedCornerShape(20.dp)
                Surface(
                    shape = speedShape,
                    color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .liquidGlassChrome(speedShape, enabled = isGlass),
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        speeds.forEach { speed ->
                            val isSelected = (playbackSpeed == speed)
                            val speedText = if (speed == 1.0f) "1x" else if (speed == 2.0f) "2x" else "${speed}x"
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedDialog = false
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = speedText,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // ── Album Artwork (Squircle Card ↔ Vinyl Disc Mode) ──
            if (!isVinylMode) {
                val artShape = RoundedCornerShape(28.dp)
                Surface(
                    shape = artShape,
                    shadowElevation = 16.dp,
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .size(270.dp)
                        .clip(artShape)
                        .clickable {
                            isVinylMode = true
                            PetalHapticEngine.getInstance(context).playClick(context)
                        }
                        .then(
                            if (isGlass) Modifier.liquidGlassChrome(artShape, enabled = true, sheenIntensity = 0.9f)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), artShape)
                        ),
                ) {
                    if (metadata.artwork != null) {
                        Image(
                            bitmap = metadata.artwork!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.ui_album_artwork),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.surfaceContainerHighest,
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp),
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(270.dp)
                        .shadow(16.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF161618))
                        .border(
                            width = 6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            shape = CircleShape
                        )
                        .clickable {
                            isVinylMode = false
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (metadata.artwork != null) {
                        Image(
                            bitmap = metadata.artwork!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.ui_album_artwork),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(discRotation.value),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(discRotation.value)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.surfaceContainerHighest,
                                            Color(0xFF101012),
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // ── Track Metadata (PixelPlayer Style) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${metadata.artist} • ${metadata.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!metadata.metaBadge.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PetalAudioMetaBadge(
                        metaText = metadata.metaBadge ?: "",
                        isLiquidGlass = isGlass,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // ── Wavy Seekbar with Timers (PixelPlayer Engine) ──
            PetalSeekbarWithTimers(
                positionMs = positionMs,
                durationMs = durationMs,
                isPaused = !isPlaying,
                onSeek = { targetMs ->
                    positionMs = targetMs
                    exoPlayer.seekTo(targetMs)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Playback Controls (PixelPlayer Dynamic Weights & Morphing) ──
            PetalAnimatedPlaybackControls(
                isPlayingProvider = { isPlaying },
                onPrevious = {
                    val prev = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(prev)
                    positionMs = prev
                },
                onPlayPause = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
                onNext = {
                    val next = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))
                    exoPlayer.seekTo(next)
                    positionMs = next
                },
                previousIcon = Icons.Rounded.SkipPrevious,
                nextIcon = Icons.Rounded.SkipNext,
                previousContentDescription = stringResource(R.string.ui_rewind_10s),
                nextContentDescription = stringResource(R.string.ui_forward_10s),
                isLiquidGlass = isGlass,
                height = 76.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Secondary Tools Row (Repeat, Speed, Vinyl Mode Toggle) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Repeat Mode Toggle
                IconButton(
                    onClick = {
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                        exoPlayer.repeatMode = repeatMode
                        PetalHapticEngine.getInstance(context).playClick(context)
                    },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.ui_repeat_mode),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Speed Capsule
                PetalSpeedCapsule(
                    speed = playbackSpeed,
                    isLiquidGlass = isGlass,
                    onClick = { showSpeedDialog = !showSpeedDialog },
                )

                // Vinyl Disc / Squircle Artwork Mode Toggle
                IconButton(
                    onClick = {
                        isVinylMode = !isVinylMode
                        PetalHapticEngine.getInstance(context).playClick(context)
                    },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = if (isVinylMode) Icons.Rounded.Album else Icons.Rounded.GraphicEq,
                        contentDescription = "Toggle Turntable",
                        tint = if (isVinylMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
        }
    }
}
