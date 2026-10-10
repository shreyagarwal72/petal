/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 * Ported & adapted with Material 3 Expressive Liquid Glass from SimpMusic
 */

package com.petal.browser.ui.containment

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.sign
import com.kyant.backdrop.backdrops.layerBackdrop as nativeBackdrop

/**
 * The backdrop a glass surface samples from.
 */
typealias PetalBackdrop = LayerBackdrop

val LocalPetalBackdrop = androidx.compose.runtime.compositionLocalOf<PetalBackdrop?> { null }

/** Marks a composable as the source layer that sibling glass surfaces refract. */
fun Modifier.petalBackdropSource(backdrop: PetalBackdrop): Modifier = this.nativeBackdrop(backdrop)

@Composable
fun rememberPetalBackdrop(color: Color = Color.Transparent): PetalBackdrop =
    rememberLayerBackdrop {
        if (color != Color.Transparent) {
            drawRect(color)
        }
        drawContent()
    }

/** Blur needs API 31 and the lens shader needs API 33, so older phones keep the fallback look. */
val petalRealGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Press/hold state holder for a single liquid-glass surface.
 * Ported directly from SimpMusic / Kyant catalog with observe-only drag/press recogniser.
 */
class GlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0f at rest, animating to 1f while pressed. Read in draw/effect/layer blocks. */
    val pressProgress: Float get() = pressAnimation.value

    /** Local-space touch point used as the centre of the press glow. */
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) =
        with(pointer) {
            inspectDragGestures(
                onDragStart = { down ->
                    touchPosition = down.position
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                },
                onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
                onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
            ) { change, _ ->
                touchPosition = change.position
            }
        }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/**
 * Dynamic background luminance sampler (from SimpMusic).
 * Automatically reads the luminance of the sampled layer so glass darkens or brightens smoothly.
 */
private const val SAMPLE_SIZE = 5
private const val SAMPLE_INTERVAL_MS = 2_500L
private const val LUMINANCE_CHANGE_THRESHOLD = 0.02f

@Composable
fun rememberGlassLuminance(
    source: GraphicsLayer,
    enabled: Boolean = true,
): State<Float> {
    val sample = rememberGraphicsLayer()
    val luminance = remember { Animatable(0.5f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(source, sample, enabled, lifecycle, density, layoutDirection) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
            while (isActive) {
                val sourceSize = source.size
                if (sourceSize.width > 0 && sourceSize.height > 0) {
                    val target =
                        try {
                            sample.record(density, layoutDirection, IntSize(SAMPLE_SIZE, SAMPLE_SIZE)) {
                                scale(
                                    scaleX = SAMPLE_SIZE.toFloat() / sourceSize.width,
                                    scaleY = SAMPLE_SIZE.toFloat() / sourceSize.height,
                                    pivot = Offset.Zero,
                                ) {
                                    drawLayer(source)
                                }
                            }
                            sample.toImageBitmap().readPixels(pixels)
                            val average =
                                pixels.sumOf { color ->
                                    val r = (color shr 16 and 0xFF) / 255.0
                                    val g = (color shr 8 and 0xFF) / 255.0
                                    val b = (color and 0xFF) / 255.0
                                    0.2126 * r + 0.7152 * g + 0.0722 * b
                                } / pixels.size
                            average.toFloat().coerceIn(0.3f, 0.8f)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            return@repeatOnLifecycle
                        }
                    if (abs(target - luminance.targetValue) >= LUMINANCE_CHANGE_THRESHOLD) {
                        luminance.animateTo(target, tween(500))
                    }
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }
    return luminance.asState()
}

fun Modifier.petalRealGlass(
    backdrop: PetalBackdrop?,
    shape: Shape = CircleShape,
    isDark: Boolean = false,
    highlight: Highlight = Highlight.Default
): Modifier = this.drawInteractiveGlass(
    isDark = isDark,
    backdrop = backdrop,
    shape = shape,
    highlight = highlight
)

/**
 * Draws the real SimpMusic liquid-glass effect.
 */
fun Modifier.drawInteractiveGlass(
    isDark: Boolean,
    backdrop: PetalBackdrop?,
    layer: GraphicsLayer? = null,
    luminance: () -> Float = { 0.5f },
    shape: Shape = CircleShape,
    interaction: GlassInteraction? = null,
    pressedScale: Float = 1.08f,
    highlight: Highlight = Highlight.Default,
    blurScale: Float = 1f,
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.50f,
): Modifier {
    if (backdrop == null || !petalRealGlassSupported) {
        return this
            .clip(shape)
            .background(
                if (isDark) Color(0xFF1E1E24).copy(alpha = 0.85f)
                else Color(0xFFF2F2F7).copy(alpha = 0.85f)
            )
    }

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { highlight },
            effects = {
                val l = (luminance() * 2f - 1f).let { sign(it) * it * it }
                val press = interaction?.pressProgress ?: 0f
                vibrancy()
                colorControls(
                    brightness = 0.05f,
                    contrast = 1f,
                    saturation = 1.5f,
                )
                blur(
                    (
                        if (l > 0f) {
                            lerp(8f.dp.toPx(), 16f.dp.toPx(), l)
                        } else {
                            lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)
                        }
                    ) * blurScale + 2f.dp.toPx() * press,
                )
                lens(size.minDimension / 4f + 2f.dp.toPx() * press, size.minDimension / 2f, false)
            },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
                layer?.record { drawBackdrop() }
            },
            onDrawSurface = {
                val darken = lerp(minScrim, maxScrim, ((luminance() - 0.3f) / 0.5f).coerceIn(0f, 1f))
                drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color.White.copy(alpha = 0.18f * press),
                                        Color.Transparent,
                                    ),
                                center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension * 1.2f,
                            ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock =
                if (interaction != null) {
                    {
                        val scale = lerp(1f, pressedScale, interaction.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    }
                } else null,
        ).then(
            if (interaction != null) {
                Modifier.pointerInput(interaction) { interaction.detectPress(this) }
            } else {
                Modifier
            },
        )
}

/**
 * Convenience container wrapping arbitrary content inside liquid glass.
 */
@Composable
fun LiquidGlassContainer(
    backdrop: PetalBackdrop?,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    layer: GraphicsLayer? = null,
    luminance: () -> Float = { 0.5f },
    shape: Shape = CircleShape,
    interactive: Boolean = true,
    highlight: Highlight = Highlight.Default,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = if (interactive) rememberGlassInteraction() else null
    Box(
        modifier = modifier.drawInteractiveGlass(
            isDark = isDark,
            backdrop = backdrop,
            layer = layer,
            luminance = luminance,
            shape = shape,
            interaction = interaction,
            highlight = highlight,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Observe-only drag gesture inspector ported from SimpMusic.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent =
            drag(
                pointerId = down.id,
                onDrag = { onDrag(it, it.positionChange()) },
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
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

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) return dragEvent
        }
    }
}

/**
 * Spring-animated damped drag tracker ported from SimpMusic's LiquidGlassTabBar.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring<Float>(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring<Float>(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring<Float>(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring<Float>(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring<Float>(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier =
        Modifier.pointerInput(Unit) {
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
                },
            ) { _, dragAmount ->
                onDrag(size, dragAmount)
            }
        }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos {}
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
        animationScope.launch {
            valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            press()
            val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
            launch { valueAnimation.animateTo(target, valueAnimationSpec) }
            if (velocity != 0f) {
                launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
            }
            release()
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val targetVelocity =
            velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}
