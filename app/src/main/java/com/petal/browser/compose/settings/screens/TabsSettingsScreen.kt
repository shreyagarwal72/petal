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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.browser.PetalSessionRestoreManager
import com.petal.browser.ui.components.ExpressiveHeader
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
    var showWorkspaceChips by remember {
        mutableStateOf(sp.getBoolean(com.petal.browser.compose.tabs.PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false))
    }

    LaunchedEffect(Unit) {
        com.petal.browser.compose.tabs.PetalWorkspaceManager.migrateWorkspaceChipsVisibility(context)
        showWorkspaceChips = sp.getBoolean(com.petal.browser.compose.tabs.PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            pageBackground = { com.petal.browser.ui.components.PetalPageBackground(pageKey = "tabs_settings", modifier = Modifier.fillMaxSize()) },
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = "Tabs",
                    subtitle = "Tab management & tab groups",
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
                            2 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_show_workspace_chips),
                                    subtitle = stringResource(R.string.ui_show_workspace_chips_desc),
                                    icon = Icons.Rounded.Workspaces,
                                    checked = showWorkspaceChips,
                                    position = position,
                                    onCheckedChange = {
                                        showWorkspaceChips = it
                                        sp.edit().putBoolean(com.petal.browser.compose.tabs.PetalWorkspaceManager.PREF_SHOW_WORKSPACE_CHIPS, it).apply()
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
                        style = PetalText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    var memorySaverMode by remember {
                        mutableStateOf(sp.getString(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_MEMORY_SAVER_MODE, "balanced") ?: "balanced")
                    }
                    var suspendTimeout by remember {
                        mutableStateOf(sp.getString(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_TAB_SUSPEND_TIMEOUT, "15") ?: "15")
                    }

                    var neverSuspendPinnedMedia by remember { mutableStateOf(sp.getBoolean(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_NEVER_SUSPEND_PINNED_MEDIA, true)) }
                    var autoArchiveDays by remember { mutableStateOf(sp.getString(com.petal.browser.browser.PetalTabArchiver.PREF_AUTO_ARCHIVE_DAYS, "0") ?: "0") }

                    com.petal.browser.ui.containment.PetalGroup(rowCount = 4) { index, position ->
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
                                    subtitle = if (memorySaverMode == "off") "Disabled" else if (suspendTimeout == "never") "Never (only under memory pressure)" else "After $suspendTimeout minutes of inactivity",
                                    position = position,
                                    onClick = {
                                        if (memorySaverMode != "off") {
                                            val next = when (suspendTimeout) {
                                                "5" -> "15"
                                                "15" -> "30"
                                                "30" -> "never"
                                                "never" -> "5"
                                                else -> "15"
                                            }
                                            suspendTimeout = next
                                            sp.edit().putString(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_TAB_SUSPEND_TIMEOUT, next).apply()
                                        }
                                    }
                                )
                            }
                            2 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_never_suspend_pinned_media),
                                    subtitle = stringResource(R.string.ui_never_suspend_pinned_media_desc),
                                    icon = Icons.Rounded.PushPin,
                                    checked = neverSuspendPinnedMedia,
                                    position = position,
                                    onCheckedChange = {
                                        neverSuspendPinnedMedia = it
                                        sp.edit().putBoolean(com.petal.browser.engine.gecko.PetalTabSuspender.PREF_NEVER_SUSPEND_PINNED_MEDIA, it).apply()
                                    }
                                )
                            }
                            3 -> {
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.Archive,
                                    title = stringResource(R.string.ui_auto_archive_tabs),
                                    subtitle = if (autoArchiveDays == "0") stringResource(R.string.ui_auto_archive_off)
                                        else stringResource(R.string.ui_auto_archive_after_days, autoArchiveDays.toIntOrNull() ?: 0),
                                    position = position,
                                    onClick = {
                                        val next = when (autoArchiveDays) { "0" -> "7"; "7" -> "14"; "14" -> "30"; else -> "0" }
                                        autoArchiveDays = next
                                        sp.edit().putString(com.petal.browser.browser.PetalTabArchiver.PREF_AUTO_ARCHIVE_DAYS, next).apply()
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
