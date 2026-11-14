package com.petal.browser.ui.miuix.containment

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.miuix.components.MiuiXSwitch
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXDimensions
import com.petal.browser.ui.miuix.theme.MiuiXMotion

/**
 * Xiaomi HyperOS Preference Item Row.
 *
 * Implements standard HyperOS item layout with continuous squircle icon badge,
 * hierarchical title/subtitle, trailing control/chevron, and indented hairline divider.
 */
@Composable
fun MiuiXPreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalMiuiXColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null && enabled) 0.985f else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_row_press_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MiuiXDimensions.RowMinHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional Leading Icon Badge
            if (icon != null) {
                MiuiXBadge(
                    icon = icon,
                    containerColor = iconContainerColor,
                    iconTint = iconTint,
                )
                Spacer(modifier = Modifier.width(14.dp))
            }

            // Title & Subtitle column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    ),
                    color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.4f)
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        ),
                        color = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // Trailing Component or Chevron
            if (trailing != null) {
                trailing()
            } else if (showChevron) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Indented hairline divider
        if (showDivider) {
            val startIndent = if (icon != null) MiuiXDimensions.DividerIndent else 16.dp
            HorizontalDivider(
                modifier = Modifier.padding(start = startIndent),
                thickness = 0.5.dp,
                color = colors.divider
            )
        }
    }
}

/**
 * Xiaomi HyperOS Toggle Row (Convenience wrapper around [MiuiXPreferenceRow] + [MiuiXSwitch]).
 */
@Composable
fun MiuiXSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconTint: Color? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    MiuiXPreferenceRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconContainerColor = iconContainerColor,
        iconTint = iconTint,
        showDivider = showDivider,
        enabled = enabled,
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        trailing = {
            MiuiXSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        modifier = modifier
    )
}
