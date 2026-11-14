package com.petal.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.expressive.sliders.FancySlider
import com.petal.browser.ui.components.expressive.sliders.HyperOSSlider
import com.petal.browser.ui.components.expressive.sliders.M2Slider
import com.petal.browser.ui.components.expressive.sliders.M3Slider
import com.petal.browser.ui.components.expressive.sliders.fancyThumbShape
import com.petal.browser.ui.components.expressive.support.LocalSettingsState
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.layout.PetalSliderType
import com.petal.browser.ui.theme.layout.pillShape

/**
 * Petal's slider. Draws the style the user picked ([PetalSliderType]) with the real ImageToolbox
 * implementations, live: change the choice and every slider on screen restyles immediately.
 * When Liquid Glass is active every slider is automatically seated in a live glass track.
 *
 * [drawContainer] adds ImageToolbox's own tonal container; leave it off inside Petal rows/cards,
 * which already provide the containment surface.
 */
@Composable
fun PetalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    drawContainer: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    typeOverride: PetalSliderType? = null,
    glassOverride: Boolean? = null,
) {
    val settings = LocalSettingsState.current
    val isGlass = glassOverride ?: isLiquidGlassEnabled("sp_liquid_glass_containments")
    val isMiuix = com.petal.browser.ui.containment.isMiuixEnabled()
    val sliderType = typeOverride ?: if (isMiuix) PetalSliderType.HYPEROS else settings.sliderType
    val sheen = rememberGlassSheen(isGlass)
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    val colors: SliderColors = when (sliderType) {
        PetalSliderType.FANCY -> SliderDefaults.colors(
            activeTickColor = scheme.inverseSurface,
            inactiveTickColor = scheme.surface,
            activeTrackColor = scheme.primaryContainer,
            inactiveTrackColor = if (isGlass) Color.White.copy(alpha = 0.16f)
            else SwitchDefaults.colors().disabledCheckedTrackColor,
            disabledThumbColor = SliderDefaults.colors().disabledThumbColor,
            disabledActiveTrackColor = SliderDefaults.colors().disabledActiveTrackColor,
            thumbColor = scheme.onPrimaryContainer
        )
        else -> SliderDefaults.colors()
    }

    // Tick haptics for stepped sliders, like ImageToolbox.
    if (steps != 0) {
        var compositions by remember { mutableIntStateOf(0) }
        val updated by rememberUpdatedState(value)
        LaunchedEffect(updated) {
            if (compositions > 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            compositions++
        }
    }

    // Ease programmatic changes, but follow the finger exactly while it is down.
    val dragging by interactionSource.collectIsDraggedAsState()
    val pressing by interactionSource.collectIsPressedAsState()
    val eased by animateFloatAsState(value, tween(100), label = "sliderValue")
    val shown = if (dragging || pressing) value else eased

    val body: @Composable (Modifier) -> Unit = { mod ->
        when (sliderType) {
            PetalSliderType.FANCY -> FancySlider(
                value = shown,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                thumbShape = fancyThumbShape(),
                modifier = mod,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                drawContainer = drawContainer
            )
            PetalSliderType.MATERIAL_YOU -> M3Slider(
                value = shown,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                modifier = mod,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                drawContainer = drawContainer
            )
            PetalSliderType.MATERIAL -> M2Slider(
                value = shown,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                modifier = mod,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                drawContainer = drawContainer
            )
            PetalSliderType.HYPEROS -> HyperOSSlider(
                value = shown,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                modifier = mod,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                drawContainer = drawContainer
            )
        }
    }

    if (isGlass) {
        val shape = settings.shapeType.pillShape()
        Box(
            modifier = modifier.liquidGlassChrome(
                shape = shape,
                enabled = true,
                sheenIntensity = sheen * 0.75f,
                pressFx = false
            )
        ) {
            body(Modifier.padding(horizontal = 4.dp))
        }
    } else {
        body(modifier)
    }
}
