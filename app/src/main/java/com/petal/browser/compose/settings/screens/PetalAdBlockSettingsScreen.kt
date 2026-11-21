/*
 * PetalAdBlockSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Recreated Material 3 Expressive Shields & Content Protection Center.
 * Designed for extreme simplicity, clarity, and rock-solid privacy:
 *   1. Master Shield Hero Card & 1-tap protection switch.
 *   2. Clear Protection Modes: Standard (Recommended) vs Aggressive.
 *   3. 4 Everyday Protection Toggles (Ads, Cookie Banners, Annoyances, URL Tracking).
 *   4. Clean Site Exceptions (Whitelisted Domains) management.
 *   5. Streamlined Advanced Tools (1-tap Filter Updates & Custom Rules).
 */

package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.components.PetalSpring

import android.content.Context
import androidx.compose.animation.*
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
import androidx.preference.PreferenceManager
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalAdBlockMode
import com.petal.browser.browser.PetalFilterSubscriptionManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.theme.ExpressiveButtonShape
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
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }

    // Core States
    var activeMode by remember { mutableStateOf(PetalAdBlockEngine.getAdBlockMode(context)) }
    val isShieldEnabled = activeMode != PetalAdBlockMode.DISABLED

    var cookieDismissEnabled by remember {
        mutableStateOf(sp.getBoolean("sp_cookie_banner_auto_reject", sp.getBoolean("petal_builtin_consent_manager", true)))
    }
    var blockAnnoyances by remember {
        mutableStateOf(sp.getBoolean("sp_block_annoyances", true))
    }
    var stripTrackingParams by remember {
        mutableStateOf(sp.getBoolean("sp_strip_tracking_parameters", true))
    }

    // Filter List & Advanced State
    var subscriptions by remember { mutableStateOf(PetalFilterSubscriptionManager.getSubscriptions(context)) }
    var isUpdatingLists by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var showAdvancedLists by remember { mutableStateOf(false) }

    // Whitelist State
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var whitelistDomainInput by remember { mutableStateOf("") }
    var whitelistedDomains by remember { mutableStateOf(PetalAdBlockEngine.getWhitelistedDomains()) }

    // Custom Rules & Lists Dialogs
    var showCustomRulesDialog by remember { mutableStateOf(false) }
    var customRulesText by remember { mutableStateOf(PetalFilterSubscriptionManager.getCustomUserRules(context)) }
    var showAddCustomListDialog by remember { mutableStateOf(false) }
    var customListNameInput by remember { mutableStateOf("") }
    var customListUrlInput by remember { mutableStateOf("") }
    var customListError by remember { mutableStateOf<String?>(null) }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val heroTitleColor = if (isGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    val heroBodyColor = if (isGlass) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)

    // ─────────────────────────────────────────────────────────────────────────
    // Dialog: Domain Whitelist
    // ─────────────────────────────────────────────────────────────────────────
    if (showWhitelistDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showWhitelistDialog = false },
            title = { Text("Allowed Websites") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Websites added here will completely bypass ad blocking and tracking shields.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = whitelistDomainInput,
                            onValueChange = { whitelistDomainInput = it },
                            placeholder = { Text("example.com") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            onClick = {
                                val domain = whitelistDomainInput.trim()
                                if (domain.isNotBlank()) {
                                    PetalAdBlockEngine.addDomainToWhitelist(context, domain)
                                    whitelistedDomains = PetalAdBlockEngine.getWhitelistedDomains()
                                    whitelistDomainInput = ""
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (whitelistedDomains.isEmpty()) {
                        Text(
                            "No websites currently whitelisted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            whitelistedDomains.forEach { domain ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = domain,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(
                                        onClick = {
                                            PetalAdBlockEngine.removeDomainFromWhitelist(context, domain)
                                            whitelistedDomains = PetalAdBlockEngine.getWhitelistedDomains()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove website",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Dialog: Custom User Rules
    // ─────────────────────────────────────────────────────────────────────────
    if (showCustomRulesDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showCustomRulesDialog = false },
            title = { Text("Custom Filter Rules") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Write or paste custom EasyList/uBlock rules (one per line, e.g. ||doubleclick.net^ or @@||example.com^).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = customRulesText,
                        onValueChange = { customRulesText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("||example.com/ads/*\n@@||trusted-site.com^") }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Dialog: Add Custom Filter Subscription URL
    // ─────────────────────────────────────────────────────────────────────────
    if (showAddCustomListDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = {
                showAddCustomListDialog = false
                customListError = null
            },
            title = { Text("Add Filter Subscription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the URL of an EasyList, uBlock, or hosts format filter list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = customListNameInput,
                        onValueChange = { customListNameInput = it },
                        label = { Text("List Name") },
                        placeholder = { Text("My Custom List") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    TextField(
                        value = customListUrlInput,
                        onValueChange = {
                            customListUrlInput = it
                            customListError = null
                        },
                        label = { Text("List URL") },
                        placeholder = { Text("https://example.com/filters.txt") },
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
                            customListError = it.message ?: "Invalid URL or duplicate subscription"
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

    // ─────────────────────────────────────────────────────────────────────────
    // Main UI Layout
    // ─────────────────────────────────────────────────────────────────────────
    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "petal_shields_center")

        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = "Shields & Ad Blocking",
                    subtitle = "Fast, clean, tracker-free browsing",
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
                // ── 1. Hero Shield Status Card ──
                val totalBlocked = PetalAdBlockEngine.getTotalBlockedCount()
                val blockedLabel = if (totalBlocked == 1L) "1 ad or tracker blocked" else "$totalBlocked ads & trackers blocked"

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
                            color = if (isShieldEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isShieldEnabled) Icons.Rounded.Shield else Icons.Rounded.ShieldMoon,
                                    contentDescription = null,
                                    tint = if (isShieldEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isShieldEnabled) "Petal Shield Active" else "Petal Shield Disabled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = heroTitleColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isShieldEnabled) blockedLabel else "Browsing without ad or tracker defense",
                                style = MaterialTheme.typography.bodyMedium,
                                color = heroBodyColor
                            )
                        }
                    }
                }

                // ── 2. Master Shield Switch ──
                PetalSettingsToggleRow(
                    position = PetalGroupPosition.SINGLE,
                    title = "Shield Protection",
                    subtitle = "Blocks ads, trackers, banners, and intrusive network requests",
                    icon = Icons.Rounded.Security,
                    checked = isShieldEnabled,
                    onCheckedChange = { on ->
                        activeMode = if (on) PetalAdBlockMode.STANDARD else PetalAdBlockMode.DISABLED
                        PetalAdBlockEngine.setAdBlockMode(context, activeMode)
                    }
                )

                // ── 3. Protection Mode Selector (When Shield is Active) ──
                AnimatedVisibility(
                    visible = isShieldEnabled,
                    enter = fadeIn(animationSpec = PetalSpring.calm()) + expandVertically(animationSpec = PetalSpring.calm()),
                    exit = fadeOut(animationSpec = PetalSpring.calm()) + shrinkVertically(animationSpec = PetalSpring.calm())
                ) {
                    PetalSettingsSection(
                        title = "Protection Level",
                        icon = Icons.Rounded.Tune
                    ) {
                        PetalGroupListRow(
                            position = PetalGroupPosition.TOP,
                            onClick = {
                                activeMode = PetalAdBlockMode.STANDARD
                                PetalAdBlockEngine.setAdBlockMode(context, activeMode)
                            },
                            leading = {
                                RadioButton(
                                    selected = activeMode == PetalAdBlockMode.STANDARD,
                                    onClick = {
                                        activeMode = PetalAdBlockMode.STANDARD
                                        PetalAdBlockEngine.setAdBlockMode(context, activeMode)
                                    }
                                )
                            },
                            content = {
                                Text(
                                    text = "Standard (Recommended)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Blocks cross-site trackers, ads, and cookies while preserving first-party page features.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        PetalGroupListRow(
                            position = PetalGroupPosition.BOTTOM,
                            onClick = {
                                activeMode = PetalAdBlockMode.AGGRESSIVE
                                PetalAdBlockEngine.setAdBlockMode(context, activeMode)
                            },
                            leading = {
                                RadioButton(
                                    selected = activeMode == PetalAdBlockMode.AGGRESSIVE,
                                    onClick = {
                                        activeMode = PetalAdBlockMode.AGGRESSIVE
                                        PetalAdBlockEngine.setAdBlockMode(context, activeMode)
                                    }
                                )
                            },
                            content = {
                                Text(
                                    text = "Aggressive",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Strictly blocks all advertising scripts, first-party tracking, and telemetry pings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                // ── 4. Key Protection Toggles (Simple & Transparent) ──
                PetalSettingsSection(
                    title = "Protection Features",
                    icon = Icons.Rounded.CheckCircleOutline
                ) {
                    // Cookie & GDPR Popups
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.TOP,
                        title = "Auto-Dismiss Cookie Popups",
                        subtitle = "Automatically rejects non-essential tracking and closes GDPR consent modals",
                        icon = Icons.Rounded.Cookie,
                        checked = cookieDismissEnabled,
                        onCheckedChange = { on ->
                            cookieDismissEnabled = on
                            sp.edit()
                                .putBoolean("sp_cookie_banner_auto_reject", on)
                                .putBoolean("petal_builtin_consent_manager", on)
                                .apply()
                            try {
                                com.petal.browser.engine.gecko.PetalGeckoRuntime.syncPreferences(sp)
                            } catch (_: Throwable) {}
                        }
                    )

                    // Page Clutter & Annoyances
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.MIDDLE,
                        title = "Block Clutter & Annoyances",
                        subtitle = "Hides newsletter popups, floating chat widgets, and social media prompts",
                        icon = Icons.Rounded.NotificationsOff,
                        checked = blockAnnoyances,
                        onCheckedChange = { on ->
                            blockAnnoyances = on
                            sp.edit().putBoolean("sp_block_annoyances", on).apply()
                            PetalFilterSubscriptionManager.toggleSubscription(context, "brave_social", on)
                            PetalFilterSubscriptionManager.reloadActiveRules(context)
                        }
                    )

                    // Strip URL Tracking
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.BOTTOM,
                        title = "Strip Tracking Parameters",
                        subtitle = "Removes tracking query tokens (utm_*, fbclid, gclid) from opened URLs",
                        icon = Icons.Rounded.LinkOff,
                        checked = stripTrackingParams,
                        onCheckedChange = { on ->
                            stripTrackingParams = on
                            sp.edit().putBoolean("sp_strip_tracking_parameters", on).apply()
                        }
                    )
                }

                // ── 5. Site Exceptions (Whitelist) ──
                PetalSettingsSection(
                    title = "Exceptions",
                    icon = Icons.Rounded.Rule
                ) {
                    PetalGroupNavigationRow(
                        title = "Allowed Websites (${whitelistedDomains.size})",
                        subtitle = if (whitelistedDomains.isEmpty()) "No websites currently bypass shields" else "Websites permitted to run ads and trackers",
                        position = PetalGroupPosition.SINGLE,
                        onClick = { showWhitelistDialog = true },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) }
                    )
                }

                // ── 6. Advanced Tools & Filter Lists (Cleanly Collapsed) ──
                PetalSettingsSection(
                    title = "Filter Management",
                    icon = Icons.Rounded.Layers
                ) {
                    val runUpdate: () -> Unit = {
                        if (!isUpdatingLists) {
                            isUpdatingLists = true
                            updateStatusMessage = "Checking for list updates..."
                            coroutineScope.launch {
                                val count = PetalFilterSubscriptionManager.updateAllSubscriptions(
                                    context = context,
                                    force = true,
                                    onProgress = { done, total, name ->
                                        updateStatusMessage = "Updating ($done/$total): $name"
                                    }
                                )
                                subscriptions = PetalFilterSubscriptionManager.getSubscriptions(context)
                                isUpdatingLists = false
                                updateStatusMessage = if (count > 0) "Updated $count filter lists" else "All filter lists up to date"
                            }
                        }
                    }

                    // 1-Tap Rule Updater Row
                    PetalGroupListRow(
                        position = PetalGroupPosition.TOP,
                        onClick = runUpdate,
                        leading = { PetalGroupIconBadge(Icons.Rounded.Sync) },
                        content = {
                            Text(
                                text = "Update Built-in Rules",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            val lastUpdate = PetalFilterSubscriptionManager.getLastUpdatedVersion(context)
                            val dateStr = if (lastUpdate > 0) {
                                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastUpdate))
                            } else "Bundled"
                            Text(
                                text = updateStatusMessage ?: "EasyList, EasyPrivacy & uBlock · Last updated: $dateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailing = {
                            Button(
                                onClick = runUpdate,
                                enabled = !isUpdatingLists,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                if (isUpdatingLists) {
                                    com.petal.browser.compose.composable.PetalContainedLoadingIndicator(size = 18.dp)
                                } else {
                                    Text("Update")
                                }
                            }
                        }
                    )

                    // Custom User Rules Row
                    PetalGroupNavigationRow(
                        title = "Custom User Rules",
                        subtitle = "Add custom URL blocking or exception rules",
                        position = PetalGroupPosition.MIDDLE,
                        onClick = { showCustomRulesDialog = true },
                        leadingIcon = { Icon(Icons.Rounded.EditNote, contentDescription = null) }
                    )

                    // Expand / Collapse Full Filter Subscriptions
                    PetalGroupListRow(
                        position = PetalGroupPosition.BOTTOM,
                        onClick = { showAdvancedLists = !showAdvancedLists },
                        leading = {
                            PetalGroupIconBadge(if (showAdvancedLists) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore)
                        },
                        content = {
                            Text(
                                text = if (showAdvancedLists) "Hide Detailed Filter Lists" else "Show All Filter Lists (${subscriptions.size})",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }

                // Expanded Detailed Subscriptions (Only visible when user toggles it open)
                AnimatedVisibility(
                    visible = showAdvancedLists,
                    enter = fadeIn(animationSpec = PetalSpring.calm()) + expandVertically(animationSpec = PetalSpring.calm()),
                    exit = fadeOut(animationSpec = PetalSpring.calm()) + shrinkVertically(animationSpec = PetalSpring.calm())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subscriptions.forEachIndexed { index, sub ->
                            val toggle: (Boolean) -> Unit = { enabled ->
                                PetalFilterSubscriptionManager.toggleSubscription(context, sub.id, enabled)
                                subscriptions = PetalFilterSubscriptionManager.getSubscriptions(context)
                            }
                            PetalGroupListRow(
                                position = petalGroupPositionFor(index, subscriptions.size + 1),
                                onClick = { toggle(!sub.isEnabled) },
                                leading = {
                                    PetalGroupIconBadge(
                                        icon = Icons.Rounded.FilterList,
                                        variant = if (sub.isCustom) PetalBadgeVariant.TERTIARY else PetalBadgeVariant.PRIMARY
                                    )
                                },
                                content = {
                                    Text(
                                        text = sub.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = sub.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailing = {
                                    IconSwitch(
                                        checked = sub.isEnabled,
                                        icon = Icons.Rounded.Check,
                                        onCheckedChange = toggle
                                    )
                                }
                            )
                        }

                        // Add Custom Subscription URL Button
                        PetalGroupListRow(
                            position = PetalGroupPosition.SINGLE,
                            onClick = { showAddCustomListDialog = true },
                            leading = { PetalGroupIconBadge(Icons.Rounded.AddCircleOutline) },
                            content = {
                                Text(
                                    text = "Add Custom Filter Subscription URL",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}
