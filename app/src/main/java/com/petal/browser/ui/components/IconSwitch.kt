package com.petal.browser.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Kept as the app-wide entry point for switches (64 call sites). It now simply delegates to
 * [PetalSwitch], which renders the user's chosen ImageToolbox switch style live and switches to the
 * liquid glass toggle automatically while Liquid Glass is active.
 */
@Composable
fun IconSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PetalSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbIcon = icon,
        enabled = enabled,
    )
}
