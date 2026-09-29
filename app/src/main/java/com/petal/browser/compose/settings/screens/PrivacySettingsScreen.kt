package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.containment.PetalSettingsSection

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.compose.settings.viewmodel.PrivacySettingsViewModel
import androidx.activity.ComponentActivity
import com.petal.browser.passwords.PetalPasswordsScreen
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: PrivacySettingsViewModel = hiltViewModel()
) {
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsStateWithLifecycle()
    val fingerprintProtection by viewModel.fingerprintProtection.collectAsStateWithLifecycle()
    val webrtcProtection by viewModel.webrtcProtection.collectAsStateWithLifecycle()
    val dntGpc by viewModel.dntGpc.collectAsStateWithLifecycle()
    val trimReferrers by viewModel.trimReferrers.collectAsStateWithLifecycle()
    val webauthnEnabled by viewModel.webauthnEnabled.collectAsStateWithLifecycle()
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle()
    val javaScriptEnabled by viewModel.javaScriptEnabled.collectAsStateWithLifecycle()
    val blockPopups by viewModel.blockPopups.collectAsStateWithLifecycle()
    val openRedirectsInBackground by viewModel.openRedirectsInBackground.collectAsStateWithLifecycle()
    val privateDnsMode by viewModel.privateDnsMode.collectAsStateWithLifecycle()
    val customDohUrl by viewModel.customDohUrl.collectAsStateWithLifecycle()

    PrivacySettingsScreenContent(
        adBlockEnabled = adBlockEnabled,
        blockThirdPartyCookies = blockThirdPartyCookies,
        fingerprintProtection = fingerprintProtection,
        webrtcProtection = webrtcProtection,
        dntGpc = dntGpc,
        trimReferrers = trimReferrers,
        webauthnEnabled = webauthnEnabled,
        httpsOnly = httpsOnly,
        javaScriptEnabled = javaScriptEnabled,
        blockPopups = blockPopups,
        openRedirectsInBackground = openRedirectsInBackground,
        privateDnsMode = privateDnsMode,
        customDohUrl = customDohUrl,
        onAdBlockEnabledChange = viewModel::setAdBlockEnabled,
        onBlockThirdPartyCookiesChange = viewModel::setBlockThirdPartyCookies,
        onFingerprintProtectionChange = viewModel::setFingerprintProtection,
        onWebrtcProtectionChange = viewModel::setWebrtcProtection,
        onDntGpcChange = viewModel::setDntGpc,
        onTrimReferrersChange = viewModel::setTrimReferrers,
        onWebauthnEnabledChange = viewModel::setWebauthnEnabled,
        onHttpsOnlyChange = viewModel::setHttpsOnly,
        onJavaScriptEnabledChange = viewModel::setJavaScriptEnabled,
        onBlockPopupsChange = viewModel::setBlockPopups,
        onOpenRedirectsInBackgroundChange = viewModel::setOpenRedirectsInBackground,
        onPrivateDnsModeChange = viewModel::setPrivateDnsMode,
        onCustomDohUrlChange = viewModel::setCustomDohUrl,
        onNavigateBack = onNavigateBack,
        targetHighlightItemId = targetHighlightItemId,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacySettingsScreenContent(
    adBlockEnabled: Boolean,
    blockThirdPartyCookies: Boolean,
    fingerprintProtection: Boolean,
    webrtcProtection: Boolean,
    dntGpc: Boolean,
    trimReferrers: Boolean,
    webauthnEnabled: Boolean,
    httpsOnly: Boolean,
    javaScriptEnabled: Boolean,
    blockPopups: Boolean,
    openRedirectsInBackground: Boolean,
    privateDnsMode: String,
    customDohUrl: String,
    onAdBlockEnabledChange: (Boolean) -> Unit,
    onBlockThirdPartyCookiesChange: (Boolean) -> Unit,
    onFingerprintProtectionChange: (Boolean) -> Unit,
    onWebrtcProtectionChange: (Boolean) -> Unit,
    onDntGpcChange: (Boolean) -> Unit,
    onTrimReferrersChange: (Boolean) -> Unit,
    onWebauthnEnabledChange: (Boolean) -> Unit,
    onHttpsOnlyChange: (Boolean) -> Unit,
    onJavaScriptEnabledChange: (Boolean) -> Unit,
    onBlockPopupsChange: (Boolean) -> Unit,
    onOpenRedirectsInBackgroundChange: (Boolean) -> Unit,
    onPrivateDnsModeChange: (String) -> Unit,
    onCustomDohUrlChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPasswordsScreen by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var whitelistDomainInput by remember { mutableStateOf("") }
    var whitelistedDomainsState by remember { mutableStateOf(PetalAdBlockEngine.getWhitelistedDomains()) }

    if (showWhitelistDialog) {
        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showWhitelistDialog = false },
            title = { Text(stringResource(R.string.ui_adblock_domain_whitelist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.ui_domains_added_here_will_bypass),
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
                                    whitelistedDomainsState = PetalAdBlockEngine.getWhitelistedDomains()
                                    whitelistDomainInput = ""
                                }
                            }
                        ) {
                            Text(stringResource(R.string.ui_add))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (whitelistedDomainsState.isEmpty()) {
                        Text(
                            stringResource(R.string.ui_no_whitelisted_domains),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            whitelistedDomainsState.forEach { domain ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        PetalAdBlockEngine.removeDomainFromWhitelist(context, domain)
                                        whitelistedDomainsState = PetalAdBlockEngine.getWhitelistedDomains()
                                    },
                                    label = { Text(domain) },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.ui_remove), modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWhitelistDialog = false }) {
                    Text(stringResource(R.string.ui_done))
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "privacy_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Privacy & Security",
                subtitle = "AdBlock, HTTPS-only, Private DNS & cookies",
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
                // ── Hero Privacy Shield Status Banner ──
                com.petal.browser.ui.containment.PetalStatusHeroCard(
                    title = if (adBlockEnabled) "Shield Active & Protecting" else "Shield Paused",
                    subtitle = if (adBlockEnabled) "Real-time Trie tracker blocking, HTTPS-only & anti-fingerprinting active" else "Trackers and ads are not currently being filtered",
                    statusText = if (adBlockEnabled) "Protection Enabled" else "Protection Disabled",
                    icon = Icons.Rounded.Security,
                    statusActive = adBlockEnabled,
                    actionLabel = if (adBlockEnabled) "Whitelist" else "Enable Shield",
                    onActionClick = {
                        if (adBlockEnabled) {
                            showWhitelistDialog = true
                        } else {
                            onAdBlockEnabledChange(true)
                            PetalAdBlockEngine.setAdBlockEnabled(context, true)
                        }
                    }
                )

                // ── Section 1: Shield & Anti-Tracking Protection ──
                PetalSettingsSection(
                    title = stringResource(R.string.ui_shield_anti_tracking),
                    iconRes = com.petal.browser.R.drawable.layers_filled,
                    cardId = "privacy_adblock",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_ad_tracker_shield),
                        subtitle = stringResource(R.string.ui_ublock_origin_adguard_grade_trie),
                        icon = Icons.Rounded.Shield,
                        checked = adBlockEnabled,
                        onCheckedChange = { newValue ->
                            onAdBlockEnabledChange(newValue)
                            PetalAdBlockEngine.setAdBlockEnabled(context, newValue)
                        }
                    )

                    if (adBlockEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.ui_whitelisted_domains, whitelistedDomainsState.size),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { showWhitelistDialog = true }) {
                                Text(stringResource(R.string.ui_manage_whitelist))
                            }
                        }
                    }
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_block_third_party_tracking_cookies),
                        subtitle = stringResource(R.string.ui_isolate_and_block_cross_site),
                        icon = Icons.Rounded.Cookie,
                        checked = blockThirdPartyCookies,
                        onCheckedChange = onBlockThirdPartyCookiesChange
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_canvas_audio_font_fingerprint_shield),
                        subtitle = stringResource(R.string.ui_randomize_canvas_webgl_audiocontext_an),
                        icon = Icons.Rounded.Fingerprint,
                        checked = fingerprintProtection,
                        onCheckedChange = onFingerprintProtectionChange
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_webrtc_ip_leak_shield),
                        subtitle = stringResource(R.string.ui_prevent_local_public_ip_address),
                        icon = Icons.Rounded.WifiProtectedSetup,
                        checked = webrtcProtection,
                        onCheckedChange = onWebrtcProtectionChange
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_do_not_track_global_privacy),
                        subtitle = stringResource(R.string.ui_broadcast_dnt_1_and_sec),
                        icon = Icons.Rounded.Security,
                        checked = dntGpc,
                        onCheckedChange = onDntGpcChange
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_strict_referrer_trimming),
                        subtitle = stringResource(R.string.ui_strip_cross_origin_url_paths),
                        icon = Icons.Rounded.LinkOff,
                        checked = trimReferrers,
                        onCheckedChange = onTrimReferrersChange
                    )
                }

                // ── Section 2: Security & Authentication ──
                PetalSettingsSection(
                    title = stringResource(R.string.ui_security_passkeys),
                    icon = Icons.Rounded.Lock,
                    cardId = "privacy_security",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalGroupNavigationRow(
                        title = stringResource(R.string.ui_password_manager_autofill),
                        subtitle = stringResource(R.string.ui_encrypted_local_vault_breach_checks),
                        position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                        onClick = { showPasswordsScreen = true },
                        leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) },
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_https_security_enforcer),
                        subtitle = stringResource(R.string.ui_automatically_upgrade_connections_to_h),
                        icon = Icons.Rounded.Lock,
                        checked = httpsOnly,
                        onCheckedChange = onHttpsOnlyChange
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_webauthn_passkey_support),
                        subtitle = stringResource(R.string.ui_allow_websites_to_authenticate_passwor),
                        icon = Icons.Rounded.Key,
                        checked = webauthnEnabled,
                        onCheckedChange = onWebauthnEnabledChange
                    )
                }

                // ── Section 3: Web Content & Navigation ──
                PetalSettingsSection(
                    title = stringResource(R.string.ui_web_content_navigation),
                    icon = Icons.Rounded.Code,
                    cardId = "privacy_cookies",
                    targetHighlightId = targetHighlightItemId
                ) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_enable_javascript),
                        subtitle = stringResource(R.string.ui_required_for_modern_web_features),
                        icon = Icons.Rounded.Code,
                        checked = javaScriptEnabled,
                        onCheckedChange = onJavaScriptEnabledChange
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_block_popup_windows),
                        subtitle = stringResource(R.string.ui_prevent_unwanted_popups_and_redirect),
                        icon = Icons.Rounded.OpenInNew,
                        checked = blockPopups,
                        onCheckedChange = onBlockPopupsChange
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_open_redirect_links_in_background),
                        subtitle = stringResource(R.string.ui_detect_external_redirect_links_and),
                        icon = Icons.Rounded.TabUnselected,
                        checked = openRedirectsInBackground,
                        onCheckedChange = onOpenRedirectsInBackgroundChange
                    )
                }

                // Private DNS Protection Card
                PetalSettingsSection(
                    title = stringResource(R.string.ui_private_dns_protection),
                    iconRes = com.petal.browser.R.drawable.database_filled,
                    cardId = "privacy_private_dns",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        stringResource(R.string.ui_encrypt_dns_queries_to_prevent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val dnsOptions = listOf(
                        Triple("OFF", "System Default (Off)", "Use default network DNS"),
                        Triple("CLOUDFLARE", "Cloudflare (1.1.1.1)", "Fast & private 1.1.1.1 DNS over HTTPS"),
                        Triple("GOOGLE", "Google Public DNS", "8.8.8.8 high performance resolution"),
                        Triple("CLEANBROWSING", "CleanBrowsing Family Filter", "Blocks adult & malicious sites"),
                        Triple("OPENDNS", "OpenDNS Home", "Cisco OpenDNS security protection"),
                        Triple("CUSTOM", "Custom DNS-over-HTTPS (DoH)", "Enter your preferred DoH resolver endpoint URL")
                    )

                    dnsOptions.forEach { (mode, name, desc) ->
                        Surface(
                            onClick = { onPrivateDnsModeChange(mode) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (privateDnsMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (privateDnsMode == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (privateDnsMode == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (privateDnsMode == mode) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    if (privateDnsMode == "CUSTOM") {
                        OutlinedTextField(
                            value = customDohUrl,
                            onValueChange = onCustomDohUrlChange,
                            label = { Text(stringResource(R.string.ui_custom_doh_endpoint_url)) },
                            placeholder = { Text("https://dns.adguard-dns.com/dns-query") },
                            leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val intents = listOf(
                                Intent("android.settings.PRIVATE_DNS_SETTINGS"),
                                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                                Intent(Settings.ACTION_SETTINGS)
                            )
                            for (intent in intents) {
                                try {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    break
                                } catch (e: Exception) {
                                    // continue to next fallback
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_configure_android_system_private_dns))
                    }
                }
            }
        }

        val passwordsActivity = context as? ComponentActivity
        if (passwordsActivity != null) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showPasswordsScreen,
                enter = com.petal.browser.ui.containment.PetalMotion.forwardEnter(),
                exit = com.petal.browser.ui.containment.PetalMotion.backExit(),
                modifier = Modifier.fillMaxSize()
            ) {
                PetalPasswordsScreen(
                    activity = passwordsActivity,
                    onNavigateBack = { showPasswordsScreen = false }
                )
            }
        }
    }
}

