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
import androidx.preference.PreferenceManager
import com.petal.browser.compose.tabs.PetalInactiveTabManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.unit.PetalTabSessionManager
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Tabs Settings Overview Screen matching Chrome/Brave layout from images.png:
 * - Inactive Tabs row showing current configuration ("After 21 days" / "Never")
 * - Cross-device Tab Groups auto-open switch
 */
@Composable
fun TabsSettingsScreen(
    onNavigateToInactiveSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var thresholdPref by remember {
        mutableStateOf(sp.getString(PetalInactiveTabManager.PREF_INACTIVE_DAYS_THRESHOLD, "21") ?: "21")
    }
    var restoreTabsOnStart by remember {
        mutableStateOf(sp.getBoolean(PetalTabSessionManager.PREF_RESTORE_TABS, true))
    }
    var confirmTabClose by remember {
        mutableStateOf(sp.getBoolean("sp_close_tab_confirm", false))
    }
    var autoOpenFromOtherDevices by remember {
        mutableStateOf(sp.getBoolean("sp_auto_open_tab_groups_other_devices", true))
    }

    var showInactiveSettings by remember { mutableStateOf(false) }

    if (showInactiveSettings) {
        InactiveSettingsScreen(onNavigateBack = { showInactiveSettings = false }, modifier = modifier)
        return
    }

    val thresholdSummary = remember(thresholdPref) {
        when (thresholdPref) {
            "never" -> "Never"
            "7" -> "After 7 days"
            "14" -> "After 14 days"
            "21" -> "After 21 days"
            else -> "After 21 days"
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "tabs_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Tabs",
                subtitle = "Tab management & inactive tabs",
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
                PetalSettingsSection(
                    title = stringResource(R.string.ui_tab_management),
                    icon = Icons.Rounded.Tab,
                    cardId = "tabs_management",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = stringResource(R.string.ui_configure_inactive_tab_archiving_and),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Inactive Tabs Row
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showInactiveSettings = true
                                onNavigateToInactiveSettings()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_inactive),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = thresholdSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Restore Tabs on Startup Switch
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_restore_tabs_on_startup),
                        subtitle = stringResource(R.string.ui_reopen_your_open_tabs_when),
                        icon = Icons.Rounded.Restore,
                        checked = restoreTabsOnStart,
                        onCheckedChange = {
                            restoreTabsOnStart = it
                            sp.edit().putBoolean(PetalTabSessionManager.PREF_RESTORE_TABS, it).apply()
                        }
                    )

                    // Cross-device Tab Groups Switch
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_automatically_open_tab_groups_from),
                        subtitle = stringResource(R.string.ui_sync_tab_sessions_seamlessly_across),
                        icon = Icons.Rounded.Devices,
                        checked = autoOpenFromOtherDevices,
                        onCheckedChange = {
                            autoOpenFromOtherDevices = it
                            sp.edit().putBoolean("sp_auto_open_tab_groups_other_devices", it).apply()
                        }
                    )

                    // Confirm Tab Close Switch
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_confirm_before_closing_tab),
                        subtitle = stringResource(R.string.ui_prompt_for_confirmation_before_closing),
                        icon = Icons.Rounded.Close,
                        checked = confirmTabClose,
                        onCheckedChange = {
                            confirmTabClose = it
                            sp.edit().putBoolean("sp_close_tab_confirm", it).apply()
                        }
                    )
                }
            }
        }
    }
}
