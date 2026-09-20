package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine

object PetalLaunchTracker {
    var isHomeLaunchAnimated: Boolean = false
}

/**
 * Remembers which keyed items already played their entrance so a LazyColumn/Grid item
 * that scrolls off and back on does NOT replay. Process-scoped on purpose: cheap, and
 * a fresh app launch should animate again.
 */
private object PetalEntranceRegistry {
    private const val MAX = 400
    private val seen = LinkedHashSet<Int>()

    fun hasPlayed(key: Int): Boolean = seen.contains(key)

    fun markPlayed(key: Int) {
        if (seen.size >= MAX) {
            val it = seen.iterator()
            if (it.hasNext()) { it.next(); it.remove() }
        }
        seen.add(key)
    }
}

/**
 * Entrance animation for the Home Screen that plays ONLY on initial app launch.
 * On subsequent navigation visits to the Home screen, it skips animation.
 */
@Composable
fun Modifier.homeLaunchEntrance(index: Int = 0): Modifier {
    val reduceMotion = rememberReduceMotion()
    val isAlreadyAnimated = PetalLaunchTracker.isHomeLaunchAnimated || reduceMotion
    val animProgress = remember { Animatable(if (isAlreadyAnimated) 1f else 0f) }

    LaunchedEffect(index) {
        if (!isAlreadyAnimated && animProgress.value < 1f) {
            if (index > 0) {
                kotlinx.coroutines.delay((index * PetalSpring.STAGGER_STEP_MS).coerceAtMost(280L))
            }
            animProgress.animateTo(1f, PetalSpring.enter())
        } else if (animProgress.value < 1f) {
            animProgress.snapTo(1f)
        }
        PetalLaunchTracker.isHomeLaunchAnimated = true
    }

    return graphicsLayer {
        val progress = if (PetalLaunchTracker.isHomeLaunchAnimated) 1f else animProgress.value
        val currentScale = 0.93f + (0.07f * progress)
        alpha = 1f
        scaleX = currentScale
        scaleY = currentScale
        translationY = (1f - progress) * 20.dp.toPx()
    }
}

/**
 * Staggered entrance: fade-in + subtle Y translation.
 *
 * Fixes vs. the old version:
 *  - Only the first [PetalSpring.STAGGER_MAX_ITEMS] items stagger; the rest just fade quickly.
 *  - Items that already played never replay when scrolled back into view.
 *  - Reduced-motion users get no animation.
 *
 * @param playKey optional stable key (e.g. item id hash). When null, falls back to [index],
 * which is fine for short fixed lists but WILL suppress replay across different lists that
 * reuse the same index, so pass a real key for data-driven lists.
 */
@Composable
fun Modifier.entrance(index: Int = 0, playKey: Any? = null): Modifier {
    val reduceMotion = rememberReduceMotion()
    val registryKey = remember(playKey) { playKey?.hashCode() }
    val alreadyPlayed = remember(registryKey) {
        reduceMotion || (registryKey != null && PetalEntranceRegistry.hasPlayed(registryKey))
    }
    val animProgress = remember { Animatable(if (alreadyPlayed) 1f else 0f) }

    LaunchedEffect(registryKey) {
        if (animProgress.value >= 1f) return@LaunchedEffect
        val staggerIndex = index.coerceAtMost(PetalSpring.STAGGER_MAX_ITEMS)
        if (staggerIndex > 0) {
            kotlinx.coroutines.delay(staggerIndex * PetalSpring.STAGGER_STEP_MS)
        }
        animProgress.animateTo(1f, PetalSpring.enter())
        registryKey?.let { PetalEntranceRegistry.markPlayed(it) }
    }

    return graphicsLayer {
        val progress = animProgress.value
        val currentScale = 0.95f + (0.05f * progress)
        alpha = progress
        scaleX = currentScale
        scaleY = currentScale
        translationY = (1f - progress) * 14.dp.toPx()
    }
}

/**
 * Tactile touch feedback layered on existing click handlers without changing their behavior.
 * Scale is read inside graphicsLayer, so a press does NOT recompose the caller.
 */
@Composable
fun Modifier.petalTouchFeedback(
    scaleDown: Float = PetalSpring.PRESS_SCALE_BUTTON,
    enabled: Boolean = true,
): Modifier {
    val reduceMotion = rememberReduceMotion()
    val pressedState = remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (enabled && pressedState.value) scaleDown.coerceIn(0.80f, 1f) else 1f,
        animationSpec = PetalSpring.press<Float>().orSnap(reduceMotion),
        label = "petalTouchScale",
    )

    return this
        .graphicsLayer {
            val s = scale.value
            scaleX = s
            scaleY = s
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressedState.value = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressedState.value = false
            }
        }
}

/**
 * Expressive tap feedback: compresses on press, springs back on release.
 *
 * Fixes vs. the old version:
 *  - ONE haptic per tap (light tick on press-down only, click haptic removed from onClick
 *    was doubling the vibration). Press-down tick is kept because it feels instant.
 *  - Scale is read inside graphicsLayer (no recomposition per frame).
 *  - Reduced-motion users get haptics but no scale animation.
 */
@Composable
fun Modifier.bouncyClickable(
    scaleDown: Float = PetalSpring.PRESS_SCALE_CARD,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentOnClick = rememberUpdatedState(onClick)

    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            PetalHapticEngine.getInstance(context)
                .playIfEnabled(context, PetalHapticEngine.Pattern.TICK, 0.45f)
        }
    }

    val scale = animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = PetalSpring.press<Float>().orSnap(reduceMotion),
        label = "bouncyPress",
    )
    val base = graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
    return if (onClick != null) {
        base.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = { currentOnClick.value?.invoke() },
        )
    } else {
        base
    }
}

/** Gentle infinite breathing scale for active elements. Disabled for reduced motion. */
@Composable
fun Modifier.pulse(from: Float = 1f, to: Float = 1.08f, durationMs: Int = 1800): Modifier {
    if (rememberReduceMotion()) return this
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale = transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}

/** Lightweight slide-in entrance from the side. */
@Composable
fun Modifier.slideInSpring(fromRight: Boolean = false, index: Int = 0): Modifier {
    val reduceMotion = rememberReduceMotion()
    val animProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (animProgress.value >= 1f) return@LaunchedEffect
        val staggerIndex = index.coerceAtMost(PetalSpring.STAGGER_MAX_ITEMS)
        if (staggerIndex > 0) kotlinx.coroutines.delay(staggerIndex * PetalSpring.STAGGER_STEP_MS)
        animProgress.animateTo(1f, PetalSpring.spatial())
    }
    val startX = if (fromRight) 30.dp else (-30).dp
    return graphicsLayer {
        alpha = animProgress.value
        translationX = (1f - animProgress.value) * startX.toPx()
    }
}

/** Fast pop-in for icons and badges. Playful by design. */
@Composable
fun Modifier.popIn(index: Int = 0): Modifier {
    val reduceMotion = rememberReduceMotion()
    val animProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (animProgress.value >= 1f) return@LaunchedEffect
        val staggerIndex = index.coerceAtMost(PetalSpring.STAGGER_MAX_ITEMS)
        if (staggerIndex > 0) kotlinx.coroutines.delay(staggerIndex * PetalSpring.STAGGER_STEP_MS)
        animProgress.animateTo(1f, PetalSpring.playful())
    }
    return graphicsLayer {
        val p = animProgress.value
        alpha = p.coerceIn(0f, 1f)
        scaleX = p
        scaleY = p
    }
}

/** Springy reveal container. */
@Composable
fun Modifier.springReveal(index: Int = 0): Modifier {
    val reduceMotion = rememberReduceMotion()
    val animProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (animProgress.value >= 1f) return@LaunchedEffect
        val staggerIndex = index.coerceAtMost(PetalSpring.STAGGER_MAX_ITEMS)
        if (staggerIndex > 0) kotlinx.coroutines.delay(staggerIndex * PetalSpring.STAGGER_STEP_MS)
        animProgress.animateTo(1f, PetalSpring.spatial())
    }
    return graphicsLayer {
        val p = animProgress.value
        alpha = p
        scaleY = 0.95f + (p * 0.05f)
        translationY = (1f - p) * 12.dp.toPx()
    }
}

/** Material-style fade-through for replacing content without moving the browser surface. */
@Composable
fun Modifier.fadeThrough(visible: Boolean, durationMs: Int = 180): Modifier {
    val reduceMotion = rememberReduceMotion()
    val alpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) {
            androidx.compose.animation.core.snap()
        } else {
            tween(durationMs, easing = LinearOutSlowInEasing)
        },
        label = "fadeThrough",
    )
    return graphicsLayer { this.alpha = alpha.value }
}

/** Compact scale used for menus, sheets and transient surfaces. */
@Composable
fun Modifier.surfacePopIn(visible: Boolean = true): Modifier {
    val reduceMotion = rememberReduceMotion()
    val scale = animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = PetalSpring.spatial<Float>().orSnap(reduceMotion),
        label = "surfacePopIn",
    )
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}
