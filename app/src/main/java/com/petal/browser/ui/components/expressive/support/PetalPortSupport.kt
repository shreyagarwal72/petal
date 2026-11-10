package com.petal.browser.ui.components.expressive.support

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.petal.browser.ui.theme.toSafeShadowShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.petal.browser.ui.theme.layout.LocalPetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.cornerShape
import com.petal.browser.ui.theme.layout.pillShape

/*
 * Thin compatibility layer so the ImageToolbox switch / slider sources (Apache-2.0, (c) T8RIN) can
 * run inside Petal with their original structure. Everything here maps an ImageToolbox helper onto
 * Petal's own theme, shape engine and layout state.
 */

// ── Settings access ───────────────────────────────────────────────────────────────────────────

/** Mirrors ImageToolbox's `LocalSettingsState.current` on top of Petal's live layout settings. */
object LocalSettingsState {
    val current: PetalLayoutSettings
        @Composable get() {
            PetalLayoutState.install(LocalContext.current)
            return PetalLayoutState.settings
        }
}

val PetalLayoutSettings.isNightMode: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

// ── Shapes ────────────────────────────────────────────────────────────────────────────────────

object ShapeDefaults {
    /** Fully rounded in the active shape style. */
    val circle: CornerBasedShape
        @Composable get() = LocalSettingsState.current.shapeType.pillShape()

    val small: CornerBasedShape
        @Composable get() = LocalSettingsState.current.let {
            it.shapeType.cornerShape(12.dp, multiplier = PetalLayoutState.displayCorners)
        }

    val default: CornerBasedShape
        @Composable get() = LocalSettingsState.current.let {
            it.shapeType.cornerShape(20.dp, multiplier = PetalLayoutState.displayCorners)
        }
}

@Composable
fun AutoCircleShape(): CornerBasedShape = LocalSettingsState.current.shapeType.pillShape()

@Composable
fun <T> androidx.compose.runtime.ProvidableCompositionLocal<T>.ProvidesValue(
    value: T,
    content: @Composable () -> Unit
) = androidx.compose.runtime.CompositionLocalProvider(value = this provides value, content)

// ── Colours ───────────────────────────────────────────────────────────────────────────────────

val SafeLocalContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

@Stable
fun Color.compositeOverSafe(background: Color): Color =
    (if (this.isSpecifiedSafe()) this else Color.Transparent)
        .compositeOver(if (background.isSpecifiedSafe()) background else Color.Transparent)

private fun Color.isSpecifiedSafe(): Boolean = this != Color.Unspecified

@Stable
fun Color.blend(color: Color, fraction: Float = 0.2f): Color =
    Color(ColorUtils.blendARGB(this.toArgb(), color.toArgb(), fraction.coerceIn(0f, 1f)))

@Stable
fun ColorScheme.outlineVariant(
    luminance: Float = 0.3f,
    onTopOf: Color = surfaceContainer
): Color = onSecondaryContainer
    .copy(alpha = luminance)
    .compositeOverSafe(onTopOf.takeOrElse { surface })

@Composable
inline fun takeColorFromScheme(
    action: @Composable ColorScheme.(isNightMode: Boolean) -> Color
): Color = animateColorAsState(
    MaterialTheme.colorScheme.run {
        action(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    },
    label = "takeColorFromScheme"
).value

// ── Interaction & motion ──────────────────────────────────────────────────────────────────────

@Composable
fun rememberRipple(
    bounded: Boolean = true,
    radius: Dp = Dp.Unspecified,
    contentColor: Color = Color.Unspecified
): Indication {
    val color = contentColor.takeOrElse { LocalContentColor.current }
    return remember(bounded, radius, color) {
        ripple(color = color, bounded = bounded, radius = radius)
    }
}

val PointToPointEasing = CubicBezierEasing(0.55f, 0.55f, 0f, 1f)
val FastInvokeEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

@Composable
fun animateFloatingRangeAsState(
    range: ClosedFloatingPointRange<Float>,
    animationSpec: AnimationSpec<Float> = spring()
): State<ClosedFloatingPointRange<Float>> {
    val start = animateFloatAsState(targetValue = range.start, animationSpec = animationSpec, label = "rangeStart")
    val end = animateFloatAsState(targetValue = range.endInclusive, animationSpec = animationSpec, label = "rangeEnd")
    return remember(start, end) { derivedStateOf { start.value..end.value } }
}

fun Modifier.hapticsClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    enableHaptics: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    Modifier.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = {
            if (enableHaptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    )
}

fun Modifier.hapticsClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    Modifier.hapticsClickable(
        interactionSource = null,
        indication = LocalIndication.current,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

// ── Container / shadow ────────────────────────────────────────────────────────────────────────

@Composable
fun Modifier.materialShadow(
    shape: Shape,
    elevation: Dp,
    enabled: Boolean = true,
    isClipped: Boolean = true,
    color: Color = Color.Black
): Modifier = materialShadow(
    shape = shape,
    elevation = animateDpAsState(if (enabled) elevation else 0.dp, label = "materialShadow"),
    isClipped = isClipped,
    color = color
)

@Composable
fun Modifier.materialShadow(
    shape: Shape,
    elevation: State<Dp>,
    isClipped: Boolean = true,
    color: Color = Color.Black
): Modifier = if (elevation.value > 0.dp) {
    this.shadow(
        elevation = elevation.value,
        shape = shape.toSafeShadowShape(),
        clip = false,
        ambientColor = color,
        spotColor = color
    )
} else this

/**
 * Tonal container with an optional hairline border and soft shadow - the parameter list matches
 * ImageToolbox's `container(...)` so the ported sliders and switches compile unchanged.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Modifier.container(
    shape: Shape? = null,
    color: Color = Color.Unspecified,
    resultPadding: Dp = 4.dp,
    borderWidth: Dp = Dp.Unspecified,
    borderColor: Color? = null,
    autoShadowElevation: Dp = if (color != Color.Transparent) 1.dp else 0.dp,
    clip: Boolean = true,
    composeColorOnTopOfBackground: Boolean = true,
    isShadowClip: Boolean = true,
    isStandaloneContainer: Boolean = true,
    shadowColor: Color = Color.Black
): Modifier {
    val resultShape = shape ?: ShapeDefaults.default
    val scheme = MaterialTheme.colorScheme
    val containerColor = when {
        color == Color.Unspecified -> SafeLocalContainerColor
        composeColorOnTopOfBackground -> color.compositeOverSafe(scheme.background)
        else -> color
    }
    val stroke = if (borderWidth == Dp.Unspecified) 0.dp else borderWidth
    return this
        .materialShadow(
            shape = resultShape,
            elevation = autoShadowElevation.coerceAtLeast(0.dp),
            isClipped = isShadowClip,
            color = shadowColor
        )
        .then(if (clip) Modifier.clip(resultShape) else Modifier)
        .background(containerColor, resultShape)
        .then(
            if (stroke > 0.dp) Modifier.border(stroke, borderColor ?: scheme.outlineVariant(0.1f, containerColor), resultShape)
            else Modifier
        )
}
