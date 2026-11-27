package com.petal.browser.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icon button with tap and long-press on the button itself (no wrapper that eats the touch).
 * Visuals stay 24dp; touch height stays at least 48dp even when [visualWidth] shrinks to 40dp.
 */
@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualWidth: Dp = 48.dp,
    allowLongClickWhenDisabled: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val clickableEnabled = enabled || (allowLongClickWhenDisabled && onLongClick != null)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(visualWidth)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = clickableEnabled,
                role = Role.Button,
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = { if (enabled) onClick() },
                onLongClick = onLongClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
