package com.petal.browser.compose.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Timer
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
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.PetalSettingsSection
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Inactive Tabs Settings Screen — Material 3 Expressive redesign.
 *
 * Threshold picker: SingleChoiceSegmentedButtonRow (Never / 7d / 14d / 21d / Custom).
 * Custom option reveals an animated Slider (1–365 days) with a live day-count badge.
 * Toggles use IconSwitch for Archive Duplicates and Auto-Close.
 * Informational card at bottom explains inactive tab behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InactiveSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var thresholdPref by remember {
        mutableStateOf(sp.getString(PetalInactiveTabManager.PREF_INACTIVE_DAYS_THRESHOLD, "21") ?: "21")
    }
    var customDays by remember {
        mutableIntStateOf(sp.getInt(PetalInactiveTabManager.PREF_CUSTOM_INACTIVE_DAYS, 21).coerceIn(1, 365))
    }
    var archiveDuplicates by remember {
        mutableStateOf(sp.getBoolean(PetalInactiveTabManager.PREF_ARCHIVE_DUPLICATES, true))
    }
    var autoClose3Months by remember {
        mutableStateOf(sp.getBoolean(PetalInactiveTabManager.PREF_AUTO_CLOSE_INACTIVE_3_MONTHS, true))
    }

    val thresholdOptions = listOf("never" to "Never", "7" to "7d", "14" to "14d", "21" to "21d", "custom" to "Custom")

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "inactive_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Inactive Tabs",
                subtitle = "Manage automatic tab archiving",
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Inactivity Threshold ──
                PetalSettingsSection(
                    title = stringResource(R.string.ui_inactivity_threshold),
                    icon = Icons.Filled.Timer,
                ) {
                    Text(
                        text = stringResource(R.string.ui_move_tabs_that_haven_t),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.height(4.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        thresholdOptions.forEachIndexed { index, (key, label) ->
                            SegmentedButton(
                                selected = thresholdPref == key,
                                onClick = {
                                    thresholdPref = key
                                    sp.edit().putString(
                                        PetalInactiveTabManager.PREF_INACTIVE_DAYS_THRESHOLD, key
                                    ).apply()
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = thresholdOptions.size
                                ),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = thresholdPref == key)
                                },
                                label = {
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = thresholdPref == "custom",
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.ui_custom_duration),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_day, customDays, if (customDays == 1) "" else "s"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Slider(
                                    value = customDays.toFloat(),
                                    onValueChange = { v ->
                                        customDays = v.toInt().coerceIn(1, 365)
                                        sp.edit().putInt(
                                            PetalInactiveTabManager.PREF_CUSTOM_INACTIVE_DAYS, customDays
                                        ).apply()
                                    },
                                    valueRange = 1f..365f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(R.string.ui_1_day),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        stringResource(R.string.ui_365_days),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Auto-Archive ──
                PetalSettingsSection(
                    title = stringResource(R.string.ui_auto_archive),
                    icon = Icons.Filled.Archive,
                ) {
                    com.petal.browser.ui.containment.PetalGroup(rowCount = 2) { index, position ->
                        when (index) {
                            0 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_archive_duplicate_tabs),
                                    subtitle = stringResource(R.string.ui_keeps_only_the_most_recently),
                                    icon = Icons.Rounded.ContentCopy,
                                    checked = archiveDuplicates,
                                    position = position,
                                    onCheckedChange = {
                                        archiveDuplicates = it
                                        sp.edit().putBoolean(
                                            PetalInactiveTabManager.PREF_ARCHIVE_DUPLICATES, it
                                        ).apply()
                                    }
                                )
                            }
                            1 -> {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = stringResource(R.string.ui_auto_close_after_3_months),
                                    subtitle = stringResource(R.string.ui_inactive_tabs_older_than_90),
                                    icon = Icons.Rounded.DeleteSweep,
                                    checked = autoClose3Months,
                                    position = position,
                                    onCheckedChange = {
                                        autoClose3Months = it
                                        sp.edit().putBoolean(
                                            PetalInactiveTabManager.PREF_AUTO_CLOSE_INACTIVE_3_MONTHS, it
                                        ).apply()
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Info Card ──
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 1.dp)
                        )
                        Text(
                            stringResource(R.string.ui_inactive_tabs_are_preserved_with),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
