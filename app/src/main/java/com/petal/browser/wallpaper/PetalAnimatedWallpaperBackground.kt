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
import com.petal.browser.ui.components.PRESET_WALLPAPERS
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
    modifier: Modifier = Modifier
) {
    // ExoPlayer and the AndroidView can outlive the Activity context through Compose
    // effects. Keep the player bound to the application context instead.
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val isVideo = remember(wallpaperUri) {
        val lower = wallpaperUri.lowercase()
        lower.endsWith(".mp4") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mkv") ||
        lower.endsWith(".mov") ||
        lower.endsWith(".3gp") ||
        lower.endsWith(".ts") ||
        lower.endsWith(".avi") ||
        lower.endsWith(".flv") ||
        lower.endsWith(".m4v") ||
        lower.endsWith(".m3u8") ||
        lower.contains(".m3u8?") ||
        lower.contains(".mp4?") ||
        lower.contains(".webm?") ||
        lower.contains("video-files") ||
        lower.contains("videos.pexels.com") ||
        lower.contains("/video/") ||
        lower.contains("live_wallpaper_") ||
        runCatching {
            val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(wallpaperUri)
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            mime?.startsWith("video/") == true
        }.getOrDefault(false)
    }

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

        val imageModel = remember(wallpaperUri, videoPlaybackFailed) {
            runCatching {
                val effectiveUri = if (isVideo) {
                    PRESET_WALLPAPERS.firstOrNull { it.fullUrl == wallpaperUri }?.thumbUrl ?: wallpaperUri
                } else {
                    wallpaperUri
                }
                when {
                    effectiveUri.startsWith("android.resource://") -> Uri.parse(effectiveUri)
                    effectiveUri.startsWith("file://") -> File(effectiveUri.removePrefix("file://"))
                    effectiveUri.startsWith("/") -> File(effectiveUri)
                    effectiveUri.startsWith("content://") -> Uri.parse(effectiveUri)
                    else -> effectiveUri
                }
            }.getOrElse { wallpaperUri }
        }
        val imageRequest = remember(imageModel, isGif) {
            runCatching {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(300)
                    .apply {
                        if (isGif) {
                            decoderFactory(
                                if (Build.VERSION.SDK_INT >= 28) {
                                    ImageDecoderDecoder.Factory()
                                } else {
                                    GifDecoder.Factory()
                                }
                            )
                        } else {
                            allowHardware(true)
                            bitmapConfig(Bitmap.Config.RGB_565)
                        }
                    }
                    .build()
            }.getOrNull()
        }

        // Static thumbnail / preview layer is ALWAYS rendered beneath the video or static image
        // to prevent black screen flickers during live wallpaper initialization.
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
        }

        if (isVideo && !videoPlaybackFailed) {
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
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    videoBuffering = playbackState == Player.STATE_BUFFERING
                                }

                                override fun onRenderedFirstFrame() {
                                    isFirstFrameRendered = true
                                    videoBuffering = false
                                }
                            })
                            prepare()
                            play()
                        }
                }.getOrElse { e ->
                    android.util.Log.e("PetalWallpaper", "Failed to build ExoPlayer: $wallpaperUri", e)
                    videoPlaybackFailed = true
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
