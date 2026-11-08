package com.petal.browser.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

data class ExpressiveSegmentItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null
)

private fun expressiveButtonShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(50.dp)
    index == 0 -> RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp)
    else -> RoundedCornerShape(6.dp)
}

/**
 * Connected segmented button group aligned with [com.petal.browser.ui.containment.PetalConnectedButtonGroup] styling:
 * - 50dp outer / 6dp inner corners
 * - 6dp horizontal spacing between segments
 * - Zero BorderStroke / outlines (border = null)
 * - Theme-driven M3 color tokens (primaryContainer / surfaceContainerHigh)
 * - 48dp minimum touch target
 * - Dynamic weight expansion on active selection (1.1f active / 0.65f inactive)
 */
@Composable
fun ExpressiveButtonGroup(
    items: List<ExpressiveSegmentItem>,
    selectedId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    if (items.isEmpty()) return

    val resolvedMinHeight = height.coerceAtLeast(48.dp)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val haptics = LocalHapticFeedback.current
    val hasSelection = items.any { it.id == selectedId }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val count = items.size
        items.forEachIndexed { index, item ->
            val isSelected = (item.id == selectedId)
            // Material 3 Expressive connected group (pattern from PixelMusic): the selected segment
            // morphs into a full pill while its neighbours keep tight inner corners.
            val baseShape = expressiveButtonShape(index, count)
            val pillCorner by animateDpAsState(
                targetValue = if (isSelected && count > 1) 50.dp else 0.dp,
                animationSpec = PetalSpring.segment(),
                label = "ExpressiveSegmentPill"
            )
            val shape: Shape = if (isSelected && count > 1) RoundedCornerShape(pillCorner.coerceAtLeast(6.dp)) else baseShape
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val isActive = isSelected || isPressed

            val animatedWeight by animateFloatAsState(
                targetValue = if (!hasSelection && !isPressed) 1f else if (isActive) 1.15f else 0.88f,
                animationSpec = PetalSpring.segment(),
                label = "ExpressiveSegmentWeight"
            )

            val containerColor = if (isSelected) {
                if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.primaryContainer
            } else {
                if (isGlass) petalGroupSurfaceColor()
                else MaterialTheme.colorScheme.surfaceContainerHigh
            }

            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            val baseModifier = Modifier
                .weight(animatedWeight)
                .defaultMinSize(minHeight = resolvedMinHeight)
                .semantics { role = Role.RadioButton }

            val buttonModifier = if (isGlass) {
                baseModifier.liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = if (isSelected) 0.80f else 0.35f,
                    pressFx = true
                )
            } else {
                baseModifier
            }

            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onItemSelected(item.id)
                },
                modifier = buttonModifier,
                shape = shape,
                color = containerColor,
                contentColor = contentColor,
                border = null,
                interactionSource = interactionSource
            ) {
                val compact = count >= 3
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = resolvedMinHeight)
                        .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected && item.icon == null) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                    }
                    if (item.icon != null) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                    }
                    Text(
                        text = item.label,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )
                }
            }
        }
    }
}
