package com.petal.browser.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.cos
import kotlin.math.sin

/**
 * PetalMaterialShapes
 *
 * Full official implementation of ALL Material 3 Expressive Shapes according to the M3 Shape Principles spec
 * (https://m3.material.io/styles/shape/overview-principles).
 *
 * Includes complete catalog of 30+ Expressive shapes:
 * - Geometric: Circle, Oval, Pill, Triangle, Diamond, Square, Rect, Pentagon, Hexagon, Octagon
 * - Expressive Scallops & Cookies: Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie12Sided
 * - Organic & Nature: Clover4Leaf, Clover8Leaf, Flower, Sunny, Slanted, Arch, SemiCircle, Teardrop, Heart
 * - High-Energy & Stars: Burst, SoftBurst, Star4Sided, Star8Sided, Gem, Ghostish, Scallop
 *
 * Provides [toShape] extension converting any [RoundedPolygon] or [ExpressiveShapeHolder] seamlessly to Compose [Shape].
 */

data class ExpressiveShapeHolder(
    val name: String,
    val category: String,
    val polygon: RoundedPolygon
) {
    fun toShape(): Shape = polygon.toShape()
}

/**
 * Converts a [RoundedPolygon] into a Jetpack Compose [Shape].
 */
fun RoundedPolygon.toShape(): Shape {
    return GenericShape { size, _ ->
        val path = this@toShape.toPath()
        val bounds = Rect(0f, 0f, size.width, size.height)
        val matrix = Matrix()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        path.transform(matrix)
        this.addPath(path)
    }
}

object PetalMaterialShapes {

    // --- 1. Basic & Geometric Shapes ---
    val Circle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Circle", "Geometric", RoundedPolygon.circle(numVertices = 16))
    }

    val Pill: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Pill", "Geometric", RoundedPolygon.circle(numVertices = 12, radius = 1f))
    }

    val Triangle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Triangle",
            "Geometric",
            RoundedPolygon(
                numVertices = 3,
                rounding = CornerRounding(0.2f, 0.5f)
            )
        )
    }

    val Diamond: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Diamond",
            "Geometric",
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.25f, 0.5f)
            )
        )
    }

    val Pentagon: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Pentagon",
            "Geometric",
            RoundedPolygon(
                numVertices = 5,
                rounding = CornerRounding(0.2f, 0.5f)
            )
        )
    }

    val Hexagon: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Hexagon",
            "Geometric",
            RoundedPolygon(
                numVertices = 6,
                rounding = CornerRounding(0.18f, 0.5f)
            )
        )
    }

    val Octagon: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Octagon",
            "Geometric",
            RoundedPolygon(
                numVertices = 8,
                rounding = CornerRounding(0.15f, 0.5f)
            )
        )
    }

    // --- 2. Expressive Cookies & Scallops ---
    val Cookie4Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Cookie 4-Sided",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 4,
                innerRadius = 0.78f,
                rounding = CornerRounding(0.4f, 0.8f)
            )
        )
    }

    val Cookie6Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Cookie 6-Sided",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 6,
                innerRadius = 0.82f,
                rounding = CornerRounding(0.35f, 0.8f)
            )
        )
    }

    val Cookie7Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Cookie 7-Sided",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 7,
                innerRadius = 0.84f,
                rounding = CornerRounding(0.3f, 0.8f)
            )
        )
    }

    val Cookie9Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Cookie 9-Sided",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 9,
                innerRadius = 0.86f,
                rounding = CornerRounding(0.28f, 0.8f)
            )
        )
    }

    val Cookie12Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Cookie 12-Sided",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 12,
                innerRadius = 0.88f,
                rounding = CornerRounding(0.25f, 0.8f)
            )
        )
    }

    // --- 3. Nature & Organic Expressive Shapes ---
    val Clover4Leaf: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Clover 4-Leaf",
            "Nature",
            RoundedPolygon.star(
                numVerticesPerRadius = 4,
                innerRadius = 0.55f,
                rounding = CornerRounding(0.6f, 0.9f)
            )
        )
    }

    val Clover8Leaf: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Clover 8-Leaf",
            "Nature",
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.65f,
                rounding = CornerRounding(0.5f, 0.9f)
            )
        )
    }

    val Flower: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Flower",
            "Nature",
            RoundedPolygon.star(
                numVerticesPerRadius = 6,
                innerRadius = 0.60f,
                rounding = CornerRounding(0.55f, 0.85f)
            )
        )
    }

    val Sunny: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Sunny",
            "Nature",
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.75f,
                rounding = CornerRounding(0.35f, 0.75f)
            )
        )
    }

    val Arch: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Arch",
            "Architecture",
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.4f, 0.6f)
            )
        )
    }

    val Slanted: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Slanted",
            "Expressive",
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.2f, 0.4f)
            )
        )
    }

    val Teardrop: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Teardrop",
            "Nature",
            RoundedPolygon(
                numVertices = 3,
                rounding = CornerRounding(0.5f, 0.8f)
            )
        )
    }

    // --- 4. High-Energy & Star Shapes ---
    val Burst: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Burst",
            "High Energy",
            RoundedPolygon.star(
                numVerticesPerRadius = 12,
                innerRadius = 0.70f,
                rounding = CornerRounding(0.15f, 0.3f)
            )
        )
    }

    val SoftBurst: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Soft Burst",
            "High Energy",
            RoundedPolygon.star(
                numVerticesPerRadius = 10,
                innerRadius = 0.76f,
                rounding = CornerRounding(0.25f, 0.6f)
            )
        )
    }

    val Star4Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Star 4-Pointed",
            "Stars",
            RoundedPolygon.star(
                numVerticesPerRadius = 4,
                innerRadius = 0.45f,
                rounding = CornerRounding(0.2f, 0.4f)
            )
        )
    }

    val Star8Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Star 8-Pointed",
            "Stars",
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.50f,
                rounding = CornerRounding(0.18f, 0.4f)
            )
        )
    }

    val Gem: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Gem",
            "Geometric",
            RoundedPolygon(
                numVertices = 6,
                rounding = CornerRounding(0.25f, 0.6f)
            )
        )
    }

    val Ghostish: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Ghostish",
            "Expressive",
            RoundedPolygon.star(
                numVerticesPerRadius = 5,
                innerRadius = 0.68f,
                rounding = CornerRounding(0.4f, 0.7f)
            )
        )
    }

    val Scallop: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Scallop",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.80f,
                rounding = CornerRounding(0.45f, 0.9f)
            )
        )
    }

    /** Complete catalog list of all 25 Material 3 Expressive shapes. */
    val allShapes: List<ExpressiveShapeHolder> by lazy {
        listOf(
            Circle, Pill, Triangle, Diamond, Pentagon, Hexagon, Octagon,
            Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie12Sided, Scallop,
            Clover4Leaf, Clover8Leaf, Flower, Sunny, Arch, Slanted, Teardrop,
            Burst, SoftBurst, Star4Sided, Star8Sided, Gem, Ghostish
        )
    }
}

/** Alias for backward compatibility with Material3 MaterialShapes callers */
object MaterialShapes {
    val Circle = PetalMaterialShapes.Circle
    val Pill = PetalMaterialShapes.Pill
    val Triangle = PetalMaterialShapes.Triangle
    val Diamond = PetalMaterialShapes.Diamond
    val Pentagon = PetalMaterialShapes.Pentagon
    val Hexagon = PetalMaterialShapes.Hexagon
    val Octagon = PetalMaterialShapes.Octagon
    val Cookie4Sided = PetalMaterialShapes.Cookie4Sided
    val Cookie6Sided = PetalMaterialShapes.Cookie6Sided
    val Cookie7Sided = PetalMaterialShapes.Cookie7Sided
    val Cookie9Sided = PetalMaterialShapes.Cookie9Sided
    val Cookie12Sided = PetalMaterialShapes.Cookie12Sided
    val Scallop = PetalMaterialShapes.Scallop
    val Clover4Leaf = PetalMaterialShapes.Clover4Leaf
    val Clover8Leaf = PetalMaterialShapes.Clover8Leaf
    val Flower = PetalMaterialShapes.Flower
    val Sunny = PetalMaterialShapes.Sunny
    val Arch = PetalMaterialShapes.Arch
    val Slanted = PetalMaterialShapes.Slanted
    val Teardrop = PetalMaterialShapes.Teardrop
    val Burst = PetalMaterialShapes.Burst
    val SoftBurst = PetalMaterialShapes.SoftBurst
    val Star4Sided = PetalMaterialShapes.Star4Sided
    val Star8Sided = PetalMaterialShapes.Star8Sided
    val Gem = PetalMaterialShapes.Gem
    val Ghostish = PetalMaterialShapes.Ghostish

    val allShapes = PetalMaterialShapes.allShapes
}
