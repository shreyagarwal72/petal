package com.petal.browser.media

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.FastForward
import com.petal.browser.media.subtitle.PetalSubtitleEngine
import com.petal.browser.media.subtitle.SubtitleCue
import com.petal.browser.media.subtitle.PetalSubtitleConfigSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.liquidGlassChrome
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Petal Video Player Overlay (Completely Rebuilt)
 * Features:
 * - Dynamic Style switching (Material 3 Expressive vs Liquid Glass) via Settings (`sp_native_player_style`)
 * - REX-Player Inspired Controls: Slide-to-Unlock, Animated Double-Tap Seek Waves, Spring Volume/Brightness Vertical Sliders
 * - Soul Browser Web-Media Bidirectional Sync (Seek, Play/Pause, Speed, Aspect, Duration)
 * - Pure Jetpack Compose with zero lag & complete haptic feedback integration
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
    onAspectRatioToggle: ((String) -> Unit)? = null,
    onPipClick: () -> Unit,
    onCloseFullscreen: () -> Unit,
    videoUrl: String? = null,
    onCastClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val isLiquidGlass = remember(sp) {
        sp.getString("sp_native_player_style", "LIQUID_GLASS") == "LIQUID_GLASS"
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    val topClearance = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding().coerceAtLeast(16.dp)
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding().coerceAtLeast(16.dp)

    // Player State
    var areControlsVisible by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(playbackSpeed) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    var showAspectSheet by remember { mutableStateOf(false) }
    var currentAspectModeId by remember { mutableStateOf("FIT") }

    // Video Brightness State
    var videoBrightness by remember { mutableFloatStateOf(1.0f) }

    // Gesture HUD Overlays
    var volumeHudLevel by remember { mutableIntStateOf(-1) }
    var brightnessHudLevel by remember { mutableFloatStateOf(-1f) }
    var doubleTapSeekText by remember { mutableStateOf<String?>(null) }
    var doubleTapIsForward by remember { mutableStateOf(true) }
    var aspectRatioHudText by remember { mutableStateOf<String?>(null) }
    // Subtitle & 2x Speed Boost State
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var loadedSubtitles by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleFileName by remember { mutableStateOf<String?>(null) }
    var subtitleDelayMs by remember { mutableStateOf(0L) }
    var subtitleFontSizeSp by remember { mutableFloatStateOf(18f) }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var isLongPressBoostActive by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableFloatStateOf(playbackSpeed) }

    // Auto-hide controls after 4 seconds of inactivity when playing and not locked
    LaunchedEffect(areControlsVisible, isPlaying, isControlsLocked) {
        if (areControlsVisible && isPlaying && !isControlsLocked) {
            delay(4000)
            areControlsVisible = false
            showSpeedSelector = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── 1. Gesture Layer (Captures taps, double-taps, brightness & volume drags) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isControlsLocked) {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = !areControlsVisible
                            if (!areControlsVisible) showSpeedSelector = false
                        },
                        onDoubleTap = { offset ->
                            if (isControlsLocked) return@detectTapGestures
                            val width = size.width
                            if (offset.x < width * 0.4f) {
                                onRewind()
                                doubleTapIsForward = false
                                doubleTapSeekText = "-10s"
                                PetalHapticEngine.getInstance(context).playClick(context)
                            } else if (offset.x > width * 0.6f) {
                                onFastForward()
                                doubleTapIsForward = true
                                doubleTapSeekText = "+10s"
                                PetalHapticEngine.getInstance(context).playClick(context)
                            } else {
                                onPlayPauseToggle()
                                PetalHapticEngine.getInstance(context).playClick(context)
                            }
                        },
                        onLongPress = {
                            if (!isControlsLocked && isPlaying) {
                                speedBeforeBoost = currentSpeed
                                isLongPressBoostActive = true
                                onSpeedChange(2.0f)
                                PetalHapticEngine.getInstance(context).playHeavyClick(context)
                            }
                        },
                        onPress = {
                            try {
                                awaitRelease()
                            } finally {
                                if (isLongPressBoostActive) {
                                    isLongPressBoostActive = false
                                    onSpeedChange(speedBeforeBoost)
                                }
                            }
                        }
                    )
                }
                .pointerInput(isControlsLocked) {
                    if (isControlsLocked) return@pointerInput
                    detectDragGestures(
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
                            // Left side vertical swipe: Brightness
                            val newBrightness = (videoBrightness + (deltaY / 600f)).coerceIn(0.1f, 1.0f)
                            videoBrightness = newBrightness
                            brightnessHudLevel = newBrightness
                        } else if (x > width * 0.55f) {
                            // Right side vertical swipe: Volume
                            audioManager?.let { am ->
                                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val step = if (deltaY > 0) 1 else -1
                                if (abs(deltaY) > 18f) {
                                    val newVol = (currentVol + step).coerceIn(0, maxVolume)
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    volumeHudLevel = newVol
                                }
                            }
                        }
                    }
                },
        )

        // ── 2. Screen Brightness Dimmer Overlay ──────────────────────────────
        if (videoBrightness < 1.0f) {
            val dimAlpha = ((1.0f - videoBrightness) * 0.85f).coerceIn(0f, 0.85f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
            )
        }

        // ── 3. REX-Style Double-Tap Seek Animation HUD ──────────────────────
        LaunchedEffect(doubleTapSeekText) {
            if (doubleTapSeekText != null) {
                delay(750)
                doubleTapSeekText = null
            }
        }
        if (doubleTapSeekText != null) {
            Box(
                modifier = Modifier.align(if (doubleTapIsForward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PetalDoubleTapSeekTriangles(isForward = doubleTapIsForward)
                    Text(
                        text = doubleTapSeekText ?: "",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── 3.5. 2x Speed Boost HUD Indicator (Soul / YouTube Style) ────────
        if (isLongPressBoostActive) {
            val boostShape = RoundedCornerShape(24.dp)
            Surface(
                shape = boostShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topClearance + 20.dp)
                    .clip(boostShape)
                    .then(
                        if (isLiquidGlass) Modifier.liquidGlassChrome(boostShape, enabled = true, sheenIntensity = 0.9f)
                        else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), boostShape)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "2X SPEED",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ── 3.6. Subtitle Overlay Renderer ───────────────────────────────────
        if (loadedSubtitles.isNotEmpty()) {
            val activeCue = remember(positionMs, subtitleDelayMs, loadedSubtitles) {
                PetalSubtitleEngine.getActiveCue(loadedSubtitles, positionMs, subtitleDelayMs)
            }
            if (activeCue != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (areControlsVisible) bottomClearance + 76.dp else bottomClearance + 24.dp)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeCue.text,
                        color = subtitleTextColor,
                        fontSize = subtitleFontSizeSp.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black,
                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        ),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // ── 4. Aspect Ratio Indicator ────────────────────────────────────────
        LaunchedEffect(aspectRatioHudText) {
            if (aspectRatioHudText != null) {
                delay(1000)
                aspectRatioHudText = null
            }
        }
        if (aspectRatioHudText != null) {
            val shape = CircleShape
            val hudModifier = if (isLiquidGlass) {
                Modifier
                    .background(Color.Black.copy(alpha = 0.55f), shape)
                    .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.9f)
            } else {
                Modifier
                    .background(Color.Black.copy(alpha = 0.85f), shape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            }
            Surface(
                shape = shape,
                color = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(shape)
                    .then(hudModifier)
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.FitScreen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = aspectRatioHudText ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        // ── 5. Vertical Brightness & Volume HUD ─────────────────────────────
        if (brightnessHudLevel >= 0f) {
            PetalBrightnessHud(
                brightness = brightnessHudLevel,
                isLiquidGlass = isLiquidGlass,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp),
            )
        }

        if (volumeHudLevel >= 0) {
            PetalVolumeHud(
                volume = volumeHudLevel,
                maxVolume = maxVolume,
                isLiquidGlass = isLiquidGlass,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp),
            )
        }

        // ── 6. Locked State: Slide to Unlock Capsule ────────────────────────
        if (isControlsLocked) {
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomClearance + 24.dp),
            ) {
                PetalSlideToUnlock(
                    isLiquidGlass = isLiquidGlass,
                    onUnlock = {
                        isControlsLocked = false
                        areControlsVisible = true
                        PetalHapticEngine.getInstance(context).playClick(context)
                    },
                )
            }
        }

        // ── 7. Primary Interactive Controls (Top Bar, Center Play, Bottom Bar) ──
        AnimatedVisibility(
            visible = areControlsVisible && !isControlsLocked,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top & Bottom Gradient Scrims for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            ),
                        ),
                )

                // ── Top Bar: Title, Speed, Aspect, Lock, PiP, Cast, Close ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = topClearance, start = 16.dp, end = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        ) {
                            IconButton(onClick = onCloseFullscreen) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title.ifBlank { stringResource(R.string.app_name) },
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // Speed jumping capsule
                            PetalSpeedCapsule(
                                speed = currentSpeed,
                                isLiquidGlass = isLiquidGlass,
                                onClick = { showSpeedSelector = !showSpeedSelector },
                            )

                            // Aspect Ratio
                            IconButton(onClick = { showAspectSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.FitScreen,
                                    contentDescription = stringResource(R.string.ui_aspect_ratio),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            // Subtitles & Sync
                            IconButton(onClick = { showSubtitleSheet = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Subtitles,
                                    contentDescription = "Subtitles",
                                    tint = if (loadedSubtitles.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            // Lock Player Controls
                            IconButton(onClick = {
                                isControlsLocked = true
                                PetalHapticEngine.getInstance(context).playClick(context)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.ui_player_locked),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            // Cast Button
                            if (onCastClick != null) {
                                IconButton(onClick = { onCastClick.invoke() }) {
                                    Icon(
                                        imageVector = Icons.Default.Cast,
                                        contentDescription = stringResource(R.string.ui_cast_to_device),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            // Picture in Picture
                            IconButton(onClick = onPipClick) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }

                    // Horizontal Speed Quick Selector Bar
                    AnimatedVisibility(visible = showSpeedSelector) {
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
                        val selectorShape = RoundedCornerShape(20.dp)
                        val selectorModifier = if (isLiquidGlass) {
                            Modifier
                                .background(Color.Black.copy(alpha = 0.55f), selectorShape)
                                .liquidGlassChrome(selectorShape, enabled = true, sheenIntensity = 0.8f)
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f), selectorShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), selectorShape)
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(selectorShape)
                                .then(selectorModifier)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                speeds.forEach { spd ->
                                    val isSelected = currentSpeed == spd
                                    val pillShape = RoundedCornerShape(12.dp)
                                    val pillModifier = if (isSelected) {
                                        if (isLiquidGlass) {
                                            Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), pillShape)
                                                .liquidGlassChrome(pillShape, enabled = true, sheenIntensity = 0.9f)
                                        } else {
                                            Modifier.background(MaterialTheme.colorScheme.primary, pillShape)
                                        }
                                    } else {
                                        Modifier.background(Color.Transparent, pillShape)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(pillShape)
                                            .then(pillModifier)
                                            .clickable {
                                                currentSpeed = spd
                                                onSpeedChange(spd)
                                                showSpeedSelector = false
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = "${spd}x",
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Center Action Controls (Rewind 10s, Big Play/Pause, Fast-Forward 10s) ──
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sideBtnShape = CircleShape
                    val sideBtnModifier = if (isLiquidGlass) {
                        Modifier
                            .background(Color.Black.copy(alpha = 0.5f), sideBtnShape)
                            .liquidGlassChrome(sideBtnShape, enabled = true, sheenIntensity = 0.8f)
                    } else {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f), sideBtnShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), sideBtnShape)
                    }

                    IconButton(
                        onClick = {
                            onRewind()
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(sideBtnShape)
                            .then(sideBtnModifier),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = stringResource(R.string.ui_rewind_10s),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    val playBtnShape = CircleShape
                    val playBtnModifier = if (isLiquidGlass) {
                        Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), playBtnShape)
                            .liquidGlassChrome(playBtnShape, enabled = true, sheenIntensity = 1.0f)
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.primary, playBtnShape)
                    }

                    IconButton(
                        onClick = {
                            onPlayPauseToggle()
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(playBtnShape)
                            .then(playBtnModifier),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp),
                        )
                    }

                    IconButton(
                        onClick = {
                            onFastForward()
                            PetalHapticEngine.getInstance(context).playClick(context)
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(sideBtnShape)
                            .then(sideBtnModifier),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = stringResource(R.string.ui_forward_10s),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // ── Bottom Seekbar & Controls Area ──────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomClearance)
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

        // ── Aspect Ratio Sheet ──────────────────────────────────────────────
        if (showAspectSheet) {
            PetalAspectRatioSheet(
                currentModeId = currentAspectModeId,
                onSelectMode = { option ->
                    currentAspectModeId = option.id
                    aspectRatioHudText = option.label
                    onAspectRatioToggle?.invoke(option.id)
                    showAspectSheet = false
                },
                onDismissRequest = { showAspectSheet = false },
            )
        }

        // ── Subtitle & Sync Sheet ────────────────────────────────────────────
        if (showSubtitleSheet) {
            PetalSubtitleConfigSheet(
                isLiquidGlass = isLiquidGlass,
                subtitleDelayMs = subtitleDelayMs,
                subtitleFontSizeSp = subtitleFontSizeSp,
                subtitleTextColor = subtitleTextColor,
                onSubtitleDelayChange = { subtitleDelayMs = it },
                onSubtitleFontSizeChange = { subtitleFontSizeSp = it },
                onSubtitleTextColorChange = { subtitleTextColor = it },
                onSubtitlesLoaded = { cues, name ->
                    loadedSubtitles = cues
                    subtitleFileName = name
                    showSubtitleSheet = false
                },
                onClearSubtitles = {
                    loadedSubtitles = emptyList()
                    subtitleFileName = null
                    showSubtitleSheet = false
                },
                hasActiveSubtitles = loadedSubtitles.isNotEmpty(),
                activeSubtitleName = subtitleFileName,
                onDismissRequest = { showSubtitleSheet = false }
            )
        }
    }
}
