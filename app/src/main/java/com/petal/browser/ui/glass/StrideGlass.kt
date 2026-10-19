package com.petal.browser.ui.glass

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil

/**
 * Stride's liquid glass, brought to Petal.
 *
 * Stride's nav bar lives in the same Compose tree as the screens it floats over, so it can
 * record them into a layer. Petal's bar is its own ComposeView floating over `main_content`
 * (GeckoView or the Compose home), so here the backdrop is that View, recorded into a
 * [RenderNode] — a list of references to its children's display lists, not a copy of
 * pixels, so recording it is cheap and it always shows their latest frame.
 *
 * The glass is re-recorded before every frame the window draws ([ViewTreeObserver.OnPreDrawListener]).
 * Only RenderNodes are touched there, never a View, so it schedules nothing and an idle
 * screen stays idle. A RenderNode drawn in two places reports its changes to only one of
 * them, so the glass must be re-recorded rather than left to notice on its own.
 *
 * The effect stack is Stride's: vibrancy, then blur (Android 12), then the rounded-rect
 * lens at the rim (Android 13), a breath of tint, a lit top edge and a rim.
 */
@RequiresApi(Build.VERSION_CODES.S)
class GlassBackdrop internal constructor(private val source: View) {
    internal val node = RenderNode("PetalGlassBackdrop")
    private val bars = LinkedHashSet<GlassBar>()
    private val sourceLoc = IntArray(2)
    private var listening: ViewTreeObserver? = null

    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        refresh()
        true
    }

    /** Where the source sits in its window, in px. */
    internal fun sourceOrigin(): Offset {
        source.getLocationInWindow(sourceLoc)
        return Offset(sourceLoc[0].toFloat(), sourceLoc[1].toFloat())
    }

    internal fun attach(bar: GlassBar) {
        bars += bar
        if (listening == null || listening?.isAlive != true) {
            listening = source.viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
        }
    }

    internal fun detach(bar: GlassBar) {
        bars -= bar
        if (bars.isEmpty()) {
            listening?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
            listening = null
        }
    }

    /** Records the source again, then every bar sampling it. */
    internal fun refresh() {
        if (!recordSource()) return
        for (bar in bars) {
            // A minimized (INVISIBLE) bar draws nothing; skip it until it is shown again.
            if (bar.host?.isShown == false) continue
            bar.record()
        }
    }

    internal fun recordSource(): Boolean {
        val w = source.width
        val h = source.height
        if (!source.isAttachedToWindow || w <= 0 || h <= 0) return false
        node.setPosition(0, 0, w, h)
        val canvas = node.beginRecording(w, h)
        try {
            // Background plus each child's display list, by reference.
            source.draw(canvas)
            // A top bar sits over the strip main_content reserves above the page, which is
            // empty (the window shows black there). Fill it with the page's first rows,
            // mirrored about the strip's lower edge, so the glass has real content to bend.
            val strip = source.paddingTop
            if (strip in 1 until h) {
                val save = canvas.save()
                canvas.clipRect(0, 0, w, strip)
                canvas.translate(0f, 2f * strip)
                canvas.scale(1f, -1f)
                source.draw(canvas)
                canvas.restoreToCount(save)
            }
        } catch (_: Throwable) {
            // A child that cannot be drawn twice must not take the bar down with it.
        } finally {
            node.endRecording()
        }
        return true
    }

    companion object {
        /** A backdrop recording [source]; null below Android 12, where nothing can be blurred. */
        @JvmStatic
        fun of(source: View): GlassBackdrop? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else GlassBackdrop(source)
    }
}

/**
 * How the glass looks. Blur and lens sizes are in px; [edge] and [refraction] are
 * multipliers as in Stride's Settings, 1 being the look as designed.
 */
internal data class GlassLook(
    val fill: Int,
    val specular: Int,
    val rimTop: Int,
    val rimBottom: Int,
    val blur: Float,
    val lensHeight: Float,
    val lensAmount: Float,
    val specHeight: Float,
    val rimWidth: Float,
    val pad: Float
)

internal data class SlabLook(
    val fill: Int,
    val rimTop: Int,
    val rimBottom: Int,
    val lensHeight: Float,
    val lensAmount: Float,
    val rimWidth: Float,
    val pad: Float
)

/**
 * The nav bar's glass: the backdrop under it, bent and frosted, plus the clear selection
 * slab lying on it. Everything is drawn into RenderNodes that the Compose bar only
 * references, so the glass can be refreshed every frame without recomposing anything.
 */
@RequiresApi(Build.VERSION_CODES.S)
class GlassBar internal constructor(private val backdrop: GlassBackdrop) {
    /** The backdrop, blurred and lensed, with a margin around the bar for the blur. */
    private val glassNode = RenderNode("PetalGlassBarGlass")
    /** The finished bar: glass, tint, highlight and rim, clipped to its shape. */
    internal val barNode = RenderNode("PetalGlassBar")
    /** The bar seen through the selection slab: magnified and bent at the slab's rim. */
    private val slabNode = RenderNode("PetalGlassSlab")

    // Geometry, kept by the Compose side.
    internal var host: View? = null
    internal var coords: LayoutCoordinates? = null
    private var size = Size.Zero
    private var outline: android.graphics.Path? = null
    private var radii: FloatArray? = null
    private var look: GlassLook? = null
    private var effectKey: Any? = null

    private var slab: Rect? = null
    private var slabOutline: android.graphics.Path? = null
    private var slabRadii: FloatArray? = null
    private var slabLook: SlabLook? = null
    private var slabEffectKey: Any? = null

    private val hostLoc = IntArray(2)
    private val fillPaint = Paint()
    private val specPaint = Paint()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val slabFillPaint = Paint()
    private val slabRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    internal fun attach() = backdrop.attach(this)
    internal fun detach() = backdrop.detach(this)

    internal fun setShape(size: Size, shape: Shape, direction: LayoutDirection, density: Density, look: GlassLook) {
        if (size == this.size && look == this.look && outline != null) return
        this.size = size
        this.look = look
        outline = Path().apply { addOutline(shape.createOutline(size, direction, density)) }.asAndroidPath()
        radii = cornerRadii(shape, size, direction, density)
        val key = listOf(size, radii?.toList(), look.blur, look.lensHeight, look.lensAmount, look.pad)
        if (key != effectKey) {
            effectKey = key
            glassNode.setRenderEffect(glassEffect(size, look, radii))
        }
        fillPaint.color = look.fill
        specPaint.shader = LinearGradient(
            0f, 0f, 0f, look.specHeight, look.specular, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        rimPaint.strokeWidth = look.rimWidth * 2
        rimPaint.shader = LinearGradient(
            0f, 0f, 0f, size.height, look.rimTop, look.rimBottom, Shader.TileMode.CLAMP
        )
    }

    /** The slab's place in bar coordinates; null hides it. */
    internal fun setSlab(rect: Rect?, shape: Shape, direction: LayoutDirection, density: Density, look: SlabLook) {
        val lookChanged = look != slabLook
        slabLook = look
        if (rect == null || rect.width <= 0f || rect.height <= 0f) {
            slab = null
            return
        }
        val sizeChanged = slab?.size != rect.size || lookChanged
        slab = rect
        if (sizeChanged || slabOutline == null) {
            slabOutline = Path().apply { addOutline(shape.createOutline(rect.size, direction, density)) }.asAndroidPath()
            slabRadii = cornerRadii(shape, rect.size, direction, density)
        }
        val key = listOf(rect.size, slabRadii?.toList(), look)
        if (key != slabEffectKey) {
            slabEffectKey = key
            slabNode.setRenderEffect(
                slabRadii?.let { lensEffect(rect.size, look.pad, it, look.lensHeight, look.lensAmount) }
            )
        }
        slabFillPaint.color = look.fill
        slabRimPaint.strokeWidth = look.rimWidth * 2
        if (sizeChanged || slabRimPaint.shader == null) {
            slabRimPaint.shader = LinearGradient(
                0f, 0f, 0f, rect.height, look.rimTop, look.rimBottom, Shader.TileMode.CLAMP
            )
        }
    }

    /** Re-records the bar and its slab from the latest backdrop and geometry. */
    internal fun record() {
        val look = look ?: return
        val outline = outline ?: return
        val host = host ?: return
        val coords = coords?.takeIf { it.isAttached } ?: return
        if (size.width <= 0f || size.height <= 0f) return

        host.getLocationInWindow(hostLoc)
        val inRoot = coords.positionInRoot()
        val shift = backdrop.sourceOrigin() - Offset(hostLoc[0] + inRoot.x, hostLoc[1] + inRoot.y)
        val pad = look.pad
        val w = size.width
        val h = size.height

        if (!backdrop.node.hasDisplayList()) {
            backdrop.recordSource()
        }

        val pw = ceil(w + 2 * pad).toInt()
        val ph = ceil(h + 2 * pad).toInt()
        glassNode.setPosition(0, 0, pw, ph)
        glassNode.beginRecording(pw, ph).let { c ->
            c.translate(shift.x + pad, shift.y + pad)
            c.drawRenderNode(backdrop.node)
            glassNode.endRecording()
        }

        val bw = ceil(w).toInt()
        val bh = ceil(h).toInt()
        barNode.setPosition(0, 0, bw, bh)
        barNode.beginRecording(bw, bh).let { c ->
            val save = c.save()
            c.clipPath(outline)
            c.translate(-pad, -pad)
            c.drawRenderNode(glassNode)
            c.translate(pad, pad)
            c.drawRect(0f, 0f, w, h, fillPaint)
            c.drawRect(0f, 0f, w, look.specHeight, specPaint)
            c.drawPath(outline, rimPaint)
            c.restoreToCount(save)
            barNode.endRecording()
        }
        recordSlab()
    }

    internal fun recordSlab() {
        val r = slab ?: return
        val look = slabLook ?: return
        val pad = look.pad
        val pw = ceil(r.width + 2 * pad).toInt()
        val ph = ceil(r.height + 2 * pad).toInt()
        slabNode.setPosition(0, 0, pw, ph)
        slabNode.beginRecording(pw, ph).let { c ->
            // A slab of glass magnifies: the bar under it is drawn a little larger around
            // the slab's centre, like a lens.
            c.scale(SLAB_MAGNIFY, SLAB_MAGNIFY, pad + r.width / 2f, pad + r.height / 2f)
            c.translate(pad - r.left, pad - r.top)
            c.drawRenderNode(barNode)
            slabNode.endRecording()
        }
    }

    /** Draws the finished bar on [canvas], in bar coordinates. False when it cannot. */
    internal fun drawBar(canvas: Canvas): Boolean {
        val rc = canvas as? RecordingCanvas ?: return false
        if (!barNode.hasDisplayList()) return false
        rc.drawRenderNode(barNode)
        return true
    }

    /** Draws the slab at [offset] from the bar's origin (the caller's own position in the bar). */
    internal fun drawSlab(canvas: Canvas, offset: Offset) {
        val rc = canvas as? RecordingCanvas ?: return
        val r = slab ?: return
        val outline = slabOutline ?: return
        val look = slabLook ?: return
        if (!slabNode.hasDisplayList()) return
        val save = rc.save()
        rc.translate(r.left - offset.x, r.top - offset.y)
        val inner = rc.save()
        rc.clipPath(outline)
        rc.translate(-look.pad, -look.pad)
        rc.drawRenderNode(slabNode)
        rc.translate(look.pad, look.pad)
        rc.drawRect(0f, 0f, r.width, r.height, slabFillPaint)
        rc.restoreToCount(inner)
        // The rim is left unclipped, as in Stride, so the slab's edge reads crisply.
        rc.drawPath(outline, slabRimPaint)
        rc.restoreToCount(save)
    }

    internal fun hasSlab(): Boolean = slab != null
}

/** How much larger things look through the selection slab. */
const val SLAB_MAGNIFY = 1.12f

/** Vibrancy, then blur, then (Android 13+) the lens — the order Stride and Vyxel chain them. */
@RequiresApi(Build.VERSION_CODES.S)
private fun glassEffect(size: Size, look: GlassLook, radii: FloatArray?): RenderEffect {
    val vibrancy = RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
    )
    val blurred = if (look.blur >= 0.5f) {
        RenderEffect.createBlurEffect(look.blur, look.blur, vibrancy, Shader.TileMode.CLAMP)
    } else vibrancy
    val lens = radii?.let { lensEffect(size, look.pad, it, look.lensHeight, look.lensAmount) } ?: return blurred
    return RenderEffect.createChainEffect(lens, blurred)
}

/** Stride's rounded-rect lens alone. Null below Android 13 (no runtime shader). */
internal fun lensEffect(size: Size, pad: Float, radii: FloatArray, height: Float, amount: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (height <= 0f || amount <= 0f) return null
    return lensShaderEffect(size, pad, radii, height, amount)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensShaderEffect(size: Size, pad: Float, radii: FloatArray, height: Float, amount: Float): RenderEffect {
    val shader = LensShaders.plain
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("offset", -pad, -pad)
    shader.setFloatUniform("cornerRadii", radii)
    shader.setFloatUniform("refractionHeight", height.coerceAtMost(size.minDimension / 2f))
    shader.setFloatUniform("refractionAmount", -amount)
    shader.setFloatUniform("depthEffect", 0f)
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/** Corner radii in the shader's order — top-left, top-right, bottom-right, bottom-left. */
internal fun cornerRadii(shape: Shape, size: Size, direction: LayoutDirection, density: Density): FloatArray {
    if (shape !is CornerBasedShape) return floatArrayOf(0f, 0f, 0f, 0f)
    val ltr = direction == LayoutDirection.Ltr
    val topStart = shape.topStart.toPx(size, density)
    val topEnd = shape.topEnd.toPx(size, density)
    val bottomEnd = shape.bottomEnd.toPx(size, density)
    val bottomStart = shape.bottomStart.toPx(size, density)
    val limit = size.minDimension / 2f
    return floatArrayOf(
        if (ltr) topStart else topEnd,
        if (ltr) topEnd else topStart,
        if (ltr) bottomEnd else bottomStart,
        if (ltr) bottomStart else bottomEnd,
    ).also { r -> for (i in r.indices) r[i] = r[i].coerceIn(0f, limit) }
}

internal fun Color.argb(): Int = toArgb()

/** Stride's refraction shader (from Vyxel's GlassSurface), compiled once. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object LensShaders {
    val plain by lazy { RuntimeShader(REFRACTION) }
}

private const val REFRACTION = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    // Clamp refracted coordinate within the recorded glass buffer [0, size - 2 * offset]
    // Note that offset is negative (-pad, -pad), so the buffer size is size + 2 * pad.
    float2 maxCoord = size - 2.0 * offset;
    float2 refractedCoord = clamp(coord + d * grad, float2(0.5), maxCoord - float2(0.5));
    return content.eval(refractedCoord);
}
"""
