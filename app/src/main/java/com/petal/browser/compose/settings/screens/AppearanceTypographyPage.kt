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
fun AppearanceTypographyPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val gsFlexSettings by viewModel.gsFlexSettings.collectAsStateWithLifecycle()
    val customFontName by viewModel.customFontName.collectAsStateWithLifecycle()
    val onAppFontChange: (AppFont) -> Unit = viewModel::setAppFont
    val onGsFlexPresetChange: (GSFlexPreset) -> Unit = viewModel::setGsFlexPreset
    val onGsFlexSettingsChange: (com.petal.browser.ui.theme.GSFlexSettings) -> Unit = viewModel::setGsFlexSettings

    var showFontPicker by remember { mutableStateOf(false) }
    // System picker kept as fallback for the Petal picker's "Browse system" button
    val systemFontPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            PetalFontHelper.saveCustomFontUri(context, it)
            onAppFontChange(AppFont.CUSTOM)
        }
    }

    AppearanceSubPageScaffold(
        title = "Typography",
        subtitle = "Fonts, Petal Signature axes & custom font",
        pageSeed = "appearance_typography",
        onBack = onBack,
        modifier = modifier,
        overlay = {
            if (showFontPicker) {
                com.petal.browser.compose.file.PetalFilePickerScreen(
                    mimeTypes = arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "*/*"),
                    onDismissRequest = { showFontPicker = false },
                    onFileSelected = { file ->
                        showFontPicker = false
                        PetalFontHelper.saveCustomFontUri(context, android.net.Uri.fromFile(file))
                        onAppFontChange(AppFont.CUSTOM)
                    },
                    onBrowseSystemFallback = {
                        showFontPicker = false
                        systemFontPicker.launch("*/*")
                    }
                )
            }
        }
    ) {
        PetalSettingsSection(
            title = stringResource(R.string.ui_typography_fonts),
            icon = Icons.Rounded.FontDownload,
            cardId = "appearance_font",
            targetHighlightId = targetHighlightItemId
        ) {
            Text(
                stringResource(R.string.ui_choose_typography_style_or_load),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Font Family Chips
            Text(
                stringResource(R.string.ui_select_font_family),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            com.petal.browser.ui.components.PetalChoiceButtonGroup(
                options = AppFont.values().map { font ->
                    com.petal.browser.ui.components.PetalButtonGroupItem(
                        value = font,
                        label = font.label
                    )
                },
                selected = appFont,
                minHeight = 44.dp,
                scrollableIfCrowded = true,
                onSelect = { font ->
                    if (font == AppFont.CUSTOM) {
                        showFontPicker = true
                    }
                    onAppFontChange(font)
                }
            )

            // Petal Signature Typography Customization (Zenith GS Flex Engine)
            AnimatedVisibility(visible = appFont == AppFont.PETAL, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Live Preview Card
                    val previewTypography = remember(gsFlexSettings) {
                        com.petal.browser.ui.theme.createGsFlexTypography(gsFlexSettings)
                    }
                    var lastNonCustomPreset by remember(gsFlexSettings.preset) {
                        mutableStateOf(if (gsFlexSettings.preset == GSFlexPreset.CUSTOM) GSFlexPreset.PETAL_SIGNATURE else gsFlexSettings.preset)
                    }
                    var selectedLevelTab by remember { mutableIntStateOf(0) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Petal Signature",
                                style = previewTypography.displayLarge.copy(
                                    fontSize = 38.sp,
                                    lineHeight = 42.sp,
                                    letterSpacing = (-1.5).sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Headline Treatment",
                                style = previewTypography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Standard body text for optimized reading experience in Petal browser.",
                                style = previewTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    // Design System Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Design System Presets",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val presetOptions = listOf(
                            com.petal.browser.ui.components.PetalButtonGroupItem(
                                value = GSFlexPreset.PETAL_SIGNATURE,
                                label = "Petal Signature"
                            ),
                            com.petal.browser.ui.components.PetalButtonGroupItem(
                                value = GSFlexPreset.NEO,
                                label = "Neo"
                            ),
                            com.petal.browser.ui.components.PetalButtonGroupItem(
                                value = GSFlexPreset.COMPACT,
                                label = "Impact"
                            ),
                            com.petal.browser.ui.components.PetalButtonGroupItem(
                                value = GSFlexPreset.AIRY,
                                label = "Airy"
                            )
                        )
                        com.petal.browser.ui.components.PetalChoiceButtonGroup(
                            options = presetOptions,
                            selected = if (gsFlexSettings.preset == GSFlexPreset.CUSTOM) lastNonCustomPreset else gsFlexSettings.preset,
                            minHeight = 44.dp,
                            scrollableIfCrowded = true,
                            onSelect = { newPreset ->
                                lastNonCustomPreset = newPreset
                                onGsFlexPresetChange(newPreset)
                                if (gsFlexSettings.preset != GSFlexPreset.CUSTOM) {
                                    onGsFlexSettingsChange(gsFlexSettings.copy(preset = newPreset))
                                }
                            }
                        )
                    }

                    // Custom Variable Toggle
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = "Use Custom Variable",
                        subtitle = "Override preset with your own fine-tuned axes",
                        icon = Icons.Rounded.Tune,
                        checked = gsFlexSettings.preset == GSFlexPreset.CUSTOM,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                        onCheckedChange = { checked ->
                            val newPreset = if (checked) GSFlexPreset.CUSTOM else lastNonCustomPreset
                            onGsFlexPresetChange(newPreset)
                            // Entering Custom starts from the preset that was active, not stale saved axes.
                            val seed = if (checked) com.petal.browser.ui.theme.getPresetFontAxes(lastNonCustomPreset) else null
                            onGsFlexSettingsChange(
                                if (seed != null) gsFlexSettings.copy(
                                    preset = newPreset, display = seed.first, headline = seed.second, body = seed.third
                                ) else gsFlexSettings.copy(preset = newPreset)
                            )
                        }
                    )

                    // Custom Variable Fine-Tuning Section
                    AnimatedVisibility(visible = gsFlexSettings.preset == GSFlexPreset.CUSTOM, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Button(
                                onClick = {
                                    val presetAxes = com.petal.browser.ui.theme.getPresetFontAxes(lastNonCustomPreset)
                                    onGsFlexSettingsChange(
                                        gsFlexSettings.copy(
                                            preset = GSFlexPreset.CUSTOM,
                                            display = presetAxes.first,
                                            headline = presetAxes.second,
                                            body = presetAxes.third
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Copy from Current Preset", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Fine-tune Axes per Level",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            val levelTabs = listOf("Display", "Headline", "Body")
                            com.petal.browser.ui.components.PetalChoiceButtonGroup(
                                labels = levelTabs,
                                selectedIndex = selectedLevelTab,
                                onSelectIndex = { selectedLevelTab = it },
                                minHeight = 44.dp,
                                scrollableIfCrowded = false
                            )

                            val currentAxes = when (selectedLevelTab) {
                                0 -> gsFlexSettings.display
                                1 -> gsFlexSettings.headline
                                else -> gsFlexSettings.body
                            }

                            fun updateAxes(newAxes: com.petal.browser.ui.theme.FontAxes) {
                                val updated = when (selectedLevelTab) {
                                    0 -> gsFlexSettings.copy(preset = GSFlexPreset.CUSTOM, display = newAxes)
                                    1 -> gsFlexSettings.copy(preset = GSFlexPreset.CUSTOM, headline = newAxes)
                                    else -> gsFlexSettings.copy(preset = GSFlexPreset.CUSTOM, body = newAxes)
                                }
                                onGsFlexSettingsChange(updated)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                GSFlexVariableSlider(
                                    label = "Weight",
                                    value = currentAxes.weight,
                                    range = 100f..1000f,
                                    formatter = { v -> String.format(java.util.Locale.US, "%.0f", v) },
                                    onValueChange = { newWeight -> updateAxes(currentAxes.copy(weight = newWeight)) }
                                )
                                GSFlexVariableSlider(
                                    label = "Width",
                                    value = currentAxes.width,
                                    range = 25f..150f,
                                    formatter = { v -> String.format(java.util.Locale.US, "%.0f", v) },
                                    onValueChange = { newWidth -> updateAxes(currentAxes.copy(width = newWidth)) }
                                )
                                GSFlexVariableSlider(
                                    label = "Optical Size",
                                    value = currentAxes.opsz,
                                    range = 6f..72f,
                                    formatter = { v -> String.format(java.util.Locale.US, "%.0f pt", v) },
                                    onValueChange = { newOpsz -> updateAxes(currentAxes.copy(opsz = newOpsz)) }
                                )
                                GSFlexVariableSlider(
                                    label = "Grade",
                                    value = currentAxes.grade,
                                    range = -200f..200f,
                                    formatter = { v -> String.format(java.util.Locale.US, "%+.0f", v) },
                                    onValueChange = { newGrade -> updateAxes(currentAxes.copy(grade = newGrade)) }
                                )
                                GSFlexVariableSlider(
                                    label = "Slant",
                                    value = currentAxes.slant,
                                    range = -10f..0f,
                                    formatter = { v -> String.format(java.util.Locale.US, "%.0f°", v) },
                                    onValueChange = { newSlant -> updateAxes(currentAxes.copy(slant = newSlant)) }
                                )
                                GSFlexVariableSlider(
                                    label = "Roundness",
                                    value = currentAxes.roundness,
                                    range = 0f..100f,
                                    formatter = { v -> String.format(java.util.Locale.US, "%.0f%%", v) },
                                    onValueChange = { newRoundness -> updateAxes(currentAxes.copy(roundness = newRoundness)) }
                                )
                            }
                        }
                    }
                }
            }

            // Nunito Typography Preview (Zenith Rounded Variable)
            AnimatedVisibility(visible = appFont == AppFont.NUNITO, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Nunito",
                            style = com.petal.browser.ui.theme.NunitoTypography.displayLarge.copy(
                                fontSize = 38.sp,
                                lineHeight = 42.sp,
                                letterSpacing = (-1.0).sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Zenith Soft Geometric Rounded",
                            style = com.petal.browser.ui.theme.NunitoTypography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Warm, balanced, rounded typography inspired by the Zenith design language.",
                            style = com.petal.browser.ui.theme.NunitoTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // System Typography Preview (Device Platform Default)
            AnimatedVisibility(visible = appFont == AppFont.SYSTEM, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "System Default",
                            style = com.petal.browser.ui.theme.SystemTypography.displayLarge.copy(
                                fontSize = 38.sp,
                                lineHeight = 42.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Platform Native Typescale",
                            style = com.petal.browser.ui.theme.SystemTypography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Uses your device's system font and follows OS accessibility typography settings.",
                            style = com.petal.browser.ui.theme.SystemTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // Custom Font File Picker UI (Containment Surface & Icon Badge)
            AnimatedVisibility(visible = appFont == AppFont.CUSTOM, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                Surface(
                    shape = com.petal.browser.ui.containment.petalGroupShape(com.petal.browser.ui.containment.PetalGroupPosition.SINGLE),
                    color = com.petal.browser.ui.containment.petalGroupSurfaceColor(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            icon = Icons.Rounded.FontDownload,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ui_custom_font_file),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = customFontName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showFontPicker = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_browse))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun GSFlexVariableSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatter: (Float) -> String = { v -> String.format(java.util.Locale.US, "%.0f", v) },
    onValueChange: (Float) -> Unit
) {
    // The slider owns its value while dragging. Previously every frame wrote all 19 font prefs,
    // rebuilt the app typography and waited for the prefs flow to hand the value back, so the
    // thumb lagged, jumped back and fought the finger. Now it stays put and commits (debounced)
    // plus once when the finger lifts.
    var local by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(value) }
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    androidx.compose.runtime.LaunchedEffect(value) { if (!dragging) local = value }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { local }
            .drop(1)
            .debounce(120)
            .collect { if (dragging) latest(it) }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = formatter(local),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
        }
        com.petal.browser.ui.components.PetalSlider(
            value = local,
            onValueChange = { dragging = true; local = it },
            onValueChangeFinished = { dragging = false; latest(local) },
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
