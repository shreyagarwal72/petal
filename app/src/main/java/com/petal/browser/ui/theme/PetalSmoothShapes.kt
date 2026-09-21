package com.petal.browser.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// SmoothRoundedShape
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A rounded rectangle with continuous-curvature ("squircle") corners.
 *
 * Plain rounded corners join the straight edge with a visible kink in curvature;
 * at large radii that reads as "a card from any app". Smoothing spreads the curve
 * along the edge, making large containers feel soft and deliberate — the same
 * technique used by Relay's design system.
 *
 * Corners are given individually so asymmetric shapes (e.g. a tucked hero card)
 * are first-class members of the family.
 */
@Immutable
class SmoothRoundedShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val smoothing: Float = 0.6f,
) : Shape {
    constructor(all: Dp, smoothing: Float = 0.6f) : this(all, all, all, all, smoothing)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(size.toRect())
        val maxRadius = minOf(size.width, size.height) / 2f
        fun rounding(corner: Dp) = with(density) {
            CornerRounding(corner.toPx().coerceAtMost(maxRadius), smoothing)
        }
        val (tl, tr) = if (layoutDirection == LayoutDirection.Ltr) topStart to topEnd else topEnd to topStart
        val (bl, br) = if (layoutDirection == LayoutDirection.Ltr) bottomStart to bottomEnd else bottomEnd to bottomStart
        // Vertex order of RoundedPolygon.rectangle: bottom-right, bottom-left, top-left, top-right.
        val polygon = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            perVertexRounding = listOf(rounding(br), rounding(bl), rounding(tl), rounding(tr)),
            centerX = size.width / 2f,
            centerY = size.height / 2f,
        )
        return Outline.Generic(polygon.toComposePath())
    }

    override fun equals(other: Any?): Boolean =
        other is SmoothRoundedShape &&
            other.topStart == topStart && other.topEnd == topEnd &&
            other.bottomEnd == bottomEnd && other.bottomStart == bottomStart &&
            other.smoothing == smoothing

    override fun hashCode(): Int =
        listOf(topStart, topEnd, bottomEnd, bottomStart, smoothing).hashCode()
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)

internal fun RoundedPolygon.toComposePath(path: Path = Path()): Path {
    path.rewind()
    val cubics = cubics
    if (cubics.isEmpty()) return path
    path.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
    for (cubic in cubics) {
        path.cubicTo(
            cubic.control0X, cubic.control0Y,
            cubic.control1X, cubic.control1Y,
            cubic.anchor1X, cubic.anchor1Y,
        )
    }
    path.close()
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
// MorphShape
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A shape partway between two Material Expressive shapes.
 *
 * Used where a component changes state by morphing its silhouette rather than
 * swapping one element for another (e.g. a progress ring → success badge,
 * or a chip collapsing into a pill).
 */
@Immutable
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        val matrix = Matrix().apply {
            // Material shapes live in a unit square; scale to the component and
            // spin around its centre.
            translate(size.width / 2f, size.height / 2f)
            rotateZ(rotation)
            translate(-size.width / 2f, -size.height / 2f)
            scale(size.width, size.height)
        }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PetalBrowserShapes  —  browser-adapted design tokens
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Petal's browser shape family, ported from Relay's design token system and
 * adapted for browser UI conventions.
 *
 * | Token            | Used for                                    | Character                    |
 * |------------------|---------------------------------------------|------------------------------|
 * | Card             | Main browser cards, download cards          | 32dp smooth                  |
 * | CardHero         | Featured / active tab card                  | 44dp smooth, tucked corner   |
 * | CardPressed      | Card while pressed / selected               | 44dp smooth (softens)        |
 * | Sheet            | Bottom sheets                               | 36dp top corners only        |
 * | Dialog           | Dialogs, overlays                           | 36dp smooth                  |
 * | Action           | Large action tiles, FAB, speed-dial         | 32dp smooth                  |
 * | ActionCompact    | Compact action buttons                      | 24dp smooth                  |
 * | Pill             | Chips, address bar pills, badges            | fully rounded                |
 * | Small            | Activity rows, list items, rows             | 20dp smooth                  |
 * | ExtraSmall       | Inline tags, micro badges                   | 10dp                         |
 * | OmniboxField     | Address / search input field                | 28dp smooth                  |
 * | TabItem          | Tab strip items                             | 20dp smooth top only         |
 * | Snackbar         | Toast / snackbar surfaces                   | 16dp smooth                  |
 * | MenuSurface      | Popup menus, context menus                  | 24dp smooth                  |
 * | QuickActions     | Quick-action row pills                      | 28dp smooth                  |
 *
 * The tucked corner on [CardHero] points toward content below, so the eye
 * travels naturally from the hero tab into its action — identical to Relay's
 * Hero card design intent.
 */
object PetalBrowserShapes {

    // ── Cards ─────────────────────────────────────────────────────────────────

    /** Standard browser card (downloads, bookmarks, history items). */
    val Card: Shape = SmoothRoundedShape(32.dp)

    /** The most relevant / active tab or hero content card.
     *  Asymmetric: bottom-start corner is tucked so the eye flows downward. */
    val CardHero: Shape = SmoothRoundedShape(
        topStart = 44.dp,
        topEnd = 44.dp,
        bottomEnd = 44.dp,
        bottomStart = 18.dp,
    )

    /** Card shape while pressed — softens uniformly to signal "giving". */
    val CardPressed: Shape = SmoothRoundedShape(44.dp)

    // ── Sheets & Dialogs ──────────────────────────────────────────────────────

    /** Bottom sheets: top corners only, flat bottom. */
    val Sheet: Shape = SmoothRoundedShape(
        topStart = 36.dp,
        topEnd = 36.dp,
        bottomEnd = 0.dp,
        bottomStart = 0.dp,
    )

    /** Dialogs and modal overlays. */
    val Dialog: Shape = SmoothRoundedShape(36.dp)

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Large action tiles, FAB, speed-dial arcs. */
    val Action: Shape = SmoothRoundedShape(32.dp)

    /** Compact action buttons, small icon buttons. */
    val ActionCompact: Shape = SmoothRoundedShape(24.dp)

    // ── Pills & Chips ─────────────────────────────────────────────────────────

    /** Fully rounded pill — address bar chips, status pills, search chips. */
    val Pill: Shape = RoundedCornerShape(percent = 50)

    // ── List & Row Surfaces ───────────────────────────────────────────────────

    /** Activity rows, list surfaces, grouped item containers. */
    val Small: Shape = SmoothRoundedShape(20.dp)

    /** Inline tags, micro badges, tiny labels. */
    val ExtraSmall: Shape = RoundedCornerShape(10.dp)

    // ── Browser-Specific Surfaces ─────────────────────────────────────────────

    /** Omnibox / address bar input field. */
    val OmniboxField: Shape = SmoothRoundedShape(28.dp)

    /** Tab strip items: top corners rounded, bottom flat so they sit on the bar. */
    val TabItem: Shape = SmoothRoundedShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomEnd = 0.dp,
        bottomStart = 0.dp,
    )

    /** Toast / Snackbar surface. */
    val Snackbar: Shape = SmoothRoundedShape(16.dp)

    /** Popup menus, overflow menus, context menus. */
    val MenuSurface: Shape = SmoothRoundedShape(24.dp)

    /** Quick-action row pills (tab swipe pill, speed dial). */
    val QuickActions: Shape = SmoothRoundedShape(28.dp)

    // ── Corner Radii for Animatable Variants ──────────────────────────────────

    /**
     * Raw corner radii for components that animate between resting and pressed states
     * using [SmoothRoundedShape] directly (avoids boxing overhead in animation loops).
     */
    object Radius {
        val Card = 32.dp
        val CardPressed = 44.dp
        val CardHero = 44.dp
        val CardHeroTucked = 18.dp
        val Action = 32.dp
        val OmniboxField = 28.dp
        val MenuSurface = 24.dp
    }

    // ── Organic Icon Shapes (M3 Expressive catalog) ───────────────────────────

    /**
     * Organic per-category avatar shapes from PetalMaterialShapes, mapped to
     * browser content kinds. Distinct silhouettes at 24dp.
     *
     * Mirrors Relay's [avatarFor] pattern for device kinds.
     */
    fun iconShapeFor(category: PetalIconCategory) = when (category) {
        PetalIconCategory.Web       -> MaterialShapes.Cookie6Sided
        PetalIconCategory.Download  -> MaterialShapes.Square
        PetalIconCategory.History   -> MaterialShapes.Clover4Leaf
        PetalIconCategory.Bookmark  -> MaterialShapes.Sunny
        PetalIconCategory.Settings  -> MaterialShapes.Pentagon
        PetalIconCategory.Extension -> MaterialShapes.Gem
        PetalIconCategory.Media     -> MaterialShapes.Flower
        PetalIconCategory.Privacy   -> MaterialShapes.Cookie9Sided
    }
}

/** Content category for [PetalBrowserShapes.iconShapeFor]. */
enum class PetalIconCategory {
    Web, Download, History, Bookmark, Settings, Extension, Media, Privacy
}

// ─────────────────────────────────────────────────────────────────────────────
// Modifier extensions  —  press feedback + entrance animation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Press feedback for large tappable browser surfaces.
 *
 * A quick scale-down on press followed by a springy release — the same physical
 * press language used by Relay's [pressScale]. Pair with a [SmoothRoundedShape]
 * animation in the caller for the full "card gives" effect.
 */
@Composable
fun Modifier.petalPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "petalPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Entrance animation: content rises into place on a spring when it first appears.
 * Items stagger by [index] so a list assembles itself rather than appearing all at once.
 *
 * [play] belongs to the screen, not the item — a list that recycles a row while
 * scrolling does not re-animate it.
 */
@Composable
fun Modifier.petalEntrance(index: Int = 0, play: Boolean = true): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * PETAL_STAGGER_MS)
        progress.animateTo(1f)
    }
    if (!play) return this
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 20.dp.toPx()
        scaleX = 0.98f + 0.02f * progress.value
        scaleY = scaleX
    }
}

private const val PETAL_STAGGER_MS = 45L
