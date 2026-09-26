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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
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
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun PetalAnimatedWallpaperBackground(
    wallpaperUri: String,
    dim: Float = 0.20f,
    blur: Float = 0f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val blurModifier = if (blur > 0.5f) Modifier.blur(blur.dp) else Modifier

        if (isVideo) {
            val exoPlayer = remember(wallpaperUri) {
                val mediaUri = if (wallpaperUri.startsWith("file://")) {
                    Uri.fromFile(File(wallpaperUri.removePrefix("file://")))
                } else if (wallpaperUri.startsWith("/")) {
                    Uri.fromFile(File(wallpaperUri))
                } else {
                    Uri.parse(wallpaperUri)
                }

                ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.fromUri(mediaUri)
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f // Mute background video
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            android.util.Log.e("PetalWallpaper", "ExoPlayer error playing wallpaper: $wallpaperUri", error)
                        }
                    })
                    prepare()
                    play()
                }
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    exoPlayer.stop()
                    exoPlayer.release()
                }
            }

            AndroidView(
                factory = { ctx ->
                    val playerView = try {
                        val inflater = LayoutInflater.from(ctx)
                        inflater.inflate(R.layout.view_petal_wallpaper_player, null) as PlayerView
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (blur > 0.5f) {
                            try {
                                val effect = android.graphics.RenderEffect.createBlurEffect(
                                    blur * 2.5f,
                                    blur * 2.5f,
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                                view.setRenderEffect(effect)
                            } catch (_: Throwable) {}
                        } else {
                            try {
                                view.setRenderEffect(null)
                            } catch (_: Throwable) {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
        } else {
            val imageModel = remember(wallpaperUri) {
                if (wallpaperUri.startsWith("file://")) {
                    File(wallpaperUri.removePrefix("file://"))
                } else if (wallpaperUri.startsWith("/")) {
                    File(wallpaperUri)
                } else {
                    wallpaperUri
                }
            }
            val imageRequest = remember(imageModel) {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(300)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
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
