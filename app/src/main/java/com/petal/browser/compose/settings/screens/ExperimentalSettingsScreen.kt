package com.petal.browser.compose.settings.screens

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.settings.viewmodel.ExperimentalSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalShapedPasswordInput
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.ScrollFadeRow
import com.petal.browser.unit.HelperUnit
import com.petal.browser.appleduo.AppleDuoManager

@Composable
fun ExperimentalSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExperimentalSettingsViewModel = hiltViewModel()
) {
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val addressBarPosition by viewModel.addressBarPosition.collectAsStateWithLifecycle()
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

    ExperimentalSettingsScreenContent(
        appLanguage = appLanguage,
        addressBarPosition = addressBarPosition,
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
        onAppLanguageChange = viewModel::setAppLanguage,
        onAddressBarPositionChange = viewModel::setAddressBarPosition,
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
        modifier = modifier
    )
}

@Composable
fun ExperimentalSettingsScreenContent(
    appLanguage: String,
    addressBarPosition: String,
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
    onAppLanguageChange: (String) -> Unit,
    onAddressBarPositionChange: (String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current


    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "experimental_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Experimental",
                subtitle = "App language, experimental features and advanced settings",
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
                // App Language Card
                SettingsCategoryCard(title = "App Language", iconRes = com.petal.browser.R.drawable.translate) {
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
                    ScrollFadeRow(
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
                                            onAppLanguageChange(tag)
                                            HelperUnit.setAppLanguage(context, tag)
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

                // Address Bar Position Card
                SettingsCategoryCard(title = "Address Bar Position (Experimental)", iconRes = com.petal.browser.R.drawable.build_filled) {
                    Text(
                        "Choose whether the URL search address bar appears at the top or bottom of the screen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val addressBarScrollState = rememberScrollState()
                    ScrollFadeRow(
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
                                    onAddressBarPositionChange("TOP")
                                    (context as? BrowserActivity)?.applyAddressBarPosition()
                                    (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                                },
                                label = { Text("Top (Default)") },
                                leadingIcon = if (addressBarPosition == "TOP") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = addressBarPosition == "BOTTOM",
                                onClick = {
                                    onAddressBarPositionChange("BOTTOM")
                                    (context as? BrowserActivity)?.applyAddressBarPosition()
                                    (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                                },
                                label = { Text("Bottom") },
                                leadingIcon = if (addressBarPosition == "BOTTOM") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Navigation Safeguards Card
                SettingsCategoryCard(title = "Navigation Safeguards", icon = Icons.Rounded.ExitToApp) {
                    ToggleRow(
                        title = "Double Back Exit",
                        subtitle = "Press back twice quickly to exit the browser",
                        icon = Icons.Rounded.ExitToApp,
                        checked = doubleBackExit,
                        onCheckedChange = onDoubleBackExitChange
                    )
                }

                // Apple Duo (BETA) Card
                SettingsCategoryCard(
                    title = "Apple Duo (BETA)",
                    icon = Icons.Rounded.Animation
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

                        Divider(
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

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
