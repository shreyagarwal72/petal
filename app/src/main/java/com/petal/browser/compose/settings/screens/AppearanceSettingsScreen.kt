package com.petal.browser.compose.settings.screens

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.settings.viewmodel.AppearanceSettingsViewModel
import com.petal.browser.ui.components.*
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.PetalHighRefreshRateManager
import com.petal.browser.widget.PetalSearchWidgetProvider

@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val fontWidth by viewModel.fontWidth.collectAsStateWithLifecycle()
    val fontWeight by viewModel.fontWeight.collectAsStateWithLifecycle()
    val fontRoundness by viewModel.fontRoundness.collectAsStateWithLifecycle()
    val gsFlexPreset by viewModel.gsFlexPreset.collectAsStateWithLifecycle()
    val colorStyle by viewModel.colorStyle.collectAsStateWithLifecycle()
    val paletteId by viewModel.paletteId.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val amoledMode by viewModel.amoledMode.collectAsStateWithLifecycle()
    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
    val floatingTabBar by viewModel.floatingTabBar.collectAsStateWithLifecycle()
    val expressiveBgShapes by viewModel.expressiveBgShapes.collectAsStateWithLifecycle()
    val bgShapeChangeMode by viewModel.bgShapeChangeMode.collectAsStateWithLifecycle()
    val bgShapeRotationMin by viewModel.bgShapeRotationMin.collectAsStateWithLifecycle()
    val highRefreshRate by viewModel.highRefreshRate.collectAsStateWithLifecycle()
    val customFontName by viewModel.customFontName.collectAsStateWithLifecycle()

    AppearanceSettingsScreenContent(
        appFont = appFont,
        fontWidth = fontWidth,
        fontWeight = fontWeight,
        fontRoundness = fontRoundness,
        gsFlexPreset = gsFlexPreset,
        colorStyle = colorStyle,
        paletteId = paletteId,
        dynamicColor = dynamicColor,
        amoledMode = amoledMode,
        themeConfig = themeConfig,
        floatingTabBar = floatingTabBar,
        expressiveBgShapes = expressiveBgShapes,
        bgShapeChangeMode = bgShapeChangeMode,
        bgShapeRotationMin = bgShapeRotationMin,
        highRefreshRate = highRefreshRate,
        customFontName = customFontName,
        onAppFontChange = viewModel::setAppFont,
        onFontWidthChange = viewModel::setFontWidth,
        onFontWeightChange = viewModel::setFontWeight,
        onFontRoundnessChange = viewModel::setFontRoundness,
        onGsFlexPresetChange = viewModel::setGsFlexPreset,
        onColorStyleChange = viewModel::setColorStyle,
        onPaletteIdChange = viewModel::setPaletteId,
        onDynamicColorChange = viewModel::setDynamicColor,
        onAmoledModeChange = viewModel::setAmoledMode,
        onThemeConfigChange = viewModel::setThemeConfig,
        onFloatingTabBarChange = viewModel::setFloatingTabBar,
        onExpressiveBgShapesChange = viewModel::setExpressiveBgShapes,
        onBgShapeChangeModeChange = viewModel::setBgShapeChangeMode,
        onBgShapeRotationMinChange = viewModel::setBgShapeRotationMin,
        onHighRefreshRateChange = viewModel::setHighRefreshRate,
        onCustomFontNameChange = viewModel::setCustomFontName,
        onNavigateBack = onNavigateBack,
        targetHighlightItemId = targetHighlightItemId,
        modifier = modifier
    )
}

@Composable
fun AppearanceSettingsScreenContent(
    appFont: AppFont,
    fontWidth: Float,
    fontWeight: Float,
    fontRoundness: Float,
    gsFlexPreset: GSFlexPreset,
    colorStyle: ColorStyle,
    paletteId: String,
    dynamicColor: Boolean,
    amoledMode: Boolean,
    themeConfig: ThemeConfig,
    floatingTabBar: Boolean,
    expressiveBgShapes: Boolean,
    bgShapeChangeMode: String,
    bgShapeRotationMin: Int,
    highRefreshRate: Boolean,
    customFontName: String,
    onAppFontChange: (AppFont) -> Unit,
    onFontWidthChange: (Float) -> Unit,
    onFontWeightChange: (Float) -> Unit,
    onFontRoundnessChange: (Float) -> Unit,
    onGsFlexPresetChange: (GSFlexPreset) -> Unit,
    onColorStyleChange: (ColorStyle) -> Unit,
    onPaletteIdChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAmoledModeChange: (Boolean) -> Unit,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    onFloatingTabBarChange: (Boolean) -> Unit,
    onExpressiveBgShapesChange: (Boolean) -> Unit,
    onBgShapeChangeModeChange: (String) -> Unit,
    onBgShapeRotationMinChange: (Int) -> Unit,
    onHighRefreshRateChange: (Boolean) -> Unit,
    onCustomFontNameChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = when (themeConfig) {
        ThemeConfig.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
    }

    val fontPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            PetalFontHelper.saveCustomFontUri(context, it)
            onAppFontChange(AppFont.CUSTOM)
        }
    }

    val maxDetectedRefreshRate = remember(context) {
        PetalHighRefreshRateManager.getMaxSupportedRefreshRate(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "appearance_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Appearance & Theme",
                subtitle = "Fonts, theme modes, color palettes, AMOLED & Material You",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: App Theme & Dynamic Color Palette
                SettingsCategoryCard(
                    title = "Theme & Color Palette",
                    iconRes = com.petal.browser.R.drawable.brightness_medium_filled,
                    cardId = "appearance_theme",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        "Customize app color schemes, dynamic theming and OLED black mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Theme Mode Chips
                    Text(
                        "Theme Mode:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val themeScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = themeScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(themeScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeConfig.values().forEach { config ->
                                val label = when (config) {
                                    ThemeConfig.FOLLOW_SYSTEM -> "System Default"
                                    ThemeConfig.LIGHT -> "Light Mode"
                                    ThemeConfig.DARK -> "Dark Mode"
                                }
                                val icon = when (config) {
                                    ThemeConfig.FOLLOW_SYSTEM -> Icons.Rounded.BrightnessAuto
                                    ThemeConfig.LIGHT -> Icons.Rounded.LightMode
                                    ThemeConfig.DARK -> Icons.Rounded.DarkMode
                                }
                                FilterChip(
                                    selected = themeConfig == config,
                                    onClick = {
                                        onThemeConfigChange(config)
                                        when (config) {
                                            ThemeConfig.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                            ThemeConfig.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                            ThemeConfig.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                        }
                                    },
                                    label = { Text(label) },
                                    leadingIcon = {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                    // Preset Color Palettes
                    Text(
                        "Preset Color Palettes:",
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
                                        .clip(CircleShape)
                                        .background(pal.seed)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onPaletteIdChange(pal.id)
                                            onDynamicColorChange(false)
                                            PetalSearchWidgetProvider.updateAllWidgets(context)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, contentDescription = pal.label, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Palette Style Swatches
                    Text(
                        "Palette Harmony Style:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val currentPalette = remember(paletteId) {
                        PetalPalettes.firstOrNull { it.id == paletteId } ?: PetalPalettes.first()
                    }
                    val isEffectiveAmoled = isDarkTheme && amoledMode
                    val activeBaseScheme = remember(currentPalette, isDarkTheme, isEffectiveAmoled) {
                        if (isDarkTheme) {
                            if (isEffectiveAmoled) currentPalette.dark.applyAmoled() else currentPalette.dark
                        } else {
                            currentPalette.light
                        }
                    }
                    val activePreviewScheme = remember(activeBaseScheme, colorStyle) {
                        activeBaseScheme.applyStyle(colorStyle)
                    }
                    val styleSchemes = remember(activeBaseScheme) {
                        ColorStyle.entries.associateWith { style ->
                            activeBaseScheme.applyStyle(style)
                        }
                    }

                    val paletteStyleScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = paletteStyleScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(paletteStyleScrollState)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ColorStyle.entries.forEach { style ->
                                val swatchScheme = styleSchemes[style] ?: activePreviewScheme
                                PaletteSwatchSquare(
                                    scheme = swatchScheme,
                                    selected = colorStyle == style,
                                    onClick = {
                                        onColorStyleChange(style)
                                        PetalSearchWidgetProvider.updateAllWidgets(context)
                                    },
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                    ToggleRow(
                        title = "Material You Dynamic Color",
                        subtitle = "Adapt accent colors from your system wallpaper (Android 12+)",
                        icon = Icons.Rounded.ColorLens,
                        checked = dynamicColor,
                        onCheckedChange = { newValue ->
                            onDynamicColorChange(newValue)
                            PetalSearchWidgetProvider.updateAllWidgets(context)
                        }
                    )
                    // AMOLED Black Toggle
                    ToggleRow(
                        title = "AMOLED Black Dark Mode",
                        subtitle = if (isDarkTheme) "Pure black background ladder for OLED displays" else "Disabled in Light Mode (Requires Dark theme)",
                        icon = Icons.Rounded.DarkMode,
                        checked = amoledMode && isDarkTheme,
                        enabled = isDarkTheme,
                        onCheckedChange = { newValue ->
                            onAmoledModeChange(newValue)
                            PetalSearchWidgetProvider.updateAllWidgets(context)
                        }
                    )
                }

                // Section 2: Custom Fonts & Typography
                SettingsCategoryCard(
                    title = "Typography & Fonts",
                    iconRes = com.petal.browser.R.drawable.database_filled,
                    cardId = "appearance_font",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        "Choose typography style or load custom TrueType / OpenType font files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Font Family Chips
                    Text(
                        "Select Font Family:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val fontFamilyScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = fontFamilyScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(fontFamilyScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppFont.values().forEach { font ->
                                FilterChip(
                                    selected = appFont == font,
                                    onClick = {
                                        onAppFontChange(font)
                                        if (font == AppFont.CUSTOM) {
                                            fontPickerLauncher.launch("*/*")
                                        }
                                    },
                                    label = { Text(font.label) },
                                    leadingIcon = if (appFont == font) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // GS Flex Preset Chips (For Petal Signature)
                    AnimatedVisibility(visible = appFont == AppFont.PETAL) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Petal Signature Design Preset:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val presetScrollState = rememberScrollState()
                            ScrollFadeRow(
                                scrollState = presetScrollState,
                                edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(presetScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GSFlexPreset.values().forEach { preset ->
                                        FilterChip(
                                            selected = gsFlexPreset == preset,
                                            onClick = { onGsFlexPresetChange(preset) },
                                            label = { Text(preset.label.substringBefore(" (")) },
                                            leadingIcon = if (gsFlexPreset == preset) {
                                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Custom Font File Picker UI
                    AnimatedVisibility(visible = appFont == AppFont.CUSTOM) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FontDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Custom Font File",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = customFontName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { fontPickerLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Browse")
                                }
                            }
                        }
                    }
                }

                // Section 3: Layout & Ambient Morphing Shapes
                SettingsCategoryCard(
                    title = "Layout & Expressive Motion",
                    iconRes = com.petal.browser.R.drawable.layers_filled,
                    cardId = "appearance_layout",
                    targetHighlightId = targetHighlightItemId
                ) {
                    // Floating Tab Bar Toggle
                    ToggleRow(
                        title = "Floating Tab Bar",
                        subtitle = "Show the bottom bar as a floating pill instead of a flat bar",
                        icon = Icons.Rounded.SpaceBar,
                        checked = floatingTabBar,
                        onCheckedChange = onFloatingTabBarChange
                    )
                    // Material 3 Expressive Background Morphing Shapes Toggle
                    ToggleRow(
                        title = "M3 Expressive Morphing Shapes",
                        subtitle = "Display ambient morphing background shapes across all app screens",
                        icon = Icons.Rounded.BubbleChart,
                        checked = expressiveBgShapes,
                        onCheckedChange = onExpressiveBgShapesChange
                    )

                    if (expressiveBgShapes) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Shape Change Mode:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            ExpressiveButtonGroup(
                                items = listOf(
                                    ExpressiveSegmentItem(id = "ALWAYS", label = "Always", icon = Icons.Rounded.Autorenew),
                                    ExpressiveSegmentItem(id = "PERIODIC", label = "Periodically", icon = Icons.Rounded.Schedule)
                                ),
                                selectedId = bgShapeChangeMode,
                                onItemSelected = onBgShapeChangeModeChange,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (bgShapeChangeMode == "PERIODIC") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Auto-Change Interval:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$bgShapeRotationMin min",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    PetalSlider(
                                        value = bgShapeRotationMin.coerceIn(1, 60).toFloat(),
                                        onValueChange = { newValue ->
                                            val rounded = Math.round(newValue).coerceIn(1, 60)
                                            if (rounded != bgShapeRotationMin) {
                                                onBgShapeRotationMinChange(rounded)
                                            }
                                        },
                                        valueRange = 1f..60f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 4: Display Refresh Rate & Performance
                SettingsCategoryCard(
                    title = "Display & Performance",
                    icon = Icons.Rounded.Speed,
                    cardId = "appearance_refresh",
                    targetHighlightId = targetHighlightItemId
                ) {
                    ToggleRow(
                        title = "High Refresh Rate (120Hz+)",
                        subtitle = "Force 120Hz/144Hz peak display refresh rate and smooth 120 FPS frame pacing (Detected hardware peak: ${maxDetectedRefreshRate.toInt()} Hz)",
                        icon = Icons.Rounded.Speed,
                        checked = highRefreshRate,
                        onCheckedChange = { newValue ->
                            onHighRefreshRateChange(newValue)
                            (context as? Activity)?.let { act ->
                                if (newValue) {
                                    PetalHighRefreshRateManager.applyHighRefreshRate(act)
                                } else {
                                    PetalHighRefreshRateManager.resetRefreshRate(act)
                                }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PaletteSwatchSquare(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val circleRadius = maxWidth / 2
        val innerCorner by animateDpAsState(
            targetValue = if (selected) 12.dp else circleRadius,
            label = "paletteInnerCorner"
        )
        val outerCorner by animateDpAsState(
            targetValue = if (selected) 16.dp else circleRadius,
            label = "paletteOuterCorner"
        )
        val outlinePadding by animateDpAsState(
            targetValue = if (selected) 4.dp else 0.dp,
            label = "paletteOutlinePadding"
        )
        val borderWidth by animateDpAsState(
            targetValue = if (selected) 2.dp else 0.dp,
            label = "paletteBorderWidth"
        )

        Surface(
            onClick = onClick,
            color = scheme.surfaceContainerHighest,
            shape = RoundedCornerShape(outerCorner),
            border = if (borderWidth > 0.dp) BorderStroke(borderWidth, scheme.primary) else null,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(outlinePadding)
            ) {
                Surface(
                    color = scheme.surface,
                    shape = RoundedCornerShape(innerCorner),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.secondary)
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.tertiary)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(scheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }
        }
    }
}
