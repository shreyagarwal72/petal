@file:OptIn(ExperimentalMaterial3Api::class)

package com.petal.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Petal Expressive Material 3 Slider for Display & Scaling settings:
 * Sleek 16dp rounded pill track with responsive primary active fill,
 * smooth floating circular thumb, and responsive drag gestures.
 */
@Composable
fun PetalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0
) {
    StrideSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps
    )
}

@Composable
fun StrideSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            activeTickColor = MaterialTheme.colorScheme.onPrimary,
            inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .shadow(4.dp, CircleShape)
                    .background(
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        },
        track = { sliderState ->
            val fraction = (sliderState.value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    )
}
