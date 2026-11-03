package com.petal.browser.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.layout.LocalPetalLayoutSettings
import com.petal.browser.ui.theme.layout.MaterialStarShape
import com.petal.browser.ui.theme.layout.PetalSliderType
import kotlin.math.roundToInt

/**
 * Expressive slider faithful to ImageToolbox's slider system.
 * Supports FancySlider (with continuous track & rotating star thumb),
 * HyperOSSlider (with 30dp embedded pill track), M3Slider, and M2Slider.
 * ALL variants dynamically support live Liquid Glass styling when active.
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
) {
    val layoutSettings = LocalPetalLayoutSettings.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)

    when (layoutSettings.sliderType) {
        PetalSliderType.FANCY -> {
            ImageToolboxFancySlider(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                valueRange = valueRange,
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
                steps = steps,
                isGlass = isGlass,
                sheen = sheen
            )
        }
        PetalSliderType.HYPEROS -> {
            ImageToolboxHyperOSSlider(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                valueRange = valueRange,
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
                isGlass = isGlass,
                sheen = sheen
            )
        }
        PetalSliderType.MATERIAL_YOU -> {
            ImageToolboxM3Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                valueRange = valueRange,
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
                isPillThumb = true,
                isGlass = isGlass,
                sheen = sheen
            )
        }
        PetalSliderType.MATERIAL -> {
            ImageToolboxM3Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                valueRange = valueRange,
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
                isPillThumb = false,
                isGlass = isGlass,
                sheen = sheen
            )
        }
    }
}

/**
 * ImageToolbox FancySlider faithful implementation:
 * - Rotating MaterialStarShape thumb (1080 deg * fraction)
 * - Continuous canvas track segments with circular caps
 * - Liquid Glass auto-chroming when active
 */
@Composable
private fun ImageToolboxFancySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    isGlass: Boolean = false,
    sheen: Float = 0f
) {
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val thumbShape = MaterialStarShape
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest
    val activeTrackColor = if (enabled) primaryColor else MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(
                if (isGlass) Modifier.liquidGlassChrome(
                    RoundedCornerShape(22.dp),
                    enabled = true,
                    sheenIntensity = sheen * 0.7f,
                    pressFx = false
                ) else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val thumbSize = 28.dp
        val horizontalPadding = 14.dp
        val travel = totalWidth - horizontalPadding * 2

        fun updateFromX(xPx: Float, density: Float) {
            val padPx = horizontalPadding.value * density
            val travelPx = travel.value * density
            val newFraction = ((xPx - padPx) / travelPx).coerceIn(0f, 1f)
            val stepFraction = if (steps > 0) {
                (newFraction * (steps + 1)).roundToInt().toFloat() / (steps + 1)
            } else newFraction
            val newValue = valueRange.start + stepFraction * rangeSpan
            onValueChange(newValue)
        }

        val gestureModifier = if (enabled) {
            Modifier.pointerInput(totalWidth, valueRange, steps) {
                detectTapGestures(
                    onPress = { offset ->
                        updateFromX(offset.x, density)
                        tryAwaitRelease()
                        onValueChangeFinished?.invoke()
                    }
                )
            }.pointerInput(totalWidth, valueRange, steps) {
                detectDragGestures(
                    onDragStart = { offset ->
                        updateFromX(offset.x, density)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x, density)
                    },
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDragCancel = { onValueChangeFinished?.invoke() }
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            // Track canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = horizontalPadding)
            ) {
                val trackHeight = 16.dp.toPx()
                val radius = trackHeight / 2f
                val y = size.height / 2f
                val activeEnd = size.width * fraction

                // Inactive track pill
                drawRoundRect(
                    color = trackBgColor,
                    topLeft = Offset(0f, y - radius),
                    size = Size(size.width, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                )

                // Active track pill
                if (fraction > 0.001f) {
                    drawRoundRect(
                        color = activeTrackColor,
                        topLeft = Offset(0f, y - radius),
                        size = Size(activeEnd, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                    )
                }

                // Ticks
                if (steps > 0) {
                    val tickColor = if (enabled) Color.White.copy(alpha = 0.5f) else Color.Transparent
                    for (i in 1..steps) {
                        val tickFraction = i.toFloat() / (steps + 1)
                        drawCircle(
                            color = tickColor,
                            radius = 2.dp.toPx(),
                            center = Offset(size.width * tickFraction, y)
                        )
                    }
                }
            }

            // Rolling thumb
            val thumbStart = horizontalPadding + travel * fraction - (thumbSize / 2f)
            Box(
                modifier = Modifier
                    .offset(x = thumbStart.coerceAtLeast(0.dp))
                    .size(thumbSize)
                    .align(Alignment.CenterStart)
                    .rotate(1080f * fraction)
                    .clip(thumbShape)
                    .background(if (enabled) primaryColor else MaterialTheme.colorScheme.outlineVariant)
                    .then(
                        if (isGlass) Modifier.liquidGlassChrome(
                            thumbShape,
                            enabled = true,
                            sheenIntensity = 0.85f,
                            pressFx = false
                        ) else Modifier
                    )
            )
        }
    }
}

/**
 * ImageToolbox HyperOSSlider faithful implementation:
 * - 30dp embedded pill track with smooth inside capsule thumb
 * - High contrast visual styling
 */
@Composable
private fun ImageToolboxHyperOSSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    isGlass: Boolean = false,
    sheen: Float = 0f
) {
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val shape = RoundedCornerShape(15.dp)
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .then(
                if (isGlass) Modifier.liquidGlassChrome(
                    shape,
                    enabled = true,
                    sheenIntensity = sheen * 0.75f,
                    pressFx = false
                ) else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val thumbWidth = 14.dp
        val thumbHeight = 24.dp
        val padding = 3.dp
        val travel = totalWidth - padding * 2 - thumbWidth

        fun updateFromX(xPx: Float, density: Float) {
            val padPx = padding.value * density
            val travelPx = travel.value * density
            val newFraction = ((xPx - padPx) / travelPx).coerceIn(0f, 1f)
            val newValue = valueRange.start + newFraction * rangeSpan
            onValueChange(newValue)
        }

        val gestureModifier = if (enabled) {
            Modifier.pointerInput(totalWidth, valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        updateFromX(offset.x, density)
                        tryAwaitRelease()
                        onValueChangeFinished?.invoke()
                    }
                )
            }.pointerInput(totalWidth, valueRange) {
                detectDragGestures(
                    onDragStart = { offset -> updateFromX(offset.x, density) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x, density)
                    },
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDragCancel = { onValueChangeFinished?.invoke() }
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackBgColor)
                .then(gestureModifier)
        ) {
            // Fill track
            val fillWidth = padding * 2 + travel * fraction + thumbWidth
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fillWidth)
                    .clip(shape)
                    .background(
                        if (enabled) primaryColor.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
            )

            // Embedded thumb capsule
            val thumbStart = padding + travel * fraction
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbStart)
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (enabled) primaryColor else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

/**
 * ImageToolbox M3Slider / M2Slider faithful implementation:
 * - Sleek horizontal rail with centered round or pill thumb
 */
@Composable
private fun ImageToolboxM3Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    isPillThumb: Boolean = false,
    isGlass: Boolean = false,
    sheen: Float = 0f
) {
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .then(
                if (isGlass) Modifier.liquidGlassChrome(
                    RoundedCornerShape(18.dp),
                    enabled = true,
                    sheenIntensity = sheen * 0.65f,
                    pressFx = false
                ) else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val thumbSize = if (isPillThumb) 22.dp else 20.dp
        val travel = totalWidth - thumbSize

        fun updateFromX(xPx: Float, density: Float) {
            val travelPx = travel.value * density
            val newFraction = (xPx / travelPx).coerceIn(0f, 1f)
            val newValue = valueRange.start + newFraction * rangeSpan
            onValueChange(newValue)
        }

        val gestureModifier = if (enabled) {
            Modifier.pointerInput(totalWidth, valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        updateFromX(offset.x, density)
                        tryAwaitRelease()
                        onValueChangeFinished?.invoke()
                    }
                )
            }.pointerInput(totalWidth, valueRange) {
                detectDragGestures(
                    onDragStart = { offset -> updateFromX(offset.x, density) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x, density)
                    },
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDragCancel = { onValueChangeFinished?.invoke() }
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier),
            contentAlignment = Alignment.CenterStart
        ) {
            // Rail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isPillThumb) 16.dp else 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(trackBgColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(50))
                        .background(if (enabled) primaryColor else MaterialTheme.colorScheme.outlineVariant)
                )
            }

            // Thumb
            val thumbStart = travel * fraction
            Box(
                modifier = Modifier
                    .offset(x = thumbStart)
                    .size(
                        width = if (isPillThumb) 8.dp else thumbSize,
                        height = if (isPillThumb) 24.dp else thumbSize
                    )
                    .clip(if (isPillThumb) RoundedCornerShape(4.dp) else CircleShape)
                    .background(if (enabled) primaryColor else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}
