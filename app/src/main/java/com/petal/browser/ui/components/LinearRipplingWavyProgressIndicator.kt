package com.petal.browser.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Linear Wavy Progress Indicator.
 * Delegates directly to the official [androidx.compose.material3.LinearWavyProgressIndicator].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LinearRipplingWavyProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    label: String? = null,
    height: Dp = 4.5.dp,
    strokeWidth: Dp = 3.5.dp,
    waveAmplitude: Dp = 3.dp,
    waveWavelength: Dp = 24.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
) {
    if (progress != null) {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth(),
            color = activeColor,
            trackColor = trackColor
        )
    } else {
        LinearWavyProgressIndicator(
            modifier = modifier.fillMaxWidth(),
            color = activeColor,
            trackColor = trackColor
        )
    }
}
