package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.components.PetalSpring

import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.icons.imagetoolbox.AutoAwesome
import com.petal.browser.ui.icons.imagetoolbox.HyperOS
import com.petal.browser.ui.icons.imagetoolbox.ImageToolboxIcons
import com.petal.browser.ui.icons.imagetoolbox.ToggleOn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.theme.layout.UiExperience
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.PetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalSwitchType
import com.petal.browser.ui.theme.layout.PetalSliderType
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.PetalContainmentShapeChoice
import com.petal.browser.ui.theme.layout.PetalBadgeShape
import com.petal.browser.ui.theme.layout.rememberPetalLayoutSettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalLanguages
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import com.petal.browser.ui.theme.trackRevealOrigin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.Card
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.settings.viewmodel.AppearanceSettingsViewModel
import com.petal.browser.ui.components.*
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.PetalHighRefreshRateManager
import com.petal.browser.widget.PetalSearchWidgetProvider
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.runtime.saveable.rememberSaveable
import com.petal.browser.ui.theme.ThemeReveal

@Composable
fun AppearanceAdvancedColorsPage(
    reveal: ThemeReveal,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val paletteId by viewModel.paletteId.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val themeSp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val customRevision = com.petal.browser.ui.theme.PetalThemePrefs.revision
    var pickerSlot by remember { mutableStateOf(-1) } // 0 primary, 1 secondary, 2 tertiary
    val isCustomPalette = paletteId == com.petal.browser.ui.theme.CUSTOM_PALETTE_ID && !dynamicColor

    AppearanceSubPageScaffold(
        title = "Advanced colors",
        subtitle = "Mix palettes and custom palette colors",
        pageSeed = "appearance_advanced_colors",
        onBack = onBack,
        modifier = modifier
    ) {
        PetalSettingsSection(
            title = "Advanced colors",
            icon = Icons.Rounded.Tune,
            cardId = "appearance_advanced_colors",
            targetHighlightId = targetHighlightItemId
        ) {
            // ── Mix palettes: primary / secondary / tertiary from different sources at once ──
            val mixConfig = remember(customRevision) { com.petal.browser.ui.theme.PetalPaletteMix.config(context) }
            val mixCustom = remember(customRevision) { com.petal.browser.ui.theme.PetalCustomPalette.spec(context) }
            var mixEnabled by remember { mutableStateOf(mixConfig.enabled) }
            var mixDialogRole by remember { mutableStateOf(0) } // 1 secondary, 2 tertiary

            com.petal.browser.ui.containment.PetalSettingsToggleRow(
                title = "Mix palettes",
                subtitle = "Harmonize distinct color sources for secondary and tertiary while keeping your primary",
                icon = Icons.Rounded.ColorLens,
                checked = mixEnabled,
                position = if (mixEnabled) com.petal.browser.ui.containment.PetalGroupPosition.TOP else com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                onCheckedChange = { on ->
                    mixEnabled = on
                    reveal.reveal {
                        val editor = themeSp.edit().putBoolean(com.petal.browser.ui.theme.PetalPaletteMix.KEY_ENABLED, on)
                        if (on && mixConfig.secondary == com.petal.browser.ui.theme.PetalPaletteMix.Source.Auto && mixConfig.tertiary == com.petal.browser.ui.theme.PetalPaletteMix.Source.Auto) {
                            val (defaultSec, defaultTer) = com.petal.browser.ui.theme.PetalPaletteMix.defaultVibrantSources()
                            editor.putString(com.petal.browser.ui.theme.PetalPaletteMix.KEY_SECONDARY, com.petal.browser.ui.theme.PetalPaletteMix.encode(defaultSec))
                            editor.putString(com.petal.browser.ui.theme.PetalPaletteMix.KEY_TERTIARY, com.petal.browser.ui.theme.PetalPaletteMix.encode(defaultTer))
                        }
                        editor.apply()
                        com.petal.browser.ui.theme.PetalThemePrefs.bump()
                        PetalSearchWidgetProvider.updateAllWidgets(context)
                    }
                }
            )

            AnimatedVisibility(
                visible = mixEnabled,
                enter = expandVertically(animationSpec = PetalSpring.calm()) + fadeIn(animationSpec = PetalSpring.calm()),
                exit = shrinkVertically(animationSpec = PetalSpring.calm()) + fadeOut(animationSpec = PetalSpring.calm())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Three live color swatches
                    com.petal.browser.ui.components.PetalMixPreviewStrip()

                    // One-tap harmonious recipes strip
                    com.petal.browser.ui.components.PetalMixRecipesStrip(
                        activeSecondary = mixConfig.secondary,
                        activeTertiary = mixConfig.tertiary,
                        onSelectRecipe = { recipe ->
                            reveal.reveal {
                                themeSp.edit()
                                    .putString(com.petal.browser.ui.theme.PetalPaletteMix.KEY_SECONDARY, com.petal.browser.ui.theme.PetalPaletteMix.encode(recipe.secondary))
                                    .putString(com.petal.browser.ui.theme.PetalPaletteMix.KEY_TERTIARY, com.petal.browser.ui.theme.PetalPaletteMix.encode(recipe.tertiary))
                                    .apply()
                                com.petal.browser.ui.theme.PetalThemePrefs.bump()
                                PetalSearchWidgetProvider.updateAllWidgets(context)
                            }
                        }
                    )

                    // Interactive Component Preview Sandbox
                    com.petal.browser.ui.components.PetalMixComponentPreview()

                    // Secondary & Tertiary Source Rows
                    com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { index, position ->
                        val role = index + 1
                        val src = if (role == 1) mixConfig.secondary else mixConfig.tertiary
                        val customArgb = if (role == 1) mixCustom.secondary else mixCustom.tertiary
                        com.petal.browser.ui.containment.PetalGroupListRow(
                            position = position,
                            onClick = { mixDialogRole = role },
                            leading = {
                                com.petal.browser.ui.containment.PetalGroupIconBadge(
                                    icon = if (role == 1) Icons.Rounded.Palette else Icons.Rounded.Colorize,
                                    variant = if (src != com.petal.browser.ui.theme.PetalPaletteMix.Source.Auto)
                                        com.petal.browser.ui.containment.PetalBadgeVariant.PRIMARY
                                    else com.petal.browser.ui.containment.PetalBadgeVariant.SURFACE_TONAL
                                )
                            },
                            content = {
                                Text(
                                    if (role == 1) "Secondary accent" else "Tertiary highlight",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    com.petal.browser.ui.components.mixSourceLabel(src, customArgb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            if (mixDialogRole != 0) {
                val role = mixDialogRole
                val key = if (role == 1) com.petal.browser.ui.theme.PetalPaletteMix.KEY_SECONDARY else com.petal.browser.ui.theme.PetalPaletteMix.KEY_TERTIARY
                com.petal.browser.ui.components.PetalMixSourceDialog(
                    title = if (role == 1) "Secondary colour from" else "Tertiary colour from",
                    current = if (role == 1) mixConfig.secondary else mixConfig.tertiary,
                    customArgb = if (role == 1) mixCustom.secondary else mixCustom.tertiary,
                    onSelect = { src ->
                        mixDialogRole = 0
                        reveal.reveal {
                            themeSp.edit().putString(key, com.petal.browser.ui.theme.PetalPaletteMix.encode(src)).apply()
                            com.petal.browser.ui.theme.PetalThemePrefs.bump()
                            PetalSearchWidgetProvider.updateAllWidgets(context)
                        }
                    },
                    onPickCustom = {
                        mixDialogRole = 0
                        themeSp.edit().putString(key, com.petal.browser.ui.theme.PetalPaletteMix.encode(com.petal.browser.ui.theme.PetalPaletteMix.Source.Custom)).apply()
                        com.petal.browser.ui.theme.PetalThemePrefs.bump()
                        pickerSlot = role
                    },
                    onDismiss = { mixDialogRole = 0 }
                )
            }

            if (isCustomPalette) {
                val spec = remember(customRevision) { com.petal.browser.ui.theme.PetalCustomPalette.spec(context) }
                com.petal.browser.ui.containment.PetalGroup(rowCount = 3) { index, position ->
                    val (slotTitle, slotColor, slotHint) = when (index) {
                        0 -> Triple("Primary colour", spec.seed, "Seed for the whole palette")
                        1 -> Triple("Secondary colour", spec.secondary, "Auto: derived from the primary")
                        else -> Triple("Tertiary colour", spec.tertiary, "Auto: derived from the primary")
                    }
                    com.petal.browser.ui.containment.PetalGroupListRow(
                        position = position,
                        onClick = { pickerSlot = index },
                        leading = {
                            Box(
                                Modifier.size(40.dp).clip(ExpressiveButtonShape)
                                    .background(if (slotColor != 0) Color(slotColor) else MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (slotColor == 0) Text("Auto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        content = {
                            Text(slotTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (slotColor != 0) "#%06X".format(slotColor and 0xFFFFFF) else slotHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = if (index > 0 && slotColor != 0) {
                            {
                                TextButton(onClick = {
                                    reveal.reveal {
                                        themeSp.edit().putInt(
                                            if (index == 1) com.petal.browser.ui.theme.PetalCustomPalette.KEY_SECONDARY
                                            else com.petal.browser.ui.theme.PetalCustomPalette.KEY_TERTIARY, 0
                                        ).apply()
                                        PetalSearchWidgetProvider.updateAllWidgets(context)
                                    }
                                }) { Text("Reset") }
                            }
                        } else null
                    )
                }
            }


            AppearanceCustomColorPickerDialog(
                slot = pickerSlot,
                reveal = reveal,
                onDismiss = { pickerSlot = -1 }
            )
        }
    }
}
