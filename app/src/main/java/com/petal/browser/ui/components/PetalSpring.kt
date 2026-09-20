package com.petal.browser.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Single source of truth for Petal Compose motion (springs/tweens). The View-system
 * counterpart is com.petal.browser.motion.PetalMotion (Java).
 *
 * Rule of thumb:
 *  - Spatial  : things that MOVE or RESIZE  -> spring, no bounce
 *  - Effects  : things that only FADE/TINT  -> short tween
 *  - Press    : finger-down feedback        -> stiff spring, tiny overshoot
 *  - Playful  : rare accents (toggle, FAB)  -> the only place bounce is allowed
 *
 * Add new motion here instead of writing inline spring(...) / tween(...) calls.
 */
object PetalSpring {

    /** M3 emphasized-decelerate curve, same values already used in .rules. */
    val EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Layout moves, reorders, sheets, size changes. Calm, no bounce. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Element entering the screen. A hint of life, but not wobbly. */
    fun <T> enter(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Element leaving. Faster than enter so the UI feels responsive. */
    fun <T> exit(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Finger-down / finger-up scale feedback. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 400f,
    )

    /** Use sparingly: toggles, FAB, celebratory moments. */
    fun <T> playful(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Pure opacity/colour changes. Fade-in slightly slower than fade-out. */
    fun <T> fadeIn(): FiniteAnimationSpec<T> = tween(durationMillis = 200, easing = EmphasizedEasing)
    fun <T> fadeOut(): FiniteAnimationSpec<T> = tween(durationMillis = 140, easing = EmphasizedEasing)

    /** Standard press depth so every tappable surface feels the same. */
    const val PRESS_SCALE_CARD = 0.97f
    const val PRESS_SCALE_BUTTON = 0.94f
    const val PRESS_SCALE_SMALL = 0.90f

    /** Max items that get a staggered entrance; the rest appear instantly. */
    const val STAGGER_MAX_ITEMS = 8
    const val STAGGER_STEP_MS = 30L
}

/**
 * True when the user turned system animations off (Developer options / Accessibility
 * "Remove animations"). Read once per composition location.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    // Same rule as com.petal.browser.motion.PetalMotion (Java): animators disabled
    // globally OR duration scale == 0 means the user wants no motion.
    return remember(context) {
        try {
            val disabled = !android.animation.ValueAnimator.areAnimatorsEnabled()
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            disabled || scale <= 0f
        } catch (_: Exception) {
            false
        }
    }
}

/** Returns [snap] when reduced motion is on, otherwise [spec]. */
fun <T> FiniteAnimationSpec<T>.orSnap(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else this
