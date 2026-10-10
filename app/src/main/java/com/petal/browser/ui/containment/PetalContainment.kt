package com.petal.browser.ui.containment

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.preference.PreferenceManager

enum class PetalGroupPosition { SINGLE, TOP, MIDDLE, BOTTOM }

val LocalPetalSectionHighlighted = compositionLocalOf { false }

/**
 * Checks whether Liquid Glass UI is enabled system-wide.
 */
@Composable
fun isLiquidGlassEnabled(componentKey: String = "sp_liquid_glass_enabled"): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean("sp_liquid_glass_enabled", false))
    }
    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_liquid_glass_enabled" || key == "sp_liquid_glass_unlocked") {
                enabled = sp.getBoolean("sp_liquid_glass_enabled", false)
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
fun Modifier.liquidGlassChrome(
    shape: Shape,
    enabled: Boolean = true,
    sheenIntensity: Float = 0.60f,
    tintColor: Color = Color.Unspecified,
    // When a backdrop is supplied (and the device supports it) the surface becomes REAL glass:
    // live blur of what is behind it + lens refraction. The caller must keep its own container
    // transparent in that case. Null keeps the old translucent-fill look.
    backdrop: PetalBackdrop? = null,
    isDark: Boolean = false
): Modifier = if (!enabled) this else Modifier
    .petalRealGlass(backdrop, shape, isDark)
    .liquidGlassSheen(shape, sheenIntensity, tintColor)

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
    val glassAlpha = if (isGlass) sp.getFloat("sp_liquid_glass_alpha", 0.70f).coerceIn(0.20f, 0.95f) else 1f
    val glassTint = if (isGlass) sp.getString("sp_liquid_glass_tint", "FROSTED") ?: "FROSTED" else "FROSTED"

    val baseGlassColor = when (glassTint) {
        "ACCENT" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = glassAlpha * 0.85f)
        "DEEP" -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = glassAlpha)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassAlpha)
    }

    val target = when {
        LocalPetalSectionHighlighted.current -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isGlass -> baseGlassColor
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val color by animateColorAsState(target, label = "petalGroupSurface")
    return color
}

fun petalGroupShape(position: PetalGroupPosition): RoundedCornerShape = when (position) {
    PetalGroupPosition.SINGLE -> RoundedCornerShape(28.dp)
    PetalGroupPosition.TOP -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    PetalGroupPosition.MIDDLE -> RoundedCornerShape(6.dp)
    PetalGroupPosition.BOTTOM -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
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
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
    shape: Shape = RoundedCornerShape(14.dp),
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
    shape: Shape = RoundedCornerShape(14.dp),
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
    Box(modifier.size(size).clip(RoundedCornerShape(14.dp)).background(container), contentAlignment = Alignment.Center) {
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
    Box(modifier.size(size).clip(RoundedCornerShape(14.dp)).background(container), contentAlignment = Alignment.Center) {
        Icon(painter, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PetalGroupIconBadge(
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
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
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
    val shape = petalGroupShape(position)
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
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
            if (trailing != null) trailing() else Icon(Icons.Filled.ChevronRight, null,
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
        trailing = { Switch(checked, if (enabled) onCheckedChange else null, thumbContent = if (checked) ({ Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null) })
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
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
    val shape = petalGroupShape(position)
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
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
                    shape = RoundedCornerShape(14.dp),
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
            Switch(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled,
                thumbContent = {
                    AnimatedContent(targetState = checked, transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) }, label = "petalSwitchThumb") { isChecked ->
                        Icon(if (isChecked) Icons.Filled.Check else Icons.Filled.Close, null, Modifier.size(SwitchDefaults.IconSize))
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary, checkedIconColor = MaterialTheme.colorScheme.primary),
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
                shape = RoundedCornerShape(14.dp),
                containerColor = containerColor,
                contentColor = contentColor,
            ) { leadingIcon() }
            else PetalGroupIconBadge(Icons.Filled.Settings, variant = variant)
        },
        content = {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailing = trailingIcon ?: { Icon(Icons.Filled.ChevronRight, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)) },
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
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
    val shape = petalGroupShape(position)
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
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
                icon = Icons.Filled.Check,
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
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
    val shape = petalGroupShape(position)
    val badgeContainer = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val badgeTint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
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
fun PetalGroupListRow(
    position: PetalGroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
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
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f
    val shape = petalGroupShape(position)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (LocalPetalSectionHighlighted.current) petalGroupSurfaceColor() else if (selected) selectedColor else petalGroupSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen)
            .combinedClickable(interactionSource = source, indication = ripple(), onClick = onClick, onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } })
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            leading(); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), content = content); trailing?.invoke()
        }
    }
}

@Composable
fun PetalSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

data class PetalConnectedButtonItem(val label: String, val icon: ImageVector? = null, val selected: Boolean = false)

private fun petalConnectedShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(20.dp)
    index == 0 -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp)
    else -> RoundedCornerShape(4.dp)
}

@Composable
fun PetalConnectedButtonGroup(items: List<PetalConnectedButtonItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((-1).dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { index, item ->
            val selected = item.selected || (selectedIndex >= 0 && index == selectedIndex)
            val shape = petalConnectedShape(index, items.size)
            Surface(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(index) }, modifier = Modifier.weight(1f),
                shape = shape, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                val compact = items.size >= 3
                Row(Modifier.defaultMinSize(minHeight = 44.dp).padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    item.icon?.let { Icon(it, null, Modifier.size(if (compact) 16.dp else 18.dp)); Spacer(Modifier.width(if (compact) 4.dp else 6.dp)) }
                    Text(item.label, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PetalFloatingToolbar(modifier: Modifier = Modifier, elevation: Dp = 6.dp, content: @Composable RowScope.() -> Unit) {
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = elevation, shadowElevation = elevation) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PetalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    dragHandle: (@Composable () -> Unit)? = {
        Box(Modifier.padding(vertical = 12.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = dragHandle,
        content = content)
}

@Composable
fun PetalSelectableOptionCard(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    subtitle: String? = null, leading: (@Composable () -> Unit)? = null) {
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, label = "petalOptionContainer")
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border)) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke(); if (leading != null) Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (selected) Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

object PetalContainmentShapes {
    val Hero = RoundedCornerShape(32.dp)
    val HeroInner = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
    val Badge = RoundedCornerShape(50)
}

@Composable
fun PetalHeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = PetalContainmentShapes.Hero,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (LocalPetalSectionHighlighted.current) petalGroupSurfaceColor() else containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), content = content)
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
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.scale(scale),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (LocalPetalSectionHighlighted.current) petalGroupSurfaceColor() else containerColor),
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
                                .clip(CircleShape)
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
