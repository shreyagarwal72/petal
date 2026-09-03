package com.petal.browser.compose.settings.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.compose.settings.viewmodel.PrivacySettingsViewModel
import com.petal.browser.flags.PetalChromeFlagsBridge
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    val privateDnsMode by viewModel.privateDnsMode.collectAsStateWithLifecycle()

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
        privateDnsMode = privateDnsMode,
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
        onPrivateDnsModeChange = viewModel::setPrivateDnsMode,
        onNavigateBack = onNavigateBack,
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
    privateDnsMode: String,
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
    onPrivateDnsModeChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var whitelistDomainInput by remember { mutableStateOf("") }
    var whitelistedDomainsState by remember { mutableStateOf(PetalAdBlockEngine.getWhitelistedDomains()) }

    if (showWhitelistDialog) {
        AlertDialog(
            onDismissRequest = { showWhitelistDialog = false },
            title = { Text("AdBlock Domain Whitelist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Domains added here will bypass ad and tracker filtering:",
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
                            Text("Add")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (whitelistedDomainsState.isEmpty()) {
                        Text(
                            "No whitelisted domains.",
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
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                    }
                                )
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
                // Privacy & Shield Protection Card
                SettingsCategoryCard(title = "Privacy & Shield Protection", iconRes = com.petal.browser.R.drawable.layers_filled) {
                    ToggleRow(
                        title = "Ad & Tracker Shield",
                        subtitle = "uBlock Origin & AdGuard-grade Trie filter engine & scriptlets",
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
                                text = "Whitelisted Domains (${whitelistedDomainsState.size})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { showWhitelistDialog = true }) {
                                Text("Manage Whitelist")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Block Third-Party Tracking Cookies",
                        subtitle = "Isolate and block cross-site cookies used for ad tracking",
                        icon = Icons.Rounded.Cookie,
                        checked = blockThirdPartyCookies,
                        onCheckedChange = onBlockThirdPartyCookiesChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Canvas, Audio & Font Fingerprint Shield",
                        subtitle = "Randomize canvas, WebGL, AudioContext, and font geometry to defeat browser fingerprinting",
                        icon = Icons.Rounded.Fingerprint,
                        checked = fingerprintProtection,
                        onCheckedChange = onFingerprintProtectionChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "WebRTC IP Leak Shield",
                        subtitle = "Prevent local & public IP address leaks via WebRTC STUN/TURN queries",
                        icon = Icons.Rounded.WifiProtectedSetup,
                        checked = webrtcProtection,
                        onCheckedChange = onWebrtcProtectionChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Do Not Track & Global Privacy Control (GPC)",
                        subtitle = "Broadcast DNT: 1 and Sec-GPC: 1 signals requesting websites not to sell or share your data",
                        icon = Icons.Rounded.Security,
                        checked = dntGpc,
                        onCheckedChange = onDntGpcChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Strict Referrer Trimming",
                        subtitle = "Strip cross-origin URL paths from referrer headers to protect browsing privacy",
                        icon = Icons.Rounded.LinkOff,
                        checked = trimReferrers,
                        onCheckedChange = onTrimReferrersChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "WebAuthn & Passkey Support",
                        subtitle = "Allow websites to authenticate passwordless sign-ins using biometric passkeys, hardware tokens & Google Password Manager",
                        icon = Icons.Rounded.Key,
                        checked = webauthnEnabled,
                        onCheckedChange = onWebauthnEnabledChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Block Popup Windows",
                        subtitle = "Prevent unwanted popups and redirect windows",
                        icon = Icons.Rounded.OpenInNew,
                        checked = blockPopups,
                        onCheckedChange = onBlockPopupsChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "HTTPS Security Enforcer",
                        subtitle = "Automatically upgrade connections to HTTPS",
                        icon = Icons.Rounded.Lock,
                        checked = httpsOnly,
                        onCheckedChange = onHttpsOnlyChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Enable JavaScript",
                        subtitle = "Required for modern web features",
                        icon = Icons.Rounded.Code,
                        checked = javaScriptEnabled,
                        onCheckedChange = onJavaScriptEnabledChange
                    )
                }

                // Private DNS Protection Card
                SettingsCategoryCard(title = "Private DNS Protection", iconRes = com.petal.browser.R.drawable.database_filled) {
                    Text(
                        "Encrypt DNS queries to prevent tracking & block malicious content:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val dnsOptions = listOf(
                        Triple("OFF", "System Default (Off)", "Use default network DNS"),
                        Triple("CLOUDFLARE", "Cloudflare (1.1.1.1)", "Fast & private 1.1.1.1 DNS over HTTPS"),
                        Triple("GOOGLE", "Google Public DNS", "8.8.8.8 high performance resolution"),
                        Triple("CLEANBROWSING", "CleanBrowsing Family Filter", "Blocks adult & malicious sites"),
                        Triple("OPENDNS", "OpenDNS Home", "Cisco OpenDNS security protection")
                    )

                    dnsOptions.forEach { (mode, name, desc) ->
                        Surface(
                            onClick = { onPrivateDnsModeChange(mode) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (privateDnsMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = if (privateDnsMode == mode) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
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
                                        color = if (privateDnsMode == mode) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (privateDnsMode == mode) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
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
                        Text("Configure Android System Private DNS")
                    }
                }

                // Chrome & Petal Flags Card
                SettingsCategoryCard(title = "Experimental Petal & Chrome Flags", iconRes = com.petal.browser.R.drawable.build_filled) {
                    Surface(
                        onClick = {
                            if (context is ComponentActivity) {
                                PetalChromeFlagsBridge.showFlags(context, null)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Petal & Chrome Experimental Flags (petal://flags)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "Enable or disable WebGPU, hardware acceleration, force dark mode, HTTP/3 QUIC, and experimental Web APIs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
