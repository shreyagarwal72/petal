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

@Composable
fun AppearanceMotionPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val expressiveBgShapes by viewModel.expressiveBgShapes.collectAsStateWithLifecycle()
    val bgShapeChangeMode by viewModel.bgShapeChangeMode.collectAsStateWithLifecycle()
    val bgShapeRotationMin by viewModel.bgShapeRotationMin.collectAsStateWithLifecycle()
    val highRefreshRate by viewModel.highRefreshRate.collectAsStateWithLifecycle()
    val launchRippleEnabled by viewModel.launchRippleEnabled.collectAsStateWithLifecycle()
    val motionBlurEnabled by viewModel.motionBlurEnabled.collectAsStateWithLifecycle()
    val motionBlurScale by viewModel.motionBlurScale.collectAsStateWithLifecycle()
    val onExpressiveBgShapesChange: (Boolean) -> Unit = viewModel::setExpressiveBgShapes
    val onBgShapeChangeModeChange: (String) -> Unit = viewModel::setBgShapeChangeMode
    val onBgShapeRotationMinChange: (Int) -> Unit = viewModel::setBgShapeRotationMin
    val onHighRefreshRateChange: (Boolean) -> Unit = viewModel::setHighRefreshRate
    val onLaunchRippleEnabledChange: (Boolean) -> Unit = viewModel::setLaunchRippleEnabled
    val onMotionBlurEnabledChange: (Boolean) -> Unit = viewModel::setMotionBlurEnabled
    val onMotionBlurScaleChange: (Float) -> Unit = viewModel::setMotionBlurScale

    val maxDetectedRefreshRate = remember(context) {
        PetalHighRefreshRateManager.getMaxSupportedRefreshRate(context)
    }

    var showMorphingShapesExplainer by remember { mutableStateOf(false) }
    var showMotionBlurExplainer by remember { mutableStateOf(false) }

    AppearanceSubPageScaffold(
        title = "Motion & Performance",
        subtitle = "Refresh rate, launch animation, motion blur & ambient shapes",
        pageSeed = "appearance_motion",
        onBack = onBack,
        modifier = modifier,
        overlay = {
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
        }
    ) {
        PetalSettingsSection(
            title = stringResource(R.string.ui_display_performance),
            icon = Icons.Rounded.Speed,
            cardId = "appearance_refresh",
            targetHighlightId = targetHighlightItemId,
            onInfoClick = { showMotionBlurExplainer = true }
        ) {
            var appLaunchAnim by remember {
                mutableStateOf(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_app_launch_animation", true))
            }
            var showSplashPreview by remember { mutableStateOf(false) }

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
                position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
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

            if (appLaunchAnim) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = { showSplashPreview = true },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Preview Animation")
                    }
                }
            }

            if (showSplashPreview) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showSplashPreview = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.petal.browser.compose.splash.PetalSplashScreen(
                            onSplashComplete = { showSplashPreview = false },
                            durationMs = 2200L
                        )
                    }
                }
            }

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
                enter = fadeIn(animationSpec = PetalSpring.calm()) + expandVertically(animationSpec = PetalSpring.calm()),
                exit = fadeOut(animationSpec = PetalSpring.calm()) + shrinkVertically(animationSpec = PetalSpring.calm())
            ) {
                com.petal.browser.ui.containment.PetalHeroCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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
        }

        // Ambient morphing background shapes (moved here from the old Layout section)
        PetalSettingsSection(
            title = stringResource(R.string.ui_m3_expressive_morphing_shapes),
            iconRes = com.petal.browser.R.drawable.layers_filled,
            cardId = "appearance_expressive_shapes",
            targetHighlightId = targetHighlightItemId,
            onInfoClick = { showMorphingShapesExplainer = true }
        ) {
            // Material 3 Expressive Background Morphing Shapes Toggle
            com.petal.browser.ui.containment.PetalSettingsToggleRow(
                title = stringResource(R.string.ui_m3_expressive_morphing_shapes),
                subtitle = stringResource(R.string.ui_display_ambient_morphing_background_sh),
                icon = Icons.Rounded.BubbleChart,
                checked = expressiveBgShapes,
                position = if (expressiveBgShapes) com.petal.browser.ui.containment.PetalGroupPosition.TOP else com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                onCheckedChange = onExpressiveBgShapesChange
            )

            AnimatedVisibility(
                visible = expressiveBgShapes,
                enter = fadeIn(animationSpec = PetalSpring.calm()) + expandVertically(animationSpec = PetalSpring.calm()),
                exit = fadeOut(animationSpec = PetalSpring.calm()) + shrinkVertically(animationSpec = PetalSpring.calm())
            ) {
                com.petal.browser.ui.containment.PetalHeroCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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
        }
    }
}
