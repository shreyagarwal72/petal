package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.liquidGlassChrome

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.PetalLanguages
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
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val matchWebsiteLanguage by viewModel.matchWebsiteLanguage.collectAsStateWithLifecycle()
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
    val expressiveShapes35 by viewModel.expressiveShapes35.collectAsStateWithLifecycle()
    val launchRippleEnabled by viewModel.launchRippleEnabled.collectAsStateWithLifecycle()
    val motionBlurEnabled by viewModel.motionBlurEnabled.collectAsStateWithLifecycle()
    val motionBlurScale by viewModel.motionBlurScale.collectAsStateWithLifecycle()
    val liquidGlassUnlocked by viewModel.liquidGlassUnlocked.collectAsStateWithLifecycle()
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsStateWithLifecycle()
    val liquidGlassAlpha by viewModel.liquidGlassAlpha.collectAsStateWithLifecycle()
    val liquidGlassSheen by viewModel.liquidGlassSheen.collectAsStateWithLifecycle()
    val liquidGlassTint by viewModel.liquidGlassTint.collectAsStateWithLifecycle()
    val liquidGlassContainments by viewModel.liquidGlassContainments.collectAsStateWithLifecycle()
    val liquidGlassBottomNav by viewModel.liquidGlassBottomNav.collectAsStateWithLifecycle()
    val liquidGlassAddressBar by viewModel.liquidGlassAddressBar.collectAsStateWithLifecycle()
    val liquidGlassOverflowBlurBg by viewModel.liquidGlassOverflowBlurBg.collectAsStateWithLifecycle()
    val liquidGlassBgMode by viewModel.liquidGlassBgMode.collectAsStateWithLifecycle()
    val liquidGlassBgImageUri by viewModel.liquidGlassBgImageUri.collectAsStateWithLifecycle()
    val liquidGlassBgDim by viewModel.liquidGlassBgDim.collectAsStateWithLifecycle()
    val liquidGlassBgBlur by viewModel.liquidGlassBgBlur.collectAsStateWithLifecycle()

    AppearanceSettingsScreenContent(
        appFont = appFont,
        appLanguage = appLanguage,
        matchWebsiteLanguage = matchWebsiteLanguage,
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
        expressiveShapes35 = expressiveShapes35,
        launchRippleEnabled = launchRippleEnabled,
        motionBlurEnabled = motionBlurEnabled,
        motionBlurScale = motionBlurScale,
        liquidGlassUnlocked = liquidGlassUnlocked,
        liquidGlassEnabled = liquidGlassEnabled,
        liquidGlassAlpha = liquidGlassAlpha,
        liquidGlassSheen = liquidGlassSheen,
        liquidGlassTint = liquidGlassTint,
        liquidGlassContainments = liquidGlassContainments,
        liquidGlassBottomNav = liquidGlassBottomNav,
        liquidGlassAddressBar = liquidGlassAddressBar,
        liquidGlassOverflowBlurBg = liquidGlassOverflowBlurBg,
        liquidGlassBgMode = liquidGlassBgMode,
        liquidGlassBgImageUri = liquidGlassBgImageUri,
        liquidGlassBgDim = liquidGlassBgDim,
        liquidGlassBgBlur = liquidGlassBgBlur,
        onAppFontChange = viewModel::setAppFont,
        onAppLanguageChange = viewModel::setAppLanguage,
        onMatchWebsiteLanguageChange = viewModel::setMatchWebsiteLanguage,
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
        onExpressiveShapes35Change = viewModel::setExpressiveShapes35,
        onBgShapeChangeModeChange = viewModel::setBgShapeChangeMode,
        onBgShapeRotationMinChange = viewModel::setBgShapeRotationMin,
        onHighRefreshRateChange = viewModel::setHighRefreshRate,
        onCustomFontNameChange = viewModel::setCustomFontName,
        onLaunchRippleEnabledChange = viewModel::setLaunchRippleEnabled,
        onMotionBlurEnabledChange = viewModel::setMotionBlurEnabled,
        onMotionBlurScaleChange = viewModel::setMotionBlurScale,
        onLiquidGlassEnabledChange = viewModel::setLiquidGlassEnabled,
        onLiquidGlassAlphaChange = viewModel::setLiquidGlassAlpha,
        onLiquidGlassSheenChange = viewModel::setLiquidGlassSheen,
        onLiquidGlassTintChange = viewModel::setLiquidGlassTint,
        onLiquidGlassContainmentsChange = viewModel::setLiquidGlassContainments,
        onLiquidGlassBottomNavChange = viewModel::setLiquidGlassBottomNav,
        onLiquidGlassAddressBarChange = viewModel::setLiquidGlassAddressBar,
        onLiquidGlassOverflowBlurBgChange = viewModel::setLiquidGlassOverflowBlurBg,
        onLiquidGlassBgModeChange = viewModel::setLiquidGlassBgMode,
        onLiquidGlassBgImageUriChange = viewModel::setLiquidGlassBgImageUri,
        onLiquidGlassBgDimChange = viewModel::setLiquidGlassBgDim,
        onLiquidGlassBgBlurChange = viewModel::setLiquidGlassBgBlur,
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
    expressiveShapes35: Boolean = true,
    launchRippleEnabled: Boolean = true,
    motionBlurEnabled: Boolean = false,
    motionBlurScale: Float = 1.0f,
    liquidGlassUnlocked: Boolean = false,
    liquidGlassEnabled: Boolean = false,
    liquidGlassAlpha: Float = 0.70f,
    liquidGlassSheen: Float = 0.60f,
    liquidGlassTint: String = "FROSTED",
    liquidGlassContainments: Boolean = true,
    liquidGlassBottomNav: Boolean = true,
    liquidGlassAddressBar: Boolean = true,
    liquidGlassOverflowBlurBg: Boolean = true,
    liquidGlassBgMode: String = "STRIDE",
    liquidGlassBgImageUri: String = "",
    liquidGlassBgDim: Float = 0.35f,
    liquidGlassBgBlur: Float = 16f,
    appLanguage: String = "system",
    matchWebsiteLanguage: Boolean = true,
    onAppFontChange: (AppFont) -> Unit,
    onAppLanguageChange: (String) -> Unit = {},
    onMatchWebsiteLanguageChange: (Boolean) -> Unit = {},
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
    onExpressiveShapes35Change: (Boolean) -> Unit = {},
    onBgShapeChangeModeChange: (String) -> Unit,
    onBgShapeRotationMinChange: (Int) -> Unit,
    onHighRefreshRateChange: (Boolean) -> Unit,
    onCustomFontNameChange: (String) -> Unit,
    onLaunchRippleEnabledChange: (Boolean) -> Unit = {},
    onMotionBlurEnabledChange: (Boolean) -> Unit = {},
    onMotionBlurScaleChange: (Float) -> Unit = {},
    onLiquidGlassEnabledChange: (Boolean) -> Unit = {},
    onLiquidGlassAlphaChange: (Float) -> Unit = {},
    onLiquidGlassSheenChange: (Float) -> Unit = {},
    onLiquidGlassTintChange: (String) -> Unit = {},
    onLiquidGlassContainmentsChange: (Boolean) -> Unit = {},
    onLiquidGlassBottomNavChange: (Boolean) -> Unit = {},
    onLiquidGlassAddressBarChange: (Boolean) -> Unit = {},
    onLiquidGlassOverflowBlurBgChange: (Boolean) -> Unit = {},
    onLiquidGlassBgModeChange: (String) -> Unit = {},
    onLiquidGlassBgImageUriChange: (String) -> Unit = {},
    onLiquidGlassBgDimChange: (Float) -> Unit = {},
    onLiquidGlassBgBlurChange: (Float) -> Unit = {},
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

    var showFontPicker by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }

    // Liquid Glass Background Image Picker states
    var showBgPickerChoice by remember { mutableStateOf(false) }
    var showBuiltInImagePicker by remember { mutableStateOf(false) }
    var pendingGlassCropUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // System picker kept as fallback for the Petal picker's "Browse system" button
    val systemFontPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            PetalFontHelper.saveCustomFontUri(context, it)
            onAppFontChange(AppFont.CUSTOM)
        }
    }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            pendingGlassCropUri = it
        }
    }

    val maxDetectedRefreshRate = remember(context) {
        PetalHighRefreshRateManager.getMaxSupportedRefreshRate(context)
    }

    var showMorphingShapesExplainer by remember { mutableStateOf(false) }
    var showMotionBlurExplainer by remember { mutableStateOf(false) }

    PetalFeatureExplainerDialog(
        visible = showMorphingShapesExplainer,
        onDismissRequest = { showMorphingShapesExplainer = false },
        feature = PetalExplainerFeature(
            type = ExplainerFeatureType.EXPRESSIVE_MORPHING_SHAPES,
            title = "Morphing Ambient Shapes",
            badge = "M3 EXPRESSIVE",
            subtitle = "Fluid Continuous Kinetic Geometry",
            bullets = listOf(
                "Real-time polygonal morphing powered by AndroidX Graphics Shapes.",
                "Smooth cubic Bézier corner interpolation with adaptive breathing cycles.",
                "Automatically paused during battery saver or low power states.",
                "Fully tinted dynamically according to your active Material You color style."
            ),
            icon = Icons.Rounded.BubbleChart,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.primary
        )
    )

    PetalFeatureExplainerDialog(
        visible = showMotionBlurExplainer,
        onDismissRequest = { showMotionBlurExplainer = false },
        feature = PetalExplainerFeature(
            type = ExplainerFeatureType.MOTION_BLUR,
            title = "Motion Blur Engine",
            badge = "PHYSICS",
            subtitle = "Velocity-Scaled Kinetic Smearing",
            bullets = listOf(
                "GPU-accelerated directional RenderEffect velocity smearing on fast list/grid flings.",
                "Calibrated to your screen's peak refresh rate (up to 120Hz/144Hz).",
                "Scalable blur intensity multiplier from 0.5x to 2.5x.",
                "Adaptive frame pacing ensures 60+ FPS without dropped scroll frames."
            ),
            icon = Icons.Rounded.Speed,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.primary
        )
    )

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
                    .scrollMotionBlur(scrollState, enabled = motionBlurEnabled)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 0: App Language
                val currentLanguage = remember(appLanguage) { PetalLanguages.findLanguage(appLanguage) }
                PetalSettingsSection(
                    title = stringResource(R.string.ui_app_language),
                    iconRes = com.petal.browser.R.drawable.translate,
                    cardId = "appearance_language",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        stringResource(R.string.ui_choose_your_preferred_display_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Containment Group: Quick Languages & Website Language Setting
                    val isGlass = liquidGlassEnabled && liquidGlassContainments
                    val glassSheen = if (isGlass) liquidGlassSheen.coerceIn(0f, 1f) else 0.60f
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Top item: Quick languages horizontal row
                        val quickLangShape = com.petal.browser.ui.containment.petalGroupShape(com.petal.browser.ui.containment.PetalGroupPosition.TOP)
                        Card(
                            shape = quickLangShape,
                            colors = CardDefaults.cardColors(containerColor = com.petal.browser.ui.containment.petalGroupSurfaceColor()),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassChrome(
                                    shape = quickLangShape,
                                    enabled = isGlass,
                                    sheenIntensity = glassSheen
                                )
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                                        icon = Icons.Rounded.Language,
                                        container = MaterialTheme.colorScheme.secondaryContainer,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        size = 38.dp,
                                        iconSize = 20.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.ui_quick_languages),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.ui_switch_common_languages_instantly),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val quickLanguages = remember {
                                    listOf(
                                        PetalLanguages.findLanguage("system"),
                                        PetalLanguages.findLanguage("en"),
                                        PetalLanguages.findLanguage("hi-Latn"),
                                        PetalLanguages.findLanguage("hi"),
                                        PetalLanguages.findLanguage("es")
                                    )
                                }

                                val quickLangScrollState = rememberScrollState()
                                ScrollFadeRow(
                                    scrollState = quickLangScrollState,
                                    edgeColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(quickLangScrollState),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        quickLanguages.forEach { lang ->
                                            val isSelected = appLanguage == lang.tag
                                            val chipShape = RoundedCornerShape(12.dp)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    if (appLanguage != lang.tag) {
                                                        onAppLanguageChange(lang.tag)
                                                        HelperUnit.setAppLanguage(context, lang.tag)
                                                    }
                                                },
                                                shape = chipShape,
                                                border = if (isGlass) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected),
                                                modifier = if (isGlass) {
                                                    Modifier.liquidGlassChrome(
                                                        shape = chipShape,
                                                        enabled = true,
                                                        sheenIntensity = if (isSelected) 0.5f else 0.25f
                                                    )
                                                } else Modifier,
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = if (isGlass) {
                                                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f)
                                                    } else MaterialTheme.colorScheme.surfaceContainerLow,
                                                    selectedContainerColor = if (isGlass) {
                                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                                                    } else MaterialTheme.colorScheme.secondaryContainer
                                                ),
                                                label = { Text(lang.nativeName) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Middle item: Action row to open All Languages modal sheet
                        com.petal.browser.ui.containment.PetalGroupRow(
                            icon = Icons.Rounded.TravelExplore,
                            title = stringResource(R.string.ui_all_languages),
                            subtitle = "${PetalLanguages.ALL_LANGUAGES.size} languages supported",
                            position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = {
                                languageSearchQuery = ""
                                showLanguageSheet = true
                            }
                        )

                        // Bottom item: Match Website Language toggle
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            title = stringResource(R.string.ui_website_language_title),
                            subtitle = stringResource(R.string.ui_website_language_subtitle),
                            icon = Icons.Rounded.Http,
                            checked = matchWebsiteLanguage,
                            onCheckedChange = { checked ->
                                onMatchWebsiteLanguageChange(checked)
                                PreferenceManager.getDefaultSharedPreferences(context)
                                    .edit()
                                    .putBoolean("sp_match_website_language", checked)
                                    .apply()
                            },
                            position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM
                        )
                    }
                }

                // Section 1: App Theme & Dynamic Color Palette
                PetalSettingsSection(
                    title = stringResource(R.string.ui_theme_color_palette),
                    iconRes = com.petal.browser.R.drawable.brightness_medium_filled,
                    cardId = "appearance_theme",
                    targetHighlightId = targetHighlightItemId
                ) {
                    var appLaunchAnim by remember {
                        mutableStateOf(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_app_launch_animation", true))
                    }
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                        title = "Quantum Web Launch Animation",
                        subtitle = "Procedural particle stream of web icons during app startup",
                        icon = Icons.Rounded.AutoAwesome,
                        checked = appLaunchAnim,
                        onCheckedChange = { checked ->
                            appLaunchAnim = checked
                            PreferenceManager.getDefaultSharedPreferences(context).edit()
                                .putBoolean("sp_app_launch_animation", checked)
                                .apply()
                        }
                    )

                    Spacer(Modifier.height(8.dp))

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
                            onThemeConfigChange(config)
                            when (config) {
                                ThemeConfig.FOLLOW_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                ThemeConfig.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                ThemeConfig.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                            }
                        }
                    )
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
                        stringResource(R.string.ui_palette_harmony_style),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val currentPalette = remember(paletteId) {
                        PetalPalettes.firstOrNull { it.id == paletteId } ?: PetalPalettes.first()
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
                            onDynamicColorChange(newValue)
                            PetalSearchWidgetProvider.updateAllWidgets(context)
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
                            onAmoledModeChange(newValue)
                            PetalSearchWidgetProvider.updateAllWidgets(context)
                        }
                    )
                }

                // Section 2: Custom Fonts & Typography
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
                        onSelect = { font ->
                            onAppFontChange(font)
                            if (font == AppFont.CUSTOM) showFontPicker = true
                        }
                    )

                    // GS Flex Preset Chips (For Petal Signature)
                    AnimatedVisibility(visible = appFont == AppFont.PETAL) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.ui_petal_signature_design_preset),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            com.petal.browser.ui.components.PetalChoiceButtonGroup(
                                options = GSFlexPreset.values().map { preset ->
                                    com.petal.browser.ui.components.PetalButtonGroupItem(
                                        value = preset,
                                        label = preset.label.substringBefore(" (")
                                    )
                                },
                                selected = gsFlexPreset,
                                minHeight = 44.dp,
                                onSelect = onGsFlexPresetChange
                            )
                        }
                    }

                    // Custom Font File Picker UI (Containment Surface & Icon Badge)
                    AnimatedVisibility(visible = appFont == AppFont.CUSTOM) {
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

                // Section 3: Layout & Ambient Morphing Shapes
                PetalSettingsSection(
                    title = stringResource(R.string.ui_layout_expressive_motion),
                    iconRes = com.petal.browser.R.drawable.layers_filled,
                    cardId = "appearance_layout",
                    targetHighlightId = targetHighlightItemId,
                    onInfoClick = { showMorphingShapesExplainer = true }
                ) {
                    // Floating Tab Bar Toggle
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_floating_tab_bar),
                        subtitle = stringResource(R.string.ui_show_the_bottom_bar_as),
                        icon = Icons.Rounded.SpaceBar,
                        checked = floatingTabBar,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                        onCheckedChange = onFloatingTabBarChange
                    )
                    // Stride-style liquid glass on the floating bar (Android 12+). Web pages
                    // switch renderer to show through it, so it applies to tabs opened after.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val glassContext = androidx.compose.ui.platform.LocalContext.current
                        val glassPrefs = remember(glassContext) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(glassContext) }
                        var glassNav by remember { mutableStateOf(glassPrefs.getBoolean(com.petal.browser.ui.glass.StrideGlassPrefs.KEY, true)) }
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            title = "Liquid glass nav bar",
                            subtitle = "Blur and bend the page behind the floating bar. Applies to tabs opened after the change",
                            icon = Icons.Rounded.BlurOn,
                            checked = glassNav,
                            position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                            onCheckedChange = {
                                glassNav = it
                                glassPrefs.edit().putBoolean(com.petal.browser.ui.glass.StrideGlassPrefs.KEY, it).apply()
                            }
                        )
                    }
                    // Material 3 Expressive Background Morphing Shapes Toggle
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_m3_expressive_morphing_shapes),
                        subtitle = stringResource(R.string.ui_display_ambient_morphing_background_sh),
                        icon = Icons.Rounded.BubbleChart,
                        checked = expressiveBgShapes,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                        onCheckedChange = onExpressiveBgShapesChange
                    )

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_m3_expressive_shapes_global),
                        subtitle = stringResource(R.string.ui_m3_expressive_shapes_global_desc),
                        icon = Icons.Rounded.Category,
                        checked = expressiveShapes35,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                        onCheckedChange = onExpressiveShapes35Change
                    )

                    if (expressiveBgShapes) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ui_shape_change_mode),
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
                                            text = stringResource(R.string.ui_auto_change_interval),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_min, bgShapeRotationMin),
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
                PetalSettingsSection(
                    title = stringResource(R.string.ui_display_performance),
                    icon = Icons.Rounded.Speed,
                    cardId = "appearance_refresh",
                    targetHighlightId = targetHighlightItemId,
                    onInfoClick = { showMotionBlurExplainer = true }
                ) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_high_refresh_rate_120hz),
                        subtitle = stringResource(R.string.ui_force_120hz_144hz_peak_display, maxDetectedRefreshRate.toInt()),
                        icon = Icons.Rounded.Speed,
                        checked = highRefreshRate,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
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

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_app_launch_ripple_effect),
                        subtitle = stringResource(R.string.ui_display_fluid_liquid_displacement_ripp),
                        icon = Icons.Rounded.WaterDrop,
                        checked = launchRippleEnabled,
                        position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                        onCheckedChange = onLaunchRippleEnabledChange
                    )

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_motion_blur),
                        subtitle = stringResource(R.string.ui_motion_blur_subtitle),
                        iconRes = com.petal.browser.R.drawable.rounded_settings_motion_mode_24,
                        checked = motionBlurEnabled,
                        position = if (motionBlurEnabled) com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE else com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                        onCheckedChange = onMotionBlurEnabledChange
                    )

                    AnimatedVisibility(
                        visible = motionBlurEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.ui_motion_blur_amount),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    String.format(java.util.Locale.getDefault(), "%.1fx", motionBlurScale),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = motionBlurScale,
                                onValueChange = onMotionBlurScaleChange,
                                valueRange = 0.5f..2.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Section 5: Liquid Glass UI & Surfaces. Always shown: its old unlock (a long
                // press in the About Developer sheet) is no longer reachable from the app.
                run {
                    PetalSettingsSection(
                        title = "Liquid Glass UI & Surfaces",
                        icon = Icons.Rounded.AutoAwesome,
                        cardId = "appearance_liquid_glass",
                        targetHighlightId = targetHighlightItemId
                    ) {
                        Text(
                            text = "Stride and Vyxel-style glass with a shared frosted wallpaper, refracted edges and soft highlights.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Master Toggle
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            title = "Liquid Glass UI",
                            subtitle = if (liquidGlassEnabled) "Active system-wide across containments, bottom bar & address bar" else "Enable liquid glass materials & specular effects",
                            icon = Icons.Rounded.BlurOn,
                            checked = liquidGlassEnabled,
                            position = if (liquidGlassEnabled) com.petal.browser.ui.containment.PetalGroupPosition.TOP else com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                            onCheckedChange = onLiquidGlassEnabledChange
                        )

                        if (liquidGlassEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Real-time local state so sliding immediately updates on this screen without lag or waiting for disk writes
                                var liveAlpha by remember(liquidGlassAlpha) { mutableFloatStateOf(liquidGlassAlpha) }
                                var liveSheen by remember(liquidGlassSheen) { mutableFloatStateOf(liquidGlassSheen) }

                                // Live Liquid Glass Interactive Preview Card
                                val previewShape = RoundedCornerShape(24.dp)
                                val previewBaseColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = (0.12f + liveAlpha * 0.40f).coerceIn(0f, 1f))
                                
                                // Vibrant backdrop container behind the glass card to demonstrate live refraction & translucency
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(previewShape)
                                ) {
                                    // Colorful vivid multi-color gradient underneath to showcase light bending & transparency
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f),
                                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                                                    )
                                                )
                                            )
                                    )

                                    Card(
                                        shape = previewShape,
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (com.petal.browser.ui.glass.LocalPetalGlassWallpaper.current?.ready == true) Color.Transparent else previewBaseColor
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .liquidGlassChrome(
                                                shape = previewShape,
                                                enabled = true,
                                                sheenIntensity = liveSheen,
                                                alphaOverride = liveAlpha,
                                                sheenOverride = liveSheen
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(18.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Column {
                                                        Text(
                                                            text = "Liquid Glass Live Preview",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "Specular light refraction & chromatic rim",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "Pure frosted glass material renders responsive chromatic edges and real-time alpha translucency over the active backdrop layer.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Glass Translucency Slider (Stride style PetalSlider)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Surface Translucency / Opacity",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${Math.round(liveAlpha * 100)}%",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    PetalSlider(
                                        value = liveAlpha,
                                        onValueChange = {
                                            liveAlpha = it
                                            onLiquidGlassAlphaChange(it)
                                        },
                                        valueRange = 0.10f..0.95f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Specular Sheen Intensity Slider
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Specular Sheen & Border Reflection",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${Math.round(liveSheen * 100)}%",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    PetalSlider(
                                        value = liveSheen,
                                        onValueChange = {
                                            liveSheen = it
                                            onLiquidGlassSheenChange(it)
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Divider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                // Component Level Tweaks (Stride custom components)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Component Surfaces",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                            title = "Cards & Containments",
                                            subtitle = "Apply liquid glass material to cards, list groups & dialogs",
                                            icon = Icons.Rounded.Layers,
                                            checked = liquidGlassContainments,
                                            position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                                            onCheckedChange = onLiquidGlassContainmentsChange
                                        )
                                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                            title = "Bottom Navigation Bar",
                                            subtitle = "Translucent floating or persistent bottom navigation bar",
                                            icon = Icons.Rounded.SpaceBar,
                                            checked = liquidGlassBottomNav,
                                            position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                                            onCheckedChange = onLiquidGlassBottomNavChange
                                        )
                                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                            title = "Address & Search Bar",
                                            subtitle = "Translucent address bar pill with specular edge glow",
                                            icon = Icons.Rounded.Search,
                                            checked = liquidGlassAddressBar,
                                            position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                                            onCheckedChange = onLiquidGlassAddressBarChange
                                        )
                                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                            title = "Overflow Menu Background Blur",
                                            subtitle = "Blur outside wallpaper & window instead of dark dimming when menu opens",
                                            icon = Icons.Rounded.BlurCircular,
                                            checked = liquidGlassOverflowBlurBg,
                                            position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                                            onCheckedChange = onLiquidGlassOverflowBlurBgChange
                                        )
                                    }
                                }

                                Divider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                // Background Mode Selector
                                Text(
                                    text = "Liquid Glass Background Layer",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                ExpressiveButtonGroup(
                                    items = listOf(
                                        ExpressiveSegmentItem(id = "STRIDE", label = "Glass", icon = Icons.Rounded.BlurOn),
                                        ExpressiveSegmentItem(id = "MORPHING", label = "Morphs", icon = Icons.Rounded.BubbleChart),
                                        ExpressiveSegmentItem(id = "IMAGE", label = "Wallpaper", icon = Icons.Rounded.Wallpaper)
                                    ),
                                    selectedId = liquidGlassBgMode,
                                    onItemSelected = onLiquidGlassBgModeChange,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (liquidGlassBgMode == "IMAGE") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = { showBgPickerChoice = true },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(if (liquidGlassBgImageUri.isNotEmpty()) "Replace Image" else "Choose Image")
                                            }

                                            if (liquidGlassBgImageUri.isNotEmpty()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onLiquidGlassBgImageUriChange("")
                                                        onLiquidGlassBgModeChange("MORPHING")
                                                    },
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Clear")
                                                }
                                            }
                                        }

                                        if (liquidGlassBgImageUri.isNotEmpty()) {
                                            // Image Dim Slider
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Wallpaper Dim Level",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "${Math.round(liquidGlassBgDim * 100)}%",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                PetalSlider(
                                                    value = liquidGlassBgDim,
                                                    onValueChange = onLiquidGlassBgDimChange,
                                                    valueRange = 0f..0.85f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            // Image Blur Slider
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Wallpaper Blur Radius",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "${Math.round(liquidGlassBgBlur)} dp",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                PetalSlider(
                                                    value = liquidGlassBgBlur,
                                                    onValueChange = onLiquidGlassBgBlurChange,
                                                    valueRange = 0f..40f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Text selection toolbar (Material 3 Expressive / liquid glass), app-wide.
                com.petal.browser.ui.selection.PetalSelectionToolbarSettingsSection(targetHighlightId = targetHighlightItemId)

                Spacer(Modifier.height(32.dp))
            }
        }

        if (showBgPickerChoice) {
            com.petal.browser.ui.containment.PetalSheet(
                onDismissRequest = { showBgPickerChoice = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose Wallpaper Source",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select an image using Petal's built-in file picker or your device's media gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    com.petal.browser.ui.containment.PetalSelectableOptionCard(
                        title = "Petal Built-in File & Image Picker",
                        subtitle = "Browse device folders, downloads, and storage roots directly in Petal",
                        selected = false,
                        onClick = {
                            showBgPickerChoice = false
                            showBuiltInImagePicker = true
                        },
                        leading = {
                            com.petal.browser.ui.containment.PetalGroupIconBadge(
                                icon = Icons.Rounded.FolderOpen,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    )

                    com.petal.browser.ui.containment.PetalSelectableOptionCard(
                        title = "System Media Gallery",
                        subtitle = "Select from Google Photos, Gallery or System File Picker",
                        selected = false,
                        onClick = {
                            showBgPickerChoice = false
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        leading = {
                            com.petal.browser.ui.containment.PetalGroupIconBadge(
                                icon = Icons.Rounded.PhotoLibrary,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    )
                }
            }
        }

        if (showBuiltInImagePicker) {
            com.petal.browser.compose.file.PetalFilePickerScreen(
                mimeTypes = arrayOf("image/*"),
                onDismissRequest = { showBuiltInImagePicker = false },
                onFileSelected = { file ->
                    showBuiltInImagePicker = false
                    pendingGlassCropUri = android.net.Uri.fromFile(file)
                },
                onBrowseSystemFallback = {
                    showBuiltInImagePicker = false
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )
        }

        pendingGlassCropUri?.let { cropUri ->
            PetalWallpaperCropSheet(
                imageUri = cropUri,
                title = "Crop Liquid Glass Wallpaper",
                buttonLabel = "Apply Liquid Glass Wallpaper",
                onDismiss = { pendingGlassCropUri = null },
                onWallpaperCropped = { croppedLocalUri ->
                    pendingGlassCropUri = null
                    onLiquidGlassBgImageUriChange(croppedLocalUri.toString())
                    onLiquidGlassBgModeChange("IMAGE")
                }
            )
        }

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

        if (showLanguageSheet) {
            com.petal.browser.ui.containment.PetalSheet(
                onDismissRequest = { showLanguageSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_app_language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ui_choose_your_preferred_display_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    val langSearchShape = RoundedCornerShape(16.dp)
                    val isSheetGlass = liquidGlassEnabled && liquidGlassContainments
                    OutlinedTextField(
                        value = languageSearchQuery,
                        onValueChange = { languageSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassChrome(shape = langSearchShape, enabled = isSheetGlass, sheenIntensity = 0.45f),
                        placeholder = { Text("Search language...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (languageSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { languageSearchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = langSearchShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isSheetGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = if (isSheetGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = if (isSheetGlass) Color.Transparent else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isSheetGlass) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    val filteredLanguages = remember(languageSearchQuery) {
                        if (languageSearchQuery.isBlank()) {
                            PetalLanguages.ALL_LANGUAGES
                        } else {
                            val q = languageSearchQuery.trim().lowercase()
                            PetalLanguages.ALL_LANGUAGES.filter {
                                it.nativeName.lowercase().contains(q) ||
                                it.englishName.lowercase().contains(q) ||
                                it.tag.lowercase().contains(q)
                            }
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(filteredLanguages.size) { idx ->
                            val lang = filteredLanguages[idx]
                            val isSelected = appLanguage == lang.tag
                            val position = com.petal.browser.ui.containment.petalGroupPositionFor(idx, filteredLanguages.size)
                            com.petal.browser.ui.containment.PetalGroupListRow(
                                position = position,
                                selected = isSelected,
                                onClick = {
                                    if (appLanguage != lang.tag) {
                                        onAppLanguageChange(lang.tag)
                                        HelperUnit.setAppLanguage(context, lang.tag)
                                    }
                                    showLanguageSheet = false
                                },
                                leading = {
                                    val badgeContainer = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                                    val badgeTint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                                        icon = Icons.Rounded.Translate,
                                        container = badgeContainer,
                                        tint = badgeTint
                                    )
                                },
                                content = {
                                    Text(
                                        text = lang.displayLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (lang.tag != "system") "BCP-47: ${lang.tag} • ${lang.nativeName}" else stringResource(R.string.ui_follow_device_language),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                },
                                trailing = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
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
            targetValue = if (selected) 3.dp else 0.dp,
            label = "paletteOutlinePadding"
        )

        Surface(
            onClick = onClick,
            color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
            shape = RoundedCornerShape(outerCorner),
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
