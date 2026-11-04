package com.petal.browser.ui.theme.layout

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Shape types ported from ImageToolbox for dynamic corner rendering across UI components.
 */
enum class PetalShapeType(val key: String, val title: String, val subtitle: String) {
    ROUNDED("ROUNDED", "Rounded", "Classic gentle smooth rounded corners"),
    CUT("CUT", "Cut", "Chamfered geometric corners"),
    SQUIRCLE("SQUIRCLE", "Squircle", "Tight super-ellipse corners"),
    SMOOTH("SMOOTH", "Smooth", "Continuous-curvature corners that ease into the edge"),
    WAVY("WAVY", "Wavy", "Rounded corners with a gentle rippled edge"),
    SCOOP("SCOOP", "Scoop", "Concave scooped-out corners"),
    NOTCH("NOTCH", "Notch", "Stepped square notches");

    /** Uniform-radius shape of this style, scaled by the live corner multiplier. */
    fun createShape(baseRadius: Dp, multiplier: Float): Shape = cornerShape(baseRadius, multiplier = multiplier)

    companion object {
        fun fromKey(key: String?): PetalShapeType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: ROUNDED
    }
}
