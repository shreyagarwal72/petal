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
import androidx.compose.material.icons.rounded.ToggleOn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
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
import com.petal.browser.ui.theme.layout.PetalFlingType
import com.petal.browser.ui.theme.layout.PetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.PetalSliderType
import com.petal.browser.ui.theme.layout.PetalSwitchType
import java.util.Locale

/**
 * ImageToolbox-style Layout Settings & Live Controls Section.
 * Matching the layout in ImageToolbox:
 * - Switch Type (Material You, Compose, Pixel, Fluent, Cupertino, HyperOS, OneUI)
 * - Slider Type (Fancy, Material You, Material, HyperOS)
 * - Shapes Type (Rounded, Cut, Squircle, Smooth, Wavy, Scoop, Notch)
 * - Corners Size (0f..2f slider with live numeric badge)
 * - Corners Animation Throttle (0..500ms slider with ms badge)
 * - Fling Type (Android Native, Smooth, iOS Style, Snappy, Floaty, Reduced Motion)
 * - Motion Duration Scale (0.25x..3x slider with multiplier badge)
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
            // 1. Switch Type
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

            // 2. Slider Type
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

            // 3. Shapes Type
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

            // 4. Corners Size Slider
            val cornersCardShape = petalGroupShape(PetalGroupPosition.MIDDLE)
            Card(
                shape = cornersCardShape,
                colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassChrome(shape = cornersCardShape, enabled = isGlass, sheenIntensity = glassSheen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PetalGroupIconBadge(
                                icon = Icons.Rounded.Percent,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 36.dp,
                                iconSize = 18.dp
                            )
                            Text(
                                text = "Corners Size",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", cornersSize),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    PetalSlider(
                        value = cornersSize,
                        onValueChange = {
                            cornersSize = it
                            sp.edit().putFloat(PetalLayoutSettings.KEY_CORNERS_SIZE, it).apply()
                            PetalThemePrefs.bump()
                        },
                        valueRange = 0f..2f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 5. Corners Animation Throttle
            val throttleCardShape = petalGroupShape(PetalGroupPosition.MIDDLE)
            Card(
                shape = throttleCardShape,
                colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassChrome(shape = throttleCardShape, enabled = isGlass, sheenIntensity = glassSheen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PetalGroupIconBadge(
                                icon = Icons.Rounded.Schedule,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 36.dp,
                                iconSize = 18.dp
                            )
                            Text(
                                text = "Corners Animation Throttle",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${cornersThrottleMs} ms",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    PetalSlider(
                        value = cornersThrottleMs.toFloat(),
                        onValueChange = {
                            val v = it.toLong()
                            cornersThrottleMs = v
                            sp.edit().putLong(PetalLayoutSettings.KEY_CORNERS_THROTTLE, v).apply()
                            PetalThemePrefs.bump()
                        },
                        valueRange = 0f..500f,
                        steps = 50,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 6. Fling Type
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

            // 7. Motion Duration Scale
            val motionCardShape = petalGroupShape(PetalGroupPosition.BOTTOM)
            Card(
                shape = motionCardShape,
                colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassChrome(shape = motionCardShape, enabled = isGlass, sheenIntensity = glassSheen)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PetalGroupIconBadge(
                                icon = Icons.Rounded.FastForward,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                size = 36.dp,
                                iconSize = 18.dp
                            )
                            Text(
                                text = "Motion Duration Scale",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.2f", motionDurationScale)}x",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    PetalSlider(
                        value = motionDurationScale,
                        onValueChange = {
                            motionDurationScale = it
                            sp.edit().putFloat(PetalLayoutSettings.KEY_MOTION_DURATION_SCALE, it).apply()
                            PetalThemePrefs.bump()
                        },
                        valueRange = 0.25f..3.0f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // ── Bottom Sheet Pickers ──

    // Switch Type Sheet
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PetalSwitchType.entries.forEach { item ->
                    val isSelected = item == switchType
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                switchType = item
                                sp.edit().putString(PetalLayoutSettings.KEY_SWITCH_TYPE, item.key).apply()
                                PetalThemePrefs.bump()
                                showSwitchTypeSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Slider Type Sheet
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PetalSliderType.entries.forEach { item ->
                    val isSelected = item == sliderType
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sliderType = item
                                sp.edit().putString(PetalLayoutSettings.KEY_SLIDER_TYPE, item.key).apply()
                                PetalThemePrefs.bump()
                                showSliderTypeSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Shapes Type Sheet
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PetalShapeType.entries.forEach { item ->
                    val isSelected = item == shapeType
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                shapeType = item
                                sp.edit().putString(PetalLayoutSettings.KEY_SHAPE_TYPE, item.key).apply()
                                PetalThemePrefs.bump()
                                showShapeTypeSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Fling Type Sheet
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PetalFlingType.entries.forEach { item ->
                    val isSelected = item == flingType
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                flingType = item
                                sp.edit().putString(PetalLayoutSettings.KEY_FLING_TYPE, item.key).apply()
                                PetalThemePrefs.bump()
                                showFlingTypeSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
