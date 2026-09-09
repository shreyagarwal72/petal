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
import com.petal.browser.ui.components.BudExpression
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalMascot
import com.petal.browser.ui.components.PetalMascotPrefs

/**
 * Tabs Settings Overview Screen matching Chrome/Brave layout from images.png:
 * - Inactive Tabs row showing current configuration ("After 21 days" / "Never")
 * - Cross-device Tab Groups auto-open switch
 */
@Composable
fun TabsSettingsScreen(
    onNavigateToInactiveSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var thresholdPref by remember {
        mutableStateOf(sp.getString(PetalInactiveTabManager.PREF_INACTIVE_DAYS_THRESHOLD, "21") ?: "21")
    }
    var autoOpenFromOtherDevices by remember {
        mutableStateOf(sp.getBoolean("sp_auto_open_tab_groups_other_devices", true))
    }
    var showBudMascot by remember {
        mutableStateOf(sp.getBoolean(PetalMascotPrefs.PREF_SHOW_MASCOT, PetalMascotPrefs.DEFAULT_SHOW_MASCOT))
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
                // Inactive Tabs Row
                Surface(
                    shape = RoundedCornerShape(20.dp),
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
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Inactive",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = thresholdSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Cross-device Tab Groups Switch
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
                        Text(
                            text = "Automatically open tab groups from other devices",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(end = 16.dp)
                        )

                        Switch(
                            checked = autoOpenFromOtherDevices,
                            onCheckedChange = {
                                autoOpenFromOtherDevices = it
                                sp.edit().putBoolean("sp_auto_open_tab_groups_other_devices", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                // Show Bud Mascot Switch
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
                            modifier = Modifier.weight(1f).padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Show Bud mascot",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Show Petal's mascot in empty tab states and the crash recovery dialog",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = showBudMascot,
                            onCheckedChange = {
                                showBudMascot = it
                                sp.edit().putBoolean(PetalMascotPrefs.PREF_SHOW_MASCOT, it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                // Bud live preview — shown only when the mascot toggle is on
                if (showBudMascot) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val expressions = remember { listOf(
                            BudExpression.Neutral,
                            BudExpression.Happy,
                            BudExpression.Thinking,
                            BudExpression.Searching,
                            BudExpression.Sleeping,
                            BudExpression.Excited,
                            BudExpression.Error
                        )}
                        var currentExprIndex by remember { mutableIntStateOf(0) }
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            while (true) {
                                kotlinx.coroutines.delay(2000)
                                currentExprIndex = (currentExprIndex + 1) % expressions.size
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Meet Bud",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PetalMascot(
                                expression = expressions[currentExprIndex],
                                size = 80.dp,
                                playEntrance = false,
                                enableIdleBreathing = true,
                                onClick = { currentExprIndex = (currentExprIndex + 1) % expressions.size }
                            )
                            Text(
                                text = when (expressions[currentExprIndex]) {
                                    is BudExpression.Neutral  -> "Resting"
                                    is BudExpression.Happy    -> "Happy"
                                    is BudExpression.Thinking -> "Thinking"
                                    is BudExpression.Searching -> "Searching"
                                    is BudExpression.Sleeping -> "Sleeping"
                                    is BudExpression.Excited  -> "Excited"
                                    is BudExpression.Error    -> "Error"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
