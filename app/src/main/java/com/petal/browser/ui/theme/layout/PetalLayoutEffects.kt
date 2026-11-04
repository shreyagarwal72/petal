package com.petal.browser.ui.theme.layout

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Multiplies the *duration* of every animation spec produced by [base] by [timeScale]
 * (2.0 = twice as slow, 0.5 = twice as fast). Springs get stiffness / scale^2, which scales their
 * settle time by the same factor while keeping their bounciness; tweens scale their duration.
 */
class ScaledMotionScheme(private val base: MotionScheme, private val timeScale: Float) : MotionScheme {
    private fun <T> FiniteAnimationSpec<T>.scaled(): FiniteAnimationSpec<T> = when (this) {
        is SpringSpec<T> -> SpringSpec(dampingRatio, stiffness / (timeScale * timeScale), visibilityThreshold)
        is TweenSpec<T> -> TweenSpec(
            durationMillis = (durationMillis * timeScale).toInt().coerceAtLeast(1),
            delay = (delay * timeScale).toInt(),
            easing = easing
        )
        else -> this
    }

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = base.defaultSpatialSpec<T>().scaled()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = base.fastSpatialSpec<T>().scaled()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = base.slowSpatialSpec<T>().scaled()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = base.defaultEffectsSpec<T>().scaled()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = base.fastEffectsSpec<T>().scaled()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = base.slowEffectsSpec<T>().scaled()
}

/** Friction multiplier per fling style (higher = stops sooner). DEFAULT keeps Android's own physics. */
private fun PetalFlingType.friction(): Float? = when (this) {
    PetalFlingType.DEFAULT -> null
    PetalFlingType.SMOOTH -> 0.75f
    PetalFlingType.IOS_STYLE -> 0.55f
    PetalFlingType.SNAPPY -> 1.9f
    PetalFlingType.FLOATY -> 0.28f
    PetalFlingType.REDUCED_MOTION -> 7f
}

private class PetalDecayFling(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var lastValue = 0f
        var remaining = initialVelocity
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            remaining = velocity
            if (abs(delta - consumed) > 0.5f) cancelAnimation() // hit an edge
        }
        return remaining
    }
}

/** The fling behaviour matching the user's chosen [PetalFlingType]; follows changes live. */
@Composable
fun petalFlingBehavior(): FlingBehavior {
    val type = PetalLayoutState.settings.flingType
    val native = ScrollableDefaults.flingBehavior()
    return remember(type, native) {
        val f = type.friction()
        if (f == null) native else PetalDecayFling(exponentialDecay(frictionMultiplier = f))
    }
}
