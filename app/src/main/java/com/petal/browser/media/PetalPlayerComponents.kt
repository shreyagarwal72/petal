package com.petal.browser.media

import com.petal.browser.ui.theme.ExpressiveButtonShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import java.util.Locale
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Petal Player Component Suite
 * Inspired by REX-Player and engineered for Petal Browser:
 * - Animated Double-Tap Seek View with cascading arrows
 * - Adaptive Vertical Volume & Brightness HUD Sliders (with spring width expand 22dp -> 32dp)
 * - Slide to Unlock with gesture boundary and lock/unlock icon morphing
 * - Speed indicator pill with jumping spring text animation
 */

// ── 1. Animated Double-Tap Seek Arrows ──────────────────────────────────────

@Composable
fun PetalDoubleTapSeekTriangles(
    isForward: Boolean,
    modifier: Modifier = Modifier,
) {
    val duration = 650L
    val a1 = remember { Animatable(0f) }
    val a2 = remember { Animatable(0f) }
    val a3 = remember { Animatable(0f) }

    LaunchedEffect(isForward) {
        while (true) {
            val step = (duration / 4).toInt()
            a1.animateTo(1f, animationSpec = tween(step))
            a2.animateTo(1f, animationSpec = tween(step))
            a3.animateTo(1f, animationSpec = tween(step))
            a1.animateTo(0f, animationSpec = tween(step))
            a2.animateTo(0f, animationSpec = tween(step))
            a3.animateTo(0f, animationSpec = tween(step))
        }
    }

    val rotation = if (isForward) 0f else 180f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
        modifier = modifier.rotate(rotation),
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = a1.value),
            modifier = Modifier.size(24.dp),
        )
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = a2.value),
            modifier = Modifier.size(24.dp),
        )
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = a3.value),
            modifier = Modifier.size(24.dp),
        )
    }
}

// ── 2. Vertical HUD Slider (Spring Animated 22dp -> 32dp) ───────────────────

@Composable
fun PetalVerticalSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isActive: Boolean,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier,
) {
    val coerced = value.coerceIn(range)
    val percentage = ((coerced - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    val trackWidthAnim = remember { Animatable(22f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            trackWidthAnim.animateTo(
                32f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        } else {
            delay(150)
            trackWidthAnim.animateTo(
                22f,
                animationSpec = spring(
                    dampingRatio = 0.45f,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }

    val trackWidth = trackWidthAnim.value.dp
    val trackShape = RoundedCornerShape(16.dp)

    val trackModifier = if (isLiquidGlass) {
        Modifier
            .background(Color.White.copy(alpha = 0.08f), trackShape)
            .liquidGlassChrome(trackShape, enabled = true, sheenIntensity = 0.8f)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), trackShape)
    }

    Box(
        modifier = modifier
            .height(130.dp)
            .width(36.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(trackWidth)
                .clip(trackShape)
                .then(trackModifier),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val animatedFraction by animateFloatAsState(
                targetValue = percentage,
                animationSpec = tween(120),
                label = "petal_slider_fraction",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFraction)
                    .background(
                        if (isLiquidGlass) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.primary,
                    ),
            )
        }
    }
}

@Composable
fun PetalBrightnessHud(
    brightness: Float,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val surfaceModifier = if (isLiquidGlass) {
        Modifier
            .background(petalGroupSurfaceColor(), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.9f)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f), shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(surfaceModifier)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${(brightness * 100).toInt()}%",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            PetalVerticalSlider(
                value = brightness,
                range = 0.1f..1.0f,
                isActive = true,
                isLiquidGlass = isLiquidGlass,
            )
            Icon(
                imageVector = when {
                    brightness < 0.35f -> Icons.Rounded.BrightnessLow
                    brightness < 0.70f -> Icons.Rounded.BrightnessMedium
                    else -> Icons.Rounded.BrightnessHigh
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun PetalVolumeHud(
    volume: Int,
    maxVolume: Int,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val pct = if (maxVolume > 0) ((volume.toFloat() / maxVolume.toFloat()) * 100).roundToInt() else 0

    val surfaceModifier = if (isLiquidGlass) {
        Modifier
            .background(petalGroupSurfaceColor(), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.9f)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f), shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(surfaceModifier)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "$pct%",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            PetalVerticalSlider(
                value = volume.toFloat(),
                range = 0f..maxVolume.toFloat().coerceAtLeast(1f),
                isActive = true,
                isLiquidGlass = isLiquidGlass,
            )
            Icon(
                imageVector = when {
                    volume == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
                    volume < (maxVolume / 3) -> Icons.AutoMirrored.Rounded.VolumeMute
                    volume < (maxVolume * 2 / 3) -> Icons.AutoMirrored.Rounded.VolumeDown
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── 3. Slide to Unlock Pill (REX-Player Pattern) ─────────────────────────────

@Composable
fun PetalSlideToUnlock(
    onUnlock: () -> Unit,
    isLiquidGlass: Boolean,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val sliderSize = 52.dp
    val offsetX = remember { Animatable(0f) }

    val containerShape = RoundedCornerShape(32.dp)
    val containerModifier = if (isLiquidGlass) {
        Modifier
            .background(petalGroupSurfaceColor(), containerShape)
            .liquidGlassChrome(containerShape, enabled = true, sheenIntensity = 0.9f)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f), containerShape)
    }

    val sliderShape = CircleShape
    val sliderModifier = if (isLiquidGlass) {
        Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), sliderShape)
            .liquidGlassChrome(sliderShape, enabled = true, sheenIntensity = 1.0f)
    } else {
        Modifier.background(MaterialTheme.colorScheme.primary, sliderShape)
    }

    Box(
        modifier = modifier
            .width(220.dp)
            .height(60.dp)
            .clip(containerShape)
            .then(containerModifier)
            .padding(4.dp)
            .onSizeChanged { size -> containerWidthPx = size.width.toFloat() },
    ) {
        val sliderSizePx = containerWidthPx * (52f / 212f)
        val maxOffset = if (containerWidthPx > 0f) containerWidthPx - sliderSizePx else 0f
        val unlockThreshold = if (maxOffset > 0f) maxOffset * 0.82f else Float.MAX_VALUE

        // Background hint text that fades as slider progresses
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 50.dp)
                .alpha(if (maxOffset > 0f) 1f - (offsetX.value / maxOffset).coerceIn(0f, 1f) else 1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ui_slide_to_unlock),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        val progress = if (maxOffset > 0f) (offsetX.value / maxOffset).coerceIn(0f, 1f) else 0f
        val showUnlocked = progress > 0.5f

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(sliderSize)
                .clip(sliderShape)
                .then(sliderModifier)
                .pointerInput(containerWidthPx) {
                    if (containerWidthPx <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value >= unlockThreshold) {
                                onUnlock()
                            } else {
                                coroutineScope.launch {
                                    offsetX.animateTo(0f, tween(250))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { offsetX.animateTo(0f, tween(250)) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val next = (offsetX.value + dragAmount).coerceIn(0f, maxOffset)
                                offsetX.snapTo(next)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (showUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ── 4. Playback Speed Jumping Capsule (REX-Player Pattern) ───────────────────

@Composable
fun PetalSpeedCapsule(
    speed: Float,
    isLiquidGlass: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedText = String.format("%.2f", speed)
    val shape = RoundedCornerShape(100.dp)

    val capsuleModifier = if (isLiquidGlass) {
        Modifier
            .background(Color.White.copy(alpha = 0.08f), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.8f)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f), shape)
    }

    Surface(
        shape = shape,
        color = Color.Transparent,
        modifier = modifier
            .clip(shape)
            .then(capsuleModifier)
            .clickable { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FastForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            AnimatedContent(
                targetState = speedText,
                transitionSpec = {
                    (fadeIn(tween(100)) +
                        scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) +
                        slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    ).togetherWith(
                        fadeOut(tween(100)) +
                        scaleOut(targetScale = 1.1f, animationSpec = tween(100)) +
                        slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = tween(100))
                    ).using(SizeTransform(clip = false))
                },
                label = "SpeedCapsuleSpring",
            ) { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = target,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Text(
                        text = "x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 1.dp),
                    )
                }
            }
        }
    }
}

// ── 5. PixelPlayer-Powered Animated Playback Controls ───────────────────────

enum class PetalPlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * Material 3 Expressive Playback Controls adopted from PixelPlayer
 * (com.theveloper.pixelplay.presentation.components.player.AnimatedPlaybackControls).
 *
 * Features:
 * - Dynamic button weight expansion/compression physics on click.
 * - Corner morphing on play/pause (60dp pill ↔ 26dp expressive squircle).
 * - Smooth icon crossfade with MotionScheme.expressive.
 * - Live liquid glass chrome support (liquidGlassChrome).
 * - Full tactile haptic feedback.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalAnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
    baseWeight: Float = 1.0f,
    expansionWeight: Float = 1.10f,
    compressionWeight: Float = 0.65f,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    previousIcon: ImageVector = Icons.Rounded.SkipPrevious,
    nextIcon: ImageVector = Icons.Rounded.SkipNext,
    previousContentDescription: String = "Previous",
    nextContentDescription: String = "Next",
    isLiquidGlass: Boolean = false,
    colorOtherButtons: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    colorPlayPause: Color = MaterialTheme.colorScheme.primary,
    tintPlayPauseIcon: Color = MaterialTheme.colorScheme.onPrimary,
    tintOtherIcons: Color = MaterialTheme.colorScheme.onSurface,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 28.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PetalPlaybackButtonType?>(null) }
    var clickTrigger by remember { mutableIntStateOf(0) }
    val latestIsPlayingProvider by rememberUpdatedState(isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(lastClicked)
    val isPlayPauseLocked =
        lastClicked == PetalPlaybackButtonType.NEXT || lastClicked == PetalPlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val motionScheme = remember { MotionScheme.expressive() }
    val pressAnimationSpec = remember { motionScheme.fastSpatialSpec<Float>() }
    val defaultSpatialDpSpec = remember { motionScheme.defaultSpatialSpec<Dp>() }

    LaunchedEffect(lastClicked, clickTrigger) {
        if (lastClicked != null) {
            val delayTime = when (lastClicked) {
                PetalPlaybackButtonType.NEXT, PetalPlaybackButtonType.PREVIOUS -> 500L
                else -> releaseDelay
            }
            delay(delayTime)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }
        val shouldDelay = latestLastClicked != PetalPlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) {
            delay(releaseDelay)
        }
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fun weightFor(button: PetalPlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            // ── Previous / Rewind Button ──
            val prevWeight by animateFloatAsState(
                targetValue = weightFor(PetalPlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight",
            )
            val prevShape = RoundedCornerShape(
                topStart = 50.dp,
                bottomStart = 50.dp,
                topEnd = 6.dp,
                bottomEnd = 6.dp
            )
            val prevModifier = if (isLiquidGlass) {
                Modifier
                    .background(colorOtherButtons.copy(alpha = 0.5f), prevShape)
                    .liquidGlassChrome(prevShape, enabled = true, sheenIntensity = 0.85f, pressFx = true)
            } else {
                Modifier.background(colorOtherButtons, prevShape)
            }
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(prevShape)
                    .then(prevModifier)
                    .clickable {
                        lastClicked = PetalPlaybackButtonType.PREVIOUS
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PetalHapticEngine.getInstance(context).playClick(context)
                        coroutineScope.launch {
                            delay(140)
                            onPrevious()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = previousIcon,
                    contentDescription = previousContentDescription,
                    tint = tintOtherIcons,
                    modifier = Modifier.size(iconSize),
                )
            }

            // ── Play / Pause Corner-Morphing Button ──
            val playWeight by animateFloatAsState(
                targetValue = weightFor(PetalPlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight",
            )
            val playCorner by animateDpAsState(
                targetValue = if (!playPauseVisualState) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = defaultSpatialDpSpec,
                label = "playCorner",
            )
            val playShape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = playCorner,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = playCorner,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = playCorner,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = playCorner,
                smoothnessAsPercentBR = 60,
            )
            val playModifier = if (isLiquidGlass) {
                Modifier
                    .background(colorPlayPause.copy(alpha = 0.9f), playShape)
                    .liquidGlassChrome(playShape, enabled = true, sheenIntensity = 1.0f, pressFx = true)
            } else {
                Modifier.background(colorPlayPause, playShape)
            }
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .clip(playShape)
                    .then(playModifier)
                    .clickable {
                        lastClicked = PetalPlaybackButtonType.PLAY_PAUSE
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PetalHapticEngine.getInstance(context).playClick(context)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = playPauseVisualState,
                    animationSpec = motionScheme.fastEffectsSpec(),
                    label = "playPauseCrossfade",
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = tintPlayPauseIcon,
                        modifier = Modifier.size(playPauseIconSize),
                    )
                }
            }

            // ── Next / Forward Button ──
            val nextWeight by animateFloatAsState(
                targetValue = weightFor(PetalPlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight",
            )
            val nextShape = RoundedCornerShape(
                topStart = 6.dp,
                bottomStart = 6.dp,
                topEnd = 50.dp,
                bottomEnd = 50.dp
            )
            val nextModifier = if (isLiquidGlass) {
                Modifier
                    .background(colorOtherButtons.copy(alpha = 0.5f), nextShape)
                    .liquidGlassChrome(nextShape, enabled = true, sheenIntensity = 0.85f, pressFx = true)
            } else {
                Modifier.background(colorOtherButtons, nextShape)
            }
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(nextShape)
                    .then(nextModifier)
                    .clickable {
                        lastClicked = PetalPlaybackButtonType.NEXT
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PetalHapticEngine.getInstance(context).playClick(context)
                        coroutineScope.launch {
                            delay(140)
                            onNext()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = nextIcon,
                    contentDescription = nextContentDescription,
                    tint = tintOtherIcons,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

// ── 6. Standalone Pixel Morphing Play/Pause Button ───────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalPixelPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    cornerPlaying: Dp = 60.dp,
    cornerPaused: Dp = 26.dp,
    isLiquidGlass: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconSize: Dp = 36.dp,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val motionScheme = remember { MotionScheme.expressive() }
    val cornerAnim by animateDpAsState(
        targetValue = if (isPlaying) cornerPlaying else cornerPaused,
        animationSpec = motionScheme.defaultSpatialSpec<Dp>(),
        label = "pixelPlayPauseCorner",
    )
    val shape = RoundedCornerShape(cornerAnim)
    val btnModifier = if (isLiquidGlass) {
        Modifier
            .background(containerColor.copy(alpha = 0.9f), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 1.0f, pressFx = true)
    } else {
        Modifier.background(containerColor, shape)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(btnModifier)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                PetalHapticEngine.getInstance(context).playClick(context)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = isPlaying,
            animationSpec = motionScheme.fastEffectsSpec(),
            label = "pixelPlayPauseIconCrossfade",
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

// ── 7. Standalone Pixel Expressive Control Button ────────────────────────────

@Composable
fun PetalPixelControlButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    isLiquidGlass: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = Color.White,
    iconSize: Dp = 28.dp,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val shape = ExpressiveButtonShape
    val btnModifier = if (isLiquidGlass) {
        Modifier
            .background(containerColor.copy(alpha = 0.5f), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.85f, pressFx = true)
    } else {
        Modifier.background(containerColor, shape)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(btnModifier)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                PetalHapticEngine.getInstance(context).playClick(context)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ── 8. Pixel-Style Audio Meta Badge ──────────────────────────────────────────

@Composable
fun PetalAudioMetaBadge(
    metaText: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isLiquidGlass: Boolean = false,
) {
    if (metaText.isBlank()) return
    val shape = RoundedCornerShape(999.dp)
    val badgeModifier = if (isLiquidGlass) {
        Modifier
            .background(petalGroupSurfaceColor(), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.8f)
    } else {
        Modifier.background(textColor.copy(alpha = 0.12f), shape)
    }

    Surface(
        shape = shape,
        color = Color.Transparent,
        modifier = modifier
            .clip(shape)
            .then(badgeModifier)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = metaText,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            letterSpacing = 0.5.sp,
        )
    }
}

// ── 9. Segmented Compartment Button & Bottom Toggle Row ──────────────────────

@Composable
fun PetalToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeCornerRadius: Dp = 60.dp,
    inactiveCornerRadius: Dp = 10.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val targetBgColor = if (active) activeColor else inactiveColor
    val bgColor by animateColorAsState(
        targetValue = if (enabled) targetBgColor else targetBgColor.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 240),
        label = "segmentBg"
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else inactiveCornerRadius,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "segmentCorner"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(alpha = if (enabled) 1f else 0.38f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * Bottom Connected Segmented Compartment Pill Bar
 * Exactly as displayed in PixelPlayer and the reference screenshot:
 * A single continuous stadium pill (60dp corners) housing 3 compartments:
 * 1. Shuffle (toggle active/inactive)
 * 2. Repeat (Off / All / One)
 * 3. Favorite (heart pop bounce)
 */
@Composable
fun PetalBottomToggleRow(
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    activeColorShuffle: Color = MaterialTheme.colorScheme.primary,
    activeColorRepeat: Color = MaterialTheme.colorScheme.secondary,
    activeColorFavorite: Color = MaterialTheme.colorScheme.tertiary,
    onActiveColorShuffle: Color = MaterialTheme.colorScheme.onPrimary,
    onActiveColorRepeat: Color = MaterialTheme.colorScheme.onSecondary,
    onActiveColorFavorite: Color = MaterialTheme.colorScheme.onTertiary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
    inactiveColor: Color = Color.Transparent,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isLiquidGlass: Boolean = false,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val rowCorners = 60.dp
    val rowShape = RoundedCornerShape(rowCorners)

    // Interactive scale bounce for favorite heart
    var favClickTrigger by remember { mutableIntStateOf(0) }
    val favScale by animateFloatAsState(
        targetValue = if (favClickTrigger % 2 == 1) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "favScale"
    )

    val rowModifier = if (isLiquidGlass) {
        Modifier
            .background(containerColor, rowShape)
            .liquidGlassChrome(rowShape, enabled = true, sheenIntensity = 0.85f)
    } else {
        Modifier.background(containerColor, rowShape)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(rowShape)
            .then(rowModifier)
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            // 1. Shuffle
            PetalToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                activeColor = activeColorShuffle,
                activeContentColor = onActiveColorShuffle,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                activeCornerRadius = rowCorners,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PetalHapticEngine.getInstance(context).playClick(context)
                    onShuffleToggle()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffleEnabled) onActiveColorShuffle else inactiveContentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Repeat
            val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
            PetalToggleSegmentButton(
                modifier = commonModifier,
                active = isRepeatActive,
                activeColor = activeColorRepeat,
                activeContentColor = onActiveColorRepeat,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                activeCornerRadius = rowCorners,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PetalHapticEngine.getInstance(context).playClick(context)
                    onRepeatToggle()
                }
            ) {
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = if (isRepeatActive) onActiveColorRepeat else inactiveContentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 3. Favorite (Heart)
            PetalToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = activeColorFavorite,
                activeContentColor = onActiveColorFavorite,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                activeCornerRadius = rowCorners,
                onClick = {
                    favClickTrigger++
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PetalHapticEngine.getInstance(context).playClick(context)
                    onFavoriteToggle()
                }
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) onActiveColorFavorite else inactiveContentColor,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer(scaleX = favScale, scaleY = favScale)
                )
            }
        }
    }
}

// ── 10. Pixel-Style Player Header Top Bar ────────────────────────────────────

/**
 * Top Navigation Bar matching the PixelPlayer screenshot:
 * - Left: Rounded collapse down chevron pill button
 * - Center: Bold "Now Playing" heading
 * - Right: Cast / Audio route pill badge + Queue/Playlist asymmetrical button
 */
@Composable
fun PetalPlayerHeaderTopBar(
    onCollapse: () -> Unit,
    onQueueClick: () -> Unit,
    onAudioRouteClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Now Playing",
    audioRouteName: String? = null,
    isCastActive: Boolean = false,
    isBluetoothActive: Boolean = false,
    isLiquidGlass: Boolean = false,
    onPipClick: (() -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val pillBg = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Collapse Pill Button
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(ExpressiveButtonShape)
                .background(pillBg)
                .then(
                    if (isLiquidGlass) Modifier.liquidGlassChrome(ExpressiveButtonShape, enabled = true, sheenIntensity = 0.8f, pressFx = true)
                    else Modifier
                )
                .clickable {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PetalHapticEngine.getInstance(context).playClick(context)
                    onCollapse()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }

        // Center Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Right Actions: Route Pill + Queue Button
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Audio Route Pill
            val routeShape = RoundedCornerShape(
                topStart = 50.dp,
                topEnd = if (!audioRouteName.isNullOrBlank()) 50.dp else 8.dp,
                bottomStart = 50.dp,
                bottomEnd = if (!audioRouteName.isNullOrBlank()) 50.dp else 8.dp
            )
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))
                    .clip(routeShape)
                    .background(pillBg)
                    .then(
                        if (isLiquidGlass) Modifier.liquidGlassChrome(routeShape, enabled = true, sheenIntensity = 0.8f, pressFx = true)
                        else Modifier
                    )
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PetalHapticEngine.getInstance(context).playClick(context)
                        onAudioRouteClick()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val routeIcon = when {
                        isCastActive -> Icons.Rounded.Cast
                        isBluetoothActive -> Icons.Rounded.Bluetooth
                        else -> Icons.Rounded.Speaker
                    }
                    Icon(
                        imageVector = routeIcon,
                        contentDescription = "Audio Output",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (!audioRouteName.isNullOrBlank()) {
                        Text(
                            text = audioRouteName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }

            // Queue Asymmetric Pill Button
            val queueShape = RoundedCornerShape(topStart = 8.dp, topEnd = 50.dp, bottomStart = 8.dp, bottomEnd = 50.dp)
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 42.dp)
                    .clip(queueShape)
                    .background(pillBg)
                    .then(
                        if (isLiquidGlass) Modifier.liquidGlassChrome(queueShape, enabled = true, sheenIntensity = 0.8f, pressFx = true)
                        else Modifier
                    )
                    .clickable {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        PetalHapticEngine.getInstance(context).playClick(context)
                        onQueueClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            // PiP Button (if callback provided)
            if (onPipClick != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(ExpressiveButtonShape)
                        .background(pillBg)
                        .then(
                            if (isLiquidGlass) Modifier.liquidGlassChrome(ExpressiveButtonShape, enabled = true, sheenIntensity = 0.8f, pressFx = true)
                            else Modifier
                        )
                        .clickable {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            PetalHapticEngine.getInstance(context).playClick(context)
                            onPipClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureInPictureAlt,
                        contentDescription = "Picture in Picture",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── 11. Pixel-Style Metadata & Timing Helpers ───────────────────────────────

fun formatAudioMetaLabel(mimeType: String?, bitrate: Int?, sampleRate: Int?): String? {
    val formatLabel = when {
        mimeType == null -> null
        mimeType.contains("flac", ignoreCase = true) -> "FLAC"
        mimeType.contains("opus", ignoreCase = true) -> "OPUS"
        mimeType.contains("mp3", ignoreCase = true) || mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
        mimeType.contains("aac", ignoreCase = true) -> "AAC"
        mimeType.contains("ogg", ignoreCase = true) -> "OGG"
        mimeType.contains("wav", ignoreCase = true) -> "WAV"
        mimeType.contains("m4a", ignoreCase = true) -> "M4A"
        else -> null
    }

    val parts = buildList {
        sampleRate?.takeIf { it > 0 }?.let { add(String.format(Locale.US, "%.0f kHz", it / 1000.0)) }
        bitrate?.takeIf { it > 0 }?.let { bitrateValue ->
            val kbpsLabel = "${bitrateValue / 1000} kbps"
            if (formatLabel != null) {
                add("$kbpsLabel • $formatLabel")
            } else {
                add(kbpsLabel)
            }
        } ?: formatLabel?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

/**
 * PixelPlayer-style Efficient Time Labels row with centered audio spec badge:
 * Elapsed Time (00:11)  |  [ 48 kHz • 135 kbps • OPUS ]  |  Total Time (03:57)
 */
@Composable
fun PetalExpressiveTimeLabels(
    currentPositionMs: Long,
    totalDurationMs: Long,
    audioMetaLabel: String?,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isLiquidGlass: Boolean = false,
) {
    val posSec = (currentPositionMs / 1000L).coerceAtLeast(0L)
    val durSec = (totalDurationMs / 1000L).coerceAtLeast(0L)
    val posStr = String.format(Locale.US, "%02d:%02d", posSec / 60, posSec % 60)
    val durStr = String.format(Locale.US, "%02d:%02d", durSec / 60, durSec % 60)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = posStr,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = durStr,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }

        if (!audioMetaLabel.isNullOrBlank()) {
            val badgeShape = RoundedCornerShape(999.dp)
            val badgeModifier = if (isLiquidGlass) {
                Modifier
                    .background(petalGroupSurfaceColor(), badgeShape)
                    .liquidGlassChrome(badgeShape, enabled = true, sheenIntensity = 0.8f)
            } else {
                Modifier.background(textColor.copy(alpha = 0.12f), badgeShape)
            }

            Surface(
                shape = badgeShape,
                color = Color.Transparent,
                modifier = Modifier
                    .clip(badgeShape)
                    .then(badgeModifier)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = audioMetaLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}
