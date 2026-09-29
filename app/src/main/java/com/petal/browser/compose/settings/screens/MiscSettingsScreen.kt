package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.clickable
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
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.lens.PetalLensManager

@Composable
fun MiscSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: MiscSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val autoOpenApps by viewModel.autoOpenApps.collectAsStateWithLifecycle()
    val customTabsEnabled by viewModel.customTabsEnabled.collectAsStateWithLifecycle()
    val customTabsEtp by viewModel.customTabsEtp.collectAsStateWithLifecycle()
    var snapProvider by remember { mutableStateOf(PetalLensManager.snapProvider(context)) }

    MiscSettingsScreenContent(
        autoOpenApps = autoOpenApps,
        customTabsEnabled = customTabsEnabled,
        customTabsEtp = customTabsEtp,
        snapProvider = snapProvider,
        onAutoOpenAppsChange = viewModel::setAutoOpenApps,
        onCustomTabsEnabledChange = viewModel::setCustomTabsEnabled,
        onCustomTabsEtpChange = viewModel::setCustomTabsEtp,
        onSnapProviderChange = {
            PetalLensManager.setSnapProvider(context, it)
            snapProvider = it
        },
        onNavigateBack = onNavigateBack,
        targetHighlightItemId = targetHighlightItemId,
        modifier = modifier
    )
}

@Composable
fun MiscSettingsScreenContent(
    autoOpenApps: Boolean,
    customTabsEnabled: Boolean,
    customTabsEtp: Boolean,
    snapProvider: PetalLensManager.SnapProvider,
    onAutoOpenAppsChange: (Boolean) -> Unit,
    onCustomTabsEnabledChange: (Boolean) -> Unit,
    onCustomTabsEtpChange: (Boolean) -> Unit,
    onSnapProviderChange: (PetalLensManager.SnapProvider) -> Unit,
    onNavigateBack: () -> Unit,
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "misc_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Miscellaneous",
                subtitle = "Custom tabs, external apps and camera tools",
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
                // External Applications & Tools Card
                SettingsCategoryCard(
                    title = "Snap Photo Scanner",
                    icon = Icons.Rounded.QrCodeScanner,
                    cardId = "misc_snap_photo",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = "Choose which scanner receives photos from Snap Photo and all Petal widgets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        PetalLensManager.SnapProvider.ASK to "Ask every time",
                        PetalLensManager.SnapProvider.GOOGLE_LENS to "Google Lens",
                        PetalLensManager.SnapProvider.PETAL_SCANNER to "Petal QR Scanner"
                    ).forEach { (provider, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSnapProviderChange(provider) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = snapProvider == provider, onClick = { onSnapProviderChange(provider) })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = { onSnapProviderChange(PetalLensManager.SnapProvider.ASK) }) {
                        Text("Choose again next time")
                    }
                }

                // External Applications & Custom Tabs Card
                SettingsCategoryCard(
                    title = "Custom Tabs & External Links",
                    icon = Icons.Rounded.OpenInBrowser,
                    cardId = "misc_apps",
                    targetHighlightId = targetHighlightItemId
                ) {
                    ToggleRow(
                        title = "Petal Custom Tabs",
                        subtitle = "Open links from external apps in a fast, lightweight Custom Tab overlay",
                        icon = Icons.Rounded.OpenInBrowser,
                        checked = customTabsEnabled,
                        onCheckedChange = onCustomTabsEnabledChange
                    )

                    ToggleRow(
                        title = "Enhanced Tracking Protection",
                        subtitle = "Isolate cross-site trackers and block known tracking scripts inside Custom Tabs",
                        icon = Icons.Rounded.Security,
                        checked = customTabsEtp,
                        onCheckedChange = onCustomTabsEtpChange
                    )

                    ToggleRow(
                        title = "Auto Open External Apps",
                        subtitle = "Allow YouTube, Maps & Play Store links to open in external native apps instead of Petal",
                        icon = Icons.Rounded.Launch,
                        checked = autoOpenApps,
                        onCheckedChange = onAutoOpenAppsChange
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
