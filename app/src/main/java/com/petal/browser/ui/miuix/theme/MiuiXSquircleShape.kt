package com.petal.browser.ui.miuix.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
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

/**
 * Continuous Curvature Squircle Shape (HyperOS / MIUIX signature superellipse).
 *
 * Implements G2 curvature continuity (superellipse smoothing) matching HyperOS guidelines
 * and the compose-miuix-ui squircle module. Unlike a standard circular-arc rounded rectangle,
 * the curvature transitions smoothly from zero at the edge tangents to peak at the diagonal,
 * eliminating the visual harshness at arc transition points.
 *
 * @param smoothing Curvature smoothing factor (0.0 = classic circular arc, 0.68 = HyperOS standard, 1.0 = deep squircle).
 */
class MiuiXSquircleShape(
    val smoothing: Float = 0.68f,
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(
        radius: Dp,
        smoothing: Float = 0.68f,
    ) : this(
        smoothing = smoothing,
        topStart = CornerSize(radius),
        topEnd = CornerSize(radius),
        bottomEnd = CornerSize(radius),
        bottomStart = CornerSize(radius),
    )

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = MiuiXSquircleShape(smoothing, topStart, topEnd, bottomEnd, bottomStart)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }

        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = if (ltr) topStart else topEnd
        val tr = if (ltr) topEnd else topStart
        val br = if (ltr) bottomEnd else bottomStart
        val bl = if (ltr) bottomStart else bottomEnd

        // Fallback to fast native rounded rect if smoothing is zero
        if (smoothing <= 0.01f) {
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

        // Clockwise contour tracing with G2 superellipse cubic bezier segments
        var cursor = Offset(tl, 0f)
        path.moveTo(cursor.x, cursor.y)

        fun drawSquircleCorner(
            corner: Offset,
            incomingEdge: Offset,
            outgoingEdge: Offset,
            radius: Float,
        ) {
            val ua = unitVector(incomingEdge - corner)
            val ub = unitVector(outgoingEdge - corner)

            // Extends tangent distance outward proportional to smoothing factor
            val maxExtension = (minDim / 2f - radius).coerceAtLeast(0f)
            val ext = (radius * smoothing * 0.35f).coerceAtMost(maxExtension)
            val d = radius + ext

            val a2 = corner + ua * d
            val b2 = corner + ub * d

            // Draw straight edge to start of corner curve
            path.lineTo(a2.x, a2.y)

            // G2 continuous control points
            val ctlFactor = 0.28f + (1f - smoothing) * 0.1677f
            val c1 = corner + ua * (d * ctlFactor)
            val c2 = corner + ub * (d * ctlFactor)

            path.cubicTo(c1.x, c1.y, c2.x, c2.y, b2.x, b2.y)
            cursor = b2
        }

        // Top-Right corner
        if (tr > 0f) {
            drawSquircleCorner(Offset(w, 0f), Offset(w - tr, 0f), Offset(w, tr), tr)
        } else {
            path.lineTo(w, 0f)
            cursor = Offset(w, 0f)
        }

        // Bottom-Right corner
        if (br > 0f) {
            drawSquircleCorner(Offset(w, h), Offset(w, h - br), Offset(w - br, h), br)
        } else {
            path.lineTo(w, h)
            cursor = Offset(w, h)
        }

        // Bottom-Left corner
        if (bl > 0f) {
            drawSquircleCorner(Offset(0f, h), Offset(bl, h), Offset(0f, h - bl), bl)
        } else {
            path.lineTo(0f, h)
            cursor = Offset(0f, h)
        }

        // Top-Left corner
        if (tl > 0f) {
            drawSquircleCorner(Offset(0f, 0f), Offset(0f, tl), Offset(tl, 0f), tl)
        } else {
            path.lineTo(0f, 0f)
            cursor = Offset(0f, 0f)
        }

        path.close()
        return Outline.Generic(path)
    }

    private fun unitVector(v: Offset): Offset {
        val len = kotlin.math.hypot(v.x, v.y)
        return if (len <= 0.0001f) Offset.Zero else Offset(v.x / len, v.y / len)
    }
}

/** Standard MiuiX Squircle presets matching HyperOS component hierarchy. */
object MiuiXShapes {
    val Pill: CornerBasedShape = MiuiXSquircleShape(9999.dp, smoothing = 0.68f)
    val ExtraLarge: CornerBasedShape = MiuiXSquircleShape(32.dp, smoothing = 0.68f)
    val Large: CornerBasedShape = MiuiXSquircleShape(24.dp, smoothing = 0.68f)
    val Card: CornerBasedShape = MiuiXSquircleShape(20.dp, smoothing = 0.68f)
    val Medium: CornerBasedShape = MiuiXSquircleShape(16.dp, smoothing = 0.68f)
    val Small: CornerBasedShape = MiuiXSquircleShape(12.dp, smoothing = 0.68f)
    val ExtraSmall: CornerBasedShape = MiuiXSquircleShape(8.dp, smoothing = 0.68f)
    val Badge: CornerBasedShape = MiuiXSquircleShape(14.dp, smoothing = 0.65f)
}
