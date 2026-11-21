package com.petal.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalPopupMenu
import com.petal.browser.ui.containment.PetalPopupMenuItem
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.petalGroupSurfaceColor

@Immutable
class PetalOverflowAction(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val role: PetalGroupRole = PetalGroupRole.Secondary,
)

/**
 * A toolbar row of icon buttons that keeps as many actions visible as the width allows and moves the
 * rest behind a trailing "more" button that opens a [PetalPopupMenu].
 *
 * - The pressed button grows and its neighbours give way a little (same idea as an M3 Expressive button group).
 * - Uses role colors, or the shared glass surface when Liquid Glass is on; solid when [MotionGate] asks to simplify.
 * - Every button is at least 48dp wide.
 * - [maxVisible] caps the visible count even when there is room (use it to force overflow).
 */
@Composable
fun PetalOverflowActionRow(
    actions: List<PetalOverflowAction>,
    modifier: Modifier = Modifier,
    maxVisible: Int = Int.MAX_VALUE,
) {
    if (actions.isEmpty()) return
    val slot = 48.dp
    val gap = 3.dp
    BoxWithConstraints(modifier = modifier) {
        val available = if (constraints.hasBoundedWidth) maxWidth else 10000.dp
        val fit = ((available + gap) / (slot + gap)).toInt().coerceAtLeast(1)
        val cap = minOf(fit, maxVisible.coerceAtLeast(0))
        val visibleCount = if (actions.size <= cap) actions.size else (cap - 1).coerceAtLeast(0)
        val overflow = actions.drop(visibleCount)
        var pressedIndex by remember { mutableStateOf(-1) }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            actions.take(visibleCount).forEachIndexed { index, action ->
                OverflowRowButton(
                    action = action,
                    index = index,
                    pressedIndex = pressedIndex,
                    onPressedChange = { pressedIndex = if (it) index else -1 },
                    slot = slot,
                )
            }
            if (overflow.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OverflowRowButton(
                        action = PetalOverflowAction(
                            id = "overflow",
                            label = stringResource(R.string.ui_overflow_more),
                            icon = Icons.Rounded.MoreVert,
                            onClick = { expanded = true },
                            role = PetalGroupRole.Tertiary,
                        ),
                        index = visibleCount,
                        pressedIndex = pressedIndex,
                        onPressedChange = { pressedIndex = if (it) visibleCount else -1 },
                        slot = slot,
                    )
                    PetalPopupMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        overflow.forEach { item ->
                            PetalPopupMenuItem(
                                text = item.label,
                                enabled = item.enabled,
                                onClick = { expanded = false; item.onClick() },
                                leadingIcon = { Icon(item.icon, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverflowRowButton(
    action: PetalOverflowAction,
    index: Int,
    pressedIndex: Int,
    onPressedChange: (Boolean) -> Unit,
    slot: Dp,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    androidx.compose.runtime.LaunchedEffect(pressed) { onPressedChange(pressed) }

    val simplify = com.petal.browser.motion.MotionGate.shouldSimplifyEffects()
    val glass = isLiquidGlassEnabled("sp_liquid_glass_containments") && !simplify
    val roleColors = petalRoleColors(action.role)
    val container by animateColorAsState(
        targetValue = if (glass) petalGroupSurfaceColor() else roleColors.container,
        animationSpec = PetalSpring.crossfade(),
        label = "OverflowBtnContainer",
    )
    val content = if (glass) MaterialTheme.colorScheme.onSurface else roleColors.onContainer

    // Pressed button grows, direct neighbours shrink a little.
    val target = when {
        simplify -> slot
        pressedIndex == index -> slot + 10.dp
        pressedIndex >= 0 && kotlin.math.abs(pressedIndex - index) == 1 -> slot - 6.dp
        else -> slot
    }
    val width by animateDpAsState(target, PetalSpring.spatial(), label = "OverflowBtnWidth")
    val corner by animateDpAsState(if (pressed && !simplify) 12.dp else 24.dp, PetalSpring.spatial(), label = "OverflowBtnCorner")

    Box(
        modifier = Modifier
            .width(width)
            .size(width = width, height = slot)
            .clip(RoundedCornerShape(corner))
            .background(container.copy(alpha = if (action.enabled) 1f else 0.5f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = action.enabled,
                role = Role.Button,
                onClickLabel = action.label,
            ) {
                PetalHapticEngine.getInstance(context)
                    .playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.5f)
                action.onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = content.copy(alpha = if (action.enabled) 1f else 0.38f),
            modifier = Modifier.size(22.dp),
        )
    }
}
