package com.petal.browser.ui.containment

import com.petal.browser.ui.components.PetalSpring

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp

/*
 * PixelMusic-style sheet building blocks for Petal.
 *
 * Use inside PetalSheet { } to get the same language as PixelMusic's song-options sheet:
 *  - a header with a rounded 80dp leading visual and a light, large title
 *  - fully rounded (pill) action buttons with tonal color roles, min height 66dp
 *  - circular icon actions that sit beside a wide pill
 *  - a segmented pill switcher pinned to the bottom of the sheet
 */


private const val SHEET_GLASS_KEY = "sp_liquid_glass_containments"

/**
 * Surface used by every sheet part. With liquid glass on it is a live refracting glass chip tinted
 * with [tint] (the sheet already paints the shared wallpaper backdrop behind it); otherwise a flat
 * filled container. Tappable parts pass [onClick] and get Kyant-style press feedback on glass.
 */
@Composable
fun Modifier.petalSheetSurface(
    shape: Shape,
    color: Color,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    sheen: Float = 0.45f,
): Modifier {
    val glass = isLiquidGlassEnabled(SHEET_GLASS_KEY)
    val base = this.clip(shape)
    val styled = if (glass) {
        base.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = sheen, tintColor = color, pressFx = onClick != null)
    } else {
        base.background(color)
    }
    return if (onClick != null) styled.clickable(role = role, onClick = onClick) else styled
}

/** Rounded card container for grouped content (pickers, info rows, lists). */
@Composable
fun PetalSheetCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .petalSheetSurface(shape, color, onClick, sheen = 0.3f)
            .padding(contentPadding),
        content = content,
    )
}

/** Small uppercase-free section label used above groups of actions. */
@Composable
fun PetalSheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * Full-width list action: leading icon in a round chip, title + optional subtitle, optional trailing.
 * For lists of actions that are too many/too long for pill pairs (menus, per-item options).
 */
@Composable
fun PetalSheetListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: PetalSheetTone = PetalSheetTone.Secondary,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val danger = tone == PetalSheetTone.Danger
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .petalSheetSurface(RoundedCornerShape(32.dp), container, onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .petalSheetSurface(CircleShape, tone.container()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
        } else Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

/** Colour roles for [PetalSheetPill] / [PetalSheetIconPill]. */
enum class PetalSheetTone { Primary, Secondary, Tertiary, TertiaryStrong, Neutral, Danger }

@Composable
private fun PetalSheetTone.container(): Color = when (this) {
    PetalSheetTone.Primary -> MaterialTheme.colorScheme.primaryContainer
    PetalSheetTone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
    PetalSheetTone.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
    PetalSheetTone.TertiaryStrong -> MaterialTheme.colorScheme.tertiary
    PetalSheetTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    PetalSheetTone.Danger -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun PetalSheetTone.content(): Color = when (this) {
    PetalSheetTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
    PetalSheetTone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
    PetalSheetTone.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
    PetalSheetTone.TertiaryStrong -> MaterialTheme.colorScheme.onTertiary
    PetalSheetTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    PetalSheetTone.Danger -> MaterialTheme.colorScheme.onErrorContainer
}

/** Wide pill action: icon + label, 66dp tall, fully rounded. Live glass when liquid glass is on. */
@Composable
fun PetalSheetPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PetalSheetTone = PetalSheetTone.Secondary,
    minHeight: Dp = 66.dp,
) {
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .petalSheetSurface(CircleShape, tone.container(), onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = tone.content(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Circular icon action that sits next to a wide pill (favourite / share style). */
@Composable
fun PetalSheetIconPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tone: PetalSheetTone = PetalSheetTone.Neutral,
    minHeight: Dp = 66.dp,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else tone.container(),
        animationSpec = PetalSpring.calm(), label = "sheet_icon_pill_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else tone.content(),
        animationSpec = PetalSpring.calm(), label = "sheet_icon_pill_content"
    )
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .petalSheetSurface(CircleShape, container, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(if (minHeight < 56.dp) 22.dp else 28.dp))
    }
}

/** Sheet header: rounded 80dp visual, light large title, optional subtitle and trailing action. */
@Composable
fun PetalSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 30.sp),
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** One segment of [PetalSheetSegmentedBar]. */
data class PetalSheetSegment(val label: String, val icon: ImageVector)

/**
 * Bottom pill switcher (OPTIONS | INFO in PixelMusic). The selected segment is filled with
 * primaryContainer, the others sit on a darker surface inside a surfaceContainerHigh track.
 */
@Composable
fun PetalSheetSegmentedBar(
    segments: List<PetalSheetSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .petalSheetSurface(CircleShape, MaterialTheme.colorScheme.surfaceContainerHighest, sheen = 0.3f)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest,
                PetalSpring.calm(), label = "segment_bg"
            )
            val fg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                PetalSpring.calm(), label = "segment_fg"
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .petalSheetSurface(CircleShape, bg, { onSelect(index) }, role = Role.Tab, sheen = if (selected) 0.5f else 0.15f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(segment.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = segment.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Convenience row that lays pills out with the 10dp rhythm used in PixelMusic. */
@Composable
fun PetalSheetActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Cookie-shaped check badge shown on a selected [PetalSheetChoicePill] / row. */
@Composable
fun PetalSheetCheckBadge(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, checkColor: Color = MaterialTheme.colorScheme.onPrimary) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(com.petal.browser.ui.theme.ExpressiveButtonShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = checkColor, modifier = Modifier.size(16.dp))
    }
}

/**
 * Selectable choice for a pair/trio of options (view mode, layout...). Icon above a (max 2 line) label so
 * long names never get chopped mid-word, fully rounded, with a cookie check badge when [selected].
 * Selected = primaryContainer, unselected = tonal [PetalSheetTone.Neutral] on glass/filled surface.
 */
@Composable
fun PetalSheetChoicePill(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 88.dp,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = PetalSpring.calm(), label = "sheet_choice_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = PetalSpring.calm(), label = "sheet_choice_content"
    )
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .petalSheetSurface(RoundedCornerShape(40.dp), container, onClick, role = Role.RadioButton)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (selected) PetalSheetCheckBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

/**
 * Full-width row with a round icon chip, title/subtitle and a switch. The whole row toggles. The switch
 * is the shared [com.petal.browser.ui.components.IconSwitch], so it follows the chosen switch style and
 * becomes the liquid glass toggle while Liquid Glass is on.
 */
@Composable
fun PetalSheetToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchIcon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: PetalSheetTone = PetalSheetTone.Secondary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .petalSheetSurface(RoundedCornerShape(36.dp), MaterialTheme.colorScheme.surfaceContainerHighest, { onCheckedChange(!checked) }, role = Role.Switch)
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(48.dp).petalSheetSurface(CircleShape, tone.container()), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
        } else Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(12.dp))
        com.petal.browser.ui.components.IconSwitch(checked = checked, icon = switchIcon, onCheckedChange = onCheckedChange)
    }
}
