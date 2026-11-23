package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.components.PetalSpring

import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.icons.imagetoolbox.AutoAwesome
import com.petal.browser.ui.icons.imagetoolbox.HyperOS
import com.petal.browser.ui.icons.imagetoolbox.ImageToolboxIcons
import com.petal.browser.ui.icons.imagetoolbox.ToggleOn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.theme.layout.UiExperience
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.PetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalSwitchType
import com.petal.browser.ui.theme.layout.PetalSliderType
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.PetalContainmentShapeChoice
import com.petal.browser.ui.theme.layout.PetalBadgeShape
import com.petal.browser.ui.theme.layout.rememberPetalLayoutSettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalLanguages
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import com.petal.browser.ui.theme.trackRevealOrigin
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
import com.petal.browser.ui.kit.Card
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.petal.browser.ui.theme.ThemeReveal
import androidx.compose.ui.graphics.vector.ImageVector
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper

/**
 * The pages behind the Appearance hub. Each one is a real sub-page ([com.petal.browser.predictive.PetalSubPageHost]):
 * the hub stays composed behind it and the sub-page owns its own predictive back gesture.
 */
enum class AppearanceSubPage(val title: String, val subtitle: String, val icon: ImageVector) {
    THEME_COLORS("Theme & Colors", "Mode, palettes, Material You & AMOLED", Icons.Rounded.Palette),
    TYPOGRAPHY("Typography", "Fonts, Petal Signature axes & custom font", Icons.Rounded.FontDownload),
    GLASS_BLUR("Glass & Blur", "Liquid Glass surfaces and background blur", Icons.Rounded.BlurOn),
    LAYOUT_SHAPES("Layout & Shapes", "Tab bar, controls, shapes & text selection", Icons.Rounded.Category),
    MOTION_PERFORMANCE("Motion & Performance", "Refresh rate, launch animation, blur & ambient shapes", Icons.Rounded.Speed),
    WIDGETS("Home Screen Widgets", "Customize each Petal widget", Icons.Rounded.Widgets),
    LANGUAGE("App Language", "Display language and website language", Icons.Rounded.Translate);

    companion object {
        /** True for ids that live on the nested Advanced colors page (reached through Theme & Colors). */
        fun isAdvancedColorsId(id: String?): Boolean {
            val k = id?.trim()?.lowercase().orEmpty()
            return k.contains("advanced_color") || k.contains("mix") || k.contains("custom_palette")
        }

        /**
         * Maps a settings-search / featured-carousel highlight id to the page that holds it, so a deep
         * link opens the right page first and the card is then highlighted there.
         */
        fun forHighlightId(id: String?): AppearanceSubPage? {
            val k = id?.trim()?.lowercase().orEmpty()
            if (k.isEmpty()) return null
            return when {
                isAdvancedColorsId(k) -> THEME_COLORS
                k.contains("motion") || k.contains("refresh") || k.contains("morph") ||
                    k.contains("expressive_shapes") || k.contains("launch") || k.contains("ripple") -> MOTION_PERFORMANCE
                k.contains("glass") || k.contains("blur") -> GLASS_BLUR
                k.contains("layout") || k.contains("containment") || k.contains("button_shape") ||
                    k.contains("selection_toolbar") || k.contains("tab_bar") -> LAYOUT_SHAPES
                k.contains("font") || k.contains("typograph") -> TYPOGRAPHY
                k.contains("widget") -> WIDGETS
                k.contains("language") -> LANGUAGE
                k.contains("theme") || k.contains("amoled") || k.contains("dynamic_color") ||
                    k.contains("color") || k.contains("palette") -> THEME_COLORS
                else -> null
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val reveal = rememberThemeReveal()
    // Same ambient glass wallpaper handling as the Privacy page, so glass chrome keeps its backdrop
    // inside the sub-page layers.
    val ambientWallpaper = LocalPetalGlassWallpaper.current
    val wallpaper = ambientWallpaper ?: rememberPetalGlassWallpaper()

    // A deep link (search / featured carousel) opens straight into the right page.
    var subPage by rememberSaveable { mutableStateOf(AppearanceSubPage.forHighlightId(targetHighlightItemId)) }
    val startOnAdvanced = remember(targetHighlightItemId) { AppearanceSubPage.isAdvancedColorsId(targetHighlightItemId) }

    val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()
    val colorStyle by viewModel.colorStyle.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsStateWithLifecycle()
    val floatingTabBar by viewModel.floatingTabBar.collectAsStateWithLifecycle()
    val highRefreshRate by viewModel.highRefreshRate.collectAsStateWithLifecycle()
    val motionBlurEnabled by viewModel.motionBlurEnabled.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    val summaries = mapOf(
        AppearanceSubPage.THEME_COLORS to buildString {
            append(when (themeConfig) {
                ThemeConfig.FOLLOW_SYSTEM -> "System"
                ThemeConfig.LIGHT -> "Light"
                ThemeConfig.DARK -> "Dark"
            })
            append(" · ")
            append(if (dynamicColor) "Material You" else colorStyle.label)
        },
        AppearanceSubPage.TYPOGRAPHY to appFont.label,
        AppearanceSubPage.GLASS_BLUR to (if (liquidGlassEnabled) "Liquid Glass on" else "Liquid Glass off"),
        AppearanceSubPage.LAYOUT_SHAPES to (if (floatingTabBar) "Floating tab bar" else "Docked tab bar"),
        AppearanceSubPage.MOTION_PERFORMANCE to buildList {
            if (highRefreshRate) add("High refresh rate")
            if (motionBlurEnabled) add("Motion blur")
        }.joinToString(" · ").ifEmpty { AppearanceSubPage.MOTION_PERFORMANCE.subtitle },
        AppearanceSubPage.WIDGETS to "3 home screen widgets",
        AppearanceSubPage.LANGUAGE to PetalLanguages.findLanguage(appLanguage).displayLabel
    )

    CompositionLocalProvider(LocalPetalGlassWallpaper provides wallpaper) {
        Box(modifier = modifier.fillMaxSize().trackRevealOrigin(reveal)) {
            com.petal.browser.predictive.PetalSubPageHost(
                visible = subPage != null,
                onBack = { subPage = null },
                subPage = {
                    when (subPage) {
                        AppearanceSubPage.THEME_COLORS -> AppearanceThemePage(
                            reveal = reveal,
                            onBack = { subPage = null },
                            startOnAdvanced = startOnAdvanced,
                            targetHighlightItemId = targetHighlightItemId
                        )
                        AppearanceSubPage.TYPOGRAPHY -> AppearanceTypographyPage(
                            onBack = { subPage = null },
                            targetHighlightItemId = targetHighlightItemId
                        )
                        AppearanceSubPage.GLASS_BLUR -> AppearanceGlassPage(
                            onBack = { subPage = null },
                            targetHighlightItemId = targetHighlightItemId
                        )
                        AppearanceSubPage.LAYOUT_SHAPES -> AppearanceLayoutPage(
                            onBack = { subPage = null },
                            targetHighlightItemId = targetHighlightItemId
                        )
                        AppearanceSubPage.MOTION_PERFORMANCE -> AppearanceMotionPage(
                            onBack = { subPage = null },
                            targetHighlightItemId = targetHighlightItemId
                        )
                        AppearanceSubPage.WIDGETS -> AppearanceWidgetsPage(
                            onBack = { subPage = null },
                            targetHighlightItemId = targetHighlightItemId
                        )
                        AppearanceSubPage.LANGUAGE -> AppearanceLanguagePage(
                            onBack = { subPage = null },
                            targetHighlightItemId = targetHighlightItemId
                        )
                        null -> Unit
                    }
                },
                parent = {
                    AppearanceSubPageScaffold(
                        title = "Appearance & Theme",
                        subtitle = "Fonts, theme modes, color palettes, AMOLED & Material You",
                        pageSeed = "appearance_settings",
                        onBack = onNavigateBack
                    ) {
                        val style = listOf(
                            AppearanceSubPage.THEME_COLORS,
                            AppearanceSubPage.TYPOGRAPHY,
                            AppearanceSubPage.GLASS_BLUR,
                            AppearanceSubPage.LAYOUT_SHAPES
                        )
                        val behavior = listOf(
                            AppearanceSubPage.MOTION_PERFORMANCE,
                            AppearanceSubPage.WIDGETS,
                            AppearanceSubPage.LANGUAGE
                        )
                        PetalSettingsSection(title = "Style") {
                            style.forEachIndexed { index, page ->
                                AppearanceHubRow(
                                    page = page,
                                    summary = summaries[page].orEmpty(),
                                    position = com.petal.browser.ui.containment.petalGroupPositionFor(index, style.size),
                                    onClick = { subPage = page }
                                )
                            }
                        }
                        PetalSettingsSection(title = "Behavior & more") {
                            behavior.forEachIndexed { index, page ->
                                AppearanceHubRow(
                                    page = page,
                                    summary = summaries[page].orEmpty(),
                                    position = com.petal.browser.ui.containment.petalGroupPositionFor(index, behavior.size),
                                    onClick = { subPage = page }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AppearanceHubRow(
    page: AppearanceSubPage,
    summary: String,
    position: com.petal.browser.ui.containment.PetalGroupPosition,
    onClick: () -> Unit
) {
    com.petal.browser.ui.containment.PetalGroupNavigationRow(
        title = page.title,
        subtitle = summary,
        position = position,
        onClick = onClick,
        leadingIcon = { Icon(page.icon, contentDescription = null) }
    )
}
