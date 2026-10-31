package com.petal.browser.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Expressive Circular Wavy Progress Indicator (ported from komi-store).
 *
 * Implements the Material 3 Expressive circular wavy progress indicator (a ring with
 * a darker track and a wavy moving arc) used as the universal loading indicator throughout Petal.
 *
 * Indeterminate when [progress] is null, determinate when a [progress] lambda is passed.
 * For sizes >= 24.dp, matches komi-store defaults (default stroke, track, and wavelength).
 * For small sizes under 24.dp (e.g. 14, 16, 18, 20 dp inside buttons, chips, and rows),
 * passes a thinner stroke of about 2 dp and a shorter wavelength so the wave still reads and
 * nothing looks clipped.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalCircularWavyProgressIndicator(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Unspecified,
    size: Dp = 40.dp,
    strokeWidth: Dp? = null,
    wavelength: Dp? = null,
    waveAmplitude: Dp? = null
) {
    val isSmall = size < 24.dp
    val density = LocalDensity.current
    val hasCustomStroke = isSmall || strokeWidth != null || wavelength != null

    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = "Loading"
            },
        contentAlignment = Alignment.Center
    ) {
        if (hasCustomStroke) {
            val effStrokeWidth = strokeWidth ?: if (isSmall) 2.dp else 4.dp
            val effWavelength = wavelength ?: if (isSmall) 10.dp else 20.dp
            val stroke = remember(density, effStrokeWidth) {
                with(density) {
                    Stroke(
                        width = effStrokeWidth.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            if (progress != null) {
                if (trackColor != Color.Unspecified) {
                    CircularWavyProgressIndicator(
                        progress = progress,
                        color = color,
                        trackColor = trackColor,
                        stroke = stroke,
                        trackStroke = stroke,
                        wavelength = effWavelength,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularWavyProgressIndicator(
                        progress = progress,
                        color = color,
                        stroke = stroke,
                        trackStroke = stroke,
                        wavelength = effWavelength,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                if (trackColor != Color.Unspecified) {
                    CircularWavyProgressIndicator(
                        color = color,
                        trackColor = trackColor,
                        stroke = stroke,
                        trackStroke = stroke,
                        wavelength = effWavelength,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularWavyProgressIndicator(
                        color = color,
                        stroke = stroke,
                        trackStroke = stroke,
                        wavelength = effWavelength,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // For 24 dp and above: use komi-store defaults
            if (progress != null) {
                if (trackColor != Color.Unspecified) {
                    CircularWavyProgressIndicator(
                        progress = progress,
                        color = color,
                        trackColor = trackColor,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularWavyProgressIndicator(
                        progress = progress,
                        color = color,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                if (trackColor != Color.Unspecified) {
                    CircularWavyProgressIndicator(
                        color = color,
                        trackColor = trackColor,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularWavyProgressIndicator(
                        color = color,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
