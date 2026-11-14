package com.petal.browser.ui.miuix.containment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXDimensions
import com.petal.browser.ui.miuix.theme.MiuiXShapes

/**
 * Xiaomi HyperOS Continuous Squircle Icon Badge.
 *
 * Distinctive 38dp continuous squircle container with gentle tint background
 * and centered icon.
 */
@Composable
fun MiuiXBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = MiuiXDimensions.BadgeSize,
    containerColor: Color? = null,
    iconTint: Color? = null,
) {
    val colors = LocalMiuiXColors.current
    val actualContainerColor = containerColor ?: colors.primaryContainer
    val actualIconTint = iconTint ?: colors.primary

    Box(
        modifier = modifier
            .size(size)
            .clip(MiuiXShapes.Badge)
            .background(actualContainerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = actualIconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}
