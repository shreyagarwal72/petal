package com.petal.browser.ui.theme.layout

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.ExpressiveShapeHolder
import com.petal.browser.ui.theme.PetalMaterialShapes

/**
 * Containment's own shape style. [FOLLOW_APP] keeps containment in sync with the app-wide
 * "Shapes Type"; every other entry overrides it for containment only (rows, cards, chips, hero surfaces).
 */
enum class PetalContainmentShapeChoice(val key: String, val title: String, val subtitle: String, val type: PetalShapeType?) {
    FOLLOW_APP("FOLLOW", "Follow Shapes Type", "Containment uses the app-wide shape style", null),
    ROUNDED("ROUNDED", PetalShapeType.ROUNDED.title, PetalShapeType.ROUNDED.subtitle, PetalShapeType.ROUNDED),
    CUT("CUT", PetalShapeType.CUT.title, PetalShapeType.CUT.subtitle, PetalShapeType.CUT),
    SQUIRCLE("SQUIRCLE", PetalShapeType.SQUIRCLE.title, PetalShapeType.SQUIRCLE.subtitle, PetalShapeType.SQUIRCLE),
    SMOOTH("SMOOTH", PetalShapeType.SMOOTH.title, PetalShapeType.SMOOTH.subtitle, PetalShapeType.SMOOTH),
    WAVY("WAVY", PetalShapeType.WAVY.title, PetalShapeType.WAVY.subtitle, PetalShapeType.WAVY),
    SCOOP("SCOOP", PetalShapeType.SCOOP.title, PetalShapeType.SCOOP.subtitle, PetalShapeType.SCOOP),
    NOTCH("NOTCH", PetalShapeType.NOTCH.title, PetalShapeType.NOTCH.subtitle, PetalShapeType.NOTCH);

    companion object {
        fun fromKey(key: String?): PetalContainmentShapeChoice =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FOLLOW_APP
    }
}

/**
 * Shape of the icon container ("badge") behind every containment icon. The Material 3 Expressive
 * polygons (cookie, sunny, flower, clover...) are the same family the Expressive Keyboard uses for its
 * icon tiles. [FOLLOW] draws the classic 14dp tile in the containment shape style, so existing screens
 * look exactly as before until the user picks something else.
 */
enum class PetalBadgeShape(
    val key: String,
    val title: String,
    val subtitle: String,
    private val holder: (() -> ExpressiveShapeHolder)?,
) {
    MIXED("MIXED", "Mixed (random)", "A different Expressive shape for each icon, stable across launches", null),
    FOLLOW("FOLLOW", "Follow containment", "Rounded tile in the containment shape style", null),
    CIRCLE("CIRCLE", "Circle", "Classic round icon container", { PetalMaterialShapes.Circle }),
    ROUNDED_SQUARE("ROUNDED_SQUARE", "Soft square", "Expressive rounded square", { PetalMaterialShapes.RoundedSquare }),
    COOKIE_12("COOKIE_12", "Cookie", "Twelve gentle waves", { PetalMaterialShapes.Cookie12Sided }),
    COOKIE_9("COOKIE_9", "Cookie 9", "Nine rounded waves", { PetalMaterialShapes.Cookie9Sided }),
    SUNNY("SUNNY", "Sunny", "Eight-point sunburst", { PetalMaterialShapes.Sunny }),
    FLOWER("FLOWER", "Flower", "Six soft petals", { PetalMaterialShapes.Flower }),
    CLOVER_4("CLOVER_4", "Clover", "Four-leaf clover", { PetalMaterialShapes.Clover4Leaf }),
    SOFT_BURST("SOFT_BURST", "Soft burst", "Rounded starburst", { PetalMaterialShapes.SoftBurst }),
    PUFFY("PUFFY", "Puffy", "Pillowy blob", { PetalMaterialShapes.Puffy }),
    SCALLOP("SCALLOP", "Scallop", "Scalloped edge", { PetalMaterialShapes.Scallop });

    /** True when this badge is a Material 3 Expressive polygon rather than a corner-based tile. */
    val isPolygon: Boolean get() = holder != null

    /**
     * [seed] only matters for [MIXED]: the same seed always yields the same shape, so an icon keeps its
     * shape across recompositions and launches while neighbouring icons differ.
     */
    fun shape(type: PetalShapeType, cornerMultiplier: Float, seed: Int = 0): Shape = when {
        this == MIXED -> mixedPool[Math.floorMod(seed, mixedPool.size)].invoke().toShape()
        holder != null -> holder.invoke().toShape()
        else -> type.cornerShape(14.dp, multiplier = cornerMultiplier)
    }

    companion object {
        /** Expressive shapes only (no plain circle or square), as in the Expressive Keyboard. */
        private val mixedPool: List<() -> ExpressiveShapeHolder> = listOf(
            { PetalMaterialShapes.Cookie12Sided },
            { PetalMaterialShapes.Sunny },
            { PetalMaterialShapes.Flower },
            { PetalMaterialShapes.Clover4Leaf },
            { PetalMaterialShapes.Cookie9Sided },
            { PetalMaterialShapes.SoftBurst },
            { PetalMaterialShapes.Puffy },
            { PetalMaterialShapes.Scallop },
        )

        fun fromKey(key: String?): PetalBadgeShape =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: MIXED
    }
}
