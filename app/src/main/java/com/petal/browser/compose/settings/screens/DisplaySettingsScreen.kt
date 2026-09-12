package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.accessibility.PetalAccessibilityEngine
import com.petal.browser.compose.settings.viewmodel.DisplaySettingsViewModel
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.ScrollFadeRow

@Composable
fun DisplaySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: DisplaySettingsViewModel = hiltViewModel()
) {
    val touchHaptics by viewModel.touchHaptics.collectAsStateWithLifecycle()
    val scrollHaptics by viewModel.scrollHaptics.collectAsStateWithLifecycle()
    val predictiveBack by viewModel.predictiveBack.collectAsStateWithLifecycle()
    val depthBlur by viewModel.depthBlur.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val zoomLevelScale by viewModel.zoomLevelScale.collectAsStateWithLifecycle()
    val forceZoom by viewModel.forceZoom.collectAsStateWithLifecycle()
    val readerModeDetection by viewModel.readerModeDetection.collectAsStateWithLifecycle()
    val caretBrowsing by viewModel.caretBrowsing.collectAsStateWithLifecycle()
    val touchpadSwipeNav by viewModel.touchpadSwipeNav.collectAsStateWithLifecycle()
    val addressBarSwipeTabs by viewModel.addressBarSwipeTabs.collectAsStateWithLifecycle()
    val addressBarQuickActions by viewModel.addressBarQuickActions.collectAsStateWithLifecycle()

    val doubleBackExit by viewModel.doubleBackExit.collectAsStateWithLifecycle()
    val appleDuoEnabled by viewModel.appleDuoEnabled.collectAsStateWithLifecycle()
    val appleDuoWebsites by viewModel.appleDuoWebsites.collectAsStateWithLifecycle()
    val appleDuoUseSensor by viewModel.appleDuoUseSensor.collectAsStateWithLifecycle()
    val appleDuoManualTilt by viewModel.appleDuoManualTilt.collectAsStateWithLifecycle()
    val appleDuoAutoRecenter by viewModel.appleDuoAutoRecenter.collectAsStateWithLifecycle()
    val appleDuoEyeDistance by viewModel.appleDuoEyeDistance.collectAsStateWithLifecycle()
    val appleDuoBlurSpread by viewModel.appleDuoBlurSpread.collectAsStateWithLifecycle()
    val appleDuoDarkening by viewModel.appleDuoDarkening.collectAsStateWithLifecycle()
    val appleDuoCurrentTilt by viewModel.appleDuoCurrentTilt.collectAsStateWithLifecycle()
    val appleDuoCurrentHinge by viewModel.appleDuoCurrentHinge.collectAsStateWithLifecycle()
    val appleDuoHasSensor by viewModel.appleDuoHasSensor.collectAsStateWithLifecycle()

    DisplaySettingsScreenContent(
        touchHaptics = touchHaptics,
        scrollHaptics = scrollHaptics,
        predictiveBack = predictiveBack,
        depthBlur = depthBlur,
        fontSizeScale = fontSizeScale,
        zoomLevelScale = zoomLevelScale,
        forceZoom = forceZoom,
        readerModeDetection = readerModeDetection,
        caretBrowsing = caretBrowsing,
        touchpadSwipeNav = touchpadSwipeNav,
        addressBarSwipeTabs = addressBarSwipeTabs,
        addressBarQuickActions = addressBarQuickActions,
        doubleBackExit = doubleBackExit,
        appleDuoEnabled = appleDuoEnabled,
        appleDuoWebsites = appleDuoWebsites,
        appleDuoUseSensor = appleDuoUseSensor,
        appleDuoManualTilt = appleDuoManualTilt,
        appleDuoAutoRecenter = appleDuoAutoRecenter,
        appleDuoEyeDistance = appleDuoEyeDistance,
        appleDuoBlurSpread = appleDuoBlurSpread,
        appleDuoDarkening = appleDuoDarkening,
        appleDuoCurrentTilt = appleDuoCurrentTilt,
        appleDuoCurrentHinge = appleDuoCurrentHinge,
        appleDuoHasSensor = appleDuoHasSensor,
        onTouchHapticsChange = viewModel::setTouchHaptics,
        onScrollHapticsChange = viewModel::setScrollHaptics,
        onPredictiveBackChange = viewModel::setPredictiveBack,
        onDepthBlurChange = viewModel::setDepthBlur,
        onFontSizeScaleChange = viewModel::setFontSizeScale,
        onZoomLevelScaleChange = viewModel::setZoomLevelScale,
        onForceZoomChange = viewModel::setForceZoom,
        onReaderModeDetectionChange = viewModel::setReaderModeDetection,
        onCaretBrowsingChange = viewModel::setCaretBrowsing,
        onTouchpadSwipeNavChange = viewModel::setTouchpadSwipeNav,
        onAddressBarSwipeTabsChange = viewModel::setAddressBarSwipeTabs,
        onAddressBarQuickActionsChange = viewModel::setAddressBarQuickActions,
        onDoubleBackExitChange = viewModel::setDoubleBackExit,
        onAppleDuoEnabledChange = viewModel::setAppleDuoEnabled,
        onAppleDuoWebsitesChange = viewModel::setAppleDuoWebsites,
        onAppleDuoUseSensorChange = viewModel::setAppleDuoUseSensor,
        onAppleDuoManualTiltChange = viewModel::setAppleDuoManualTilt,
        onAppleDuoAutoRecenterChange = viewModel::setAppleDuoAutoRecenter,
        onAppleDuoEyeDistanceChange = viewModel::setAppleDuoEyeDistance,
        onAppleDuoBlurSpreadChange = viewModel::setAppleDuoBlurSpread,
        onAppleDuoDarkeningChange = viewModel::setAppleDuoDarkening,
        onAppleDuoRecalibrate = viewModel::recalibrateAppleDuo,
        onNavigateBack = onNavigateBack,
        targetHighlightItemId = targetHighlightItemId,
        modifier = modifier
    )
}

@Composable
fun DisplaySettingsScreenContent(
    touchHaptics: Boolean,
    scrollHaptics: Boolean,
    predictiveBack: Boolean,
    depthBlur: Boolean,
    fontSizeScale: Float,
    zoomLevelScale: Float,
    forceZoom: Boolean,
    readerModeDetection: Boolean,
    caretBrowsing: Boolean,
    touchpadSwipeNav: Boolean,
    addressBarSwipeTabs: Boolean,
    addressBarQuickActions: Boolean,
    doubleBackExit: Boolean,
    appleDuoEnabled: Boolean,
    appleDuoWebsites: Boolean,
    appleDuoUseSensor: Boolean,
    appleDuoManualTilt: Float,
    appleDuoAutoRecenter: Boolean,
    appleDuoEyeDistance: Float,
    appleDuoBlurSpread: Float,
    appleDuoDarkening: Float,
    appleDuoCurrentTilt: Float,
    appleDuoCurrentHinge: Float,
    appleDuoHasSensor: Boolean,
    onTouchHapticsChange: (Boolean) -> Unit,
    onScrollHapticsChange: (Boolean) -> Unit,
    onPredictiveBackChange: (Boolean) -> Unit,
    onDepthBlurChange: (Boolean) -> Unit,
    onFontSizeScaleChange: (Float) -> Unit,
    onZoomLevelScaleChange: (Float) -> Unit,
    onForceZoomChange: (Boolean) -> Unit,
    onReaderModeDetectionChange: (Boolean) -> Unit,
    onCaretBrowsingChange: (Boolean) -> Unit,
    onTouchpadSwipeNavChange: (Boolean) -> Unit,
    onAddressBarSwipeTabsChange: (Boolean) -> Unit,
    onAddressBarQuickActionsChange: (Boolean) -> Unit,
    onDoubleBackExitChange: (Boolean) -> Unit,
    onAppleDuoEnabledChange: (Boolean) -> Unit,
    onAppleDuoWebsitesChange: (Boolean) -> Unit,
    onAppleDuoUseSensorChange: (Boolean) -> Unit,
    onAppleDuoManualTiltChange: (Float) -> Unit,
    onAppleDuoAutoRecenterChange: (Boolean) -> Unit,
    onAppleDuoEyeDistanceChange: (Float) -> Unit,
    onAppleDuoBlurSpreadChange: (Float) -> Unit,
    onAppleDuoDarkeningChange: (Float) -> Unit,
    onAppleDuoRecalibrate: () -> Unit,
    onNavigateBack: () -> Unit,
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "display_zoom_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Accessibility & Display",
                subtitle = "Navigation gestures, haptics, font scaling, and 3D visual effects",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Navigation Safeguards Card
                SettingsCategoryCard(
                    title = "Navigation Safeguards",
                    icon = Icons.Rounded.ExitToApp,
                    cardId = "nav_safeguards",
                    targetHighlightId = targetHighlightItemId
                ) {
                    ToggleRow(
                        title = "Double Back to Exit",
                        subtitle = "Press back twice quickly to exit the browser",
                        icon = Icons.Rounded.ExitToApp,
                        checked = doubleBackExit,
                        onCheckedChange = onDoubleBackExitChange
                    )
                }

                // Apple Duo (BETA) Card
                SettingsCategoryCard(
                    title = "Apple Duo (BETA)",
                    icon = Icons.Rounded.Animation,
                    cardId = "apple_duo",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = "Real-time frosted-glass fold and 3D device tilt perspective animation adapted from Duo-animation by Atomicx7.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ToggleRow(
                        title = "Enable Apple Duo Fold Effect",
                        subtitle = "Apply dynamic frosted glass fold animation responding to device tilt",
                        icon = Icons.Rounded.Animation,
                        checked = appleDuoEnabled,
                        onCheckedChange = onAppleDuoEnabledChange
                    )

                    if (appleDuoEnabled) {
                        ToggleRow(
                            title = "Show in Websites",
                            subtitle = "Keep the 3D frosted fold active when viewing web pages and websites",
                            icon = Icons.Rounded.Language,
                            checked = appleDuoWebsites,
                            onCheckedChange = onAppleDuoWebsitesChange
                        )

                        ToggleRow(
                            title = if (appleDuoUseSensor && appleDuoHasSensor) "Sensor Motion Tracking" else "Manual Tilt Control",
                            subtitle = if (!appleDuoHasSensor) "Rotation sensor unavailable on this device — manual mode active" else "Use gyroscope and game rotation sensors for 6DoF tilt",
                            icon = if (appleDuoUseSensor && appleDuoHasSensor) Icons.Rounded.ScreenRotation else Icons.Rounded.Tune,
                            checked = appleDuoUseSensor && appleDuoHasSensor,
                            enabled = appleDuoHasSensor,
                            onCheckedChange = onAppleDuoUseSensorChange
                        )

                        if (!appleDuoUseSensor || !appleDuoHasSensor) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Manual Tilt Angle",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "%.1f° (Hinge %s)".format(appleDuoManualTilt, if (appleDuoManualTilt >= 0f) "Right" else "Left"),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                PetalSlider(
                                    value = appleDuoManualTilt,
                                    onValueChange = onAppleDuoManualTiltChange,
                                    valueRange = -45f..45f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Text(
                                text = "Live Tilt: %.1f° · Hinge %s".format(appleDuoCurrentTilt, if (appleDuoCurrentHinge >= 0f) "Right" else "Left"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            ToggleRow(
                                title = "Auto-Recenter Washout",
                                subtitle = "Continuously absorb gyro drift and posture changes while phone is still",
                                icon = Icons.Rounded.Autorenew,
                                checked = appleDuoAutoRecenter,
                                onCheckedChange = onAppleDuoAutoRecenterChange
                            )

                            Button(
                                onClick = onAppleDuoRecalibrate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.FilterCenterFocus, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Recalibrate Zero Pose")
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Text(
                            text = "Physics & Shader Tuning",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Eye Distance Slider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Eye Distance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.0f mm".format(appleDuoEyeDistance),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = appleDuoEyeDistance,
                                onValueChange = onAppleDuoEyeDistanceChange,
                                valueRange = 200f..800f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Blur Spread Slider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Blur Spread Radius",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.3f".format(appleDuoBlurSpread),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = appleDuoBlurSpread,
                                onValueChange = onAppleDuoBlurSpreadChange,
                                valueRange = 0.02f..0.30f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Darkening Slider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Glass Frost Darkening",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.3f".format(appleDuoDarkening),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = appleDuoDarkening,
                                onValueChange = onAppleDuoDarkeningChange,
                                valueRange = 0.001f..0.05f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                // Accessibility & Display Options Card
                SettingsCategoryCard(
                    title = "Accessibility & Display Options",
                    iconRes = com.petal.browser.R.drawable.mobile_vibrate_filled,
                    cardId = "display",
                    targetHighlightId = targetHighlightItemId
                ) {
                    ToggleRow(
                        title = "Predictive Back Animations",
                        subtitle = "Enable fluid predictive back gesture scaling and slide transitions across all screens",
                        icon = Icons.Rounded.Animation,
                        checked = predictiveBack,
                        onCheckedChange = onPredictiveBackChange
                    )

                    ToggleRow(
                        title = "Depth Blur Effects",
                        subtitle = "Show 24.dp depth blur and black dim overlay on back pages during navigation and predictive gestures",
                        icon = Icons.Rounded.BlurOn,
                        checked = depthBlur,
                        onCheckedChange = onDepthBlurChange
                    )

                    ToggleRow(
                        title = "Touch Haptics Engine",
                        subtitle = "Tactile feedback on button presses and UI interactions",
                        icon = Icons.Rounded.Vibration,
                        checked = touchHaptics,
                        onCheckedChange = { newValue ->
                            onTouchHapticsChange(newValue)
                            if (newValue) {
                                PetalHapticEngine.getInstance(context).playClick(context)
                            }
                        }
                    )

                    ToggleRow(
                        title = "Scroll Haptics",
                        subtitle = "Subtle tactile feedback while scrolling web pages and lists",
                        icon = Icons.Rounded.TouchApp,
                        checked = scrollHaptics,
                        enabled = touchHaptics,
                        onCheckedChange = { newValue ->
                            onScrollHapticsChange(newValue)
                            if (newValue && touchHaptics) {
                                PetalHapticEngine.getInstance(context).playClick(context)
                            }
                        }
                    )
                    // Text Font Scale Slider & Live Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Text Font Scale",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${(fontSizeScale * 100f).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            PetalSlider(
                                value = fontSizeScale,
                                onValueChange = onFontSizeScaleChange,
                                valueRange = 0.7f..1.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "LIVE FONT PREVIEW (${(fontSizeScale * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "The quick brown fox jumps over the lazy dog.",
                                        fontSize = (15 * fontSizeScale).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Default Page Zoom Slider & Live Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Default Page Zoom",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${(zoomLevelScale * 100f).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            PetalSlider(
                                value = zoomLevelScale,
                                onValueChange = onZoomLevelScaleChange,
                                valueRange = 0.8f..2.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "LIVE ZOOM PREVIEW (${(zoomLevelScale * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((75 * zoomLevelScale).dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size((12 * zoomLevelScale).dp)) {}
                                                Text(
                                                    "Sample Web Page Article",
                                                    fontSize = (12 * zoomLevelScale).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                "Rendering responsive web content at ${(zoomLevelScale * 100).toInt()}% zoom scale.",
                                                fontSize = (10 * zoomLevelScale).sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ToggleRow(
                        title = "Force Enable Zoom (Override Viewport)",
                        subtitle = "Override website viewport locks (user-scalable=no) to allow pinch-to-zoom on all pages",
                        icon = Icons.Rounded.ZoomIn,
                        checked = forceZoom,
                        onCheckedChange = onForceZoomChange
                    )

                    ToggleRow(
                        title = "Simplified View for Webpages",
                        subtitle = "Detect article content and enable reader mode prompts for clean distraction-free reading",
                        icon = Icons.Rounded.Article,
                        checked = readerModeDetection,
                        onCheckedChange = onReaderModeDetectionChange
                    )

                    ToggleRow(
                        title = "Caret Browsing (F7 Shortcut)",
                        subtitle = "Navigate and select text within webpages using a movable keyboard cursor (toggle anytime via F7)",
                        icon = Icons.Rounded.TextFormat,
                        checked = caretBrowsing,
                        onCheckedChange = { newValue ->
                            onCaretBrowsingChange(newValue)
                            PetalAccessibilityEngine.setCaretBrowsing(context, null, newValue)
                        }
                    )

                    ToggleRow(
                        title = "Touchpad Two-Finger Navigation",
                        subtitle = "Swipe horizontally with two fingers on a touchpad or trackpad to navigate back and forward in history",
                        icon = Icons.Rounded.Swipe,
                        checked = touchpadSwipeNav,
                        onCheckedChange = onTouchpadSwipeNavChange
                    )

                    ToggleRow(
                        title = "Address Bar Horizontal Swipe to Switch Tabs",
                        subtitle = "Swipe left or right across the address bar pill to fluidly switch between open tabs",
                        icon = Icons.Rounded.Swipe,
                        checked = addressBarSwipeTabs,
                        onCheckedChange = onAddressBarSwipeTabsChange
                    )

                    ToggleRow(
                        title = "Address Bar Long-Press Quick Actions",
                        subtitle = "Long press the address bar for quick actions: Clean Copy, Paste & Go, Bookmark, and Hard Refresh",
                        icon = Icons.Rounded.TouchApp,
                        checked = addressBarQuickActions,
                        onCheckedChange = onAddressBarQuickActionsChange
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                PetalAccessibilityEngine.launchCaptionSettings(context)
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
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
