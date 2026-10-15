/*
 * PetalAdBlockSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive AdBlock & Shields Control Center for Petal Browser.
 * Combines Brave Shields modes, Cromite filter subscriptions, and Firefox GeckoView ETP.
 */

package com.petal.browser.compose.settings.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalAdBlockMode
import com.petal.browser.browser.PetalFilterSubscription
import com.petal.browser.browser.PetalFilterSubscriptionManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalMaterialAlertDialog
import com.petal.browser.ui.containment.PetalSettingsSection
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetalAdBlockSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeMode by remember { mutableStateOf(PetalAdBlockEngine.getAdBlockMode(context)) }
    var subscriptions by remember { mutableStateOf(PetalFilterSubscriptionManager.getSubscriptions(context)) }
    var isUpdatingLists by remember { mutableStateOf(false) }
    var updateProgressText by remember { mutableStateOf("") }

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sp = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = if (isGlass) sp.getFloat("sp_liquid_glass_sheen", 0.60f).coerceIn(0f, 1f) else 0.60f

    var showAddCustomListDialog by remember { mutableStateOf(false) }
    var customListNameInput by remember { mutableStateOf("") }
    var customListUrlInput by remember { mutableStateOf("") }
    var customListError by remember { mutableStateOf<String?>(null) }

    var showCustomRulesDialog by remember { mutableStateOf(false) }
    var customRulesText by remember { mutableStateOf(PetalFilterSubscriptionManager.getCustomUserRules(context)) }

    var showWhitelistDialog by remember { mutableStateOf(false) }
    var whitelistDomainInput by remember { mutableStateOf("") }
    var whitelistedDomains by remember { mutableStateOf(PetalAdBlockEngine.getWhitelistedDomains()) }

    // Dialog: Add Custom Filter Subscription
    if (showAddCustomListDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = {
                showAddCustomListDialog = false
                customListError = null
            },
            title = { Text("Add Custom Filter List") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the URL of an EasyList, AdGuard, or hosts formatted filter list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customListNameInput,
                        onValueChange = { customListNameInput = it },
                        label = { Text("List Name") },
                        placeholder = { Text("My AdBlock Rules") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = customListUrlInput,
                        onValueChange = {
                            customListUrlInput = it
                            customListError = null
                        },
                        label = { Text("List URL") },
                        placeholder = { Text("https://example.com/rules.txt") },
                        singleLine = true,
                        isError = customListError != null,
                        supportingText = customListError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = PetalFilterSubscriptionManager.addCustomSubscription(
                            context = context,
                            name = customListNameInput.trim(),
                            url = customListUrlInput.trim()
                        )
                        result.onSuccess {
                            subscriptions = PetalFilterSubscriptionManager.getSubscriptions(context)
                            showAddCustomListDialog = false
                            customListNameInput = ""
                            customListUrlInput = ""
                            customListError = null
                        }.onFailure {
                            customListError = it.message ?: "Failed to add subscription"
                        }
                    }
                ) {
                    Text("Add List")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomListDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Custom User Rules
    if (showCustomRulesDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showCustomRulesDialog = false },
            title = { Text("Custom Filter Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add custom blocking rules (one per line, e.g. ||doubleclick.net^ or domain.com).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customRulesText,
                        onValueChange = { customRulesText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("||example.com/ads/*\n0.0.0.0 tracking.server.com") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        PetalFilterSubscriptionManager.saveCustomUserRules(context, customRulesText)
                        showCustomRulesDialog = false
                    }
                ) {
                    Text("Save Rules")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRulesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Domain Whitelist
    if (showWhitelistDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showWhitelistDialog = false },
            title = { Text("Whitelisted Domains") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Domains added here will completely bypass shields and ad blocking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = whitelistDomainInput,
                            onValueChange = { whitelistDomainInput = it },
                            placeholder = { Text("example.com") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (whitelistDomainInput.isNotBlank()) {
                                    PetalAdBlockEngine.addDomainToWhitelist(context, whitelistDomainInput.trim())
                                    whitelistedDomains = PetalAdBlockEngine.getWhitelistedDomains()
                                    whitelistDomainInput = ""
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (whitelistedDomains.isEmpty()) {
                        Text(
                            "No whitelisted domains.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        whitelistedDomains.forEach { domain ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(domain, style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = {
                                        PetalAdBlockEngine.removeDomainFromWhitelist(context, domain)
                                        whitelistedDomains = PetalAdBlockEngine.getWhitelistedDomains()
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhitelistDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "adblock_control_center")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Shields & AdBlock Control",
                subtitle = "Brave Shields, Cromite subscriptions & GeckoView ETP",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Hero Stats Card ──
                val totalBlocked = PetalAdBlockEngine.getTotalBlockedCount()
                val activeListsCount = subscriptions.count { it.isEnabled }

                PetalHeroCard(
                    title = "Petal Shield Active",
                    subtitle = "$totalBlocked ads & trackers blocked across $activeListsCount filter lists",
                    icon = Icons.Rounded.Security,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // ── Protection Mode Selector (Brave Shields Style) ──
                Text(
                    text = "Protection Level",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PetalAdBlockMode.values().forEach { mode ->
                        val isSelected = activeMode == mode
                        val (cardColor, borderColor) = if (isSelected) {
                            Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primary)
                        } else {
                            Pair(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), Color.Transparent)
                        }
                        val cardShape = RoundedCornerShape(16.dp)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .clickable {
                                    activeMode = mode
                                    PetalAdBlockEngine.setAdBlockMode(context, mode)
                                }
                                .com.petal.browser.ui.containment.liquidGlassChrome(
                                    shape = cardShape,
                                    enabled = isGlass,
                                    sheenIntensity = sheen
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else cardColor
                            ),
                            border = if (!isGlass && isSelected) BorderStroke(2.dp, borderColor) else if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            shape = cardShape
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        activeMode = mode
                                        PetalAdBlockEngine.setAdBlockMode(context, mode)
                                    }
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (mode == PetalAdBlockMode.STANDARD) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "RECOMMENDED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Filter Subscriptions Section (Cromite Style) ──
                PetalSettingsSection(
                    title = "Filter List Subscriptions",
                    iconRes = com.petal.browser.R.drawable.layers_filled
                ) {
                    // Update lists row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Update Lists",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isUpdatingLists) updateProgressText else "Downloads fresh rules with ETag caching",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                if (!isUpdatingLists) {
                                    isUpdatingLists = true
                                    coroutineScope.launch {
                                        PetalFilterSubscriptionManager.updateAllSubscriptions(
                                            context = context,
                                            force = true,
                                            onProgress = { done, total, name ->
                                                updateProgressText = "Updating ($done/$total): $name"
                                            }
                                        )
                                        subscriptions = PetalFilterSubscriptionManager.getSubscriptions(context)
                                        isUpdatingLists = false
                                        updateProgressText = ""
                                    }
                                }
                            },
                            enabled = !isUpdatingLists,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isUpdatingLists) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Update")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Individual subscription list items
                    subscriptions.forEachIndexed { index, sub ->
                        val position = when {
                            subscriptions.size == 1 -> PetalGroupPosition.SOLO
                            index == 0 -> PetalGroupPosition.TOP
                            index == subscriptions.size - 1 -> PetalGroupPosition.BOTTOM
                            else -> PetalGroupPosition.MIDDLE
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = sub.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (sub.isCustom) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "CUSTOM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = sub.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (sub.rulesCount > 0) {
                                    val dateStr = if (sub.lastUpdatedMs > 0) {
                                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(sub.lastUpdatedMs))
                                    } else "Bundled"
                                    Text(
                                        text = "${sub.rulesCount} rules · Updated $dateStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Switch(
                                checked = sub.isEnabled,
                                onCheckedChange = { enabled ->
                                    PetalFilterSubscriptionManager.toggleSubscription(context, sub.id, enabled)
                                    subscriptions = PetalFilterSubscriptionManager.getSubscriptions(context)
                                }
                            )
                            if (sub.isCustom) {
                                IconButton(
                                    onClick = {
                                        PetalFilterSubscriptionManager.removeCustomSubscription(context, sub.id)
                                        subscriptions = PetalFilterSubscriptionManager.getSubscriptions(context)
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete custom list", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Add Custom List Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddCustomListDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add Custom Subscription URL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ── Rules & Whitelist Section ──
                PetalSettingsSection(
                    title = "Rules & Exceptions",
                    iconRes = com.petal.browser.R.drawable.ic_search
                ) {
                    com.petal.browser.ui.containment.PetalGroupNavigationRow(
                        title = "Custom User Rules",
                        subtitle = "Write or paste custom adblock and cosmetic rules",
                        position = PetalGroupPosition.TOP,
                        onClick = { showCustomRulesDialog = true },
                        leadingIcon = { Icon(Icons.Rounded.EditNote, contentDescription = null) }
                    )
                    com.petal.browser.ui.containment.PetalGroupNavigationRow(
                        title = "Domain Whitelist (${whitelistedDomains.size} domains)",
                        subtitle = "Websites allowed to bypass shields",
                        position = PetalGroupPosition.BOTTOM,
                        onClick = { showWhitelistDialog = true },
                        leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) }
                    )
                }
            }
        }
    }
}
