package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.theme.ExpressiveButtonShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.EnhancedSliderItem
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.PetalSwitch
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.rememberPetalGroupPressScale
import com.petal.browser.ui.containment.rememberPetalGroupShape
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.icons.imagetoolbox.Android
import com.petal.browser.ui.icons.imagetoolbox.Animation
import com.petal.browser.ui.icons.imagetoolbox.AutoAwesome
import com.petal.browser.ui.icons.imagetoolbox.Cube
import com.petal.browser.ui.icons.imagetoolbox.HyperOS
import com.petal.browser.ui.icons.imagetoolbox.IOS
import com.petal.browser.ui.icons.imagetoolbox.ImageToolboxIcons
import com.petal.browser.ui.icons.imagetoolbox.MaterialDesign
import com.petal.browser.ui.icons.imagetoolbox.MiniEdit
import com.petal.browser.ui.icons.imagetoolbox.Percent
import com.petal.browser.ui.icons.imagetoolbox.RadioButtonChecked
import com.petal.browser.ui.icons.imagetoolbox.RadioButtonUnchecked
import com.petal.browser.ui.icons.imagetoolbox.RoundedCorner
import com.petal.browser.ui.icons.imagetoolbox.SamsungLetter
import com.petal.browser.ui.icons.imagetoolbox.Slider
import com.petal.browser.ui.icons.imagetoolbox.Timelapse
import com.petal.browser.ui.icons.imagetoolbox.ToggleOff
import com.petal.browser.ui.icons.imagetoolbox.ToggleOn
import com.petal.browser.ui.icons.imagetoolbox.Windows
import com.petal.browser.ui.theme.layout.PetalBadgeShape
import com.petal.browser.ui.theme.layout.PetalContainmentShapeChoice
import com.petal.browser.ui.theme.layout.PetalFlingType
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.PetalSliderType
import com.petal.browser.ui.theme.layout.PetalSwitchType
import com.petal.browser.ui.theme.layout.cornerShape
import com.petal.browser.ui.theme.layout.petalFlingBehavior
import com.petal.browser.ui.theme.layout.rememberPetalLayoutSettings
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Expressive Layout & Controls (ImageToolbox-style).
 *
 * Everything here is live: picking a switch / slider / shape / fling style, or moving a slider,
 * restyles the whole app on the spot - the picker sheets stay open so you can try each option and
 * see it applied behind the sheet. Liquid Glass needs no switch type: while it is active every
 * switch automatically becomes the glass toggle.
 */
@Composable
fun PetalLayoutSettingsSection(
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = rememberPetalLayoutSettings()
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val total = 8
    fun pos(i: Int) = petalGroupPositionFor(i, total)

    PetalSettingsSection(
        title = "Expressive Layout & Controls",
        icon = Icons.Rounded.Gesture,
        cardId = "appearance_layout_controls",
        targetHighlightId = targetHighlightItemId,
        modifier = modifier
    ) {
        Text(
            text = "Switch, slider and shape styles from ImageToolbox - changes apply instantly across Petal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        PetalGroupRow(
            icon = ImageToolboxIcons.Outlined.ToggleOn,
            title = "Switch Type",
            subtitle = if (isGlass) "Liquid Glass toggle (automatic while glass is on)" else settings.switchType.title,
            position = pos(0),
            iconContainer = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            trailing = { EditGlyph() },
            onClick = { sheet = SHEET_SWITCH }
        )
        PetalGroupRow(
            icon = ImageToolboxIcons.Rounded.Slider,
            title = "Slider Type",
            subtitle = settings.sliderType.title,
            position = pos(1),
            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            trailing = { EditGlyph() },
            onClick = { sheet = SHEET_SLIDER }
        )
        PetalGroupRow(
            icon = ImageToolboxIcons.Rounded.RoundedCorner,
            title = "Shapes Type",
            subtitle = settings.shapeType.title,
            position = pos(2),
            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            trailing = { EditGlyph() },
            onClick = { sheet = SHEET_SHAPE }
        )

        EnhancedSliderItem(
            value = settings.cornersSize,
            title = "Corners Size",
            icon = ImageToolboxIcons.Rounded.Percent,
            valueText = String.format(Locale.US, "%.2f", settings.cornersSize),
            defaultValue = 1f,
            valueRange = 0f..2f,
            position = pos(3),
            onValueChange = { v -> PetalLayoutState.update(context) { it.copy(cornersSize = v) } }
        )

        PetalGroupRow(
            icon = ImageToolboxIcons.Rounded.Animation,
            title = "Fling Type",
            subtitle = "${settings.flingType.title} · Applies to lists & app surfaces",
            position = pos(4),
            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            trailing = { EditGlyph() },
            onClick = { sheet = SHEET_FLING }
        )

        EnhancedSliderItem(
            value = settings.motionDurationScale,
            title = "Motion Duration Scale",
            icon = Icons.Rounded.FastForward,
            valueText = String.format(Locale.US, "%.2fx", settings.motionDurationScale),
            defaultValue = 1f,
            valueRange = 0.25f..3f,
            steps = 10,
            position = pos(5),
            onValueChange = { v -> PetalLayoutState.update(context) { it.copy(motionDurationScale = v) } }
        )

        PetalSettingsToggleRow(
            title = "Switch Shadows",
            subtitle = "Soft shadow under switch thumbs",
            icon = Icons.Rounded.Layers,
            checked = settings.drawSwitchShadows,
            position = pos(6),
            onCheckedChange = { v -> PetalLayoutState.update(context) { it.copy(drawSwitchShadows = v) } }
        )
        PetalSettingsToggleRow(
            title = "Slider Shadows",
            subtitle = "Soft shadow under slider thumbs",
            icon = Icons.Rounded.LinearScale,
            checked = settings.drawSliderShadows,
            position = pos(7),
            onCheckedChange = { v -> PetalLayoutState.update(context) { it.copy(drawSliderShadows = v) } }
        )
    }

    // ── Containment shapes ── containment's own shape style, corner size and icon container shape.
    val totalContainment = 4
    fun cpos(i: Int) = petalGroupPositionFor(i, totalContainment)

    PetalSettingsSection(
        title = "Containment Shapes",
        icon = Icons.Rounded.Category,
        cardId = "appearance_containment_shapes",
        targetHighlightId = targetHighlightItemId,
        modifier = modifier
    ) {
        Text(
            text = "Shape of Petal's cards, rows, chips and icon containers. Follows Shapes Type unless you pick your own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        PetalGroupRow(
            icon = ImageToolboxIcons.Rounded.RoundedCorner,
            title = "Containment Shape",
            subtitle = if (settings.containmentShape == PetalContainmentShapeChoice.FOLLOW_APP)
                "Following Shapes Type · ${settings.shapeType.title}" else settings.containmentShape.title,
            position = cpos(0),
            iconContainer = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            trailing = { EditGlyph() },
            onClick = { sheet = SHEET_CONTAIN_SHAPE }
        )
        PetalSettingsToggleRow(
            title = "Own Corner Size",
            subtitle = if (settings.containmentOwnCorners) "Containment uses its own corner size" else "Containment follows Corners Size",
            icon = ImageToolboxIcons.Rounded.Percent,
            checked = settings.containmentOwnCorners,
            position = cpos(1),
            onCheckedChange = { v -> PetalLayoutState.update(context) { it.copy(containmentOwnCorners = v) } }
        )
        EnhancedSliderItem(
            value = settings.containmentCornersSize,
            title = "Containment Corners",
            icon = ImageToolboxIcons.Rounded.RoundedCorner,
            valueText = String.format(Locale.US, "%.2f", settings.containmentCornersSize),
            defaultValue = 1f,
            valueRange = 0f..2f,
            position = cpos(2),
            enabled = settings.containmentOwnCorners,
            onValueChange = { v -> PetalLayoutState.update(context) { it.copy(containmentCornersSize = v) } }
        )
        PetalGroupRow(
            icon = Icons.Rounded.Category,
            title = "Icon Container Shape",
            subtitle = settings.badgeShape.title,
            position = cpos(3),
            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            trailing = { EditGlyph() },
            onClick = { sheet = SHEET_BADGE_SHAPE }
        )
    }

    // ── Pickers ── selection applies immediately; the sheet stays open to compare styles.

    if (sheet == SHEET_SWITCH) {
        ChoiceSheet(
            title = "Switch Type",
            headerIcon = ImageToolboxIcons.Outlined.ToggleOff,
            items = PetalSwitchType.entries,
            selected = settings.switchType,
            banner = if (isGlass) "Liquid Glass is on, so every switch is the glass toggle automatically. " +
                "Your choice below applies as soon as Liquid Glass is off." else null,
            onSelect = { t -> PetalLayoutState.update(context) { it.copy(switchType = t) } },
            onDismiss = { sheet = null }
        ) { item, selected, position, onClick ->
            val demo = rememberDemoToggle()
            ChoiceCard(
                selected = selected,
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon(),
                position = position,
                onClick = onClick,
                trailingPreview = {
                    // Always shows the real style (glassOverride = false) even while glass is active.
                    PetalSwitch(
                        checked = demo.value,
                        onCheckedChange = { demo.value = it },
                        thumbIcon = Icons.Rounded.Info,
                        typeOverride = item,
                        glassOverride = false
                    )
                }
            )
        }
    }

    if (sheet == SHEET_SLIDER) {
        ChoiceSheet(
            title = "Slider Type",
            headerIcon = ImageToolboxIcons.Rounded.Slider,
            items = PetalSliderType.entries,
            selected = settings.sliderType,
            banner = null,
            onSelect = { t -> PetalLayoutState.update(context) { it.copy(sliderType = t) } },
            onDismiss = { sheet = null }
        ) { item, selected, position, onClick ->
            ChoiceCard(
                selected = selected,
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon(),
                position = position,
                onClick = onClick,
                bottomPreview = { SliderDemo(item) }
            )
        }
    }

    if (sheet == SHEET_SHAPE) {
        ChoiceSheet(
            title = "Shapes Type",
            headerIcon = ImageToolboxIcons.Rounded.RoundedCorner,
            items = PetalShapeType.entries,
            selected = settings.shapeType,
            banner = null,
            onSelect = { t -> PetalLayoutState.update(context) { it.copy(shapeType = t) } },
            onDismiss = { sheet = null }
        ) { item, selected, position, onClick ->
            ChoiceCard(
                selected = selected,
                title = item.title,
                subtitle = item.subtitle,
                icon = ImageToolboxIcons.Rounded.RoundedCorner,
                position = position,
                onClick = onClick,
                trailingPreview = {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(item.cornerShape(16.dp, multiplier = settings.cornersSize.coerceAtLeast(0.4f)))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            )
                    )
                }
            )
        }
    }

    if (sheet == SHEET_CONTAIN_SHAPE) {
        ChoiceSheet(
            title = "Containment Shape",
            headerIcon = ImageToolboxIcons.Rounded.RoundedCorner,
            items = PetalContainmentShapeChoice.entries,
            selected = settings.containmentShape,
            banner = null,
            onSelect = { t -> PetalLayoutState.update(context) { it.copy(containmentShape = t) } },
            onDismiss = { sheet = null }
        ) { item, selected, position, onClick ->
            val previewType = item.type ?: settings.shapeType
            ChoiceCard(
                selected = selected,
                title = item.title,
                subtitle = item.subtitle,
                icon = ImageToolboxIcons.Rounded.RoundedCorner,
                position = position,
                onClick = onClick,
                trailingPreview = {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(previewType.cornerShape(16.dp, multiplier = PetalLayoutState.containmentCorners.coerceAtLeast(0.4f)))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            )
                    )
                }
            )
        }
    }

    if (sheet == SHEET_BADGE_SHAPE) {
        ChoiceSheet(
            title = "Icon Container Shape",
            headerIcon = Icons.Rounded.Category,
            items = PetalBadgeShape.entries,
            selected = settings.badgeShape,
            banner = "Mixed gives each icon its own Material 3 Expressive shape, like the Expressive Keyboard. Pick one shape to use it everywhere.",
            onSelect = { t -> PetalLayoutState.update(context) { it.copy(badgeShape = t) } },
            onDismiss = { sheet = null }
        ) { item, selected, position, onClick ->
            ChoiceCard(
                selected = selected,
                title = item.title,
                subtitle = item.subtitle,
                icon = Icons.Rounded.Category,
                position = position,
                onClick = onClick,
                trailingPreview = {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(item.shape(settings.containmentShapeType, PetalLayoutState.containmentCorners))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Star, null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        }
    }

    if (sheet == SHEET_FLING) {
        ChoiceSheet(
            title = "Fling Type",
            headerIcon = ImageToolboxIcons.Rounded.Animation,
            items = PetalFlingType.entries,
            selected = settings.flingType,
            banner = null,
            onSelect = { t -> PetalLayoutState.update(context) { it.copy(flingType = t) } },
            onDismiss = { sheet = null }
        ) { item, selected, position, onClick ->
            ChoiceCard(
                selected = selected,
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon(),
                position = position,
                onClick = onClick,
                trailingPreview = { FlingDemo(item) }
            )
        }
    }
}

private const val SHEET_SWITCH = "switch"
private const val SHEET_SLIDER = "slider"
private const val SHEET_SHAPE = "shape"
private const val SHEET_FLING = "fling"
private const val SHEET_CONTAIN_SHAPE = "containment_shape"
private const val SHEET_BADGE_SHAPE = "badge_shape"

// ── Icons (ImageToolbox) ─────────────────────────────────────────────────────────────────────

private fun PetalSwitchType.icon(): ImageVector = when (this) {
    PetalSwitchType.MATERIAL_YOU -> ImageToolboxIcons.Outlined.MaterialDesign
    PetalSwitchType.COMPOSE -> ImageToolboxIcons.Outlined.Cube
    PetalSwitchType.PIXEL -> ImageToolboxIcons.Rounded.Android
    PetalSwitchType.FLUENT -> ImageToolboxIcons.Rounded.Windows
    PetalSwitchType.CUPERTINO -> ImageToolboxIcons.Rounded.IOS
    PetalSwitchType.HYPEROS -> ImageToolboxIcons.Outlined.HyperOS
    PetalSwitchType.ONE_UI -> ImageToolboxIcons.Outlined.SamsungLetter
}

private fun PetalSliderType.icon(): ImageVector = when (this) {
    PetalSliderType.FANCY -> ImageToolboxIcons.Rounded.AutoAwesome
    PetalSliderType.MATERIAL_YOU -> ImageToolboxIcons.Outlined.MaterialDesign
    PetalSliderType.MATERIAL -> ImageToolboxIcons.Rounded.Android
    PetalSliderType.HYPEROS -> ImageToolboxIcons.Outlined.HyperOS
}

private fun PetalFlingType.icon(): ImageVector = when (this) {
    PetalFlingType.DEFAULT -> ImageToolboxIcons.Rounded.Android
    PetalFlingType.SMOOTH -> Icons.Rounded.Waves
    PetalFlingType.IOS_STYLE -> ImageToolboxIcons.Rounded.IOS
    PetalFlingType.SNAPPY -> Icons.Rounded.Bolt
    PetalFlingType.FLOATY -> Icons.Rounded.Air
    PetalFlingType.REDUCED_MOTION -> Icons.Rounded.Block
}

@Composable
private fun EditGlyph() {
    Icon(
        imageVector = ImageToolboxIcons.Rounded.MiniEdit,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
    )
}

// ── Picker sheet ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun <T> ChoiceSheet(
    title: String,
    headerIcon: ImageVector,
    items: List<T>,
    selected: T,
    banner: String?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    row: @Composable (item: T, selected: Boolean, position: PetalGroupPosition, onClick: () -> Unit) -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    PetalSheet(
        onDismissRequest = onDismiss,
        titleContent = { SheetTitle(title, headerIcon) },
        actionContent = { CloseButton(onDismiss) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState(), flingBehavior = petalFlingBehavior())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (banner != null) {
                Text(
                    text = banner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items.forEachIndexed { index, item ->
                row(item, item == selected, petalGroupPositionFor(index, items.size)) { onSelect(item) }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .clip(ExpressiveButtonShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RowScope.CloseButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
    ) {
        Text("Close", fontWeight = FontWeight.SemiBold)
    }
}

/** One selectable option: Petal containment surface, live glass, spring press, animated selection. */
@Composable
private fun ChoiceCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    position: PetalGroupPosition,
    onClick: () -> Unit,
    trailingPreview: (@Composable () -> Unit)? = null,
    bottomPreview: (@Composable () -> Unit)? = null,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val scheme = MaterialTheme.colorScheme
    val shape = rememberPetalGroupShape(position)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressScale = rememberPetalGroupPressScale(source)
    val surface = petalGroupSurfaceColor()

    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer.copy(alpha = if (isGlass) 0.55f else 1f) else surface,
        animationSpec = tween(220),
        label = "choiceContainer"
    )
    val outline by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer.copy(alpha = 0.45f) else Color.Transparent,
        animationSpec = tween(220),
        label = "choiceOutline"
    )

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true)
            .border(1.5.dp, outline, shape)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetalGroupIconBadge(
                    icon = icon,
                    container = if (selected) scheme.primary else scheme.secondaryContainer,
                    tint = if (selected) scheme.onPrimary else scheme.onSecondaryContainer,
                    size = 40.dp,
                    iconSize = 22.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (trailingPreview != null) {
                    Spacer(Modifier.width(10.dp))
                    trailingPreview()
                }
                Spacer(Modifier.width(8.dp))
                Crossfade(targetState = selected, label = "choiceRadio") { on ->
                    Icon(
                        imageVector = if (on) ImageToolboxIcons.Rounded.RadioButtonChecked
                        else ImageToolboxIcons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (on) "Selected" else "Not selected",
                        tint = if (on) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (bottomPreview != null) {
                Spacer(Modifier.height(8.dp))
                bottomPreview()
            }
        }
    }
}

// ── Live previews ────────────────────────────────────────────────────────────────────────────

/** Flips by itself so each switch style shows its animation; any tap on it also flips it. */
@Composable
private fun rememberDemoToggle(): androidx.compose.runtime.MutableState<Boolean> {
    val state = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1900)
            state.value = !state.value
        }
    }
    return state
}

@Composable
private fun SliderDemo(type: PetalSliderType) {
    val transition = rememberInfiniteTransition(label = "sliderDemo")
    val auto by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "sliderDemoValue"
    )
    var user by remember { mutableStateOf<Float?>(null) }
    PetalSlider(
        value = user ?: auto,
        onValueChange = { user = it },
        valueRange = 0f..1f,
        typeOverride = type,
        glassOverride = false,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FlingDemo(type: PetalFlingType) {
    val (duration, overshoot) = when (type) {
        PetalFlingType.DEFAULT -> 900 to false
        PetalFlingType.SMOOTH -> 1150 to false
        PetalFlingType.IOS_STYLE -> 1300 to true
        PetalFlingType.SNAPPY -> 520 to false
        PetalFlingType.FLOATY -> 1700 to false
        PetalFlingType.REDUCED_MOTION -> 140 to false
    }
    val decel = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
    val transition = rememberInfiniteTransition(label = "flingDemo")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = duration + 800
                0f at 0
                if (overshoot) 1.12f at (duration * 0.65f).toInt() using decel
                1f at duration using decel
                1f at duration + 800
            }
        ),
        label = "flingDemoX"
    )
    val track = 52.dp
    val dot = 12.dp
    Box(modifier = Modifier.size(width = track, height = dot)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = track, height = 4.dp)
                .clip(ExpressiveButtonShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(
            Modifier
                .offset(x = (track - dot) * x.coerceIn(0f, 1.2f))
                .size(dot)
                .clip(ExpressiveButtonShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
