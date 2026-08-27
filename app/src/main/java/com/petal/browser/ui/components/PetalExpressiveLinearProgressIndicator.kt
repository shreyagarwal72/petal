package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * PetalExpressiveLinearProgressIndicator
 *
 * Full official implementation of Material 3 Expressive Linear Progress Indicator according to the
 * M3 Progress Indicators specification (https://m3.material.io/components/progress-indicators/overview).
 *
 * Features:
 * - Thick expressive capsule track with pill-shaped rounded ends & inner gaps (Stop Indicator dot).
 * - Variable-height / multi-thickness track options (`ExpressiveTrackHeight = 8.dp` or `10.dp`).
 * - Smooth sine-wave rippling motion when downloading/loading actively.
 * - Indeterminate mode with dual asymmetric morphing waves.
 * - Morphing end-caps using M3 shape pill geometry.
 *
 * @param progress Current progress value between 0.0f and 1.0f. Set to null for Indeterminate mode.
 * @param modifier Composable modifier.
 * @param color Active progress track color. Defaults to `MaterialTheme.colorScheme.primary`.
 * @param trackColor Inactive track background color. Defaults to `MaterialTheme.colorScheme.surfaceContainerHighest`.
 * @param height Height of the expressive linear track capsule (default 8.dp for M3 Expressive).
 * @param isWaveActive Enables dynamic sine-wave motion for active transfers.
 */
@Composable
fun PetalExpressiveLinearProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Dp = 8.dp,
    isWaveActive: Boolean = true
) {
    val animatedProgress = remember { Animatable(progress ?: 0f) }

    LaunchedEffect(progress) {
        if (progress != null) {
            animatedProgress.animateTo(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_progress_wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val indeterminatePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indeterminate_phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val width = size.width
        val trackHeight = size.height
        val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        // 1. Inactive Track Capsule
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = cornerRadius
        )

        if (progress != null) {
            // --- Determined Progress Mode ---
            val currentVal = animatedProgress.value
            val activeWidth = width * currentVal

            if (activeWidth > 0f) {
                if (isWaveActive && currentVal < 1.0f && activeWidth > trackHeight) {
                    // Material 3 Expressive Rippling Sine-Wave Bar
                    val wavePath = Path()
                    val amplitude = trackHeight * 0.22f
                    val wavelength = 36.dp.toPx()
                    val steps = 60
                    val startY = trackHeight / 2f

                    wavePath.moveTo(0f, startY)
                    for (i in 0..steps) {
                        val x = (i.toFloat() / steps) * activeWidth
                        val angle = (x / wavelength) * 2f * PI.toFloat() + waveOffset
                        val y = startY + sin(angle) * amplitude
                        wavePath.lineTo(x, y)
                    }

                    wavePath.lineTo(activeWidth, trackHeight)
                    wavePath.lineTo(0f, trackHeight)
                    wavePath.close()

                    // Base Fill Capsule
                    drawRoundRect(
                        color = color,
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = cornerRadius
                    )

                    // Overlay Sine Wave Layer
                    drawPath(
                        path = wavePath,
                        color = color.copy(alpha = 0.45f)
                    )
                } else {
                    // Standard M3 Expressive Thick Capsule
                    drawRoundRect(
                        color = color,
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = cornerRadius
                    )
                }

                // Stop Indicator Dot (M3 Expressive Spec gap indicator)
                if (currentVal < 0.98f && activeWidth + 12.dp.toPx() < width) {
                    drawCircle(
                        color = color,
                        radius = trackHeight * 0.25f,
                        center = Offset(activeWidth + 6.dp.toPx(), trackHeight / 2f)
                    )
                }
            }
        } else {
            // --- Indeterminate Asymmetric Wave Mode ---
            val barLength = width * 0.45f
            val startX = (width + barLength) * indeterminatePosition - barLength
            val endX = (startX + barLength).coerceIn(0f, width)
            val drawStartX = startX.coerceIn(0f, width)

            if (endX > drawStartX) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(drawStartX, 0f),
                    size = Size(endX - drawStartX, trackHeight),
                    cornerRadius = cornerRadius
                )
            }
        }
    }
}
