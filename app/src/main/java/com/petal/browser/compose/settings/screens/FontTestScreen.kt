package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.components.PetalButtonGroupItem
import com.petal.browser.ui.components.PetalChoiceButtonGroup
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.theme.FontAxes
import com.petal.browser.ui.theme.FontState
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.PetalFontAccentColors
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.ui.theme.VariableFontFactory
import com.petal.browser.ui.theme.withFontAccent

/**
 * Interactive variable typography test sandbox modeled after Zenith's FontTestScreen,
 * integrated into Petal's settings navigation stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontTestScreen(
    onBack: () -> Unit,
    initialFontState: FontState,
    onApplyToApp: (FontState) -> Unit,
    modifier: Modifier = Modifier
) {
    var sandboxSettings by remember(initialFontState) { mutableStateOf(initialFontState.gsFlexSettings) }
    var customText by rememberSaveable { mutableStateOf("Petal") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val colorScheme = MaterialTheme.colorScheme
    val accent = remember(initialFontState.accentMode, colorScheme) {
        PetalFontAccentColors.resolve(initialFontState.accentMode, colorScheme)
    }
    val dynamicTypography = remember(sandboxSettings, accent) {
        VariableFontFactory.createGsFlexTypography(sandboxSettings).withFontAccent(accent)
    }

    val presetItems = remember {
        listOf(
            GSFlexPreset.PETAL_SIGNATURE,
            GSFlexPreset.NEO,
            GSFlexPreset.COMPACT,
            GSFlexPreset.AIRY
        ).map { PetalButtonGroupItem(value = it, label = it.label) }
    }

    AppearanceSubPageScaffold(
        title = "Font Test Lab",
        subtitle = "Sandbox for live preview text, scale & presets",
        pageSeed = "appearance_font_test",
        onBack = onBack,
        modifier = modifier
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Custom") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Scale") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Presets") })
        }

        when (selectedTab) {
            0 -> {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text("Interactive Text") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp)
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = customText.ifBlank { "Petal" },
                            style = dynamicTypography.displayLarge.copy(
                                fontSize = 56.sp,
                                lineHeight = 62.sp,
                                letterSpacing = (-1.5).sp
                            ),
                            color = accent.headingOr(colorScheme.onSurface)
                        )
                        Text(
                            text = "Custom Editorial Treatment",
                            style = dynamicTypography.titleMedium,
                            color = accent.labelOr(colorScheme.primary)
                        )
                        Text(
                            text = "Adaptive variable weight, width, optical size, grade, slant & roundness.",
                            style = dynamicTypography.bodyMedium,
                            color = accent.bodyOr(colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            1 -> {
                Text(
                    "Dynamic Typescale",
                    style = PetalText.sectionHeader,
                    color = colorScheme.primary
                )
                ScaleSampleRow("Display Large", dynamicTypography.displayLarge, accent.headingOr(colorScheme.onSurface))
                ScaleSampleRow("Headline Medium", dynamicTypography.headlineMedium, accent.headingOr(colorScheme.onSurface))
                ScaleSampleRow("Title Large", dynamicTypography.titleLarge, accent.headingOr(colorScheme.onSurface))
                ScaleSampleRow("Body Large", dynamicTypography.bodyLarge, accent.bodyOr(colorScheme.onSurface))
                ScaleSampleRow("Label Small", dynamicTypography.labelSmall, accent.labelOr(colorScheme.onSurfaceVariant))
            }

            2 -> {
                Text(
                    "Signature Presets",
                    style = PetalText.sectionHeader,
                    color = colorScheme.primary
                )
                PetalChoiceButtonGroup(
                    options = presetItems,
                    selected = sandboxSettings.preset,
                    minHeight = 44.dp,
                    scrollableIfCrowded = true,
                    onSelect = { preset ->
                        val axes = VariableFontFactory.getPresetFontAxes(preset)
                        sandboxSettings = sandboxSettings.copy(
                            preset = preset,
                            display = axes.first,
                            headline = axes.second,
                            body = axes.third
                        )
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                sandboxSettings.preset.label,
                                style = dynamicTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.primary
                            )
                        }
                        Text(
                            "Material 3 Expressive typography preset tuned for Petal across headings, labels, and reading surfaces.",
                            style = dynamicTypography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                ScaleSampleRow("Display (Hero)", dynamicTypography.displayMedium, accent.headingOr(colorScheme.onSurface))
                ScaleSampleRow("Headline (Section)", dynamicTypography.headlineMedium, accent.headingOr(colorScheme.onSurface))
                ScaleSampleRow("Body (Reading)", dynamicTypography.bodyLarge, accent.bodyOr(colorScheme.onSurface))
            }
        }

        if (selectedTab != 2) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Global Axis Override (All Tiers)",
                style = PetalText.sectionHeader,
                color = colorScheme.secondary
            )

            val activeAxes = sandboxSettings.display
            fun updateGlobal(transform: (FontAxes) -> FontAxes) {
                sandboxSettings = sandboxSettings.copy(
                    preset = GSFlexPreset.CUSTOM,
                    display = transform(sandboxSettings.display),
                    headline = transform(sandboxSettings.headline),
                    body = transform(sandboxSettings.body)
                )
            }

            FontAxes.Axis.entries.forEach { axis ->
                GSFlexVariableSlider(
                    label = axis.label,
                    value = axis.get(activeAxes),
                    range = axis.range,
                    formatter = axis::format,
                    onDrag = { v -> updateGlobal { axis.withValue(it, v) } },
                    onCommit = { v -> updateGlobal { axis.withValue(it, v) } }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val sig = VariableFontFactory.getPresetFontAxes(GSFlexPreset.PETAL_SIGNATURE)
                    sandboxSettings = sandboxSettings.copy(
                        preset = GSFlexPreset.PETAL_SIGNATURE,
                        display = sig.first,
                        headline = sig.second,
                        body = sig.third
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset Sandbox")
            }

            Button(
                onClick = {
                    onApplyToApp(initialFontState.copy(gsFlexSettings = sandboxSettings))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apply to Petal")
            }
        }
    }
}

@Composable
private fun ScaleSampleRow(
    label: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = PetalText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Pack my box with five dozen liquor jugs",
                style = style,
                color = color
            )
        }
    }
}
