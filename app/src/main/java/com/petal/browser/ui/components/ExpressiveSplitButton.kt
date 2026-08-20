package com.petal.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SplitButtonVariant {
    FILLED, TONAL, ELEVATED, OUTLINED
}

/**
 * M3 Expressive Split Button pairing a primary action button with a connected,
 * morphing dropdown menu button.
 */
@Composable
fun ExpressiveSplitButton(
    label: String,
    onPrimaryClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isMenuExpanded: Boolean = false,
    variant: SplitButtonVariant = SplitButtonVariant.FILLED,
    height: Dp = 48.dp
) {
    val rotation by animateFloatAsState(
        targetValue = if (isMenuExpanded) 180f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "SplitButtonMenuRotation"
    )

    val containerColor = when (variant) {
        SplitButtonVariant.FILLED -> MaterialTheme.colorScheme.primary
        SplitButtonVariant.TONAL -> MaterialTheme.colorScheme.secondaryContainer
        SplitButtonVariant.ELEVATED -> MaterialTheme.colorScheme.surfaceContainerHigh
        SplitButtonVariant.OUTLINED -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when (variant) {
        SplitButtonVariant.FILLED -> MaterialTheme.colorScheme.onPrimary
        SplitButtonVariant.TONAL -> MaterialTheme.colorScheme.onSecondaryContainer
        SplitButtonVariant.ELEVATED -> MaterialTheme.colorScheme.primary
        SplitButtonVariant.OUTLINED -> MaterialTheme.colorScheme.primary
    }

    val cornerRadius = height / 2
    val primaryShape = RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius, topEnd = 4.dp, bottomEnd = 4.dp)
    val menuShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = cornerRadius, bottomEnd = cornerRadius)

    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent,
        border = if (variant == SplitButtonVariant.OUTLINED) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else null,
        shadowElevation = if (variant == SplitButtonVariant.ELEVATED) 4.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(height)
        ) {
            // Primary Action Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxHeight()
                    .clip(primaryShape)
                    .background(containerColor)
                    .clickable(onClick = onPrimaryClick)
                    .padding(horizontal = 16.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
            }

            Spacer(Modifier.width(2.dp))

            // Connected Morphing Menu Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(height)
                    .clip(menuShape)
                    .background(containerColor)
                    .clickable(onClick = onMenuClick)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = "Expand options",
                    tint = contentColor,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }
    }
}
