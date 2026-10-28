/*
 * PetalSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Main Material 3 Settings Screen for Petal Browser.
 * Fully migrated to Hilt ViewModel architecture & RvSystem-Monitor visual style.
 */

package com.petal.browser.compose.settings

import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.compose.settings.screens.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.saveable.rememberSaveable
import com.petal.browser.predictive.LocalZenithHeaderActive
import com.petal.browser.predictive.LocalZenithHeaderHoisted
import com.petal.browser.predictive.PetalPredictiveJunction
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.theme.*

enum class SettingsCategory(val title: String, val subtitle: String, val iconRes: Int) {
    OVERVIEW("Settings", "Browse all settings categories", com.petal.browser.R.drawable.settings_filled),
    API_INTEGRATIONS("API & Integrations Hub", "AndroidX WebKit, Google Credential Manager & Palette APIs", com.petal.browser.R.drawable.ic_ai_stars),
    APPEARANCE("Appearance & Theme", "Fonts, theme modes, color palettes, AMOLED & Material You", com.petal.browser.R.drawable.brightness_medium_filled),
    PRIVACY("Privacy & Security", "AdBlock, HTTPS-only, Private DNS & cookies", com.petal.browser.R.drawable.layers_filled),
    SEARCH_HOMEPAGE("Search Engine & Home", "Default search engine and custom homepage", com.petal.browser.R.drawable.home_filled),
    DISPLAY_ZOOM("Accessibility", "Touch haptics, text font scaling and page zoom preview", com.petal.browser.R.drawable.mobile_vibrate_filled),
    ADDRESS_BAR("Address Bar", "Position, size, gestures and toolbar actions", com.petal.browser.R.drawable.ic_search),
    MEDIA("Media", "Video player, media sniffer & background detection", com.petal.browser.R.drawable.video_filled),
    DOWNLOADS("Downloads", "Download engine, external downloaders & storage management", com.petal.browser.R.drawable.icon_download),
    TABS("Tabs", "Inactive tabs, tab groups and tab cleanup", com.petal.browser.R.drawable.icon_tab),
    MISCELLANEOUS("Miscellaneous", "Custom tabs, external apps and camera tools", com.petal.browser.R.drawable.app_registration),
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

private val SettingsPushEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val SETTINGS_PUSH_MS = 340
private const val SETTINGS_POP_MS = 300

/**
 * One persistent composition for the hub and every category page.
 *
 * The old version swapped between two completely different trees (hub-only vs hub+page) whenever a
 * category opened or closed. That tore down and rebuilt the hub and the predictive-back handler at
 * the exact moment the gesture committed (the visible pop at the end of the back animation), and
 * the open animation was a container transform that shrank a whole blank page down to the tapped
 * row. Now the hub stays composed underneath at all times, a category is a layer pushed on top of
 * it (shared-axis slide + fade, hub parallax/dim/blur easing in alongside), and back, gesture or
 * arrow, simply pops that layer.
 */
@Composable
fun PetalSettingsScreen(
    backgroundSnapshot: ImageBitmap? = null,
    initialCategory: SettingsCategory = SettingsCategory.OVERVIEW,
    onBackPress: () -> Unit = {}
) {
    var pageCategory by remember(initialCategory) {
        mutableStateOf<SettingsCategory?>(initialCategory.takeIf { it != SettingsCategory.OVERVIEW })
    }
    var activeHeaderCategory by remember(initialCategory) {
        mutableStateOf<SettingsCategory?>(initialCategory.takeIf { it != SettingsCategory.OVERVIEW })
    }
    var currentHighlightId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 0 = hub only, 1 = category page fully pushed. Starts pushed when we were opened straight into a category.
    val push = remember(initialCategory) {
        Animatable(if (initialCategory != SettingsCategory.OVERVIEW) 1f else 0f)
    }
    val drilled = pageCategory != null
    val behindFraction: () -> Float = remember(push) { { if (pageCategory != null) push.value else 0f } }

    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsState()
    val isZenithMode = remember(junctionPredictiveEnabled, pageCategory) {
        PetalPredictiveJunction.shouldUseZenithHeaderTransition(
            isPredictiveSettingEnabled = junctionPredictiveEnabled,
            pageKey = if (pageCategory != null) "CATEGORY_DRILLDOWN" else null
        )
    }

    fun openCategory(category: SettingsCategory, highlightId: String?) {
        searchQuery = ""
        currentHighlightId = highlightId
        val wasOpen = pageCategory != null
        activeHeaderCategory = category
        pageCategory = category
        if (!wasOpen) {
            scope.launch {
                push.snapTo(0f)
                push.animateTo(1f, tween(SETTINGS_PUSH_MS, easing = SettingsPushEasing))
            }
        }
    }

    // Arrow / in-page back: animate the page out, then drop it.
    fun closeCategory() {
        activeHeaderCategory = null
        scope.launch {
            push.animateTo(0f, tween(SETTINGS_POP_MS, easing = SettingsPushEasing))
            pageCategory = null
            currentHighlightId = null
        }
    }

    PetalPredictiveBackSurface(
        enabled = true,
        onBack = {
            if (pageCategory != null) {
                // The gesture already slid the page away; remove it without a second animation.
                activeHeaderCategory = null
                pageCategory = null
                currentHighlightId = null
                scope.launch { push.snapTo(0f) }
            } else {
                onBackPress()
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isZenithMode) {
                ExpressiveHeader(
                    title = activeHeaderCategory?.title ?: "Settings",
                    subtitle = activeHeaderCategory?.subtitle ?: "Browser Preferences & Customization",
                    onBack = if (activeHeaderCategory != null) { { closeCategory() } } else onBackPress,
                    isRootHeader = true,
                    actions = {
                        if (activeHeaderCategory == null) {
                            HeaderActionIcon(
                                icon = if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = if (isSearchOpen) "Close search" else "Search settings",
                                onClick = {
                                    if (isSearchOpen) {
                                        isSearchOpen = false
                                        searchQuery = ""
                                    } else {
                                        isSearchOpen = true
                                    }
                                }
                            )
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CompositionLocalProvider(
                    LocalZenithHeaderHoisted provides isZenithMode,
                    LocalZenithHeaderActive provides isZenithMode
                ) {
                    PetalScreenWrapper(
                        isBehind = drilled,
                        backgroundSnapshot = if (drilled) null else backgroundSnapshot,
                        behindFraction = if (drilled) behindFraction else null,
                    ) {
                        SettingsHubScreen(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onCategoryClick = { category, highlightId -> openCategory(category, highlightId) },
                            onNavigateBack = onBackPress,
                            isSearchOpen = isSearchOpen,
                            onSearchOpenChange = { isSearchOpen = it }
                        )
                    }

                    val shown = pageCategory
                    if (shown != null) {
                        PetalScreenWrapper(
                            isBehind = false,
                            modifier = Modifier.graphicsLayer {
                                val e = push.value
                                translationX = (1f - e) * size.width * 0.33f
                                alpha = (e * 2.2f).coerceIn(0f, 1f)
                            }
                        ) {
                            PetalSettingsAnimatedContent(targetKey = shown) { category ->
                                RenderCategoryContent(
                                    category = category,
                                    targetHighlightItemId = currentHighlightId,
                                    onNavigateToAddressBar = { openCategory(SettingsCategory.ADDRESS_BAR, null) },
                                    onNavigateToDownloads = { openCategory(SettingsCategory.DOWNLOADS, null) },
                                    onNavigateBack = { closeCategory() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderCategoryContent(
    category: SettingsCategory,
    targetHighlightItemId: String? = null,
    onNavigateToAddressBar: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    when (category) {
        SettingsCategory.OVERVIEW -> {}
        SettingsCategory.API_INTEGRATIONS -> {
            ApiIntegrationsSettingsScreen(onNavigateBack = onNavigateBack, targetHighlightItemId = targetHighlightItemId)
        }
        SettingsCategory.APPEARANCE -> {
            AppearanceSettingsScreen(onNavigateBack = onNavigateBack, targetHighlightItemId = targetHighlightItemId)
        }
        SettingsCategory.PRIVACY -> {
            PrivacySettingsScreen(onNavigateBack = onNavigateBack, targetHighlightItemId = targetHighlightItemId)
        }
        SettingsCategory.SEARCH_HOMEPAGE -> {
            SearchHomeSettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToAddressBarSettings = onNavigateToAddressBar,
                targetHighlightItemId = targetHighlightItemId
            )
        }
        SettingsCategory.DISPLAY_ZOOM -> {
            DisplaySettingsScreen(onNavigateBack = onNavigateBack)
        }
        SettingsCategory.ADDRESS_BAR -> {
            AddressBarSettingsScreen(onNavigateBack = onNavigateBack, targetHighlightItemId = targetHighlightItemId)
        }
        SettingsCategory.MEDIA -> {
            MediaSettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToDownloads = onNavigateToDownloads,
                targetHighlightItemId = targetHighlightItemId
            )
        }
        SettingsCategory.DOWNLOADS -> {
            DownloadSettingsScreen(onNavigateBack = onNavigateBack, targetHighlightItemId = targetHighlightItemId)
        }
        SettingsCategory.TABS -> {
            TabsSettingsScreen(
                onNavigateToInactiveSettings = { /* dedicated page is shown from Tabs */ },
                onNavigateBack = onNavigateBack,
                targetHighlightItemId = targetHighlightItemId
            )
        }
        SettingsCategory.MISCELLANEOUS -> {
            MiscSettingsScreen(
                onNavigateBack = onNavigateBack,
                targetHighlightItemId = targetHighlightItemId
            )
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


/**
 * Crossfades between categories (e.g. Search -> Address Bar). The content lambda receives the
 * category it is rendering: previously it ignored the animated state and always drew the NEW
 * category, so the outgoing page visibly jumped to the incoming one before fading.
 */
@Composable
private fun PetalSettingsAnimatedContent(
    targetKey: SettingsCategory,
    content: @Composable (SettingsCategory) -> Unit
) {
    AnimatedContent(
        targetState = targetKey,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) togetherWith
                fadeOut(animationSpec = tween(160)))
        },
        label = "petalSettingsContentTransition"
    ) { category ->
        content(category)
    }
}
