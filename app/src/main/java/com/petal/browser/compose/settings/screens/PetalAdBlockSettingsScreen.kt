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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalAdBlockMode
import com.petal.browser.browser.PetalFilterSubscription
import com.petal.browser.browser.PetalFilterSubscriptionManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalMaterialAlertDialog
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor
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
                // In glass the card is see-through, so text must follow the surface, not the
                // (light) primaryContainer it would sit on when glass is off.
                val heroTitleColor = if (isGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
                val heroBodyColor = if (isGlass) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                val blockedLabel = if (totalBlocked == 1L) "1 ad or tracker blocked" else "$totalBlocked ads & trackers blocked"
                val listsLabel = if (activeListsCount == 1) "1 filter list" else "$activeListsCount filter lists"

                PetalHeroCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Petal Shield Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = heroTitleColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$blockedLabel across $listsLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroBodyColor
                            )
                        }
                    }
                }

                // ── Protection Mode Selector (Brave Shields Style) ──
                Text(
                    text = "Protection Level",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PetalAdBlockMode.values().forEach { mode ->
                        ShieldModeCard(
                            mode = mode,
                            selected = activeMode == mode,
                            isGlass = isGlass,
                            sheen = sheen,
                            onClick = {
                                activeMode = mode
                                PetalAdBlockEngine.setAdBlockMode(context, mode)
                            }
                        )
                    }
                }

                // ── Filter Subscriptions Section (Cromite Style) ──
                val rowTotal = subscriptions.size + 2 // update row + lists + add row
                val runUpdate: () -> Unit = {
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
                }
                PetalSettingsSection(
                    title = "Filter List Subscriptions",
                    iconRes = com.petal.browser.R.drawable.layers_filled
                ) {
                    // Update lists row
                    PetalGroupListRow(
                        position = petalGroupPositionFor(0, rowTotal),
                        onClick = runUpdate,
                        leading = { PetalGroupIconBadge(Icons.Rounded.Refresh) },
                        content = {
                            Text(
                                text = "Auto-Update Lists",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isUpdatingLists) updateProgressText else "Downloads fresh rules with ETag caching",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailing = {
                            Button(
                                onClick = runUpdate,
                                enabled = !isUpdatingLists,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                if (isUpdatingLists) {
                                    com.petal.browser.compose.composable.PetalContainedLoadingIndicator(size = 18.dp)
                                } else {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Update")
                                }
                            }
                        }
                    )

                    // Individual subscription list items
                    subscriptions.forEachIndexed { index, sub ->
                        val toggle: (Boolean) -> Unit = { enabled ->
                            PetalFilterSubscriptionManager.toggleSubscription(context, sub.id, enabled)
                            subscriptions = PetalFilterSubscriptionManager.getSubscriptions(context)
                        }
                        PetalGroupListRow(
                            position = petalGroupPositionFor(index + 1, rowTotal),
                            onClick = { toggle(!sub.isEnabled) },
                            leading = {
                                PetalGroupIconBadge(
                                    icon = Icons.Rounded.FilterList,
                                    variant = if (sub.isCustom) PetalBadgeVariant.TERTIARY else PetalBadgeVariant.PRIMARY
                                )
                            },
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = sub.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
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
                                                maxLines = 1,
                                                softWrap = false,
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
                            },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconSwitch(
                                        checked = sub.isEnabled,
                                        icon = Icons.Rounded.Check,
                                        onCheckedChange = toggle,
                                        modifier = Modifier.padding(start = 8.dp)
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
                        )
                    }

                    // Add Custom List Button
                    PetalGroupListRow(
                        position = petalGroupPositionFor(rowTotal - 1, rowTotal),
                        onClick = { showAddCustomListDialog = true },
                        leading = { PetalGroupIconBadge(Icons.Rounded.AddCircleOutline) },
                        content = {
                            Text(
                                text = "Add Custom Subscription URL",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }

                // ── Samsung Internet Content Blockers Bridge ──
                val samsungBlockers = remember {
                    com.petal.browser.browser.samsung.SamsungContentBlockerBridge.findInstalledSamsungBlockers(context)
                }
                if (samsungBlockers.isNotEmpty()) {
                    PetalSettingsSection(
                        title = "Samsung Internet Extensions",
                        iconRes = com.petal.browser.R.drawable.layers_filled
                    ) {
                        samsungBlockers.forEachIndexed { index, blocker ->
                            var enabled by remember {
                                mutableStateOf(com.petal.browser.browser.samsung.SamsungContentBlockerBridge.isBlockerEnabled(context, blocker.packageName))
                            }
                            val setEnabled: (Boolean) -> Unit = { isChecked ->
                                enabled = isChecked
                                com.petal.browser.browser.samsung.SamsungContentBlockerBridge.setBlockerEnabled(
                                    context,
                                    blocker.packageName,
                                    isChecked
                                )
                            }
                            PetalGroupListRow(
                                position = petalGroupPositionFor(index, samsungBlockers.size),
                                onClick = { setEnabled(!enabled) },
                                leading = { PetalGroupIconBadge(Icons.Rounded.Security, variant = PetalBadgeVariant.SECONDARY) },
                                content = {
                                    Text(
                                        text = blocker.appName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Samsung Content Blocker (${blocker.packageName})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailing = {
                                    IconSwitch(
                                        checked = enabled,
                                        icon = Icons.Rounded.Check,
                                        onCheckedChange = setEnabled,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            )
                        }
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

                // keep the last row clear of the gesture/nav bar
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
    }
}


/**
 * One protection-level card. Glass: transparent surface + a single rim (no extra border except the
 * selected outline), text colour set explicitly so it never falls back to black on a see-through
 * card. Non-glass: normal tonal Material card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShieldModeCard(
    mode: PetalAdBlockMode,
    selected: Boolean,
    isGlass: Boolean,
    sheen: Float,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(24.dp)
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isGlass -> petalGroupSurfaceColor()
                selected -> scheme.secondaryContainer
                else -> scheme.surfaceContainerHigh
            },
            contentColor = scheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (selected) BorderStroke(if (isGlass) 1.5.dp else 2.dp, scheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChrome(
                shape = cardShape,
                enabled = isGlass,
                sheenIntensity = sheen,
                tintColor = if (selected) scheme.primaryContainer else Color.Unspecified
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The whole card is the click target, so the radio is display-only.
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    if (mode == PetalAdBlockMode.STANDARD) {
                        Surface(shape = CircleShape, color = scheme.primary) {
                            Text(
                                text = "RECOMMENDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onPrimary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}
