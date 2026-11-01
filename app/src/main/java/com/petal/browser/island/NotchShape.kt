package com.petal.browser.island

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

enum class NotchEdge { TOP, LEFT, RIGHT }

/**
 * A notch shape hanging from one edge whose corners flare outwards into it by [flare].
 * Adapted from expressive-cutout for Petal's custom island.
 */
data class NotchShape(
    val flare: Dp,
    val bottomLeft: Dp,
    val bottomRight: Dp,
    val edge: NotchEdge = NotchEdge.TOP,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(path(size, density, includeFlatEdge = true))
    }

    fun path(size: Size, density: Density, includeFlatEdge: Boolean): Path {
        val sideways = edge != NotchEdge.TOP
        val span = if (sideways) size.height else size.width
        val depth = if (sideways) size.width else size.height
        return topEdgedPath(span, depth, density, includeFlatEdge).apply {
            if (sideways) transform(quarterTurn(size))
        }
    }

    private fun topEdgedPath(span: Float, depth: Float, density: Density, includeFlatEdge: Boolean) =
        with(density) {
            val f = flare.toPx().coerceIn(0f, minOf(span / 2f, depth))
            val body = span - 2f * f
            val maxFar = minOf(body / 2f, depth - f).coerceAtLeast(0f)
            val bl = bottomLeft.toPx().coerceIn(0f, maxFar)
            val br = bottomRight.toPx().coerceIn(0f, maxFar)

            Path().apply {
                if (includeFlatEdge) {
                    moveTo(0f, 0f)
                    lineTo(span, 0f)
                } else {
                    moveTo(span, 0f)
                }
                arcTo(Rect(span - f, 0f, span + f, 2f * f), 270f, -90f, false)
                lineTo(span - f, depth - br)
                arcTo(Rect(span - f - 2f * br, depth - 2f * br, span - f, depth), 0f, 90f, false)
                lineTo(f + bl, depth)
                arcTo(Rect(f, depth - 2f * bl, f + 2f * bl, depth), 90f, 90f, false)
                lineTo(f, f)
                arcTo(Rect(-f, 0f, f, 2f * f), 0f, -90f, false)
                if (includeFlatEdge) close()
            }
        }

    private fun quarterTurn(size: Size) = Matrix().apply {
        if (edge == NotchEdge.LEFT) {
            translate(0f, size.height)
            rotateZ(-90f)
        } else {
            translate(size.width, 0f)
            rotateZ(90f)
        }
    }
}
