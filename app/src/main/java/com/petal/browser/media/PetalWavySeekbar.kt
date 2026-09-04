package com.petal.browser.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Petal Wavy Seekbar with interactive timers.
 * Ported and adapted from mpvEx (SquigglySeekbar + SeekbarWithTimers) in pure Jetpack Compose.
 */
@Composable
fun PetalSeekbarWithTimers(
    positionMs: Long,
    durationMs: Long,
    isPaused: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationSeconds = (durationMs.toFloat() / 1000f).coerceAtLeast(0f)
    val positionSeconds = (positionMs.toFloat() / 1000f).coerceIn(0f, durationSeconds.coerceAtLeast(0.1f))

    var isUserInteracting by remember { mutableStateOf(false) }
    var userPositionSeconds by remember { mutableFloatStateOf(positionSeconds) }
    var isInvertedTimer by remember { mutableStateOf(false) }

    val animatedPosition = remember { Animatable(positionSeconds) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(positionSeconds, isUserInteracting) {
        if (!isUserInteracting && positionSeconds != animatedPosition.value) {
            scope.launch {
                animatedPosition.animateTo(
                    targetValue = positionSeconds,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = LinearEasing,
                    ),
                )
            }
        }
    }

    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PetalVideoTimer(
            seconds = if (isUserInteracting) userPositionSeconds else positionSeconds,
            isInverted = false,
            onClick = {},
            modifier = Modifier.width(64.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(durationSeconds) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (durationSeconds > 0f) {
                                    val newPos = (offset.x / size.width) * durationSeconds
                                    userPositionSeconds = newPos.coerceIn(0f, durationSeconds)
                                    onSeek((userPositionSeconds * 1000f).toLong())
                                    scope.launch {
                                        animatedPosition.snapTo(userPositionSeconds)
                                        isUserInteracting = false
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(durationSeconds) {
                        detectDragGestures(
                            onDragStart = { isUserInteracting = true },
                            onDragEnd = {
                                scope.launch {
                                    delay(40)
                                    animatedPosition.snapTo(userPositionSeconds)
                                    isUserInteracting = false
                                    onSeek((userPositionSeconds * 1000f).toLong())
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    delay(40)
                                    animatedPosition.snapTo(userPositionSeconds)
                                    isUserInteracting = false
                                }
                            },
                        ) { change, _ ->
                            change.consume()
                            if (durationSeconds > 0f) {
                                val newPos = (change.position.x / size.width) * durationSeconds
                                userPositionSeconds = newPos.coerceIn(0f, durationSeconds)
                                onSeek((userPositionSeconds * 1000f).toLong())
                            }
                        }
                    },
            )

            PetalSquigglySeekbar(
                position = if (isUserInteracting) userPositionSeconds else animatedPosition.value,
                duration = durationSeconds,
                isPaused = isPaused,
                isScrubbing = isUserInteracting,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PetalVideoTimer(
            seconds = if (isInvertedTimer) (positionSeconds - durationSeconds) else durationSeconds,
            isInverted = isInvertedTimer,
            onClick = { isInvertedTimer = !isInvertedTimer },
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
fun PetalVideoTimer(
    seconds: Float,
    isInverted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSec = kotlin.math.abs(seconds.toInt())
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

@Composable
fun PetalSquigglySeekbar(
    position: Float,
    duration: Float,
    isPaused: Boolean,
    isScrubbing: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBackgroundColor = Color.White.copy(alpha = 0.3f)

    var phaseOffset by remember { mutableFloatStateOf(0f) }
    var heightFraction by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    val waveLength = 80f
    val lineAmplitude = 6f
    val phaseSpeed = 12f

    LaunchedEffect(isPaused, isScrubbing) {
        scope.launch {
            val shouldFlatten = isPaused || isScrubbing
            val targetHeight = if (shouldFlatten) 0f else 1f
            val durationMs = if (shouldFlatten) 500 else 750

            val animator = Animatable(heightFraction)
            animator.animateTo(
                targetValue = targetHeight,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = LinearEasing,
                ),
            ) {
                heightFraction = value
            }
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) return@LaunchedEffect
        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffset += deltaTime * phaseSpeed
                phaseOffset %= waveLength
                lastFrameTime = frameTimeMillis
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        val strokeWidth = 4.dp.toPx()
        val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
        val totalWidth = size.width
        val totalProgressPx = totalWidth * progress
        val centerY = size.height / 2f

        val path = Path()
        val waveStart = -phaseOffset - waveLength / 2f
        val waveEnd = totalWidth

        path.moveTo(waveStart, centerY)

        var currentX = waveStart
        var waveSign = 1f
        val dist = waveLength / 2f

        while (currentX < waveEnd) {
            waveSign = -waveSign
            val nextX = currentX + dist
            val midX = currentX + dist / 2f
            val currentAmp = waveSign * heightFraction * lineAmplitude
            val nextAmp = -waveSign * heightFraction * lineAmplitude

            path.cubicTo(
                midX,
                centerY + currentAmp,
                midX,
                centerY + nextAmp,
                nextX,
                centerY + nextAmp,
            )
            currentX = nextX
        }

        val clipTop = lineAmplitude + strokeWidth + 4f

        fun drawPathWithGaps(startX: Float, endX: Float, color: Color) {
            if (endX <= startX) return
            clipRect(
                left = startX,
                top = centerY - clipTop,
                right = endX,
                bottom = centerY + clipTop,
            ) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        drawPathWithGaps(0f, totalProgressPx, primaryColor)
        drawPathWithGaps(totalProgressPx, totalWidth, trackBackgroundColor)

        val barHalfHeight = lineAmplitude + strokeWidth + 2f
        val barWidth = 4.5.dp.toPx()

        drawLine(
            color = primaryColor,
            start = Offset(totalProgressPx, centerY - barHalfHeight),
            end = Offset(totalProgressPx, centerY + barHalfHeight),
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
        )
    }
}
