/*
 * PetalSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Comprehensive Material 3 Settings Screen for Petal Browser featuring:
 * 1. Live Interactive Font & Accent Customization Preview (Stride Variable Fonts & Monet Palette Styles)
 * 2. Search Settings Filter Bar
 * 3. Default Search Engine Selector
 * 4. Custom Homepage Configuration (Petal Home vs Custom Web URL)
 * 5. Background Video & Audio Playback Settings
 * 6. Private DNS Options (CleanBrowsing Family Filter, Cloudflare 1.1.1.1, Google Public DNS, OpenDNS)
 * 7. Popular Languages Selector (English, Spanish, Hindi, French, German, Chinese, Arabic, Portuguese, Russian, Japanese)
 * 8. Privacy & AdBlock Protection Settings
 * 9. Font & Page Zoom Scaling Sliders (StrideSlider)
 * 10. About App & About Developer Sections
 */

package com.petal.browser.compose.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.petal.browser.ui.components.PetalFeatureTile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalSearchEngineSheetContent
import com.petal.browser.ui.components.StrideSlider
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.availableSearchEngines
import com.petal.browser.ui.theme.*

object PetalSettingsBridge {
    @JvmStatic
    fun createSettingsView(activity: ComponentActivity, onBackPress: () -> Unit): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX") }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 100f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 400)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 0f)) }
                var presetName by remember { mutableStateOf(sp.getString("sp_gs_flex_preset", "DEFAULT") ?: "DEFAULT") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var themeConfigName by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 100f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 400)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 0f)
                            "sp_gs_flex_preset" -> presetName = sp.getString("sp_gs_flex_preset", "DEFAULT") ?: "DEFAULT"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_expressive_colors" -> {}
                            "sp_expressive_feature_tiles" -> {}
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) {
                    try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.GS_FLEX }
                }
                val gsFlexPreset = remember(presetName) {
                    try { GSFlexPreset.valueOf(presetName) } catch (e: Exception) { GSFlexPreset.ZENITH }
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }
                val themeConfig = remember(themeConfigName) {
                    try { ThemeConfig.valueOf(themeConfigName) } catch (e: Exception) { ThemeConfig.FOLLOW_SYSTEM }
                }

                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                val isDarkTheme = when (themeConfig) {
                    ThemeConfig.FOLLOW_SYSTEM -> systemDark
                    ThemeConfig.LIGHT -> false
                    ThemeConfig.DARK -> true
                }

                PetalExpressiveTheme(
                    darkTheme = isDarkTheme,
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal,
                    gsFlexPreset = gsFlexPreset,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalSettingsScreen(onBackPress = onBackPress)
                }
            }
        }
    }
}

enum class SettingsCategory(val title: String, val subtitle: String, val icon: ImageVector) {
    OVERVIEW("Settings", "Browse all settings categories", Icons.Rounded.Settings),
    APPEARANCE("Appearance & Theme", "Fonts, theme modes, color palettes, AMOLED & Material You", Icons.Rounded.Palette),
    PRIVACY("Privacy & Security", "AdBlock, HTTPS-only, Private DNS & cookies", Icons.Rounded.Shield),
    SEARCH_HOMEPAGE("Search Engine & Home", "Default search engine, custom homepage", Icons.Rounded.Search),
    DISPLAY_ZOOM("Accessibility", "Touch haptics, text font scaling and page zoom preview", Icons.Rounded.Accessibility),
    DATA_STORAGE("Data & Backup", "Backup and restore history, bookmarks & settings", Icons.Rounded.Backup),
    UPDATER("App Updates", "Check for updates and auto-check on launch", Icons.Rounded.SystemUpdate),
    ABOUT("About & Developer", "App version, licenses, GitHub & developer", Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSettingsScreen(onBackPress: () -> Unit = {}) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val appVersionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) { "1.0.0" }
    }
    val appVersionCode = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) { 100L }
    }

    var currentCategory by remember { mutableStateOf(SettingsCategory.OVERVIEW) }
    var searchQuery by remember { mutableStateOf("") }

    // Saved Preference States
    var selectedFont by remember {
        mutableStateOf(try { AppFont.valueOf(sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX") } catch (e: Exception) { AppFont.GS_FLEX })
    }
    var selectedPreset by remember {
        mutableStateOf(try { GSFlexPreset.valueOf(sp.getString("sp_gs_flex_preset", "ZENITH") ?: "ZENITH") } catch (e: Exception) { GSFlexPreset.ZENITH })
    }
    var fontWidth by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 100f)) }
    var fontWeight by remember { mutableFloatStateOf(sp.getInt("sp_font_weight", 400).toFloat()) }
    var fontRoundness by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 0f)) }
    var selectedColorStyle by remember {
        mutableStateOf(try { ColorStyle.valueOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") } catch (e: Exception) { ColorStyle.TONAL_SPOT })
    }
    var selectedPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
    var selectedThemeConfig by remember {
        mutableStateOf(try { ThemeConfig.valueOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") } catch (e: Exception) { ThemeConfig.FOLLOW_SYSTEM })
    }
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var isDynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
    var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
    var isExpressiveFeatureTiles by remember { mutableStateOf(sp.getBoolean("sp_expressive_feature_tiles", true)) }

    // Private DNS & Language States
    var privateDnsMode by remember { mutableStateOf(sp.getString("sp_private_dns_mode", "OFF") ?: "OFF") }
    var appLanguage by remember { mutableStateOf(sp.getString("sp_app_language", "system") ?: "system") }

    // Custom Homepage & Background Play
    var homepageType by remember { mutableStateOf(sp.getString("sp_home_type", "0") ?: "0") }
    var customHomeUrl by remember { mutableStateOf(sp.getString("sp_custom_homepage_url", "https://google.com") ?: "https://google.com") }
    var isBackgroundPlay by remember { mutableStateOf(sp.getBoolean("sp_background_play", false)) }
    var isAutoPip by remember { mutableStateOf(sp.getBoolean("sp_auto_pip", true)) }
    var isForceDarkMode by remember { mutableStateOf(sp.getBoolean("sp_force_dark_mode", false)) }

    // Protection & WebView States
    var isAdBlock by remember { mutableStateOf(sp.getBoolean("sp_ad_block", true)) }
    var isHttpsOnly by remember { mutableStateOf(sp.getBoolean("sp_https_only", true)) }
    var isJavaScript by remember { mutableStateOf(sp.getBoolean("sp_javascript", true)) }
    var isBlockPopups by remember { mutableStateOf(sp.getBoolean("sp_block_popups", true)) }
    var isAutoOpenApps by remember { mutableStateOf(sp.getBoolean("sp_auto_open_apps", true)) }
    var isCheckUpdateOnLaunch by remember { mutableStateOf(sp.getBoolean("sp_check_update_on_launch", true)) }
    var isTouchHaptics by remember { mutableStateOf(sp.getBoolean("sp_touch_haptics", true)) }
    var predictiveBackAnim by remember {
        mutableStateOf(
            com.petal.browser.animation.predictiveback.PredictiveBackAnimation.fromValueOrDefault(
                sp.getString("sp_predictive_back_anim", com.petal.browser.animation.predictiveback.PredictiveBackAnimation.CLASSIC.value) ?: "ksu_classic"
            )
        )
    }
    var predictiveBackExitDir by remember {
        mutableStateOf(
            com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.fromValueOrDefault(
                sp.getString("sp_predictive_back_exit_dir", com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.ALWAYS_RIGHT.value) ?: "always_right"
            )
        )
    }
    var addressBarPosition by remember { mutableStateOf(sp.getString("sp_address_bar_position", "TOP") ?: "TOP") }
    var fontSize by remember { mutableFloatStateOf(sp.getFloat("sp_font_size_scale", 1.0f)) }
    var zoomLevel by remember { mutableFloatStateOf(sp.getFloat("sp_zoom_level_scale", 1.0f)) }
    var searchEngineIndex by remember { mutableStateOf(sp.getString("sp_search_engine", "0") ?: "0") }
    var showEngineSheet by remember { mutableStateOf(false) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_expressive_feature_tiles" -> {
                    isExpressiveFeatureTiles = sp.getBoolean("sp_expressive_feature_tiles", true)
                }
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (showEngineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEngineSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            PetalSearchEngineSheetContent(
                onConfirm = { idx ->
                    sp.edit().putString("sp_search_engine", idx.toString()).apply()
                    searchEngineIndex = idx.toString()
                    showEngineSheet = false
                },
                onCancel = { showEngineSheet = false }
            )
        }
    }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var backIsLeftEdge by remember { mutableStateOf(true) }
    val animatedBackProgress by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "SettingsBackProgress"
    )

    // Always keep this handler active - including on the Overview screen - so that a
    // back-swipe from Settings is never left for the Activity-level browser back logic
    // to handle. That logic knows nothing about Settings being open and would otherwise
    // fall straight through to "press back again to exit", skipping the home/current
    // site screen entirely. Chrome-style behavior: sub-page -> Overview -> browser
    // (home/current site) -> exit, one destination at a time.
    androidx.activity.compose.PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress = backEvent.progress
                backIsLeftEdge = backEvent.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT
            }
            backProgress = 0f
            if (currentCategory != SettingsCategory.OVERVIEW) {
                currentCategory = SettingsCategory.OVERVIEW
            } else {
                onBackPress()
            }
        } catch (e: Exception) {
            backProgress = 0f
        }
    }

    fun matchesSearch(sectionTitle: String, keywords: String): Boolean {
        if (searchQuery.isBlank()) return true
        val query = searchQuery.trim().lowercase()
        return sectionTitle.lowercase().contains(query) || keywords.lowercase().contains(query)
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (selectedThemeConfig) {
        ThemeConfig.FOLLOW_SYSTEM -> systemDark
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
    }

    PetalExpressiveTheme(
        darkTheme = isDarkTheme,
        dynamicColor = isDynamicColor,
        useAmoled = isAmoled,
        expressiveColors = isExpressiveColors,
        appFont = selectedFont,
        fontWidth = fontWidth,
        fontWeight = fontWeight.toInt(),
        fontRoundness = fontRoundness,
        gsFlexPreset = selectedPreset,
        colorStyle = selectedColorStyle,
        paletteId = selectedPaletteId
    ) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                    TopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = if (searchQuery.isNotBlank()) "Search Results" else currentCategory.title,
                                transitionSpec = {
                                    (fadeIn() + scaleIn(initialScale = 0.92f))
                                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.92f))
                                },
                                label = "ZenithHeaderTitleAnimation"
                            ) { titleText ->
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (currentCategory != SettingsCategory.OVERVIEW) {
                                        currentCategory = SettingsCategory.OVERVIEW
                                    } else {
                                        onBackPress()
                                    }
                                },
                                modifier = Modifier.bouncyClickable {
                                    if (currentCategory != SettingsCategory.OVERVIEW) {
                                        currentCategory = SettingsCategory.OVERVIEW
                                    } else {
                                        onBackPress()
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )

                    // 🔍 Settings Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        placeholder = { Text("Search settings...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            // Reflects whichever Predictive Back Animation style is selected above, so the
            // Settings screen itself previews the same AOSP / MIUIX / Scale / Classic / None
            // feel that's applied to the browser's own back gesture.
            val backFrame = com.petal.browser.animation.predictiveback.PredictiveBackStyle.frameFor(
                animation = predictiveBackAnim,
                exitDirection = predictiveBackExitDir,
                progress = animatedBackProgress,
                isLeftEdge = backIsLeftEdge,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .graphicsLayer {
                        scaleX = backFrame.scale
                        scaleY = backFrame.scale
                        this.alpha = backFrame.alpha
                        translationX = backFrame.translationXDp.dp.toPx()
                        clip = animatedBackProgress > 0.01f
                        shape = RoundedCornerShape(backFrame.cornerRadiusDp.dp)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                if (currentCategory == SettingsCategory.OVERVIEW && searchQuery.isBlank()) {
                    Text(
                        "Categories",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    val categories = listOf(
                        SettingsCategory.APPEARANCE,
                        SettingsCategory.PRIVACY,
                        SettingsCategory.SEARCH_HOMEPAGE,
                        SettingsCategory.DISPLAY_ZOOM,
                        SettingsCategory.DATA_STORAGE,
                        SettingsCategory.UPDATER,
                        SettingsCategory.ABOUT
                    )

                    val tileColorway = listOf(
                        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
                    )

                    categories.forEachIndexed { index, cat ->
                        if (isExpressiveFeatureTiles) {
                            val (container, onContainer) = tileColorway[index % tileColorway.size]
                            PetalFeatureTile(
                                title = cat.title,
                                subtitle = cat.subtitle,
                                icon = cat.icon,
                                container = container,
                                onContainer = onContainer,
                                onClick = { currentCategory = cat },
                            )
                        } else {
                            SettingsCategoryRow(
                                title = cat.title,
                                subtitle = cat.subtitle,
                                icon = cat.icon,
                                onClick = { currentCategory = cat }
                            )
                        }
                    }
                }

                // 1. Live Interactive Font & Accent Customization
                if ((currentCategory == SettingsCategory.APPEARANCE || searchQuery.isNotBlank()) && matchesSearch("Appearance", "fonts accent theme palette amoled")) {
                    SettingsCategoryCard(title = "Custom Fonts & Accent Themes", icon = Icons.Rounded.Palette) {
                        Text(
                            "Customize app typography and accent style",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // --- Theme Mode Chips (Light, Dark, System) ---
                        Text(
                            "App Theme Mode:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
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
                                    selected = selectedThemeConfig == config,
                                    onClick = {
                                        selectedThemeConfig = config
                                        sp.edit().putString("sp_theme_config", config.name).apply()
                                    },
                                    label = { Text(label) },
                                    leadingIcon = {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }

                        // --- Font Choice Chips ---
                        Text(
                            "Select Font Family:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppFont.values().forEach { font ->
                                FilterChip(
                                    selected = selectedFont == font,
                                    onClick = {
                                        selectedFont = font
                                        sp.edit().putString("sp_app_font", font.name).apply()
                                    },
                                    label = { Text(font.label) },
                                    leadingIcon = if (selectedFont == font) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        // --- Accent Style Chips ---
                        Text(
                            "Select Accent Color Style:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ColorStyle.values().forEach { style ->
                                FilterChip(
                                    selected = selectedColorStyle == style,
                                    onClick = {
                                        selectedColorStyle = style
                                        sp.edit().putString("sp_color_style", style.name).apply()
                                    },
                                    label = { Text(style.label) },
                                    leadingIcon = if (selectedColorStyle == style) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        // --- Preset Palette Seeds ---
                        Text(
                            "Preset Color Palettes:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PetalPalettes.forEach { pal ->
                                val isSelected = selectedPaletteId == pal.id && !isDynamicColor
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
                                            selectedPaletteId = pal.id
                                            isDynamicColor = false
                                            sp.edit().putString("sp_palette_id", pal.id).putBoolean("useDynamicColor", false).apply()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, contentDescription = pal.label, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // AMOLED Black Toggle
                        ToggleRow(
                            title = "AMOLED Black Dark Mode",
                            subtitle = "Pure black background ladder for OLED displays",
                            icon = Icons.Rounded.DarkMode,
                            checked = isAmoled,
                            onCheckedChange = { newValue ->
                                isAmoled = newValue
                                sp.edit().putBoolean("sp_amoled", newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Dynamic Color Toggle
                        ToggleRow(
                            title = "Material You Dynamic Colors",
                            subtitle = "Extract system accent wallpaper colors on Android 12+",
                            icon = Icons.Rounded.ColorLens,
                            checked = isDynamicColor,
                            onCheckedChange = { newValue ->
                                isDynamicColor = newValue
                                sp.edit().putBoolean("useDynamicColor", newValue).apply()
                            }
                        )

                        // Expressive Colors Toggle
                        ToggleRow(
                            title = "Expressive Container Colors",
                            subtitle = "Use vibrant container tint contrast for background and surfaces",
                            icon = Icons.Rounded.Palette,
                            checked = isExpressiveColors,
                            onCheckedChange = { newValue ->
                                isExpressiveColors = newValue
                                sp.edit().putBoolean("sp_expressive_colors", newValue).apply()
                            }
                        )

                        // Expressive Feature Tiles Toggle
                        ToggleRow(
                            title = "Expressive Feature Tiles",
                            subtitle = "Use scalloped icon cards for settings and account actions instead of plain rows",
                            icon = Icons.Rounded.GridView,
                            checked = isExpressiveFeatureTiles,
                            onCheckedChange = { newValue ->
                                isExpressiveFeatureTiles = newValue
                                sp.edit().putBoolean("sp_expressive_feature_tiles", newValue).apply()
                            }
                        )

                    }
                }

                // 2. Custom Homepage & Background Play
                if ((currentCategory == SettingsCategory.SEARCH_HOMEPAGE || searchQuery.isNotBlank()) && matchesSearch("Homepage", "custom home start page background play video audio media")) {
                    SettingsCategoryCard(title = "Homepage & Media Playback", icon = Icons.Rounded.Home) {
                        Text(
                            "Custom Homepage:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = homepageType == "0",
                                onClick = {
                                    homepageType = "0"
                                    sp.edit().putString("sp_home_type", "0").apply()
                                },
                                label = { Text("Petal Start Page") }
                            )
                            FilterChip(
                                selected = homepageType == "1",
                                onClick = {
                                    homepageType = "1"
                                    sp.edit().putString("sp_home_type", "1").apply()
                                },
                                label = { Text("Custom URL") }
                            )
                        }

                        if (homepageType == "1") {
                            OutlinedTextField(
                                value = customHomeUrl,
                                onValueChange = {
                                    customHomeUrl = it
                                    sp.edit().putString("sp_custom_homepage_url", it).apply()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Enter Homepage URL") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Background Video & Audio Playback Toggle
                        ToggleRow(
                            title = "Background Audio & Video Playback",
                            subtitle = "Keep YouTube & web media playing when switching tabs or backgrounding app",
                            icon = Icons.Rounded.PlayCircle,
                            checked = isBackgroundPlay,
                            onCheckedChange = { newValue ->
                                isBackgroundPlay = newValue
                                sp.edit().putBoolean("sp_background_play", newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Auto Picture-in-Picture Toggle
                        val isPipSupported = remember {
                            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                        }
                        ToggleRow(
                            title = if (isPipSupported) "Auto Picture-in-Picture (PiP)" else "Auto Picture-in-Picture (Not Supported)",
                            subtitle = if (isPipSupported) "Automatically enter floating PiP window when leaving app during video playback" else "Picture-in-Picture mode is not supported on this device",
                            icon = Icons.Rounded.PictureInPicture,
                            checked = isAutoPip && isPipSupported,
                            onCheckedChange = { newValue ->
                                if (isPipSupported) {
                                    isAutoPip = newValue
                                    sp.edit().putBoolean("sp_auto_pip", newValue).apply()
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Force Dark Mode for Web Content Toggle
                        ToggleRow(
                            title = "Force Dark Web Content",
                            subtitle = "Automatically apply dark themes to websites that do not natively support dark mode",
                            icon = Icons.Rounded.DarkMode,
                            checked = isForceDarkMode,
                            onCheckedChange = { newValue ->
                                isForceDarkMode = newValue
                                sp.edit().putBoolean("sp_force_dark_mode", newValue).apply()
                            }
                        )
                    }
                }

                // 3. Private DNS Options
                if ((currentCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Private DNS", "dns cleanbrowsing cloudflare 1.1.1.1 google opendns security filter")) {
                    SettingsCategoryCard(title = "Private DNS Protection", icon = Icons.Rounded.Dns) {
                        Text(
                            "Encrypt DNS queries to prevent tracking & block malicious content:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val dnsOptions = listOf(
                            Triple("OFF", "System Default (Off)", "Use default network DNS"),
                            Triple("CLOUDFLARE", "Cloudflare (1.1.1.1)", "Fast & private 1.1.1.1 DNS over HTTPS"),
                            Triple("GOOGLE", "Google Public DNS", "8.8.8.8 high performance resolution"),
                            Triple("CLEANBROWSING", "CleanBrowsing Family Filter", "Blocks adult & malicious sites"),
                            Triple("OPENDNS", "OpenDNS Home", "Cisco OpenDNS security protection")
                        )

                        dnsOptions.forEach { (mode, name, desc) ->
                            Surface(
                                onClick = {
                                    privateDnsMode = mode
                                    sp.edit().putString("sp_private_dns_mode", mode).apply()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (privateDnsMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (privateDnsMode == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (privateDnsMode == mode) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (privateDnsMode == mode) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val intents = listOf(
                                    Intent("android.settings.PRIVATE_DNS_SETTINGS"),
                                    Intent(Settings.ACTION_WIRELESS_SETTINGS),
                                    Intent(Settings.ACTION_SETTINGS)
                                )
                                for (intent in intents) {
                                    try {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                        break
                                    } catch (e: Exception) {
                                        // continue to next fallback
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Configure Android System Private DNS")
                        }
                    }
                }

                // 4. Popular Languages Selector
                if ((currentCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Language", "languages popular english spanish hindi french german chinese arabic portuguese russian japanese")) {
                    SettingsCategoryCard(title = "App Language", icon = Icons.Rounded.Language) {
                        Text(
                            "Choose your preferred display language:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val languages = listOf(
                            Pair("system", "System Default"),
                            Pair("en", "English"),
                            Pair("es", "Español (Spanish)"),
                            Pair("hi", "हिन्दी (Hindi)"),
                            Pair("fr", "Français (French)"),
                            Pair("de", "Deutsch (German)"),
                            Pair("zh", "中文 (Chinese)"),
                            Pair("ar", "العربية (Arabic)"),
                            Pair("pt", "Português (Portuguese)"),
                            Pair("ru", "Русский (Russian)"),
                            Pair("ja", "日本語 (Japanese)")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            languages.forEach { (tag, label) ->
                                FilterChip(
                                    selected = appLanguage == tag,
                                    onClick = {
                                        appLanguage = tag
                                        sp.edit().putString("sp_app_language", tag).apply()
                                        val localeList = if (tag == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                                        AppCompatDelegate.setApplicationLocales(localeList)
                                    },
                                    label = { Text(label) },
                                    leadingIcon = if (appLanguage == tag) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // 5. Default Search Engine Section
                if ((currentCategory == SettingsCategory.SEARCH_HOMEPAGE || searchQuery.isNotBlank()) && matchesSearch("Search Engine", "google duckduckgo bing brave startpage ecosia search provider")) {
                    SettingsCategoryCard(title = "Default Search Engine", icon = Icons.Rounded.Search) {
                        val currentEngineName = remember(searchEngineIndex) {
                            val idx = searchEngineIndex.toIntOrNull() ?: 0
                            availableSearchEngines.find { it.index == idx }?.name ?: "Google"
                        }
                        Surface(
                            onClick = { showEngineSheet = true },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Default Search Provider",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currentEngineName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 6. Privacy & Shield Section
                if ((currentCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Privacy Shield", "adblock tracker popups https javascript external apps protection")) {
                    SettingsCategoryCard(title = "Privacy & Shield Protection", icon = Icons.Rounded.Shield) {
                        ToggleRow(
                            title = "Ad & Tracker Shield",
                            subtitle = "Block invasive ads, popunders, and web trackers",
                            icon = Icons.Rounded.Shield,
                            checked = isAdBlock,
                            onCheckedChange = { newValue ->
                                isAdBlock = newValue
                                sp.edit().putBoolean("sp_ad_block", newValue).putBoolean("profileStandard_adBlock", newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "Block Popup Windows",
                            subtitle = "Prevent unwanted popups and redirect windows",
                            icon = Icons.Rounded.OpenInNew,
                            checked = isBlockPopups,
                            onCheckedChange = { newValue ->
                                isBlockPopups = newValue
                                sp.edit().putBoolean("sp_block_popups", newValue).putBoolean("profileStandard_javascriptPopUp", !newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "Auto Open External Apps",
                            subtitle = "Open YouTube, Maps & Play Store links in native apps",
                            icon = Icons.Rounded.Launch,
                            checked = isAutoOpenApps,
                            onCheckedChange = { newValue ->
                                isAutoOpenApps = newValue
                                sp.edit().putBoolean("sp_auto_open_apps", newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "HTTPS Security Enforcer",
                            subtitle = "Automatically upgrade connections to HTTPS",
                            icon = Icons.Rounded.Lock,
                            checked = isHttpsOnly,
                            onCheckedChange = { newValue ->
                                isHttpsOnly = newValue
                                sp.edit().putBoolean("sp_https_only", newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "Enable JavaScript",
                            subtitle = "Required for modern web features",
                            icon = Icons.Rounded.Code,
                            checked = isJavaScript,
                            onCheckedChange = { newValue ->
                                isJavaScript = newValue
                                sp.edit().putBoolean("sp_javascript", newValue).putBoolean("profileStandard_javascript", newValue).apply()
                            }
                        )
                    }
                }

                // 7. Accessibility & Scaling (using StrideSlider)
                if ((currentCategory == SettingsCategory.DISPLAY_ZOOM || searchQuery.isNotBlank()) && matchesSearch("Accessibility", "haptics touch vibration text font scale page zoom text scaling stride slider blur address bar top bottom")) {
                    SettingsCategoryCard(title = "Accessibility & Display Options", icon = Icons.Rounded.Accessibility) {
                        ToggleRow(
                            title = "Touch Haptics",
                            subtitle = "Vibrate with tactile feedback on button presses throughout the app",
                            icon = Icons.Rounded.Vibration,
                            checked = isTouchHaptics,
                            onCheckedChange = { newValue ->
                                isTouchHaptics = newValue
                                sp.edit().putBoolean("sp_touch_haptics", newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        val isPredictiveAnimEnabled = predictiveBackAnim != com.petal.browser.animation.predictiveback.PredictiveBackAnimation.NONE
                        ToggleRow(
                            title = "Predictive Back Animation",
                            subtitle = if (isPredictiveAnimEnabled) "Enabled predictive gesture animations" else "Disabled predictive gesture animations",
                            icon = Icons.Rounded.Gesture,
                            checked = isPredictiveAnimEnabled,
                            onCheckedChange = { enabled ->
                                val targetAnim = if (enabled) {
                                    com.petal.browser.animation.predictiveback.PredictiveBackAnimation.CLASSIC
                                } else {
                                    com.petal.browser.animation.predictiveback.PredictiveBackAnimation.NONE
                                }
                                predictiveBackAnim = targetAnim
                                sp.edit().putString("sp_predictive_back_anim", targetAnim.value).apply()
                            }
                        )

                        if (predictiveBackAnim == com.petal.browser.animation.predictiveback.PredictiveBackAnimation.SCALE) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Predictive Exit Direction:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.entries.forEach { dir ->
                                    FilterChip(
                                        selected = predictiveBackExitDir == dir,
                                        onClick = {
                                            predictiveBackExitDir = dir
                                            sp.edit().putString("sp_predictive_back_exit_dir", dir.value).apply()
                                        },
                                        label = { Text(dir.label) },
                                        leadingIcon = if (predictiveBackExitDir == dir) {
                                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Text(
                            "Address Bar Location:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = addressBarPosition == "TOP",
                                onClick = {
                                    addressBarPosition = "TOP"
                                    sp.edit().putString("sp_address_bar_position", "TOP").apply()
                                    if (context is ComponentActivity && context is com.petal.browser.activity.BrowserActivity) {
                                        (context as com.petal.browser.activity.BrowserActivity).applyAddressBarPosition()
                                    }
                                },
                                label = { Text("Top (Default)") },
                                leadingIcon = if (addressBarPosition == "TOP") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = addressBarPosition == "BOTTOM",
                                onClick = {
                                    addressBarPosition = "BOTTOM"
                                    sp.edit().putString("sp_address_bar_position", "BOTTOM").apply()
                                    if (context is ComponentActivity && context is com.petal.browser.activity.BrowserActivity) {
                                        (context as com.petal.browser.activity.BrowserActivity).applyAddressBarPosition()
                                    }
                                },
                                label = { Text("Bottom") },
                                leadingIcon = if (addressBarPosition == "BOTTOM") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Text Font Scale", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${(fontSize * 100f).toInt()}%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(10.dp))
                            StrideSlider(
                                value = fontSize,
                                onValueChange = { newValue ->
                                    fontSize = newValue
                                    sp.edit().putFloat("sp_font_size_scale", newValue).apply()
                                },
                                valueRange = 0.7f..1.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            // LIVE TEXT SCALE PREVIEW BOX
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "LIVE FONT PREVIEW (${(fontSize * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "The quick brown fox jumps over the lazy dog.",
                                        fontSize = (15 * fontSize).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }



                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Default Page Zoom", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${(zoomLevel * 100f).toInt()}%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(10.dp))
                            StrideSlider(
                                value = zoomLevel,
                                onValueChange = { newValue ->
                                    zoomLevel = newValue
                                    sp.edit().putFloat("sp_zoom_level_scale", newValue).apply()
                                },
                                valueRange = 0.8f..2.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            // LIVE PAGE ZOOM PREVIEW BOX
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "LIVE ZOOM PREVIEW (${(zoomLevel * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth().height((75 * zoomLevel).dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size((12 * zoomLevel).dp)) {}
                                                Text(
                                                    "Sample Web Page Article",
                                                    fontSize = (12 * zoomLevel).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                "Rendering responsive web content at ${(zoomLevel * 100).toInt()}% zoom scale.",
                                                fontSize = (10 * zoomLevel).sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 8. Full Backup & Sync Section
                var showBackupDialog by remember { mutableStateOf(false) }
                var showRestoreDialog by remember { mutableStateOf(false) }

                var backupBookmarks by remember { mutableStateOf(true) }
                var backupHistory by remember { mutableStateOf(true) }
                var backupSavedSites by remember { mutableStateOf(true) }
                var backupSettings by remember { mutableStateOf(true) }

                var restoreBookmarks by remember { mutableStateOf(true) }
                var restoreHistory by remember { mutableStateOf(true) }
                var restoreSavedSites by remember { mutableStateOf(true) }
                var restoreSettings by remember { mutableStateOf(true) }

                val createBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
                ) { uri: android.net.Uri? ->
                    if (uri != null) {
                        com.petal.browser.unit.BackupUnit.backupToUri(
                            context,
                            uri,
                            backupBookmarks,
                            backupHistory,
                            backupSavedSites,
                            backupSettings
                        )
                    }
                }

                val openRestoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri: android.net.Uri? ->
                    if (uri != null) {
                        com.petal.browser.unit.BackupUnit.restoreFromUri(
                            context,
                            uri,
                            restoreBookmarks,
                            restoreHistory,
                            restoreSavedSites,
                            restoreSettings
                        )
                    }
                }

                if (showBackupDialog) {
                    AlertDialog(
                        onDismissRequest = { showBackupDialog = false },
                        title = { Text("Backup Options (JSON)") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select items to include in backup file:")
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupBookmarks = !backupBookmarks }) {
                                    Checkbox(checked = backupBookmarks, onCheckedChange = { backupBookmarks = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Bookmarks")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupHistory = !backupHistory }) {
                                    Checkbox(checked = backupHistory, onCheckedChange = { backupHistory = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Browsing History")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupSavedSites = !backupSavedSites }) {
                                    Checkbox(checked = backupSavedSites, onCheckedChange = { backupSavedSites = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Saved Startsite Webpages")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backupSettings = !backupSettings }) {
                                    Checkbox(checked = backupSettings, onCheckedChange = { backupSettings = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Browser & Theme Settings")
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showBackupDialog = false
                                createBackupLauncher.launch("petal_browser_backup.json")
                            }) {
                                Text("Choose Save Folder")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBackupDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showRestoreDialog) {
                    AlertDialog(
                        onDismissRequest = { showRestoreDialog = false },
                        title = { Text("Restore Options (JSON)") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Select items to restore from JSON file:")
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreBookmarks = !restoreBookmarks }) {
                                    Checkbox(checked = restoreBookmarks, onCheckedChange = { restoreBookmarks = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Bookmarks")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreHistory = !restoreHistory }) {
                                    Checkbox(checked = restoreHistory, onCheckedChange = { restoreHistory = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Browsing History")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreSavedSites = !restoreSavedSites }) {
                                    Checkbox(checked = restoreSavedSites, onCheckedChange = { restoreSavedSites = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Saved Startsite Webpages")
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { restoreSettings = !restoreSettings }) {
                                    Checkbox(checked = restoreSettings, onCheckedChange = { restoreSettings = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Browser & Theme Settings")
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                showRestoreDialog = false
                                openRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                            }) {
                                Text("Choose Backup File")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestoreDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if ((currentCategory == SettingsCategory.DATA_STORAGE || searchQuery.isNotBlank()) && matchesSearch("Backup Sync", "backup restore sync history bookmarks settings database export import json")) {
                    SettingsCategoryCard(title = "Backup & Restore (JSON)", icon = Icons.Rounded.Backup) {
                        Text(
                            "Export or restore specific items to/from a single JSON file (Documents/browser_backup/petal_browser_backup.json):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { showBackupDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Backup...")
                            }

                            OutlinedButton(
                                onClick = { showRestoreDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore...")
                            }
                        }
                    }
                }

                // API Integration & Services Settings
                if ((currentCategory == SettingsCategory.PRIVACY || currentCategory == SettingsCategory.GENERAL || searchQuery.isNotBlank()) && matchesSearch("API Services", "api search suggestions reader wayback translation google bing duckduckgo")) {
                    SettingsCategoryCard(title = "API Integrations & Web Services", icon = Icons.Rounded.Api) {
                        var enableLiveSuggestions by remember { mutableStateOf(sp.getBoolean("sp_enable_live_suggestions", true)) }
                        var enableReaderApi by remember { mutableStateOf(sp.getBoolean("sp_enable_reader_api", true)) }
                        var enableWaybackApi by remember { mutableStateOf(sp.getBoolean("sp_enable_wayback_api", true)) }

                        ToggleRow(
                            title = "Live Search Recommendations",
                            subtitle = "Fetch live autocomplete suggestions from Google, DuckDuckGo, or Bing",
                            icon = Icons.Rounded.Search,
                            checked = enableLiveSuggestions,
                            onCheckedChange = {
                                enableLiveSuggestions = it
                                sp.edit().putBoolean("sp_enable_live_suggestions", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "Readability Reader Mode API",
                            subtitle = "Automatically parse uncluttered article view for blogs and news sites",
                            icon = Icons.Rounded.Article,
                            checked = enableReaderApi,
                            onCheckedChange = {
                                enableReaderApi = it
                                sp.edit().putBoolean("sp_enable_reader_api", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "Wayback Machine Snapshot API",
                            subtitle = "Offer Internet Archive snapshots when encountering broken links or 404 pages",
                            icon = Icons.Rounded.History,
                            checked = enableWaybackApi,
                            onCheckedChange = {
                                enableWaybackApi = it
                                sp.edit().putBoolean("sp_enable_wayback_api", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        var enableDuckAssistApi by remember { mutableStateOf(sp.getBoolean("sp_enable_duck_assist", true)) }
                        var enableSslAuditApi by remember { mutableStateOf(sp.getBoolean("sp_enable_ssl_audit", true)) }

                        ToggleRow(
                            title = "DuckAssist Instant Answer API",
                            subtitle = "Fetch instant topic summaries and definition boxes for search queries",
                            icon = Icons.Rounded.Lightbulb,
                            checked = enableDuckAssistApi,
                            onCheckedChange = {
                                enableDuckAssistApi = it
                                sp.edit().putBoolean("sp_enable_duck_assist", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "DNS & SSL Health Audit API",
                            subtitle = "Resolve DoH IP records and audit domain security in Site Info sheet",
                            icon = Icons.Rounded.Security,
                            checked = enableSslAuditApi,
                            onCheckedChange = {
                                enableSslAuditApi = it
                                sp.edit().putBoolean("sp_enable_ssl_audit", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        var enableDictionaryApi by remember { mutableStateOf(sp.getBoolean("sp_enable_dictionary", true)) }
                        var enableWeatherApi by remember { mutableStateOf(sp.getBoolean("sp_enable_weather", true)) }

                        ToggleRow(
                            title = "English Dictionary Lookup API",
                            subtitle = "Instant word definitions and phonetic pronunciations on text selection",
                            icon = Icons.Rounded.Translate,
                            checked = enableDictionaryApi,
                            onCheckedChange = {
                                enableDictionaryApi = it
                                sp.edit().putBoolean("sp_enable_dictionary", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "Privacy-Friendly Weather API",
                            subtitle = "Lightweight offline-first weather status headers via wttr.in",
                            icon = Icons.Rounded.Cloud,
                            checked = enableWeatherApi,
                            onCheckedChange = {
                                enableWeatherApi = it
                                sp.edit().putBoolean("sp_enable_weather", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        var enablePasswordBreachApi by remember { mutableStateOf(sp.getBoolean("sp_enable_hibp_breach", true)) }
                        var enableUnsplashWallpaperApi by remember { mutableStateOf(sp.getBoolean("sp_enable_unsplash_wallpapers", true)) }

                        ToggleRow(
                            title = "Password Breach Audit API (HIBP)",
                            subtitle = "K-anonymity hash lookup to audit saved passwords against data breaches",
                            icon = Icons.Rounded.Lock,
                            checked = enablePasswordBreachApi,
                            onCheckedChange = {
                                enablePasswordBreachApi = it
                                sp.edit().putBoolean("sp_enable_hibp_breach", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        ToggleRow(
                            title = "Dynamic Unsplash Wallpapers API",
                            subtitle = "Fetch daily high-resolution wallpapers for the browser Home Screen",
                            icon = Icons.Rounded.Image,
                            checked = enableUnsplashWallpaperApi,
                            onCheckedChange = {
                                enableUnsplashWallpaperApi = it
                                sp.edit().putBoolean("sp_enable_unsplash_wallpapers", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        var enableWhoisRdapApi by remember { mutableStateOf(sp.getBoolean("sp_enable_whois_rdap", true)) }

                        ToggleRow(
                            title = "Whois & RDAP Domain Trust Audit API",
                            subtitle = "Inspect domain registration, owner, and registrar info in Site Info sheet",
                            icon = Icons.Rounded.VerifiedUser,
                            checked = enableWhoisRdapApi,
                            onCheckedChange = {
                                enableWhoisRdapApi = it
                                sp.edit().putBoolean("sp_enable_whois_rdap", it).apply()
                            }
                        )
                    }
                }

                // 9. App Updates & Inbuilt Updater Section
                if ((currentCategory == SettingsCategory.UPDATER || searchQuery.isNotBlank()) && matchesSearch("App Updates", "update updater version check launch github download upgrade")) {
                    SettingsCategoryCard(title = "App Updates & Inbuilt Updater", icon = Icons.Rounded.SystemUpdate) {
                        ToggleRow(
                            title = "Check for Updates on Launch",
                            subtitle = "Automatically check for new browser releases when app starts",
                            icon = Icons.Rounded.SystemUpdate,
                            checked = isCheckUpdateOnLaunch,
                            onCheckedChange = { newValue ->
                                isCheckUpdateOnLaunch = newValue
                                sp.edit().putBoolean("sp_check_update_on_launch", newValue).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Check for Updates Now",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Current Version: v$appVersionName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (context is ComponentActivity) {
                                            com.petal.browser.unit.UpdateUnit.checkForUpdates(context, false)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Check Now")
                                }
                            }
                        }
                    }
                }

                // 9. About App & About Developer Sections
                if ((currentCategory == SettingsCategory.ABOUT || searchQuery.isNotBlank()) && matchesSearch("About", "app developer version github licenses terms open source")) {
                    SettingsCategoryCard(title = "About App & Developer", icon = Icons.Rounded.Info) {
                        // About App Subcard
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    Column {
                                        Text("Petal Browser", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("v$appVersionName (Build $appVersionCode)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Text(
                                    "A modern, lightning fast, privacy-focused Android Web Browser built with Jetpack Compose & Material 3 Expressive UI. Includes Stride typography, Private DNS, Real AdBlock engine, and fluid motion physics.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // About Developer Subcard
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    Column {
                                        Text("About Developer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text("Crafted with ❤ for Android & Termux", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    com.petal.browser.unit.BrowserUnit.intentURL(context, Uri.parse("https://github.com/shreyagarwal72/"))
                                                } catch (e: Exception) { e.printStackTrace() }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("GitHub", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    com.petal.browser.unit.BrowserUnit.intentURL(context, Uri.parse("https://github.com/shreyagarwal72/foss_browser/"))
                                                } catch (e: Exception) { e.printStackTrace() }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Source", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    com.petal.browser.unit.BrowserUnit.intentURL(context, Uri.parse("https://t.me/championworkspace"))
                                                } catch (e: Exception) { e.printStackTrace() }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Telegram", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }

                                        Button(
                                            onClick = {
                                                try {
                                                    com.petal.browser.unit.BrowserUnit.intentURL(context, Uri.parse("https://github.com/shreyagarwal72/foss_browser/issues"))
                                                } catch (e: Exception) { e.printStackTrace() }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        ) {
                                            Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("Feedback", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconSwitch(
            checked = checked,
            icon = icon,
            onCheckedChange = onCheckedChange
        )
    }
}
