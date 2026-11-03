package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.components.EnhancedSliderItem
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.rememberGlassSheen
import com.petal.browser.ui.theme.PetalThemePrefs
import com.petal.browser.ui.theme.layout.LayoutIcons
import com.petal.browser.ui.theme.layout.PetalFlingType
import com.petal.browser.ui.theme.layout.PetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.PetalSliderType
import com.petal.browser.ui.theme.layout.PetalSwitchType
import java.util.Locale

/**
 * ImageToolbox-style Layout Settings & Live Controls Section.
 * Fully aligned with Petal Containment Design System:
 * - Connected group containment rows (TOP, MIDDLE, BOTTOM)
 * - EnhancedSliderItems with live numeric pills, icon badges, and reset actions
 * - Bottom Sheet pickers redesigned with Petal containment items & ImageToolbox icons
 * - Auto-applied Liquid Glass styling when liquid glass theme is active (NO manual toggle)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalLayoutSettingsSection(
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // Live preference states
    var switchType by remember {
        mutableStateOf(PetalSwitchType.fromKey(sp.getString(PetalLayoutSettings.KEY_SWITCH_TYPE, PetalSwitchType.COMPOSE.key)))
    }
    var sliderType by remember {
        mutableStateOf(PetalSliderType.fromKey(sp.getString(PetalLayoutSettings.KEY_SLIDER_TYPE, PetalSliderType.FANCY.key)))
    }
    var shapeType by remember {
        mutableStateOf(PetalShapeType.fromKey(sp.getString(PetalLayoutSettings.KEY_SHAPE_TYPE, PetalShapeType.ROUNDED.key)))
    }
    var cornersSize by remember {
        mutableFloatStateOf(sp.getFloat(PetalLayoutSettings.KEY_CORNERS_SIZE, 1.0f))
    }
    var cornersThrottleMs by remember {
        mutableLongStateOf(sp.getLong(PetalLayoutSettings.KEY_CORNERS_THROTTLE, 153L))
    }
    var flingType by remember {
        mutableStateOf(PetalFlingType.fromKey(sp.getString(PetalLayoutSettings.KEY_FLING_TYPE, PetalFlingType.DEFAULT.key)))
    }
    var motionDurationScale by remember {
        mutableFloatStateOf(sp.getFloat(PetalLayoutSettings.KEY_MOTION_DURATION_SCALE, 1.0f))
    }

    // Modal Sheet states
    var showSwitchTypeSheet by remember { mutableStateOf(false) }
    var showSliderTypeSheet by remember { mutableStateOf(false) }
    var showShapeTypeSheet by remember { mutableStateOf(false) }
    var showFlingTypeSheet by remember { mutableStateOf(false) }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val glassSheen = rememberGlassSheen(isGlass)

    PetalSettingsSection(
        title = "Expressive Layout & Controls",
        icon = Icons.Rounded.Gesture,
        cardId = "appearance_layout_controls",
        targetHighlightId = targetHighlightItemId
    ) {
        Text(
            text = "Tactile control physics, shapes, and component styles ported from ImageToolbox",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 1. Switch Type Picker Row
            PetalGroupRow(
                icon = Icons.Rounded.ToggleOn,
                title = "Switch Type",
                subtitle = if (isGlass) "Auto (Liquid Glass Active)" else switchType.title,
                position = PetalGroupPosition.TOP,
                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Switch Type",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showSwitchTypeSheet = true }
            )

            // 2. Slider Type Picker Row
            PetalGroupRow(
                icon = Icons.Rounded.LinearScale,
                title = "Slider Type",
                subtitle = sliderType.title,
                position = PetalGroupPosition.MIDDLE,
                iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Slider Type",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showSliderTypeSheet = true }
            )

            // 3. Shapes Type Picker Row
            PetalGroupRow(
                icon = Icons.Rounded.RoundedCorner,
                title = "Shapes Type",
                subtitle = shapeType.title,
                position = PetalGroupPosition.MIDDLE,
                iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Shapes Type",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showShapeTypeSheet = true }
            )

            // 4. Corners Size Slider (EnhancedSliderItem)
            EnhancedSliderItem(
                value = cornersSize,
                title = "Corners Size",
                icon = Icons.Rounded.Percent,
                valueText = "${(cornersSize * 100).toInt()}%",
                defaultValue = 1.0f,
                valueRange = 0f..2f,
                steps = 20,
                position = PetalGroupPosition.MIDDLE,
                onValueChange = {
                    cornersSize = it
                    sp.edit().putFloat(PetalLayoutSettings.KEY_CORNERS_SIZE, it).apply()
                    PetalThemePrefs.bump()
                }
            )

            // 5. Corners Animation Throttle (EnhancedSliderItem)
            EnhancedSliderItem(
                value = cornersThrottleMs.toFloat(),
                title = "Corners Animation Throttle",
                icon = Icons.Rounded.Schedule,
                valueText = "${cornersThrottleMs} ms",
                defaultValue = 153f,
                valueRange = 0f..500f,
                steps = 50,
                position = PetalGroupPosition.MIDDLE,
                onValueChange = {
                    val v = it.toLong()
                    cornersThrottleMs = v
                    sp.edit().putLong(PetalLayoutSettings.KEY_CORNERS_THROTTLE, v).apply()
                    PetalThemePrefs.bump()
                }
            )

            // 6. Fling Type Picker Row
            PetalGroupRow(
                icon = Icons.Rounded.Speed,
                title = "Fling Type",
                subtitle = flingType.title,
                position = PetalGroupPosition.MIDDLE,
                iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                trailing = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Fling Type",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showFlingTypeSheet = true }
            )

            // 7. Motion Duration Scale (EnhancedSliderItem)
            EnhancedSliderItem(
                value = motionDurationScale,
                title = "Motion Duration Scale",
                icon = Icons.Rounded.FastForward,
                valueText = "${String.format(Locale.US, "%.2f", motionDurationScale)}x",
                defaultValue = 1.0f,
                valueRange = 0.25f..3.0f,
                steps = 11,
                position = PetalGroupPosition.BOTTOM,
                onValueChange = {
                    motionDurationScale = it
                    sp.edit().putFloat(PetalLayoutSettings.KEY_MOTION_DURATION_SCALE, it).apply()
                    PetalThemePrefs.bump()
                }
            )
        }
    }

    // ── Bottom Sheet Pickers in Petal Containment Design ──

    // 1. Switch Type Sheet
    if (showSwitchTypeSheet) {
        PetalSheet(
            title = "Switch Type",
            onDismissRequest = { showSwitchTypeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val entries = PetalSwitchType.entries
                entries.forEachIndexed { index, item ->
                    val isSelected = item == switchType
                    val pos = PetalGroupPosition.byIndex(index, entries.size)
                    val icon = when (item) {
                        PetalSwitchType.MATERIAL_YOU -> Icons.Rounded.Android
                        PetalSwitchType.COMPOSE -> Icons.Rounded.Widgets
                        PetalSwitchType.PIXEL -> Icons.Rounded.Android
                        PetalSwitchType.FLUENT -> LayoutIcons.Windows
                        PetalSwitchType.CUPERTINO -> LayoutIcons.IOS
                        PetalSwitchType.HYPEROS -> LayoutIcons.HyperOS
                        PetalSwitchType.ONE_UI -> LayoutIcons.SamsungLetter
                    }

                    PetalGroupRow(
                        icon = icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        position = pos,
                        iconContainer = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Selected" else "Unselected",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            switchType = item
                            sp.edit().putString(PetalLayoutSettings.KEY_SWITCH_TYPE, item.key).apply()
                            PetalThemePrefs.bump()
                            showSwitchTypeSheet = false
                        }
                    )
                }
            }
        }
    }

    // 2. Slider Type Sheet
    if (showSliderTypeSheet) {
        PetalSheet(
            title = "Slider Type",
            onDismissRequest = { showSliderTypeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val entries = PetalSliderType.entries
                entries.forEachIndexed { index, item ->
                    val isSelected = item == sliderType
                    val pos = PetalGroupPosition.byIndex(index, entries.size)
                    val icon = when (item) {
                        PetalSliderType.FANCY -> Icons.Rounded.Star
                        PetalSliderType.MATERIAL_YOU -> Icons.Rounded.Android
                        PetalSliderType.MATERIAL -> Icons.Rounded.LinearScale
                        PetalSliderType.HYPEROS -> LayoutIcons.HyperOS
                    }

                    PetalGroupRow(
                        icon = icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        position = pos,
                        iconContainer = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Selected" else "Unselected",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            sliderType = item
                            sp.edit().putString(PetalLayoutSettings.KEY_SLIDER_TYPE, item.key).apply()
                            PetalThemePrefs.bump()
                            showSliderTypeSheet = false
                        }
                    )
                }
            }
        }
    }

    // 3. Shape Type Sheet
    if (showShapeTypeSheet) {
        PetalSheet(
            title = "Shapes Type",
            onDismissRequest = { showShapeTypeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val entries = PetalShapeType.entries
                entries.forEachIndexed { index, item ->
                    val isSelected = item == shapeType
                    val pos = PetalGroupPosition.byIndex(index, entries.size)

                    PetalGroupRow(
                        icon = Icons.Rounded.RoundedCorner,
                        title = item.title,
                        subtitle = item.subtitle,
                        position = pos,
                        iconContainer = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Selected" else "Unselected",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            shapeType = item
                            sp.edit().putString(PetalLayoutSettings.KEY_SHAPE_TYPE, item.key).apply()
                            PetalThemePrefs.bump()
                            showShapeTypeSheet = false
                        }
                    )
                }
            }
        }
    }

    // 4. Fling Type Sheet
    if (showFlingTypeSheet) {
        PetalSheet(
            title = "Fling Type",
            onDismissRequest = { showFlingTypeSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val entries = PetalFlingType.entries
                entries.forEachIndexed { index, item ->
                    val isSelected = item == flingType
                    val pos = PetalGroupPosition.byIndex(index, entries.size)

                    PetalGroupRow(
                        icon = Icons.Rounded.Speed,
                        title = item.title,
                        subtitle = item.subtitle,
                        position = pos,
                        iconContainer = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconTint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailing = {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Selected" else "Unselected",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = {
                            flingType = item
                            sp.edit().putString(PetalLayoutSettings.KEY_FLING_TYPE, item.key).apply()
                            PetalThemePrefs.bump()
                            showFlingTypeSheet = false
                        }
                    )
                }
            }
        }
    }
}
