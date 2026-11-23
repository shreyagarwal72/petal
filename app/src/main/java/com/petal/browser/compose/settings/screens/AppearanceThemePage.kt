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
fun AppearanceThemePage(
    reveal: ThemeReveal,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startOnAdvanced: Boolean = false,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val colorStyle by viewModel.colorStyle.collectAsStateWithLifecycle()
    val paletteId by viewModel.paletteId.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val amoledMode by viewModel.amoledMode.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
    val onColorStyleChange: (ColorStyle) -> Unit = viewModel::setColorStyle
    val onPaletteIdChange: (String) -> Unit = viewModel::setPaletteId
    val onDynamicColorChange: (Boolean) -> Unit = viewModel::setDynamicColor
    val onAmoledModeChange: (Boolean) -> Unit = viewModel::setAmoledMode
    val onThemeConfigChange: (ThemeConfig) -> Unit = viewModel::setThemeConfig
    val isDarkTheme = when (themeConfig) {
        ThemeConfig.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
    }

    // Advanced colors is a real nested sub-page: this page stays composed (state kept) behind it and
    // the sub-page owns its own predictive back, so back returns here instead of to the hub.
    var showAdvanced by rememberSaveable { mutableStateOf(startOnAdvanced) }
    com.petal.browser.predictive.PetalSubPageHost(
        visible = showAdvanced,
        onBack = { showAdvanced = false },
        modifier = modifier,
        subPage = {
            AppearanceAdvancedColorsPage(
                reveal = reveal,
                onBack = { showAdvanced = false },
                targetHighlightItemId = targetHighlightItemId
            )
        },
        parent = {
            AppearanceSubPageScaffold(
                title = "Theme & Colors",
                subtitle = "Mode, palettes, Material You & AMOLED",
                pageSeed = "appearance_theme",
                onBack = onBack
            ) {
                PetalSettingsSection(
                    title = stringResource(R.string.ui_theme_color_palette),
                    iconRes = com.petal.browser.R.drawable.brightness_medium_filled,
                    cardId = "appearance_theme",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        stringResource(R.string.ui_customize_app_color_schemes_dynamic),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Theme Mode Chips
                    Text(
                        stringResource(R.string.ui_theme_mode),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    com.petal.browser.ui.components.PetalChoiceButtonGroup(
                        options = listOf(
                            com.petal.browser.ui.components.PetalButtonGroupItem(ThemeConfig.FOLLOW_SYSTEM, "System", icon = Icons.Rounded.BrightnessAuto),
                            com.petal.browser.ui.components.PetalButtonGroupItem(ThemeConfig.LIGHT, "Light", icon = Icons.Rounded.LightMode),
                            com.petal.browser.ui.components.PetalButtonGroupItem(ThemeConfig.DARK, "Dark", icon = Icons.Rounded.DarkMode),
                        ),
                        selected = themeConfig,
                        minHeight = 44.dp,
                        onSelect = { config ->
                            reveal.reveal {
                                onThemeConfigChange(config)
                                when (config) {
                                    ThemeConfig.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                    ThemeConfig.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                    ThemeConfig.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                }
                            }
                        }
                    )
                    // Custom palette state (ColorBlendr-style: seed + optional secondary/tertiary)
                    val customRevision = com.petal.browser.ui.theme.PetalThemePrefs.revision
                    var pickerSlot by remember { mutableStateOf(-1) } // 0 primary, 1 secondary, 2 tertiary

                    // Preset Color Palettes
                    Text(
                        stringResource(R.string.ui_preset_color_palettes),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val paletteScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = paletteScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(paletteScrollState)
                        ) {
                            PetalPalettes.forEach { pal ->
                                val isSelected = paletteId == pal.id && !dynamicColor
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(ExpressiveButtonShape)
                                        .background(pal.seed)
                                        .clickable {
                                            reveal.reveal {
                                                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                                                    .edit().putBoolean("sp_palette_manually_selected", true).apply()
                                                onPaletteIdChange(pal.id)
                                                onDynamicColorChange(false)
                                                PetalSearchWidgetProvider.updateAllWidgets(context)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, contentDescription = pal.label, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            // Custom palette tile: rainbow ring with + until the user picks colours.
                            val customSelected = paletteId == com.petal.browser.ui.theme.CUSTOM_PALETTE_ID && !dynamicColor
                            val customSpec = remember(customRevision) { com.petal.browser.ui.theme.PetalCustomPalette.spec(context) }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(ExpressiveButtonShape)
                                    .background(
                                        if (customSelected) Brush.linearGradient(listOf(Color(customSpec.seed), Color(customSpec.seed)))
                                        else Brush.sweepGradient(listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFF5252)))
                                    )
                                    .clickable {
                                        if (customSelected) {
                                            pickerSlot = 0
                                        } else {
                                            reveal.reveal {
                                                androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                                                    .edit().putBoolean("sp_palette_manually_selected", true).apply()
                                                onPaletteIdChange(com.petal.browser.ui.theme.CUSTOM_PALETTE_ID)
                                                onDynamicColorChange(false)
                                                PetalSearchWidgetProvider.updateAllWidgets(context)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (customSelected) Icons.Rounded.Edit else Icons.Rounded.Add,
                                    contentDescription = "Custom palette",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Palette Style Swatches
                    Text(
                        stringResource(R.string.ui_palette_harmony_style),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val isCustomPalette = paletteId == com.petal.browser.ui.theme.CUSTOM_PALETTE_ID && !dynamicColor
                    val currentPalette = remember(paletteId) {
                        if (paletteId == com.petal.browser.ui.theme.CUSTOM_PALETTE_ID) PetalPalettes.first()
                        else PetalPalettes.firstOrNull { it.id == paletteId } ?: PetalPalettes.first()
                    }
                    val isEffectiveAmoled = isDarkTheme && amoledMode
                    val activeBaseScheme = remember(currentPalette, dynamicColor, isDarkTheme, isEffectiveAmoled, context) {
                        val base = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                        } else {
                            if (isDarkTheme) currentPalette.dark else currentPalette.light
                        }
                        if (isDarkTheme && isEffectiveAmoled) base.applyAmoled() else base
                    }
                    val visibleStyles = remember(isCustomPalette) { ColorStyle.entries.filter { isCustomPalette || !it.customOnly } }
                    val styleSchemes = remember(activeBaseScheme, isCustomPalette, customRevision, isDarkTheme, isEffectiveAmoled) {
                        visibleStyles.associateWith { style ->
                            if (isCustomPalette) {
                                val gen = com.petal.browser.ui.theme.PetalCustomPalette.scheme(
                                    com.petal.browser.ui.theme.PetalCustomPalette.spec(context), style, isDarkTheme
                                )
                                if (isDarkTheme && isEffectiveAmoled) gen.applyAmoled() else gen
                            } else activeBaseScheme.applyStyle(style)
                        }
                    }
                    val activePreviewScheme = styleSchemes[colorStyle] ?: activeBaseScheme

                    // Harmony style picker (WiFi Weakspot design, Liquid Glass aware)
                    PetalColorStylePicker(
                        styles = visibleStyles,
                        schemes = styleSchemes,
                        fallbackScheme = activePreviewScheme,
                        selected = colorStyle,
                        dark = isDarkTheme,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        onSelect = { style ->
                            reveal.reveal {
                                onColorStyleChange(style)
                                PetalSearchWidgetProvider.updateAllWidgets(context)
                            }
                        }
                    )

                    // Color Style Description Card (Containment Surface)
                    Surface(
                        shape = com.petal.browser.ui.containment.petalGroupShape(com.petal.browser.ui.containment.PetalGroupPosition.SINGLE),
                        color = com.petal.browser.ui.containment.petalGroupSurfaceColor(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = colorStyle.label,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = colorStyle.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Material You Dynamic Color Toggle
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_material_you_dynamic_color),
                        subtitle = stringResource(R.string.ui_adapt_accent_colors_from_your),
                        icon = Icons.Rounded.ColorLens,
                        checked = dynamicColor,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                        onCheckedChange = { newValue ->
                            reveal.reveal {
                                onDynamicColorChange(newValue)
                                PetalSearchWidgetProvider.updateAllWidgets(context)
                            }
                        }
                    )
                    // AMOLED Black Toggle
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_amoled_black_dark_mode),
                        subtitle = if (isDarkTheme) "Pure black background ladder for OLED displays" else "Disabled in Light Mode (Requires Dark theme)",
                        icon = Icons.Rounded.DarkMode,
                        checked = amoledMode && isDarkTheme,
                        enabled = isDarkTheme,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                        onCheckedChange = { newValue ->
                            reveal.reveal {
                                onAmoledModeChange(newValue)
                                PetalSearchWidgetProvider.updateAllWidgets(context)
                            }
                        }
                    )

                    // Custom palette editor lives on its own page.
                    com.petal.browser.ui.containment.PetalGroupNavigationRow(
                        title = "Custom palette colors",
                        subtitle = "Customize primary seed and role accents",
                        position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                        onClick = { showAdvanced = true },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) }
                    )

                    com.petal.browser.compose.settings.screens.AppearanceCustomColorPickerDialog(
                        slot = pickerSlot,
                        reveal = reveal,
                        onDismiss = { pickerSlot = -1 }
                    )
                }
            }
        }
    )
}

/**
 * Colour picker for the custom palette slots (0 primary, 1 secondary, 2 tertiary). Shown while
 * [slot] is 0..2; [onDismiss] must reset the caller's slot state to -1.
 */
@Composable
internal fun AppearanceCustomColorPickerDialog(
    slot: Int,
    reveal: ThemeReveal,
    onDismiss: () -> Unit
) {
    if (slot < 0) return
    val context = LocalContext.current
    val spec = com.petal.browser.ui.theme.PetalCustomPalette.spec(context)
    val initial = when (slot) {
        0 -> spec.seed
        1 -> if (spec.secondary != 0) spec.secondary else spec.seed
        else -> if (spec.tertiary != 0) spec.tertiary else spec.seed
    }
    com.petal.browser.ui.components.PetalColorPickerDialog(
        initialColor = Color(initial),
        title = when (slot) { 0 -> "Primary colour"; 1 -> "Secondary colour"; else -> "Tertiary colour" },
        onDismiss = onDismiss,
        onConfirm = { picked ->
            onDismiss()
            reveal.reveal {
                PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(
                    when (slot) {
                        0 -> com.petal.browser.ui.theme.PetalCustomPalette.KEY_SEED
                        1 -> com.petal.browser.ui.theme.PetalCustomPalette.KEY_SECONDARY
                        else -> com.petal.browser.ui.theme.PetalCustomPalette.KEY_TERTIARY
                    },
                    picked.toArgb() or (0xFF shl 24)
                ).apply()
                PetalSearchWidgetProvider.updateAllWidgets(context)
            }
        }
    )
}


