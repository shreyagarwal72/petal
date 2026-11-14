package com.petal.browser.ui.miuix.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXMotion
import com.petal.browser.ui.miuix.theme.MiuiXShapes
import com.petal.browser.ui.miuix.theme.MiuiXSquircleShape

/**
 * Xiaomi HyperOS Primary Action Button.
 *
 * Rendered with continuous squircle geometry, vibrant HyperOS blue fill,
 * and fluid spring-scale press response.
 */
@Composable
fun MiuiXButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MiuiXShapes.Medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val miuixColors = LocalMiuiXColors.current
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) MiuiXMotion.PRESS_SCALE else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_button_scale"
    )

    val backgroundColor = if (enabled) miuixColors.primary else miuixColors.primary.copy(alpha = 0.45f)
    val contentColor = if (enabled) miuixColors.onPrimary else miuixColors.onPrimary.copy(alpha = 0.6f)

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        interactionSource = actualSource,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) {
                content()
            }
        }
    }
}

/**
 * Xiaomi HyperOS Tonal / Secondary Container Button.
 */
@Composable
fun MiuiXTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MiuiXShapes.Medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val miuixColors = LocalMiuiXColors.current
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) MiuiXMotion.PRESS_SCALE else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_tonal_button_scale"
    )

    val backgroundColor = if (enabled) miuixColors.primaryContainer else miuixColors.primaryContainer.copy(alpha = 0.4f)
    val contentColor = if (enabled) miuixColors.onPrimaryContainer else miuixColors.onPrimaryContainer.copy(alpha = 0.5f)

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        interactionSource = actualSource,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) {
                content()
            }
        }
    }
}

/**
 * Xiaomi HyperOS Outlined Squircle Button.
 */
@Composable
fun MiuiXOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MiuiXShapes.Medium,
    borderStroke: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val miuixColors = LocalMiuiXColors.current
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) MiuiXMotion.PRESS_SCALE else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_outlined_button_scale"
    )

    val border = borderStroke ?: BorderStroke(
        width = 1.dp,
        color = if (enabled) miuixColors.outline else miuixColors.outline.copy(alpha = 0.5f)
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        contentColor = if (enabled) miuixColors.primary else miuixColors.primary.copy(alpha = 0.5f),
        border = border,
        interactionSource = actualSource,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) {
                content()
            }
        }
    }
}

/**
 * Xiaomi HyperOS Continuous Squircle Icon Button.
 */
@Composable
fun MiuiXIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    shape: Shape = MiuiXShapes.Small,
    tint: Color = LocalMiuiXColors.current.onSurface,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_icon_button_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = actualSource,
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) tint else tint.copy(alpha = 0.38f)) {
            content()
        }
    }
}
