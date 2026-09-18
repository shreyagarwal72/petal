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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Expressive Circular Wavy Progress Indicator.
 *
 * Implements the official Material 3 Expressive wavy circular progress indicator
 * (as seen in RvSystem-Monitor MemoryStorageProgressRow).
 *
 * Provides smooth determinate progress or continuously rotating wavy ripples when indeterminate.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalCircularWavyProgressIndicator(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    size: Dp = 44.dp,
    strokeWidth: Dp = 4.dp,
    wavelength: Dp = 18.dp,
    waveAmplitude: Dp = 2.5.dp
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val customStroke = remember(density, strokeWidth) {
        with(density) {
            Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (progress != null) {
            // Determinate Wavy Circular Progress
            CircularWavyProgressIndicator(
                progress = progress,
                color = color,
                trackColor = trackColor,
                stroke = customStroke,
                trackStroke = customStroke,
                wavelength = wavelength,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Indeterminate Wavy Circular Progress
            CircularWavyProgressIndicator(
                color = color,
                trackColor = trackColor,
                stroke = customStroke,
                trackStroke = customStroke,
                wavelength = wavelength,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
