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
    if (PetalLaunchTracker.isHomeLaunchAnimated) {
        return this
    }

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
        PetalLaunchTracker.isHomeLaunchAnimated = true
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
 * Expressive tap feedback: squashes on press and springs back on release.
 */
@Composable
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.94f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bouncyPress",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = {
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
            onClick()
        },
    )
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
