/*
 * PetalPrivacyShieldSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Privacy & Tracker Shield HUD bottom sheet for Petal Browser.
 * Hosted in PetalRootApp via PetalSheet (ModalBottomSheet with live blur + liquid glass).
 */

package com.petal.browser.ui.components

import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NoEncryption
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WifiProtectedSetup
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.privacy.PetalElementBlocker
import com.petal.browser.privacy.PetalElementZapper
import com.petal.browser.privacy.PetalHttpsOnlyWhitelist
import com.petal.browser.ui.containment.PetalAlertDialog
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalContainmentShapes
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.PetalSuggestionChip
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

data class ShieldSheetRequest(
    val pageUrl: String,
    val onToggleAdBlock: (Boolean) -> Unit = {}
)

object PetalPrivacyShieldSheet {

    private val _request = MutableStateFlow<ShieldSheetRequest?>(null)
    val request: StateFlow<ShieldSheetRequest?> = _request.asStateFlow()

    @Volatile
    private var lastShowAtMs: Long = 0L

    @JvmStatic
    fun show(activity: ComponentActivity, pageUrl: String, onToggleAdBlock: (Boolean) -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (_request.value != null || now - lastShowAtMs < 350L) return
        lastShowAtMs = now
        activity.runOnUiThread {
            if (_request.value != null) return@runOnUiThread
            _request.value = ShieldSheetRequest(
                pageUrl = pageUrl,
                onToggleAdBlock = onToggleAdBlock
            )
        }
    }

    @JvmStatic
    fun dismiss() {
        _request.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalPrivacyShieldBottomSheet(
    request: ShieldSheetRequest,
    activeTab: TabSessionState?,
    tabController: PetalTabViewController?,
    onToggleAdBlock: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    val pageUrl = activeTab?.content?.url?.takeIf { it.isNotBlank() } ?: request.pageUrl
    val host = remember(pageUrl) {
        try {
            Uri.parse(pageUrl).host ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    val cleanHost = remember(host) { host.removePrefix("www.") }
    val isHttps = pageUrl.startsWith("https://", ignoreCase = true)
    val blockedCount = PetalAdBlockEngine.getBlockedCountForDomain(cleanHost)
    val totalBlocked = PetalAdBlockEngine.getTotalBlockedCount()

    val dismissWithAnimation: () -> Unit = {
        scope.launch {
            try {
                if (sheetState.currentValue != SheetValue.Hidden) {
                    sheetState.hide()
                }
            } finally {
                onDismiss()
            }
        }
    }

    PetalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        PrivacyShieldContent(
            activity = activity,
            tabController = tabController,
            domain = cleanHost.ifEmpty { "Current Website" },
            pageUrl = pageUrl,
            isHttps = isHttps,
            blockedCount = blockedCount,
            totalBlocked = totalBlocked,
            onDismiss = dismissWithAnimation,
            onReloadRequested = {
                if (activity is MainActivity) {
                    activity.reloadCurrentBrowserTab()
                } else {
                    tabController?.reload()
                }
            },
            onToggleAdBlock = onToggleAdBlock
        )
    }
}

@Composable
private fun PrivacyShieldContent(
    activity: ComponentActivity?,
    tabController: PetalTabViewController?,
    domain: String,
    pageUrl: String,
    isHttps: Boolean,
    blockedCount: Int,
    totalBlocked: Long,
    onDismiss: () -> Unit,
    onReloadRequested: () -> Unit,
    onToggleAdBlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val cleanHost = remember(domain) { domain.removePrefix("www.") }

    var isWhitelisted by remember(cleanHost) {
        mutableStateOf(PetalAdBlockEngine.isDomainWhitelisted(cleanHost))
    }
    var globalAdBlock by remember {
        mutableStateOf(sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true)))
    }
    var blockCookies by remember {
        mutableStateOf(sp.getBoolean("sp_block_third_party_cookies", !sp.getBoolean("profileStandard_cookiesThirdParty", false)))
    }
    var fingerprintProtection by remember {
        mutableStateOf(sp.getBoolean("sp_fingerprint_protection", sp.getBoolean("profileStandard_fingerPrintProtection", false)))
    }
    var webrtcProtection by remember {
        mutableStateOf(sp.getBoolean("sp_webrtc_protection", sp.getBoolean("profileStandard_webrtcProtection", true)))
    }
    var dntGpc by remember {
        mutableStateOf(sp.getBoolean("sp_dnt_gpc", sp.getBoolean("profileStandard_dnt", true)))
    }
    var httpsOnly by remember {
        mutableStateOf(sp.getBoolean("sp_https_only", sp.getBoolean("profileStandard_httpsOnly", false)))
    }

    val installedExtensions by com.petal.browser.extensions.PetalExtensionManager.extensions.collectAsState()
    val pendingPrompt by com.petal.browser.extensions.PetalExtensionManager.pendingPrompt.collectAsState()
    val adguardExt = remember(installedExtensions) {
        installedExtensions.firstOrNull {
            it.id.equals("adguardadblocker@adguard.com", ignoreCase = true) ||
                it.id.equals("adguard-adblocker", ignoreCase = true) ||
                it.name.contains("AdGuard", ignoreCase = true)
        }
    }
    val isAdGuardInstalled = adguardExt != null
    var isInstallingAdGuard by remember { mutableStateOf(false) }

    pendingPrompt?.let { prompt ->
        com.petal.browser.compose.extensions.InstallPermissionDialog(prompt = prompt)
    }

    val categoryCounts = remember(cleanHost) {
        PetalAdBlockEngine.getCategoryCountsForDomain(cleanHost)
    }
    var isHttpsBypassed by remember(cleanHost) {
        mutableStateOf(PetalHttpsOnlyWhitelist.isBypassed(context, cleanHost))
    }
    var showBlockElementDialog by remember { mutableStateOf(false) }
    var customCssSelector by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                val shieldActive = globalAdBlock && !isWhitelisted
                PetalGroupIconBadge(
                    icon = if (shieldActive) Icons.Rounded.Shield else Icons.Rounded.Security,
                    variant = if (shieldActive) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL,
                    size = 44.dp,
                    iconSize = 24.dp
                )
                Column {
                    Text(
                        text = stringResource(R.string.ui_petal_shield_hud),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onReloadRequested) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.ui_reload),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Stat Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PetalHeroCard(
                    modifier = Modifier.weight(1f),
                    shape = PetalContainmentShapes.HeroInner,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "$blockedCount",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ui_blocked_on_this_site),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                PetalHeroCard(
                    modifier = Modifier.weight(1f),
                    shape = PetalContainmentShapes.HeroInner,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (totalBlocked > 0) "$totalBlocked" else "Active",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.ui_total_threats_stopped),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live ETP Category Breakdown Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val adsLabel = "Ads: ${categoryCounts.ads}"
                val trackersLabel = "Trackers: ${categoryCounts.analytics}"
                val socialLabel = "Social: ${categoryCounts.social}"
                val cookiesLabel = if (categoryCounts.cookies > 0) "Cookies: ${categoryCounts.cookies}" else "Cookies: Iso"

                PetalSuggestionChip(
                    onClick = {},
                    label = { Text(adsLabel, fontSize = 11.sp, maxLines = 1) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                PetalSuggestionChip(
                    onClick = {},
                    label = { Text(trackersLabel, fontSize = 11.sp, maxLines = 1) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                PetalSuggestionChip(
                    onClick = {},
                    label = { Text(socialLabel, fontSize = 11.sp, maxLines = 1) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                PetalSuggestionChip(
                    onClick = {},
                    label = { Text(cookiesLabel, fontSize = 11.sp, maxLines = 1) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            // Connection Security Banner
            PetalHeroCard(
                modifier = Modifier.fillMaxWidth(),
                shape = PetalContainmentShapes.HeroInner,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = if (isHttps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHttps) "Connection is secure (HTTPS)" else "Connection not secure (HTTP)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isHttps) "Information you share is private & encrypted" else "Beware of entering sensitive credentials or data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isHttps && httpsOnly) {
                PetalSettingsToggleRow(
                    title = "Allow HTTP for this domain",
                    subtitle = if (isHttpsBypassed) "HTTPS-Only upgrade bypassed for $cleanHost" else "GeckoView blocks unencrypted HTTP",
                    icon = Icons.Rounded.NoEncryption,
                    checked = isHttpsBypassed,
                    onCheckedChange = { checked ->
                        isHttpsBypassed = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        if (checked) {
                            PetalHttpsOnlyWhitelist.addBypass(context, cleanHost)
                        } else {
                            PetalHttpsOnlyWhitelist.removeBypass(context, cleanHost)
                        }
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.SINGLE
                )
            }

            // Site Protections Section
            PetalSettingsSection(
                title = "Site Filtering & Whitelist",
                icon = Icons.Rounded.Shield,
                cardId = "shield_site_filtering"
            ) {
                PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_trust_whitelist_domain),
                    subtitle = if (isWhitelisted) "AdBlocker disabled for $domain" else "Shields actively filtering $domain",
                    icon = Icons.Rounded.Verified,
                    checked = isWhitelisted,
                    onCheckedChange = { checked ->
                        isWhitelisted = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        if (checked) {
                            PetalAdBlockEngine.addDomainToWhitelist(context, cleanHost)
                        } else {
                            PetalAdBlockEngine.removeDomainFromWhitelist(context, cleanHost)
                        }
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.TOP
                )
                PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_master_ad_tracker_shield),
                    subtitle = if (globalAdBlock) "Global ad & telemetry blocker active" else "Global blocker paused",
                    icon = Icons.Rounded.Shield,
                    checked = globalAdBlock,
                    onCheckedChange = { checked ->
                        globalAdBlock = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        sp.edit()
                            .putBoolean("sp_ad_block", checked)
                            .putBoolean("profileStandard_adBlock", checked)
                            .apply()
                        PetalAdBlockEngine.setAdBlockEnabled(context, checked)
                        PetalGeckoRuntime.syncPreferences(sp)
                        onToggleAdBlock(checked)
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.MIDDLE
                )

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        if (activity is MainActivity) {
                            PetalElementZapper.launch(activity, activity.currentAlbumController)
                        } else if (tabController != null) {
                            PetalElementZapper.launch(tabController)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Zap Element (Interactive Visual Picker)", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = { showBlockElementDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Block Page Element (CSS Selector)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // AdGuard AdBlocker Recommended Extension Section
            PetalSettingsSection(
                title = "Firefox WebExtension Add-on",
                icon = Icons.Rounded.Extension,
                cardId = "shield_adguard_adblocker"
            ) {
                if (isAdGuardInstalled && adguardExt != null) {
                    PetalGroupRow(
                        icon = Icons.Rounded.Verified,
                        title = "AdGuard AdBlocker Active",
                        position = PetalGroupPosition.SINGLE,
                        onClick = {
                            onDismiss()
                            activity?.let { act ->
                                com.petal.browser.extensions.PetalExtensionManager.triggerBrowserAction(adguardExt.id, act)
                            }
                        },
                        subtitle = "Comprehensive cosmetic filtering & tracker defense running alongside Petal shields"
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "AdGuard AdBlocker (Recommended)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Enhance Petal's built-in Trie blocker with AdGuard's premier content blocker for cosmetic filtering, tracker removal, and anti-adblock defense.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = {
                                isInstallingAdGuard = true
                                val adguardEntry = com.petal.browser.extensions.PetalExtensionManager.catalog.firstOrNull { it.id == "adguard-adblocker" }
                                val url = adguardEntry?.downloadUrl ?: "https://addons.mozilla.org/firefox/downloads/latest/adguard-adblocker/latest.xpi"
                                com.petal.browser.extensions.PetalExtensionManager.install(url, autoConfirm = true) { success, msg ->
                                    isInstallingAdGuard = false
                                    Toast.makeText(context, msg ?: if (success) "AdGuard AdBlocker connected" else "Install failed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isInstallingAdGuard,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isInstallingAdGuard) "Connecting AdGuard AdBlocker..." else "1-Tap Install AdGuard AdBlocker")
                        }
                    }
                }
            }

            // Advanced Privacy Shields Section
            PetalSettingsSection(
                title = stringResource(R.string.ui_shield_anti_tracking),
                icon = Icons.Rounded.Security,
                cardId = "shield_advanced_privacy"
            ) {
                PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_block_third_party_tracking_cookies),
                    subtitle = stringResource(R.string.ui_isolate_and_block_cross_site),
                    icon = Icons.Rounded.Cookie,
                    checked = blockCookies,
                    onCheckedChange = { checked ->
                        blockCookies = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        sp.edit()
                            .putBoolean("sp_block_third_party_cookies", checked)
                            .putBoolean("profileStandard_cookiesThirdParty", !checked)
                            .apply()
                        PetalGeckoRuntime.syncPreferences(sp)
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.TOP
                )
                PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_canvas_audio_font_fingerprint_shield),
                    subtitle = stringResource(R.string.ui_randomize_canvas_webgl_audiocontext_an),
                    icon = Icons.Rounded.Fingerprint,
                    checked = fingerprintProtection,
                    onCheckedChange = { checked ->
                        fingerprintProtection = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        sp.edit()
                            .putBoolean("sp_fingerprint_protection", checked)
                            .putBoolean("profileStandard_fingerPrintProtection", checked)
                            .apply()
                        PetalGeckoRuntime.syncPreferences(sp)
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.MIDDLE
                )
                PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_webrtc_ip_leak_shield),
                    subtitle = stringResource(R.string.ui_prevent_local_public_ip_address),
                    icon = Icons.Rounded.WifiProtectedSetup,
                    checked = webrtcProtection,
                    onCheckedChange = { checked ->
                        webrtcProtection = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        sp.edit()
                            .putBoolean("sp_webrtc_protection", checked)
                            .putBoolean("profileStandard_webrtcProtection", checked)
                            .apply()
                        PetalGeckoRuntime.syncPreferences(sp)
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.MIDDLE
                )
                PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_do_not_track_global_privacy),
                    subtitle = stringResource(R.string.ui_broadcast_dnt_1_and_sec),
                    icon = Icons.Rounded.Security,
                    checked = dntGpc,
                    onCheckedChange = { checked ->
                        dntGpc = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        sp.edit()
                            .putBoolean("sp_dnt_gpc", checked)
                            .putBoolean("profileStandard_dnt", checked)
                            .apply()
                        PetalGeckoRuntime.syncPreferences(sp)
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.MIDDLE
                )
                PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_https_only_mode),
                    subtitle = stringResource(R.string.ui_automatically_upgrade_connections_to_h),
                    icon = Icons.Rounded.Lock,
                    checked = httpsOnly,
                    onCheckedChange = { checked ->
                        httpsOnly = checked
                        PetalHapticEngine.getInstance(context).selectionChanged(context)
                        sp.edit()
                            .putBoolean("sp_https_only", checked)
                            .putBoolean("profileStandard_httpsOnly", checked)
                            .apply()
                        PetalGeckoRuntime.syncPreferences(sp)
                        onReloadRequested()
                    },
                    position = PetalGroupPosition.BOTTOM
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (showBlockElementDialog) {
            PetalAlertDialog(
                onDismissRequest = { showBlockElementDialog = false },
                title = "Block Element on $cleanHost",
                message = "Enter a CSS selector (e.g. '.newsletter-modal', '#paywall-banner') to permanently hide it from this domain.",
                confirmText = "Block",
                onConfirm = {
                    if (customCssSelector.isNotBlank()) {
                        PetalElementBlocker.addSelector(context, cleanHost, customCssSelector.trim())
                        onReloadRequested()
                        showBlockElementDialog = false
                        customCssSelector = ""
                    }
                },
                dismissText = "Cancel",
                onDismiss = { showBlockElementDialog = false },
                confirmEnabled = customCssSelector.isNotBlank(),
                content = {
                    TextField(
                        value = customCssSelector,
                        onValueChange = { customCssSelector = it },
                        placeholder = { Text("e.g. .popup-overlay") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
    }
}
