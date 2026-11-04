package com.petal.browser.ui.theme.layout

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Corner shape that really renders every [PetalShapeType] style:
 *  - ROUNDED  circular arcs
 *  - CUT      straight chamfers
 *  - SQUIRCLE tighter superellipse-like corners
 *  - SMOOTH   continuous-curvature ("iOS") corners that start earlier on the edge
 *  - WAVY     rounded corners plus a gentle ripple along the long edges
 *  - SCOOP    concave quarter-circle corners
 *  - NOTCH    stepped square notches
 *
 * It is a [CornerBasedShape], so every M3 component and `Shape.copy(...)` caller keeps working.
 */
class PetalCornerShape(
    val kind: PetalShapeType,
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = PetalCornerShape(kind, topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())

        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = if (ltr) topStart else topEnd
        val tr = if (ltr) topEnd else topStart
        val br = if (ltr) bottomEnd else bottomStart
        val bl = if (ltr) bottomStart else bottomEnd

        if (kind == PetalShapeType.ROUNDED) {
            return Outline.Rounded(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(tl),
                    topRight = CornerRadius(tr),
                    bottomRight = CornerRadius(br),
                    bottomLeft = CornerRadius(bl),
                )
            )
        }

        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        val path = Path()
        val ripple = kind == PetalShapeType.WAVY

        // The path is walked clockwise. `cursor` always holds the current point so edges can ripple.
        var cursor = Offset(tl, 0f)
        path.moveTo(cursor.x, cursor.y)

        fun corner(c: Offset, a: Offset, b: Offset, r: Float) {
            // a = nominal tangent point on the incoming edge, b = on the outgoing edge (both r from c).
            val ua = unit(a - c)
            val ub = unit(b - c)
            val ext = when (kind) {
                PetalShapeType.SQUIRCLE -> r * 0.25f
                PetalShapeType.SMOOTH -> r * 0.6f
                else -> 0f
            }.coerceAtMost((minDim / 2f - r).coerceAtLeast(0f))
            val d = r + ext
            val a2 = c + ua * d
            val b2 = c + ub * d
            edge(path, cursor, a2, ripple, r)
            when (kind) {
                PetalShapeType.CUT -> path.lineTo(b2.x, b2.y)
                PetalShapeType.SCOOP -> {
                    val k = 0.5523f * r
                    val c1 = a + ub * k
                    val c2 = b + ua * k
                    path.cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
                }
                PetalShapeType.NOTCH -> {
                    val step = r * 0.5f
                    val p1 = c + ua * r + ub * step
                    val p2 = c + ua * step + ub * step
                    val p3 = c + ua * step + ub * r
                    path.lineTo(p1.x, p1.y)
                    path.lineTo(p2.x, p2.y)
                    path.lineTo(p3.x, p3.y)
                    path.lineTo(b.x, b.y)
                }
                else -> {
                    val ctl = when (kind) {
                        PetalShapeType.SQUIRCLE -> 0.28f
                        PetalShapeType.SMOOTH -> 0.30f
                        else -> 0.4477f // circular arc
                    }
                    val c1 = c + ua * (d * ctl)
                    val c2 = c + ub * (d * ctl)
                    path.cubicTo(c1.x, c1.y, c2.x, c2.y, b2.x, b2.y)
                }
            }
            cursor = if (kind == PetalShapeType.CUT || kind == PetalShapeType.SCOOP || kind == PetalShapeType.NOTCH) b else b2
        }

        if (tr > 0f) corner(Offset(w, 0f), Offset(w - tr, 0f), Offset(w, tr), tr)
        else { edge(path, cursor, Offset(w, 0f), ripple, 0f); cursor = Offset(w, 0f) }

        if (br > 0f) corner(Offset(w, h), Offset(w, h - br), Offset(w - br, h), br)
        else { edge(path, cursor, Offset(w, h), ripple, 0f); cursor = Offset(w, h) }

        if (bl > 0f) corner(Offset(0f, h), Offset(bl, h), Offset(0f, h - bl), bl)
        else { edge(path, cursor, Offset(0f, h), ripple, 0f); cursor = Offset(0f, h) }

        if (tl > 0f) corner(Offset(0f, 0f), Offset(0f, tl), Offset(tl, 0f), tl)
        else { edge(path, cursor, Offset(0f, 0f), ripple, 0f); cursor = Offset(0f, 0f) }

        path.close()
        return Outline.Generic(path)
    }

    private fun edge(path: Path, from: Offset, to: Offset, ripple: Boolean, r: Float) {
        val delta = to - from
        val len = hypot(delta.x, delta.y)
        if (!ripple || r <= 0f || len < r * 4f) {
            path.lineTo(to.x, to.y)
            return
        }
        val waves = floor(len / (r * 1.6f)).toInt().coerceIn(2, 24)
        val seg = len / waves
        val dir = delta / len
        val normal = Offset(dir.y, -dir.x) // outward for a clockwise walk in screen space
        val amp = (r * 0.09f).coerceAtMost(9f)
        var p = from
        for (i in 0 until waves) {
            val q = if (i == waves - 1) to else from + dir * (seg * (i + 1))
            val sign = if (i % 2 == 0) 1f else -1f
            val bump = normal * (amp * 1.33f * sign)
            val c1 = p + dir * (seg / 3f) + bump
            val c2 = q - dir * (seg / 3f) + bump
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, q.x, q.y)
            p = q
        }
    }

    private fun unit(v: Offset): Offset {
        val l = hypot(v.x, v.y)
        return if (l == 0f) Offset.Zero else Offset(v.x / l, v.y / l)
    }
}

/** Builds a corner shape of this style from per-corner radii, scaled by the live corner multiplier. */
fun PetalShapeType.cornerShape(
    topStart: Dp,
    topEnd: Dp = topStart,
    bottomEnd: Dp = topStart,
    bottomStart: Dp = topStart,
    multiplier: Float = 1f,
): CornerBasedShape {
    val m = multiplier.coerceIn(0f, 2f)
    fun s(d: Dp) = CornerSize((d.value * m).dp)
    return if (this == PetalShapeType.ROUNDED) {
        RoundedCornerShape(s(topStart), s(topEnd), s(bottomEnd), s(bottomStart))
    } else {
        PetalCornerShape(this, s(topStart), s(topEnd), s(bottomEnd), s(bottomStart))
    }
}

/** A pill (fully rounded) in this style; non-circular styles use a fixed fraction so they stay legible. */
fun PetalShapeType.pillShape(): CornerBasedShape = when (this) {
    PetalShapeType.ROUNDED, PetalShapeType.SQUIRCLE, PetalShapeType.SMOOTH, PetalShapeType.WAVY ->
        RoundedCornerShape(50)
    PetalShapeType.CUT -> PetalCornerShape(this, CornerSize(32), CornerSize(32), CornerSize(32), CornerSize(32))
    PetalShapeType.SCOOP, PetalShapeType.NOTCH ->
        PetalCornerShape(this, CornerSize(28), CornerSize(28), CornerSize(28), CornerSize(28))
}
