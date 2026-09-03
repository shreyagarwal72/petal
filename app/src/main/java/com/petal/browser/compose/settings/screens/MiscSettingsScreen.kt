package com.petal.browser.compose.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.settings.viewmodel.MiscSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground

@Composable
fun MiscSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiscSettingsViewModel = hiltViewModel()
) {
    val autoOpenApps by viewModel.autoOpenApps.collectAsStateWithLifecycle()
    val checkUpdateOnLaunch by viewModel.checkUpdateOnLaunch.collectAsStateWithLifecycle()

    MiscSettingsScreenContent(
        autoOpenApps = autoOpenApps,
        checkUpdateOnLaunch = checkUpdateOnLaunch,
        onAutoOpenAppsChange = viewModel::setAutoOpenApps,
        onCheckUpdateOnLaunchChange = viewModel::setCheckUpdateOnLaunch,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun MiscSettingsScreenContent(
    autoOpenApps: Boolean,
    checkUpdateOnLaunch: Boolean,
    onAutoOpenAppsChange: (Boolean) -> Unit,
    onCheckUpdateOnLaunchChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "misc_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Miscellaneous",
                subtitle = "External apps handling and extra browser tools",
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
                SettingsCategoryCard(title = "External Applications & Links", iconRes = com.petal.browser.R.drawable.download_2_filled) {
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

