package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.theme.PetalText

import com.petal.browser.ui.components.PetalSpring

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.compose.settings.viewmodel.PrivacySettingsViewModel
import com.petal.browser.passwords.PetalPasswordsScreen
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper
import java.text.NumberFormat

/**
 * Unified Privacy & Security Screen (Stage 5 Consolidation)
 * Single coherent page in Petal containment style organized into 7 core pillars:
 * 1. Ad & Tracker Blocking (live counter, master switch, filter lists, site exceptions whitelist)
 * 2. Tracking Protection (Standard/Strict segmented control, Total Cookie Protection, Unlinkable Session Partitioning)
 * 3. Fingerprinting Protection (Gecko RFP / FPP primary defense, Canvas/Audio randomization)
 * 4. Connection Security (HTTPS-Only, Private DNS / DoH, WebRTC leak shield, Privacy signals GPC+DNT, Strict referrer trimming)
 * 5. Site Permissions & Content (Popups, GDPR cookie banners, JavaScript)
 * 6. Private Browsing (Biometric lock, Clear on exit)
 * 7. Passwords & Passkeys (Unified Password manager & autofill vault)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    onNavigateToAppLock: () -> Unit = {},
    viewModel: PrivacySettingsViewModel = hiltViewModel()
) {
    val privacyProfile by viewModel.privacyProfile.collectAsStateWithLifecycle()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsStateWithLifecycle()
    val totalCookieProtection by viewModel.totalCookieProtection.collectAsStateWithLifecycle()
    val cookieBannerAutoReject by viewModel.cookieBannerAutoReject.collectAsStateWithLifecycle()
    val etpStrict by viewModel.etpStrict.collectAsStateWithLifecycle()
    val biometricTabLock by viewModel.biometricTabLock.collectAsStateWithLifecycle()
    val sessionPartitioning by viewModel.sessionPartitioning.collectAsStateWithLifecycle()
    val fingerprintProtection by viewModel.fingerprintProtection.collectAsStateWithLifecycle()
    val webrtcProtection by viewModel.webrtcProtection.collectAsStateWithLifecycle()
    val dntGpc by viewModel.dntGpc.collectAsStateWithLifecycle()
    val trimReferrers by viewModel.trimReferrers.collectAsStateWithLifecycle()
    val httpsOnly by viewModel.httpsOnly.collectAsStateWithLifecycle()
    val javaScriptEnabled by viewModel.javaScriptEnabled.collectAsStateWithLifecycle()
    val blockPopups by viewModel.blockPopups.collectAsStateWithLifecycle()
    val privateDnsMode by viewModel.privateDnsMode.collectAsStateWithLifecycle()
    val customDohUrl by viewModel.customDohUrl.collectAsStateWithLifecycle()
    val nextDnsProfileId by viewModel.nextDnsProfileId.collectAsStateWithLifecycle()
    val bounceTracking by viewModel.bounceTracking.collectAsStateWithLifecycle()
    val canvasLetterboxing by viewModel.canvasLetterboxing.collectAsStateWithLifecycle()
    val drmEnabled by viewModel.drmEnabled.collectAsStateWithLifecycle()
    val loginAutofillEnabled by viewModel.loginAutofillEnabled.collectAsStateWithLifecycle()
    val fetchShortcutFavicons by viewModel.fetchShortcutFavicons.collectAsStateWithLifecycle()

    PrivacySettingsScreenContent(
        privacyProfile = privacyProfile,
        adBlockEnabled = adBlockEnabled,
        blockThirdPartyCookies = blockThirdPartyCookies,
        totalCookieProtection = totalCookieProtection,
        cookieBannerAutoReject = cookieBannerAutoReject,
        etpStrict = etpStrict,
        biometricTabLock = biometricTabLock,
        sessionPartitioning = sessionPartitioning,
        fingerprintProtection = fingerprintProtection,
        webrtcProtection = webrtcProtection,
        dntGpc = dntGpc,
        trimReferrers = trimReferrers,
        httpsOnly = httpsOnly,
        javaScriptEnabled = javaScriptEnabled,
        blockPopups = blockPopups,
        privateDnsMode = privateDnsMode,
        customDohUrl = customDohUrl,
        nextDnsProfileId = nextDnsProfileId,
        bounceTracking = bounceTracking,
        canvasLetterboxing = canvasLetterboxing,
        drmEnabled = drmEnabled,
        loginAutofillEnabled = loginAutofillEnabled,
        fetchShortcutFavicons = fetchShortcutFavicons,
        onPrivacyProfileChange = viewModel::setPrivacyProfile,
        onAdBlockEnabledChange = viewModel::setAdBlockEnabled,
        onBlockThirdPartyCookiesChange = viewModel::setBlockThirdPartyCookies,
        onTotalCookieProtectionChange = viewModel::setTotalCookieProtection,
        onCrossSiteTrackingChange = viewModel::setCrossSiteTracking,
        onCookieBannerAutoRejectChange = viewModel::setCookieBannerAutoReject,
        onEtpStrictChange = viewModel::setEtpStrict,
        onBiometricTabLockChange = viewModel::setBiometricTabLock,
        onSessionPartitioningChange = viewModel::setSessionPartitioning,
        onFingerprintProtectionChange = viewModel::setFingerprintProtection,
        onWebrtcProtectionChange = viewModel::setWebrtcProtection,
        onDntGpcChange = viewModel::setDntGpc,
        onTrimReferrersChange = viewModel::setTrimReferrers,
        onHttpsOnlyChange = viewModel::setHttpsOnly,
        onJavaScriptEnabledChange = viewModel::setJavaScriptEnabled,
        onBlockPopupsChange = viewModel::setBlockPopups,
        onPrivateDnsModeChange = viewModel::setPrivateDnsMode,
        onCustomDohUrlChange = viewModel::setCustomDohUrl,
        onNextDnsProfileIdChange = viewModel::setNextDnsProfileId,
        onBounceTrackingChange = viewModel::setBounceTracking,
        onCanvasLetterboxingChange = viewModel::setCanvasLetterboxing,
        onDrmEnabledChange = viewModel::setDrmEnabled,
        onLoginAutofillEnabledChange = viewModel::setLoginAutofillEnabled,
        onFetchShortcutFaviconsChange = viewModel::setFetchShortcutFavicons,
        onResetPrivacyToRecommended = viewModel::resetPrivacyToRecommended,
        onNavigateBack = onNavigateBack,
        onNavigateToAppLock = onNavigateToAppLock,
        targetHighlightItemId = targetHighlightItemId,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacySettingsScreenContent(
    privacyProfile: String = "HARDENED",
    adBlockEnabled: Boolean,
    blockThirdPartyCookies: Boolean,
    totalCookieProtection: Boolean = true,
    cookieBannerAutoReject: Boolean = true,
    etpStrict: Boolean = false,
    biometricTabLock: Boolean = false,
    sessionPartitioning: Boolean = true,
    fingerprintProtection: Boolean,
    webrtcProtection: Boolean,
    dntGpc: Boolean,
    trimReferrers: Boolean,
    httpsOnly: Boolean,
    javaScriptEnabled: Boolean,
    blockPopups: Boolean,
    privateDnsMode: String,
    customDohUrl: String,
    nextDnsProfileId: String = "",
    bounceTracking: Boolean = false,
    canvasLetterboxing: Boolean = false,
    drmEnabled: Boolean = true,
    loginAutofillEnabled: Boolean = true,
    fetchShortcutFavicons: Boolean = true,
    onPrivacyProfileChange: (String) -> Unit = {},
    onAdBlockEnabledChange: (Boolean) -> Unit,
    onBlockThirdPartyCookiesChange: (Boolean) -> Unit,
    onTotalCookieProtectionChange: (Boolean) -> Unit = {},
    onCrossSiteTrackingChange: (Boolean) -> Unit = {},
    onCookieBannerAutoRejectChange: (Boolean) -> Unit = {},
    onEtpStrictChange: (Boolean) -> Unit = {},
    onBiometricTabLockChange: (Boolean) -> Unit = {},
    onSessionPartitioningChange: (Boolean) -> Unit = {},
    onFingerprintProtectionChange: (Boolean) -> Unit,
    onWebrtcProtectionChange: (Boolean) -> Unit,
    onDntGpcChange: (Boolean) -> Unit,
    onTrimReferrersChange: (Boolean) -> Unit,
    onHttpsOnlyChange: (Boolean) -> Unit,
    onJavaScriptEnabledChange: (Boolean) -> Unit,
    onBlockPopupsChange: (Boolean) -> Unit,
    onPrivateDnsModeChange: (String) -> Unit,
    onCustomDohUrlChange: (String) -> Unit,
    onNextDnsProfileIdChange: (String) -> Unit = {},
    onBounceTrackingChange: (Boolean) -> Unit = {},
    onCanvasLetterboxingChange: (Boolean) -> Unit = {},
    onDrmEnabledChange: (Boolean) -> Unit = {},
    onLoginAutofillEnabledChange: (Boolean) -> Unit = {},
    onFetchShortcutFaviconsChange: (Boolean) -> Unit = {},
    onResetPrivacyToRecommended: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToAppLock: () -> Unit = {},
    targetHighlightItemId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val ambientWallpaper = LocalPetalGlassWallpaper.current
    val wallpaper = ambientWallpaper ?: rememberPetalGlassWallpaper()
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    var showPasswordsScreen by remember { mutableStateOf(false) }
    var showAdBlockSettings by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showDeleteScreenDialog by remember { mutableStateOf(false) }
    var showResetPrivacyDialog by remember { mutableStateOf(false) }
    var whitelistDomainInput by remember { mutableStateOf("") }
    var whitelistedDomainsState by remember { mutableStateOf(PetalAdBlockEngine.getWhitelistedDomains()) }

    val advancedHighlightIds = remember {
        listOf(
            "privacy_advanced",
            "privacy_etp",
            "privacy_cross_site",
            "privacy_cookies",
            "privacy_session_partitioning",
            "privacy_bounce_tracking",
            "privacy_fingerprinting",
            "privacy_letterboxing",
            "privacy_webrtc",
            "privacy_dnt_gpc",
            "privacy_trim_referrers",
            "privacy_drm",
            "privacy_autofill",
            "privacy_shortcut_favicons",
            "privacy_private_dns",
            "privacy_javascript",
        )
    }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(targetHighlightItemId) {
        val target = targetHighlightItemId?.trim()?.lowercase().orEmpty()
        if (target.isNotEmpty() && advancedHighlightIds.any { id ->
                id == target || target.contains(id) || id.contains(target)
            }
        ) {
            advancedExpanded = true
        }
    }

    var clearOnExit by remember {
        mutableStateOf(sp.getBoolean("sp_clear_quit", false) || sp.getBoolean("sp_clear_on_exit", false))
    }

    if (showResetPrivacyDialog) {
        PetalAlertDialog(
            onDismissRequest = { showResetPrivacyDialog = false },
            title = "Reset privacy to recommended",
            confirmText = "Reset",
            onConfirm = {
                showResetPrivacyDialog = false
                onResetPrivacyToRecommended()
                clearOnExit = false
                PetalAdBlockEngine.setAdBlockEnabled(context, true)
            },
            dismissText = stringResource(R.string.ui_cancel),
            onDismiss = { showResetPrivacyDialog = false },
            icon = Icons.Rounded.RestartAlt,
            content = {
                Text(
                    "Restore default Hardened profile, tracking protection, fingerprinting, connection, and content privacy settings?",
                    style = PetalText.dialogBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }

    val totalBlockedCount = remember(adBlockEnabled) {
        PetalAdBlockEngine.getTotalBlockedCount()
    }

    if (showWhitelistDialog) {
        PetalAlertDialog(
            onDismissRequest = { showWhitelistDialog = false },
            title = stringResource(R.string.ui_adblock_domain_whitelist),
            confirmText = stringResource(R.string.ui_done),
            onConfirm = { showWhitelistDialog = false },
            dismissText = null,
            icon = Icons.Rounded.FilterList,
            content = {
                Text(
                    stringResource(R.string.ui_domains_added_here_will_bypass),
                    style = PetalText.dialogBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = whitelistDomainInput,
                        onValueChange = { whitelistDomainInput = it },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Button(
                        onClick = {
                            if (whitelistDomainInput.isNotBlank()) {
                                PetalAdBlockEngine.addDomainToWhitelist(context, whitelistDomainInput.trim())
                                whitelistedDomainsState = PetalAdBlockEngine.getWhitelistedDomains()
                                whitelistDomainInput = ""
                            }
                        },
                        shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp)
                    ) {
                        Text(stringResource(R.string.ui_add))
                    }
                }

                if (whitelistedDomainsState.isEmpty()) {
                    Text(
                        stringResource(R.string.ui_no_whitelisted_domains),
                        style = PetalText.emptyState,
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
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.ui_remove),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        )
    }

    CompositionLocalProvider(LocalPetalGlassWallpaper provides wallpaper) {
        val passwordsActivity = context as? ComponentActivity
        val hasAdBlockSub = showAdBlockSettings
        val hasPasswordsSub = showPasswordsScreen && passwordsActivity != null
        // AdBlock Settings and Password Manager are real sub-pages: this page stays composed
        // (and blurred / dimmed by the sub-page's own back gesture) behind them.
        com.petal.browser.predictive.PetalSubPageHost(
            visible = hasAdBlockSub || hasPasswordsSub,
            onBack = {
                if (hasAdBlockSub) {
                    showAdBlockSettings = false
                    whitelistedDomainsState = PetalAdBlockEngine.getWhitelistedDomains()
                } else {
                    showPasswordsScreen = false
                }
            },
            modifier = modifier,
            subPage = {
                if (hasAdBlockSub) {
                    PetalAdBlockSettingsScreen(
                        onNavigateBack = {
                            showAdBlockSettings = false
                            whitelistedDomainsState = PetalAdBlockEngine.getWhitelistedDomains()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (hasPasswordsSub && passwordsActivity != null) {
                    PetalPasswordsScreen(
                        activity = passwordsActivity,
                        onNavigateBack = { showPasswordsScreen = false },
                        embedded = true
                    )
                }
            },
            parent = {
        Box(modifier = Modifier.fillMaxSize()) {
            com.petal.browser.ui.components.PetalGlassHeaderLayout(
                modifier = Modifier.fillMaxSize(),
                pageBackground = { com.petal.browser.ui.components.PetalPageBackground(pageKey = "privacy_settings", modifier = Modifier.fillMaxSize()) },
                header = { headerModifier, headerBackdrop ->
                    ExpressiveHeader(
                        modifier = headerModifier,
                        backdrop = headerBackdrop,
                        title = "Privacy & Security",
                        subtitle = "AdBlock, Tracking Protection & Connection Security",
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
                    // ── 1. Privacy Profile (Standard / Hardened / Strict) ──
                    PetalSettingsSection(
                        title = "Privacy Profile",
                        icon = Icons.Rounded.Security,
                        cardId = "privacy_profile",
                        targetHighlightId = targetHighlightItemId
                    ) {
                        val profileOptions = listOf(
                            Triple(
                                "STANDARD",
                                "Standard",
                                "Balanced privacy protections with maximum website compatibility"
                            ),
                            Triple(
                                "HARDENED",
                                "Hardened (Recommended)",
                                "Arkenfox baseline with WebRTC leak defense and referrer trimming"
                            ),
                            Triple(
                                "STRICT",
                                "Strict",
                                "Tor-style resist fingerprinting, strict origin rules, and hardware API blocks"
                            )
                        )
                        PetalGroup(rowCount = profileOptions.size) { index, position ->
                            val (key, title, subtitle) = profileOptions[index]
                            val isSelected = privacyProfile.equals(key, ignoreCase = true)
                            PetalGroupListRow(
                                position = position,
                                selected = isSelected,
                                onClick = { onPrivacyProfileChange(key) },
                                leading = {
                                    PetalGroupIconBadge(
                                        icon = when (key) {
                                            "STRICT" -> Icons.Rounded.PrivacyTip
                                            "HARDENED" -> Icons.Rounded.Security
                                            else -> Icons.Rounded.Shield
                                        },
                                        variant = if (isSelected) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL
                                    )
                                },
                                content = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            style = PetalText.rowTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSelected && !key.equals("STANDARD", ignoreCase = true)) {
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ) {
                                                Text(
                                                    text = "Restart Petal to apply",
                                                    style = PetalText.badge,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = subtitle,
                                        style = PetalText.rowSubtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailing = {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // ── 2. Ad & Tracker Shield (Stats + Master Switch + Filter Lists + Site Exceptions) ──
                    PetalSettingsSection(
                        title = "Ad & Tracker Shield",
                        icon = Icons.Rounded.Shield,
                        cardId = "privacy_adblock",
                        targetHighlightId = targetHighlightItemId
                    ) {
                        PetalHeroCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                PetalGroupIconBadge(
                                    icon = if (adBlockEnabled) Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
                                    variant = if (adBlockEnabled) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL,
                                    size = 48.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (adBlockEnabled) "Shields Actively Blocking" else "Shields Paused",
                                        style = PetalText.cardTitle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${NumberFormat.getInstance().format(totalBlockedCount)} ads & trackers blocked",
                                        style = PetalText.cardBody,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.TOP,
                            title = "Ad & Tracker Shield",
                            subtitle = "Intercepts ads, analytics, and third-party trackers via WebExtension blocker and Gecko ETP",
                            icon = Icons.Rounded.Block,
                            checked = adBlockEnabled,
                            onCheckedChange = { newValue ->
                                onAdBlockEnabledChange(newValue)
                                PetalAdBlockEngine.setAdBlockEnabled(context, newValue)
                            }
                        )

                        PetalGroupNavigationRow(
                            variant = privacyRowVariants[1],
                            title = "Filter Lists & Rules",
                            subtitle = "EasyList, uAssets, Hagezi, and custom subscriptions",
                            position = PetalGroupPosition.MIDDLE,
                            onClick = { showAdBlockSettings = true },
                            leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) }
                        )

                        PetalGroupNavigationRow(
                            variant = privacyRowVariants[2],
                            title = "Site Exceptions (Whitelist)",
                            subtitle = "${whitelistedDomainsState.size} domains allowed",
                            position = PetalGroupPosition.BOTTOM,
                            onClick = { showWhitelistDialog = true },
                            leadingIcon = { Icon(Icons.Rounded.Rule, contentDescription = null) }
                        )
                    }

                    // ── 3. Browsing Protection (HTTPS-Only, Block Pop-ups, Auto-Reject Cookie Banners) ──
                    PetalSettingsSection(
                        title = "Browsing Protection",
                        icon = Icons.Rounded.Https,
                        cardId = "privacy_https_only",
                        highlightIds = listOf("privacy_popups", "privacy_cookie_banners"),
                        targetHighlightId = targetHighlightItemId
                    ) {
                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.TOP,
                            title = "HTTPS-Only Mode",
                            subtitle = "Automatically upgrade connections to HTTPS and warn on insecure HTTP",
                            icon = Icons.Rounded.Https,
                            checked = httpsOnly,
                            onCheckedChange = onHttpsOnlyChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[1],
                            position = PetalGroupPosition.MIDDLE,
                            title = "Block Pop-up Windows",
                            subtitle = "Prevents unwanted new windows and intrusive pop-ups",
                            icon = Icons.Rounded.OpenInNew,
                            checked = blockPopups,
                            onCheckedChange = onBlockPopupsChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[2],
                            position = PetalGroupPosition.BOTTOM,
                            title = "Auto-Reject Cookie Banners (GDPR)",
                            subtitle = "Automatically declines cookie consent popups and modals",
                            icon = Icons.Rounded.VerifiedUser,
                            checked = cookieBannerAutoReject,
                            onCheckedChange = onCookieBannerAutoRejectChange
                        )
                    }

                    // ── 4. Data, Lock & Passwords (Clear Data, Exit Purge, App Lock, Biometric Tab Lock, Password Manager) ──
                    PetalSettingsSection(
                        title = "Data, Lock & Passwords",
                        icon = Icons.Rounded.Lock,
                        cardId = "privacy_incognito",
                        highlightIds = listOf("privacy_clear_data", "privacy_app_lock", "privacy_passwords"),
                        targetHighlightId = targetHighlightItemId
                    ) {
                        com.petal.browser.ui.containment.PetalGroupRow(
                            position = PetalGroupPosition.TOP,
                            title = "Clear Browsing Data",
                            subtitle = "Permanently remove history, cache, cookies, and local database storage",
                            icon = Icons.Rounded.DeleteOutline,
                            onClick = { showDeleteScreenDialog = true }
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.MIDDLE,
                            title = "Clear Data on Exit",
                            subtitle = "Automatically purge cache, cookies, and browsing sessions when closing Petal",
                            icon = Icons.Rounded.DeleteSweep,
                            checked = clearOnExit,
                            onCheckedChange = { newValue ->
                                clearOnExit = newValue
                                sp.edit()
                                    .putBoolean("sp_clear_quit", newValue)
                                    .putBoolean("sp_clear_on_exit", newValue)
                                    .apply()
                            }
                        )

                        PetalGroupNavigationRow(
                            variant = privacyRowVariants[1],
                            title = "App Lock",
                            subtitle = "Fingerprint or passcode protection for Petal",
                            position = PetalGroupPosition.MIDDLE,
                            onClick = onNavigateToAppLock,
                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) }
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[2],
                            position = PetalGroupPosition.MIDDLE,
                            title = "Biometric Tab Lock",
                            subtitle = "Require biometric authentication (fingerprint/face) to view incognito tabs",
                            icon = Icons.Rounded.Fingerprint,
                            checked = biometricTabLock,
                            onCheckedChange = onBiometricTabLockChange
                        )

                        PetalGroupNavigationRow(
                            variant = privacyRowVariants[0],
                            title = "Password Manager & Autofill Vault",
                            subtitle = "Encrypted local credentials, passkeys, and breach alert monitoring",
                            position = PetalGroupPosition.BOTTOM,
                            onClick = { showPasswordsScreen = true },
                            leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) }
                        )
                    }

                    // ── 5. Advanced (Collapsed by default; auto-expands on search target match) ──
                    PetalSettingsSection(
                        title = "Advanced",
                        icon = Icons.Rounded.Tune,
                        cardId = "privacy_advanced",
                        highlightIds = advancedHighlightIds,
                        targetHighlightId = targetHighlightItemId,
                        collapsible = true,
                        expanded = advancedExpanded,
                        onToggleExpand = { advancedExpanded = !advancedExpanded }
                    ) {
                        // Cross-site tracking Standard / Strict grouped control + underlying sp_ switches
                        PetalSectionLabel("Cross-site tracking")
                        val isCrossSiteStrict =
                            etpStrict && blockThirdPartyCookies && bounceTracking && totalCookieProtection && sessionPartitioning
                        val crossSiteModes = listOf(
                            Pair(
                                false,
                                Pair(
                                    "Standard",
                                    "Total Cookie Protection and session partitioning with high site compatibility"
                                )
                            ),
                            Pair(
                                true,
                                Pair(
                                    "Strict",
                                    "Enables strict tracking protection, third-party cookie blocking, and bounce tracking purge"
                                )
                            )
                        )
                        PetalGroup(rowCount = crossSiteModes.size) { index, position ->
                            val (strictMode, info) = crossSiteModes[index]
                            val (title, subtitle) = info
                            val isSelected = isCrossSiteStrict == strictMode
                            PetalGroupListRow(
                                position = position,
                                selected = isSelected,
                                onClick = { onCrossSiteTrackingChange(strictMode) },
                                leading = {
                                    PetalGroupIconBadge(
                                        icon = if (strictMode) Icons.Rounded.Security else Icons.Rounded.Shield,
                                        variant = if (isSelected) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL
                                    )
                                },
                                content = {
                                    Text(
                                        text = title,
                                        style = PetalText.rowTitle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = subtitle,
                                        style = PetalText.rowSubtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailing = {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            )
                        }

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.TOP,
                            title = "Total Cookie Protection",
                            subtitle = "Dynamic first-party cookie jar isolation and state partitioning",
                            icon = Icons.Rounded.Cookie,
                            checked = totalCookieProtection,
                            onCheckedChange = onTotalCookieProtectionChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[1],
                            position = PetalGroupPosition.MIDDLE,
                            title = "Unlinkable Session Partitioning",
                            subtitle = "Isolates client network state per tab session to prevent cross-site linkability",
                            icon = Icons.Rounded.Splitscreen,
                            checked = sessionPartitioning,
                            onCheckedChange = onSessionPartitioningChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[2],
                            position = PetalGroupPosition.MIDDLE,
                            title = "Block Third-Party Cookies",
                            subtitle = "Prevent cross-site tracking cookies across sites",
                            icon = Icons.Rounded.Block,
                            checked = blockThirdPartyCookies,
                            onCheckedChange = onBlockThirdPartyCookiesChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.BOTTOM,
                            title = "Bounce Tracking Protection",
                            subtitle = "Purges redirect tracker state when traversing bounce redirect intermediaries",
                            icon = Icons.Rounded.AltRoute,
                            checked = bounceTracking,
                            onCheckedChange = onBounceTrackingChange
                        )

                        Spacer(Modifier.height(4.dp))

                        // Resist Fingerprinting with Canvas Letterboxing nested underneath
                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.TOP,
                            title = "Resist Fingerprinting (Gecko RFP)",
                            subtitle = "Uniform canvas, audio context, hardware metrics, and reduced timer precision",
                            icon = Icons.Rounded.Fingerprint,
                            checked = fingerprintProtection,
                            onCheckedChange = onFingerprintProtectionChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[1],
                            position = PetalGroupPosition.BOTTOM,
                            modifier = Modifier.padding(start = 16.dp),
                            title = "Canvas Letterboxing",
                            subtitle = "Nested under Resist Fingerprinting: adds viewport margins to mask display size (may break some sites)",
                            icon = Icons.Rounded.AspectRatio,
                            checked = canvasLetterboxing,
                            onCheckedChange = onCanvasLetterboxingChange
                        )

                        Spacer(Modifier.height(4.dp))

                        // Referrers, Signals, WebRTC, DRM, Autofill, JavaScript
                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[2],
                            position = PetalGroupPosition.TOP,
                            title = "Strict Referrer Trimming",
                            subtitle = "Strips cross-origin URL paths and query parameters from HTTP Referer headers",
                            icon = Icons.Rounded.LinkOff,
                            checked = trimReferrers,
                            onCheckedChange = onTrimReferrersChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.MIDDLE,
                            title = "Privacy Signals (GPC + DNT)",
                            subtitle = "Broadcasts Global Privacy Control and Do Not Track signals with every HTTP request",
                            icon = Icons.Rounded.Security,
                            checked = dntGpc,
                            onCheckedChange = onDntGpcChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[1],
                            position = PetalGroupPosition.MIDDLE,
                            title = "WebRTC IP Leak Protection",
                            subtitle = "Prevents leaking local private IP address via WebRTC peer connections",
                            icon = Icons.Rounded.WifiProtectedSetup,
                            checked = webrtcProtection,
                            onCheckedChange = onWebrtcProtectionChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[2],
                            position = PetalGroupPosition.MIDDLE,
                            title = "DRM Content Playback",
                            subtitle = "Allow playing DRM-protected digital media (Widevine)",
                            icon = Icons.Rounded.PlayCircle,
                            checked = drmEnabled,
                            onCheckedChange = onDrmEnabledChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[0],
                            position = PetalGroupPosition.MIDDLE,
                            title = "Autofill Logins & Passwords",
                            subtitle = "Automatically suggest and fill saved credentials on web forms",
                            icon = Icons.Rounded.Password,
                            checked = loginAutofillEnabled,
                            onCheckedChange = onLoginAutofillEnabledChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[1],
                            position = PetalGroupPosition.MIDDLE,
                            title = "JavaScript",
                            subtitle = "Enable JavaScript execution for web pages",
                            icon = Icons.Rounded.Code,
                            checked = javaScriptEnabled,
                            onCheckedChange = onJavaScriptEnabledChange
                        )

                        PetalSettingsToggleRow(
                            variant = privacyRowVariants[2],
                            position = PetalGroupPosition.BOTTOM,
                            title = stringResource(R.string.ui_shortcut_favicon_fetching),
                            subtitle = stringResource(R.string.ui_shortcut_favicon_fetching_desc),
                            icon = Icons.Rounded.Public,
                            checked = fetchShortcutFavicons,
                            onCheckedChange = onFetchShortcutFaviconsChange
                        )

                        Spacer(Modifier.height(4.dp))

                        // Secure Private DNS (DoH) inside Advanced
                        PetalSectionLabel("Secure Private DNS (DoH)")
                        val dnsEntries = remember {
                            buildList<DnsEntry> {
                                add(DnsEntry.Single("OFF", "System Default (Off)", "Use default network DNS", false))
                                com.petal.browser.unit.PetalDnsCatalog.presets.groupBy { it.provider }.forEach { (provider, list) ->
                                    if (list.size == 1) {
                                        add(DnsEntry.Single(list[0].mode, list[0].title, list[0].description, false))
                                    } else {
                                        add(DnsEntry.Group(provider, list.map { DnsChoice(it.mode, it.variant ?: it.provider, it.description) }))
                                    }
                                }
                                add(DnsEntry.Single("CUSTOM", "Custom DNS-over-HTTPS (DoH)", "Enter your preferred DoH resolver endpoint URL", true))
                            }
                        }
                        var expandedDns by rememberSaveable { mutableStateOf<String?>(null) }

                        PetalGroup(rowCount = dnsEntries.size) { index, position ->
                            when (val entry = dnsEntries[index]) {
                                is DnsEntry.Single -> {
                                    val isSelected = privateDnsMode == entry.mode
                                    PetalGroupListRow(
                                        position = position,
                                        selected = isSelected,
                                        onClick = {
                                            expandedDns = null
                                            onPrivateDnsModeChange(entry.mode)
                                        },
                                        leading = {
                                            PetalGroupIconBadge(
                                                icon = if (entry.custom) Icons.Rounded.Dns else Icons.Rounded.Security,
                                                variant = if (isSelected) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL
                                            )
                                        },
                                        content = {
                                            Text(
                                                text = entry.title,
                                                style = PetalText.rowTitle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = entry.desc,
                                                style = PetalText.rowSubtitle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailing = {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                                is DnsEntry.Group -> {
                                    val activeChoice = entry.choices.firstOrNull { it.mode == privateDnsMode }
                                    val isExpanded = expandedDns == entry.provider
                                    val chevronAngle by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isExpanded) 180f else 0f,
                                        label = "dnsChevron"
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        PetalGroupListRow(
                                            position = position,
                                            selected = activeChoice != null,
                                            onClick = { expandedDns = if (isExpanded) null else entry.provider },
                                            leading = {
                                                PetalGroupIconBadge(
                                                    icon = Icons.Rounded.Security,
                                                    variant = if (activeChoice != null) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL
                                                )
                                            },
                                            content = {
                                                Text(
                                                    text = entry.provider,
                                                    style = PetalText.rowTitle,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = activeChoice?.let { "Using ${it.label}" } ?: "${entry.choices.size} options",
                                                    style = PetalText.rowSubtitle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            trailing = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (activeChoice != null) {
                                                        Icon(
                                                            Icons.Rounded.CheckCircle,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                    }
                                                    Icon(
                                                        Icons.Rounded.ExpandMore,
                                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.rotate(chevronAngle)
                                                    )
                                                }
                                            }
                                        )
                                        AnimatedVisibility(visible = isExpanded, enter = androidx.compose.animation.fadeIn(PetalSpring.calm()) + androidx.compose.animation.expandIn(PetalSpring.calm()), exit = androidx.compose.animation.fadeOut(PetalSpring.calm()) + androidx.compose.animation.shrinkOut(PetalSpring.calm())) {
                                            Column(
                                                modifier = Modifier.padding(start = 20.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                entry.choices.forEachIndexed { i, choice ->
                                                    val chosen = privateDnsMode == choice.mode
                                                    PetalGroupListRow(
                                                        position = petalGroupPositionFor(i, entry.choices.size),
                                                        selected = chosen,
                                                        onClick = {
                                                            onPrivateDnsModeChange(choice.mode)
                                                            expandedDns = null
                                                        },
                                                        minHeight = 56.dp,
                                                        leading = {
                                                            PetalGroupIconBadge(
                                                                icon = Icons.Rounded.Dns,
                                                                variant = if (chosen) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL,
                                                                size = 36.dp,
                                                                iconSize = 18.dp
                                                            )
                                                        },
                                                        content = {
                                                            Text(
                                                                text = choice.label,
                                                                style = PetalText.rowTitle,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = choice.desc,
                                                                style = PetalText.rowSubtitle,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        },
                                                        trailing = {
                                                            if (chosen) {
                                                                Icon(
                                                                    Icons.Rounded.CheckCircle,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (privateDnsMode == "NEXTDNS") {
                            PetalHeroCard {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    TextField(
                                        value = nextDnsProfileId,
                                        onValueChange = onNextDnsProfileIdChange,
                                        label = { Text("NextDNS profile ID") },
                                        placeholder = { Text("abc123") },
                                        supportingText = {
                                            Text(
                                                if (nextDnsProfileId.isBlank() ||
                                                    com.petal.browser.unit.PetalDnsCatalog.isValidNextDnsProfileId(nextDnsProfileId)
                                                ) "Optional. Uses your own NextDNS profile and settings."
                                                else "Profile IDs are 4-12 letters or digits."
                                            )
                                        },
                                        isError = nextDnsProfileId.isNotBlank() &&
                                            !com.petal.browser.unit.PetalDnsCatalog.isValidNextDnsProfileId(nextDnsProfileId),
                                        leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth(), glass = false
                                    )
                                }
                            }
                        }

                        if (privateDnsMode == "CUSTOM") {
                            PetalHeroCard {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    TextField(
                                        value = customDohUrl,
                                        onValueChange = onCustomDohUrlChange,
                                        label = { Text(stringResource(R.string.ui_custom_doh_endpoint_url)) },
                                        placeholder = { Text("https://dns.adguard-dns.com/dns-query") },
                                        leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth(), glass = false
                                    )
                                }
                            }
                        }
                    }

                    // ── 6. Reset Privacy to Recommended Action ──
                    PetalSettingsSection(
                        title = "Reset",
                        icon = Icons.Rounded.RestartAlt,
                        cardId = "privacy_reset",
                        targetHighlightId = targetHighlightItemId
                    ) {
                        com.petal.browser.ui.containment.PetalGroupRow(
                            position = PetalGroupPosition.SINGLE,
                            icon = Icons.Rounded.RestartAlt,
                            title = "Reset privacy to recommended",
                            subtitle = "Restore default Hardened profile, tracking, fingerprinting, connection, and content protections",
                            onClick = { showResetPrivacyDialog = true }
                        )
                    }
                }
            }
        }

        if (showDeleteScreenDialog) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.petal.browser.compose.settings.PetalDeleteScreen(
                    onBackPress = { showDeleteScreenDialog = false }
                )
            }
        }
            }
        )
    }
}

/** Same badge colour cycle the Settings hub and Account rows use. */
private val privacyRowVariants = listOf(PetalBadgeVariant.PRIMARY, PetalBadgeVariant.SECONDARY, PetalBadgeVariant.TERTIARY)

private data class DnsChoice(val mode: String, val label: String, val desc: String)

private sealed interface DnsEntry {
    data class Single(val mode: String, val title: String, val desc: String, val custom: Boolean) : DnsEntry
    data class Group(val provider: String, val choices: List<DnsChoice>) : DnsEntry
}
