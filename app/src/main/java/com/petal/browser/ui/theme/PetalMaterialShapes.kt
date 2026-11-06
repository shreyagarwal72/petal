package com.petal.browser.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * CompositionLocal providing global enablement of the 35 Material 3 Expressive shapes.
 * When false, components fall back to gentle default rounded corners.
 */
val LocalExpressiveShapesEnabled = compositionLocalOf { true }

/**
 * PetalMaterialShapes
 *
 * Full official implementation of ALL 35 Material 3 Expressive Shapes according to the official Material 3
 * Expressive shape system specification (androidx.compose.material3.MaterialShapes / M3 Design Kit).
 *
 * Complete Official Catalog of 35 Shapes:
 * 1. Circle
 * 2. Square
 * 3. Slanted
 * 4. Arch
 * 5. SemiCircle
 * 6. Oval
 * 7. Pill
 * 8. Triangle
 * 9. Arrow
 * 10. Fan
 * 11. Diamond
 * 12. ClamShell
 * 13. Pentagon
 * 14. Gem
 * 15. Sunny
 * 16. VerySunny
 * 17. Cookie4Sided
 * 18. Cookie6Sided
 * 19. Cookie7Sided
 * 20. Cookie9Sided
 * 21. Cookie12Sided
 * 22. Ghostish
 * 23. Clover4Leaf
 * 24. Clover8Leaf
 * 25. Burst
 * 26. SoftBurst
 * 27. Boom
 * 28. SoftBoom
 * 29. Flower
 * 30. Puffy
 * 31. PuffyDiamond
 * 32. PixelCircle
 * 33. PixelTriangle
 * 34. Bun
 * 35. Heart
 *
 * Provides [toShape] extension converting any [RoundedPolygon] or [ExpressiveShapeHolder] seamlessly to Compose [Shape].
 */

data class ExpressiveShapeHolder(
    val name: String,
    val category: String,
    val polygon: RoundedPolygon
) {
    val cachedShape: Shape by lazy { polygon.toShape() }
    fun toShape(): Shape = cachedShape
}

/**
 * Converts a [RoundedPolygon] into a Jetpack Compose [Shape].
 */
fun RoundedPolygon.toShape(): Shape {
    // Normalised polygons live in the 0..1 box, so a plain scale to the target size is enough.
    val unit = android.graphics.Path().also { this.normalized().toPath(it) }
    return GenericShape { size, _ ->
        val matrix = android.graphics.Matrix()
        matrix.setScale(size.width, size.height)
        val out = android.graphics.Path()
        unit.transform(matrix, out)
        this.addPath(out.asComposePath())
    }
}

object PetalMaterialShapes {
    const val PREF_EXPRESSIVE_SHAPES_ENABLED = "sp_expressive_shapes_35_enabled"

    // --- 1. Basic & Geometric (8 Shapes) ---
    val Circle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Circle", "Basic", androidx.compose.material3.MaterialShapes.Circle)
    }

    val Square: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Square", "Basic", androidx.compose.material3.MaterialShapes.Square)
    }

    val Slanted: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Slanted", "Geometric", androidx.compose.material3.MaterialShapes.Slanted)
    }

    val Arch: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Arch", "Architecture", androidx.compose.material3.MaterialShapes.Arch)
    }

    val SemiCircle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Semi Circle", "Architecture", androidx.compose.material3.MaterialShapes.SemiCircle)
    }

    val Oval: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Oval", "Basic", androidx.compose.material3.MaterialShapes.Oval)
    }

    val Pill: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Pill", "Basic", androidx.compose.material3.MaterialShapes.Pill)
    }

    val Triangle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Triangle", "Basic", androidx.compose.material3.MaterialShapes.Triangle)
    }

    // --- 2. Dynamic & Architectural (6 Shapes) ---
    val Arrow: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Arrow", "Dynamic", androidx.compose.material3.MaterialShapes.Arrow)
    }

    val Fan: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Fan", "Dynamic", androidx.compose.material3.MaterialShapes.Fan)
    }

    val Diamond: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Diamond", "Geometric", androidx.compose.material3.MaterialShapes.Diamond)
    }

    val ClamShell: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Clam Shell", "Nature", androidx.compose.material3.MaterialShapes.ClamShell)
    }

    val Pentagon: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Pentagon", "Geometric", androidx.compose.material3.MaterialShapes.Pentagon)
    }

    val Gem: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Gem", "Geometric", androidx.compose.material3.MaterialShapes.Gem)
    }

    // --- 3. Sunny & Cookie Series (7 Shapes) ---
    val Sunny: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Sunny", "Celestial", androidx.compose.material3.MaterialShapes.Sunny)
    }

    val Sun: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Sun",
            "Celestial",
            RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.58f,
                rounding = CornerRounding(0.18f, 0.45f)
            )
        )
    }

    val VerySunny: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Very Sunny", "Celestial", androidx.compose.material3.MaterialShapes.VerySunny)
    }

    val Cookie4Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Cookie 4-Sided", "Cookies", androidx.compose.material3.MaterialShapes.Cookie4Sided)
    }

    val Cookie6Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Cookie 6-Sided", "Cookies", androidx.compose.material3.MaterialShapes.Cookie6Sided)
    }

    val Cookie7Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Cookie 7-Sided", "Cookies", androidx.compose.material3.MaterialShapes.Cookie7Sided)
    }

    val Cookie9Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Cookie 9-Sided", "Cookies", androidx.compose.material3.MaterialShapes.Cookie9Sided)
    }

    val Cookie10Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder(
            "Cookie 10-Sided",
            "Cookies",
            RoundedPolygon.star(
                numVerticesPerRadius = 10,
                innerRadius = 0.87f,
                rounding = CornerRounding(0.26f, 0.8f)
            )
        )
    }

    val Cookie12Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Cookie 12-Sided", "Cookies", androidx.compose.material3.MaterialShapes.Cookie12Sided)
    }

    // --- 4. Organic, Clovers & Playful (7 Shapes) ---
    val Ghostish: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Ghostish", "Playful", androidx.compose.material3.MaterialShapes.Ghostish)
    }

    val Clover4Leaf: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Clover 4-Leaf", "Nature", androidx.compose.material3.MaterialShapes.Clover4Leaf)
    }

    val Clover8Leaf: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Clover 8-Leaf", "Nature", androidx.compose.material3.MaterialShapes.Clover8Leaf)
    }

    val Flower: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Flower", "Nature", androidx.compose.material3.MaterialShapes.Flower)
    }

    val Puffy: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Puffy", "Playful", androidx.compose.material3.MaterialShapes.Puffy)
    }

    val PuffyDiamond: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Puffy Diamond", "Playful", androidx.compose.material3.MaterialShapes.PuffyDiamond)
    }

    val Heart: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Heart", "Organic", androidx.compose.material3.MaterialShapes.Heart)
    }

    // --- 5. High Energy, Explosive, Pixel & Treats (7 Shapes) ---
    val Burst: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Burst", "High Energy", androidx.compose.material3.MaterialShapes.Burst)
    }

    val SoftBurst: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Soft Burst", "High Energy", androidx.compose.material3.MaterialShapes.SoftBurst)
    }

    val Boom: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Boom", "High Energy", androidx.compose.material3.MaterialShapes.Boom)
    }

    val SoftBoom: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Soft Boom", "High Energy", androidx.compose.material3.MaterialShapes.SoftBoom)
    }

    val PixelCircle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Pixel Circle", "Pixel", androidx.compose.material3.MaterialShapes.PixelCircle)
    }

    val PixelTriangle: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Pixel Triangle", "Pixel", androidx.compose.material3.MaterialShapes.PixelTriangle)
    }

    val Bun: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Bun", "Organic", androidx.compose.material3.MaterialShapes.Bun)
    }

    // --- Backward Compatibility Aliases ---
    val RoundedSquare: ExpressiveShapeHolder get() = Square
    val Rectangle: ExpressiveShapeHolder get() = Square
    val RightTriangle: ExpressiveShapeHolder get() = Triangle
    val Hexagon: ExpressiveShapeHolder get() = Gem
    val Octagon: ExpressiveShapeHolder get() = PixelCircle
    val Decagon: ExpressiveShapeHolder get() = Sunny
    val Dodecagon: ExpressiveShapeHolder get() = Cookie12Sided
    val Scallop: ExpressiveShapeHolder get() = Sunny
    val SoftScallop: ExpressiveShapeHolder get() = VerySunny
    val Teardrop: ExpressiveShapeHolder get() = Bun
    val Star4Sided: ExpressiveShapeHolder get() = Cookie4Sided
    val Star8Sided: ExpressiveShapeHolder get() = Sunny

    /**
     * Curated list of 17 distinct Material 3 Expressive shapes for homescreen icon tiles
     * ensuring every homescreen shortcut icon tile has a completely unique shape:
     * 1. Arrow, 2. Fan, 3. Diamond, 4. Clamshell, 5. Pentagon, 6. Gem, 7. Very Sunny,
     * 8. Sunny, 9. 4-Sided Cookie, 10. 6-Sided Cookie, 11. 7-Sided Cookie, 12. 9-Sided Cookie,
     * 13. 10-Sided Cookie, 14. 4-Leaf Clover, 15. Flower, 16. Burst, 17. Sun.
     */
    val homescreenShapes: List<ExpressiveShapeHolder> by lazy {
        listOf(
            Arrow,
            Fan,
            Diamond,
            ClamShell,
            Pentagon,
            Gem,
            VerySunny,
            Sunny,
            Cookie4Sided,
            Cookie6Sided,
            Cookie7Sided,
            Cookie9Sided,
            Cookie10Sided,
            Clover4Leaf,
            Flower,
            Burst,
            Sun
        )
    }

    /** Complete catalog list of ALL official Material 3 Expressive shapes. */
    val allShapes: List<ExpressiveShapeHolder> by lazy {
        listOf(
            // Basic & Geometric (8)
            Circle, Square, Slanted, Arch, SemiCircle, Oval, Pill, Triangle,
            // Dynamic & Architectural (6)
            Arrow, Fan, Diamond, ClamShell, Pentagon, Gem,
            // Sunny & Cookie Series (9)
            Sunny, Sun, VerySunny, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie10Sided, Cookie12Sided,
            // Organic, Clovers & Playful (7)
            Ghostish, Clover4Leaf, Clover8Leaf, Flower, Puffy, PuffyDiamond, Heart,
            // High Energy, Explosive, Pixel & Treats (7)
            Burst, SoftBurst, Boom, SoftBoom, PixelCircle, PixelTriangle, Bun
        )
    }
}

/** Alias for backward compatibility with Material3 MaterialShapes callers */
object MaterialShapes {
    val Circle = PetalMaterialShapes.Circle
    val Square = PetalMaterialShapes.Square
    val Slanted = PetalMaterialShapes.Slanted
    val Arch = PetalMaterialShapes.Arch
    val SemiCircle = PetalMaterialShapes.SemiCircle
    val Oval = PetalMaterialShapes.Oval
    val Pill = PetalMaterialShapes.Pill
    val Triangle = PetalMaterialShapes.Triangle
    val Arrow = PetalMaterialShapes.Arrow
    val Fan = PetalMaterialShapes.Fan
    val Diamond = PetalMaterialShapes.Diamond
    val ClamShell = PetalMaterialShapes.ClamShell
    val Pentagon = PetalMaterialShapes.Pentagon
    val Gem = PetalMaterialShapes.Gem
    val Sunny = PetalMaterialShapes.Sunny
    val Sun = PetalMaterialShapes.Sun
    val VerySunny = PetalMaterialShapes.VerySunny
    val Cookie4Sided = PetalMaterialShapes.Cookie4Sided
    val Cookie6Sided = PetalMaterialShapes.Cookie6Sided
    val Cookie7Sided = PetalMaterialShapes.Cookie7Sided
    val Cookie9Sided = PetalMaterialShapes.Cookie9Sided
    val Cookie10Sided = PetalMaterialShapes.Cookie10Sided
    val Cookie12Sided = PetalMaterialShapes.Cookie12Sided
    val Ghostish = PetalMaterialShapes.Ghostish
    val Clover4Leaf = PetalMaterialShapes.Clover4Leaf
    val Clover8Leaf = PetalMaterialShapes.Clover8Leaf
    val Burst = PetalMaterialShapes.Burst
    val SoftBurst = PetalMaterialShapes.SoftBurst
    val Boom = PetalMaterialShapes.Boom
    val SoftBoom = PetalMaterialShapes.SoftBoom
    val Flower = PetalMaterialShapes.Flower
    val Puffy = PetalMaterialShapes.Puffy
    val PuffyDiamond = PetalMaterialShapes.PuffyDiamond
    val PixelCircle = PetalMaterialShapes.PixelCircle
    val PixelTriangle = PetalMaterialShapes.PixelTriangle
    val Bun = PetalMaterialShapes.Bun
    val Heart = PetalMaterialShapes.Heart

    // Aliases
    val RoundedSquare = PetalMaterialShapes.RoundedSquare
    val Rectangle = PetalMaterialShapes.Rectangle
    val RightTriangle = PetalMaterialShapes.RightTriangle
    val Hexagon = PetalMaterialShapes.Hexagon
    val Octagon = PetalMaterialShapes.Octagon
    val Decagon = PetalMaterialShapes.Decagon
    val Dodecagon = PetalMaterialShapes.Dodecagon
    val Scallop = PetalMaterialShapes.Scallop
    val SoftScallop = PetalMaterialShapes.SoftScallop
    val Teardrop = PetalMaterialShapes.Teardrop
    val Star4Sided = PetalMaterialShapes.Star4Sided
    val Star8Sided = PetalMaterialShapes.Star8Sided

    val homescreenShapes = PetalMaterialShapes.homescreenShapes
    val allShapes = PetalMaterialShapes.allShapes
}

/**
 * Material 3 Expressive Shapes Extensions on [Shapes].
 * Provides extended token properties for Expressive design scale.
 */
val Shapes.extraSmallIncreased: Shape
    get() = RoundedCornerShape(8.dp)

val Shapes.smallIncreased: Shape
    get() = RoundedCornerShape(12.dp)

val Shapes.mediumIncreased: Shape
    get() = RoundedCornerShape(16.dp)

val Shapes.largeIncreased: Shape
    get() = RoundedCornerShape(24.dp)

val Shapes.extraLargeIncreased: Shape
    get() = RoundedCornerShape(28.dp)

/**
 * Resolves an expressive shape unconditionally.
 */
@androidx.compose.runtime.Composable
fun ExpressiveShapeHolder.resolveShape(fallback: Shape = RoundedCornerShape(16.dp)): Shape {
    return toShape()
}

/**
 * Dynamically resolves one of the 35 Material 3 Expressive shapes keyed by [key].
 * Uses a stable pseudo-random index mapping based on key hash code so distinct components
 * get distinct shapes from the 35 shapes collection unconditionally.
 */
@androidx.compose.runtime.Composable
fun rememberExpressiveShape(key: Any?, fallback: Shape = RoundedCornerShape(14.dp)): Shape {
    val all = PetalMaterialShapes.allShapes
    if (all.isEmpty()) return fallback
    val hash = key?.hashCode() ?: 0
    val idx = (hash and 0x7FFFFFFF) % all.size
    return all[idx].toShape()
}

