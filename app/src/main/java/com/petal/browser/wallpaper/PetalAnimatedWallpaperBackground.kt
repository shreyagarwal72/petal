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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
        wallpaperUri.endsWith(".mp4", ignoreCase = true) ||
        wallpaperUri.endsWith(".webm", ignoreCase = true) ||
        wallpaperUri.contains("video-files", ignoreCase = true) ||
        wallpaperUri.contains("videos.pexels.com", ignoreCase = true)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val blurModifier = if (blur > 0.5f) Modifier.blur(blur.dp) else Modifier

        if (isVideo) {
            var exoPlayer by remember(wallpaperUri) {
                mutableStateOf<ExoPlayer?>(null)
            }

            DisposableEffect(wallpaperUri) {
                val player = ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.fromUri(Uri.parse(wallpaperUri))
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f // Mute background video
                    prepare()
                    playWhenReady = true
                }
                exoPlayer = player

                onDispose {
                    player.release()
                    exoPlayer = null
                }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
        } else {
            val imageRequest = remember(wallpaperUri) {
                ImageRequest.Builder(context)
                    .data(wallpaperUri)
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
