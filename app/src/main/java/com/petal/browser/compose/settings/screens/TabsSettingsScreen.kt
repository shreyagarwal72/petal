package com.petal.browser.compose.settings.screens
import com.petal.browser.ui.components.scrollMotionBlur

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
import com.petal.browser.browser.PetalSessionRestoreManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Tabs Settings Overview Screen:
 * - Session restoration on startup
 * - Cross-device Tab Groups auto-open switch
 * - Confirmation before closing tab
 */
@Composable
fun TabsSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var restoreTabsOnStart by remember {
        mutableStateOf(sp.getBoolean(PetalSessionRestoreManager.PREF_RESTORE_TABS, true))
    }
    var confirmTabClose by remember {
        mutableStateOf(sp.getBoolean("sp_close_tab_confirm", false))
    }
    var autoOpenFromOtherDevices by remember {
        mutableStateOf(sp.getBoolean("sp_auto_open_tab_groups_other_devices", true))
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "tabs_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Tabs",
                subtitle = "Tab management & tab groups",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState, flingBehavior = com.petal.browser.ui.theme.layout.petalFlingBehavior())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PetalSettingsSection(
                    title = stringResource(R.string.ui_tab_management),
                    icon = Icons.Rounded.Tab,
                    cardId = "tabs_management",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalGroup(rowCount = 3) { index, position ->
                        when (index) {
                            0 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_restore_tabs_on_startup),
                                    subtitle = stringResource(R.string.ui_reopen_your_open_tabs_when),
                                    icon = Icons.Rounded.Restore,
                                    checked = restoreTabsOnStart,
                                    position = position,
                                    onCheckedChange = {
                                        restoreTabsOnStart = it
                                        sp.edit().putBoolean(PetalSessionRestoreManager.PREF_RESTORE_TABS, it).apply()
                                    }
                                )
                            }
                            1 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_automatically_open_tab_groups_from),
                                    subtitle = stringResource(R.string.ui_sync_tab_sessions_seamlessly_across),
                                    icon = Icons.Rounded.Devices,
                                    checked = autoOpenFromOtherDevices,
                                    position = position,
                                    onCheckedChange = {
                                        autoOpenFromOtherDevices = it
                                        sp.edit().putBoolean("sp_auto_open_tab_groups_other_devices", it).apply()
                                    }
                                )
                            }
                            2 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_confirm_before_closing_tab),
                                    subtitle = stringResource(R.string.ui_prompt_for_confirmation_before_closing),
                                    icon = Icons.Rounded.Close,
                                    checked = confirmTabClose,
                                    position = position,
                                    onCheckedChange = {
                                        confirmTabClose = it
                                        sp.edit().putBoolean("sp_close_tab_confirm", it).apply()
                                    }
                                )
                            }
                        }
                    }
                }

                PetalSettingsSection(
                    title = "Performance & Memory",
                    icon = Icons.Rounded.Speed,
                    cardId = "performance_memory",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = "Firefox-grade CPU prioritization and tab memory hibernation to extend battery life.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    var memorySaverMode by remember {
                        mutableStateOf(sp.getString(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_MEMORY_SAVER_MODE, "balanced") ?: "balanced")
                    }
                    var suspendTimeout by remember {
                        mutableStateOf(sp.getString(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_TAB_SUSPEND_TIMEOUT, "15") ?: "15")
                    }

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { index, position ->
                        when (index) {
                            0 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Memory Saver Mode",
                                    subtitle = if (memorySaverMode != "off") "Frees RAM and halts CPU cycles for background tabs" else "Disabled (all tabs kept loaded)",
                                    icon = Icons.Rounded.Memory,
                                    checked = memorySaverMode != "off",
                                    position = position,
                                    onCheckedChange = { isEnabled ->
                                        val newMode = if (isEnabled) "balanced" else "off"
                                        memorySaverMode = newMode
                                        sp.edit().putString(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_MEMORY_SAVER_MODE, newMode).apply()
                                    }
                                )
                            }
                            1 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.HourglassTop,
                                    title = "Tab Hibernation Interval",
                                    subtitle = if (memorySaverMode == "off") "Disabled" else "After $suspendTimeout minutes of inactivity",
                                    position = position,
                                    onClick = {
                                        if (memorySaverMode != "off") {
                                            val next = when (suspendTimeout) {
                                                "5" -> "15"
                                                "15" -> "30"
                                                "30" -> "5"
                                                else -> "15"
                                            }
                                            suspendTimeout = next
                                            sp.edit().putString(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_TAB_SUSPEND_TIMEOUT, next).apply()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
