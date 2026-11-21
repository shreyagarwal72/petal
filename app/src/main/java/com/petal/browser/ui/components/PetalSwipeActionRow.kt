package com.petal.browser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * One action revealed by swiping a list row.
 *
 * @param destructive true = error colors, and the row slides off screen before [onTrigger] runs.
 * @param role design-system color role used when the action is not destructive.
 */
@Immutable
class PetalRowSwipeAction(
    val label: String,
    val icon: ImageVector,
    val onTrigger: () -> Unit,
    val destructive: Boolean = false,
    val role: PetalGroupRole = PetalGroupRole.Secondary,
)

/**
 * A list row you can swipe sideways to reveal actions.
 *
 * - [startActions] are revealed by swiping toward the end (left to right in LTR).
 * - [endActions] are revealed by swiping toward the start (right to left in LTR).
 * - With two actions in one direction the swipe has two steps: a short swipe picks the first
 *   action, a long swipe picks the second. A haptic tick marks each step; release to run it.
 * - Leave a direction's list empty to switch that direction off (for example on screens with a
 *   fast-scroll bar on the right edge).
 * - Every action is also exposed as an accessibility custom action, so swiping is never the only way.
 */
@Composable
fun PetalSwipeActionRow(
    modifier: Modifier = Modifier,
    startActions: List<PetalRowSwipeAction> = emptyList(),
    endActions: List<PetalRowSwipeAction> = emptyList(),
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableIntStateOf(0) }
    var offsetPx by remember { mutableFloatStateOf(0f) }

    val canStart = enabled && startActions.isNotEmpty()
    val canEnd = enabled && endActions.isNotEmpty()
    val activeActions = if (offsetPx >= 0f) startActions else endActions

    // Fractions of the row width that mark step 1 and step 2.
    fun stageOf(offset: Float, actions: List<PetalRowSwipeAction>): Int {
        if (widthPx == 0 || actions.isEmpty()) return 0
        val fraction = abs(offset) / widthPx
        var stage = 0
        if (fraction >= 0.28f) stage = 1
        if (fraction >= 0.55f && actions.size > 1) stage = 2
        return stage
    }

    val stage = stageOf(offsetPx, activeActions)
    LaunchedEffect(stage) {
        if (stage > 0) {
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).thresholdCrossed(context)
        }
    }

    suspend fun settleBack() {
        animate(
            initialValue = offsetPx,
            targetValue = 0f,
            animationSpec = PetalSpring.calm(),
        ) { value, _ -> offsetPx = value }
    }

    val dragState = rememberDraggableState { delta ->
        val maxPull = widthPx * 0.7f
        var next = offsetPx + delta
        if (!canStart && next > 0f) next = 0f
        if (!canEnd && next < 0f) next = 0f
        offsetPx = next.coerceIn(-maxPull, maxPull)
    }

    val dragModifier = if (canStart || canEnd) {
        Modifier.draggable(
            state = dragState,
            orientation = Orientation.Horizontal,
            onDragStopped = {
                val actions = if (offsetPx >= 0f) startActions else endActions
                val action = actions.getOrNull(stageOf(offsetPx, actions) - 1)
                when {
                    action == null -> settleBack()
                    action.destructive -> {
                        // Slide the row away first, then delete. If the row is still on screen a
                        // moment later (the delete did not remove it), bring it back.
                        val target = if (offsetPx >= 0f) widthPx.toFloat() else -widthPx.toFloat()
                        animate(
                            initialValue = offsetPx,
                            targetValue = target,
                            animationSpec = PetalSpring.calm(),
                        ) { value, _ -> offsetPx = value }
                        action.onTrigger()
                        delay(450)
                        settleBack()
                    }
                    else -> {
                        action.onTrigger()
                        settleBack()
                    }
                }
            },
        )
    } else Modifier

    val a11yActions = remember(startActions, endActions) {
        (startActions + endActions).map { action ->
            CustomAccessibilityAction(action.label) { action.onTrigger(); true }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .semantics { customActions = a11yActions },
    ) {
        if (abs(offsetPx) > 1f) {
            val shown = activeActions.getOrNull((stage - 1).coerceAtLeast(0))
            if (shown != null) {
                val scheme = MaterialTheme.colorScheme
                val role = petalRoleColors(shown.role)
                val container by animateColorAsState(
                    targetValue = if (shown.destructive) scheme.errorContainer else role.container,
                    animationSpec = PetalSpring.calm(),
                    label = "SwipeRevealContainer",
                )
                val onContainer = if (shown.destructive) scheme.onErrorContainer else role.onContainer
                val iconScale by animateFloatAsState(
                    targetValue = if (stage > 0) 1.15f else 0.85f,
                    animationSpec = PetalSpring.calm(),
                    label = "SwipeRevealIconScale",
                )
                val fromStart = offsetPx >= 0f
                Box(
                    modifier = Modifier
                        .align(if (fromStart) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(with(density) { abs(offsetPx).toDp() })
                        .clip(shape)
                        .background(container),
                    contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Row(
                        modifier = Modifier
                            .wrapContentWidth(if (fromStart) Alignment.Start else Alignment.End, unbounded = true)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (fromStart) {
                            Icon(shown.icon, contentDescription = null, tint = onContainer,
                                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                        }
                        Text(
                            text = shown.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        if (!fromStart) {
                            Icon(shown.icon, contentDescription = null, tint = onContainer,
                                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetPx }
                .then(dragModifier),
        ) {
            content()
        }
    }
}
