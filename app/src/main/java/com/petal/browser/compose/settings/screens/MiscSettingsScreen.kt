package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.torrent.PetalTorrentEngineManager
import com.petal.browser.ui.components.ExpressiveButtonGroup
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.ExpressiveSegmentItem
import com.petal.browser.ui.components.M3ExpressiveVariableBackground

@Composable
fun MiscSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiscSettingsViewModel = hiltViewModel()
) {
    val torrentEngineMode by viewModel.torrentEngineMode.collectAsStateWithLifecycle()
    val autoOpenApps by viewModel.autoOpenApps.collectAsStateWithLifecycle()
    val checkUpdateOnLaunch by viewModel.checkUpdateOnLaunch.collectAsStateWithLifecycle()

    MiscSettingsScreenContent(
        torrentEngineMode = torrentEngineMode,
        autoOpenApps = autoOpenApps,
        checkUpdateOnLaunch = checkUpdateOnLaunch,
        onTorrentEngineModeChange = viewModel::setTorrentEngineMode,
        onAutoOpenAppsChange = viewModel::setAutoOpenApps,
        onCheckUpdateOnLaunchChange = viewModel::setCheckUpdateOnLaunch,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun MiscSettingsScreenContent(
    torrentEngineMode: String,
    autoOpenApps: Boolean,
    checkUpdateOnLaunch: Boolean,
    onTorrentEngineModeChange: (String) -> Unit,
    onAutoOpenAppsChange: (Boolean) -> Unit,
    onCheckUpdateOnLaunchChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "misc_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Miscellaneous",
                subtitle = "Download engine, external apps handling and extra browser tools",
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
                // Download Options Card
                SettingsCategoryCard(title = "Miscellaneous & Download Options", iconRes = com.petal.browser.R.drawable.download_2_filled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Download Engine",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Select your preferred engine for downloads, torrents, and magnet links:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val engineItems = PetalTorrentEngineManager.TorrentEngineMode.values().map { mode ->
                            ExpressiveSegmentItem(
                                id = mode.key,
                                label = mode.title,
                                icon = when (mode) {
                                    PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM -> Icons.Rounded.Speed
                                    PetalTorrentEngineManager.TorrentEngineMode.ENGINE_EMBEDDED -> Icons.Rounded.Download
                                }
                            )
                        }

                        ExpressiveButtonGroup(
                            items = engineItems,
                            selectedId = torrentEngineMode,
                            onItemSelected = { selectedKey ->
                                onTorrentEngineModeChange(selectedKey)
                                val mode = PetalTorrentEngineManager.TorrentEngineMode.values().firstOrNull { it.key.equals(selectedKey, ignoreCase = true) }
                                    ?: PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM
                                PetalTorrentEngineManager.setEngineMode(context, mode)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        val activeEngineMode = PetalTorrentEngineManager.getSelectedEngineMode(context)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (activeEngineMode) {
                                                PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM -> Icons.Rounded.Speed
                                                PetalTorrentEngineManager.TorrentEngineMode.ENGINE_EMBEDDED -> Icons.Rounded.Download
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeEngineMode.title,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = activeEngineMode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
