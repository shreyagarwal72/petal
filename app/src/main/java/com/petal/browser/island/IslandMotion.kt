package com.petal.browser.island

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

enum class IslandAnimationStyle { EXPRESSIVE, EASE_IN_OUT }
enum class IslandAnimationSpeed { SLOW, DEFAULT, FAST }
enum class IslandAnimationBounce { SMALL, NORMAL, BIG }

/**
 * Builds Material 3 Expressive spatial springs and motion curves from user animation settings.
 * Full 1:1 parity with expressive-cutout motion physics.
 */
class IslandMotion(
    val style: IslandAnimationStyle = IslandAnimationStyle.EXPRESSIVE,
    val speed: IslandAnimationSpeed = IslandAnimationSpeed.DEFAULT,
    val bounce: IslandAnimationBounce = IslandAnimationBounce.NORMAL,
    val animationDurationMs: Int = BASE_TRANSITION_MS
) {
    private val animScale = animationDurationMs / BASE_TRANSITION_MS.toFloat()
    private val expressive = style == IslandAnimationStyle.EXPRESSIVE

    private fun scaled(baseMs: Int) = (baseMs * animScale).roundToInt()

    fun float(baseMs: Int = BASE_TRANSITION_MS): FiniteAnimationSpec<Float> =
        if (expressive) spatialSpec(speed, bounce, visibilityThreshold = 0.001f)
        else tween(durationMillis = scaled(baseMs), easing = EaseInOutEasing)

    fun dp(): FiniteAnimationSpec<Dp> =
        if (expressive) spatialSpec(speed, bounce, visibilityThreshold = Dp.VisibilityThreshold)
        else tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)

    fun intSize(): FiniteAnimationSpec<IntSize> =
        if (expressive) spatialSpec(speed, bounce, visibilityThreshold = IntSize.VisibilityThreshold)
        else tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)

    fun dpSmooth(): FiniteAnimationSpec<Dp> =
        if (expressive) spring(dampingRatio = 1f, stiffness = spatialStiffness(speed), visibilityThreshold = Dp.VisibilityThreshold)
        else tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)

    fun dpFirm(): FiniteAnimationSpec<Dp> =
        if (expressive) spring(dampingRatio = FIRM_DAMPING, stiffness = spatialStiffness(speed), visibilityThreshold = Dp.VisibilityThreshold)
        else tween(durationMillis = scaled(BASE_TRANSITION_MS), easing = EaseInOutEasing)

    fun boop(): FiniteAnimationSpec<Float> =
        if (expressive) spring(dampingRatio = 1f, stiffness = boopStiffness(speed), visibilityThreshold = 0.0005f)
        else tween(durationMillis = scaled(140), easing = EaseInOutEasing)

    fun swapScale(): FiniteAnimationSpec<Float> =
        if (expressive) effectsSpec(speed)
        else tween(durationMillis = SWAP_MS)

    fun swapFade(): FiniteAnimationSpec<Float> =
        if (expressive) effectsSpec(speed)
        else tween(durationMillis = SWAP_MS)

    val swapMinScale: Float = if (expressive) 0.5f else 0f

    companion object {
        const val BASE_TRANSITION_MS = 220
        private val EaseInOutEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        private const val POP_DAMPING = 0.55f
        private const val FIRM_DAMPING = 0.9f
        private const val SWAP_MS = 200

        val Default = IslandMotion()

        fun <T> spatialSpec(
            speed: IslandAnimationSpeed,
            bounce: IslandAnimationBounce,
            visibilityThreshold: T? = null
        ): SpringSpec<T> {
            val stiffness = spatialStiffness(speed)
            val dampingRatio = when (bounce) {
                IslandAnimationBounce.BIG -> 0.45f
                IslandAnimationBounce.NORMAL -> 0.6f
                IslandAnimationBounce.SMALL -> 0.8f
            }
            return spring(dampingRatio = dampingRatio, stiffness = stiffness, visibilityThreshold = visibilityThreshold)
        }

        fun spatialStiffness(speed: IslandAnimationSpeed): Float = when (speed) {
            IslandAnimationSpeed.SLOW -> 170f
            IslandAnimationSpeed.DEFAULT -> 380f
            IslandAnimationSpeed.FAST -> 800f
        }

        fun boopStiffness(speed: IslandAnimationSpeed): Float = when (speed) {
            IslandAnimationSpeed.SLOW -> 400f
            IslandAnimationSpeed.DEFAULT -> 900f
            IslandAnimationSpeed.FAST -> 1800f
        }

        fun effectsSpec(speed: IslandAnimationSpeed): SpringSpec<Float> = when (speed) {
            IslandAnimationSpeed.SLOW -> spring(dampingRatio = 1f, stiffness = 300f)
            IslandAnimationSpeed.DEFAULT -> spring(dampingRatio = 1f, stiffness = 700f)
            IslandAnimationSpeed.FAST -> spring(dampingRatio = 1f, stiffness = 1600f)
        }
    }
}
