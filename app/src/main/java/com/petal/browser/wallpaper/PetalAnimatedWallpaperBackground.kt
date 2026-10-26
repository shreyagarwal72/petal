/*
 * PetalAnimatedWallpaperBackground.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Renders static photos, animated GIFs, or looping live videos as the
 * home screen background beneath the Material 3 Expressive UI.
 * Ported from OmniBrowser with Media3 ExoPlayer integration.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.wallpaper

import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.glass.captureGlassWallpaper
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.petal.browser.R
import java.io.File

import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

@OptIn(UnstableApi::class)
@Composable
fun PetalAnimatedWallpaperBackground(
    wallpaperUri: String,
    dim: Float = 0.20f,
    blur: Float = 0f,
    modifier: Modifier = Modifier,
    /**
     * False draws only the still preview of a video, with no player and no video surface.
     * Use it for any copy that is recorded into a glass layer: a playing TextureView must
     * never be recorded and blurred, and a second player would double the decoder load.
     */
    renderVideo: Boolean = true
) {
    // ExoPlayer and the AndroidView can outlive the Activity context through Compose
    // effects. Keep the player bound to the application context instead.
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val isVideo = remember(wallpaperUri) { petalIsVideoWallpaper(wallpaperUri) }

    val isGif = remember(wallpaperUri, isVideo) {
        if (isVideo) false else {
            val lower = wallpaperUri.lowercase()
            lower.endsWith(".gif") || lower.contains(".gif?") || lower.contains("giphy.com")
        }
    }

    // Tracks whether the current video actually started playing. A stale/expired CDN link
    // (e.g. a hotlinked Pexels file URL) fails silently otherwise — ExoPlayer just logs the
    // error and renders nothing, which looks indistinguishable from "not working".
    var videoPlaybackFailed by remember(wallpaperUri) { mutableStateOf(false) }
    var videoBuffering by remember(wallpaperUri) { mutableStateOf(true) }
    var isFirstFrameRendered by remember(wallpaperUri) { mutableStateOf(false) }

    val videoEntranceAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameRendered) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "LiveWallpaperEntranceAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val blurModifier = if (blur > 0.5f) Modifier.blur(blur.dp) else Modifier

        // A video's preview is a real frame taken from the video itself (see PetalVideoThumbs),
        // never a borrowed photo. Images and GIFs use their own URI.
        val videoPoster by produceState<Any?>(
            initialValue = if (isVideo) PetalVideoThumbs.cached(context, wallpaperUri) else null,
            key1 = wallpaperUri,
            key2 = isVideo
        ) {
            value = if (isVideo) PetalVideoThumbs.get(context, wallpaperUri) else null
        }
        val imageModel: Any? = remember(wallpaperUri, isVideo, videoPoster) {
            runCatching {
                when {
                    isVideo -> videoPoster
                    wallpaperUri.startsWith("android.resource://") -> Uri.parse(wallpaperUri)
                    wallpaperUri.startsWith("file://") -> Uri.parse(wallpaperUri)
                    wallpaperUri.startsWith("/") -> File(wallpaperUri)
                    wallpaperUri.startsWith("content://") -> Uri.parse(wallpaperUri)
                    else -> wallpaperUri
                }
            }.getOrNull()
        }
        val imageRequest = remember(imageModel, isGif) {
            if (imageModel == null) null else runCatching {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(300)
                    .apply {
                        if (isGif) {
                            decoderFactory(
                                if (Build.VERSION.SDK_INT >= 28) ImageDecoderDecoder.Factory()
                                else GifDecoder.Factory()
                            )
                        } else {
                            allowHardware(true)
                            bitmapConfig(Bitmap.Config.RGB_565)
                        }
                    }
                    .build()
            }.getOrNull()
        }

        // Show a known-good preview only until the video renders its first frame, or when
        // playback fails. This prevents the thumbnail and live video being visible together.
        if (imageRequest != null && (!isVideo || videoPlaybackFailed || !isFirstFrameRendered)) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { if (!isVideo && renderVideo) PetalWallpaperManager.markReady(wallpaperUri) },
                onError = { if (!isVideo && renderVideo) PetalWallpaperManager.markReady(wallpaperUri) },
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
        }

        if (isVideo && renderVideo && !videoPlaybackFailed) {
            val exoPlayer = remember(wallpaperUri) {
                runCatching {
                    val mediaUri = when {
                        wallpaperUri.startsWith("android.resource://") -> Uri.parse(wallpaperUri)
                        wallpaperUri.startsWith("file://") -> Uri.parse(wallpaperUri)
                        wallpaperUri.startsWith("/") -> Uri.fromFile(File(wallpaperUri))
                        else -> Uri.parse(wallpaperUri)
                    }

                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            1500, // minBufferMs
                            4000, // maxBufferMs
                            500,  // bufferForPlaybackMs
                            1000  // bufferForPlaybackAfterRebufferMs
                        )
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build()

                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .setAllowCrossProtocolRedirects(true)

                    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
                    val mediaSourceFactory = DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(dataSourceFactory)

                    ExoPlayer.Builder(context)
                        .setLoadControl(loadControl)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build().apply {
                            val mediaItem = MediaItem.fromUri(mediaUri)
                            setMediaItem(mediaItem)
                            repeatMode = Player.REPEAT_MODE_ALL
                            volume = 0f // Mute background video
                            playWhenReady = true
                            addListener(object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    android.util.Log.e("PetalWallpaper", "ExoPlayer error playing wallpaper: $wallpaperUri", error)
                                    videoPlaybackFailed = true
                                    PetalWallpaperManager.markReady(wallpaperUri)
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    videoBuffering = playbackState == Player.STATE_BUFFERING
                                }

                                override fun onRenderedFirstFrame() {
                                    isFirstFrameRendered = true
                                    videoBuffering = false
                                    PetalWallpaperManager.markReady(wallpaperUri)
                                }
                            })
                            prepare()
                            play()
                        }
                }.getOrElse { e ->
                    android.util.Log.e("PetalWallpaper", "Failed to build ExoPlayer: $wallpaperUri", e)
                    videoPlaybackFailed = true
                    PetalWallpaperManager.markReady(wallpaperUri)
                    null
                }
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    try {
                        exoPlayer?.stop()
                        exoPlayer?.release()
                    } catch (_: Throwable) {}
                }
            }

            // Lifecycle-aware playback: pause decoding when activity or home screen is stopped/paused
            DisposableEffect(exoPlayer, lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            if (exoPlayer?.playbackState != Player.STATE_ENDED) {
                                exoPlayer?.play()
                            }
                        }
                        Lifecycle.Event.ON_STOP -> {
                            exoPlayer?.pause()
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            AndroidView(
                factory = { ctx ->
                    val playerView = try {
                        val inflater = LayoutInflater.from(ctx)
                        (inflater.inflate(R.layout.view_petal_wallpaper_player, null) as PlayerView).apply {
                            setSafeSurfaceView(this)
                        }
                    } catch (_: Throwable) {
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setSafeSurfaceView(this)
                        }
                    }

                    playerView.apply {
                        this.player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = videoEntranceAlpha
                    }
            )

            // Buffering feedback using ContainedLoadingIndicator with smooth fade-out
            AnimatedVisibility(
                visible = videoBuffering && !isFirstFrameRendered,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                PetalContainedLoadingIndicator(size = 44.dp)
            }
        }

        // Adjustable dimming overlay
        if (dim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f)))
            )
        }
    }
}

/**
 * Fallback to configure PlayerView to use TextureView if inflated programmatically.
 */
private fun setSafeSurfaceView(playerView: PlayerView) {
    try {
        val method = PlayerView::class.java.getMethod("setVideoSurfaceViewType", Int::class.javaPrimitiveType)
        method.invoke(playerView, 2) // 2 is SURFACE_TYPE_TEXTURE_VIEW
    } catch (_: Throwable) {
        try {
            val field = PlayerView::class.java.getDeclaredField("surfaceType")
            field.isAccessible = true
            field.setInt(playerView, 2)
        } catch (_: Throwable) {}
    }
}

/**
 * The wallpaper, drawn so that liquid glass can safely use it.
 *
 * Glass records its wallpaper into a layer, blurs it and refracts it. A playing video
 * (a TextureView) must never be recorded that way — that is what crashed when a live
 * wallpaper was applied with Liquid Glass on. So for a video the glass records only the
 * still preview, and the real video plays on top, outside the recorded layer.
 * Images and GIFs are recorded as before.
 */
@Composable
fun PetalGlassAwareWallpaperBackground(
    wallpaperUri: String,
    dim: Float,
    blur: Float,
    glassWallpaper: com.petal.browser.ui.glass.PetalGlassWallpaper?,
    modifier: Modifier = Modifier
) {
    val isVideo = remember(wallpaperUri) { petalIsVideoWallpaper(wallpaperUri) }
    when {
        glassWallpaper == null -> PetalAnimatedWallpaperBackground(
            wallpaperUri = wallpaperUri, dim = dim, blur = blur, modifier = modifier
        )
        !isVideo -> Box(modifier = modifier.fillMaxSize()) {
            PetalAnimatedWallpaperBackground(
                wallpaperUri = wallpaperUri, dim = dim, blur = blur,
                modifier = Modifier
                    .fillMaxSize()
                    .captureGlassWallpaper(glassWallpaper)
            )
        }
        else -> Box(modifier = modifier.fillMaxSize()) {
            // What the glass sees: the preview frame only.
            PetalAnimatedWallpaperBackground(
                wallpaperUri = wallpaperUri, dim = dim, blur = blur,
                modifier = Modifier
                    .fillMaxSize()
                    .captureGlassWallpaper(glassWallpaper),
                renderVideo = false
            )
            // What you see: the live video, never recorded.
            PetalAnimatedWallpaperBackground(
                wallpaperUri = wallpaperUri, dim = dim, blur = blur,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
