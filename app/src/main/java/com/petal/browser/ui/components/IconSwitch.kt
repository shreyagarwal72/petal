package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.layout.LocalPetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalSwitchType
import kotlin.math.roundToInt

/**
 * Expressive switch supporting all styles from ImageToolbox:
 * Material You, Compose, Pixel, Fluent, Cupertino, HyperOS, OneUI.
 *
 * NOTE: If Liquid Glass is enabled in theme settings, Liquid Glass styling
 * automatically overrides and applies across all switches.
 */
@Composable
fun IconSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val layoutSettings = LocalPetalLayoutSettings.current

    if (isGlass) {
        LiquidGlassSwitchImpl(
            checked = checked,
            icon = icon,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier
        )
    } else {
        when (layoutSettings.switchType) {
            PetalSwitchType.MATERIAL_YOU -> {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    thumbContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                    modifier = modifier.scale(0.95f),
                )
            }
            PetalSwitchType.COMPOSE -> {
                ComposeSwitchImpl(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.PIXEL -> {
                PixelSwitchImpl(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.CUPERTINO -> {
                CupertinoSwitchImpl(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.FLUENT -> {
                FluentSwitchImpl(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.HYPEROS -> {
                HyperOSSwitchImpl(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
            PetalSwitchType.ONE_UI -> {
                OneUISwitchImpl(
                    checked = checked,
                    icon = icon,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = modifier
                )
            }
        }
    }
}

/**
 * Liquid Glass switch: translucent frosted pill track with refraction and a glowing glass thumb.
 */
@Composable
private fun LiquidGlassSwitchImpl(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier
) {
    val sheen = rememberGlassSheen(true)
    val pillShape = RoundedCornerShape(50)
    val trackBg by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        label = "glassTrackBg"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 24.dp else 2.dp,
        animationSpec = tween(180),
        label = "glassThumbOffset"
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(pillShape)
            .background(trackBg)
            .liquidGlassChrome(shape = pillShape, enabled = true, sheenIntensity = sheen)
            .clickable(
                enabled = enabled && onCheckedChange != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .liquidGlassChrome(shape = CircleShape, enabled = true, sheenIntensity = 0.8f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (checked) icon else Icons.Rounded.Check,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ComposeSwitchImpl(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "composeTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        animationSpec = tween(180),
        label = "composeThumb"
    )

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun PixelSwitchImpl(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "pixelTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 24.dp else 4.dp,
        animationSpec = tween(200),
        label = "pixelThumb"
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun CupertinoSwitchImpl(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "cupertinoTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        animationSpec = tween(180),
        label = "cupertinoThumb"
    )

    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
private fun FluentSwitchImpl(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "fluentTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 3.dp,
        animationSpec = tween(150),
        label = "fluentThumb"
    )

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
private fun HyperOSSwitchImpl(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "hyperTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 26.dp else 3.dp,
        animationSpec = tween(160),
        label = "hyperThumb"
    )

    Box(
        modifier = modifier
            .size(width = 54.dp, height = 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun OneUISwitchImpl(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    modifier: Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "oneUiTrack"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        animationSpec = tween(190),
        label = "oneUiThumb"
    )

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
