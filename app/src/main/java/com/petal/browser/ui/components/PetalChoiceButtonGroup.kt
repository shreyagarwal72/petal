package com.petal.browser.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

/**
 * Option item model for [PetalChoiceButtonGroup].
 */
data class PetalButtonGroupItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val enabled: Boolean = true,
)

private fun petalChoiceShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(50.dp)
    index == 0 -> RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp)
    else -> RoundedCornerShape(6.dp)
}

/**
 * Universal pick-one connected toggle button group sharing identical visual styling with
 * [com.petal.browser.ui.containment.PetalConnectedButtonGroup]:
 * - 50dp outer / 6dp inner corners
 * - 6dp horizontal spacing between segments
 * - Zero BorderStroke / outlines (border = null)
 * - Theme-driven M3 color tokens (primaryContainer / surfaceContainerHigh)
 * - 48dp minimum touch target
 * - Dynamic weight expansion on active selection (1.1f active / 0.65f inactive) when uncrowded
 * - Smooth horizontal scrolling when crowded
 */
@Composable
fun <T> PetalChoiceButtonGroup(
    options: List<PetalButtonGroupItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    scrollableIfCrowded: Boolean = true,
) {
    if (options.isEmpty()) return

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val resolvedMinHeight = minHeight.coerceAtLeast(48.dp)
    val shouldScroll = scrollableIfCrowded && (options.size > 4 || options.any { it.label.length > 12 })
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val hasSelection = options.any { it.value == selected }

    val unselectedContainer = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh
    val unselectedContent = MaterialTheme.colorScheme.onSurface
    val selectedContainer = if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primaryContainer
    val selectedContent = MaterialTheme.colorScheme.onPrimaryContainer

    val rowModifier = if (shouldScroll) {
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    } else {
        modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val count = options.size
        options.forEachIndexed { index, option ->
            val isChecked = (option.value == selected)
            val shape = petalChoiceShape(index, count)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val isActive = isChecked || isPressed

            val animatedWeight by animateFloatAsState(
                targetValue = if (!hasSelection && !isPressed) 1f else if (isActive) 1.1f else 0.65f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                label = "ChoiceButtonWeight"
            )

            val baseModifier = if (shouldScroll) {
                Modifier
                    .defaultMinSize(minHeight = resolvedMinHeight)
                    .semantics { role = Role.RadioButton }
            } else {
                Modifier
                    .weight(animatedWeight)
                    .defaultMinSize(minHeight = resolvedMinHeight)
                    .semantics { role = Role.RadioButton }
            }

            val buttonModifier = if (isGlass) {
                baseModifier.liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = if (isChecked) 0.80f else 0.45f,
                    pressFx = true
                )
            } else {
                baseModifier
            }

            Surface(
                onClick = {
                    try {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.4f)
                    } catch (_: Exception) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onSelect(option.value)
                },
                modifier = buttonModifier,
                enabled = option.enabled,
                shape = shape,
                color = if (isChecked) selectedContainer else unselectedContainer,
                contentColor = if (isChecked) selectedContent else unselectedContent,
                border = null,
                interactionSource = interactionSource
            ) {
                val compact = !shouldScroll && count >= 3
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = resolvedMinHeight)
                        .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (option.icon != null) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                    } else if (option.iconRes != null) {
                        Icon(
                            painter = painterResource(id = option.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                        )
                        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                    }

                    if (option.label.isNotEmpty()) {
                        Text(
                            text = option.label,
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overloaded variant for index-based selection with simple label and optional icon lists.
 */
@Composable
fun PetalChoiceButtonGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
    minHeight: Dp = 48.dp,
    scrollableIfCrowded: Boolean = true,
) {
    val options = labels.mapIndexed { index, label ->
        PetalButtonGroupItem(
            value = index,
            label = label,
            icon = icons?.getOrNull(index)
        )
    }
    PetalChoiceButtonGroup(
        options = options,
        selected = selectedIndex,
        onSelect = onSelectIndex,
        modifier = modifier,
        minHeight = minHeight,
        scrollableIfCrowded = scrollableIfCrowded
    )
}
