package com.petal.browser.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.layout.PetalCornerShape
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.cornerShape
import com.petal.browser.ui.theme.layout.pillShape

/**
 * PetalShapes
 * ─────────────────────────────────────────────────────────────────────────────
 * Authoritative Single Source of Truth for all Design System Shapes and Surfaces
 * in Petal Browser. Unifies Material 3 Expressive shapes, squircle continuous-curvature
 * surfaces, dynamic corner models (Cut, Scoop, Notch, Squircle), and button presets.
 */
object PetalShapes {

    // ── Continuous Curvature ("Squircle") Surfaces ─────────────────────────
    val Card: Shape get() = PetalSmoothShapes.Card
    val CardHero: Shape get() = PetalSmoothShapes.CardHero
    val CardPressed: Shape get() = PetalSmoothShapes.CardPressed
    val Sheet: Shape get() = PetalSmoothShapes.Sheet
    val Dialog: Shape get() = PetalSmoothShapes.Dialog
    val Action: Shape get() = PetalSmoothShapes.Action
    val ActionCompact: Shape get() = PetalSmoothShapes.ActionCompact
    val OmniboxField: Shape get() = PetalSmoothShapes.OmniboxField
    val TabItem: Shape get() = PetalSmoothShapes.TabItem
    val Snackbar: Shape get() = PetalSmoothShapes.Snackbar
    val MenuSurface: Shape get() = PetalSmoothShapes.MenuSurface
    val QuickActions: Shape get() = PetalSmoothShapes.QuickActions

    // ── Standard Geometric Primitives ──────────────────────────────────────
    val Pill: CornerBasedShape = RoundedCornerShape(50)
    val Circle: CornerBasedShape = RoundedCornerShape(50)
    val RoundedSmall: CornerBasedShape = RoundedCornerShape(12.dp)
    val RoundedMedium: CornerBasedShape = RoundedCornerShape(20.dp)
    val RoundedLarge: CornerBasedShape = RoundedCornerShape(28.dp)
    val RoundedExtraLarge: CornerBasedShape = RoundedCornerShape(36.dp)

    // ── Material 3 Expressive 35 Shapes Catalog ────────────────────────────
    val M3 get() = PetalMaterialShapes
    val allM3Shapes get() = PetalMaterialShapes.allShapes

    // ── Dynamic Dynamic Corner Factory ─────────────────────────────────────
    fun dynamic(
        type: PetalShapeType = PetalShapeType.SMOOTH,
        radius: Dp = 20.dp,
        multiplier: Float = 1f
    ): CornerBasedShape = type.cornerShape(radius, multiplier = multiplier)

    fun dynamicPill(type: PetalShapeType = PetalShapeType.SMOOTH): CornerBasedShape =
        type.pillShape()

    // ── Squircle Helper ───────────────────────────────────────────────────
    fun squircle(radius: Dp): Shape = SmoothRoundedShape(radius)
    fun squircle(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp
    ): Shape = SmoothRoundedShape(topStart, topEnd, bottomEnd, bottomStart)
}

/**
 * CompositionLocal for active PetalShapes.
 */
val LocalPetalShapes = staticCompositionLocalOf { PetalShapes }
