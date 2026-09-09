package com.petal.browser.compose.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.SettingsSection

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
                SettingsSection(
                    title = "Inactivity Threshold",
                    icon = {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    Text(
                        text = "Move tabs that haven't been opened for this long to the Inactive section.",
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
                                        "Custom duration",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "$customDays day${if (customDays == 1) "" else "s"}",
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
                                        "1 day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "365 days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Auto-Archive ──
                SettingsSection(
                    title = "Auto-Archive",
                    icon = {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                ) {
                    // Archive duplicate tabs
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "Archive duplicate tabs",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Keeps only the most recently used tab when duplicates are found.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconSwitch(
                                checked = archiveDuplicates,
                                icon = Icons.Rounded.ContentCopy,
                                onCheckedChange = {
                                    archiveDuplicates = it
                                    sp.edit().putBoolean(
                                        PetalInactiveTabManager.PREF_ARCHIVE_DUPLICATES, it
                                    ).apply()
                                }
                            )
                        }
                    }

                    // Auto-close after 3 months
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    "Auto-close after 3 months",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Inactive tabs older than 90 days are permanently removed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconSwitch(
                                checked = autoClose3Months,
                                icon = Icons.Rounded.DeleteSweep,
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

                // ── Info Card ──
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
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
                            "Inactive tabs are preserved with their history and can be restored at any time from the Inactive section in the tab switcher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Bud sleeping preview — shows what happens to inactive tabs
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Inactive tabs go to sleep 💤",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        com.petal.browser.ui.components.PetalMascot(
                            expression = com.petal.browser.ui.components.BudExpression.Sleeping,
                            size = 72.dp,
                            playEntrance = false,
                            enableIdleBreathing = true
                        )
                        Text(
                            text = "Bud shows you which tabs haven't been visited lately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
