package com.petal.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.isLiquidGlassEnabled

data class ExpressiveSegmentItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null
)

/**
 * M3 Expressive Segmented Button Group with dynamic shape-morphing active states,
 * spring physics, and animated corner radii transitions.
 */
@Composable
fun ExpressiveButtonGroup(
    items: List<ExpressiveSegmentItem>,
    selectedId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    val outerRadius = height / 2
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val buttonGroupShape = RoundedCornerShape(outerRadius)

    Surface(
        modifier = modifier
            .height(height)
            .liquidGlassChrome(shape = buttonGroupShape, enabled = isGlass, sheenIntensity = 0.35f),
        shape = buttonGroupShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isGlass) 0.dp else 2.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(4.dp)
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = item.id == selectedId

                val animatedCornerRadius by animateDpAsState(
                    targetValue = if (isSelected) outerRadius - 4.dp else 8.dp,
                    animationSpec = spring(
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
                    ),
                    label = "SegmentCornerAnimation"
                )

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    label = "SegmentColorAnimation"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    label = "SegmentContentColorAnimation"
                )

                val shape = RoundedCornerShape(animatedCornerRadius)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(containerColor)
                        .clickable { onItemSelected(item.id) }
                        .padding(horizontal = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
