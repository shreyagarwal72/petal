package com.petal.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import com.petal.browser.ui.theme.PetalMotionPhysics

/**
 * Fast, lightweight staggered entrance animation: fade-in + subtle Y translation.
 * Uses Material 3 Expressive motion physics.
 */
@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    val spatialSpec = PetalMotionPhysics.fastSpatial
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spatialSpec
        )
    }
    return graphicsLayer {
        alpha = animProgress.value
        translationY = (1f - animProgress.value) * 16.dp.toPx()
    }
}

/**
 * Expressive tap feedback: squashes on press and springs back on release.
 * Uses Material 3 Expressive fast spatial spring physics.
 */
@Composable
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.94f,
    enabled: Boolean = true,
    onClick: () -> Unit,
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
        animationSpec = PetalMotionPhysics.fastSpatial,
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

/**
 * Material 3 Expressive Button Press Effect (https://m3.material.io/components/buttons/overview).
 * Applies tactile scale compression (fastSpatial spring), haptic tick feedback on press down,
 * and release spring recovery.
 */
@Composable
fun Modifier.expressiveButtonPress(
    scaleDown: Float = 0.94f,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pressed by interactionSource.collectIsPressedAsState()
    
    LaunchedEffect(pressed) {
        if (pressed && enabled) {
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.TICK, 0.45f)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = PetalMotionPhysics.fastSpatial,
        label = "m3ButtonPressScale",
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
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

/** Lightweight slide-in entrance from the side using Material 3 Expressive motion physics. */
@Composable
fun Modifier.slideInSpring(fromRight: Boolean = false, index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    val spatialSpec = PetalMotionPhysics.fastSpatial
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spatialSpec
        )
    }
    val startX = if (fromRight) 30.dp else (-30).dp
    return graphicsLayer {
        alpha = animProgress.value
        translationX = (1f - animProgress.value) * startX.toPx()
    }
}

/** Fast pop-in for icons and badges using Material 3 Expressive motion physics. */
@Composable
fun Modifier.popIn(index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    val spatialSpec = PetalMotionPhysics.fastSpatial
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spatialSpec
        )
    }
    return graphicsLayer {
        alpha = animProgress.value
        scaleX = animProgress.value
        scaleY = animProgress.value
    }
}

/** Springy reveal container using Material 3 Expressive motion physics. */
@Composable
fun Modifier.springReveal(index: Int = 0): Modifier {
    val animProgress = remember { Animatable(0f) }
    val spatialSpec = PetalMotionPhysics.slowSpatial
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spatialSpec
        )
    }
    return graphicsLayer {
        alpha = animProgress.value
        scaleY = 0.95f + (animProgress.value * 0.05f)
        translationY = (1f - animProgress.value) * 12.dp.toPx()
    }
}
