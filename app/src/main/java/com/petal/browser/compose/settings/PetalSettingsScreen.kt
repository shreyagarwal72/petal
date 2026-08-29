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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.composed
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.petal.browser.ui.components.ExpressiveButtonGroup
import com.petal.browser.ui.components.ExpressiveSegmentItem
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.*

object PetalSettingsBridge {
    @JvmStatic
    @JvmOverloads
    fun createSettingsView(activity: ComponentActivity, initialCategory: SettingsCategory = SettingsCategory.OVERVIEW, onBackPress: () -> Unit): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        com.petal.browser.predictive.PetalContentSnapshot.clear()
                    }
                }
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
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
                var themeConfigName by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }
                var customFontSettingsState by remember { mutableStateOf(getCustomFontSettings(sp)) }
                var customFontPathState by remember { mutableStateOf(sp.getString("sp_custom_font_path", null)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key != null && key.startsWith("sp_custom_")) {
                            customFontSettingsState = getCustomFontSettings(sp)
                            customFontPathState = sp.getString("sp_custom_font_path", null)
                        }
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
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
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
                    expressiveColors = isExpressiveColors,
                    appFont = appFont,
                    customFontPath = customFontPathState,
                    customFontSettings = customFontSettingsState,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal,
                    gsFlexSettings = gsFlexSettings,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    com.petal.browser.ui.components.ScreenWrapper {
                        PetalSettingsScreen(backgroundSnapshot = snapshotBitmap, initialCategory = initialCategory, onBackPress = onBackPress)
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

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                }
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSettingsScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
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
        mutableStateOf(AppFont.fromName(sp.getString("sp_app_font", "PETAL")))
    }
    val fontPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            PetalFontHelper.saveCustomFontUri(context, it)
            selectedFont = AppFont.CUSTOM
        }
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
    var isExpressiveBgShapes by remember { mutableStateOf(sp.getBoolean("sp_expressive_bg_shapes", true)) }
    var isHighRefreshRate by remember { mutableStateOf(sp.getBoolean("sp_high_refresh_rate", true)) }
    val maxDetectedRefreshRate = remember(context) { com.petal.browser.unit.PetalHighRefreshRateManager.getMaxSupportedRefreshRate(context) }
    var bgShapeChangeMode by remember { mutableStateOf(sp.getString("sp_bg_shape_change_mode", "ALWAYS") ?: "ALWAYS") }
    var bgShapeRotationMin by remember { mutableIntStateOf(sp.getInt("sp_bg_shape_rotation_min", 5)) }

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
    var isFingerprintProtection by remember { mutableStateOf(sp.getBoolean("sp_fingerprint_protection", true)) }
    var isWebRtcProtection by remember { mutableStateOf(sp.getBoolean("sp_webrtc_protection", true)) }
    var isBlockThirdPartyCookies by remember { mutableStateOf(sp.getBoolean("sp_block_third_party_cookies", false)) }
    var isDntGpc by remember { mutableStateOf(sp.getBoolean("sp_dnt_gpc", true)) }
    var isTrimReferrers by remember { mutableStateOf(sp.getBoolean("sp_trim_referrers", true)) }
    var isWebAuthnEnabled by remember { mutableStateOf(sp.getBoolean("sp_webauthn_enabled", true)) }
    var isHttpsOnly by remember { mutableStateOf(sp.getBoolean("sp_https_only", true)) }
    var isJavaScript by remember { mutableStateOf(sp.getBoolean("sp_javascript", true)) }
    var isBlockPopups by remember { mutableStateOf(sp.getBoolean("sp_block_popups", true)) }
    var isAutoOpenApps by remember { mutableStateOf(sp.getBoolean("sp_auto_open_apps", false)) }
    var isCheckUpdateOnLaunch by remember { mutableStateOf(sp.getBoolean("sp_check_update_on_launch", true)) }
    var isTouchHaptics by remember { mutableStateOf(sp.getBoolean("sp_touch_haptics", true)) }
    var isPredictiveBackJunction by remember { mutableStateOf(sp.getBoolean("sp_predictive_back_junction_enabled", true)) }
    var isDepthBlurJunction by remember { mutableStateOf(sp.getBoolean("sp_depth_blur_junction_enabled", true)) }
    var isAppLockEnabled by remember { mutableStateOf(sp.getBoolean("sp_app_lock_enabled", false)) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var isDoubleBackExit by remember { mutableStateOf(sp.getBoolean("sp_double_back_exit", true)) }
    var addressBarPosition by remember { mutableStateOf(sp.getString("sp_address_bar_position", "TOP") ?: "TOP") }
    var fontSize by remember { mutableFloatStateOf(sp.getFloat("sp_font_size_scale", 1.0f)) }
    var zoomLevel by remember { mutableFloatStateOf(sp.getFloat("sp_zoom_level_scale", 1.0f)) }
    var isForceZoom by remember { mutableStateOf(sp.getBoolean("sp_force_enable_zoom", true)) }
    var isReaderModeDetection by remember { mutableStateOf(sp.getBoolean("sp_reader_mode_detection", true)) }
    var isCaretBrowsing by remember { mutableStateOf(sp.getBoolean("sp_caret_browsing", false)) }
    var isTouchpadSwipeNav by remember { mutableStateOf(sp.getBoolean("sp_touchpad_swipe_nav", true)) }
    var searchEngineIndex by remember { mutableStateOf(sp.getString("sp_search_engine", "0") ?: "0") }
    var torrentEngineMode by remember { mutableStateOf(sp.getString("sp_torrent_engine", "1DM") ?: "1DM") }
    var showEngineSheet by remember { mutableStateOf(false) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_floating_tab_bar" -> {
                    isFloatingTabBar = sp.getBoolean("sp_floating_tab_bar", true)
                }
                "sp_expressive_bg_shapes" -> {
                    isExpressiveBgShapes = sp.getBoolean("sp_expressive_bg_shapes", true)
                }
                "sp_bg_shape_change_mode" -> {
                    bgShapeChangeMode = sp.getString("sp_bg_shape_change_mode", "ALWAYS") ?: "ALWAYS"
                }
                "sp_bg_shape_rotation_min" -> {
                    bgShapeRotationMin = sp.getInt("sp_bg_shape_rotation_min", 5)
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

    @Composable
    fun RenderCategoryPage(
        scaffoldCategory: SettingsCategory,
        onHeaderBack: () -> Unit
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    com.petal.browser.ui.components.ExpressiveHeader(
                        title = if (searchQuery.isNotBlank()) "Search Results" else scaffoldCategory.title,
                        subtitle = if (searchQuery.isNotBlank()) "Matching Settings" else if (scaffoldCategory == SettingsCategory.OVERVIEW) "Browser Preferences & Customization" else scaffoldCategory.subtitle,
                        onBack = onHeaderBack
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
                        key(scaffoldCategory) {
                            val categoryScrollState = rememberScrollState()
                            LaunchedEffect(scaffoldCategory) {
                                categoryScrollState.scrollTo(0)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(categoryScrollState)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                            if (scaffoldCategory == SettingsCategory.OVERVIEW && searchQuery.isBlank()) {
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

                                categories.forEach { cat ->
                                     SettingsCategoryRow(
                                         title = cat.title,
                                         subtitle = cat.subtitle,
                                         icon = cat.icon,
                                         onClick = { currentCategory = cat }
                                     )
                                 }
                                }

                            // 0. Dedicated Petal AI & API Keys Hub Sub-Screen Page
                            if ((scaffoldCategory == SettingsCategory.API_INTEGRATIONS || searchQuery.isNotBlank()) && matchesSearch("API & Integrations", "petal ai api key gemini openrouter openai grok groq key deep research webkit extensions search suggestions")) {
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

                                    val aiProviderScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
                                        scrollState = aiProviderScrollState,
                                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(aiProviderScrollState),
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

                                    val modelScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
                                        scrollState = modelScrollState,
                                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(modelScrollState),
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
                            if ((scaffoldCategory == SettingsCategory.APPEARANCE || searchQuery.isNotBlank()) && matchesSearch("Appearance", "fonts accent theme palette amoled")) {
                                AppearanceHeroBanner(
                                    selectedTheme = selectedThemeConfig,
                                    onThemeSelected = { newTheme ->
                                        selectedThemeConfig = newTheme
                                        sp.edit().putString("sp_theme_config", newTheme.name).apply()
                                        when (newTheme) {
                                            ThemeConfig.FOLLOW_SYSTEM -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                            ThemeConfig.LIGHT -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                                            ThemeConfig.DARK -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                                        }
                                    }
                                )

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
                                    val themeScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
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
                                    }

                                    // --- Font Choice Chips ---
                                    Text(
                                        "Select Font Family:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val fontFamilyScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
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
                                                selected = selectedFont == font,
                                                onClick = {
                                                    selectedFont = font
                                                    sp.edit().putString("sp_app_font", font.name).apply()
                                                    if (font == AppFont.CUSTOM) {
                                                        fontPickerLauncher.launch("*/*")
                                                    }
                                                },
                                                label = { Text(font.label) },
                                                leadingIcon = if (selectedFont == font) {
                                                    @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                    }

                                    // --- GS Flex Preset Chips (For Petal Signature) ---
                                    androidx.compose.animation.AnimatedVisibility(visible = selectedFont == AppFont.PETAL) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                "Petal Signature Design Preset:",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val presetScrollState = rememberScrollState()
                                            com.petal.browser.ui.components.ScrollFadeRow(
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
                                    }

                                    // --- Live Mini Browser Skeleton Preview ---
                                    Text(
                                        "Palette Theme Live Preview:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val currentPalette = remember(selectedPaletteId) {
                                        PetalPalettes.firstOrNull { it.id == selectedPaletteId } ?: PetalPalettes.first()
                                    }
                                    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                                    val activeBaseScheme = if (isSystemDark) currentPalette.dark else currentPalette.light
                                    val activePreviewScheme = remember(activeBaseScheme, selectedColorStyle) {
                                        activeBaseScheme.applyStyle(selectedColorStyle)
                                    }
                                    val styleSchemes = remember(activeBaseScheme) {
                                        ColorStyle.entries.associateWith { style ->
                                            activeBaseScheme.applyStyle(style)
                                        }
                                    }

                                    MiniBrowserSkeletonPreview(
                                        scheme = activePreviewScheme,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    // --- Palette Style Swatches (Imported from PixelPlayer) ---
                                    Text(
                                        "Palette Style:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Select dynamic color harmony style for accent roles and surfaces",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    val paletteStyleScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
                                        scrollState = paletteStyleScrollState,
                                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(paletteStyleScrollState)
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ColorStyle.entries.forEach { style ->
                                                val swatchScheme = styleSchemes[style] ?: activePreviewScheme
                                                PaletteSwatchSquare(
                                                    scheme = swatchScheme,
                                                    selected = selectedColorStyle == style,
                                                    onClick = {
                                                        selectedColorStyle = style
                                                        sp.edit().putString("sp_color_style", style.name).apply()
                                                        com.petal.browser.widget.PetalSearchWidgetProvider.updateAllWidgets(context)
                                                    },
                                                    modifier = Modifier.size(68.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = selectedColorStyle.label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = activePreviewScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = activePreviewScheme.tertiaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = selectedColorStyle.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = activePreviewScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }

                                    // --- Custom Font File Picker & Tier Customizer UI ---
                                    androidx.compose.animation.AnimatedVisibility(visible = selectedFont == AppFont.CUSTOM) {
                                        var customFontSettings by remember { mutableStateOf(getCustomFontSettings(sp)) }
                                        var selectedLevelTab by remember { mutableIntStateOf(0) }
                                        val customFontName = sp.getString("sp_custom_font_name", "No font file selected") ?: "No font file selected"

                                        DisposableEffect(sp) {
                                            val fontListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                                if (key != null && key.startsWith("sp_custom_")) {
                                                    customFontSettings = getCustomFontSettings(sp)
                                                }
                                            }
                                            sp.registerOnSharedPreferenceChangeListener(fontListener)
                                            onDispose { sp.unregisterOnSharedPreferenceChangeListener(fontListener) }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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

                                            Text(
                                                "Fine-tune Custom Typography per Level:",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            PrimaryTabRow(
                                                selectedTabIndex = selectedLevelTab,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                            ) {
                                                Tab(selected = selectedLevelTab == 0, onClick = { selectedLevelTab = 0 }, text = { Text("Display") })
                                                Tab(selected = selectedLevelTab == 1, onClick = { selectedLevelTab = 1 }, text = { Text("Headline") })
                                                Tab(selected = selectedLevelTab == 2, onClick = { selectedLevelTab = 2 }, text = { Text("Body") })
                                            }

                                            val currentAxes = when (selectedLevelTab) {
                                                0 -> customFontSettings.display
                                                1 -> customFontSettings.headline
                                                else -> customFontSettings.body
                                            }

                                            fun updateCurrentAxes(newAxes: FontAxes) {
                                                val updatedSettings = when (selectedLevelTab) {
                                                    0 -> customFontSettings.copy(display = newAxes)
                                                    1 -> customFontSettings.copy(headline = newAxes)
                                                    else -> customFontSettings.copy(body = newAxes)
                                                }
                                                customFontSettings = updatedSettings
                                                saveCustomFontSettings(sp, updatedSettings)
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                // Weight
                                                Column {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("Weight", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${currentAxes.weight.toInt()}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    PetalSlider(
                                                        value = currentAxes.weight,
                                                        onValueChange = { updateCurrentAxes(currentAxes.copy(weight = it)) },
                                                        valueRange = 100f..1000f
                                                    )
                                                }

                                                // Width
                                                Column {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("Width", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${currentAxes.width.toInt()}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    PetalSlider(
                                                        value = currentAxes.width,
                                                        onValueChange = { updateCurrentAxes(currentAxes.copy(width = it)) },
                                                        valueRange = 25f..150f
                                                    )
                                                }

                                                // Optical Size
                                                Column {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("Optical Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${currentAxes.opsz.toInt()} pt", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    PetalSlider(
                                                        value = currentAxes.opsz,
                                                        onValueChange = { updateCurrentAxes(currentAxes.copy(opsz = it)) },
                                                        valueRange = 6f..72f
                                                    )
                                                }

                                                // Slant
                                                Column {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("Slant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${currentAxes.slant.toInt()}°", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    PetalSlider(
                                                        value = currentAxes.slant,
                                                        onValueChange = { updateCurrentAxes(currentAxes.copy(slant = it)) },
                                                        valueRange = -10f..0f
                                                    )
                                                }

                                                // Roundness
                                                Column {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("Roundness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text("${currentAxes.roundness.toInt()}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    PetalSlider(
                                                        value = currentAxes.roundness,
                                                        onValueChange = { updateCurrentAxes(currentAxes.copy(roundness = it)) },
                                                        valueRange = 0f..100f
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
                                    val accentStyleScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
                                        scrollState = accentStyleScrollState,
                                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(accentStyleScrollState),
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
                                    }

                                    // --- Preset Palette Seeds ---
                                    Text(
                                        "Preset Color Palettes:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val paletteScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
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

                                    if (isExpressiveBgShapes) {
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
                                                 onItemSelected = { selected ->
                                                     bgShapeChangeMode = selected
                                                     sp.edit().putString("sp_bg_shape_change_mode", selected).apply()
                                                 },
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
                                                                 bgShapeRotationMin = rounded
                                                                 sp.edit().putInt("sp_bg_shape_rotation_min", rounded).apply()
                                                             }
                                                         },
                                                         valueRange = 1f..60f,
                                                         modifier = Modifier.fillMaxWidth()
                                                     )
                                                 }
                                             }
                                         }
                                     }


                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // High Refresh Rate (120Hz+) Toggle
                                    ToggleRow(
                                        title = "High Refresh Rate (120Hz+)",
                                        subtitle = "Force 120Hz/144Hz peak display refresh rate and smooth 120 FPS frame pacing (Detected hardware peak: ${maxDetectedRefreshRate.toInt()} Hz)",
                                        icon = Icons.Rounded.Speed,
                                        checked = isHighRefreshRate,
                                        onCheckedChange = { newValue ->
                                            isHighRefreshRate = newValue
                                            sp.edit().putBoolean("sp_high_refresh_rate", newValue).apply()
                                            (context as? android.app.Activity)?.let {
                                                if (newValue) {
                                                    com.petal.browser.unit.PetalHighRefreshRateManager.applyHighRefreshRate(it)
                                                } else {
                                                    com.petal.browser.unit.PetalHighRefreshRateManager.resetRefreshRate(it)
                                                }
                                            }
                                        }
                                    )

                                }
                            }

                            // 2. Custom Homepage & Background Play
                            if ((scaffoldCategory == SettingsCategory.SEARCH_HOMEPAGE || searchQuery.isNotBlank()) && matchesSearch("Homepage", "custom home start page background play video audio media")) {
                                SettingsCategoryCard(title = "Homepage & Media Playback", icon = Icons.Rounded.Home) {
                                    Text(
                                        "Custom Homepage:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    val homeTypeScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
                                        scrollState = homeTypeScrollState,
                                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(homeTypeScrollState),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = homepageType == "0",
                                                onClick = {
                                                    homepageType = "0"
                                                    sp.edit().putString("sp_home_type", "0").apply()
                                                },
                                                label = { Text("Petal Start Page") },
                                                leadingIcon = if (homepageType == "0") {
                                                    @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                            FilterChip(
                                                selected = homepageType == "1",
                                                onClick = {
                                                    homepageType = "1"
                                                    sp.edit().putString("sp_home_type", "1").apply()
                                                },
                                                label = { Text("Custom URL") },
                                                leadingIcon = if (homepageType == "1") {
                                                    @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
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
                            if ((scaffoldCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Chrome Flags", "chrome://flags petal://flags flags experimental webgpu features force dark safe browsing")) {
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

                            if ((scaffoldCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Private DNS", "dns cleanbrowsing cloudflare 1.1.1.1 google opendns security filter")) {
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
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (privateDnsMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                            border = if (privateDnsMode == mode) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
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
                            if ((scaffoldCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Language", "languages popular english hinglish spanish hindi french german chinese arabic portuguese russian japanese")) {
                                SettingsCategoryCard(title = "App Language", icon = Icons.Rounded.Language) {
                                    Text(
                                        "Choose your preferred display language:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    val languages = listOf(
                                        Pair("system", "System Default"),
                                        Pair("en", "English"),
                                        Pair("hi-Latn", "Hinglish (Hindi in English)"),
                                        Pair("hi", "हिन्दी (Hindi)"),
                                        Pair("es", "Español (Spanish)"),
                                        Pair("fr", "Français (French)"),
                                        Pair("de", "Deutsch (German)"),
                                        Pair("zh", "中文 (Chinese)"),
                                        Pair("ar", "العربية (Arabic)"),
                                        Pair("pt", "Português (Portuguese)"),
                                        Pair("ru", "Русский (Russian)"),
                                        Pair("ja", "日本語 (Japanese)")
                                    )

                                    val languageScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
                                        scrollState = languageScrollState,
                                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(languageScrollState),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        languages.forEach { (tag, label) ->
                                            FilterChip(
                                                selected = appLanguage == tag,
                                                onClick = {
                                                    if (appLanguage != tag) {
                                                        appLanguage = tag
                                                        com.petal.browser.unit.HelperUnit.setAppLanguage(context, tag)
                                                    }
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
                            }

                            // 5. Default Search Engine Section
                            if ((scaffoldCategory == SettingsCategory.SEARCH_HOMEPAGE || searchQuery.isNotBlank()) && matchesSearch("Search Engine", "google duckduckgo bing brave startpage ecosia search provider")) {
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
                            if ((scaffoldCategory == SettingsCategory.PRIVACY || searchQuery.isNotBlank()) && matchesSearch("Privacy Shield", "adblock tracker popups https javascript external apps protection")) {
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
                                        title = "Block Third-Party Tracking Cookies",
                                        subtitle = "Isolate and block cross-site cookies used for ad tracking",
                                        icon = Icons.Rounded.Cookie,
                                        checked = isBlockThirdPartyCookies,
                                        onCheckedChange = { newValue ->
                                            isBlockThirdPartyCookies = newValue
                                            sp.edit().putBoolean("sp_block_third_party_cookies", newValue)
                                                .putBoolean("profileStandard_cookiesThirdParty", !newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "Canvas, Audio & Font Fingerprint Shield",
                                        subtitle = "Randomize canvas, WebGL, AudioContext, and font geometry to defeat browser fingerprinting",
                                        icon = Icons.Rounded.Fingerprint,
                                        checked = isFingerprintProtection,
                                        onCheckedChange = { newValue ->
                                            isFingerprintProtection = newValue
                                            sp.edit().putBoolean("sp_fingerprint_protection", newValue)
                                                .putBoolean("profileStandard_fingerPrintProtection", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "WebRTC IP Leak Shield",
                                        subtitle = "Prevent WebRTC peer connections from exposing your local or real IP address",
                                        icon = Icons.Rounded.VpnLock,
                                        checked = isWebRtcProtection,
                                        onCheckedChange = { newValue ->
                                            isWebRtcProtection = newValue
                                            sp.edit().putBoolean("sp_webrtc_protection", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "Do Not Track & Global Privacy Control (GPC)",
                                        subtitle = "Broadcast DNT: 1 and Sec-GPC: 1 signals requesting websites not to sell or share your data",
                                        icon = Icons.Rounded.Security,
                                        checked = isDntGpc,
                                        onCheckedChange = { newValue ->
                                            isDntGpc = newValue
                                            sp.edit().putBoolean("sp_dnt_gpc", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "Strict Referrer Trimming",
                                        subtitle = "Strip cross-origin URL paths from referrer headers to protect browsing privacy",
                                        icon = Icons.Rounded.LinkOff,
                                        checked = isTrimReferrers,
                                        onCheckedChange = { newValue ->
                                            isTrimReferrers = newValue
                                            sp.edit().putBoolean("sp_trim_referrers", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "WebAuthn & Passkey Support",
                                        subtitle = "Allow websites to authenticate passwordless sign-ins using biometric passkeys, hardware tokens & Google Password Manager",
                                        icon = Icons.Rounded.Key,
                                        checked = isWebAuthnEnabled,
                                        onCheckedChange = { newValue ->
                                            isWebAuthnEnabled = newValue
                                            sp.edit().putBoolean("sp_webauthn_enabled", newValue).apply()
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
                                            sp.edit().putBoolean("sp_block_popups", newValue).putBoolean("profileStandard_javascriptPopUp", newValue).apply()
                                        }
                                    )

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "Auto Open External Apps",
                                        subtitle = "Allow YouTube, Maps & Play Store links to open in external native apps instead of Petal",
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

                            // 7. Accessibility & Scaling (using PetalSlider)
                            if ((scaffoldCategory == SettingsCategory.DISPLAY_ZOOM || searchQuery.isNotBlank()) && matchesSearch("Accessibility", "haptics touch vibration text font scale page zoom text scaling stride slider blur address bar top bottom download torrent engine 1dm manager")) {
                                SettingsCategoryCard(title = "Accessibility & Display Options", icon = Icons.Rounded.Accessibility) {
                                    ToggleRow(
                                        title = "Predictive Back Animations",
                                        subtitle = "Enable fluid predictive back gesture scaling and slide transitions across all screens",
                                        icon = Icons.Rounded.Animation,
                                        checked = isPredictiveBackJunction,
                                        onCheckedChange = { newValue ->
                                            isPredictiveBackJunction = newValue
                                            com.petal.browser.predictive.PetalPredictiveJunction.setPredictiveBackEnabled(sp, newValue)
                                        }
                                    )

                                    ToggleRow(
                                        title = "Depth Blur Effects",
                                        subtitle = "Show 24.dp depth blur and black dim overlay on back pages during navigation and predictive gestures",
                                        icon = Icons.Rounded.BlurOn,
                                        checked = isDepthBlurJunction,
                                        onCheckedChange = { newValue ->
                                            isDepthBlurJunction = newValue
                                            com.petal.browser.predictive.PetalPredictiveJunction.setDepthBlurEnabled(sp, newValue)
                                        }
                                    )

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
                                            val hapticPatternScrollState = rememberScrollState()
                                            com.petal.browser.ui.components.ScrollFadeRow(
                                                scrollState = hapticPatternScrollState,
                                                edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(hapticPatternScrollState),
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
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Text(
                                        "Address Bar Location:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val addressBarScrollState = rememberScrollState()
                                    com.petal.browser.ui.components.ScrollFadeRow(
                                        scrollState = addressBarScrollState,
                                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(addressBarScrollState),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = addressBarPosition == "TOP",
                                                onClick = {
                                                    addressBarPosition = "TOP"
                                                    sp.edit().putString("sp_address_bar_position", "TOP").apply()
                                                    (context as? BrowserActivity)?.applyAddressBarPosition()
                                                },
                                                label = { Text("Top (Default)") },
                                                leadingIcon = if (addressBarPosition == "TOP") {
                                                    @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                                                    @Composable { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
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

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    ToggleRow(
                                        title = "Force Enable Zoom (Override Viewport)",
                                        subtitle = "Override website viewport locks (user-scalable=no) to allow pinch-to-zoom on all pages",
                                        icon = Icons.Rounded.ZoomIn,
                                        checked = isForceZoom,
                                        onCheckedChange = { newValue ->
                                            isForceZoom = newValue
                                            sp.edit().putBoolean("sp_force_enable_zoom", newValue).apply()
                                        }
                                    )

                                    ToggleRow(
                                        title = "Simplified View for Webpages",
                                        subtitle = "Detect article content and enable reader mode prompts for clean distraction-free reading",
                                        icon = Icons.Rounded.Article,
                                        checked = isReaderModeDetection,
                                        onCheckedChange = { newValue ->
                                            isReaderModeDetection = newValue
                                            sp.edit().putBoolean("sp_reader_mode_detection", newValue).apply()
                                        }
                                    )

                                    ToggleRow(
                                        title = "Caret Browsing (F7 Shortcut)",
                                        subtitle = "Navigate and select text within webpages using a movable keyboard cursor (toggle anytime via F7)",
                                        icon = Icons.Rounded.TextFormat,
                                        checked = isCaretBrowsing,
                                        onCheckedChange = { newValue ->
                                            isCaretBrowsing = newValue
                                            com.petal.browser.accessibility.PetalAccessibilityEngine.setCaretBrowsing(context, null, newValue)
                                        }
                                    )

                                    ToggleRow(
                                        title = "Touchpad Two-Finger Navigation",
                                        subtitle = "Swipe horizontally with two fingers on a touchpad or trackpad to navigate back and forward in history",
                                        icon = Icons.Rounded.Swipe,
                                        checked = isTouchpadSwipeNav,
                                        onCheckedChange = { newValue ->
                                            isTouchpadSwipeNav = newValue
                                            sp.edit().putBoolean("sp_touchpad_swipe_nav", newValue).apply()
                                        }
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                com.petal.browser.accessibility.PetalAccessibilityEngine.launchCaptionSettings(context)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Surface(
                                                 shape = CircleShape,
                                                 color = MaterialTheme.colorScheme.primaryContainer,
                                                 modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ClosedCaption,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "System Captions Preferences",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Configure system-level closed captioning, subtitles, and text styling",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.OpenInNew,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Download Engine",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "Select your preferred engine for downloads, torrents, and magnet links:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val engineItems = com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.values().map { mode ->
                                            com.petal.browser.ui.components.ExpressiveSegmentItem(
                                                id = mode.key,
                                                label = mode.title,
                                                icon = when (mode) {
                                                    com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM -> Icons.Rounded.Speed
                                                    com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_EMBEDDED -> Icons.Rounded.Download
                                                }
                                            )
                                        }

                                        com.petal.browser.ui.components.ExpressiveButtonGroup(
                                            items = engineItems,
                                            selectedId = torrentEngineMode,
                                            onItemSelected = { selectedKey ->
                                                torrentEngineMode = selectedKey
                                                val mode = com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.values().firstOrNull { it.key.equals(selectedKey, ignoreCase = true) }
                                                    ?: com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM
                                                com.petal.browser.torrent.PetalTorrentEngineManager.setEngineMode(context, mode)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        val activeEngineMode = com.petal.browser.torrent.PetalTorrentEngineManager.getSelectedEngineMode(context)
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = when (activeEngineMode) {
                                                                com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM -> Icons.Rounded.Speed
                                                                com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_EMBEDDED -> Icons.Rounded.Download
                                                            },
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = activeEngineMode.title,
                                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = activeEngineMode.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
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

                            if ((scaffoldCategory == SettingsCategory.DATA_STORAGE || searchQuery.isNotBlank()) && matchesSearch("Backup Sync", "backup restore sync history bookmarks settings database export import json")) {
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
                            if ((scaffoldCategory == SettingsCategory.UPDATER || searchQuery.isNotBlank()) && matchesSearch("App Updates", "update updater version check launch github download upgrade")) {
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
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Rounded.Sync,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Check for Updates Now",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (isCheckingUpdate) "Checking for updates..." else "Version v$appVersionName ($appVersionCode)",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isCheckingUpdate) {
                                                com.petal.browser.compose.composable.ContainedLoadingIndicator(
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            } else {
                                                Button(
                                                    onClick = {
                                                        com.petal.browser.haptics.PetalHapticEngine.getInstance(context).play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.7f)
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
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Check", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                                }
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).play(com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.6f)
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
                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("View All Release Changelogs")
                                    }
                                }
                            }

                            // 9. About & Developer Profile Section
                            if ((scaffoldCategory == SettingsCategory.ABOUT || searchQuery.isNotBlank()) && matchesSearch("About", "app developer profile version shrey agarwal github licenses terms open source")) {
                                com.petal.browser.ui.components.DeveloperHeroCard(
                                    onCopyGithub = {
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("GitHub URL", "https://github.com/shreyagarwal72")
                                            clipboard.setPrimaryClip(clip)
                                            com.petal.browser.view.NinjaToast.show(context, "Copied GitHub URL to clipboard")
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                )

                                com.petal.browser.ui.components.DeveloperMissionCard()
                                com.petal.browser.ui.components.DeveloperMetricsGrid()

                                com.petal.browser.ui.components.DeveloperTechStackCard()

                                com.petal.browser.ui.components.DeveloperActionsCard(
                                    onOpenUrl = { url ->
                                        try {
                                            if (url == "petal://credits") {
                                                (context as? androidx.activity.ComponentActivity)?.let { act ->
                                                    com.petal.browser.ui.components.PetalCreditsBridge.show(act)
                                                }
                                            } else {
                                                com.petal.browser.unit.BrowserUnit.intentURL(context, Uri.parse(url))
                                            }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                )

                                // --- Footer Copyright ---
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Petal Browser • Open Source Project",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Made with Jetpack Compose & Material 3 Expressive UI",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(32.dp))
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
        val isCategoryDrilled = currentCategory != SettingsCategory.OVERVIEW && searchQuery.isBlank()
        val activeCategory = currentCategory

        if (isCategoryDrilled) {
            // Both backstack entries are rendered live in the same Box, exactly like RV's
            // two-entry NavDisplay: Overview stays composed underneath (isBehind = true) so
            // PetalScreenWrapper has a real surface to blur/dim/parallax, while the drilled
            // category sits on top (isBehind = false) and does the scale/corner-clip/slide.
            com.petal.browser.predictive.PetalPredictiveBackSurface(
                enabled = true,
                onBack = { currentCategory = SettingsCategory.OVERVIEW },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    com.petal.browser.predictive.PetalScreenWrapper(isBehind = true) {
                        RenderCategoryPage(
                            scaffoldCategory = SettingsCategory.OVERVIEW,
                            onHeaderBack = onBackPress
                        )
                    }
                    com.petal.browser.predictive.PetalScreenWrapper(isBehind = false) {
                        RenderCategoryPage(
                            scaffoldCategory = activeCategory,
                            onHeaderBack = { currentCategory = SettingsCategory.OVERVIEW }
                        )
                    }
                }
            }
        } else {
            com.petal.browser.predictive.PetalPredictiveBackSurface(
                enabled = true,
                onBack = onBackPress,
            ) {
                com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
                    RenderCategoryPage(
                        scaffoldCategory = SettingsCategory.OVERVIEW,
                        onHeaderBack = onBackPress
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceHeroBanner(
    selectedTheme: ThemeConfig,
    onThemeSelected: (ThemeConfig) -> Unit
) {
    val isDarkSelected = selectedTheme == ThemeConfig.DARK
    val isLightSelected = selectedTheme == ThemeConfig.LIGHT

    val cardBgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isDarkSelected) {
            androidx.compose.ui.graphics.Color(0xFF2E1A47)
        } else {
            androidx.compose.ui.graphics.Color(0xFF5B21B6)
        },
        animationSpec = androidx.compose.animation.core.tween(500),
        label = "heroCardBg"
    )

    val darkCardScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDarkSelected) 1.05f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
        ),
        label = "darkScale"
    )
    val lightCardScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isLightSelected) 1.05f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
        ),
        label = "lightScale"
    )

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cardBgColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Moon/Sun floating badge in top corner
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isDarkSelected,
                        transitionSpec = {
                            (androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn()).togetherWith(
                                androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                            )
                        },
                        label = "badgeIcon"
                    ) { dark ->
                        Icon(
                            imageVector = if (dark) Icons.Rounded.Nightlight else Icons.Rounded.LightMode,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 40.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = androidx.compose.ui.graphics.Color.White
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Turn it into pure eye candy.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                )

                Spacer(Modifier.height(24.dp))

                // Interactive Mini Theme Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Dark Mode Mini Card
                    Surface(
                        onClick = { onThemeSelected(ThemeConfig.DARK) },
                        shape = RoundedCornerShape(20.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF0F0B15),
                        border = if (isDarkSelected) {
                            androidx.compose.foundation.BorderStroke(3.dp, androidx.compose.ui.graphics.Color.White)
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .graphicsLayer {
                                scaleX = darkCardScale
                                scaleY = darkCardScale
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(androidx.compose.ui.graphics.Color(0xFFB8A0E8))
                                    .then(if (isDarkSelected) Modifier.petalShimmerEffect() else Modifier)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                    }

                    // Light Mode Mini Card
                    Surface(
                        onClick = { onThemeSelected(ThemeConfig.LIGHT) },
                        shape = RoundedCornerShape(20.dp),
                        color = androidx.compose.ui.graphics.Color(0xFFF3E8FF),
                        border = if (isLightSelected) {
                            androidx.compose.foundation.BorderStroke(3.dp, androidx.compose.ui.graphics.Color(0xFF5B21B6))
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .graphicsLayer {
                                scaleX = lightCardScale
                                scaleY = lightCardScale
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(androidx.compose.ui.graphics.Color(0xFF5B21B6))
                                    .then(if (isLightSelected) Modifier.petalShimmerEffect() else Modifier)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(0xFF5B21B6).copy(alpha = 0.6f))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(0xFF5B21B6).copy(alpha = 0.35f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.petalShimmerEffect(): Modifier = composed {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "Shimmer Transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "Shimmer Offset",
    )

    background(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.0f),
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.0f),
            ),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(x = translateAnim, y = translateAnim),
        ),
    )
}

@Composable
private fun PaletteSwatchSquare(
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.aspectRatio(1f)
    ) {
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

@Composable
private fun MiniBrowserSkeletonPreview(
    scheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    val sizeFactor = 0.85f
    fun scaled(dp: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = (dp.value * sizeFactor).dp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            color = scheme.surfaceContainerLow,
            shape = RoundedCornerShape(scaled(24.dp)),
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(scaled(14.dp)),
                verticalArrangement = Arrangement.spacedBy(scaled(10.dp))
            ) {
                // Mini Omnibox Top Bar
                Surface(
                    shape = RoundedCornerShape(scaled(20.dp)),
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(38.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = scaled(10.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(scaled(6.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(scaled(14.dp))
                                    .clip(CircleShape)
                                    .background(scheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .width(scaled(100.dp))
                                    .height(scaled(10.dp))
                                    .clip(RoundedCornerShape(scaled(6.dp)))
                                    .background(scheme.onSurfaceVariant.copy(alpha = 0.35f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(scaled(20.dp))
                                .clip(CircleShape)
                                .background(scheme.secondaryContainer)
                        )
                    }
                }

                // Mini Web / Tab Content Card
                Surface(
                    shape = RoundedCornerShape(scaled(16.dp)),
                    color = scheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(85.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(scaled(12.dp)),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(scaled(110.dp))
                                    .height(scaled(14.dp))
                                    .clip(RoundedCornerShape(scaled(6.dp)))
                                    .background(scheme.onPrimaryContainer.copy(alpha = 0.6f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(scaled(18.dp))
                                    .clip(CircleShape)
                                    .background(scheme.tertiary)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(scaled(8.dp))
                                .clip(RoundedCornerShape(scaled(4.dp)))
                                .background(scheme.onPrimaryContainer.copy(alpha = 0.3f))
                        )
                    }
                }

                // Mini Floating Bottom Bar
                Surface(
                    shape = RoundedCornerShape(scaled(50.dp)),
                    color = scheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(scaled(34.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = scaled(14.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(scaled(20.dp))
                                .clip(RoundedCornerShape(scaled(10.dp)))
                                .background(scheme.primary)
                        )
                        Spacer(Modifier.width(scaled(8.dp)))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(scaled(20.dp))
                                .clip(RoundedCornerShape(scaled(10.dp)))
                                .background(scheme.secondary)
                        )
                        Spacer(Modifier.width(scaled(8.dp)))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(scaled(20.dp))
                                .clip(RoundedCornerShape(scaled(10.dp)))
                                .background(scheme.tertiary)
                        )
                    }
                }
            }
        }
    }
}






