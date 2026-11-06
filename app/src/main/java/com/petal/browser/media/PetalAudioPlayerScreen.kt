/*
 * PetalAudioPlayerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Standalone Music / Audio Player screen.
 * Faithfully redesigned with UI/UX, physics, and animations from PixelPlayer:
 * - Dynamic blurred ambient mesh gradient background
 * - Top Navigation Bar with collapse pill, "Now Playing", Output/Cast pill, Queue pill
 * - Squircle continuous rounded album art (32dp) with interactive pause-squish scale (1.0f ↔ 0.95f)
 * - Metadata row with marquee Title + Artist, Lyrics pill button, More Options pill button
 * - Expressive Seekbar with elapsed time, centered specs pill badge (48 kHz • 135 kbps • OPUS), duration
 * - Material 3 Expressive playback controls triad with morphing squircle center button and spring weights
 * - Bottom connected segmented compartment pill bar (Shuffle, Repeat, Favorite with heart pop)
 * - Options and Lyrics bottom sheets
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Equalizer
import com.petal.browser.media.lyrics.PetalLyrics
import com.petal.browser.media.lyrics.PetalLyricsEngine
import com.petal.browser.media.lyrics.PetalLyricsSheet
import com.petal.browser.media.queue.PetalQueueItem
import com.petal.browser.media.queue.PetalQueueManager
import com.petal.browser.media.queue.PetalQueueBottomSheet
import com.petal.browser.media.sheets.PetalAudioRouteSheet
import com.petal.browser.media.sheets.PetalSleepTimerSheet
import com.petal.browser.media.sheets.PetalSongInfoSheet
import com.petal.browser.media.sheets.PetalSpeedSelectorSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

data class AudioTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: Bitmap?,
    val metaBadge: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val mimeType: String? = null,
    val formatLabel: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalAudioPlayerScreen(
    mediaUri: Uri,
    displayName: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isInPip: Boolean = false,
    onPipClick: (() -> Unit)? = null,
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
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var isShuffleEnabled by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var isVinylMode by remember { mutableStateOf(false) }
    var showMoreBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showAudioRouteSheet by remember { mutableStateOf(false) }
    var sleepTimerSeconds by remember { mutableStateOf<Long?>(null) }
    var sleepFinishSong by remember { mutableStateOf(false) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    var lyrics by remember { mutableStateOf(PetalLyrics()) }
    LaunchedEffect(mediaUri) {
        lyrics = PetalLyricsEngine.loadLyricsForUri(context, mediaUri)
    }

    val queueManager = remember(mediaUri, displayName) {
        PetalQueueManager(
            PetalQueueItem(
                id = mediaUri.toString(),
                uri = mediaUri,
                title = displayName.substringBeforeLast("."),
                artist = "Audio Track"
            )
        )
    }

    // Sleep Timer countdown ticker
    LaunchedEffect(sleepTimerSeconds) {
        val s = sleepTimerSeconds ?: return@LaunchedEffect
        if (s <= 0L) {
            exoPlayer.pause()
            sleepTimerSeconds = null
            PetalMediaSessionService.stopMediaSession(context)
            return@LaunchedEffect
        }
        delay(1000L)
        sleepTimerSeconds = s - 1L
    }

    // ── PixelPlayer-style smooth progress wiring ──────────────────────────────
    // currentPositionProvider is a lambda so WavySliderExpressive reads it
    // on every draw frame — no snapshot recomposition per tick.
    val currentPositionProvider: () -> Long = remember(exoPlayer) { { exoPlayer.currentPosition } }

    // sampledPositionMs drives the UI; updated by the polling loop below.
    var sampledPositionMs by remember { mutableLongStateOf(0L) }

    // sliderDragValue: non-null only while the user is actively scrubbing (fraction 0–1).
    var sliderDragValue by remember { mutableStateOf<Float?>(null) }

    // targetSeekFraction: holds the last committed seek fraction until playback catches up.
    var targetSeekFraction by remember { mutableFloatStateOf(-1f) }
    var lastSeekFinishedTime by remember { mutableLongStateOf(0L) }

    // animatedProgressFraction: what the seekbar thumb actually reads.
    // Priority: drag > pending seek > live smooth progress.
    // NOTE: reads durationMs directly inside the lambda (not via a snapshot val) so
    // it stays reactive — durationMs is MutableState-backed, safe to read here.
    val animatedProgressFraction: () -> Float = remember(exoPlayer) {
        {
            val dur = durationMs.coerceAtLeast(1L)
            when {
                sliderDragValue != null -> sliderDragValue!!
                targetSeekFraction >= 0f -> targetSeekFraction
                else -> (sampledPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    // Release the held seek target once playback catches up (within 4 %) or after 5 s.
    LaunchedEffect(targetSeekFraction, sampledPositionMs) {
        if (targetSeekFraction < 0f || sliderDragValue != null) return@LaunchedEffect
        val timeSinceSeek = System.currentTimeMillis() - lastSeekFinishedTime
        val dur = durationMs.coerceAtLeast(1L)
        val liveFraction = (sampledPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        val diff = kotlin.math.abs(liveFraction - targetSeekFraction)
        if (timeSinceSeek > 5_000L || diff < 0.04f) {
            targetSeekFraction = -1f
        }
    }

    // Audio Metadata State
    var metadata by remember {
        mutableStateOf(
            AudioTrackMetadata(
                title = displayName.substringBeforeLast("."),
                artist = "Unknown Artist",
                album = "Audio File",
                artwork = null,
                metaBadge = "48 kHz • 135 kbps • OPUS",
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

                val uriStr = mediaUri.toString().lowercase()
                val format = when {
                    extractedMime?.contains("flac", ignoreCase = true) == true || uriStr.endsWith(".flac") -> "FLAC"
                    extractedMime?.contains("wav", ignoreCase = true) == true || uriStr.endsWith(".wav") -> "WAV"
                    extractedMime?.contains("ogg", ignoreCase = true) == true || uriStr.endsWith(".ogg") -> "OGG"
                    extractedMime?.contains("aac", ignoreCase = true) == true || uriStr.endsWith(".aac") || uriStr.endsWith(".m4a") -> "AAC"
                    extractedMime?.contains("opus", ignoreCase = true) == true || uriStr.endsWith(".opus") -> "OPUS"
                    else -> "MP3"
                }

                val badge = formatAudioMetaLabel(extractedMime ?: format, extractedBitrate ?: 135000, 48000)
                    ?: "48 kHz • 135 kbps • $format"

                val artBmp = if (pictureBytes != null) {
                    BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
                } else null

                withContext(Dispatchers.Main) {
                    val newMeta = AudioTrackMetadata(
                        title = extractedTitle?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast("."),
                        artist = extractedArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                        album = extractedAlbum?.takeIf { it.isNotBlank() } ?: "Audio File",
                        artwork = artBmp,
                        metaBadge = badge,
                        bitrate = extractedBitrate,
                        mimeType = extractedMime,
                        formatLabel = format,
                    )
                    metadata = newMeta
                    com.petal.browser.live.PetalLiveActionBus.updateMedia(
                        title = newMeta.title,
                        artist = newMeta.artist,
                        isPlaying = exoPlayer.isPlaying,
                        positionMs = exoPlayer.currentPosition,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        albumArt = newMeta.artwork
                    )
                    PetalMediaSessionService.startMediaSession(
                        context,
                        newMeta.title,
                        newMeta.artist,
                        exoPlayer.isPlaying,
                        exoPlayer.currentPosition,
                        exoPlayer.duration.coerceAtLeast(0L),
                        newMeta.artwork,
                        com.petal.browser.activity.MediaPlayerActivity::class.java,
                        null
                    )
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    // Synchronize ExoPlayer state → Compose state, Foreground Service & PetalLiveActionBus
    DisposableEffect(exoPlayer) {
        val serviceListener = object : PetalMediaSessionService.MediaControlListener {
            override fun onPlay() { exoPlayer.play() }
            override fun onPause() { exoPlayer.pause() }
            override fun onStop() { exoPlayer.pause() }
            override fun onSeekTo(positionMs: Long) { exoPlayer.seekTo(positionMs) }
            override fun onSkip(deltaSeconds: Int) {
                val newPos = (exoPlayer.currentPosition + deltaSeconds * 1000L).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                exoPlayer.seekTo(newPos)
            }
        }
        PetalMediaSessionService.setGlobalControlListener(serviceListener)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                com.petal.browser.live.PetalLiveActionBus.updateMedia(
                    title = metadata.title,
                    artist = metadata.artist,
                    isPlaying = playing,
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                    albumArt = metadata.artwork
                )
                PetalMediaSessionService.startMediaSession(
                    context,
                    metadata.title,
                    metadata.artist,
                    playing,
                    exoPlayer.currentPosition,
                    exoPlayer.duration.coerceAtLeast(0L),
                    metadata.artwork,
                    com.petal.browser.activity.MediaPlayerActivity::class.java,
                    serviceListener
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Capture duration as soon as it is known.
                val dur = exoPlayer.duration
                if (dur > 0) durationMs = dur
                com.petal.browser.live.PetalLiveActionBus.updateMedia(
                    title = metadata.title,
                    artist = metadata.artist,
                    isPlaying = exoPlayer.isPlaying,
                    positionMs = exoPlayer.currentPosition,
                    durationMs = dur.coerceAtLeast(0L),
                    albumArt = metadata.artwork
                )
                PetalMediaSessionService.startMediaSession(
                    context,
                    metadata.title,
                    metadata.artist,
                    exoPlayer.isPlaying,
                    exoPlayer.currentPosition,
                    dur.coerceAtLeast(0L),
                    metadata.artwork,
                    com.petal.browser.activity.MediaPlayerActivity::class.java,
                    serviceListener
                )
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val bgPlay = sp.getBoolean("sp_background_play", true)
            if (!bgPlay) {
                exoPlayer.release()
                PetalMediaSessionService.stopMediaSession(context)
            }
        }
    }

    // ── PixelPlayer-style position sampler ────────────────────────────────────
    // When paused: polls slowly (every 500 ms) waiting for resume, saving CPU.
    // When playing: samples every ~180 ms (matches PixelPlayer's cadence).
    // Skips sampledPositionMs update while user is dragging (sliderDragValue != null).
    LaunchedEffect(exoPlayer) {
        fun sample() {
            val dur = exoPlayer.duration
            if (dur > 0) durationMs = dur
            // Only update sampledPositionMs when user is NOT dragging, so the
            // thumb never fights the user's finger during scrub.
            if (sliderDragValue == null) {
                sampledPositionMs = exoPlayer.currentPosition.coerceIn(0L, dur.coerceAtLeast(0L))
            }
        }

        sample() // Initial snapshot

        while (isActive) {
            if (!exoPlayer.isPlaying) {
                // Paused: back off to 500 ms polling; isPlaying is Compose state
                // so the loop will naturally catch the play event on next iteration.
                delay(500L)
                sample()
                continue
            }

            // Playing: sample every ~180 ms (matches PixelPlayer's cadence).
            delay(180L)
            sample()
        }
    }

    // Disc rotation animation for turntable mode
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

    // Album art pause squish animation (1.0f playing ↔ 0.95f paused)
    val albumArtScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.95f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "albumArtScale"
    )

    // Dynamic adaptive color engine derived from album artwork (PixelPlayer-style)
    val audioPalette = remember(metadata.artwork) {
        PetalAudioColorEngine.extractPalette(metadata.artwork)
    }

    val bgGradient = Brush.verticalGradient(
        colors = audioPalette.backgroundGradientColors
    )

    if (isInPip) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgGradient),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val pipShape = RoundedCornerShape(16.dp)
                Surface(
                    shape = pipShape,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(pipShape),
                    color = audioPalette.surfaceContainerHighest
                ) {
                    if (metadata.artwork != null) {
                        Image(
                            bitmap = metadata.artwork!!.asImageBitmap(),
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
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = audioPalette.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = metadata.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = audioPalette.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        return
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
                    .background(bgGradient),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                                .asPaddingValues().calculateTopPadding().coerceAtLeast(12.dp),
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding().coerceAtLeast(16.dp),
                        )
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // ── 1. Top Navigation Bar (PixelPlayer Header) ──
                    PetalPlayerHeaderTopBar(
                        onCollapse = onClose,
                        onQueueClick = {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            showQueueSheet = true
                        },
                        onAudioRouteClick = {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            showAudioRouteSheet = true
                        },
                        title = stringResource(R.string.ui_now_playing),
                        audioRouteName = null,
                        isLiquidGlass = isGlass,
                        onPipClick = onPipClick,
                    )

                    Spacer(modifier = Modifier.weight(0.3f))

                    // ── 2. Album Artwork: PixelPlayer Squircle continuous rounded art (32dp) ──
                    val artShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(32.dp)

                    if (!isVinylMode) {
                        Surface(
                            shape = artShape,
                            shadowElevation = 24.dp,
                            tonalElevation = 4.dp,
                            color = audioPalette.surfaceContainerHighest,
                            modifier = Modifier
                                .sizeIn(maxWidth = 310.dp, maxHeight = 310.dp)
                                .aspectRatio(1f)
                                .graphicsLayer(scaleX = albumArtScale, scaleY = albumArtScale)
                                .shadow(24.dp, artShape, spotColor = Color.Black.copy(alpha = 0.45f))
                                .clip(artShape)
                                .clickable {
                                    isVinylMode = true
                                    PetalHapticEngine.getInstance(context).playClick(context)
                                }
                                .then(
                                    if (isGlass) Modifier.liquidGlassChrome(artShape, enabled = true, sheenIntensity = 0.85f)
                                    else Modifier
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
                                                    audioPalette.primaryContainer,
                                                    audioPalette.surfaceContainerHighest,
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = audioPalette.primary,
                                        modifier = Modifier.size(76.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        // Vinyl turntable disc
                        Box(
                            modifier = Modifier
                                .sizeIn(maxWidth = 310.dp, maxHeight = 310.dp)
                                .aspectRatio(1f)
                                .shadow(24.dp, ExpressiveButtonShape, spotColor = Color.Black.copy(alpha = 0.5f))
                                .clip(ExpressiveButtonShape)
                                .background(Color(0xFF161618))
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
                                            .size(70.dp)
                                            .clip(ExpressiveButtonShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(4.dp)
                                            .clip(ExpressiveButtonShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(36.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.35f))

                    // ── 3. Metadata Row & Action Buttons (PixelPlayer Layout) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title & Artist on Left
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = metadata.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${metadata.artist} • ${metadata.album}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Quick Actions on Right: Lyrics + More Options
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val actionPillBg = if (isGlass) petalGroupSurfaceColor() else audioPalette.surfaceContainerHigh

                            // Lyrics Pill Button
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(ExpressiveButtonShape)
                                    .background(actionPillBg)
                                    .then(
                                        if (isGlass) Modifier.liquidGlassChrome(ExpressiveButtonShape, enabled = true, sheenIntensity = 0.8f, pressFx = true)
                                        else Modifier
                                    )
                                    .clickable {
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                        showLyricsSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_lyrics_24),
                                    contentDescription = "Lyrics",
                                    tint = audioPalette.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // More Options Button
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(ExpressiveButtonShape)
                                    .background(actionPillBg)
                                    .then(
                                        if (isGlass) Modifier.liquidGlassChrome(ExpressiveButtonShape, enabled = true, sheenIntensity = 0.8f, pressFx = true)
                                        else Modifier
                                    )
                                    .clickable {
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                        showMoreBottomSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More Options",
                                    tint = audioPalette.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── 4. Wavy Seekbar & Specs Badge Row (PixelPlayer Wiring) ──
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val durationSec = (durationMs.toFloat() / 1000f).coerceAtLeast(0.1f)

                        // Absorb vertical drags so the seekbar thumb never conflicts
                        // with any parent scroll/drag gesture (PixelPlayer pattern).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, _ -> }
                                    )
                                }
                        ) {
                            WavySliderExpressive(
                                // Lambda-based read: no recomposition on every tick.
                                value = {
                                    val frac = animatedProgressFraction()
                                    frac * durationSec
                                },
                                onValueChange = { newSec ->
                                    // User is dragging — hold position locally, freeze sampler output.
                                    val frac = (newSec / durationSec).coerceIn(0f, 1f)
                                    sliderDragValue = frac
                                },
                                onValueCommit = { finalSec ->
                                    val finalFrac = (finalSec / durationSec).coerceIn(0f, 1f)
                                    val targetMs = (finalFrac * durationMs).toLong()
                                    // 1. Pin target so UI doesn't snap back before ExoPlayer confirms.
                                    targetSeekFraction = finalFrac
                                    lastSeekFinishedTime = System.currentTimeMillis()
                                    // 2. Issue the actual seek.
                                    exoPlayer.seekTo(targetMs)
                                    // 3. Release drag lock; sampler resumes on next poll.
                                    sliderDragValue = null
                                },
                                valueRange = 0f..durationSec,
                                activeTrackColor = audioPalette.primary,
                                inactiveTrackColor = audioPalette.surfaceContainerHigh.copy(alpha = 0.65f),
                                thumbColor = audioPalette.primary,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Time labels read the same animatedProgressFraction to stay in sync.
                        val displayedPositionMs = remember {
                            derivedStateOf {
                                val dur = durationMs.coerceAtLeast(0L)
                                (animatedProgressFraction() * durationMs).toLong()
                                    .coerceIn(0L, dur)
                            }
                        }
                        PetalExpressiveTimeLabels(
                            currentPositionMs = displayedPositionMs.value,
                            totalDurationMs = durationMs,
                            audioMetaLabel = metadata.metaBadge ?: "48 kHz • 135 kbps • OPUS",
                            textColor = audioPalette.onSurfaceVariant,
                            isLiquidGlass = isGlass,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── 5. M3 Expressive Morphing Playback Controls Triad (PixelPlayer) ──
                    PetalAnimatedPlaybackControls(
                        isPlayingProvider = { isPlaying },
                        onPrevious = {
                            if (queueManager.queue.size > 1 && exoPlayer.currentPosition < 3000L) {
                                val prevItem = queueManager.previous()
                                if (prevItem != null) {
                                    exoPlayer.setMediaItem(MediaItem.fromUri(prevItem.uri))
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            } else {
                                val prev = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(prev)
                            }
                        },
                        onPlayPause = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        onNext = {
                            if (queueManager.queue.size > 1) {
                                val nextItem = queueManager.next()
                                if (nextItem != null) {
                                    exoPlayer.setMediaItem(MediaItem.fromUri(nextItem.uri))
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            } else {
                                val next = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))
                                exoPlayer.seekTo(next)
                            }
                        },
                        previousIcon = Icons.Rounded.SkipPrevious,
                        nextIcon = Icons.Rounded.SkipNext,
                        previousContentDescription = stringResource(R.string.ui_rewind_10s),
                        nextContentDescription = stringResource(R.string.ui_forward_10s),
                        isLiquidGlass = isGlass,
                        colorOtherButtons = audioPalette.surfaceContainerHighest,
                        colorPlayPause = audioPalette.primary,
                        tintPlayPauseIcon = audioPalette.onPrimary,
                        tintOtherIcons = audioPalette.onSurface,
                        height = 82.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ── 6. Bottom Connected Segmented Compartment Pill Bar ──
                    PetalBottomToggleRow(
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        isFavorite = isFavorite,
                        onShuffleToggle = {
                            isShuffleEnabled = !isShuffleEnabled
                            exoPlayer.shuffleModeEnabled = isShuffleEnabled
                        },
                        onRepeatToggle = {
                            repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            exoPlayer.repeatMode = repeatMode
                        },
                        onFavoriteToggle = {
                            isFavorite = !isFavorite
                        },
                        isLiquidGlass = isGlass,
                        activeColorShuffle = audioPalette.primary,
                        onActiveColorShuffle = audioPalette.onPrimary,
                        containerColor = audioPalette.surfaceContainerHighest.copy(alpha = 0.75f),
                        height = 56.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }

                // ── More Options Modal Bottom Sheet ──
                if (showMoreBottomSheet) {
                    com.petal.browser.ui.containment.PetalSheet(
                        onDismissRequest = { showMoreBottomSheet = false },
                        title = "Track Options"
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {

                            // Playback Speed Selector
                            Text(
                                text = "Playback Speed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                speeds.forEach { speed ->
                                    val isSelected = (playbackSpeed == speed)
                                    val shape = RoundedCornerShape(16.dp)
                                    Box(
                                        modifier = Modifier
                                            .clip(shape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                            .clickable {
                                                playbackSpeed = speed
                                                exoPlayer.playbackParameters = PlaybackParameters(speed)
                                                PetalHapticEngine.getInstance(context).playClick(context)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (speed == 1.0f) "1x" else "${speed}x",
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            // Details / Song Info Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        showMoreBottomSheet = false
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                        showSongInfoSheet = true
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Track Details & Audio Specs",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Sleep Timer Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        showMoreBottomSheet = false
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                        showSleepTimerSheet = true
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = if (sleepTimerSeconds != null) "Sleep Timer (${sleepTimerSeconds!! / 60}m remaining)" else "Sleep Timer",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // System Equalizer Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        showMoreBottomSheet = false
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                        try {
                                            val eqIntent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, exoPlayer.audioSessionId)
                                                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                                            }
                                            context.startActivity(eqIntent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "System equalizer not available on this device", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Equalizer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Equalizer & Audio Effects",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Vinyl / Squircle Mode Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        isVinylMode = !isVinylMode
                                        showMoreBottomSheet = false
                                        PetalHapticEngine.getInstance(context).playClick(context)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isVinylMode) Icons.Rounded.GraphicEq else Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = if (isVinylMode) "Switch to Squircle Artwork" else "Switch to Turntable Vinyl Mode",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Share Option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        showMoreBottomSheet = false
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "audio/*"
                                                putExtra(Intent.EXTRA_STREAM, mediaUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Share Track",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // ── Lyrics Sheet (PixelPlayer Karaoke View) ──
                if (showLyricsSheet) {
                    PetalLyricsSheet(
                        lyrics = lyrics,
                        currentPositionMs = currentPositionProvider(),
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        title = metadata.title,
                        artist = metadata.artist,
                        artwork = metadata.artwork,
                        audioPalette = audioPalette,
                        onDismiss = { showLyricsSheet = false },
                        onSeekTo = { pos ->
                            exoPlayer.seekTo(pos)
                        },
                        onPlayPauseToggle = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onPrevious = {
                            val prev = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(prev)
                        },
                        onNext = {
                            val next = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs)
                            exoPlayer.seekTo(next)
                        },
                        onImportLrc = { lrcText ->
                            lyrics = PetalLyricsEngine.parseLrc(lrcText)
                        }
                    )
                }

                // ── Queue & Playlist Sheet ──
                if (showQueueSheet) {
                    PetalQueueBottomSheet(
                        queueManager = queueManager,
                        isPlaying = isPlaying,
                        audioPalette = audioPalette,
                        onTrackSelected = { item ->
                            exoPlayer.setMediaItem(MediaItem.fromUri(item.uri))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        onDismiss = { showQueueSheet = false }
                    )
                }

                // ── Sleep Timer Sheet ──
                if (showSleepTimerSheet) {
                    PetalSleepTimerSheet(
                        remainingSeconds = sleepTimerSeconds,
                        finishEndOfSong = sleepFinishSong,
                        audioPalette = audioPalette,
                        onStartTimer = { mins, finishSong ->
                            sleepTimerSeconds = mins * 60L
                            sleepFinishSong = finishSong
                        },
                        onCancelTimer = {
                            sleepTimerSeconds = null
                        },
                        onDismiss = { showSleepTimerSheet = false }
                    )
                }

                // ── Track Info Sheet ──
                if (showSongInfoSheet) {
                    PetalSongInfoSheet(
                        metadata = metadata,
                        mediaUri = mediaUri,
                        durationMs = durationMs,
                        audioPalette = audioPalette,
                        onDismiss = { showSongInfoSheet = false }
                    )
                }

                // ── Speed Selector Sheet ──
                if (showSpeedSheet) {
                    PetalSpeedSelectorSheet(
                        currentSpeed = playbackSpeed,
                        audioPalette = audioPalette,
                        onSpeedSelected = { speed ->
                            playbackSpeed = speed
                            exoPlayer.playbackParameters = PlaybackParameters(speed)
                        },
                        onDismiss = { showSpeedSheet = false }
                    )
                }

                // ── Audio Route Sheet ──
                if (showAudioRouteSheet) {
                    PetalAudioRouteSheet(
                        audioPalette = audioPalette,
                        onDismiss = { showAudioRouteSheet = false }
                    )
                }
            }
        }
    }
}
