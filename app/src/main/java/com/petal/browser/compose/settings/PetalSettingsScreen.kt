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
import androidx.compose.ui.draw.clip
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
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalSearchEngineSheetContent
import com.petal.browser.ui.components.StrideSlider
import com.petal.browser.ui.components.availableSearchEngines
import com.petal.browser.ui.theme.*

object PetalSettingsBridge {
    @JvmStatic
    fun createSettingsView(activity: ComponentActivity, onBackPress: () -> Unit): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM") }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 100f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 400)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 0f)) }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 100f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 400)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 0f)
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) {
                    try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.SYSTEM }
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal,
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
    APPEARANCE("Appearance & Theme", "Fonts, color palettes, AMOLED & UI blur", Icons.Rounded.Palette),
    PRIVACY("Privacy & Security", "AdBlock, HTTPS-only, Private DNS & cookies", Icons.Rounded.Shield),
    SEARCH_HOMEPAGE("Search Engine & Home", "Default search engine, custom homepage", Icons.Rounded.Search),
    DISPLAY_ZOOM("Display & Scaling", "Text font scaling and page zoom preview", Icons.Rounded.ZoomIn),
    DATA_STORAGE("Data & Backup", "Backup and restore history, bookmarks & settings", Icons.Rounded.Backup),
    UPDATER("App Updates", "Check for updates and auto-check on launch", Icons.Rounded.SystemUpdate),
    ABOUT("About & Developer", "App version, licenses, GitHub & developer", Icons.Rounded.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSettingsScreen(onBackPress: () -> Unit = {}) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var currentCategory by remember { mutableStateOf(SettingsCategory.OVERVIEW) }
    var searchQuery by remember { mutableStateOf("") }

    // Saved Preference States
    var selectedFont by remember {
        mutableStateOf(try { AppFont.valueOf(sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM") } catch (e: Exception) { AppFont.SYSTEM })
    }
    var fontWidth by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 100f)) }
    var fontWeight by remember { mutableFloatStateOf(sp.getInt("sp_font_weight", 400).toFloat()) }
    var fontRoundness by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 0f)) }
    var selectedColorStyle by remember {
        mutableStateOf(try { ColorStyle.valueOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") } catch (e: Exception) { ColorStyle.TONAL_SPOT })
    }
    var selectedPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var isDynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }

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
    var addressBarPosition by remember { mutableStateOf(sp.getString("sp_address_bar_position", "TOP") ?: "TOP") }
    var fontSize by remember { mutableFloatStateOf(sp.getFloat("sp_font_size_scale", 1.0f)) }
    var zoomLevel by remember { mutableFloatStateOf(sp.getFloat("sp_zoom_level_scale", 1.0f)) }
    var searchEngineIndex by remember { mutableStateOf(sp.getString("sp_search_engine", "0") ?: "0") }
    var showEngineSheet by remember { mutableStateOf(false) }

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

    PetalExpressiveTheme(
        dynamicColor = isDynamicColor,
        useAmoled = isAmoled,
        appFont = selectedFont,
        fontWidth = fontWidth,
        fontWeight = fontWeight.toInt(),
        fontRoundness = fontRoundness,
        colorStyle = selectedColorStyle,
        paletteId = selectedPaletteId
    ) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                    TopAppBar(
                        title = {
                            Text(
                                currentCategory.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (currentCategory != SettingsCategory.OVERVIEW) {
                                    currentCategory = SettingsCategory.OVERVIEW
                                } else {
                                    onBackPress()
                                }
                            }) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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

                    categories.forEach { cat ->
                        Surface(
                            onClick = { currentCategory = cat },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(cat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                    Column {
                                        Text(
                                            cat.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            cat.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
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

                        // --- Font Variation Axes (Width, Weight, Roundness) ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Font Width", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${fontWidth.toInt()}%", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            StrideSlider(
                                value = fontWidth,
                                onValueChange = { newValue ->
                                    fontWidth = newValue
                                    sp.edit().putFloat("sp_font_width", newValue).apply()
                                },
                                valueRange = 75f..125f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Font Weight", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${fontWeight.toInt()}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            StrideSlider(
                                value = fontWeight,
                                onValueChange = { newValue ->
                                    fontWeight = newValue
                                    sp.edit().putInt("sp_font_weight", newValue.toInt()).apply()
                                },
                                valueRange = 100f..900f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Font Roundness", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${fontRoundness.toInt()}%", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            StrideSlider(
                                value = fontRoundness,
                                onValueChange = { newValue ->
                                    fontRoundness = newValue
                                    sp.edit().putFloat("sp_font_roundness", newValue).apply()
                                },
                                valueRange = 0f..100f,
                                modifier = Modifier.fillMaxWidth()
                            )
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        var useBlur by remember { mutableStateOf(sp.getBoolean("sp_use_blur", true)) }
                        var useHeaderBlur by remember { mutableStateOf(sp.getBoolean("sp_use_header_blur", true)) }

                        ToggleRow(
                            title = "Header Blur",
                            subtitle = "Enable translucent frosted blur background on address bar & top headers",
                            icon = Icons.Rounded.AutoAwesome,
                            checked = useHeaderBlur,
                            onCheckedChange = { newValue ->
                                useHeaderBlur = newValue
                                sp.edit().putBoolean("sp_use_header_blur", newValue).apply()
                            }
                        )

                        ToggleRow(
                            title = "UI Blur & Glassmorphism",
                            subtitle = "Enable translucent blurred surfaces across sheets & menus",
                            icon = Icons.Rounded.BlurOn,
                            checked = useBlur,
                            onCheckedChange = { newValue ->
                                useBlur = newValue
                                sp.edit().putBoolean("sp_use_blur", newValue).apply()
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
                        ToggleRow(
                            title = "Auto Picture-in-Picture (PiP)",
                            subtitle = "Automatically enter floating PiP window when performing home gesture during full-screen video playback",
                            icon = Icons.Rounded.PictureInPicture,
                            checked = isAutoPip,
                            onCheckedChange = { newValue ->
                                isAutoPip = newValue
                                sp.edit().putBoolean("sp_auto_pip", newValue).apply()
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

                // 7. Display & Scaling Sliders (using StrideSlider)
                if ((currentCategory == SettingsCategory.DISPLAY_ZOOM || searchQuery.isNotBlank()) && matchesSearch("Display", "text font scale page zoom text scaling stride slider blur address bar top bottom")) {
                    SettingsCategoryCard(title = "Display & Layout Options", icon = Icons.Rounded.FormatSize) {
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
                if ((currentCategory == SettingsCategory.DATA_STORAGE || searchQuery.isNotBlank()) && matchesSearch("Backup Sync", "backup restore sync history bookmarks settings database export import")) {
                    SettingsCategoryCard(title = "Backup & Restore Sync", icon = Icons.Rounded.Backup) {
                        Text(
                            "Export or restore your complete browser data including history, bookmarks, saved sites, and settings:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (context is ComponentActivity) {
                                        if (!com.petal.browser.unit.BackupUnit.checkPermissionStorage(context)) {
                                            com.petal.browser.unit.BackupUnit.requestPermission(context)
                                        } else {
                                            com.petal.browser.unit.BackupUnit.backupData(context, 4) // history
                                            com.petal.browser.unit.BackupUnit.backupData(context, 5) // bookmarks
                                            com.petal.browser.unit.BackupUnit.backupData(context, 1) // saved list
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Backup All")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (context is ComponentActivity) {
                                        if (!com.petal.browser.unit.BackupUnit.checkPermissionStorage(context)) {
                                            com.petal.browser.unit.BackupUnit.requestPermission(context)
                                        } else {
                                            com.petal.browser.unit.BackupUnit.restoreData(context, 4) // history
                                            com.petal.browser.unit.BackupUnit.restoreData(context, 5) // bookmarks
                                            com.petal.browser.unit.BackupUnit.restoreData(context, 1) // saved list
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore All")
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
                                        text = "Current Version: v1.5.0-expressive",
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
                                        Text("v1.5.0-expressive (Build 150)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
