package com.petal.browser.ui.glass

import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * Interactive liquid-glass effects, ported from Kyant's AndroidLiquidGlass catalog
 * (InteractiveHighlight, DampedDragAnimation, DragGestureInspector). Apache-2.0.
 *
 *  - press highlight: a soft light that blooms under the finger and follows it
 *  - squish: the glass swells under the finger and springs back
 *  - lens on press: the rim refraction of selection slabs deepens while held
 */

/** "Liquid Glass Effects" settings. All keys start with sp_liquid_glass_ so changes apply live. */
object GlassFxPrefs {
    const val ENABLED = "sp_liquid_glass_fx"
    const val HIGHLIGHT = "sp_liquid_glass_fx_highlight"
    const val SQUISH = "sp_liquid_glass_fx_squish"
    const val LENS = "sp_liquid_glass_fx_lens"
}

@Immutable
data class GlassFx(
    val enabled: Boolean = true,
    val highlight: Boolean = true,
    val squish: Boolean = true,
    val lens: Boolean = true
) {
    val showHighlight: Boolean get() = enabled && highlight
    val showSquish: Boolean get() = enabled && squish
    val showLens: Boolean get() = enabled && lens
    val any: Boolean get() = showHighlight || showSquish || showLens
}

/** Reads the effect prefs; recomposes the caller whenever any liquid-glass pref changes. */
@Composable
fun rememberGlassFx(
    enabledOverride: Boolean? = null,
    highlightOverride: Boolean? = null,
    squishOverride: Boolean? = null,
    lensOverride: Boolean? = null
): GlassFx {
    LocalPetalGlassRevision.current
    val sp = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    return GlassFx(
        enabled = enabledOverride ?: sp.getBoolean(GlassFxPrefs.ENABLED, true),
        highlight = highlightOverride ?: sp.getBoolean(GlassFxPrefs.HIGHLIGHT, true),
        squish = squishOverride ?: sp.getBoolean(GlassFxPrefs.SQUISH, true),
        lens = lensOverride ?: sp.getBoolean(GlassFxPrefs.LENS, true)
    )
}

// ---------------------------------------------------------------------------------------
// Gestures
// ---------------------------------------------------------------------------------------

/**
 * Observes a press/drag without consuming anything, so it can sit next to clickable /
 * scrollable modifiers.
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = dragUntilUp(initialDown.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend fun AwaitPointerEventScope.dragUntilUp(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent else pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}

// ---------------------------------------------------------------------------------------
// Press highlight
// ---------------------------------------------------------------------------------------

private const val HIGHLIGHT_AGSL = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun highlightShader() = android.graphics.RuntimeShader(HIGHLIGHT_AGSL)

/**
 * Press state + the light that follows the finger. [pressProgress] and [offset] are animated
 * state, read from draw, so nothing recomposes while it moves.
 */
@Stable
class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionSpec = spring<Offset>(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressProgressAnimation.value

    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) highlightShader() else null

    /**
     * Draws the light over the glass and under the content, clipped to [shape] (the content
     * itself is never clipped). Put this after the glass modifier.
     */
    fun overlay(shape: Shape): Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
            clipPath(path) {
                if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
                    val p = position(size, positionAnimation.value)
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setColorUniform("color", Color.White.copy(0.15f * progress).toArgb())
                    shader.setFloatUniform("radius", size.minDimension * 1.5f)
                    shader.setFloatUniform(
                        "position",
                        p.x.coerceIn(0f, size.width),
                        p.y.coerceIn(0f, size.height)
                    )
                    drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
                } else {
                    drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
                }
            }
        }
        drawContent()
    }

    /** Tracks presses. Observes only; never consumes. */
    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun release() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressSpec) }
            launch { positionAnimation.animateTo(startPosition, positionSpec) }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Damped drag (selection slab: swell, stretch with velocity, snap to tab)
// ---------------------------------------------------------------------------------------

@Stable
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit
) {
    private val valueSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    private val velocitySpec = spring<Float>(0.5f, 300f, visibilityThreshold * 10f)
    private val pressSpec = spring<Float>(1f, 1000f, 0.001f)
    private val scaleXSpec = spring<Float>(0.6f, 250f, 0.001f)
    private val scaleYSpec = spring<Float>(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            androidx.compose.runtime.withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
                launch { valueAnimation.animateTo(target, valueSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocitySpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocitySpec) }
    }
}

// ---------------------------------------------------------------------------------------
// One-stop press effect for any glass surface
// ---------------------------------------------------------------------------------------

/** Press state for a glass surface: highlight + squish, honouring [GlassFx]. */
@Stable
class GlassPress internal constructor(
    val fx: GlassFx,
    internal val highlight: InteractiveHighlight
) {
    /** 0 at rest, 1 while pressed (animated). Read it from draw / graphicsLayer blocks only. */
    val progress: Float get() = highlight.pressProgress
}

@Composable
fun rememberGlassPress(fx: GlassFx = rememberGlassFx()): GlassPress {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    return remember(fx, highlight) { GlassPress(fx, highlight) }
}

/**
 * The swell: the surface grows by about [grow] while pressed. Goes BEFORE (outside) the glass
 * modifier so the glass scales with it. A no-op when squish is off.
 */
fun Modifier.glassPressScale(press: GlassPress, grow: Float = 12f): Modifier {
    if (!press.fx.showSquish) return this
    return graphicsLayer {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val p = press.progress
        scaleX = 1f + (grow.dp.toPx() / w).coerceAtMost(0.10f) * p
        scaleY = 1f + (grow.dp.toPx() / h).coerceAtMost(0.10f) * p
    }
}

/**
 * The light under the finger. Goes AFTER (inside) the glass modifier so it lies on the glass
 * and under the content. Also listens for the press, so a surface needs only this and
 * [glassPressScale].
 */
fun Modifier.glassPressOverlay(press: GlassPress, shape: Shape): Modifier {
    if (!press.fx.any) return this
    val tracking = press.highlight.gestureModifier
    return if (press.fx.showHighlight) {
        this.then(tracking).then(press.highlight.overlay(shape))
    } else {
        this.then(tracking)
    }
}
