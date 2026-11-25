package com.petal.browser.compose.settings.screens
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.theme.PetalText

import com.petal.browser.ui.containment.PetalSettingsSection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.Card
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
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun MiscSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: MiscSettingsViewModel = hiltViewModel()
) {
    val autoOpenApps by viewModel.autoOpenApps.collectAsStateWithLifecycle()
    val customTabsEnabled by viewModel.customTabsEnabled.collectAsStateWithLifecycle()
    val customTabsEtp by viewModel.customTabsEtp.collectAsStateWithLifecycle()

    MiscSettingsScreenContent(
        autoOpenApps = autoOpenApps,
        customTabsEnabled = customTabsEnabled,
        customTabsEtp = customTabsEtp,
        onAutoOpenAppsChange = viewModel::setAutoOpenApps,
        onCustomTabsEnabledChange = viewModel::setCustomTabsEnabled,
        onCustomTabsEtpChange = viewModel::setCustomTabsEtp,
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
    onAutoOpenAppsChange: (Boolean) -> Unit,
    onCustomTabsEnabledChange: (Boolean) -> Unit,
    onCustomTabsEtpChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            pageBackground = { com.petal.browser.ui.components.PetalPageBackground(pageKey = "misc_settings", modifier = Modifier.fillMaxSize()) },
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = stringResource(R.string.ui_miscellaneous),
                    subtitle = stringResource(R.string.ui_custom_tabs_external_apps_and),
                    onBack = onNavigateBack
                )
            }
        ) { topInset ->

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState, flingBehavior = com.petal.browser.ui.theme.layout.petalFlingBehavior())
                    .padding(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

                // Petal Hop: offline mini-game shown when a page can't load because of connectivity
                val offlineGameContext = LocalContext.current
                val offlineGamePrefs = remember(offlineGameContext) {
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(offlineGameContext)
                }
                var offlineGameEnabled by remember {
                    mutableStateOf(
                        offlineGamePrefs.getBoolean(
                            com.petal.browser.offlinegame.PetalOfflineGameOverlay.PREF_ENABLED,
                            true
                        )
                    )
                }
                PetalSettingsSection(
                    title = stringResource(R.string.offline_game_section_title),
                    icon = Icons.Rounded.SportsEsports,
                    cardId = "misc_offline_game",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                        title = stringResource(R.string.offline_game_setting_title),
                        subtitle = stringResource(R.string.offline_game_setting_subtitle),
                        icon = Icons.Rounded.SportsEsports,
                        checked = offlineGameEnabled,
                        onCheckedChange = { enabled ->
                            offlineGameEnabled = enabled
                            offlineGamePrefs.edit()
                                .putBoolean(
                                    com.petal.browser.offlinegame.PetalOfflineGameOverlay.PREF_ENABLED,
                                    enabled
                                )
                                .apply()
                        }
                    )
                }

                // ── Quick Settings Tiles ────────────────────────────────────────────────
                var showQsDialog by remember { mutableStateOf(false) }
                var showLegacyInstructionsDialog by remember { mutableStateOf(false) }

                PetalSettingsSection(
                    title = stringResource(R.string.quick_settings_tiles_title),
                    icon = Icons.Rounded.Widgets,
                    cardId = "misc_quick_settings",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalGroupNavigationRow(
                        title = stringResource(R.string.quick_settings_tiles_title),
                        subtitle = stringResource(R.string.quick_settings_tiles_desc),
                        position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                        leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                showQsDialog = true
                            } else {
                                showLegacyInstructionsDialog = true
                            }
                        }
                    )
                }

                if (showLegacyInstructionsDialog) {
                    com.petal.browser.ui.containment.PetalMaterialAlertDialog(
                        onDismissRequest = { showLegacyInstructionsDialog = false },
                        title = { Text(stringResource(R.string.quick_settings_dialog_title)) },
                        text = { Text(stringResource(R.string.quick_settings_dialog_legacy_instructions)) },
                        confirmButton = {
                            com.petal.browser.ui.kit.TextButton(onClick = { showLegacyInstructionsDialog = false }) {
                                Text(stringResource(R.string.ui_got_it))
                            }
                        }
                    )
                }

                if (showQsDialog) {
                    val qsContext = LocalContext.current
                    com.petal.browser.ui.containment.PetalMaterialAlertDialog(
                        onDismissRequest = { showQsDialog = false },
                        title = { Text(stringResource(R.string.quick_settings_dialog_title)) },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.petal.browser.tiles.PetalQuickSettingsManager.TILES.forEach { tile ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(tile.titleRes),
                                                style = PetalText.rowTitle
                                            )
                                            Text(
                                                text = stringResource(tile.subtitleRes),
                                                style = PetalText.rowSubtitle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        com.petal.browser.ui.kit.FilledTonalButton(
                                            onClick = {
                                                com.petal.browser.tiles.PetalQuickSettingsManager.requestAddTile(
                                                    context = qsContext,
                                                    tile = tile,
                                                    onShowLegacyInstructions = {
                                                        showLegacyInstructionsDialog = true
                                                    }
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(stringResource(R.string.quick_settings_add_tile_btn), style = PetalText.button)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            com.petal.browser.ui.kit.TextButton(onClick = { showQsDialog = false }) {
                                Text(stringResource(R.string.ui_got_it))
                            }
                        }
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
