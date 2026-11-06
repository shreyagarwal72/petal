package com.petal.browser.ui.components

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Single source of truth for Petal Compose motion (springs/tweens). The View-system
 * counterpart is com.petal.browser.motion.PetalMotion (Java).
 *
 * ═════════════════════════════════════════════════════════════════════════════
 * MOTION RULES & FOUNDATION:
 * 1. Animate ONLY scale, translation, alpha, and clip.
 * 2. Read animated values INSIDE graphicsLayer { ... } or draw blocks, NEVER
 *    during composition to eliminate redundant recompositions and layout passes.
 * 3. Use springs (PetalSpring.*) for anything a tap or gesture can interrupt.
 * ═════════════════════════════════════════════════════════════════════════════
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

    /** Live "Motion Duration Scale" set in Appearance -> Layout & Controls. */
    val timeScale: Float
        get() = try {
            com.petal.browser.ui.theme.layout.PetalLayoutState.settings.motionDurationScale.coerceIn(0.25f, 3f)
        } catch (_: Throwable) { 1f }

    private fun scaledStiffness(base: Float): Float {
        val s = timeScale
        return (base / (s * s)).coerceIn(10f, 10000f)
    }

    private fun scaledDuration(ms: Int): Int {
        val s = timeScale
        return (ms * s).toInt().coerceAtLeast(1)
    }

    /** M3 emphasized-decelerate curve, same values already used in .rules. */
    val EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Layout moves, reorders, sheets, size changes. Calm, no bounce. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = scaledStiffness(Spring.StiffnessMediumLow),
    )

    /** Element entering the screen. A hint of life, but not wobbly. */
    fun <T> enter(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = scaledStiffness(Spring.StiffnessMediumLow),
    )

    /** Element leaving. Faster than enter so the UI feels responsive. */
    fun <T> exit(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = scaledStiffness(Spring.StiffnessMedium),
    )

    /** Finger-down / finger-up scale feedback. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = scaledStiffness(400f),
    )

    /** Use sparingly: toggles, FAB, celebratory moments. */
    fun <T> playful(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = scaledStiffness(Spring.StiffnessMedium),
    )

    /** Material 3 Expressive Dialogs: snappy, tactile pop-in with controlled settle. */
    fun <T> dialog(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = scaledStiffness(380f),
    )

    /** Expressive Sheets: smooth decelerating slide with subtle spring cushion. */
    fun <T> sheet(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = scaledStiffness(320f),
    )

    /** Sheet drag settle: when finger lifts during sheet drag gestures. */
    fun <T> sheetDragSettle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = scaledStiffness(350f),
    )

    /** Menu pop (origin-anchored scale): fast, crisp expansion with slight tactile settle. */
    fun <T> menuPop(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = scaledStiffness(450f),
    )

    /** Menu origin-anchored scale: opens with tactile settle. */
    fun <T> menuScale(): FiniteAnimationSpec<T> = menuPop()

    /** Menu item fade: fades in as the menu expands. */
    fun <T> menuItemFade(): FiniteAnimationSpec<T> = tween(durationMillis = scaledDuration(160), easing = LinearOutSlowInEasing)

    /** Menu exit scale: closes faster than it opens. */
    fun <T> menuCloseScale(): FiniteAnimationSpec<T> = tween(durationMillis = scaledDuration(110), easing = FastOutLinearInEasing)

    /** Menu exit fade: snappy fade-out. */
    fun <T> menuCloseFade(): FiniteAnimationSpec<T> = tween(durationMillis = scaledDuration(90), easing = FastOutLinearInEasing)

    /** Dialog pop-in: tactile scale spring. */
    fun <T> dialogScaleIn(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = scaledStiffness(450f),
    )

    /** Dialog matching exit scale: snappy dismiss. */
    fun <T> dialogScaleOut(): FiniteAnimationSpec<T> = tween(
        durationMillis = scaledDuration(130),
        easing = FastOutLinearInEasing
    )

    /** Dialog fade-in. */
    fun <T> dialogFadeIn(): FiniteAnimationSpec<T> = tween(
        durationMillis = scaledDuration(180),
        easing = LinearOutSlowInEasing
    )

    /** Dialog matching exit fade. */
    fun <T> dialogFadeOut(): FiniteAnimationSpec<T> = tween(
        durationMillis = scaledDuration(120),
        easing = FastOutLinearInEasing
    )

    /** List reorder: calm, critically damped spring for moving items in a reordering list. */
    fun <T> listReorder(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = scaledStiffness(420f),
    )

    /** Container morphs (search bar -> page, tile -> page). Fast, no bounce, interruptible. */
    fun <T> container(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = scaledStiffness(520f),
    )

    /** Alias for container morph token. */
    fun <T> containerMorph(): FiniteAnimationSpec<T> = container()

    /** Pure opacity/colour changes. Fade-in slightly slower than fade-out. */
    fun <T> fadeIn(): FiniteAnimationSpec<T> = tween(durationMillis = scaledDuration(200), easing = EmphasizedEasing)
    fun <T> fadeOut(): FiniteAnimationSpec<T> = tween(durationMillis = scaledDuration(140), easing = EmphasizedEasing)

    /** Crossfades of icons/labels (selected state, collapsing-bar buttons). Short, symmetric. */
    fun <T> crossfade(): FiniteAnimationSpec<T> = tween(durationMillis = scaledDuration(200), easing = FastOutSlowInEasing)

    /** M3 Expressive Fade-Through: incoming content. */
    fun <T> fadeThroughIn(): FiniteAnimationSpec<T> = tween(
        durationMillis = scaledDuration(210),
        delayMillis = scaledDuration(90),
        easing = EmphasizedDecelerateEasing
    )

    /** M3 Expressive Fade-Through: outgoing content. */
    fun <T> fadeThroughOut(): FiniteAnimationSpec<T> = tween(
        durationMillis = scaledDuration(90),
        easing = EmphasizedAccelerateEasing
    )

    /** Progress and favicon fade: quick, subtle crossfade for status indicators and icons. */
    fun <T> progressFade(): FiniteAnimationSpec<T> = tween(
        durationMillis = scaledDuration(150),
        easing = FastOutSlowInEasing
    )

    /** Favicon fade: identical to progressFade for icon loading transitions. */
    fun <T> faviconFade(): FiniteAnimationSpec<T> = progressFade()

    /**
     * Segment / button-group weight changes. A critically damped spring instead of a fixed tween,
     * so rapid taps re-target smoothly with velocity instead of restarting a 240ms curve.
     */
    fun <T> segment(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = scaledStiffness(500f),
    )

    /** Standard press depth so every tappable surface feels the same. */
    const val PRESS_SCALE_CARD = 0.97f
    const val PRESS_SCALE_BUTTON = 0.94f
    const val PRESS_SCALE_SMALL = 0.90f

    /** Max items that get a staggered entrance; the rest appear instantly. */
    const val STAGGER_MAX_ITEMS = 8
    const val STAGGER_STEP_MS = 30L
}

/**
 * Applies motion transforms strictly within the graphics layer / draw phase,
 * avoiding recomposition on every animated frame.
 */
fun Modifier.petalMotionLayer(
    scaleX: () -> Float = { 1f },
    scaleY: () -> Float = { 1f },
    translationX: () -> Float = { 0f },
    translationY: () -> Float = { 0f },
    alpha: () -> Float = { 1f },
    clip: Boolean = false,
    shape: Shape = RectangleShape
): Modifier = this.graphicsLayer {
    this.scaleX = scaleX()
    this.scaleY = scaleY()
    this.translationX = translationX()
    this.translationY = translationY()
    this.alpha = alpha()
    this.clip = clip
    this.shape = shape
}

/**
 * True when the user turned system animations off (Developer options / Accessibility
 * "Remove animations"). Read once per composition location.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        com.petal.browser.motion.MotionGate.isReduceMotion(context)
    }
}

/** Returns [snap] when reduced motion is on, otherwise [spec]. */
fun <T> FiniteAnimationSpec<T>.orSnap(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else this

/** True on low-RAM devices, where decorative blur / endless motion should be skipped. */
@Composable
fun rememberLowEndDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        com.petal.browser.motion.MotionGate.isLowEndDevice(context)
    }
}

/**
 * True while the host activity is at least STARTED. Endless decorative animations should
 * read this and stop when it is false, so a backgrounded or covered screen costs no frames.
 */
@Composable
fun rememberIsForeground(): Boolean {
    val context = LocalContext.current
    val owner = remember(context) {
        var c: Context? = context
        while (c is ContextWrapper && c !is LifecycleOwner) c = c.baseContext
        c as? LifecycleOwner
    } ?: return true
    val state: State<Boolean> = remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ ->
            (state as androidx.compose.runtime.MutableState<Boolean>).value =
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return state.value
}

/**
 * True while the host lifecycle is RESUMED and system animations (animator-duration scale)
 * are enabled. When false, infinite/looping animations should pause to conserve CPU/battery
 * and honor user accessibility preferences.
 */
@Composable
fun rememberAnimationActive(): Boolean {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) return false

    val owner = remember(context) {
        var c: Context? = context
        while (c is ContextWrapper && c !is LifecycleOwner) c = c.baseContext
        c as? LifecycleOwner
    } ?: return true

    val state = remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ ->
            state.value = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return state.value
}
