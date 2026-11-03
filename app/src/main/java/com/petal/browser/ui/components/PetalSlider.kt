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
import androidx.compose.foundation.shape.CircleShape
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
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.layout.LocalPetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalSliderType

private val TrackHeight = 46.dp
private val ThumbSize = 30.dp
private val ThumbInset = 8.dp

/**
 * Expressive slider supporting ImageToolbox slider styles (Fancy, Material You, Material, HyperOS).
 * ALL variants dynamically support Liquid Glass styling with frosted glass refraction and sheen.
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
    steps: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val layoutSettings = LocalPetalLayoutSettings.current
    val trackShape = RoundedCornerShape(50)

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.height(TrackHeight),
        thumb = {},
        track = { state ->
            val target = (
                (state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start)
                ).coerceIn(0f, 1f)

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
                    .clip(trackShape)
                    .then(
                        if (isGlass) Modifier.liquidGlassChrome(
                            trackShape,
                            enabled = true,
                            sheenIntensity = sheen * 0.75f,
                            pressFx = false
                        ) else Modifier
                    )
                    .background(
                        if (isGlass) petalGroupSurfaceColor()
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            ) {
                val travel = maxWidth - ThumbSize - ThumbInset * 2
                val thumbStart = ThumbInset + travel * fraction

                when (layoutSettings.sliderType) {
                    PetalSliderType.FANCY -> {
                        // Rolling 12-sided cookie thumb inside pill track
                        val circumference = ThumbSize.value * Math.PI.toFloat()
                        val rollDegrees = (travel.value * fraction / circumference) * 360f
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
                    PetalSliderType.MATERIAL_YOU -> {
                        // Pill capsule thumb with dynamic fill bar
                        val fillWidth = if (fraction <= 0.001f) 0.dp else thumbStart + ThumbSize + ThumbInset
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(fillWidth)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = thumbStart + 6.dp)
                                .size(width = 8.dp, height = 24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.surface
                                )
                        )
                    }
                    PetalSliderType.MATERIAL -> {
                        // Standard Material 3 circle thumb centered on track rail
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = thumbStart)
                                .size(ThumbSize)
                                .clip(CircleShape)
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                        )
                    }
                    PetalSliderType.HYPEROS -> {
                        // Sleek embedded capsule thumb with thin accent rail
                        val fillWidth = if (fraction <= 0.001f) 0.dp else thumbStart + ThumbSize + ThumbInset
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(fillWidth)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = thumbStart + 3.dp)
                                .size(width = 14.dp, height = 26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        )
                    }
                }
            }
        }
    )
}
