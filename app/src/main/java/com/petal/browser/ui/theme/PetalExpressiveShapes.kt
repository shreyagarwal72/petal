package com.petal.browser.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

// ============================================================================
// Petal expressive shape engine.
//
// Mirrors the shape system of the Expressive Keyboard app:
//   * shapes are the OFFICIAL androidx.compose.material3.MaterialShapes polygons
//   * a unit path is built once per shape and scaled to the target size
//   * buttons squash and rotate on press (CookieButton behaviour)
//   * an "amount" setting decides how many surfaces get expressive shapes
// ============================================================================

/** How many surfaces use expressive shapes. Same three modes as the keyboard app. */
enum class ExpressiveShapeAmount { FULL, ACCENTS, NONE;
    companion object {
        fun fromId(id: String?): ExpressiveShapeAmount = entries.firstOrNull { it.name == id } ?: FULL
    }
}

/** Shapes that can be used for buttons. All of them come straight from MaterialShapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class ExpressiveButtonPreset(val id: String, val title: String) {
    COOKIE_12("COOKIE_12", "Cookie"),
    COOKIE_9("COOKIE_9", "Cookie 9"),
    SUNNY("SUNNY", "Sunny"),
    FLOWER("FLOWER", "Flower"),
    CLOVER_4("CLOVER_4", "Clover"),
    SOFT_BURST("SOFT_BURST", "Soft burst"),
    PUFFY("PUFFY", "Puffy"),
    PILL("PILL", "Pill"),
    CIRCLE("CIRCLE", "Circle");

    val polygon: RoundedPolygon
        get() = when (this) {
            COOKIE_12 -> androidx.compose.material3.MaterialShapes.Cookie12Sided
            COOKIE_9 -> androidx.compose.material3.MaterialShapes.Cookie9Sided
            SUNNY -> androidx.compose.material3.MaterialShapes.Sunny
            FLOWER -> androidx.compose.material3.MaterialShapes.Flower
            CLOVER_4 -> androidx.compose.material3.MaterialShapes.Clover4Leaf
            SOFT_BURST -> androidx.compose.material3.MaterialShapes.SoftBurst
            PUFFY -> androidx.compose.material3.MaterialShapes.Puffy
            PILL -> androidx.compose.material3.MaterialShapes.Pill
            CIRCLE -> androidx.compose.material3.MaterialShapes.Circle
        }

    companion object {
        fun fromId(id: String?): ExpressiveButtonPreset = entries.firstOrNull { it.id == id } ?: COOKIE_12

        /** Shapes cycled through for neighbouring buttons so a row never looks stamped. */
        val variety = listOf(COOKIE_12, SUNNY, FLOWER, COOKIE_9, CLOVER_4, SOFT_BURST)
    }
}

/** SharedPreferences keys for the shape system. */
object PetalShapePrefs {
    const val KEY_BUTTON_PRESET = "sp_button_shape_preset"
    const val KEY_SHAPE_AMOUNT = "sp_shape_amount"
}

/** Live, snapshot-backed settings. PetalTheme keeps these in sync with the preferences. */
object PetalShapeState {
    var preset by mutableStateOf(ExpressiveButtonPreset.COOKIE_12)
    var amount by mutableStateOf(ExpressiveShapeAmount.FULL)
    var enabled by mutableStateOf(true)

    /** The preset buttons should currently use, honouring "no shapes" and the master switch. */
    val effectivePreset: ExpressiveButtonPreset
        get() = if (!enabled || amount == ExpressiveShapeAmount.NONE) ExpressiveButtonPreset.CIRCLE else preset
}

// ----------------------------------------------------------------------------
// Unit path cache
// ----------------------------------------------------------------------------

private val unitPaths = ConcurrentHashMap<ExpressiveButtonPreset, android.graphics.Path>()
private val shapeMatrix = ThreadLocal.withInitial { android.graphics.Matrix() }

private fun unitPath(preset: ExpressiveButtonPreset): android.graphics.Path =
    unitPaths.getOrPut(preset) {
        android.graphics.Path().also { preset.polygon.normalized().toPath(it) }
    }

/** Writes [preset] into [path], scaled to a circle of [radius] centred on ([cx], [cy]). */
fun buildExpressivePath(
    preset: ExpressiveButtonPreset,
    cx: Float,
    cy: Float,
    radius: Float,
    path: Path = Path(),
): Path {
    path.reset()
    val m = shapeMatrix.get()!!
    m.setScale(radius * 2f, radius * 2f)
    m.postTranslate(cx - radius, cy - radius)
    unitPath(preset).transform(m, path.asAndroidPath())
    return path
}

// ----------------------------------------------------------------------------
// Shapes
// ----------------------------------------------------------------------------

/**
 * Safe convex shadow shape for expressive components.
 * Produces an [Outline.Rounded] capsule or circle which always takes the fast,
 * convex HWUI shadow tessellation path and never triggers [computeConcaveShadow].
 */
val ExpressiveShadowShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val minSide = min(size.width, size.height)
        if (minSide <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        val r = minSide / 2f
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
    }
}

/**
 * Returns a convex shadow shape if [this] shape is an expressive concave shape,
 * avoiding SkBaseShadowTessellator::computeConcaveShadow ANRs on Android HWUI.
 */
fun Shape.toSafeShadowShape(): Shape = when (this) {
    ExpressiveButtonShape, ExpressiveAccentShape -> ExpressiveShadowShape
    is ExpressiveShape -> ExpressiveShadowShape
    is RoundedCornerShape, RectangleShape, CircleShape, ExpressiveShadowShape -> this
    else -> ExpressiveShadowShape
}

/** A fixed expressive shape for clip / background / Surface. */
class ExpressiveShape(private val preset: ExpressiveButtonPreset) : Shape {
    val shadowShape: Shape get() = ExpressiveShadowShape

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        buildOutline(preset, size)
}

private fun buildOutline(preset: ExpressiveButtonPreset, size: Size): Outline {
    val minSide = min(size.width, size.height)
    if (minSide <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
    val ratio = size.width / size.height
    // Wide or tall containers (search pills, chips) keep a capsule; a cookie would be stretched.
    val capsule = preset == ExpressiveButtonPreset.PILL || preset == ExpressiveButtonPreset.CIRCLE ||
        ratio > 1.3f || ratio < 0.77f
    if (capsule) {
        val r = minSide / 2f
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
    }
    return Outline.Generic(buildExpressivePath(preset, size.width / 2f, size.height / 2f, minSide / 2f))
}

/**
 * Drop-in replacement for CircleShape across the app.
 *
 * It follows the user's chosen button shape (default: Cookie) and turns back into a normal circle
 * when expressive shapes are switched off. Wide containers automatically stay capsules.
 */
object ExpressiveButtonShape : Shape {
    val shadowShape: Shape get() = ExpressiveShadowShape

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        buildOutline(PetalShapeState.effectivePreset, size)
}

/** Shape for the main accent buttons (header, FAB-like): ignores "accents only" but honours "none". */
object ExpressiveAccentShape : Shape {
    val shadowShape: Shape get() = ExpressiveShadowShape

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        buildOutline(
            if (!PetalShapeState.enabled || PetalShapeState.amount == ExpressiveShapeAmount.NONE) ExpressiveButtonPreset.CIRCLE
            else PetalShapeState.preset,
            size,
        )
}

// ----------------------------------------------------------------------------
// Background with rotation (cached path, rotation read only while drawing)
// ----------------------------------------------------------------------------

fun Modifier.expressiveBackground(
    preset: ExpressiveButtonPreset,
    color: Color,
    rotation: () -> Float = { 0f },
): Modifier = drawWithCache {
    val path = buildExpressivePath(preset, size.width / 2f, size.height / 2f, min(size.width, size.height) / 2f)
    onDrawBehind {
        rotate(rotation(), center) { drawPath(path, color) }
    }
}

// ----------------------------------------------------------------------------
// Buttons
// ----------------------------------------------------------------------------

/**
 * The Petal version of the keyboard's CookieButton: an expressive shape that squashes and turns
 * on press, then springs back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveShapeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    preset: ExpressiveButtonPreset? = null,
    contentDescription: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "expressiveButtonScale",
    )
    val rot by animateFloatAsState(
        if (pressed) 25f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "expressiveButtonRotation",
    )
    val shapePreset = preset ?: PetalShapeState.effectivePreset
    // Tapping at the very edge of a wavy shape still counts: the touch box stays the full square.
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .expressiveBackground(shapePreset, containerColor) { rot }
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Icon inside an [ExpressiveShapeButton]. */
@Composable
fun ExpressiveIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    preset: ExpressiveButtonPreset? = null,
    onLongClick: (() -> Unit)? = null,
) {
    ExpressiveShapeButton(
        onClick = onClick,
        modifier = modifier,
        size = size,
        containerColor = containerColor,
        preset = preset,
        contentDescription = contentDescription,
        onLongClick = onLongClick,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}
