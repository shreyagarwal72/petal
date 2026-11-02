/*
 * PetalCookieShape.kt
 * ─────────────────────────────────────────────────────────────────────────
 * PixelPlayer-inspired scalloped cookie / rounded star shape for album artwork.
 * Renders an N-sided smooth scalloped cookie silhouette with continuous curves.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 Expressive Scalloped Cookie Shape adopted from PixelPlayer.
 *
 * @param sides Number of lobes / scallops (default 8 for Android 14/15 cookie art).
 * @param curve Curvature depth between 0.0 (smooth circle) and 0.2 (deep lobes).
 * @param rotation Rotation in degrees.
 * @param iterations Polygon point count for smoothness.
 */
class PetalCookieShape(
    private val sides: Int = 8,
    private val curve: Double = 0.085,
    private val rotation: Float = 0f,
    iterations: Int = 360,
) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val steps = TWO_PI / min(iterations, 360)
    private val rotationRad = (PI / 180.0) * rotation

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {
        // Map curve so inner lobe stays within boundaries
        val scaleFactor = mapRange(1.0, 0.0, 0.5, 1.0, curve)
        val r = min(size.height, size.width) * 0.48 * scaleFactor

        val xCenter = size.width * 0.5f
        val yCenter = size.height * 0.5f

        fun pointAt(t: Double): Pair<Float, Float> {
            val rLobe = r * (1.0 + curve * cos(sides * t))
            val x = rLobe * cos(t - rotationRad)
            val y = rLobe * sin(t - rotationRad)
            return (x + xCenter).toFloat() to (y + yCenter).toFloat()
        }

        val (startX, startY) = pointAt(0.0)
        moveTo(startX, startY)

        var t = steps
        while (t < TWO_PI) {
            val (x, y) = pointAt(t)
            lineTo(x, y)
            t += steps
        }

        close()
    })

    private fun mapRange(a: Double, b: Double, c: Double, d: Double, x: Double): Double {
        return (x - a) / (b - a) * (d - c) + c
    }
}
