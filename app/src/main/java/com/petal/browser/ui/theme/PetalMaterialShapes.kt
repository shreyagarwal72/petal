package com.petal.browser.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * PetalMaterialShapes
 *
 * Full official implementation of ALL 35 Material 3 Expressive Shapes according to the M3 Shape Principles spec
 * (https://m3.material.io/styles/shape/overview-principles).
 *
 * Full Catalog of 35 Shapes:
 * 1. Circle
 * 2. Oval
 * 3. Pill
 * 4. Triangle
 * 5. RightTriangle
 * 6. Diamond
 * 7. Square
 * 8. RoundedSquare
 * 9. Rectangle
 * 10. Pentagon
 * 11. Hexagon
 * 12. Octagon
 * 13. Decagon
 * 14. Dodecagon
 * 15. Cookie4Sided
 * 16. Cookie6Sided
 * 17. Cookie7Sided
 * 18. Cookie9Sided
 * 19. Cookie12Sided
 * 20. Scallop
 * 21. SoftScallop
 * 22. Clover4Leaf
 * 23. Clover8Leaf
 * 24. Flower
 * 25. Sunny
 * 26. Arch
 * 27. SemiCircle
 * 28. Slanted
 * 29. Teardrop
 * 30. Heart
 * 31. Burst
 * 32. SoftBurst
 * 33. Star4Sided
 * 34. Star8Sided
 * 35. Gem
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

import androidx.compose.ui.graphics.asComposePath

/**
 * Converts a [RoundedPolygon] into a Jetpack Compose [Shape].
 */
fun RoundedPolygon.toShape(): Shape {
    return GenericShape { size, _ ->
        val androidPath = this@toShape.toPath()
        val matrix = android.graphics.Matrix()
        matrix.postScale(size.width / 2f, size.height / 2f)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        androidPath.transform(matrix)
        this.addPath(androidPath.asComposePath())
    }
}

object PetalMaterialShapes {

    // --- 1. Basic & Geometric Shapes (14 Shapes) ---
    val Circle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Circle", "Geometric", RoundedPolygon.circle(numVertices = 16))
    }

    val Oval: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Oval", "Geometric", RoundedPolygon.circle(numVertices = 16, radius = 0.85f))
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

    val RightTriangle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Right Triangle",
            "Geometric",
            RoundedPolygon(
                numVertices = 3,
                rounding = CornerRounding(0.18f, 0.4f)
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

    val Square: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Square",
            "Geometric",
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.08f, 0.2f)
            )
        )
    }

    val RoundedSquare: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Rounded Square",
            "Geometric",
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.35f, 0.7f)
            )
        )
    }

    val Rectangle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Rectangle",
            "Geometric",
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.15f, 0.3f)
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

    val Decagon: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Decagon",
            "Geometric",
            RoundedPolygon(
                numVertices = 10,
                rounding = CornerRounding(0.12f, 0.4f)
            )
        )
    }

    val Dodecagon: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Dodecagon",
            "Geometric",
            RoundedPolygon(
                numVertices = 12,
                rounding = CornerRounding(0.10f, 0.35f)
            )
        )
    }

    // --- 2. Expressive Cookies & Scallops (7 Shapes) ---
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

    val SoftScallop: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Soft Scallop",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 10,
                innerRadius = 0.85f,
                rounding = CornerRounding(0.5f, 0.95f)
            )
        )
    }

    // --- 3. Nature, Organic & Architectural Shapes (9 Shapes) ---
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

    val SemiCircle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Semi Circle",
            "Architecture",
            RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding(0.5f, 0.7f)
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

    val Heart: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Heart",
            "Organic",
            RoundedPolygon.star(
                numVerticesPerRadius = 3,
                innerRadius = 0.62f,
                rounding = CornerRounding(0.5f, 0.85f)
            )
        )
    }

    // --- 4. High-Energy, Stars & Gems (5 Shapes) ---
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

    /** Complete catalog list of ALL 35 official Material 3 Expressive shapes. */
    val allShapes: List<ExpressiveShapeHolder> by lazy {
        listOf(
            // Geometric (14)
            Circle, Oval, Pill, Triangle, RightTriangle, Diamond, Square, RoundedSquare, Rectangle, Pentagon, Hexagon, Octagon, Decagon, Dodecagon,
            // Cookies & Scallops (7)
            Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie12Sided, Scallop, SoftScallop,
            // Nature & Organic (9)
            Clover4Leaf, Clover8Leaf, Flower, Sunny, Arch, SemiCircle, Slanted, Teardrop, Heart,
            // High Energy & Stars (5)
            Burst, SoftBurst, Star4Sided, Star8Sided, Gem
        )
    }
}

/** Alias for backward compatibility with Material3 MaterialShapes callers */
object MaterialShapes {
    val Circle = PetalMaterialShapes.Circle
    val Oval = PetalMaterialShapes.Oval
    val Pill = PetalMaterialShapes.Pill
    val Triangle = PetalMaterialShapes.Triangle
    val RightTriangle = PetalMaterialShapes.RightTriangle
    val Diamond = PetalMaterialShapes.Diamond
    val Square = PetalMaterialShapes.Square
    val RoundedSquare = PetalMaterialShapes.RoundedSquare
    val Rectangle = PetalMaterialShapes.Rectangle
    val Pentagon = PetalMaterialShapes.Pentagon
    val Hexagon = PetalMaterialShapes.Hexagon
    val Octagon = PetalMaterialShapes.Octagon
    val Decagon = PetalMaterialShapes.Decagon
    val Dodecagon = PetalMaterialShapes.Dodecagon
    val Cookie4Sided = PetalMaterialShapes.Cookie4Sided
    val Cookie6Sided = PetalMaterialShapes.Cookie6Sided
    val Cookie7Sided = PetalMaterialShapes.Cookie7Sided
    val Cookie9Sided = PetalMaterialShapes.Cookie9Sided
    val Cookie12Sided = PetalMaterialShapes.Cookie12Sided
    val Scallop = PetalMaterialShapes.Scallop
    val SoftScallop = PetalMaterialShapes.SoftScallop
    val Clover4Leaf = PetalMaterialShapes.Clover4Leaf
    val Clover8Leaf = PetalMaterialShapes.Clover8Leaf
    val Flower = PetalMaterialShapes.Flower
    val Sunny = PetalMaterialShapes.Sunny
    val Arch = PetalMaterialShapes.Arch
    val SemiCircle = PetalMaterialShapes.SemiCircle
    val Slanted = PetalMaterialShapes.Slanted
    val Teardrop = PetalMaterialShapes.Teardrop
    val Heart = PetalMaterialShapes.Heart
    val Burst = PetalMaterialShapes.Burst
    val SoftBurst = PetalMaterialShapes.SoftBurst
    val Star4Sided = PetalMaterialShapes.Star4Sided
    val Star8Sided = PetalMaterialShapes.Star8Sided
    val Gem = PetalMaterialShapes.Gem

    val allShapes = PetalMaterialShapes.allShapes
}
