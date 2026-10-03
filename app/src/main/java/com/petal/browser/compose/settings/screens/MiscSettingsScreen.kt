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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.lens.PetalLensManager
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.petalGroupShape
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
                title = stringResource(R.string.ui_miscellaneous),
                subtitle = stringResource(R.string.ui_custom_tabs_external_apps_and),
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
                // External Applications & Tools Card: Snap Photo Scanner
                PetalSettingsSection(
                    title = stringResource(R.string.ui_snap_photo_scanner),
                    icon = Icons.Rounded.QrCodeScanner,
                    cardId = "misc_snap_photo",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = stringResource(R.string.ui_choose_which_scanner_receives_photos),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    val snapOptions = listOf(
                        Triple(PetalLensManager.SnapProvider.ASK, stringResource(R.string.ui_snap_provider_ask), Icons.Rounded.HelpOutline),
                        Triple(PetalLensManager.SnapProvider.GOOGLE_LENS, stringResource(R.string.ui_snap_provider_google_lens), Icons.Rounded.Search),
                        Triple(PetalLensManager.SnapProvider.PETAL_SCANNER, stringResource(R.string.ui_snap_provider_petal_scanner), Icons.Rounded.QrCodeScanner)
                    )

                    PetalGroup(rowCount = snapOptions.size) { index, position ->
                        val (provider, label, icon) = snapOptions[index]
                        val isSelected = snapProvider == provider
                        Card(
                            onClick = { onSnapProviderChange(provider) },
                            shape = petalGroupShape(position),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PetalGroupIconBadge(
                                    icon = icon,
                                    container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSnapProviderChange(provider) }
                                )
                            }
                        }
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
                        position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                        title = stringResource(R.string.ui_petal_custom_tabs),
                        subtitle = stringResource(R.string.ui_open_links_from_external_apps),
                        icon = Icons.Rounded.OpenInBrowser,
                        checked = customTabsEnabled,
                        onCheckedChange = onCustomTabsEnabledChange
                    )

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                        title = stringResource(R.string.ui_enhanced_tracking_protection),
                        subtitle = stringResource(R.string.ui_isolate_cross_site_trackers_and),
                        icon = Icons.Rounded.Security,
                        checked = customTabsEtp,
                        onCheckedChange = onCustomTabsEtpChange
                    )

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
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
