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
import com.petal.browser.settings.AppPreferences
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

                val appFontState by AppPreferences.appFontFlow.collectAsState()
                val fontWidthVal by AppPreferences.fontWidthFlow.collectAsState()
                val fontWeightVal by AppPreferences.fontWeightFlow.collectAsState()
                val fontRoundnessVal by AppPreferences.fontRoundnessFlow.collectAsState()
                val gsFlexPresetState by AppPreferences.gsFlexPresetFlow.collectAsState()
                val colorStyleState by AppPreferences.colorStyleFlow.collectAsState()
                val paletteId by AppPreferences.paletteIdFlow.collectAsState()
                val dynamicColor by AppPreferences.isDynamicColorFlow.collectAsState()
                val isAmoled by AppPreferences.isAmoledFlow.collectAsState()
                val themeConfigState by AppPreferences.themeConfigFlow.collectAsState()

                val appFont = appFontState
                val gsFlexPreset = gsFlexPresetState
                val colorStyle = colorStyleState
                val themeConfig = themeConfigState

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
    var selectedFont by remember { mutableStateOf(AppPreferences.getAppFont(context)) }
    var selectedPreset by remember { mutableStateOf(AppPreferences.getGsFlexPreset(context)) }
    var fontWidth by remember { mutableFloatStateOf(AppPreferences.getFontWidth(context)) }
    var fontWeight by remember { mutableFloatStateOf(AppPreferences.getFontWeight(context)) }
    var fontRoundness by remember { mutableFloatStateOf(AppPreferences.getFontRoundness(context)) }
    var selectedColorStyle by remember { mutableStateOf(AppPreferences.getColorStyle(context)) }
    var selectedPaletteId by remember { mutableStateOf(AppPreferences.getPaletteId(context)) }
    var selectedThemeConfig by remember { mutableStateOf(AppPreferences.getThemeConfig(context)) }
    var isAmoled by remember { mutableStateOf(AppPreferences.isAmoled(context)) }
    var isDynamicColor by remember { mutableStateOf(AppPreferences.isDynamicColor(context)) }
    var isExpressiveColors by remember { mutableStateOf(AppPreferences.isExpressiveColors(context)) }

    // Private DNS & Language States
    var privateDnsMode by remember { mutableStateOf(AppPreferences.getPrivateDnsMode(context)) }
    var appLanguage by remember { mutableStateOf(AppPreferences.getAppLanguage(context)) }

    // Custom Homepage & Background Play
    var homepageType by remember { mutableStateOf(AppPreferences.getHomeType(context)) }
    var customHomeUrl by remember { mutableStateOf(AppPreferences.getCustomHomepageUrl(context)) }
    var isBackgroundPlay by remember { mutableStateOf(AppPreferences.isBackgroundPlay(context)) }
    var isAutoPip by remember { mutableStateOf(AppPreferences.isAutoPip(context)) }
    var isForceDarkMode by remember { mutableStateOf(AppPreferences.isForceDarkMode(context)) }

    // Protection & WebView States
    var isAdBlock by remember { mutableStateOf(AppPreferences.isAdBlock(context)) }
    var isHttpsOnly by remember { mutableStateOf(AppPreferences.isHttpsOnly(context)) }
    var isJavaScript by remember { mutableStateOf(AppPreferences.isJavaScript(context)) }
    var isBlockPopups by remember { mutableStateOf(AppPreferences.isBlockPopups(context)) }
    var isAutoOpenApps by remember { mutableStateOf(AppPreferences.isAutoOpenApps(context)) }
    var isCheckUpdateOnLaunch by remember { mutableStateOf(AppPreferences.isCheckUpdateOnLaunch(context)) }
    var isTouchHaptics by remember { mutableStateOf(AppPreferences.isTouchHaptics(context)) }
    var predictiveBackAnim by remember { mutableStateOf(AppPreferences.getPredictiveBackAnim(context)) }
    var predictiveBackExitDir by remember { mutableStateOf(AppPreferences.getPredictiveBackExitDir(context)) }
    var addressBarPosition by remember { mutableStateOf(AppPreferences.getAddressBarPosition(context)) }
    var fontSize by remember { mutableFloatStateOf(AppPreferences.getFontSizeScale(context)) }
    var zoomLevel by remember { mutableFloatStateOf(AppPreferences.getZoomLevelScale(context)) }
    var searchEngineIndex by remember { mutableStateOf(AppPreferences.getSearchEngine(context)) }
    var showEngineSheet by remember { mutableStateOf(false) }

    if (showEngineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEngineSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            PetalSearchEngineSheetContent(
                onConfirm = { idx ->
                    AppPreferences.setSearchEngine(idx.toString(), context)
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
                        val (container, onContainer) = tileColorway[index % tileColorway.size]
                        PetalFeatureTile(
                            title = cat.title,
                            subtitle = cat.subtitle,
                            icon = cat.icon,
                            container = container,
                            onContainer = onContainer,
                            onClick = { currentCategory = cat },
                        )
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
                                        AppPreferences.setThemeConfig(config, context)
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
                                        AppPreferences.setAppFont(font, context)
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
                                        AppPreferences.setColorStyle(style, context)
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
                                            AppPreferences.setPaletteId(pal.id, context)
                                            AppPreferences.setDynamicColor(false, context)
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
                                AppPreferences.setAmoled(newValue, context)
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
                                AppPreferences.setDynamicColor(newValue, context)
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
                                AppPreferences.setExpressiveColors(newValue, context)
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
                                    AppPreferences.setHomeType("0", context)
                                },
                                label = { Text("Petal Start Page") }
                            )
                            FilterChip(
                                selected = homepageType == "1",
                                onClick = {
                                    homepageType = "1"
                                    AppPreferences.setHomeType("1", context)
                                },
                                label = { Text("Custom URL") }
                            )
                        }

                        if (homepageType == "1") {
                            OutlinedTextField(
                                value = customHomeUrl,
                                onValueChange = {
                                    customHomeUrl = it
                                    AppPreferences.setCustomHomepageUrl(it, context)
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
                                AppPreferences.setBackgroundPlay(newValue, context)
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
                                    AppPreferences.setAutoPip(newValue, context)
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
                                AppPreferences.setForceDarkMode(newValue, context)
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
                                    AppPreferences.setPrivateDnsMode(mode, context)
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
                                        AppPreferences.setAppLanguage(tag, context)
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
                                AppPreferences.setAdBlock(newValue, context)
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
                                AppPreferences.setBlockPopups(newValue, context)
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
                                AppPreferences.setAutoOpenApps(newValue, context)
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
                                AppPreferences.setHttpsOnly(newValue, context)
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
                                AppPreferences.setJavaScript(newValue, context)
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
                                AppPreferences.setTouchHaptics(newValue, context)
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Text(
                            "Predictive Back Animation:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.petal.browser.animation.predictiveback.PredictiveBackAnimation.entries.forEach { anim ->
                                FilterChip(
                                    selected = predictiveBackAnim == anim,
                                    onClick = {
                                        predictiveBackAnim = anim
                                        AppPreferences.setPredictiveBackAnim(anim, context)
                                    },
                                    label = { Text(anim.label) },
                                    leadingIcon = if (predictiveBackAnim == anim) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

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
                                            AppPreferences.setPredictiveBackExitDir(dir, context)
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
                                    AppPreferences.setAddressBarPosition("TOP", context)
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
                                    AppPreferences.setAddressBarPosition("BOTTOM", context)
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
                                    AppPreferences.setFontSizeScale(newValue, context)
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
                                    AppPreferences.setZoomLevelScale(newValue, context)
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
                                AppPreferences.setCheckUpdateOnLaunch(newValue, context)
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
