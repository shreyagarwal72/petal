package com.petal.browser.ui.containment

import com.petal.browser.ui.theme.ExpressiveButtonShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxSize
import com.petal.browser.ui.glass.liveGlassWallpaper
import com.petal.browser.ui.glass.recordGlassWallpaper
import com.petal.browser.ui.glass.captureGlassWallpaper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.petal.browser.ui.theme.layout.cornerShape
import com.petal.browser.ui.theme.layout.pillShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.preference.PreferenceManager
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ChipColors
import com.petal.browser.haptics.PetalHapticEngine
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.petalGlassPanel
import com.petal.browser.ui.glass.glassPressOverlay
import com.petal.browser.ui.glass.glassPressScale

enum class PetalGroupPosition { SINGLE, TOP, MIDDLE, BOTTOM }

val LocalPetalSectionHighlighted = compositionLocalOf { false }

/**
 * Checks whether Liquid Glass UI is enabled system-wide.
 */
@Composable
fun isLiquidGlassEnabled(componentKey: String = "sp_liquid_glass_containments"): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var enabled by remember(sp, componentKey) {
        val master = sp.getBoolean("sp_liquid_glass_enabled", false)
        val comp = sp.getBoolean(componentKey, true)
        mutableStateOf(master && comp)
    }
    DisposableEffect(sp, componentKey) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_liquid_glass_unlocked" || key == "sp_liquid_glass_enabled" || key == componentKey) {
                val master = sp.getBoolean("sp_liquid_glass_enabled", false)
                val comp = sp.getBoolean(componentKey, true)
                enabled = master && comp
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

/**
 * Specular highlight, frosted refraction gradient, and chromatic reflection border for Liquid Glass containments.
 */
@Composable
fun Modifier.liquidGlassChrome(
    shape: Shape,
    enabled: Boolean = true,
    sheenIntensity: Float = 0.60f,
    tintColor: Color = Color.Unspecified,
    // Explicit sibling backdrop for headers; otherwise use the theme's shared wallpaper.
    // The caller keeps its container transparent when either source is available.
    backdrop: PetalBackdrop? = null,
    isDark: Boolean = false,
    alphaOverride: Float? = null,
    sheenOverride: Float? = null,
    refractionOverride: Float? = null,
    refractionWidthOverride: Float? = null,
    /** Kyant's press effects (swell + a light under the finger) for tappable glass. */
    pressFx: Boolean = false
): Modifier {
    val fx = com.petal.browser.ui.glass.rememberGlassFx()
    val masterOn = PreferenceManager
        .getDefaultSharedPreferences(androidx.compose.ui.platform.LocalContext.current)
        .getBoolean("sp_liquid_glass_enabled", false)
    val press = if (pressFx && enabled && masterOn && fx.any) {
        com.petal.browser.ui.glass.rememberGlassPress(fx)
    } else null
    val scaled = if (press != null) this.glassPressScale(press, 8f) else this
    val glass = scaled.liquidGlassChromeBase(
        shape, enabled, sheenIntensity, tintColor, backdrop, isDark,
        alphaOverride, sheenOverride, refractionOverride, refractionWidthOverride
    )
    return if (press != null) glass.glassPressOverlay(press, shape) else glass
}

@Composable
private fun Modifier.liquidGlassChromeBase(
    shape: Shape,
    enabled: Boolean,
    sheenIntensity: Float,
    tintColor: Color,
    backdrop: PetalBackdrop?,
    isDark: Boolean,
    alphaOverride: Float?,
    sheenOverride: Float?,
    refractionOverride: Float?,
    refractionWidthOverride: Float?
): Modifier {
    // Read first, on every path: any liquid-glass pref change (master toggle, sliders)
    // must recompose headers using the backdrop path too, not just wallpaper panels.
    LocalPetalGlassRevision.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val tuning = com.petal.browser.ui.glass.rememberRefractionTuning(refractionOverride, refractionWidthOverride)
    val masterGlass = sp.getBoolean("sp_liquid_glass_enabled", false)
    if (!enabled || !masterGlass) return this
    if (com.petal.browser.motion.MotionGate.isAnimating.value || com.petal.browser.motion.MotionGate.isLowEndDevice(context)) {
        val dark = isDark || (MaterialTheme.colorScheme.background.luminance() < .5f)
        val tint = if (tintColor != Color.Unspecified) tintColor else MaterialTheme.colorScheme.surfaceContainerHigh
        val fillAlpha = if (dark) 0.50f else 0.65f
        val effectiveSheen = (sheenOverride ?: sheenIntensity).coerceIn(0f, 1f)
        return this.background(tint.copy(alpha = fillAlpha), shape)
            .liquidGlassSheen(shape, effectiveSheen, tintColor)
    }
    val backdropOpacity = (alphaOverride ?: sp.getFloat("sp_liquid_glass_alpha", .70f)).coerceIn(.10f, .95f)
    if (backdrop != null && petalRealGlassSupported) {
        val dark = isDark || (MaterialTheme.colorScheme.background.luminance() < .5f)
        val effectiveSheen = sheenOverride ?: sheenIntensity
        return this.petalRealGlass(backdrop, shape, dark, tuning, backdropOpacity)
            .liquidGlassSheen(shape, effectiveSheen, tintColor)
    }
    if (backdrop == null && LocalPetalGlassWallpaper.current?.ready == true) {
        val dark = MaterialTheme.colorScheme.background.luminance() < .5f
        val opacity = backdropOpacity
        val tint = if (tintColor != Color.Unspecified) tintColor else MaterialTheme.colorScheme.surfaceContainerHigh
        val sheen = (sheenOverride ?: sp.getFloat("sp_liquid_glass_sheen", .60f)).coerceIn(0f, 1f)
        // Authentic physical liquid glass: alpha directly modulates translucency vs opacity.
        // At 0.10, glass is ultra-clear; at 0.95, it is milky dense frosted glass.
        val baseAlpha = if (dark) 0.14f + (opacity * 0.40f) else 0.18f + (opacity * 0.50f)
        return this.petalGlassPanel(
            shape = shape,
            tint = tint.copy(alpha = baseAlpha.coerceIn(0f, 0.96f)),
            edge = (sheen / 0.60f).coerceIn(0f, 2.5f),
            refractionOverride = tuning.strength,
            refractionWidthOverride = tuning.width
        )
    }
    val effectiveSheen = sheenOverride ?: sheenIntensity
    return this.petalRealGlass(backdrop, shape, isDark, tuning, backdropOpacity)
        .liquidGlassSheen(shape, effectiveSheen, tintColor)
}

/** Specular highlight + rim stroke only (the original Petal "glass" look). */
private fun Modifier.liquidGlassSheen(
    shape: Shape,
    sheenIntensity: Float,
    tintColor: Color
): Modifier = this.drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    }

    // 1. Soft top specular reflection sheen + subtle caustic gradient
    clipPath(path) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = (0.16f * sheenIntensity).coerceIn(0f, 1f)),
                0.35f to Color.White.copy(alpha = (0.04f * sheenIntensity).coerceIn(0f, 1f)),
                0.70f to Color.Transparent,
                1f to (if (tintColor != Color.Unspecified) tintColor.copy(alpha = 0.05f * sheenIntensity) else Color.White.copy(alpha = 0.02f * sheenIntensity)),
                startY = 0f,
                endY = size.height,
            ),
        )
    }

    // 2. High-precision dual-angle chromatic rim reflection stroke (light incident from top-left)
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = (0.48f * sheenIntensity).coerceIn(0f, 1f)),
            0.30f to (if (tintColor != Color.Unspecified) tintColor.copy(alpha = (0.28f * sheenIntensity).coerceIn(0f, 1f)) else Color.White.copy(alpha = (0.15f * sheenIntensity).coerceIn(0f, 1f))),
            0.70f to Color.White.copy(alpha = (0.06f * sheenIntensity).coerceIn(0f, 1f)),
            1f to Color.White.copy(alpha = (0.24f * sheenIntensity).coerceIn(0f, 1f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        style = Stroke(width = 1.15.dp.toPx()),
    )
}

@Composable
fun petalGroupSurfaceColor(): Color {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    LocalPetalGlassRevision.current
    if (isGlass && LocalPetalGlassWallpaper.current?.ready == true) {
        return if (LocalPetalSectionHighlighted.current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else Color.Transparent
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassAlpha = if (isGlass) sp.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.10f, 0.95f) else 1f
    val baseGlassColor = if (dark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = (0.10f + glassAlpha * 0.45f).coerceIn(0f, 0.95f))
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = (0.45f + glassAlpha * 0.45f).coerceIn(0f, 0.95f))
    }

    val target = when {
        LocalPetalSectionHighlighted.current -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isGlass -> baseGlassColor
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val color by animateColorAsState(target, label = "petalGroupSurface")
    return color
}

/**
 * Same corners as [petalGroupShape], but the four radii animate when a row's position in its group changes
 * (rows added/removed, a row becoming first/last). Nothing animates on first composition.
 * Radii are clamped at 0 so a spring overshoot can never produce a negative corner.
 */
@Composable
fun rememberPetalGroupShape(position: PetalGroupPosition): androidx.compose.foundation.shape.CornerBasedShape {
    val target = petalGroupShape(position)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spec = androidx.compose.material3.MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val probe = androidx.compose.ui.geometry.Size(1000f, 1000f)
    fun corner(c: androidx.compose.foundation.shape.CornerSize) = with(density) { c.toPx(probe, density).toDp() }
    val topStart by androidx.compose.animation.core.animateDpAsState(corner(target.topStart), spec, label = "groupTopStart")
    val topEnd by androidx.compose.animation.core.animateDpAsState(corner(target.topEnd), spec, label = "groupTopEnd")
    val bottomEnd by androidx.compose.animation.core.animateDpAsState(corner(target.bottomEnd), spec, label = "groupBottomEnd")
    val bottomStart by androidx.compose.animation.core.animateDpAsState(corner(target.bottomStart), spec, label = "groupBottomStart")
    // Radii already include the live corner multiplier (see petalGroupShape), so draw them 1:1.
    return com.petal.browser.ui.theme.layout.PetalLayoutState.settings.containmentShapeType.cornerShape(
        topStart = topStart.coerceAtLeast(0.dp),
        topEnd = topEnd.coerceAtLeast(0.dp),
        bottomEnd = bottomEnd.coerceAtLeast(0.dp),
        bottomStart = bottomStart.coerceAtLeast(0.dp),
        multiplier = 1f,
    )
}

/**
 * Corner radii for a row at [position] in a group, drawn in the user's chosen shape style and scaled by
 * the live corner size. Reads snapshot state, so every caller recomposes when the style/size changes.
 */
fun petalGroupShape(position: PetalGroupPosition): androidx.compose.foundation.shape.CornerBasedShape {
    val type = com.petal.browser.ui.theme.layout.PetalLayoutState.settings.containmentShapeType
    val m = com.petal.browser.ui.theme.layout.PetalLayoutState.containmentCorners
    return when (position) {
        PetalGroupPosition.SINGLE -> type.cornerShape(28.dp, multiplier = m)
        PetalGroupPosition.TOP -> type.cornerShape(28.dp, 28.dp, 6.dp, 6.dp, m)
        PetalGroupPosition.MIDDLE -> type.cornerShape(6.dp, multiplier = m)
        PetalGroupPosition.BOTTOM -> type.cornerShape(6.dp, 6.dp, 28.dp, 28.dp, m)
    }
}

/**
 * Containment's own uniform-radius shape: the containment shape style (own or inherited from the app-wide
 * Shapes Type) scaled by the containment corner size. Reads snapshot state, so callers recompose on change.
 */
fun petalContainmentShape(radius: Dp): androidx.compose.foundation.shape.CornerBasedShape =
    com.petal.browser.ui.theme.layout.PetalLayoutState.settings.containmentShapeType.cornerShape(
        radius, multiplier = com.petal.browser.ui.theme.layout.PetalLayoutState.containmentCorners,
    )

/** Shape for chips and other small containment controls (14dp base radius). */
fun petalChipShape(): Shape = petalContainmentShape(14.dp)

/**
 * Shape of every containment icon container. Picks a Material 3 Expressive polygon (cookie, sunny, flower,
 * clover...) when the user chose one in Appearance > Containment shapes, otherwise the classic 14dp tile in
 * the containment shape style.
 */
@androidx.compose.runtime.Composable
fun petalBadgeShape(key: Any? = null): Shape {
    val state = com.petal.browser.ui.theme.layout.PetalLayoutState
    // Stable per icon: seeded by the icon's name when known (same icon, same shape on every launch);
    // otherwise a random seed remembered for as long as this badge stays composed, so it never flickers.
    val fallback = androidx.compose.runtime.remember { kotlin.random.Random.nextInt() }
    val seed = key?.hashCode() ?: fallback
    return state.settings.badgeShape.shape(state.settings.containmentShapeType, state.containmentCorners, seed)
}

fun petalGroupPositionFor(index: Int, count: Int): PetalGroupPosition = when {
    count <= 1 -> PetalGroupPosition.SINGLE
    index == 0 -> PetalGroupPosition.TOP
    index == count - 1 -> PetalGroupPosition.BOTTOM
    else -> PetalGroupPosition.MIDDLE
}

@Composable
fun PetalGroup(rowCount: Int, modifier: Modifier = Modifier, content: @Composable (Int, PetalGroupPosition) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(rowCount) { index -> content(index, petalGroupPositionFor(index, rowCount)) }
    }
}

@Composable
fun rememberPetalGroupPressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        com.petal.browser.ui.components.PetalSpring.segment(),
        label = "petalGroupPressScale",
    )
    return scale
}

enum class PetalBadgeVariant {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SURFACE_TONAL,
    OUTLINE,
    ERROR
}

@Composable
fun PetalBadgeVariant.colors(): Pair<Color, Color> = when (this) {
    PetalBadgeVariant.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    PetalBadgeVariant.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    PetalBadgeVariant.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    PetalBadgeVariant.SURFACE_TONAL -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    PetalBadgeVariant.OUTLINE -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.primary
    PetalBadgeVariant.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

@Composable
fun PetalGroupIconBadge(
    icon: ImageVector,
    variant: PetalBadgeVariant,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = petalBadgeShape(icon.name),
) {
    val (container, tint) = variant.colors()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PetalGroupIconBadge(
    painter: Painter,
    variant: PetalBadgeVariant,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = petalBadgeShape(),
) {
    val (container, tint) = variant.colors()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PetalGroupIconBadge(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(modifier.size(size).clip(petalBadgeShape(icon.name)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PetalGroupIconBadge(
    painter: Painter,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(modifier.size(size).clip(petalBadgeShape()).background(container), contentAlignment = Alignment.Center) {
        Icon(painter, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PetalGroupIconBadge(
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
fun PetalGroupRow(
    icon: ImageVector,
    title: String,
    position: PetalGroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    danger: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    val activeSurfaceColor = petalGroupSurfaceColor()
    val disabledContainerColor = if (isGlass) {
        activeSurfaceColor.copy(alpha = (activeSurfaceColor.alpha * 0.7f).coerceAtLeast(0.12f))
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = activeSurfaceColor,
            disabledContainerColor = disabledContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PetalGroupIconBadge(icon, iconContainer, iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (trailing != null) trailing() else Icon(Icons.Rounded.ChevronRight, null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PetalGroupToggleRow(
    icon: ImageVector, title: String, subtitle: String? = null, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit, position: PetalGroupPosition = PetalGroupPosition.SINGLE, modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    PetalGroupRow(icon, title, position, { if (enabled) onCheckedChange(!checked) }, modifier.alpha(if (enabled) 1f else 0.38f), subtitle, iconContainer, iconTint, enabled = enabled,
        trailing = { com.petal.browser.ui.components.IconSwitch(checked = checked, icon = Icons.Rounded.Check, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled) })
}

@Composable
fun PetalGroupControlRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: PetalGroupPosition,
    leadingIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: PetalBadgeVariant = PetalBadgeVariant.PRIMARY,
) {
    val (containerColor, contentColor) = variant.colors()
    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    val activeSurfaceColor = petalGroupSurfaceColor()
    val disabledContainerColor = if (isGlass) {
        activeSurfaceColor.copy(alpha = (activeSurfaceColor.alpha * 0.7f).coerceAtLeast(0.12f))
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    }
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = activeSurfaceColor,
            disabledContainerColor = disabledContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                PetalGroupIconBadge(
                    shape = petalBadgeShape(),
                    containerColor = containerColor,
                    contentColor = contentColor,
                ) { leadingIcon() }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            com.petal.browser.ui.components.IconSwitch(
                checked = checked,
                icon = Icons.Rounded.Check,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled,
            )
        }
    }
}

@Composable
fun PetalGroupNavigationRow(
    title: String,
    subtitle: String,
    position: PetalGroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    variant: PetalBadgeVariant = PetalBadgeVariant.PRIMARY,
) {
    val (containerColor, contentColor) = variant.colors()
    PetalGroupListRow(
        position = position,
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.38f),
        leading = {
            if (leadingIcon != null) PetalGroupIconBadge(
                shape = petalBadgeShape(),
                containerColor = containerColor,
                contentColor = contentColor,
            ) { leadingIcon() }
            else PetalGroupIconBadge(Icons.Rounded.Settings, variant = variant)
        },
        content = {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailing = trailingIcon ?: { Icon(Icons.Rounded.ChevronRight, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) },
    )
}

@Composable
fun PetalGroupToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: PetalGroupPosition,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    val activeSurfaceColor = petalGroupSurfaceColor()
    val disabledContainerColor = if (isGlass) {
        activeSurfaceColor.copy(alpha = (activeSurfaceColor.alpha * 0.7f).coerceAtLeast(0.12f))
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    }
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = activeSurfaceColor,
            disabledContainerColor = disabledContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leadingIcon()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            com.petal.browser.ui.components.IconSwitch(
                checked = checked,
                icon = Icons.Rounded.Check,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun PetalSettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    position: PetalGroupPosition = PetalGroupPosition.SINGLE,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    val badgeContainer = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val badgeTint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val activeSurfaceColor = petalGroupSurfaceColor()
    val disabledContainerColor = if (isGlass) {
        activeSurfaceColor.copy(alpha = (activeSurfaceColor.alpha * 0.7f).coerceAtLeast(0.12f))
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    }
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = activeSurfaceColor,
            disabledContainerColor = disabledContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetalGroupIconBadge(icon, badgeContainer, badgeTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            com.petal.browser.ui.components.IconSwitch(
                checked = checked,
                icon = icon,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun PetalSettingsToggleRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    position: PetalGroupPosition = PetalGroupPosition.SINGLE,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    val badgeContainer = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val badgeTint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val activeSurfaceColor = petalGroupSurfaceColor()
    val disabledContainerColor = if (isGlass) {
        activeSurfaceColor.copy(alpha = (activeSurfaceColor.alpha * 0.7f).coerceAtLeast(0.12f))
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    }
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = activeSurfaceColor,
            disabledContainerColor = disabledContainerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetalGroupIconBadge(
                painter = painterResource(iconRes),
                container = badgeContainer,
                tint = badgeTint
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            com.petal.browser.ui.components.PetalSwitch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun PetalGroupListRow(
    position: PetalGroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    minHeight: Dp = 64.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    leading: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val haptics = LocalHapticFeedback.current
    val selectedColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val shape = rememberPetalGroupShape(position)
    val rowContainerColor = when {
        LocalPetalSectionHighlighted.current -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        selected -> selectedColor
        else -> petalGroupSurfaceColor()
    }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = rowContainerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = if (isGlass) BorderStroke(0.75.dp, Color.White.copy(alpha = 0.16f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true)
            .combinedClickable(interactionSource = source, indication = ripple(), onClick = onClick, onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } })
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(contentPadding), verticalAlignment = Alignment.CenterVertically) {
            leading(); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f), content = content); trailing?.invoke()
        }
    }
}

@Composable
fun PetalSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

data class PetalConnectedButtonItem(val label: String, val icon: ImageVector? = null, val selected: Boolean = false)

fun petalConnectedShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(50.dp)
    index == 0 -> RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp)
    else -> RoundedCornerShape(6.dp)
}

@Composable
fun PetalConnectedButtonGroup(
    items: List<PetalConnectedButtonItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var clickedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(clickedIndex) {
        if (clickedIndex != null) {
            delay(240L)
            clickedIndex = null
        }
    }

    val currentActiveIndex = pressedIndex ?: clickedIndex

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = item.selected || (selectedIndex >= 0 && index == selectedIndex)
            val shape = petalConnectedShape(index, items.size)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            LaunchedEffect(isPressed) {
                if (isPressed) {
                    pressedIndex = index
                } else if (pressedIndex == index) {
                    pressedIndex = null
                }
            }

            val targetWeight = when (currentActiveIndex) {
                null -> 1f
                index -> 1.15f
                else -> 0.88f
            }

            val animatedWeight by animateFloatAsState(
                targetValue = targetWeight,
                animationSpec = com.petal.browser.ui.components.PetalSpring.segment(),
                label = "connectedButtonWeight"
            )

            Surface(
                onClick = {
                    clickedIndex = index
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(index)
                },
                modifier = Modifier
                    .weight(animatedWeight)
                    .defaultMinSize(minHeight = 48.dp),
                shape = shape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = null,
                interactionSource = interactionSource
            ) {
                val compact = items.size >= 3
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.icon?.let {
                        Icon(it, null, Modifier.size(if (compact) 16.dp else 18.dp))
                        Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                    }
                    Text(
                        text = item.label,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun PetalFloatingToolbar(modifier: Modifier = Modifier, elevation: Dp = 6.dp, content: @Composable RowScope.() -> Unit) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val toolbarShape = ExpressiveButtonShape
    Surface(
        modifier = modifier.liquidGlassChrome(shape = toolbarShape, enabled = isGlass, sheenIntensity = sheen),
        shape = toolbarShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isGlass) 0.dp else elevation,
        shadowElevation = if (isGlass) 0.dp else elevation
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Backdrop recorder for sheets: records the live app window (or a snapshot / pattern fallback)
 * into [wallpaper] so every glass panel inside the sheet can frost + refract it. Draws nothing.
 */
@Composable
internal fun PetalSheetGlassBackdrop(
    wallpaper: com.petal.browser.ui.glass.PetalGlassWallpaper,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hostSource = com.petal.browser.ui.glass.LocalPetalGlassSource.current
    val liveSource = remember(context, hostSource) {
        if (hostSource != null) return@remember hostSource
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.let { act ->
            act.findViewById<android.view.View>(com.petal.browser.R.id.main)
                ?: if (act !is com.petal.browser.apk.PetalApkInstallActivity) {
                    act.findViewById<android.view.View>(android.R.id.content)
                } else null
        }
    }
    val frameTick = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(liveSource) {
        if (liveSource != null) {
            while (true) { androidx.compose.runtime.withFrameNanos { frameTick.longValue = it } }
        }
    }
    val snapshot = remember { com.petal.browser.predictive.PetalContentSnapshot.current }
    if (liveSource != null) {
        Box(modifier.liveGlassWallpaper(wallpaper, liveSource) { frameTick.longValue })
    } else if (snapshot != null && !snapshot.isRecycled) {
        Box(modifier.recordGlassWallpaper(wallpaper)) {
            androidx.compose.foundation.Image(
                bitmap = snapshot.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
    } else {
        Box(modifier.captureGlassWallpaper(wallpaper)) {
            com.petal.browser.ui.glass.PetalGlassPattern(Modifier.fillMaxSize())
        }
    }
}

/**
 * Glass bottom sheet. The glass panel lives INSIDE the sheet content (not on the outer modifier,
 * which sits outside the sheet's slide offset and drew a ghost panel at the top of the screen).
 * The sheet owns a live backdrop so every card inside refracts the app window in real time.
 * Set [ownsGlassBackdrop] = false only if the caller provides its own LocalPetalGlassWallpaper.
 */
// PetalSheet has been relocated to PetalTransientSurfaces.kt as part of the shared transient surfaces unification.

@Composable
fun PetalFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = petalChipShape(),
    colors: SelectableChipColors? = null,
    border: BorderStroke? = null,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val wallpaper = LocalPetalGlassWallpaper.current
    val isGlassReady = isGlass && wallpaper?.ready == true
    val context = androidx.compose.ui.platform.LocalContext.current
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        FilterChip(
            selected = selected,
            onClick = {
                try {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.35f)
                } catch (_: Exception) {}
                onClick()
            },
            label = label,
            modifier = modifier.then(
                if (isGlass) Modifier.liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = if (selected) 0.65f else 0.35f,
                    pressFx = true
                ) else Modifier
            ),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors ?: FilterChipDefaults.filterChipColors(
                containerColor = if (isGlass) {
                    if (isGlassReady) Color.Transparent else petalGroupSurfaceColor()
                } else MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedContainerColor = if (isGlass) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
            ),
            border = if (isGlass) {
                BorderStroke(
                    0.75.dp,
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.18f)
                )
            } else border
        )
    }
}

@Composable
fun PetalAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = petalChipShape(),
    colors: ChipColors? = null,
    border: BorderStroke? = null,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val wallpaper = LocalPetalGlassWallpaper.current
    val isGlassReady = isGlass && wallpaper?.ready == true
    val context = androidx.compose.ui.platform.LocalContext.current
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        AssistChip(
            onClick = {
                try {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.35f)
                } catch (_: Exception) {}
                onClick()
            },
            label = label,
            modifier = modifier.then(
                if (isGlass) Modifier.liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = 0.40f,
                    pressFx = true
                ) else Modifier
            ),
            enabled = enabled,
            leadingIcon = leadingIcon,
            shape = shape,
            colors = colors ?: AssistChipDefaults.assistChipColors(
                containerColor = if (isGlass) {
                    if (isGlassReady) Color.Transparent else petalGroupSurfaceColor()
                } else MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = if (isGlass) {
                BorderStroke(0.75.dp, Color.White.copy(alpha = 0.18f))
            } else border
        )
    }
}

@Composable
fun PetalSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = petalChipShape(),
    colors: ChipColors? = null,
    border: BorderStroke? = null,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val wallpaper = LocalPetalGlassWallpaper.current
    val isGlassReady = isGlass && wallpaper?.ready == true
    val context = androidx.compose.ui.platform.LocalContext.current
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        SuggestionChip(
            onClick = {
                try {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.35f)
                } catch (_: Exception) {}
                onClick()
            },
            label = label,
            modifier = modifier.then(
                if (isGlass) Modifier.liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = 0.35f,
                    pressFx = true
                ) else Modifier
            ),
            enabled = enabled,
            icon = icon,
            shape = shape,
            colors = colors ?: SuggestionChipDefaults.suggestionChipColors(
                containerColor = if (isGlass) {
                    if (isGlassReady) Color.Transparent else petalGroupSurfaceColor()
                } else MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = if (isGlass) {
                BorderStroke(0.75.dp, Color.White.copy(alpha = 0.18f))
            } else border
        )
    }
}

@Composable
fun PetalInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = petalChipShape(),
    colors: SelectableChipColors? = null,
    border: BorderStroke? = null,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val wallpaper = LocalPetalGlassWallpaper.current
    val isGlassReady = isGlass && wallpaper?.ready == true
    val context = androidx.compose.ui.platform.LocalContext.current
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        InputChip(
            selected = selected,
            onClick = {
                try {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.35f)
                } catch (_: Exception) {}
                onClick()
            },
            label = label,
            modifier = modifier.then(
                if (isGlass) Modifier.liquidGlassChrome(
                    shape = shape,
                    enabled = true,
                    sheenIntensity = if (selected) 0.65f else 0.35f,
                    pressFx = true
                ) else Modifier
            ),
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = colors ?: InputChipDefaults.inputChipColors(
                containerColor = if (isGlass) {
                    if (isGlassReady) Color.Transparent else petalGroupSurfaceColor()
                } else MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainerColor = if (isGlass) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else MaterialTheme.colorScheme.primaryContainer,
                labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            ),
            border = if (isGlass) {
                BorderStroke(
                    0.75.dp,
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.18f)
                )
            } else (border ?: InputChipDefaults.inputChipBorder(enabled = enabled, selected = selected))
        )
    }
}

@Composable
fun PetalSelectableOptionCard(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    subtitle: String? = null, leading: (@Composable () -> Unit)? = null) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val cardShape = petalContainmentShape(20.dp)
    val defaultContainer = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else defaultContainer, label = "petalOptionContainer")
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().liquidGlassChrome(shape = cardShape, enabled = isGlass, sheenIntensity = sheen),
        shape = cardShape,
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border)
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke(); if (leading != null) Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (selected) Box(Modifier.size(28.dp).clip(ExpressiveButtonShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Containment surface shapes. They are live getters (not constants): they follow the containment shape
 * style and corner size from Appearance, and recompose callers when those change.
 */
object PetalContainmentShapes {
    val Hero: Shape get() = petalContainmentShape(32.dp)
    val HeroInner: Shape get() = petalContainmentShape(24.dp)
    val Pill: Shape
        get() = com.petal.browser.ui.theme.layout.PetalLayoutState.settings.containmentShapeType.pillShape()
    val Badge: Shape get() = Pill
}

@Composable
fun PetalHeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = PetalContainmentShapes.Hero,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    Card(
        modifier = modifier.fillMaxWidth().liquidGlassChrome(shape, enabled = isGlass, sheenIntensity = sheen),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass || LocalPetalSectionHighlighted.current) petalGroupSurfaceColor() else containerColor,
            contentColor = if (isGlass) MaterialTheme.colorScheme.onSurface else androidx.compose.material3.contentColorFor(containerColor)
        ),
        border = if (isGlass) BorderStroke(0.75.dp, Color.White.copy(alpha = 0.20f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
fun PetalActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = PetalContainmentShapes.HeroInner,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPetalGroupPressScale(source)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.scale(scale).liquidGlassChrome(shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass || LocalPetalSectionHighlighted.current) petalGroupSurfaceColor() else containerColor,
            contentColor = if (isGlass) MaterialTheme.colorScheme.onSurface else androidx.compose.material3.contentColorFor(containerColor)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        content = content,
    )
}

@Composable
fun PetalStatusHeroCard(
    title: String,
    subtitle: String,
    statusText: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    statusActive: Boolean = true,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    PetalHeroCard(modifier = modifier, shape = PetalContainmentShapes.Hero, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PetalGroupIconBadge(icon, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, size = 46.dp, iconSize = 24.dp)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = PetalContainmentShapes.Pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(ExpressiveButtonShape)
                                .background(if (statusActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                if (actionLabel != null && onActionClick != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = onActionClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
