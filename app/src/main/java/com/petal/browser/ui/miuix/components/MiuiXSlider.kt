package com.petal.browser.ui.miuix.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.petal.browser.ui.components.expressive.sliders.HyperOSSlider
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors

/**
 * Xiaomi HyperOS / MIUIX Capsule Slider.
 *
 * Full-width continuous capsule slider with embedded track line, tactile detents,
 * and high-contrast fill progress.
 */
@Composable
fun MiuiXSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val miuixColors = LocalMiuiXColors.current

    val colors: SliderColors = SliderDefaults.colors(
        thumbColor = miuixColors.primary,
        activeTrackColor = miuixColors.primary,
        inactiveTrackColor = miuixColors.track,
        disabledThumbColor = miuixColors.primary.copy(alpha = 0.5f),
        disabledActiveTrackColor = miuixColors.primary.copy(alpha = 0.5f),
        disabledInactiveTrackColor = miuixColors.track.copy(alpha = 0.5f),
    )

    HyperOSSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        colors = colors,
        steps = steps,
        drawContainer = true,
    )
}
