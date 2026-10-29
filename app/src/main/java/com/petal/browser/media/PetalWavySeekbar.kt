/*
 * PetalWavySeekbar.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Wavy Seekbar adopted from PixelPlayer
 * (com.theveloper.pixelplay.presentation.components.WavySliderExpressive).
 * Features:
 * - LinearWavyProgressIndicator from Material 3 Expressive.
 * - Dynamic animated amplitude (flowing when playing, flattening on pause/scrub).
 * - Morphing thumb (circle dot 8dp ↔ tall rounded pill 24dp) with dynamic gap size.
 * - 60/120Hz frame-clock interpolation via withFrameNanos (zero recomposition lag).
 * - Interactive timers (elapsed + total/inverted remaining).
 * - Scrubber tooltip time bubble during drag gestures.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import com.petal.browser.ui.containment.liquidGlassChrome
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * PixelPlayer-Derived Wavy Slider Expressive.
 * Pure Material 3 Expressive linear wavy progress bar with morphing thumb.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressive(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    trackEdgePadding: Dp = thumbRadius,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f,
    onScrubStateChange: ((Boolean) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    val normalizedValueState = remember(valueRange) {
        derivedStateOf {
            val v = value()
            if (valueRange.endInclusive == valueRange.start) 0f
            else ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        }
    }

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValueState = remember(safeSemanticsStep) {
        derivedStateOf {
            val norm = normalizedValueState.value
            ((norm / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
        }
    }
    val semanticSliderValueState = remember(valueRange) {
        derivedStateOf {
            valueRange.start + semanticNormalizedValueState.value * (valueRange.endInclusive - valueRange.start)
        }
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking

    LaunchedEffect(isInteracting) {
        onScrubStateChange?.invoke(isInteracting)
    }

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim",
    )
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (enabled && isPlaying && !isInteracting) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude",
    )

    val currentHalfWidth = remember(thumbRadius, strokeWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val radius = thumbRadius
            val halfStroke = strokeWidth * 0.6f
            radius * (1f - fraction) + halfStroke * fraction
        }
    }

    val dynamicGapSize = remember {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val idleGap = 6.dp
            val draggingGap = currentHalfWidth.value + 1.2.dp
            idleGap + (draggingGap - idleGap) * fraction
        }
    }

    // Keep visual progress interpolation out of composition:
    // update this state on frame clock, then consume it inside draw lambdas.
    val renderedNormalizedProgress = remember {
        val initialVal = value()
        val initialNorm = if (valueRange.endInclusive == valueRange.start) 0f
        else ((initialVal - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        mutableFloatStateOf(initialNorm)
    }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isInteracting, enabled) {
        snapshotFlow { normalizedValueState.value }.collect { target ->
            if (!enabled || isInteracting) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val start = renderedNormalizedProgress.floatValue
            if (abs(start - target) > 0.1f) {
                renderedNormalizedProgress.floatValue = target
                lastProgressUpdateNanos = System.nanoTime()
                return@collect
            }

            val nowNanos = System.nanoTime()
            val intervalMs = if (lastProgressUpdateNanos == 0L) {
                180L
            } else {
                ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceIn(1L, 250L)
            }
            lastProgressUpdateNanos = nowNanos

            if (abs(start - target) <= 0.0001f) {
                renderedNormalizedProgress.floatValue = target
                return@collect
            }

            val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
            var startFrameNanos = 0L
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                if (startFrameNanos == 0L) startFrameNanos = frameNanos
                val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
                val fraction = (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
                renderedNormalizedProgress.floatValue = start + (target - start) * fraction
                if (fraction >= 1f) break
            }
            renderedNormalizedProgress.floatValue = target
        }
    }

    val containerHeight = max(
        WavyProgressIndicatorDefaults.LinearContainerHeight,
        max(thumbRadius * 2, thumbLineHeightWhenInteracting)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) {
                    contentDescription = semanticsLabel
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = semanticSliderValueState.value,
                    range = valueRange.start..valueRange.endInclusive,
                    steps = 0,
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                        latestOnValueChange(coerced)
                        latestOnValueCommit?.invoke(coerced)
                            ?: latestOnValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isVisible) {
            LinearWavyProgressIndicator(
                progress = { renderedNormalizedProgress.floatValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = trackEdgePadding.coerceAtLeast(0.dp))
                    .clearAndSetSemantics { },
                color = activeTrackColor,
                trackColor = inactiveTrackColor,
                stroke = stroke,
                trackStroke = stroke,
                gapSize = 2f * dynamicGapSize.value * (1.0f + 0.1573f * animatedAmplitude * animatedAmplitude),
                stopSize = 3.dp,
                amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
                wavelength = wavelength,
                waveSpeed = waveSpeed,
            )
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(containerHeight))
        }

        // Draw the Pixel morphing thumb
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isVisible) return@Canvas
            val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
            val trackStart = edgePaddingPx
            val trackEnd = size.width - edgePaddingPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2f
            val renderedProgress = renderedNormalizedProgress.floatValue

            fun lerp(start: Float, stop: Float, fraction: Float): Float {
                return start + (stop - start) * fraction
            }

            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            val rawThumbX = trackStart + (trackWidth * renderedProgress)
            val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
            val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
            val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(
                    thumbX - currentWidth / 2f,
                    thumbY - currentHeight / 2f,
                ),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f),
            )
        }

        // Gesture detector (awaitEachGesture from PixelPlayer for zero touch conflict)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, valueRange, trackEdgePaddingPx) {
                    if (!enabled) return@pointerInput

                    fun valueForX(rawX: Float): Float {
                        val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                        val trackStart = edgePadding
                        val trackEnd = size.width - edgePadding
                        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                        val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                        return valueRange.start + normalized * (valueRange.endInclusive - valueRange.start)
                    }

                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPointerSeeking = true
                            down.consume()
                            var latestGestureValue = valueForX(down.position.x)
                            latestOnValueChange(latestGestureValue)

                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: break

                                pointerId = change.id
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }

                                if (change.position != change.previousPosition) {
                                    change.consume()
                                    latestGestureValue = valueForX(change.position.x)
                                    latestOnValueChange(latestGestureValue)
                                }
                            }

                            latestOnValueCommit?.invoke(latestGestureValue)
                                ?: latestOnValueChangeFinished?.invoke()
                        } finally {
                            isPointerSeeking = false
                        }
                    }
                },
        )
    }
}

/**
 * Pixel Player Seekbar with Timers and Floating Scrub Tooltip.
 * Compatible wrapper used across Petal video player, website player, and audio player.
 */
@Composable
fun PetalSeekbarWithTimers(
    positionMs: Long,
    durationMs: Long,
    isPaused: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    isLiquidGlass: Boolean = true,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val durationSeconds = (durationMs.toFloat() / 1000f).coerceAtLeast(0f)
    val positionSeconds = (positionMs.toFloat() / 1000f).coerceIn(0f, durationSeconds.coerceAtLeast(0.1f))

    var isUserInteracting by remember { mutableStateOf(false) }
    var userPositionSeconds by remember { mutableFloatStateOf(positionSeconds) }
    var isInvertedTimer by remember { mutableStateOf(false) }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val lastHapticStep = remember { intArrayOf(-1) }

    val currentSeconds = if (isUserInteracting) userPositionSeconds else positionSeconds

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Floating Scrubber Time Bubble (Appears over thumb when dragging, Pixel-style)
        val scrubProgress = if (durationSeconds > 0f) (userPositionSeconds / durationSeconds).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isUserInteracting,
                enter = androidx.compose.animation.fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        androidx.compose.animation.scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = androidx.compose.animation.fadeOut(tween(120)) +
                        androidx.compose.animation.scaleOut(targetScale = 0.8f, animationSpec = tween(120)),
                modifier = Modifier.offset {
                    val thumbPx = (scrubProgress * barWidthPx).toInt()
                    IntOffset(x = thumbPx.coerceIn(0, (barWidthPx - 160f).toInt().coerceAtLeast(0)), y = 0)
                },
            ) {
                val bubbleShape = RoundedCornerShape(12.dp)
                Surface(
                    shape = bubbleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .clip(bubbleShape)
                        .then(
                            if (isLiquidGlass) Modifier.liquidGlassChrome(bubbleShape, enabled = true, sheenIntensity = 0.85f)
                            else Modifier
                        ),
                ) {
                    Text(
                        text = formatTime(userPositionSeconds),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Elapsed Time
            PetalVideoTimer(
                seconds = currentSeconds,
                isInverted = false,
                onClick = {},
                modifier = Modifier.width(60.dp),
            )

            // Center WavySliderExpressive from PixelPlayer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .onSizeChanged { barWidthPx = it.width.toFloat() }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                WavySliderExpressive(
                    value = { if (isUserInteracting) userPositionSeconds else positionSeconds },
                    onValueChange = { newSec ->
                        isUserInteracting = true
                        userPositionSeconds = newSec
                        val quantized = (if (durationSeconds > 0) (newSec / durationSeconds) * 20f else 0f).toInt()
                        if (quantized != lastHapticStep[0]) {
                            lastHapticStep[0] = quantized
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onValueCommit = { finalSec ->
                        userPositionSeconds = finalSec
                        onSeek((finalSec * 1000f).roundToLong())
                        isUserInteracting = false
                    },
                    valueRange = 0f..durationSeconds.coerceAtLeast(0.1f),
                    activeTrackColor = activeColor,
                    inactiveTrackColor = inactiveColor,
                    thumbColor = activeColor,
                    isPlaying = !isPaused,
                    onScrubStateChange = { isUserInteracting = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Duration / Inverted Remaining Time
            PetalVideoTimer(
                seconds = if (isInvertedTimer) (currentSeconds - durationSeconds) else durationSeconds,
                isInverted = isInvertedTimer,
                onClick = { isInvertedTimer = !isInvertedTimer },
                modifier = Modifier.width(60.dp),
            )
        }
    }
}

/**
 * Backward-compatible mapper for PetalSquigglySeekbar.
 */
@Composable
fun PetalSquigglySeekbar(
    position: Float,
    duration: Float,
    isPaused: Boolean,
    isScrubbing: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    bufferedFraction: Float = 0f,
    bufferColor: Color = Color.White.copy(alpha = 0.45f),
) {
    WavySliderExpressive(
        value = { position },
        onValueChange = {},
        valueRange = 0f..duration.coerceAtLeast(0.1f),
        activeTrackColor = activeColor,
        inactiveTrackColor = inactiveColor,
        thumbColor = activeColor,
        isPlaying = !isPaused,
        enabled = false,
        modifier = modifier,
    )
}

@Composable
fun PetalVideoTimer(
    seconds: Float,
    isInverted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSec = abs(seconds.toInt())
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val secs = totalSec % 60
    val prefix = if (isInverted && seconds < 0f) "-" else ""
    val timeText = if (hours > 0) {
        String.format(Locale.getDefault(), "%s%d:%02d:%02d", prefix, hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%s%02d:%02d", prefix, minutes, secs)
    }

    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = timeText,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick,
            )
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

private fun formatTime(seconds: Float): String {
    val totalSec = abs(seconds.toInt())
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
}
