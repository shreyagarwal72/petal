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
 * 9. Font & Page Zoom Scaling Sliders (PetalSlider)
 * 10. About App & About Developer Sections
 */

package com.petal.browser.compose.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.petal.browser.activity.BrowserActivity
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.asImageBitmap
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
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.components.availableSearchEngines
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.*

object PetalSettingsBridge {
    @JvmStatic
    @JvmOverloads
    fun createSettingsView(activity: ComponentActivity, initialCategory: SettingsCategory = SettingsCategory.OVERVIEW, onBackPress: () -> Unit): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }
                var presetName by remember { mutableStateOf(sp.getString("sp_gs_flex_preset", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var themeConfigName by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                            "sp_gs_flex_preset" ->
                                presetName = sp.getString("sp_gs_flex_preset", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_theme_config" -> themeConfigName = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
                            "sp_expressive_colors" -> {}
                            "sp_expressive_feature_tiles" -> {}
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) {
                    try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.PETAL }
                }
                val resolvedPreset = remember(presetName) {
                    try { GSFlexPreset.valueOf(presetName) } catch (e: Exception) { GSFlexPreset.PETAL }
                }
                val gsFlexSettings = remember(presetName) {
                    GSFlexSettings(preset = resolvedPreset)
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
                    gsFlexSettings = gsFlexSettings,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    com.petal.browser.ui.components.ScreenWrapper {
                        PetalSettingsScreen(initialCategory = initialCategory, onBackPress = onBackPress)
                    }
                }
            }
        }
    }
}

enum class SettingsCategory(val title: String, val subtitle: String, val icon: ImageVector) {
    OVERVIEW("Settings", "Browse all settings categories", Icons.Rounded.Settings),
    API_INTEGRATIONS("API & Integrations Hub", "AndroidX WebKit, Google Credential Manager & Palette APIs", Icons.Rounded.Extension),
    APPEARANCE("Appearance & Theme", "Fonts, theme modes, color palettes, AMOLED & Material You", Icons.Rounded.Palette),
    PRIVACY("Privacy & Security", "AdBlock, HTTPS-only, Private DNS & cookies", Icons.Rounded.Shield),
    SEARCH_HOMEPAGE("Search Engine & Home", "Default search engine and custom homepage", Icons.Rounded.Search),
    DISPLAY_ZOOM("Accessibility", "Touch haptics, text font scaling and page zoom preview", Icons.Rounded.Accessibility),
    DATA_STORAGE("Data & Backup", "Backup and restore history, bookmarks & settings", Icons.Rounded.Backup),
    UPDATER("App Updates", "Check for updates and auto-check on launch", Icons.Rounded.SystemUpdate),
    ABOUT("About & Developer", "App version, licenses, GitHub & developer", Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSettingsScreen(
    initialCategory: SettingsCategory = SettingsCategory.OVERVIEW,
    onBackPress: () -> Unit = {}
) {
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

    var currentCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800L)
        isLoading = false
    }

    // Saved Preference States
    var selectedFont by remember {
        mutableStateOf(try { AppFont.valueOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") } catch (e: Exception) { AppFont.PETAL })
    }
    var selectedPreset by remember {
        mutableStateOf(try { GSFlexPreset.valueOf(sp.getString("sp_gs_flex_preset", "PETAL") ?: "PETAL") } catch (e: Exception) { GSFlexPreset.PETAL })
    }
    val selectedGSFlexSettings by remember(selectedPreset) {
        derivedStateOf { GSFlexSettings(preset = selectedPreset) }
    }
    var fontWidth by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
    var fontWeight by remember { mutableFloatStateOf(sp.getInt("sp_font_weight", 750).toFloat()) }
    var fontRoundness by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }
    var selectedColorStyle by remember {
        mutableStateOf(try { ColorStyle.valueOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") } catch (e: Exception) { ColorStyle.TONAL_SPOT })
    }
    var selectedPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
    var selectedThemeConfig by remember {
        mutableStateOf(try { ThemeConfig.valueOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") } catch (e: Exception) { ThemeConfig.FOLLOW_SYSTEM })
    }
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var isFloatingTabBar by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }
    var isDynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
    var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
    var isExpressiveFeatureTiles by remember { mutableStateOf(sp.getBoolean("sp_expressive_feature_tiles", true)) }
    var isExpressiveBgShapes by remember { mutableStateOf(sp.getBoolean("sp_expressive_bg_shapes", true)) }

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
    var isAutoOpenApps by remember { mutableStateOf(sp.getBoolean("sp_auto_open_apps", false)) }
    var isCheckUpdateOnLaunch by remember { mutableStateOf(sp.getBoolean("sp_check_update_on_launch", true)) }
    var isTouchHaptics by remember { mutableStateOf(sp.getBoolean("sp_touch_haptics", true)) }
    var isAppLockEnabled by remember { mutableStateOf(sp.getBoolean("sp_app_lock_enabled", false)) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var isDoubleBackExit by remember { mutableStateOf(sp.getBoolean("sp_double_back_exit", true)) }
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
                "sp_floating_tab_bar" -> {
                    isFloatingTabBar = sp.getBoolean("sp_floating_tab_bar", true)
                }
                "sp_expressive_bg_shapes" -> {
                    isExpressiveBgShapes = sp.getBoolean("sp_expressive_bg_shapes", true)
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

    // In-category back (drilling back up to the overview list) stays an instant,
    // non-predictive BackHandler since it's an internal state change, not a screen exit.
    androidx.activity.compose.BackHandler(enabled = currentCategory != SettingsCategory.OVERVIEW) {
        currentCategory = SettingsCategory.OVERVIEW
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
        gsFlexSettings = selectedGSFlexSettings,
        colorStyle = selectedColorStyle,
        paletteId = selectedPaletteId
    ) {
      // Predictive back gesture only fires when the OVERVIEW category is showing;
      // while drilled into a category the BackHandler above intercepts back first.
      com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = currentCategory == SettingsCategory.OVERVIEW,
        onBack = onBackPress,
      ) {
      com.petal.browser.predictive.PetalScreenWrapper {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                // Top App Bar Header + Search (hosted in Scaffold's topBar so insets, collapse
                // offset, and content padding are all resolved consistently by Scaffold).
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    com.petal.browser.ui.components.ExpressiveHeader(
                        title = if (searchQuery.isNotBlank()) "Search Results" else currentCategory.title,
                        subtitle = if (searchQuery.isNotBlank()) "Matching Settings" else if (currentCategory == SettingsCategory.OVERVIEW) "Browser Preferences & Customization" else "Settings Category",
                        onBack = {
                            if (currentCategory != SettingsCategory.OVERVIEW) {
                                currentCategory = SettingsCategory.OVERVIEW
                            } else {
                                onBackPress()
                            }
                        }
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
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                M3ExpressiveVariableBackground(pageSeed = "settings_page")

                if (isLoading) {
                    com.petal.browser.compose.composable.ContainedLoadingIndicator(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        key(currentCategory) {
                            val categoryScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(categoryScrollState)
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

                            // 0. Dedicated Petal AI & API Keys Hub Sub-Screen Page
                            if ((currentCategory == SettingsCategory.API_INTEGRATIONS || searchQuery.isNotBlank()) && matchesSearch("API & Integrations", "petal ai api key gemini openrouter openai grok groq key deep research webkit extensions search suggestions")) {
                                SettingsCategoryCard(title = "Petal AI & API Keys Hub", icon = Icons.Rounded.AutoAwesome) {
                                    Text(
                                        "Configure AI providers, API keys, and model selections for Petal Deep Research, AI Search, and page summarizer.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    var selectedProvider by remember { mutableStateOf(com.petal.browser.compose.ai.PetalAiResearchEngine.getSelectedProvider(context)) }
                                    var currentKey by remember(selectedProvider) { mutableStateOf(com.petal.browser.compose.ai.PetalAiResearchEngine.getApiKey(context, selectedProvider)) }
                                    var selectedModel by remember(selectedProvider) { mutableStateOf(com.petal.browser.compose.ai.PetalAiResearchEngine.getSelectedModel(context, selectedProvider)) }
                                    var isKeyVisible by remember { mutableStateOf(false) }
                                    var testResultMsg by remember { mutableStateOf<String?>(null) }
                                    var isTestingKey by remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()

                                    Text(
                                        text = "Active AI Provider:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        com.petal.browser.compose.ai.AiProvider.values().forEach { provider ->
                                            val isSelected = selectedProvider == provider
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedProvider = provider
                                                    com.petal.browser.compose.ai.PetalAiResearchEngine.setSelectedProvider(context, provider)
                                                    currentKey = com.petal.browser.compose.ai.PetalAiResearchEngine.getApiKey(context, provider)
                                                    selectedModel = com.petal.browser.compose.ai.PetalAiResearchEngine.getSelectedModel(context, provider)
                                                    testResultMsg = null
                                                },
                                                label = { Text(provider.displayName) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    // API Key Field for Selected Provider
                                    OutlinedTextField(
                                        value = currentKey,
                                        onValueChange = { newKey ->
                                            currentKey = newKey
                                            com.petal.browser.compose.ai.PetalAiResearchEngine.setApiKey(context, selectedProvider, newKey)
                                            testResultMsg = null
                                        },
                                        label = { Text("${selectedProvider.displayName} API Key") },
                                        placeholder = { Text("Paste your ${selectedProvider.displayName} API Key...") },
                                        singleLine = true,
                                        visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                        leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        trailingIcon = {
                                            Row {
                                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                                    Icon(
                                                        if (isKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                        contentDescription = "Toggle Visibility"
                                                    )
                                                }
                                                if (currentKey.isNotBlank()) {
                                                    IconButton(onClick = {
                                                        currentKey = ""
                                                        com.petal.browser.compose.ai.PetalAiResearchEngine.setApiKey(context, selectedProvider, "")
                                                        testResultMsg = null
                                                    }) {
                                                        Icon(Icons.Rounded.Close, contentDescription = "Clear Key")
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(onClick = {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(selectedProvider.keyUrl))
                                            context.startActivity(intent)
                                        }) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Get Free ${selectedProvider.displayName} Key", style = MaterialTheme.typography.labelSmall)
                                        }

                                        TextButton(
                                            enabled = currentKey.isNotBlank() && !isTestingKey,
                                            onClick = {
                                                isTestingKey = true
                                                testResultMsg = "Testing connection..."
                                                com.petal.browser.compose.ai.PetalAiResearchEngine.performResearch(
                                                    context = context,
                                                    pageTitle = "Test Page",
                                                    pageUrl = "https://petal.browser/test",
                                                    pageTextContent = "Petal Browser API key verification test",
                                                    mode = com.petal.browser.compose.ai.ResearchMode.CUSTOM,
                                                    customPrompt = "Respond with 'OK' if API key is working cleanly.",
                                                    onResult = { res ->
                                                        isTestingKey = false
                                                        testResultMsg = if (res.isSuccess) "✓ API Key Verified & Connected!" else "✗ Connection Failed: ${res.exceptionOrNull()?.message ?: "Invalid Key"}"
                                                    }
                                                )
                                            }
                                        ) {
                                            if (isTestingKey) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(6.dp))
                                            } else {
                                                Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text("Test Key", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    if (testResultMsg != null) {
                                        Text(
                                            text = testResultMsg!!,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (testResultMsg!!.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Model Choice
                                    Text(
                                        text = "Preferred ${selectedProvider.displayName} Model:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        selectedProvider.availableModels.forEach { model ->
                                            val isSelected = selectedModel == model
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedModel = model
                                                    com.petal.browser.compose.ai.PetalAiResearchEngine.setSelectedModel(context, selectedProvider, model)
                                                },
                                                label = { Text(model) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // Open AI Hub Button Card
                                    Surface(
                                        onClick = {
                                            if (context is androidx.activity.ComponentActivity) {
                                                com.petal.browser.compose.ai.PetalAiHubBridge.showAiHub(context)
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Open Petal AI Hub Directory",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                                Text(
                                                    "Launch Petal AI tools, Deep Web Research, and web AI tools catalog",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                            Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    var enableLiveSuggestions by remember { mutableStateOf(sp.getBoolean("sp_enable_live_suggestions", true)) }
                                    ToggleRow(
                                        title = "Live Search Recommendations",
                                        subtitle = "Fetch live autocomplete suggestions from Google, DuckDuckGo, or Bing while typing",
                                        icon = Icons.Rounded.Search,
                                        checked = enableLiveSuggestions,
                                        onCheckedChange = { newValue ->
                                            enableLiveSuggestions = newValue
                                            sp.edit().putBoolean("sp_enable_live_suggestions", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Surface(
                                        onClick = {
                                            if (context is androidx.activity.ComponentActivity) {
                                                com.petal.browser.extensions.PetalExtensionsBridge.showExtensions(context)
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Chrome Extensions (petal://extensions)",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    "Manage active extensions, install .CRX / .ZIP files, or configure UserScript engine",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
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
                                                    @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }

                                    // --- GS Flex Preset Chips ---
                                    androidx.compose.animation.AnimatedVisibility(visible = selectedFont == AppFont.GS_FLEX) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                "GS Flex Design Preset:",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                GSFlexPreset.values().forEach { preset ->
                                                    FilterChip(
                                                        selected = selectedPreset == preset,
                                                        onClick = {
                                                            selectedPreset = preset
                                                            sp.edit().putString("sp_gs_flex_preset", preset.name).apply()
                                                        },
                                                        label = { Text(preset.label.substringBefore(" (")) },
                                                        leadingIcon = if (selectedPreset == preset) {
                                                            @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                        } else null
                                                    )
                                                }
                                            }


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
                                                    @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                                                        com.petal.browser.widget.PetalSearchWidgetProvider.updateAllWidgets(context)
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

                                    // Material You Dynamic Color Toggle
                                    ToggleRow(
                                        title = "Material You Dynamic Color",
                                        subtitle = "Adapt accent colors from your system wallpaper (Android 12+)",
                                        icon = Icons.Rounded.ColorLens,
                                        checked = isDynamicColor,
                                        onCheckedChange = { newValue ->
                                            isDynamicColor = newValue
                                            sp.edit().putBoolean("useDynamicColor", newValue).apply()
                                            com.petal.browser.widget.PetalSearchWidgetProvider.updateAllWidgets(context)
                                        }
                                    )

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
                                            com.petal.browser.widget.PetalSearchWidgetProvider.updateAllWidgets(context)
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // Floating Tab Bar Toggle
                                    ToggleRow(
                                        title = "Floating Tab Bar",
                                        subtitle = "Show the bottom bar as a floating pill instead of a flat bar",
                                        icon = Icons.Rounded.SpaceBar,
                                        checked = isFloatingTabBar,
                                        onCheckedChange = { newValue ->
                                            isFloatingTabBar = newValue
                                            sp.edit().putBoolean("sp_floating_tab_bar", newValue).apply()
                                        }
                                    )
                                    
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    
                                    // Material 3 Expressive Background Morphing Shapes Toggle
                                    ToggleRow(
                                        title = "M3 Expressive Morphing Shapes",
                                        subtitle = "Display ambient morphing background shapes across all app screens",
                                        icon = Icons.Rounded.BubbleChart,
                                        checked = isExpressiveBgShapes,
                                        onCheckedChange = { newValue ->
                                            isExpressiveBgShapes = newValue
                                            sp.edit().putBoolean("sp_expressive_bg_shapes", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // API Features Hub Toggle Row & Direct Action
                                    var isApiFeaturesEnabled by remember { mutableStateOf(sp.getBoolean("sp_api_features_hub_enabled", true)) }

                                    ToggleRow(
                                        title = "New API Integration Features Hub",
                                        subtitle = "Enable AndroidX WebKit multi-profile, Google OAuth Credential Manager & Monet color extraction",
                                        icon = Icons.Rounded.Extension,
                                        checked = isApiFeaturesEnabled,
                                        onCheckedChange = { newValue ->
                                            isApiFeaturesEnabled = newValue
                                            sp.edit().putBoolean("sp_api_features_hub_enabled", newValue).apply()
                                        }
                                    )

                                    if (isApiFeaturesEnabled) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        "API Integrations Active",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "• Palette Favicon Accent Color Extraction\n• AndroidX WebKit Private Incognito Multi-Profile\n• Google Credential Manager OAuth Sync\n• Predictive Back Gesture System Integration",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                    }

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

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    var isWaterRipplePull by remember { mutableStateOf(sp.getBoolean("sp_water_ripple_pull", true)) }

                                    // Expressive Water Ripple Pull-to-Refresh Toggle
                                    ToggleRow(
                                        title = "Expressive Water Ripple Pull-to-Refresh",
                                        subtitle = "Elastic M3 water-ripple wave animation when pulling down web pages",
                                        icon = Icons.Rounded.Waves,
                                        checked = isWaterRipplePull,
                                        onCheckedChange = { newValue ->
                                            isWaterRipplePull = newValue
                                            sp.edit().putBoolean("sp_water_ripple_pull", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    var isWallpaperBlur by remember { mutableStateOf(sp.getBoolean("sp_wallpaper_blur", true)) }
                                    var isExpressiveToast by remember { mutableStateOf(sp.getBoolean("sp_expressive_toast", true)) }

                                    ToggleRow(
                                        title = "Dynamic Wallpaper Blur Backdrop Engine",
                                        subtitle = "Real-time frosted glass backdrop blur behind browser toolbars",
                                        icon = Icons.Rounded.BlurOn,
                                        checked = isWallpaperBlur,
                                        onCheckedChange = { newValue ->
                                            isWallpaperBlur = newValue
                                            sp.edit().putBoolean("sp_wallpaper_blur", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "Floating Expressive Toast Pills",
                                        subtitle = "Slide-up spring animated toast notification pills for actions",
                                        icon = Icons.Rounded.NotificationsActive,
                                        checked = isExpressiveToast,
                                        onCheckedChange = { newValue ->
                                            isExpressiveToast = newValue
                                            sp.edit().putBoolean("sp_expressive_toast", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    var isReadingProgressPill by remember { mutableStateOf(sp.getBoolean("sp_reading_progress_pill", true)) }

                                    ToggleRow(
                                        title = "Floating Expressive Reading Progress Pill",
                                        subtitle = "Slim animated reading progress bar at top of browser while scrolling web pages",
                                        icon = Icons.Rounded.LinearScale,
                                        checked = isReadingProgressPill,
                                        onCheckedChange = { newValue ->
                                            isReadingProgressPill = newValue
                                            sp.edit().putBoolean("sp_reading_progress_pill", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    var isQuickActionsPill by remember { mutableStateOf(sp.getBoolean("sp_quick_actions_pill", true)) }

                                    ToggleRow(
                                        title = "Expressive Floating Quick Actions Pill",
                                        subtitle = "Floating quick-settings bottom pill bar for Desktop, AdBlock, and Dark Mode",
                                        icon = Icons.Rounded.Tune,
                                        checked = isQuickActionsPill,
                                        onCheckedChange = { newValue ->
                                            isQuickActionsPill = newValue
                                            sp.edit().putBoolean("sp_quick_actions_pill", newValue).apply()
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

                            // 3. Private DNS & Chrome Flags
                            if ((currentCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Chrome Flags", "chrome://flags petal://flags flags experimental webgpu features force dark safe browsing")) {
                                SettingsCategoryCard(title = "Experimental Petal & Chrome Flags", icon = Icons.Rounded.Science) {
                                    Surface(
                                        onClick = {
                                            if (context is androidx.activity.ComponentActivity) {
                                                com.petal.browser.flags.PetalChromeFlagsBridge.showFlags(context, null)
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Petal & Chrome Experimental Flags (petal://flags)",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                                Text(
                                                    "Enable or disable WebGPU, hardware acceleration, force dark mode, HTTP/3 QUIC, and experimental Web APIs",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                }
                            }

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
                                        subtitle = "uBlock Origin & AdGuard-grade Trie filter engine & scriptlets",
                                        icon = Icons.Rounded.Shield,
                                        checked = isAdBlock,
                                        onCheckedChange = { newValue ->
                                            isAdBlock = newValue
                                            com.petal.browser.browser.PetalAdBlockEngine.setAdBlockEnabled(context, newValue)
                                        }
                                    )

                                    if (isAdBlock) {
                                        var showWhitelistDialog by remember { mutableStateOf(false) }
                                        var whitelistDomainInput by remember { mutableStateOf("") }
                                        var whitelistedDomainsState by remember { mutableStateOf(com.petal.browser.browser.PetalAdBlockEngine.getWhitelistedDomains()) }

                                        if (showWhitelistDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showWhitelistDialog = false },
                                                title = { Text("AdBlock Domain Whitelist") },
                                                text = {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text(
                                                            "Domains added here will bypass ad and tracker filtering:",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            OutlinedTextField(
                                                                value = whitelistDomainInput,
                                                                onValueChange = { whitelistDomainInput = it },
                                                                placeholder = { Text("example.com") },
                                                                singleLine = true,
                                                                modifier = Modifier.weight(1f),
                                                                shape = RoundedCornerShape(12.dp)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Button(
                                                                onClick = {
                                                                    if (whitelistDomainInput.isNotBlank()) {
                                                                        com.petal.browser.browser.PetalAdBlockEngine.addDomainToWhitelist(context, whitelistDomainInput.trim())
                                                                        whitelistedDomainsState = com.petal.browser.browser.PetalAdBlockEngine.getWhitelistedDomains()
                                                                        whitelistDomainInput = ""
                                                                    }
                                                                }
                                                            ) {
                                                                Text("Add")
                                                            }
                                                        }

                                                        Spacer(Modifier.height(8.dp))

                                                        if (whitelistedDomainsState.isEmpty()) {
                                                            Text(
                                                                "No whitelisted domains.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        } else {
                                                            FlowRow(
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                whitelistedDomainsState.forEach { domain ->
                                                                    InputChip(
                                                                        selected = true,
                                                                        onClick = {
                                                                            com.petal.browser.browser.PetalAdBlockEngine.removeDomainFromWhitelist(context, domain)
                                                                            whitelistedDomainsState = com.petal.browser.browser.PetalAdBlockEngine.getWhitelistedDomains()
                                                                        },
                                                                        label = { Text(domain) },
                                                                        trailingIcon = {
                                                                            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                confirmButton = {
                                                    TextButton(onClick = { showWhitelistDialog = false }) {
                                                        Text("Done")
                                                    }
                                                }
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Whitelisted Domains (${whitelistedDomainsState.size})",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            TextButton(onClick = { showWhitelistDialog = true }) {
                                                Text("Manage Whitelist")
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "Block Popup Windows",
                                        subtitle = "Prevent unwanted popups and redirect windows",
                                        icon = Icons.Rounded.OpenInNew,
                                        checked = isBlockPopups,
                                        onCheckedChange = { newValue ->
                                            isBlockPopups = newValue
                                            sp.edit().putBoolean("sp_block_popups", newValue).putBoolean("profileStandard_javascriptPopUp", newValue).apply()
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

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "App Lock Security Passcode",
                                        subtitle = if (isAppLockEnabled) "Passcode active • Tap to configure shaped-mask passcode" else "Require shaped-mask passcode to access browser",
                                        icon = Icons.Rounded.Key,
                                        checked = isAppLockEnabled,
                                        onCheckedChange = { enabled ->
                                            isAppLockEnabled = enabled
                                            sp.edit().putBoolean("sp_app_lock_enabled", enabled).apply()
                                            if (enabled) {
                                                showPasscodeDialog = true
                                            }
                                        }
                                    )
                                }
                            }

                            // 7. Accessibility & Scaling (using PetalSlider)
                            if ((currentCategory == SettingsCategory.DISPLAY_ZOOM || searchQuery.isNotBlank()) && matchesSearch("Accessibility", "haptics touch vibration text font scale page zoom text scaling stride slider blur address bar top bottom")) {
                                SettingsCategoryCard(title = "Accessibility & Display Options", icon = Icons.Rounded.Accessibility) {
                                    ToggleRow(
                                        title = "Touch Haptics Engine",
                                        subtitle = "Vibrate with Ever-Haptics tactile feedback on button presses and UI gestures",
                                        icon = Icons.Rounded.Vibration,
                                        checked = isTouchHaptics,
                                        onCheckedChange = { newValue ->
                                            isTouchHaptics = newValue
                                            sp.edit().putBoolean("sp_touch_haptics", newValue).apply()
                                            if (newValue) {
                                                com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                                                    .play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
                                            }
                                        }
                                    )

                                    if (isTouchHaptics) {
                                        var testPattern by remember { mutableStateOf(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK) }
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Haptic Pattern Test & Preview:",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                com.petal.browser.haptics.PetalHapticEngine.Pattern.values().forEach { pattern ->
                                                    FilterChip(
                                                        selected = testPattern == pattern,
                                                        onClick = {
                                                            testPattern = pattern
                                                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                                                                .play(pattern, 0.75f)
                                                        },
                                                        label = { Text(pattern.name.replace("_", " ")) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    ToggleRow(
                                        title = "Press Back Again to Exit",
                                        subtitle = "Require confirmation back press before closing Petal (works with both gesture and 3-button navigation)",
                                        icon = Icons.Rounded.ExitToApp,
                                        checked = isDoubleBackExit,
                                        onCheckedChange = { newValue ->
                                            isDoubleBackExit = newValue
                                            sp.edit().putBoolean("sp_double_back_exit", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    val blurFlow by com.petal.browser.predictive.PetalPredictiveJunction.isDepthBlurEnabled.collectAsState()

                                    ToggleRow(
                                        title = "Depth Blur Effect (Junction)",
                                        subtitle = if (blurFlow) "Background page receives 24dp blur & corner morphing during back navigation" else "Disabled depth blur; uses solid dim overlay",
                                        icon = Icons.Rounded.Animation,
                                        checked = blurFlow,
                                        onCheckedChange = { enabled ->
                                            com.petal.browser.predictive.PetalPredictiveJunction.setDepthBlurEnabled(sp, enabled)
                                        }
                                    )

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
                                                (context as? BrowserActivity)?.applyAddressBarPosition()
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
                                                (context as? BrowserActivity)?.applyAddressBarPosition()
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
                                        PetalSlider(
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
                                        PetalSlider(
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
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { showBackupDialog = true },
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Backup", maxLines = 1)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { showRestoreDialog = true },
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Restore", maxLines = 1)
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Downgrade Data Protection: Automatic snapshots are saved to Documents/browser_backup/petal_downgrade_snapshot.json whenever app version updates, allowing seamless data restoration if downgrading to older versions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                                    var isCheckingUpdate by remember { mutableStateOf(false) }

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
                                                    text = if (isCheckingUpdate) "Checking for updates..." else "Current Version: v$appVersionName",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isCheckingUpdate) {
                                                com.petal.browser.compose.composable.ContainedLoadingIndicator(
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            } else {
                                                Button(
                                                    onClick = {
                                                        isCheckingUpdate = true
                                                        var act: android.app.Activity? = null
                                                        var ctx = context
                                                        while (ctx is android.content.ContextWrapper) {
                                                            if (ctx is android.app.Activity) {
                                                                act = ctx
                                                                break
                                                            }
                                                            ctx = ctx.baseContext
                                                        }
                                                        if (act != null) {
                                                            com.petal.browser.unit.UpdateUnit.checkForUpdates(act, false) {
                                                                isCheckingUpdate = false
                                                            }
                                                        } else {
                                                            isCheckingUpdate = false
                                                            com.petal.browser.view.NinjaToast.show(context, "Checking for updates...")
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

                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedButton(
                                        onClick = {
                                            var act: android.app.Activity? = null
                                            var ctx = context
                                            while (ctx is android.content.ContextWrapper) {
                                                if (ctx is android.app.Activity) {
                                                    act = ctx
                                                    break
                                                }
                                                ctx = ctx.baseContext
                                            }
                                            if (act is androidx.activity.ComponentActivity) {
                                                com.petal.browser.ui.components.PetalUpdateSheetBridge.showChangelogHistorySheet(act)
                                            } else {
                                                com.petal.browser.view.NinjaToast.show(context, "Fetching release history...")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("View All Release Changelogs")
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
                                                                com.petal.browser.unit.BrowserUnit.intentURL(context, Uri.parse("https://github.com/shreyagarwal72/petal/"))
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
                                                                com.petal.browser.unit.BrowserUnit.intentURL(context, Uri.parse("https://github.com/shreyagarwal72/petal/issues"))
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

                                    Spacer(Modifier.height(32.dp))
                                        }
                                    }
                            }
                    }
                }
            }
        }
        // App Lock Passcode Configuration Dialog using PetalShapedPasswordInput
    if (showPasscodeDialog) {
        var tempPasscode by remember { mutableStateOf(sp.getString("sp_app_lock_passcode", "") ?: "") }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = {
                Text(
                    text = "Configure Security Passcode",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a security passcode. Typed characters will be masked with Material 3 Expressive shapes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.petal.browser.ui.components.PetalShapedPasswordInput(
                        value = tempPasscode,
                        onValueChange = {
                            tempPasscode = it
                            if (dialogError != null) dialogError = null
                        },
                        hintText = "New Passcode",
                        isError = dialogError != null,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onUnlock = {
                            if (tempPasscode.trim().length >= 4) {
                                sp.edit().putString("sp_app_lock_passcode", tempPasscode.trim()).apply()
                                showPasscodeDialog = false
                            } else {
                                dialogError = "Passcode must be at least 4 characters"
                            }
                        },
                        unlockButtonText = "Save"
                    )
                    if (dialogError != null) {
                        Text(
                            text = dialogError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPasscodeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconSwitch(
            checked = checked,
            icon = icon,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PetalVariableSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                String.format(java.util.Locale.getDefault(), "%.0f", value),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        PetalSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
