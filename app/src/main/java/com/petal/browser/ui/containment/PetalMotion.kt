package com.petal.browser.ui.containment

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

object PetalMotion {
    const val Quick = 160
    const val Standard = 300
    const val Emphasized = 440

    fun <T> spatialFast() = spring<T>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
    fun <T> spatialDefault() = spring<T>(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow)
    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow)
    fun <T> effectsFast() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    fun <T> effectsDefault() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    fun <T> effectsSlow() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)

    fun forwardEnter(): EnterTransition = fadeIn(effectsDefault()) + slideInHorizontally(spatialDefault()) { it / 10 } + scaleIn(spatialFast(), initialScale = 0.96f)
    fun forwardExit(): ExitTransition = fadeOut(effectsFast()) + scaleOut(effectsDefault(), targetScale = 0.985f)
    fun backEnter(): EnterTransition = fadeIn(effectsDefault()) + slideInHorizontally(spatialDefault()) { -it / 12 } + scaleIn(spatialFast(), initialScale = 0.96f)
    fun backExit(): ExitTransition = fadeOut(effectsFast()) + slideOutHorizontally(spatialDefault()) { it / 12 } + scaleOut(effectsDefault(), targetScale = 0.985f)

    fun quickTween() = tween<Float>(Quick, easing = FastOutSlowInEasing)
    fun standardTween() = tween<Float>(Standard, easing = FastOutSlowInEasing)
    fun emphasizedTween() = tween<Float>(Emphasized, easing = FastOutSlowInEasing)
}

@Composable
fun Modifier.petalPressScale(interactionSource: MutableInteractionSource, targetScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) targetScale else 1f, PetalMotion.spatialFast(), label = "petalPressScale")
    return scale(scale)
}

@Composable
fun Modifier.petalBounceClickable(enabled: Boolean = true, targetScale: Float = 0.97f, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return petalPressScale(source, targetScale).clickable(source, ripple(), enabled, onClick = onClick)
}
