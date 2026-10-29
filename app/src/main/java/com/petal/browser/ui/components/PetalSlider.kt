package com.petal.browser.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val TrackHeight = 46.dp
private val ThumbSize = 30.dp
private val ThumbInset = 8.dp

/**
 * ImageToolbox-style expressive slider: a chunky pill track with a filled
 * section and a scalloped cookie thumb that rides *inside* the pill. The thumb
 * physically rolls — its rotation is derived from the distance travelled, so
 * the scallops turn like a wheel as the value changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    /** Number of discrete stops between the ends (0 = continuous). */
    steps: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.height(TrackHeight),
        // The track draws the thumb too, so it can be positioned inside the pill
        thumb = {},
        track = { state ->
            val target = (
                (state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start)
                ).coerceIn(0f, 1f)

            // When actively interacting (dragging/pressing), follow touch 1:1 with zero lag.
            // When idle (e.g. preset or programmatic update), use snappy spring animation.
            val animatedFraction by animateFloatAsState(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 1200f
                ),
                label = "sliderFraction"
            )
            val fraction = if (isDragged || isPressed) target else animatedFraction

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                val travel = maxWidth - ThumbSize - ThumbInset * 2
                val thumbStart = ThumbInset + travel * fraction

                // Rolling: one full turn per circumference travelled
                val circumference = ThumbSize.value * Math.PI.toFloat()
                val rollDegrees = (travel.value * fraction / circumference) * 360f

                // Deep filled section — its rounded cap sits just past the thumb
                val fillWidth = if (fraction <= 0.001f) 0.dp else thumbStart + ThumbSize + ThumbInset
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(fillWidth)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
                // Pale scalloped thumb, rolling as it travels
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = thumbStart)
                        .size(ThumbSize)
                        .graphicsLayer { rotationZ = rollDegrees }
                        .clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                )
            }
        }
    )
}
