package com.petal.browser.now

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import com.petal.browser.ui.components.PetalSpring
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Petal-native Material 3 Expressive motion specs for Petal Now.
 *
 * Separates spatial resizing springs, critically damped incremental-growth springs,
 * firm anchored geometry, monotonic alpha/color effects, and velocity-preserving
 * single-arc pops while respecting both [PetalSpring.timeScale] and [reduceMotion].
 */
internal class PetalNowMotion(
    private val prefs: PetalNowPrefs,
    val reduceMotion: Boolean,
) {
    private val expressive: Boolean = !reduceMotion && prefs.animStyle == PetalNowAnimStyle.EXPRESSIVE
    private val timeScale: Float = PetalSpring.timeScale

    private fun scaledMs(baseMs: Int): Int = (baseMs * timeScale).roundToInt().coerceAtLeast(1)

    private fun scaledStiffness(base: Float): Float {
        val s = timeScale
        return (base / (s * s)).coerceIn(20f, 12_000f)
    }

    private fun baseSpatialStiffness(): Float = when (prefs.animSpeed) {
        PetalNowAnimSpeed.SLOW -> 190f
        PetalNowAnimSpeed.DEFAULT -> 400f
        PetalNowAnimSpeed.FAST -> 820f
    }

    private fun boopBaseStiffness(): Float = when (prefs.animSpeed) {
        PetalNowAnimSpeed.SLOW -> 420f
        PetalNowAnimSpeed.DEFAULT -> 920f
        PetalNowAnimSpeed.FAST -> 1850f
    }

    private fun spatialDamping(): Float = when (prefs.animBounce) {
        PetalNowAnimBounce.SMALL -> 0.80f
        PetalNowAnimBounce.NORMAL -> 0.62f
        PetalNowAnimBounce.BIG -> 0.48f
    }

    private fun <T> spatialSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = spatialDamping(),
        stiffness = scaledStiffness(baseSpatialStiffness()),
        visibilityThreshold = visibilityThreshold,
    )

    /** General spatial transition for container width, corners, and offsets. */
    fun dp(baseMs: Int = BASE_MS): AnimationSpec<Dp> = when {
        reduceMotion -> snap()
        expressive -> spatialSpring(visibilityThreshold = Dp.VisibilityThreshold)
        else -> tween(durationMillis = scaledMs(baseMs), easing = PetalSpring.EmphasizedEasing)
    }

    /** Critically damped spatial transition for content heights settling incrementally. */
    fun dpSmooth(baseMs: Int = BASE_MS): AnimationSpec<Dp> = when {
        reduceMotion -> snap()
        expressive -> spring(
            dampingRatio = 1f,
            stiffness = scaledStiffness(baseSpatialStiffness()),
            visibilityThreshold = Dp.VisibilityThreshold,
        )
        else -> tween(durationMillis = scaledMs(baseMs), easing = PetalSpring.EmphasizedEasing)
    }

    /** Near-critical spatial transition for edge-anchored cards with minimal overshoot. */
    fun dpFirm(baseMs: Int = BASE_MS): AnimationSpec<Dp> = when {
        reduceMotion -> snap()
        expressive -> spring(
            dampingRatio = 0.90f,
            stiffness = scaledStiffness(baseSpatialStiffness()),
            visibilityThreshold = Dp.VisibilityThreshold,
        )
        else -> tween(durationMillis = scaledMs(baseMs), easing = PetalSpring.EmphasizedEasing)
    }

    /** Fraction reveal / progress spatial spec (clamped by consumers when undershooting). */
    fun float(baseMs: Int = BASE_MS): FiniteAnimationSpec<Float> = when {
        reduceMotion -> snap()
        expressive -> spatialSpring(visibilityThreshold = 0.001f)
        else -> tween(durationMillis = scaledMs(baseMs), easing = PetalSpring.EmphasizedEasing)
    }

    /** Critically damped alpha / tint effects so transitions never over-brighten. */
    fun fade(baseMs: Int = EFFECTS_MS): FiniteAnimationSpec<Float> = when {
        reduceMotion -> snap()
        expressive -> spring(
            dampingRatio = 1f,
            stiffness = scaledStiffness(
                when (prefs.animSpeed) {
                    PetalNowAnimSpeed.SLOW -> 320f
                    PetalNowAnimSpeed.DEFAULT -> 720f
                    PetalNowAnimSpeed.FAST -> 1600f
                }
            ),
            visibilityThreshold = 0.002f,
        )
        else -> tween(durationMillis = scaledMs(baseMs), easing = PetalSpring.EmphasizedDecelerateEasing)
    }

    /** Stiff press-down / release spring carrying finger-release momentum cleanly. */
    fun boop(): FiniteAnimationSpec<Float> = when {
        reduceMotion -> snap()
        expressive -> spring(
            dampingRatio = 1f,
            stiffness = scaledStiffness(boopBaseStiffness()),
            visibilityThreshold = 0.0005f,
        )
        else -> tween(durationMillis = scaledMs(130), easing = PetalSpring.EmphasizedEasing)
    }

    /** Expressive M3E polygon / state-flip morph spec (used e.g. for Ad Blocker ON <-> OFF). */
    fun shapeMorph(): FiniteAnimationSpec<Float> = when {
        reduceMotion -> snap()
        expressive -> spring(
            dampingRatio = 0.68f,
            stiffness = scaledStiffness(460f),
            visibilityThreshold = 0.001f,
        )
        else -> tween(durationMillis = scaledMs(240), easing = PetalSpring.EmphasizedEasing)
    }

    /**
     * Single-arc swell triggered on confirmations / hero flips: launched from `1f` with initial
     * velocity calibrated to peak near [peak] before settling back to `1f`.
     */
    suspend fun pop(scale: Animatable<Float, AnimationVector1D>, peak: Float = 1.07f) {
        if (reduceMotion) {
            scale.snapTo(1f)
            return
        }
        if (expressive) {
            val stiffness = scaledStiffness(baseSpatialStiffness())
            val velocity = (peak - 1f) * sqrt(stiffness) / POP_PEAK_RATIO
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = POP_DAMPING,
                    stiffness = stiffness,
                    visibilityThreshold = 0.0005f,
                ),
                initialVelocity = velocity,
            )
        } else {
            scale.animateTo(peak, tween(scaledMs(75), easing = PetalSpring.EmphasizedEasing))
            scale.animateTo(1f, tween(scaledMs(150), easing = PetalSpring.EmphasizedDecelerateEasing))
        }
    }

    /** Quick badge arrival pop when a live card updates inside an already visible capsule. */
    suspend fun popIn(scale: Animatable<Float, AnimationVector1D>) {
        if (reduceMotion) {
            scale.snapTo(1f)
            return
        }
        scale.snapTo(0.62f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = float(190),
        )
    }

    companion object {
        private const val BASE_MS = 230
        private const val EFFECTS_MS = 190
        private const val POP_DAMPING = 0.56f
        private const val POP_PEAK_RATIO = 0.514f
    }
}
