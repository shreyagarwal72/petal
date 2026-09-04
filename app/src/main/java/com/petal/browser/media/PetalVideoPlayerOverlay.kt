package com.petal.browser.media

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Petal Video Player Overlay
 * Pure Jetpack Compose overlay modeled after mpvEx:
 * - Overlaps video views cleanly with transparent gesture backdrop
 * - Features the signature mpvEx Squiggly / Wavy Seekbar
 * - Vertical slide gestures for left brightness & right volume with HUD cards
 * - Double-tap left/right seek with pill animations and haptic ticks
 * - Speed selection, PiP trigger, and title header
 */
@Composable
fun PetalVideoPlayerOverlay(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onFastForward: () -> Unit,
    onRewind: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPipClick: () -> Unit,
    onCloseFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    var areControlsVisible by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableFloatStateOf(playbackSpeed) }
    var showSpeedSelector by remember { mutableStateOf(false) }

    // Local Video Brightness State (0.1f = very dim, 1.0f = full normal brightness)
    // Adjusting brightness only affects the video layer, not the browser activity or device window
    var videoBrightness by remember { mutableFloatStateOf(1.0f) }

    // HUD gesture overlays
    var volumeHudLevel by remember { mutableIntStateOf(-1) }
    var brightnessHudLevel by remember { mutableFloatStateOf(-1f) }
    var doubleTapSeekText by remember { mutableStateOf<String?>(null) }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(4000)
            areControlsVisible = false
            showSpeedSelector = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // Dedicated Background Gesture Layer:
        // Placed at the bottom of the Box stack so controls layer above receives all click events cleanly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = !areControlsVisible
                            if (!areControlsVisible) showSpeedSelector = false
                        },
                        onDoubleTap = { offset ->
                            val width = size.width
                            if (offset.x < width * 0.4f) {
                                onRewind()
                                doubleTapSeekText = "-10s"
                                PetalHapticEngine.getInstance(context).playClick(context)
                            } else if (offset.x > width * 0.6f) {
                                onFastForward()
                                doubleTapSeekText = "+10s"
                                PetalHapticEngine.getInstance(context).playClick(context)
                            } else {
                                onPlayPauseToggle()
                                PetalHapticEngine.getInstance(context).playClick(context)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {},
                        onDragEnd = {
                            volumeHudLevel = -1
                            brightnessHudLevel = -1f
                        },
                        onDragCancel = {
                            volumeHudLevel = -1
                            brightnessHudLevel = -1f
                        },
                    ) { change, dragAmount ->
                        val width = size.width
                        val x = change.position.x
                        val deltaY = -dragAmount.y

                        if (x < width * 0.45f) {
                            // Left side vertical gesture: Video Brightness (affects ONLY video overlay, not browser)
                            val newBrightness = (videoBrightness + (deltaY / 600f)).coerceIn(0.1f, 1.0f)
                            videoBrightness = newBrightness
                            brightnessHudLevel = newBrightness
                        } else if (x > width * 0.55f) {
                            // Right side vertical gesture: Volume
                            audioManager?.let { am ->
                                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val step = if (deltaY > 0) 1 else -1
                                if (abs(deltaY) > 20f) {
                                    val newVol = (currentVol + step).coerceIn(0, maxVolume)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    volumeHudLevel = newVol
                                }
                            }
                        }
                    }
                },
        )

        // Local Video Dimmer Layer: Dims the video content beneath without affecting browser or system brightness
        if (videoBrightness < 1.0f) {
            val dimAlpha = ((1.0f - videoBrightness) * 0.85f).coerceIn(0f, 0.85f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
            )
        }
        // Double-tap Seek Pill Indicator
        LaunchedEffect(doubleTapSeekText) {
            if (doubleTapSeekText != null) {
                delay(800)
                doubleTapSeekText = null
            }
        }
        if (doubleTapSeekText != null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            ) {
                Text(
                    text = doubleTapSeekText ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }

        // Gesture HUD - Brightness & Volume
        if (brightnessHudLevel >= 0f) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessHigh,
                        contentDescription = "Brightness",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(brightnessHudLevel * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (volumeHudLevel >= 0) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    val volIcon = when {
                        volumeHudLevel == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                        volumeHudLevel < (maxVolume / 2) -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    Icon(
                        imageVector = volIcon,
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${((volumeHudLevel.toFloat() / maxVolume.toFloat()) * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Controls Overlay with Gradients
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Gradient Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                            ),
                        )
                        .align(Alignment.TopCenter),
                )

                // Bottom Gradient Scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .align(Alignment.BottomCenter),
                )

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onCloseFullscreen,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Text(
                        text = title.ifEmpty { "Web Video" },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )

                    IconButton(
                        onClick = {
                            showSpeedSelector = !showSpeedSelector
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Playback Speed",
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = onPipClick,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture-in-Picture",
                            tint = Color.White,
                        )
                    }
                }

                // Speed Selector Pill Card (if active)
                if (showSpeedSelector) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 56.dp, end = 24.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                val isSelected = (currentSpeed == speed)
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        )
                                        .clickable {
                                            currentSpeed = speed
                                            onSpeedChange(speed)
                                            showSpeedSelector = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                // Center Action Buttons (Rewind, Play/Pause, Fast-Forward)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            onRewind()
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            onPlayPauseToggle()
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            onFastForward()
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // Bottom Seekbar Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    PetalSeekbarWithTimers(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPaused = !isPlaying,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
