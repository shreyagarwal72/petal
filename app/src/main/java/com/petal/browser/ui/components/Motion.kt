package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object PetalLaunchTracker {
    var isHomeLaunchAnimated: Boolean = false
}

/**
 * Entrance animation for the Home Screen that plays ONLY on initial app launch.
 * On subsequent navigation visits to the Home screen, it skips animation.
 */
@Composable
fun Modifier.homeLaunchEntrance(index: Int = 0): Modifier {
    val isAlreadyAnimated = PetalLaunchTracker.isHomeLaunchAnimated
    val animProgress = remember { Animatable(if (isAlreadyAnimated) 1f else 0f) }

    LaunchedEffect(index) {
        if (!isAlreadyAnimated && animProgress.value < 1f) {
            if (index > 0) {
                kotlinx.coroutines.delay((index * 35L).coerceAtMost(280L))
            }
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
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
 * Fast, lightweight staggered entrance animation: fade-in + subtle Y translation.
 */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        if (index > 0) {
            kotlinx.coroutines.delay((index * 35L).coerceAtMost(280L))
        }
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return graphicsLayer {
        val progress = animProgress.value
        val currentScale = 0.93f + (0.07f * progress)
        alpha = progress
        scaleX = currentScale
        scaleY = currentScale
        translationY = (1f - progress) * 20.dp.toPx()
    }
}

/**
 * Material 3 Expressive-style tactile touch feedback that can be layered on
 * existing click handlers without replacing their semantics or behavior.
 * The touched surface compresses to 92% and springs back naturally.
 */
@Composable
fun Modifier.petalTouchFeedback(
    scaleDown: Float = 0.92f,
    enabled: Boolean = true,
): Modifier {
    val pressedState = remember { mutableStateOf(false) }
    val pressed = pressedState.value
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) scaleDown.coerceIn(0.80f, 1f) else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f,
        ),
        label = "petalTouchScale",
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
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
 * Expressive tap feedback: squashes on press and springs back on release.
 */
@Composable
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.92f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
): Modifier {
    val context = androidx.compose.ui.platform.LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    
    LaunchedEffect(pressed) {
        if (pressed) {
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.TICK, 0.45f)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f,
        ),
        label = "bouncyPress",
    )
    val base = graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return if (onClick != null) {
        base.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = {
                com.petal.browser.haptics.PetalHapticEngine.getInstance(context).playClick(context)
                onClick()
            },
        )
    } else {
        base
    }
}

/** Gentle infinite breathing scale for active elements. */
@Composable
fun Modifier.pulse(from: Float = 1f, to: Float = 1.08f, durationMs: Int = 1800): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Lightweight slide-in entrance from the side. */
@Composable
fun Modifier.slideInSpring(fromRight: Boolean = false, index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    val startX = if (fromRight) 30.dp else (-30).dp
    return graphicsLayer {
        alpha = animProgress.value
        translationX = (1f - animProgress.value) * startX.toPx()
    }
}

/** Fast pop-in for icons and badges. */
@Composable
fun Modifier.popIn(index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    return graphicsLayer {
        alpha = animProgress.value
        scaleX = animProgress.value
        scaleY = animProgress.value
    }
}

/** Springy reveal container. */
@Composable
fun Modifier.springReveal(index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return graphicsLayer {
        alpha = animProgress.value
        scaleY = 0.95f + (animProgress.value * 0.05f)
        translationY = (1f - animProgress.value) * 12.dp.toPx()
    }
}


/** Material-style fade-through for replacing content without moving the browser surface. */
@Composable
fun Modifier.fadeThrough(visible: Boolean, durationMs: Int = 180): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMs, easing = LinearOutSlowInEasing),
        label = "fadeThrough"
    )
    return graphicsLayer { this.alpha = alpha }
}

/** Compact shared-element-like scale used for menus, sheets and transient surfaces. */
@Composable
fun Modifier.surfacePopIn(visible: Boolean = true): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "surfacePopIn"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
