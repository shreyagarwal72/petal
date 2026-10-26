package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.petal.browser.haptics.PetalHapticEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * Orion Store inspired motion kit for Petal.
 *
 *  - PetalInstantMotionScheme : every M3 spec snaps (reduce motion / animations off)
 *  - Modifier.petalSpringClick: ONE press modifier (squash spring + ripple + haptic tick)
 *  - PetalSwipeRow            : finger-following swipe actions that never recompose while dragging
 *  - PetalMorphShape/Blob     : live M3 shape morphing
 *
 * Springs come from PetalSpring so there is still a single source of truth.
 */

/** "Disable animations": every Material 3 motion token snaps. Selected by PetalTheme when reduce-motion is on. */
object PetalInstantMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

private fun hapticTick(context: android.content.Context, amplitude: Float = 0.45f) {
    PetalHapticEngine.getInstance(context)
        .playIfEnabled(context, PetalHapticEngine.Pattern.TICK, amplitude)
}

/**
 * The one press modifier: squashes on press with the fast spatial spring, ripple clipped to [shape],
 * and a single haptic tick on click. Scale is read inside graphicsLayer (no recomposition per frame).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.petalSpringClick(
    shape: Shape = RoundedCornerShape(20.dp),
    enabled: Boolean = true,
    pressedScale: Float = PetalSpring.PRESS_SCALE_BUTTON,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier {
    val context = LocalContext.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "petalSpringClick",
    )
    val currentClick by rememberUpdatedState(onClick)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = {
                hapticTick(context)
                currentClick()
            },
        )
}

/** What a swipe reveals and does. [removes] rows slide out and fold away before [onTrigger]; others fire and spring back. */
class PetalSwipeAction(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color,
    val removes: Boolean,
    val onTrigger: () -> Unit,
)

/**
 * Swipe a row right ([startToEnd]) or left ([endToStart]).
 * The row follows the finger 1:1, the action icon grows and turns in with the travel (read in the draw layer,
 * so dragging never recomposes), the background fills with a tick once the swipe is armed, and release
 * springs on the expressive scheme. The content should paint its own opaque container.
 */
@Composable
fun PetalSwipeRow(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    enabled: Boolean = true,
    startToEnd: PetalSwipeAction? = null,
    endToStart: PetalSwipeAction? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val reduceMotion = rememberReduceMotion()
    val trip = with(LocalDensity.current) { 88.dp.toPx() }

    // Plain state while dragging, a cancellable animate() only to settle (per-delta snapTo coroutines
    // would land after release and cancel the spring-back).
    var x by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    var width by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val shown = remember { MutableTransitionState(true) }
    var fired by remember { mutableStateOf<PetalSwipeAction?>(null) }

    val spring: AnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val fx = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val foldSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val foldFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    fun settleTo(target: Float, spec: AnimationSpec<Float>, then: () -> Unit = {}) {
        settle?.cancel()
        settle = scope.launch {
            animate(x, target, animationSpec = if (reduceMotion) snap() else spec) { v, _ -> x = v }
            then()
        }
    }

    val dir by remember { derivedStateOf { sign(x) } }
    val armed by remember { derivedStateOf { abs(x) >= trip } }
    LaunchedEffect(armed) { if (armed) hapticTick(context, 0.6f) }
    LaunchedEffect(shown.isIdle, shown.currentState) {
        if (shown.isIdle && !shown.currentState) fired?.onTrigger?.invoke()
    }

    val dragState = rememberDraggableState { d ->
        // Non-removing actions get rubber-band resistance past the trip point so the row never looks like it is leaving.
        val a = if (x + d >= 0f) startToEnd else endToStart
        val k = if (a?.removes == false && abs(x) > trip) 0.35f else 1f
        x = (x + d * k).coerceIn(
            if (endToStart != null) -width else 0f,
            if (startToEnd != null) width else 0f,
        )
    }

    AnimatedVisibility(
        visibleState = shown,
        modifier = modifier,
        exit = shrinkVertically(foldSpatial) + fadeOut(foldFade),
    ) {
        val action = if (dir > 0f) startToEnd else if (dir < 0f) endToStart else null
        val bg by animateColorAsState(
            if (armed && action != null) action.container else cs.surfaceContainerHighest,
            fx,
            label = "petalSwipeBg",
        )
        val fg by animateColorAsState(
            if (armed && action != null) action.content else cs.onSurfaceVariant,
            fx,
            label = "petalSwipeFg",
        )
        Box(
            Modifier
                .clip(shape)
                .onSizeChanged { width = it.width.toFloat() }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled && (startToEnd != null || endToStart != null),
                    onDragStarted = { settle?.cancel() },
                    onDragStopped = { velocity ->
                        val act = if (x > 0f) startToEnd else if (x < 0f) endToStart else null
                        val flung = abs(velocity) > 1600f && sign(velocity) == sign(x) && abs(x) > trip * 0.4f
                        when {
                            act == null || !(abs(x) >= trip || flung) -> settleTo(0f, spring)
                            act.removes -> {
                                hapticTick(context, 0.8f)
                                settleTo(sign(x) * width, tween(160, easing = FastOutLinearInEasing)) {
                                    fired = act
                                    shown.targetState = false
                                }
                            }
                            else -> {
                                hapticTick(context, 0.8f)
                                act.onTrigger()
                                settleTo(0f, spring)
                            }
                        }
                    },
                ),
        ) {
            // Underlay is only painted while the row is actually displaced.
            if (action != null) {
                Row(
                    Modifier
                        .matchParentSize()
                        .background(bg)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (dir < 0f) Arrangement.End else Arrangement.Start,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = fg,
                        modifier = Modifier.graphicsLayer {
                            val k = (abs(x) / trip).coerceIn(0f, 1f)
                            scaleX = 0.4f + 0.6f * k
                            scaleY = scaleX
                            alpha = k
                            rotationZ = (1f - k) * -45f * dir
                        },
                    )
                }
            }
            Box(Modifier.graphicsLayer { translationX = x }) { content() }
        }
    }
}

/** Live morph between two MaterialShapes polygons (normalized 0..1 space, scaled to the layout size). */
class PetalMorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }
}

/**
 * Blob that morphs to shapes[index] with the slow spatial spring and a quarter turn.
 * Use for nav indicators, empty states and hero art.
 */
@Composable
fun PetalMorphBlob(
    shapes: List<RoundedPolygon>,
    index: Int,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduceMotion = rememberReduceMotion()
    var from by remember { mutableIntStateOf(index) }
    var to by remember { mutableIntStateOf(index) }
    val progress = remember { Animatable(1f) }
    val spec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.slowSpatialSpec()
    LaunchedEffect(index) {
        if (index != to) {
            from = to
            to = index
            if (reduceMotion) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, spec)
            }
        }
    }
    val morph = remember(from, to, shapes) { Morph(shapes[from % shapes.size], shapes[to % shapes.size]) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = (to + progress.value - 1f) * 90f }
                .background(color, PetalMorphShape(morph, progress.value)),
        )
        content()
    }
}

/**
 * One-shot source rect (window coordinates) of the element a full-screen overlay should grow out of,
 * e.g. a download row opening its viewer. The overlay reads it once with [take].
 */
object PetalContainerSource {
    private const val MAX_AGE_MS = 1500L

    @Volatile
    private var bounds: androidx.compose.ui.geometry.Rect? = null

    @Volatile
    private var setAt = 0L

    fun set(rect: androidx.compose.ui.geometry.Rect?) {
        bounds = rect
        setAt = android.os.SystemClock.uptimeMillis()
    }

    /** Returns the recorded rect once; null if none, already taken, or stale (the tap never opened anything). */
    fun take(): androidx.compose.ui.geometry.Rect? {
        val b = bounds
        bounds = null
        return if (b != null && android.os.SystemClock.uptimeMillis() - setAt <= MAX_AGE_MS) b else null
    }
}

/**
 * Hand-built container transform (enter): the overlay starts as the source element's rectangle
 * (scale + translate + rounded clip) and springs out to full size. Does nothing when no source was
 * recorded or reduce-motion is on. Assumes the overlay fills the window.
 */
@Composable
fun Modifier.petalContainerEnter(cornerFrom: Dp = 28.dp): Modifier {
    val source = remember { PetalContainerSource.take() }
    val reduceMotion = rememberReduceMotion()
    if (source == null || reduceMotion) return this
    val progress = remember { Animatable(0f) }
    val spec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spec) }
    return this
        .onSizeChanged { size = it }
        .graphicsLayer {
            if (size.width == 0 || size.height == 0) {
                alpha = 0f
                return@graphicsLayer
            }
            val p = progress.value
            val q = p.coerceIn(0f, 1f)
            scaleX = source.width / size.width + (1f - source.width / size.width) * p
            scaleY = source.height / size.height + (1f - source.height / size.height) * p
            translationX = (1f - p) * (source.center.x - size.width / 2f)
            translationY = (1f - p) * (source.center.y - size.height / 2f)
            alpha = (0.35f + q * 2f).coerceIn(0f, 1f)
            this.shape = RoundedCornerShape(cornerFrom.toPx() * (1f - q))
            clip = true
        }
}

/** Shared press-scale: squashes to [pressedScale] while pressed, springing on the theme's fast spatial spec. */
@Composable
fun rememberPetalPressScale(
    interactionSource: androidx.compose.foundation.interaction.InteractionSource,
    pressedScale: Float,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "petalPressScale",
    )
    return scale
}
