package com.petal.browser.compose.settings.screens

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
    viewModel: DisplaySettingsViewModel = hiltViewModel()
) {
    val touchHaptics by viewModel.touchHaptics.collectAsStateWithLifecycle()
    val predictiveBack by viewModel.predictiveBack.collectAsStateWithLifecycle()
    val depthBlur by viewModel.depthBlur.collectAsStateWithLifecycle()
    val fontSizeScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
    val zoomLevelScale by viewModel.zoomLevelScale.collectAsStateWithLifecycle()
    val forceZoom by viewModel.forceZoom.collectAsStateWithLifecycle()
    val readerModeDetection by viewModel.readerModeDetection.collectAsStateWithLifecycle()
    val caretBrowsing by viewModel.caretBrowsing.collectAsStateWithLifecycle()
    val touchpadSwipeNav by viewModel.touchpadSwipeNav.collectAsStateWithLifecycle()

    DisplaySettingsScreenContent(
        touchHaptics = touchHaptics,
        predictiveBack = predictiveBack,
        depthBlur = depthBlur,
        fontSizeScale = fontSizeScale,
        zoomLevelScale = zoomLevelScale,
        forceZoom = forceZoom,
        readerModeDetection = readerModeDetection,
        caretBrowsing = caretBrowsing,
        touchpadSwipeNav = touchpadSwipeNav,
        onTouchHapticsChange = viewModel::setTouchHaptics,
        onPredictiveBackChange = viewModel::setPredictiveBack,
        onDepthBlurChange = viewModel::setDepthBlur,
        onFontSizeScaleChange = viewModel::setFontSizeScale,
        onZoomLevelScaleChange = viewModel::setZoomLevelScale,
        onForceZoomChange = viewModel::setForceZoom,
        onReaderModeDetectionChange = viewModel::setReaderModeDetection,
        onCaretBrowsingChange = viewModel::setCaretBrowsing,
        onTouchpadSwipeNavChange = viewModel::setTouchpadSwipeNav,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun DisplaySettingsScreenContent(
    touchHaptics: Boolean,
    predictiveBack: Boolean,
    depthBlur: Boolean,
    fontSizeScale: Float,
    zoomLevelScale: Float,
    forceZoom: Boolean,
    readerModeDetection: Boolean,
    caretBrowsing: Boolean,
    touchpadSwipeNav: Boolean,
    onTouchHapticsChange: (Boolean) -> Unit,
    onPredictiveBackChange: (Boolean) -> Unit,
    onDepthBlurChange: (Boolean) -> Unit,
    onFontSizeScaleChange: (Float) -> Unit,
    onZoomLevelScaleChange: (Float) -> Unit,
    onForceZoomChange: (Boolean) -> Unit,
    onReaderModeDetectionChange: (Boolean) -> Unit,
    onCaretBrowsingChange: (Boolean) -> Unit,
    onTouchpadSwipeNavChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "display_zoom_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Accessibility",
                subtitle = "Touch haptics, text font scaling and page zoom preview",
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
                // Accessibility & Display Options Card
                SettingsCategoryCard(title = "Accessibility & Display Options", iconRes = com.petal.browser.R.drawable.mobile_vibrate_filled) {
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
                        subtitle = "Vibrate with Ever-Haptics tactile feedback on button presses and UI gestures",
                        icon = Icons.Rounded.Vibration,
                        checked = touchHaptics,
                        onCheckedChange = { newValue ->
                            onTouchHapticsChange(newValue)
                            if (newValue) {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                            }
                        }
                    )

                    if (touchHaptics) {
                        var testPattern by remember { mutableStateOf(PetalHapticEngine.Pattern.CLICK) }
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
                            ScrollFadeRow(
                                scrollState = hapticPatternScrollState,
                                edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(hapticPatternScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PetalHapticEngine.Pattern.values().forEach { pattern ->
                                        FilterChip(
                                            selected = testPattern == pattern,
                                            onClick = {
                                                testPattern = pattern
                                                PetalHapticEngine.getInstance(context).play(pattern, 0.75f)
                                            },
                                            label = { Text(pattern.name.replace("_", " ")) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Text Font Scale Slider & Live Box
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
                            Text("${(fontSizeScale * 100f).toInt()}%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
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
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

                    // Default Page Zoom Slider & Live Box
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
                            Text("${(zoomLevelScale * 100f).toInt()}%", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
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
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
