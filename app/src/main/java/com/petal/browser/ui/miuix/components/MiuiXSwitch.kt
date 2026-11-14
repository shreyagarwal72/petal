package com.petal.browser.ui.miuix.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.petal.browser.ui.components.expressive.switches.HyperOSSwitch
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors

/**
 * Xiaomi HyperOS / MIUIX Spring Switch.
 *
 * High-contrast accent toggle featuring low-damping spring thumb transitions,
 * tactile spring release animation, and high-visibility track styling.
 */
@Composable
fun MiuiXSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val miuixColors = LocalMiuiXColors.current

    val colors: SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = miuixColors.primary,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = miuixColors.track,
        uncheckedBorderColor = Color.Transparent,
        disabledCheckedThumbColor = Color.White.copy(alpha = 0.6f),
        disabledCheckedTrackColor = miuixColors.primary.copy(alpha = 0.4f),
        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
        disabledUncheckedTrackColor = miuixColors.track.copy(alpha = 0.4f),
    )

    HyperOSSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = colors,
        interactionSource = interactionSource,
        enabled = enabled,
    )
}
