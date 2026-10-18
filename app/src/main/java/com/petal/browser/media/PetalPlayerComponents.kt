package com.petal.browser.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.ui.containment.liquidGlassChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = a1.value),
            modifier = Modifier.size(24.dp),
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = a2.value),
            modifier = Modifier.size(24.dp),
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
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
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f), trackShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), trackShape)
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
            .background(Color.Black.copy(alpha = 0.55f), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.9f)
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
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
                    brightness < 0.35f -> Icons.Default.BrightnessLow
                    brightness < 0.70f -> Icons.Default.BrightnessMedium
                    else -> Icons.Default.BrightnessHigh
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
            .background(Color.Black.copy(alpha = 0.55f), shape)
            .liquidGlassChrome(shape, enabled = true, sheenIntensity = 0.9f)
    } else {
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
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
                    volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                    volume < (maxVolume / 3) -> Icons.AutoMirrored.Filled.VolumeMute
                    volume < (maxVolume * 2 / 3) -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
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
            .background(Color.Black.copy(alpha = 0.55f), containerShape)
            .liquidGlassChrome(containerShape, enabled = true, sheenIntensity = 0.9f)
    } else {
        Modifier
            .background(Color.Black.copy(alpha = 0.75f), containerShape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), containerShape)
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
                imageVector = if (showUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
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
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
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
                imageVector = Icons.Default.FastForward,
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
