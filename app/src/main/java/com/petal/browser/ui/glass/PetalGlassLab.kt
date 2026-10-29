package com.petal.browser.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.containment.petalRealGlassSupported
import com.petal.browser.ui.containment.rememberPetalBackdrop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The values the Appearance sliders are driving, passed in live (unsaved) from the sliders. */
@Immutable
data class GlassLabTuning(
    val opacity: Float,
    val sheen: Float,
    val refraction: Float,
    val refractionWidth: Float
)

/**
 * A playground of real liquid glass — Kyant's backdrop blur, lens and highlight — over moving
 * colour and fine lines, so every slider shows at once on a panel, a draggable lens, and a
 * bottom-tabs bar with a selection slab. Press, drag and the slab all use Kyant's interaction
 * effects, honouring the "Liquid Glass Effects" toggles.
 */
@Composable
fun PetalGlassLab(
    opacity: Float,
    sheen: Float,
    refraction: Float,
    refractionWidth: Float,
    fx: GlassFx,
    modifier: Modifier = Modifier
) {
    val tuning = GlassLabTuning(opacity, sheen, refraction, refractionWidth)
    val backdrop = rememberPetalBackdrop()
    val outer = RoundedCornerShape(28.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(outer)
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val orbSize = 76.dp
        val maxX = with(density) { (maxWidth - orbSize).toPx() }
        val maxY = with(density) { (maxHeight - orbSize).toPx() }
        var orb by remember { mutableStateOf(Offset(with(density) { 28.dp.toPx() }, with(density) { 112.dp.toPx() })) }

        // The picture the glass bends. It is the backdrop SOURCE; the glass are its siblings.
        LabScene(Modifier.fillMaxSize().layerBackdrop(backdrop))

        // Panel
        val panelPress = rememberGlassPress(fx)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .fillMaxWidth(0.92f)
                .glassPressScale(panelPress, 10f)
                .labGlass(backdrop, RoundedCornerShape(26.dp), tuning) { if (fx.showLens) panelPress.progress else 0f }
                .glassPressOverlay(panelPress, RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Liquid Glass", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Slide a setting below and watch this panel, the lens and the tabs change.",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
        }

        // Draggable lens
        val orbPress = rememberGlassPress(fx)
        Box(
            modifier = Modifier
                .offset { IntOffset(orb.x.roundToInt(), orb.y.roundToInt()) }
                .size(orbSize)
                .glassPressScale(orbPress, 14f)
                .labGlass(backdrop, CircleShape, tuning) { if (fx.showLens) orbPress.progress else 0f }
                .glassPressOverlay(orbPress, CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        orb = Offset(
                            (orb.x + drag.x).coerceIn(0f, maxX.coerceAtLeast(0f)),
                            (orb.y + drag.y).coerceIn(0f, maxY.coerceAtLeast(0f))
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("drag", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }

        // Tabs
        LabTabs(
            backdrop = backdrop,
            tuning = tuning,
            fx = fx,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .fillMaxWidth(0.88f)
        )
    }
}

@Composable
private fun LabTabs(
    backdrop: PetalBackdrop,
    tuning: GlassLabTuning,
    fx: GlassFx,
    modifier: Modifier
) {
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.Settings)
    val count = icons.size
    val scope = rememberCoroutineScope()
    val barPress = rememberGlassPress(fx)
    val capsule = RoundedCornerShape(50)

    BoxWithConstraints(modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / count }
        var selected by remember { mutableIntStateOf(0) }
        val drag = remember(scope) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = 0f,
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val index = targetValue.roundToInt().coerceIn(0, count - 1)
                    selected = index
                    animateToValue(index.toFloat())
                },
                onDrag = { _, amount ->
                    updateValue((targetValue + amount.x / tabWidth).coerceIn(0f, (count - 1).toFloat()))
                }
            )
        }

        // The bar
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .glassPressScale(barPress, 12f)
                .labGlass(backdrop, capsule, tuning) { if (fx.showLens) barPress.progress else 0f }
                .glassPressOverlay(barPress, capsule)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icons.forEachIndexed { i, icon ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selected = i
                            drag.animateToValue(i.toFloat())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }

        // The selection slab: clear glass that swells, deepens its lens and stretches as it moves.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = drag.value * tabWidth
                    if (fx.showSquish) {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val velocity = drag.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    }
                }
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsule },
                    effects = {
                        if (petalRealGlassSupported && fx.showLens && tuning.refraction > 0.001f) {
                            val p = drag.pressProgress
                            lens(
                                (10.dp.toPx() * tuning.refractionWidth * p).coerceAtMost(size.minDimension / 2f),
                                14.dp.toPx() * tuning.refraction * p
                            )
                        }
                    },
                    highlight = if (tuning.sheen > 0.01f) {
                        { Highlight.Default.copy(alpha = (drag.pressProgress * (tuning.sheen / 0.6f)).coerceIn(0f, 1f)) }
                    } else null,
                    onDrawSurface = {
                        val p = drag.pressProgress
                        drawRect(Color.White.copy(alpha = 0.16f * (1f - p)))
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / count)
        )
    }
}

/**
 * Kyant glass with the Appearance values applied: [GlassLabTuning.opacity] sets blur and veil,
 * [GlassLabTuning.sheen] the highlight, the refraction pair the rim lens. [lensBoost] (0..1,
 * read at draw time) deepens the lens while the surface is pressed.
 */
private fun Modifier.labGlass(
    backdrop: PetalBackdrop,
    shape: Shape,
    t: GlassLabTuning,
    lensBoost: () -> Float
): Modifier {
    if (!petalRealGlassSupported) {
        // Below Android 12 nothing can be blurred: a plain frosted veil stands in.
        return this.clip(shape).background(Color.White.copy(alpha = (0.10f + t.opacity * 0.30f).coerceIn(0f, 0.9f)))
    }
    val sheenAlpha = (t.sheen / 0.6f).coerceIn(0f, 1f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        highlight = if (t.sheen > 0.01f) {
            { Highlight.Default.copy(alpha = sheenAlpha) }
        } else null,
        effects = {
            vibrancy()
            blur((3f + t.opacity * 9f).dp.toPx())
            if (t.refraction > 0.001f && t.refractionWidth > 0.001f) {
                val boost = lensBoost()
                val height = (22.dp.toPx() * t.refractionWidth * (1f + 0.5f * boost))
                    .coerceAtMost(size.minDimension / 2f)
                lens(height, 30.dp.toPx() * t.refraction * (1f + 0.9f * boost))
            }
        },
        onDrawSurface = {
            drawRect(Color.White.copy(alpha = (0.04f + t.opacity * 0.22f).coerceIn(0f, 0.9f)))
        }
    )
}

/** Colour that moves, with fine lines that make bending obvious. This is the backdrop source. */
@Composable
private fun LabScene(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "glassLab")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "labPhase"
    )
    val blobs = remember {
        listOf(Color(0xFFFF7A59), Color(0xFF35D0BA), Color(0xFFFFD166), Color(0xFF6C8CFF))
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFF0B1B3A), Color(0xFF3A1054), Color(0xFF7A2536)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            blobs.forEachIndexed { i, color ->
                val a = (phase + i * 0.25f) * 2f * PI.toFloat()
                val center = Offset(
                    size.width * (0.5f + 0.40f * cos(a + i)),
                    size.height * (0.5f + 0.34f * sin(a * 1.3f + i))
                )
                val radius = size.minDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius),
                    radius = radius,
                    center = center
                )
            }
            // Moving fine lines: straight in the middle, bent wherever the lens pulls them.
            val step = 20.dp.toPx()
            var x = -step + (phase * step * 3f) % step
            while (x < size.width + step) {
                drawLine(Color.White.copy(alpha = 0.30f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += step
            }
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("PETAL BROWSER", color = Color.White.copy(alpha = 0.55f), fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("liquid glass bends what is behind it", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
        }
    }
}
