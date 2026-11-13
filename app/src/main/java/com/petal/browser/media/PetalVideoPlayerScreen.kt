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
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.runtime.withFrameNanos
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.captureGlassWallpaperLive
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper

@OptIn(UnstableApi::class)
@Composable
fun PetalVideoPlayerScreen(
    videoUri: Uri,
    displayName: String,
    initialPositionMs: Long = 0L,
    initialSpeed: Float = 1.0f,
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
            if (initialPositionMs > 0L) {
                seekTo(initialPositionMs)
            }
            if (initialSpeed != 1.0f) {
                playbackParameters = PlaybackParameters(initialSpeed)
            }
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(initialSpeed) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var videoWidth by remember { mutableIntStateOf(16) }
    var videoHeight by remember { mutableIntStateOf(9) }
    val isInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
        activity.isInPictureInPictureMode
    } else false

    // Synchronize ExoPlayer events, PiP remote actions and PetalLiveActionBus
    DisposableEffect(activity, exoPlayer) {
        if (activity != null) {
            PetalPipController.registerPipReceiver(activity, object : PetalPipController.PipActionCallback {
                override fun onPlay() { exoPlayer.play() }
                override fun onPause() { exoPlayer.pause() }
                override fun onSkip(deltaSeconds: Int) {
                    val newPos = (exoPlayer.currentPosition + deltaSeconds * 1000L).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                    exoPlayer.seekTo(newPos)
                }
            })
        }

        val serviceListener = object : PetalMediaSessionService.MediaControlListener {
            override fun onPlay() { exoPlayer.play() }
            override fun onPause() { exoPlayer.pause() }
            override fun onStop() { exoPlayer.pause() }
            override fun onSeekTo(pos: Long) { exoPlayer.seekTo(pos) }
            override fun onSkip(delta: Int) {
                val newPos = (exoPlayer.currentPosition + delta * 1000L).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                exoPlayer.seekTo(newPos)
            }
        }
        PetalMediaSessionService.setGlobalControlListener(serviceListener)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                com.petal.browser.live.PetalLiveActionBus.updateMedia(
                    title = displayName,
                    artist = "Video Playback",
                    isPlaying = playing,
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                    albumArt = null
                )
                PetalMediaSessionService.startMediaSession(
                    context,
                    displayName,
                    "Video Playback",
                    playing,
                    exoPlayer.currentPosition,
                    exoPlayer.duration.coerceAtLeast(0L),
                    null,
                    com.petal.browser.activity.MediaPlayerActivity::class.java,
                    serviceListener
                )
                if (activity != null) {
                    val aspect = if (videoWidth > 0 && videoHeight > 0) Rational(videoWidth, videoHeight) else Rational(16, 9)
                    PetalPipController.updatePipParams(activity, playing, aspect, enableAutoEnter = playing)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    durationMs = if (dur > 0) dur else 0L
                    com.petal.browser.live.PetalLiveActionBus.updateMedia(
                        title = displayName,
                        artist = "Video Playback",
                        isPlaying = exoPlayer.isPlaying,
                        positionMs = exoPlayer.currentPosition,
                        durationMs = durationMs,
                        albumArt = null
                    )
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    if (activity != null) {
                        PetalPipController.updatePipParams(activity, exoPlayer.isPlaying, Rational(videoSize.width, videoSize.height), enableAutoEnter = exoPlayer.isPlaying)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            if (activity != null) {
                PetalPipController.unregisterPipReceiver(activity)
            }
            val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val bgPlay = sp.getBoolean("sp_background_play", false)
            if (!bgPlay) {
                exoPlayer.release()
                PetalMediaSessionService.stopMediaSession(context)
            }
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

    val hostView = androidx.compose.ui.platform.LocalView.current
    val backgroundSnapshot = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val videoWallpaper = rememberPetalGlassWallpaper()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalPetalGlassSource provides hostView,
        LocalPetalGlassWallpaper provides (if (isGlass) videoWallpaper else null)
    ) {
        com.petal.browser.predictive.PetalPredictiveBackSurface(
            enabled = true,
            onBack = onClose
        ) {
            com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
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

        // Live Liquid Glass wallpaper capture for video overlay controls
        if (isGlass) {
            val videoGlassTick = remember { mutableLongStateOf(0L) }
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    while (true) withFrameNanos { videoGlassTick.longValue = it }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .captureGlassWallpaperLive(videoWallpaper) { videoGlassTick.longValue }
            )
        }

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
                        "16:9", "WIDE_16_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        "4:3", "CLASSIC_4_3" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                        "1:1", "SQUARE_1_1" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        "9:16", "VERTICAL_9_16" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        "21:9", "CINEMA_21_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onPipClick = {
                    if (activity != null) {
                        val videoAspect = if (videoWidth > 0 && videoHeight > 0) Rational(videoWidth, videoHeight) else Rational(16, 9)
                        PetalPipController.enterPip(activity, isPlaying, videoAspect)
                    }
                },
                onCloseFullscreen = onClose,
                videoUrl = videoUri.toString(),
                onCastClick = {
                    PetalCastManager.castMedia(context, videoUri.toString(), displayName)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
            }
        }
    }
}
