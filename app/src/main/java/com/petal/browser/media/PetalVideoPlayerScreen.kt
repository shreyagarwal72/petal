/*
 * PetalVideoPlayerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Native standalone video player composable powered by AndroidX Media3 ExoPlayer
 * and the custom PetalVideoPlayerOverlay (with M3 wavy seekbar, volume/brightness HUD gestures,
 * double-tap skip, aspect ratio modes, speed selector, and PiP support).
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun PetalVideoPlayerScreen(
    videoUri: Uri,
    displayName: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // ── ExoPlayer Setup ──
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var videoWidth by remember { mutableIntStateOf(16) }
    var videoHeight by remember { mutableIntStateOf(9) }
    val isInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
        activity.isInPictureInPictureMode
    } else false

    // Synchronize ExoPlayer events
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

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Polling player position every 200ms when playing
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

    BackHandler {
        onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ExoPlayer PlayerView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Use custom PetalVideoPlayerOverlay instead of stock controls
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            }
        )

        // Custom Petal Overlay (Squiggly seekbar, HUD gestures, PiP, Speed, Back)
        // Hidden during Picture-in-Picture mode to prevent UI controls glitching/shuttering
        if (!isInPip) {
            PetalVideoPlayerOverlay(
                title = displayName,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                playbackSpeed = playbackSpeed,
                onPlayPauseToggle = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
                onSeek = { targetMs ->
                    positionMs = targetMs
                    exoPlayer.seekTo(targetMs)
                },
                onFastForward = {
                    val nextPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration.coerceAtLeast(0L))
                    exoPlayer.seekTo(nextPos)
                    positionMs = nextPos
                },
                onRewind = {
                    val prevPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(prevPos)
                    positionMs = prevPos
                },
                onSpeedChange = { speed ->
                    playbackSpeed = speed
                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                },
                onAspectRatioToggle = { modeId ->
                    resizeMode = when (modeId) {
                        "ZOOM" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        "STRETCH" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "WIDE_16_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        "CLASSIC_4_3" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onPipClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                        try {
                            val targetW = videoWidth.coerceIn(1, 10000)
                            val targetH = videoHeight.coerceIn(1, 10000)
                            val aspectNumerator = targetW.coerceIn(1, 239)
                            val aspectDenominator = targetH.coerceIn(1, 239)
                            val videoAspect = try {
                                Rational(targetW, targetH).let { r ->
                                    val f = r.toFloat()
                                    if (f < 0.41841f) Rational(100, 239)
                                    else if (f > 2.39f) Rational(239, 100)
                                    else r
                                }
                            } catch (_: Exception) {
                                Rational(16, 9)
                            }
                            val pipParams = PictureInPictureParams.Builder()
                                .setAspectRatio(videoAspect)
                                .build()
                            activity.enterPictureInPictureMode(pipParams)
                        } catch (_: Exception) {}
                    }
                },
                onCloseFullscreen = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
