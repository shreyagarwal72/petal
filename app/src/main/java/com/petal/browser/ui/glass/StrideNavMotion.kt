package com.petal.browser.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tap feedback the expressive way, from Stride: the element itself squashes on press and
 * springs back on release. Replaces the ripple.
 */
@Composable
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.9f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyPress"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * The nav icons' reaction to being chosen: a springy pop from small, a tilt that wobbles
 * back upright, and a hop that lands. The item being left gives a small dip. Nothing plays
 * on first composition, so showing the bar doesn't set every icon bouncing.
 */
@Composable
fun Modifier.navIconMotion(selected: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        coroutineScope {
            if (selected) {
                launch {
                    scale.snapTo(0.55f)
                    scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 520f))
                }
                launch {
                    tilt.snapTo(-18f)
                    tilt.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 360f))
                }
                launch {
                    hop.snapTo(-8f)
                    hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 420f))
                }
            } else {
                launch {
                    scale.snapTo(0.82f)
                    scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 600f))
                }
            }
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        rotationZ = tilt.value
        translationY = hop.value.dp.toPx()
    }
}

/**
 * One selection weight per item, 1 for the selected one and 0 for the rest, moved together
 * by one spring — the labels, the bar's width and the slab all derive from these, so they
 * stay a single motion.
 *
 * Stepped by hand: a long frame advances it by at most one normal frame, so the slide
 * starts after a stall instead of skipping.
 */
@Stable
class TabWeights<K>(keys: List<K>, selected: K) {
    private val values = keys.associateWith { mutableFloatStateOf(if (it == selected) 1f else 0f) }
    private val velocity = keys.associateWith { 0f }.toMutableMap()

    operator fun get(key: K): Float = values[key]?.floatValue ?: 0f

    suspend fun animateTo(selected: K) {
        var last = -1L
        while (true) {
            val settled = withFrameNanos { now ->
                val dt = if (last < 0) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(MAX_STEP)
                last = now
                var rest = true
                for ((key, state) in values) {
                    val target = if (key == selected) 1f else 0f
                    var x = state.floatValue
                    var v = velocity.getValue(key)
                    var left = dt
                    while (left > 0f) {
                        val h = minOf(left, SUB_STEP)
                        v += (-STIFFNESS * (x - target) - DAMPING * v) * h
                        x += v * h
                        left -= h
                    }
                    if (abs(x - target) < 0.001f && abs(v) < 0.01f) {
                        x = target
                        v = 0f
                    } else rest = false
                    state.floatValue = x
                    velocity[key] = v
                }
                rest
            }
            if (settled) break
        }
    }

    private companion object {
        const val STIFFNESS = 380f
        val DAMPING = 2f * 0.85f * sqrt(STIFFNESS)
        const val MAX_STEP = 1f / 60f
        const val SUB_STEP = 1f / 480f
    }
}
