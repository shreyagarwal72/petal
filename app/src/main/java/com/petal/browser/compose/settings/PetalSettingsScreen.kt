/*
 * PetalSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Main Material 3 Settings Screen for Petal Browser.
 * Fully migrated to Hilt ViewModel architecture & RvSystem-Monitor visual style.
 */

package com.petal.browser.compose.settings

import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.compose.settings.screens.*
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ScreenWrapper
import com.petal.browser.ui.theme.*

enum class SettingsCategory(val title: String, val subtitle: String, val iconRes: Int) {
    OVERVIEW("Settings", "Browse all settings categories", com.petal.browser.R.drawable.settings_filled),
    API_INTEGRATIONS("API & Integrations Hub", "AndroidX WebKit, Google Credential Manager & Palette APIs", com.petal.browser.R.drawable.ic_rust_logo),
    APPEARANCE("Appearance & Theme", "Fonts, theme modes, color palettes, AMOLED & Material You", com.petal.browser.R.drawable.brightness_medium_filled),
    PRIVACY("Privacy & Security", "AdBlock, HTTPS-only, Private DNS & cookies", com.petal.browser.R.drawable.layers_filled),
    SEARCH_HOMEPAGE("Search Engine & Home", "Default search engine and custom homepage", com.petal.browser.R.drawable.home_filled),
    DISPLAY_ZOOM("Accessibility", "Touch haptics, text font scaling and page zoom preview", com.petal.browser.R.drawable.mobile_vibrate_filled),
    EXPERIMENTAL("Experimental", "App language, intro animations, experimental features and advanced settings", com.petal.browser.R.drawable.build_filled),
    MISCELLANEOUS("Miscellaneous", "Download engine, external apps handling and extra browser tools", com.petal.browser.R.drawable.download_2_filled),
    DATA_STORAGE("Data & Backup", "Backup and restore history, bookmarks & settings", com.petal.browser.R.drawable.backup_filled),
    UPDATER("Updates & Diagnostics", "Release tracker, auto-updates & crash reporting", com.petal.browser.R.drawable.update_rounded),
    ABOUT("About & Developer", "App version, licenses, GitHub & developer", com.petal.browser.R.drawable.info_filled)
}

object PetalSettingsBridge {
    @JvmStatic
    @JvmOverloads
    fun createSettingsView(activity: ComponentActivity, initialCategory: SettingsCategory = SettingsCategory.OVERVIEW, onBackPress: () -> Unit): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        PetalContentSnapshot.clear()
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
                    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key != null && key.startsWith("sp_custom_")) {
                            customFontSettingsState = getCustomFontSettings(sp)
                            customFontPathState = sp.getString("sp_custom_font_path", null)
                        }
                        when (key) {
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                            "sp_gs_flex_preset" -> presetName = sp.getString("sp_gs_flex_preset", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_theme_config" -> themeConfigName = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
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

                val systemDark = isSystemInDarkTheme()
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
                    ScreenWrapper {
                        PetalSettingsScreen(
                            backgroundSnapshot = snapshotBitmap,
                            initialCategory = initialCategory,
                            onBackPress = onBackPress
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PetalSettingsScreen(
    backgroundSnapshot: ImageBitmap? = null,
    initialCategory: SettingsCategory = SettingsCategory.OVERVIEW,
    onBackPress: () -> Unit = {}
) {
    var currentCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var searchQuery by remember { mutableStateOf("") }

    val isCategoryDrilled = currentCategory != SettingsCategory.OVERVIEW && searchQuery.isBlank()
    val activeCategory = currentCategory

    if (isCategoryDrilled) {
        PetalPredictiveBackSurface(
            enabled = true,
            onBack = { currentCategory = SettingsCategory.OVERVIEW }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PetalScreenWrapper(isBehind = true) {
                    SettingsHubScreen(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onCategoryClick = { currentCategory = it },
                        onNavigateBack = onBackPress
                    )
                }
                PetalScreenWrapper(isBehind = false) {
                    RenderCategoryContent(
                        category = activeCategory,
                        onNavigateBack = { currentCategory = SettingsCategory.OVERVIEW }
                    )
                }
            }
        }
    } else {
        PetalPredictiveBackSurface(
            enabled = true,
            onBack = onBackPress
        ) {
            PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
                SettingsHubScreen(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onCategoryClick = { currentCategory = it },
                    onNavigateBack = onBackPress
                )
            }
        }
    }
}

@Composable
private fun RenderCategoryContent(
    category: SettingsCategory,
    onNavigateBack: () -> Unit
) {
    when (category) {
        SettingsCategory.OVERVIEW -> {}
        SettingsCategory.API_INTEGRATIONS -> {
            ApiIntegrationsSettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.APPEARANCE -> {
            AppearanceSettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.PRIVACY -> {
            PrivacySettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.SEARCH_HOMEPAGE -> {
            SearchHomeSettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.DISPLAY_ZOOM -> {
            DisplaySettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.EXPERIMENTAL -> {
            ExperimentalSettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.MISCELLANEOUS -> {
            MiscSettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.DATA_STORAGE -> {
            DataBackupSettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.UPDATER -> {
            UpdaterSettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.ABOUT -> {
            AboutSettingsScreen(onNavigateBack = onNavigateBack)
        }
    }
}
