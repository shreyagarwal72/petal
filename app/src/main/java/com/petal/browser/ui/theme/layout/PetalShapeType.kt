package com.petal.browser.ui.theme.layout

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape types ported from ImageToolbox for dynamic corner rendering across UI components.
 */
enum class PetalShapeType(val key: String, val title: String, val subtitle: String) {
    ROUNDED("ROUNDED", "Rounded", "Classic gentle smooth rounded corners"),
    CUT("CUT", "Cut", "Chiseled geometric angled corners"),
    SQUIRCLE("SQUIRCLE", "Squircle", "Super-ellipse continuous curvature"),
    SMOOTH("SMOOTH", "Smooth", "High-order cubic curvature with soft transitions"),
    WAVY("WAVY", "Wavy", "Playful undulating kinetic contours"),
    SCOOP("SCOOP", "Scoop", "Inverted circular scooped corners"),
    NOTCH("NOTCH", "Notch", "Tactile slotted architectural notch corners");

    fun createShape(baseRadius: Dp, multiplier: Float): Shape {
        val radius = (baseRadius.value * multiplier.coerceIn(0f, 2f)).dp
        return when (this) {
            ROUNDED, SMOOTH, SQUIRCLE, WAVY -> RoundedCornerShape(radius)
            CUT, SCOOP, NOTCH -> CutCornerShape(radius)
        }
    }

    companion object {
        fun fromKey(key: String?): PetalShapeType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ROUNDED
    }
}
