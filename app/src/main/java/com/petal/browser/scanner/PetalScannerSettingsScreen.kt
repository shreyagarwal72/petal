package com.petal.browser.scanner

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.ExpressiveButtonShape

/**
 * Scanner settings screen allowing user to configure auto-open, haptics, beep,
 * history retention, and security warning flags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalScannerSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isLiquidGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    var autoOpen by remember { mutableStateOf(PetalScannerSettings.isAutoOpenEnabled(context)) }
    var vibrate by remember { mutableStateOf(PetalScannerSettings.isVibrateEnabled(context)) }
    var beep by remember { mutableStateOf(PetalScannerSettings.isBeepEnabled(context)) }
    var historyEnabled by remember { mutableStateOf(PetalScannerSettings.isHistoryEnabled(context)) }
    var safetyWarnings by remember { mutableStateOf(PetalScannerSettings.isSafetyWarningsEnabled(context)) }

    PetalPredictiveBackSurface(onBack = onBack) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Scanner Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section: Behavior
                Text(
                    text = "Behavior & Feedback",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )

                SettingsCard(isLiquidGlass = isLiquidGlass) {
                    SettingToggleRow(
                        title = "Auto-open web links",
                        subtitle = "Immediately open recognized website links in the browser",
                        checked = autoOpen,
                        onCheckedChange = {
                            autoOpen = it
                            PetalScannerSettings.setAutoOpenEnabled(context, it)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingToggleRow(
                        title = "Haptic feedback",
                        subtitle = "Vibrate when a barcode is recognized",
                        checked = vibrate,
                        onCheckedChange = {
                            vibrate = it
                            PetalScannerSettings.setVibrateEnabled(context, it)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingToggleRow(
                        title = "Audio beep",
                        subtitle = "Play a short beep sound on detection",
                        checked = beep,
                        onCheckedChange = {
                            beep = it
                            PetalScannerSettings.setBeepEnabled(context, it)
                        }
                    )
                }

                // Section: Safety & Privacy
                Text(
                    text = "Safety & Privacy",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )

                SettingsCard(isLiquidGlass = isLiquidGlass) {
                    SettingToggleRow(
                        title = "Link safety inspection",
                        subtitle = "Flag punycode, lookalike domains, shorteners, and unencrypted HTTP",
                        checked = safetyWarnings,
                        onCheckedChange = {
                            safetyWarnings = it
                            PetalScannerSettings.setSafetyWarningsEnabled(context, it)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingToggleRow(
                        title = "Save scan history",
                        subtitle = "Retain local scan history on this device",
                        checked = historyEnabled,
                        onCheckedChange = {
                            historyEnabled = it
                            PetalScannerSettings.setHistoryEnabled(context, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    isLiquidGlass: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isLiquidGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isLiquidGlass) Modifier.liquidGlassChrome(RoundedCornerShape(20.dp), enabled = true) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
