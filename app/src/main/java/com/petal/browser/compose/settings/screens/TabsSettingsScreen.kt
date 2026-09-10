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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.compose.tabs.PetalInactiveTabManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground

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
                SettingsCategoryCard(
                    title = "Tab Management",
                    icon = Icons.Rounded.Tab,
                    cardId = "tabs_management",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = "Configure inactive tab archiving and multi-device tab sync",
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
                                    text = "Inactive",
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

                    // Cross-device Tab Groups Switch
                    ToggleRow(
                        title = "Automatically open tab groups from other devices",
                        subtitle = "Sync tab sessions seamlessly across connected devices",
                        icon = Icons.Rounded.Devices,
                        checked = autoOpenFromOtherDevices,
                        onCheckedChange = {
                            autoOpenFromOtherDevices = it
                            sp.edit().putBoolean("sp_auto_open_tab_groups_other_devices", it).apply()
                        }
                    )
                }
            }
        }
    }
}
