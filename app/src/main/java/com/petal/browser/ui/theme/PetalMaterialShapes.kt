package com.petal.browser.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import java.util.Locale

/**
 * CompositionLocal providing global enablement of the 35 Material 3 Expressive shapes.
 * When false, components fall back to gentle default rounded corners.
 */
val LocalExpressiveShapesEnabled = compositionLocalOf { true }

/**
 * High-level shape picker categories corresponding to curated tabs in the UI.
 */
enum class PetalShapeGroup(val label: String) {
    BASIC("Basic"),
    COOKIE("Cookie"),
    DYNAMIC("Dynamic"),
    ORGANIC("Organic"),
    ENERGETIC("Energetic"),
}

/**
 * PetalMaterialShapes
 *
 * Official catalog of all 35 Material 3 Expressive shapes backed directly by
 * [androidx.compose.material3.MaterialShapes].
 *
 * Provides [toShape] converting any [RoundedPolygon] or [ExpressiveShapeHolder] cleanly to a
 * Compose [Shape] with uniform scaling and centering inside arbitrary bounds.
 */
data class ExpressiveShapeHolder(
    val name: String,
    val category: String,
    val polygon: RoundedPolygon
) {
    val cachedShape: Shape by lazy { polygon.toShape() }
    fun toShape(): Shape = cachedShape
    val shadowShape: Shape get() = ExpressiveShadowShape
    fun toSafeShadowShape(): Shape = cachedShape.toSafeShadowShape()
}

/**
 * Converts an official [RoundedPolygon] into a Jetpack Compose [Shape].
 *
 * Scales uniformly by `minOf(width / boundsWidth, height / boundsHeight)` and centers
 * inside the target layout bounds so non-square containers never distort or clip.
 */
fun RoundedPolygon.toShape(): Shape {
    val normalized = this.normalized()
    val unit = android.graphics.Path().also { normalized.toPath(it) }
    return GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@GenericShape
        val bounds = android.graphics.RectF()
        unit.computeBounds(bounds, true)
        val bw = bounds.width().coerceAtLeast(1e-4f)
        val bh = bounds.height().coerceAtLeast(1e-4f)
        val scale = minOf(w / bw, h / bh)
        val matrix = android.graphics.Matrix()
        matrix.postTranslate(-bounds.centerX(), -bounds.centerY())
        matrix.postScale(scale, scale)
        matrix.postTranslate(w / 2f, h / 2f)
        val out = android.graphics.Path()
        unit.transform(matrix, out)
        this.addPath(out.asComposePath())
    }
}

object PetalMaterialShapes {
    const val PREF_EXPRESSIVE_SHAPES_ENABLED = "sp_expressive_shapes_35_enabled"

    // --- 1. Basic & Geometric ---
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

    // --- 2. Dynamic & Architectural ---
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

    // --- 3. Sunny & Cookie Series ---
    val Sunny: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Sunny", "Celestial", androidx.compose.material3.MaterialShapes.Sunny)
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

    val Cookie12Sided: ExpressiveShapeHolder by lazy {
        ExpressiveShapeHolder("Cookie 12-Sided", "Cookies", androidx.compose.material3.MaterialShapes.Cookie12Sided)
    }

    // --- 4. Organic, Clovers & Playful ---
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

    // --- 5. High Energy, Explosive, Pixel & Treats ---
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

    /** Curated subset of official Material 3 Expressive shapes for homescreen icon tiles. */
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
            Cookie12Sided,
            Clover4Leaf,
            Flower,
            Burst,
            SoftBurst
        )
    }

    /** Complete official catalog of all 35 Material 3 Expressive shapes. */
    val allShapes: List<ExpressiveShapeHolder> by lazy {
        listOf(
            // Basic (9)
            Circle, Square, Slanted, Arch, SemiCircle, Oval, Pill, Triangle, Diamond,
            // Cookie & Sunny (7)
            Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie12Sided, Sunny, VerySunny,
            // Dynamic (6)
            Arrow, Fan, ClamShell, Pentagon, Gem, PuffyDiamond,
            // Organic (7)
            Clover4Leaf, Clover8Leaf, Flower, Puffy, Heart, Bun, Ghostish,
            // Energetic & Pixel (6)
            Burst, SoftBurst, Boom, SoftBoom, PixelCircle, PixelTriangle
        )
    }

    private fun normalizeKey(raw: String): String {
        return raw.trim()
            .lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
    }

    private val lookupMap: Map<String, ExpressiveShapeHolder> by lazy {
        val map = LinkedHashMap<String, ExpressiveShapeHolder>(64)
        allShapes.forEach { holder ->
            map[normalizeKey(holder.name)] = holder
        }
        // Property-style names & legacy/retired shape names mapped cleanly without crashing:
        map["sun"] = Sunny
        map["cookie10sided"] = Cookie9Sided
        map["roundedsquare"] = Square
        map["rectangle"] = Square
        map["righttriangle"] = Triangle
        map["hexagon"] = Gem
        map["octagon"] = PixelCircle
        map["decagon"] = Sunny
        map["dodecagon"] = Cookie12Sided
        map["scallop"] = Sunny
        map["softscallop"] = VerySunny
        map["teardrop"] = Bun
        map["star4sided"] = Cookie4Sided
        map["star8sided"] = Sunny
        map
    }

    /**
     * Resolves a saved or requested shape name case-insensitively, supporting both current names
     * and legacy aliases (such as "Sun" -> [Sunny] or "Cookie10Sided" -> [Cookie9Sided]).
     */
    fun resolveByName(name: String?): ExpressiveShapeHolder? {
        if (name.isNullOrBlank()) return null
        return lookupMap[normalizeKey(name)]
    }

    /** Returns the picker filter tab for [holder]. */
    fun groupFor(holder: ExpressiveShapeHolder): PetalShapeGroup {
        return when (holder.name) {
            "Circle", "Square", "Slanted", "Arch", "Semi Circle", "Oval", "Pill", "Triangle", "Diamond" ->
                PetalShapeGroup.BASIC
            "Cookie 4-Sided", "Cookie 6-Sided", "Cookie 7-Sided", "Cookie 9-Sided", "Cookie 12-Sided", "Sunny", "Very Sunny" ->
                PetalShapeGroup.COOKIE
            "Arrow", "Fan", "Clam Shell", "Pentagon", "Gem", "Puffy Diamond" ->
                PetalShapeGroup.DYNAMIC
            "Clover 4-Leaf", "Clover 8-Leaf", "Flower", "Puffy", "Heart", "Bun", "Ghostish" ->
                PetalShapeGroup.ORGANIC
            else -> PetalShapeGroup.ENERGETIC
        }
    }

    /** Returns all shapes belonging to [group] in catalog order. */
    fun shapesInGroup(group: PetalShapeGroup): List<ExpressiveShapeHolder> {
        return allShapes.filter { groupFor(it) == group }
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
    val VerySunny = PetalMaterialShapes.VerySunny
    val Cookie4Sided = PetalMaterialShapes.Cookie4Sided
    val Cookie6Sided = PetalMaterialShapes.Cookie6Sided
    val Cookie7Sided = PetalMaterialShapes.Cookie7Sided
    val Cookie9Sided = PetalMaterialShapes.Cookie9Sided
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
