package com.petal.browser.compose.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChromeReaderMode
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.compose.file.PetalFilePickerScreen
import com.petal.browser.compose.settings.viewmodel.AppearanceSettingsViewModel
import com.petal.browser.predictive.PetalSubPageHost
import com.petal.browser.ui.components.PetalButtonGroupItem
import com.petal.browser.ui.components.PetalChoiceButtonGroup
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupNavigationRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.FontAccentMode
import com.petal.browser.ui.theme.FontAxes
import com.petal.browser.ui.theme.FontState
import com.petal.browser.ui.theme.FontTier
import com.petal.browser.ui.theme.GSFlexPreset
import com.petal.browser.ui.theme.PetalFontAccentColors
import com.petal.browser.ui.theme.PetalFontHelper
import com.petal.browser.ui.theme.PetalFontPrefs
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.ui.theme.VariableFontFactory
import com.petal.browser.ui.theme.withFontAccent
import com.petal.browser.view.PetalToast
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppearanceTypographyPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val fontState by viewModel.fontState.collectAsStateWithLifecycle()

    var showFontPicker by remember { mutableStateOf(false) }
    var showFontTest by rememberSaveable { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    // Drag-local preview overrides so moving any slider previews immediately in the card
    // while single-key debounced writes keep the rest of the UI responsive.
    var livePreviewOverride by remember(fontState) { mutableStateOf<FontState?>(null) }
    val effectivePreviewState = livePreviewOverride ?: fontState

    val systemFontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (PetalFontHelper.saveCustomFontUri(context, it) != null) {
                viewModel.setAppFont(AppFont.CUSTOM)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PetalSubPageHost(
            visible = showFontTest,
            onBack = { showFontTest = false },
            subPage = {
                FontTestScreen(
                    onBack = { showFontTest = false },
                    initialFontState = fontState,
                    onApplyToApp = { chosen ->
                        viewModel.setGsFlexSettings(chosen.gsFlexSettings)
                        viewModel.setAppFont(AppFont.PETAL)
                        PetalToast.show(context, "Applied font settings")
                    }
                )
            },
            parent = {
                AppearanceSubPageScaffold(
                    title = "Typography",
                    subtitle = "Fonts, variable axes & font accents",
                    pageSeed = "appearance_typography",
                    onBack = onBack,
                    overlay = {
                        if (showFontPicker) {
                            PetalFilePickerScreen(
                                mimeTypes = arrayOf(
                                    "font/ttf",
                                    "font/otf",
                                    "application/x-font-ttf",
                                    "application/x-font-otf",
                                    "*/*"
                                ),
                                onDismissRequest = { showFontPicker = false },
                                onFileSelected = { file ->
                                    showFontPicker = false
                                    if (PetalFontHelper.saveCustomFontUri(context, Uri.fromFile(file)) != null) {
                                        viewModel.setAppFont(AppFont.CUSTOM)
                                    }
                                },
                                onBrowseSystemFallback = {
                                    showFontPicker = false
                                    systemFontPicker.launch("*/*")
                                }
                            )
                        }
                    }
                ) {
                    // ── Shared Live Typography Preview Card (All 4 Fonts + Accent) ──
                    val colorScheme = MaterialTheme.colorScheme
                    val previewAccent = remember(effectivePreviewState.accentMode, colorScheme) {
                        PetalFontAccentColors.resolve(effectivePreviewState.accentMode, colorScheme)
                    }
                    val previewTypography = remember(effectivePreviewState, previewAccent) {
                        VariableFontFactory.createTypography(effectivePreviewState).withFontAccent(previewAccent)
                    }

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MaterialTheme(typography = previewTypography) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(effectivePreviewState.appFont.label)
                                                if (effectivePreviewState.appFont == AppFont.PETAL) {
                                                    append(" · ")
                                                    append(effectivePreviewState.gsFlexSettings.preset.label)
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "LIVE PREVIEW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = previewAccent.labelOr(colorScheme.onSurfaceVariant)
                                    )
                                }

                                Text(
                                    text = "The quick brown fox jumps over the lazy dog",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = previewAccent.headingOr(colorScheme.onSurface)
                                )
                                Text(
                                    text = "Expressive browser headings & sharp contrast",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = previewAccent.headingOr(colorScheme.onSurface)
                                )
                                Text(
                                    text = "Clean, readable body typography across pages, dialogs, settings, and widgets. Numbers 0123456789.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = previewAccent.bodyOr(colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }

                    // ── Font Family Selector ───────────────────────────────────────
                    PetalSettingsSection(
                        title = stringResource(R.string.ui_typography_fonts),
                        icon = Icons.Rounded.FontDownload,
                        cardId = "appearance_font",
                        targetHighlightId = targetHighlightItemId
                    ) {
                        Text(
                            stringResource(R.string.ui_choose_typography_style_or_load),
                            style = PetalText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            stringResource(R.string.ui_select_font_family),
                            style = PetalText.sectionHeader,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        PetalChoiceButtonGroup(
                            options = AppFont.entries.map { font ->
                                PetalButtonGroupItem(value = font, label = font.label)
                            },
                            selected = fontState.appFont,
                            minHeight = 44.dp,
                            scrollableIfCrowded = true,
                            onSelect = { font ->
                                livePreviewOverride = null
                                if (font == AppFont.CUSTOM &&
                                    PetalFontHelper.resolveReadableCustomFontFile(fontState.customFont) == null
                                ) {
                                    showFontPicker = true
                                } else {
                                    viewModel.setAppFont(font)
                                }
                            }
                        )

                        // ── 1. Google Sans Flex / Petal Signature ──────────────────
                        AnimatedVisibility(
                            visible = fontState.appFont == AppFont.PETAL,
                            enter = fadeIn(PetalSpring.calm()) + expandIn(PetalSpring.calm()),
                            exit = fadeOut(PetalSpring.calm()) + shrinkOut(PetalSpring.calm())
                        ) {
                            PetalSignatureCustomizer(
                                fontState = fontState,
                                targetHighlightItemId = targetHighlightItemId,
                                onPreviewChange = { livePreviewOverride = it },
                                onSelectPreset = { preset ->
                                    livePreviewOverride = null
                                    viewModel.setGsFlexPreset(preset)
                                },
                                onResetToPreset = { preset ->
                                    livePreviewOverride = null
                                    viewModel.resetGsFlexToPreset(preset)
                                },
                                onToggleCustomMode = { checked, fallbackPreset ->
                                    livePreviewOverride = null
                                    if (checked) {
                                        val seed = VariableFontFactory.getPresetFontAxes(fallbackPreset)
                                        viewModel.setGsFlexSettings(
                                            fontState.gsFlexSettings.copy(
                                                preset = GSFlexPreset.CUSTOM,
                                                display = seed.first,
                                                headline = seed.second,
                                                body = seed.third
                                            )
                                        )
                                    } else {
                                        viewModel.setGsFlexPreset(fallbackPreset)
                                    }
                                },
                                onCopyPreset = { preset ->
                                    livePreviewOverride = null
                                    val seed = VariableFontFactory.getPresetFontAxes(preset)
                                    viewModel.setGsFlexSettings(
                                        fontState.gsFlexSettings.copy(
                                            preset = GSFlexPreset.CUSTOM,
                                            display = seed.first,
                                            headline = seed.second,
                                            body = seed.third
                                        )
                                    )
                                },
                                onDragTierAxis = { tier, axis, value ->
                                    viewModel.setGsFlexAxis(tier, axis, value, debounce = true)
                                },
                                onDragAllAxis = { axis, value ->
                                    viewModel.setAllGsFlexAxes(axis, value, debounce = true)
                                },
                                onCommitTierAxis = { tier, axis, value ->
                                    livePreviewOverride = null
                                    viewModel.setGsFlexAxis(tier, axis, value, debounce = false)
                                },
                                onCommitAllAxis = { axis, value ->
                                    livePreviewOverride = null
                                    viewModel.setAllGsFlexAxes(axis, value, debounce = false)
                                }
                            )
                        }

                        // ── 2. Nunito ──────────────────────────────────────────────
                        AnimatedVisibility(
                            visible = fontState.appFont == AppFont.NUNITO,
                            enter = fadeIn(PetalSpring.calm()) + expandIn(PetalSpring.calm()),
                            exit = fadeOut(PetalSpring.calm()) + shrinkOut(PetalSpring.calm())
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    shape = petalGroupShape(PetalGroupPosition.SINGLE),
                                    color = petalGroupSurfaceColor(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PetalGroupIconBadge(
                                            icon = Icons.Rounded.AutoAwesome,
                                            container = MaterialTheme.colorScheme.secondaryContainer,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                "Nunito Active",
                                                style = PetalText.cardTitle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "Soft geometric rounded variable typeface (wght 100–1000).",
                                                style = PetalText.cardBody,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                GSFlexVariableSlider(
                                    label = "Base Weight",
                                    value = (livePreviewOverride?.nunitoWeight ?: fontState.nunitoWeight).toFloat(),
                                    range = FontAxes.NUNITO_WEIGHT_RANGE,
                                    formatter = { "${it.roundToInt()}" },
                                    onDrag = { w ->
                                        livePreviewOverride = fontState.copy(nunitoWeight = w.roundToInt())
                                    },
                                    onCommit = { w ->
                                        livePreviewOverride = null
                                        viewModel.setNunitoWeight(w.roundToInt())
                                    }
                                )
                            }
                        }

                        // ── 3. System Default ──────────────────────────────────────
                        AnimatedVisibility(
                            visible = fontState.appFont == AppFont.SYSTEM,
                            enter = fadeIn(PetalSpring.calm()) + expandIn(PetalSpring.calm()),
                            exit = fadeOut(PetalSpring.calm()) + shrinkOut(PetalSpring.calm())
                        ) {
                            Surface(
                                shape = petalGroupShape(PetalGroupPosition.SINGLE),
                                color = petalGroupSurfaceColor(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PetalGroupIconBadge(
                                        icon = Icons.Rounded.FontDownload,
                                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            "System Default",
                                            style = PetalText.cardTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Follows Android OS typography and accessibility scaling.",
                                            style = PetalText.cardBody,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // ── 4. Custom TTF / OTF Font ───────────────────────────────
                        AnimatedVisibility(
                            visible = fontState.appFont == AppFont.CUSTOM,
                            enter = fadeIn(PetalSpring.calm()) + expandIn(PetalSpring.calm()),
                            exit = fadeOut(PetalSpring.calm()) + shrinkOut(PetalSpring.calm())
                        ) {
                            CustomFontCustomizer(
                                fontState = fontState,
                                onBrowseClick = { showFontPicker = true },
                                onPreviewChange = { livePreviewOverride = it },
                                onDragTierAxis = { tier, axis, value ->
                                    viewModel.setCustomFontAxis(tier, axis, value, debounce = true)
                                },
                                onDragAllAxis = { axis, value ->
                                    viewModel.setAllCustomFontAxes(axis, value, debounce = true)
                                },
                                onCommitTierAxis = { tier, axis, value ->
                                    livePreviewOverride = null
                                    viewModel.setCustomFontAxis(tier, axis, value, debounce = false)
                                },
                                onCommitAllAxis = { axis, value ->
                                    livePreviewOverride = null
                                    viewModel.setAllCustomFontAxes(axis, value, debounce = false)
                                },
                                onResetCustomAxes = {
                                    livePreviewOverride = null
                                    viewModel.setCustomFontSettings(
                                        com.petal.browser.ui.theme.CustomFontSettings()
                                    )
                                }
                            )
                        }
                    }

                    // ── Font Accent Section (Phase 4) ──────────────────────────────
                    PetalSettingsSection(
                        title = "Font Accent",
                        icon = Icons.Rounded.ColorLens,
                        cardId = "appearance_font_accent",
                        targetHighlightId = targetHighlightItemId
                    ) {
                        Text(
                            "Apply your active theme accent color to typography. Contrast against surfaces is checked automatically (min 4.5:1).",
                            style = PetalText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PetalChoiceButtonGroup(
                            options = FontAccentMode.entries.map { mode ->
                                PetalButtonGroupItem(value = mode, label = mode.label)
                            },
                            selected = fontState.accentMode,
                            minHeight = 42.dp,
                            scrollableIfCrowded = true,
                            onSelect = { mode ->
                                livePreviewOverride = null
                                viewModel.setFontAccentMode(mode)
                            }
                        )
                    }

                    // ── Reader & Tools Section (Phase 5 & 6) ───────────────────────
                    PetalSettingsSection(
                        title = "Tools & Compatibility",
                        icon = Icons.Rounded.Science,
                        cardId = "appearance_font_test",
                        targetHighlightId = targetHighlightItemId
                    ) {
                        PetalSettingsToggleRow(
                            title = "Use app font in Reader Mode",
                            subtitle = "Reader's System font option follows your selected Petal font",
                            icon = Icons.Rounded.ChromeReaderMode,
                            checked = fontState.readerFollowsAppFont,
                            position = PetalGroupPosition.TOP,
                            onCheckedChange = viewModel::setReaderFollowsAppFont
                        )

                        PetalGroupNavigationRow(
                            title = "Font Test Lab",
                            subtitle = "Edit custom sample text, inspect typescale & test presets",
                            position = PetalGroupPosition.MIDDLE,
                            onClick = { showFontTest = true },
                            leadingIcon = { Icon(Icons.Rounded.Science, contentDescription = null) }
                        )

                        Surface(
                            shape = petalGroupShape(PetalGroupPosition.BOTTOM),
                            color = petalGroupSurfaceColor(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            val json = PetalFontPrefs.exportJson(sp).toString(2)
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Petal Font Config", json))
                                            PetalToast.show(context, "Copied font configuration JSON")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Export JSON")
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                            importJsonText = clip
                                            showImportJsonDialog = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Import JSON")
                                    }
                                }

                                FilledTonalButton(
                                    onClick = {
                                        livePreviewOverride = null
                                        viewModel.resetAllFontSettings()
                                        PetalToast.show(context, "All font settings reset to defaults")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reset All Font Settings")
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showImportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = { Text("Import Font Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste previously exported Petal font configuration JSON.",
                        style = PetalText.dialogBody
                    )
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        label = { Text("Font JSON") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = try {
                            PetalFontPrefs.importJson(sp, JSONObject(importJsonText))
                        } catch (_: Throwable) {
                            false
                        }
                        if (ok) {
                            showImportJsonDialog = false
                            PetalToast.show(context, "Imported font configuration")
                        } else {
                            PetalToast.show(context, "Invalid font configuration JSON")
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportJsonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PetalSignatureCustomizer(
    fontState: FontState,
    targetHighlightItemId: String?,
    onPreviewChange: (FontState) -> Unit,
    onSelectPreset: (GSFlexPreset) -> Unit,
    onResetToPreset: (GSFlexPreset) -> Unit,
    onToggleCustomMode: (Boolean, GSFlexPreset) -> Unit,
    onCopyPreset: (GSFlexPreset) -> Unit,
    onDragTierAxis: (FontTier, FontAxes.Axis, Float) -> Unit,
    onDragAllAxis: (FontAxes.Axis, Float) -> Unit,
    onCommitTierAxis: (FontTier, FontAxes.Axis, Float) -> Unit,
    onCommitAllAxis: (FontAxes.Axis, Float) -> Unit
) {
    val gs = fontState.gsFlexSettings
    var lastNonCustomPreset by remember(gs.preset) {
        mutableStateOf(if (gs.preset == GSFlexPreset.CUSTOM) GSFlexPreset.PETAL_SIGNATURE else gs.preset)
    }
    var selectedTier by rememberSaveable { mutableStateOf(FontTier.DISPLAY) }
    var globalOverride by rememberSaveable { mutableStateOf(false) }

    val presetItems = remember {
        listOf(
            GSFlexPreset.PETAL_SIGNATURE,
            GSFlexPreset.NEO,
            GSFlexPreset.COMPACT,
            GSFlexPreset.AIRY
        ).map { PetalButtonGroupItem(value = it, label = it.label) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Presets",
                    style = PetalText.sectionHeader,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { onResetToPreset(lastNonCustomPreset) }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset to preset", style = PetalText.button)
                }
            }

            PetalChoiceButtonGroup(
                options = presetItems,
                selected = if (gs.preset == GSFlexPreset.CUSTOM) lastNonCustomPreset else gs.preset,
                minHeight = 42.dp,
                scrollableIfCrowded = true,
                onSelect = { newPreset ->
                    lastNonCustomPreset = newPreset
                    onSelectPreset(newPreset)
                }
            )
        }

        PetalSettingsToggleRow(
            title = "Use Custom Variable",
            subtitle = "Customize variable axes for Display, Headline & Body",
            icon = Icons.Rounded.Tune,
            checked = gs.preset == GSFlexPreset.CUSTOM,
            position = PetalGroupPosition.SINGLE,
            onCheckedChange = { checked -> onToggleCustomMode(checked, lastNonCustomPreset) }
        )

        AnimatedVisibility(
            visible = gs.preset == GSFlexPreset.CUSTOM,
            enter = fadeIn(PetalSpring.calm()) + expandIn(PetalSpring.calm()),
            exit = fadeOut(PetalSpring.calm()) + shrinkOut(PetalSpring.calm())
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FilledTonalButton(
                    onClick = { onCopyPreset(lastNonCustomPreset) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Copy from ${lastNonCustomPreset.label}",
                        style = PetalText.button
                    )
                }

                PetalSettingsToggleRow(
                    title = "Global Axis Override",
                    subtitle = "Apply each slider change to Display, Headline & Body together",
                    icon = Icons.Rounded.Tune,
                    checked = globalOverride,
                    position = PetalGroupPosition.SINGLE,
                    onCheckedChange = { globalOverride = it }
                )

                if (!globalOverride) {
                    PetalChoiceButtonGroup(
                        options = FontTier.entries.map { PetalButtonGroupItem(it, it.label) },
                        selected = selectedTier,
                        onSelect = { selectedTier = it },
                        minHeight = 42.dp,
                        scrollableIfCrowded = false
                    )
                }

                val currentAxes = gs.axesFor(if (globalOverride) FontTier.DISPLAY else selectedTier)
                FontAxes.Axis.entries.forEach { axis ->
                    GSFlexVariableSlider(
                        label = axis.label,
                        value = axis.get(currentAxes),
                        range = axis.range,
                        formatter = axis::format,
                        onDrag = { v ->
                            val updatedGs = if (globalOverride) {
                                gs.copy(
                                    preset = GSFlexPreset.CUSTOM,
                                    display = axis.withValue(gs.display, v),
                                    headline = axis.withValue(gs.headline, v),
                                    body = axis.withValue(gs.body, v)
                                )
                            } else {
                                gs.copy(preset = GSFlexPreset.CUSTOM)
                                    .withTierAxes(selectedTier, axis.withValue(currentAxes, v))
                            }
                            onPreviewChange(fontState.copy(gsFlexSettings = updatedGs))
                            if (globalOverride) onDragAllAxis(axis, v) else onDragTierAxis(selectedTier, axis, v)
                        },
                        onCommit = { v ->
                            if (globalOverride) {
                                onCommitAllAxis(axis, v)
                            } else {
                                onCommitTierAxis(selectedTier, axis, v)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFontCustomizer(
    fontState: FontState,
    onBrowseClick: () -> Unit,
    onPreviewChange: (FontState) -> Unit,
    onDragTierAxis: (FontTier, FontAxes.Axis, Float) -> Unit,
    onDragAllAxis: (FontAxes.Axis, Float) -> Unit,
    onCommitTierAxis: (FontTier, FontAxes.Axis, Float) -> Unit,
    onCommitAllAxis: (FontAxes.Axis, Float) -> Unit,
    onResetCustomAxes: () -> Unit
) {
    val custom = fontState.customFont
    val resolvedFile = PetalFontHelper.resolveReadableCustomFontFile(custom)
    var selectedTier by rememberSaveable { mutableStateOf(FontTier.DISPLAY) }
    var globalOverride by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = petalGroupShape(PetalGroupPosition.SINGLE),
            color = petalGroupSurfaceColor(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PetalGroupIconBadge(
                    icon = Icons.Rounded.FontDownload,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_custom_font_file),
                        style = PetalText.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (resolvedFile != null) custom.name else "No valid font loaded",
                        style = PetalText.rowSubtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (resolvedFile != null) {
                        Text(
                            text = if (custom.isVariable) {
                                "Variable OpenType font · 3-tier axis controls unlocked"
                            } else {
                                "Static font · weights 100–1000 active"
                            },
                            style = PetalText.caption,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Button(
                    onClick = onBrowseClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_browse), style = PetalText.button)
                }
            }
        }

        if (resolvedFile != null && custom.isVariable) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Custom Variable Axes",
                    style = PetalText.sectionHeader
                )
                TextButton(onClick = onResetCustomAxes) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset axes", style = PetalText.button)
                }
            }

            PetalSettingsToggleRow(
                title = "Global Axis Override",
                subtitle = "Edit Display, Headline & Body simultaneously",
                icon = Icons.Rounded.Tune,
                checked = globalOverride,
                position = PetalGroupPosition.SINGLE,
                onCheckedChange = { globalOverride = it }
            )

            if (!globalOverride) {
                PetalChoiceButtonGroup(
                    options = FontTier.entries.map { PetalButtonGroupItem(it, it.label) },
                    selected = selectedTier,
                    onSelect = { selectedTier = it },
                    minHeight = 42.dp,
                    scrollableIfCrowded = false
                )
            }

            val currentAxes = custom.settings.axesFor(if (globalOverride) FontTier.DISPLAY else selectedTier)
            FontAxes.Axis.entries.forEach { axis ->
                GSFlexVariableSlider(
                    label = axis.label,
                    value = axis.get(currentAxes),
                    range = axis.range,
                    formatter = axis::format,
                    onDrag = { v ->
                        val updatedSettings = if (globalOverride) {
                            custom.settings.copy(
                                display = axis.withValue(custom.settings.display, v),
                                headline = axis.withValue(custom.settings.headline, v),
                                body = axis.withValue(custom.settings.body, v)
                            )
                        } else {
                            custom.settings.withTierAxes(selectedTier, axis.withValue(currentAxes, v))
                        }
                        onPreviewChange(fontState.copy(customFont = custom.copy(settings = updatedSettings)))
                        if (globalOverride) onDragAllAxis(axis, v) else onDragTierAxis(selectedTier, axis, v)
                    },
                    onCommit = { v ->
                        if (globalOverride) {
                            onCommitAllAxis(axis, v)
                        } else {
                            onCommitTierAxis(selectedTier, axis, v)
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun GSFlexVariableSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatter: (Float) -> String = { v -> String.format(Locale.US, "%.0f", v) },
    onDrag: ((Float) -> Unit)? = null,
    onCommit: (Float) -> Unit
) {
    // Slider owns local state while dragging so the thumb never lags or jumps back.
    // Debounced persistence lives centrally in AppearanceSettingsViewModel.
    var local by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }

    if (!dragging && local != value) {
        local = value
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
                style = PetalText.sectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = formatter(local),
                style = PetalText.value,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
        }
        PetalSlider(
            value = local,
            onValueChange = { next ->
                dragging = true
                local = next
                onDrag?.invoke(next)
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(local)
            },
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
