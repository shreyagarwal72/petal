package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.containment.PetalSettingsSection

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
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

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
                PetalSettingsSection(
                    title = stringResource(R.string.ui_snap_photo_scanner),
                    icon = Icons.Rounded.QrCodeScanner,
                    cardId = "misc_snap_photo",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = stringResource(R.string.ui_choose_which_scanner_receives_photos),
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
                        Text(stringResource(R.string.ui_choose_again_next_time))
                    }
                }

                // External Applications & Custom Tabs Card
                PetalSettingsSection(
                    title = stringResource(R.string.ui_custom_tabs_external_links),
                    icon = Icons.Rounded.OpenInBrowser,
                    cardId = "misc_apps",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_petal_custom_tabs),
                        subtitle = stringResource(R.string.ui_open_links_from_external_apps),
                        icon = Icons.Rounded.OpenInBrowser,
                        checked = customTabsEnabled,
                        onCheckedChange = onCustomTabsEnabledChange
                    )

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_enhanced_tracking_protection),
                        subtitle = stringResource(R.string.ui_isolate_cross_site_trackers_and),
                        icon = Icons.Rounded.Security,
                        checked = customTabsEtp,
                        onCheckedChange = onCustomTabsEtpChange
                    )

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_auto_open_external_apps),
                        subtitle = stringResource(R.string.ui_allow_youtube_maps_play_store),
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
