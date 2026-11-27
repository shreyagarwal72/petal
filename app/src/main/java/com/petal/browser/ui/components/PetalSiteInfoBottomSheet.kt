package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.PetalText
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.ui.glass.captureGlassWallpaper
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.containment.PetalFilterChip
import com.petal.browser.ui.containment.PetalSuggestionChip
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.petalSheetSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSiteInfoBottomSheet(
    albumController: AlbumController?,
    onDismissRequest: () -> Unit,
    onResetSiteData: () -> Unit
) {
    val context = LocalContext.current
    val mainAct = context as? com.petal.browser.activity.MainActivity
    val currentTab = mainAct?.viewModel?.activeTab?.value
    val tabViewController = albumController as? PetalTabViewController
    
    val currentUrl = currentTab?.content?.url?.takeIf { it.isNotBlank() } ?: albumController?.url ?: ""
    val domain = remember(currentUrl) { PetalAndroidUtils.domain(currentUrl) }
    val favicon: Bitmap? = currentTab?.content?.icon ?: tabViewController?.favicon

    // Check GeckoView SecurityInformation if available, plus URL scheme
    val isSecureConnection = (currentTab?.content?.securityInfo?.isSecure == true) || (tabViewController?.currentSecurityInfo?.isSecure == true)
    val securityHost = tabViewController?.currentSecurityInfo?.host ?: domain.takeIf { it.isNotBlank() }
    val effectiveUrl = remember(currentUrl) {
        val trimmed = currentUrl.trim()
        if (trimmed.isEmpty()) ""
        else if (trimmed.contains("://")) trimmed
        else "https://$trimmed"
    }
    val urlScheme = remember(effectiveUrl) {
        try {
            val uri = Uri.parse(effectiveUrl)
            uri.scheme?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    val isHttps = urlScheme == "https" || isSecureConnection
    val isHttp = urlScheme == "http"
    val isInternalPage = currentUrl.startsWith("petal:") || currentUrl.startsWith("about:")
    val sslCertificate: SslCertificate? = null
    val isSecure = isHttps || (isInternalPage && currentUrl.isNotEmpty())

    // Cookie Count for domain
    var cookieCount by remember(currentUrl) {
        mutableIntStateOf(
            try {
                com.petal.browser.download.PetalGeckoCookieBridge.getCookieCount(currentUrl)
            } catch (e: Exception) { 0 }
        )
    }

    // SharedPreferences for site permission states
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val profile = remember { com.petal.browser.engine.gecko.PetalEngineBridge.getProfile(context) }

    var isCameraAllowed by remember { mutableStateOf(sp.getBoolean(profile + "_camera", false)) }
    var isMicAllowed by remember { mutableStateOf(sp.getBoolean(profile + "_microphone", false)) }
    var isLocationAllowed by remember { mutableStateOf(sp.getBoolean(profile + "_location", false)) }
    var isNotificationsAllowed by remember { mutableStateOf(sp.getBoolean("sp_notifications_$domain", true)) }

    // Site display preferences
    var isDesktopSite by remember(domain) {
        mutableStateOf(
            if (domain.isNotEmpty() && sp.contains("sp_desktop_site_$domain")) {
                sp.getBoolean("sp_desktop_site_$domain", false)
            } else {
                sp.getBoolean("sp_desktop_site", false)
            }
        )
    }

    // Enhanced Tracking Protection (ETP) breakdown
    val isDomainWhitelisted = remember(domain) { com.petal.browser.browser.PetalAdBlockEngine.isDomainWhitelisted(domain) }
    var trackingProtectionEnabled by remember(domain, isDomainWhitelisted) { mutableStateOf(!isDomainWhitelisted) }
    val blockedCount = remember(domain) { com.petal.browser.browser.PetalAdBlockEngine.getBlockedCountForDomain(domain) }
    val categoryCounts = remember(domain) { com.petal.browser.browser.PetalAdBlockEngine.getCategoryCountsForDomain(domain) }
    val initialDomainMode = remember(domain) { com.petal.browser.browser.PetalAdBlockEngine.getEffectiveMode(context, domain) }

    val isLiquidGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")

    com.petal.browser.ui.containment.PetalSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // --- Domain & Security Header (PixelMusic sheet style, live-glass aware) ---
            com.petal.browser.ui.containment.PetalSheetHeader(
                title = domain.ifEmpty { "Current Website" },
                subtitle = when {
                    isHttps -> "Connection is secure"
                    isInternalPage -> "Internal browser page"
                    isHttp -> "Connection not secure"
                    else -> "Connection status unavailable"
                },
                leading = {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .petalSheetSurface(
                                com.petal.browser.ui.theme.SmoothRoundedShape(24.dp),
                                MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (favicon != null) {
                            Image(
                                bitmap = favicon.asImageBitmap(),
                                contentDescription = stringResource(R.string.ui_site_favicon),
                                modifier = Modifier.size(40.dp).clip(ExpressiveButtonShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            val certDetails = when {
                sslCertificate != null && isHttps ->
                    "Issued to: ${sslCertificate.issuedTo.cName}\nIssued by: ${sslCertificate.issuedBy.oName}"
                securityHost != null && isHttps ->
                    "Host: $securityHost\nYour connection is encrypted and verified."
                isHttps -> "Your information is encrypted when sent to this site."
                isInternalPage -> "This page is part of Petal Browser and does not send network data."
                isHttp -> "You should not enter sensitive info on this site (unencrypted HTTP)."
                else -> "Security information is unavailable for this page."
            }
            com.petal.browser.ui.containment.PetalSheetListRow(
                title = when {
                    isHttps && sslCertificate != null -> "Valid Security Certificate"
                    isHttps -> "Encrypted Connection"
                    isInternalPage -> "Secure Local Origin"
                    isHttp -> "Unencrypted Connection"
                    else -> "Connection status unavailable"
                },
                subtitle = certDetails,
                icon = when {
                    isHttps || isInternalPage -> Icons.Rounded.Lock
                    isHttp -> Icons.Rounded.LockOpen
                    else -> Icons.Rounded.HelpOutline
                },
                tone = when {
                    isHttps || isInternalPage -> com.petal.browser.ui.containment.PetalSheetTone.Primary
                    isHttp -> com.petal.browser.ui.containment.PetalSheetTone.Danger
                    else -> com.petal.browser.ui.containment.PetalSheetTone.Secondary
                },
                onClick = {}
            )

            Spacer(modifier = Modifier.height(14.dp))

            // --- Enhanced Tracking Protection (ETP Shield) Section ---
            Text(
                text = stringResource(R.string.ui_enhanced_tracking_protection),
                style = PetalText.sectionHeader,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )

            com.petal.browser.ui.containment.PetalHeroCard(
                shape = com.petal.browser.ui.containment.PetalContainmentShapes.Hero,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = RoundedCornerShape(12.dp),
                            containerColor = if (trackingProtectionEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (trackingProtectionEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 50.dp,
                            iconSize = 24.dp
                        ) {
                            Icon(
                                imageVector = if (trackingProtectionEnabled) Icons.Rounded.Shield else Icons.Rounded.ShieldMoon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (trackingProtectionEnabled) "Protections Active" else "Protections Paused",
                                style = PetalText.cardTitle
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (trackingProtectionEnabled) {
                                    if (blockedCount > 0) "$blockedCount trackers & ads blocked on this site" else "Blocking known cross-site trackers & ads"
                                } else {
                                    "Protection is disabled for this domain"
                                },
                                style = PetalText.cardBody
                            )
                        }

                        IconSwitch(
                            checked = trackingProtectionEnabled,
                            icon = Icons.Rounded.Check,
                            onCheckedChange = { enabled ->
                                trackingProtectionEnabled = enabled
                                if (enabled) {
                                    com.petal.browser.browser.PetalAdBlockEngine.removeDomainFromWhitelist(context, domain)
                                } else {
                                    com.petal.browser.browser.PetalAdBlockEngine.addDomainToWhitelist(context, domain)
                                }
                                tabViewController?.reload()
                            }
                        )
                    }

                    if (trackingProtectionEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val adsLabel = if (categoryCounts.ads > 0) "Ads: ${categoryCounts.ads}" else "Ads: 0"
                            val trackersLabel = if (categoryCounts.analytics > 0) "Trackers: ${categoryCounts.analytics}" else "Trackers: 0"
                            val socialLabel = if (categoryCounts.social > 0) "Social: ${categoryCounts.social}" else "Social: 0"
                            val cookiesLabel = if (categoryCounts.cookies > 0) "Cookies: ${categoryCounts.cookies}" else "Cookies: Isolated"

                            PetalSuggestionChip(
                                onClick = {},
                                label = { Text(adsLabel, style = PetalText.badge, maxLines = 1) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            PetalSuggestionChip(
                                onClick = {},
                                label = { Text(trackersLabel, style = PetalText.badge, maxLines = 1) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            PetalSuggestionChip(
                                onClick = {},
                                label = { Text(socialLabel, style = PetalText.badge, maxLines = 1) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            PetalSuggestionChip(
                                onClick = {},
                                label = { Text(cookiesLabel, style = PetalText.badge, maxLines = 1) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        var isForgetful by remember {
                            mutableStateOf(com.petal.browser.privacy.ForgetfulBrowsingManager.isDomainForgetful(context, domain))
                        }
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                            title = "Forgetful Browsing",
                            subtitle = "Clear cookies, storage and cache when tab is closed",
                            icon = Icons.Rounded.DeleteSweep,
                            checked = isForgetful,
                            onCheckedChange = { checked ->
                                isForgetful = checked
                                com.petal.browser.privacy.ForgetfulBrowsingManager.setDomainForgetful(context, domain, checked)
                            }
                        )
                        var isDarkWhitelisted by remember {
                            mutableStateOf(
                                com.petal.browser.extensions.PetalBuiltInExtensionManager.isDomainInWhitelist(
                                    domain,
                                    com.petal.browser.extensions.PetalBuiltInExtensionManager.getDarkWebpagesWhitelist(context)
                                )
                            )
                        }
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                            title = "Smart Dark Mode for Site",
                            subtitle = if (!isDarkWhitelisted) "Active (bright canvas inverted if not dark)" else "Excluded from Smart Dark Mode",
                            icon = Icons.Rounded.Nightlight,
                            checked = !isDarkWhitelisted,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    com.petal.browser.extensions.PetalBuiltInExtensionManager.removeDarkWebpageWhitelist(context, domain)
                                    isDarkWhitelisted = false
                                } else {
                                    com.petal.browser.extensions.PetalBuiltInExtensionManager.addDarkWebpageWhitelist(context, domain)
                                    isDarkWhitelisted = true
                                }
                                tabViewController?.reload()
                            }
                        )

                        // Dark mode for this site: follow global / force dark / force light.
                        // Stored as sp_force_dark_$domain; the global sp_force_dark_mode is never touched.
                        if (domain.isNotEmpty()) {
                            var siteScheme by remember(domain) {
                                mutableStateOf(com.petal.browser.engine.gecko.PetalSiteColorScheme.get(sp, domain))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Dark mode for this site",
                                style = PetalText.sectionHeader,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            val schemeValues = listOf(
                                com.petal.browser.engine.gecko.PetalSiteColorScheme.FOLLOW,
                                com.petal.browser.engine.gecko.PetalSiteColorScheme.DARK,
                                com.petal.browser.engine.gecko.PetalSiteColorScheme.LIGHT
                            )
                            com.petal.browser.ui.containment.PetalSheetSegmentedBar(
                                segments = listOf(
                                    com.petal.browser.ui.containment.PetalSheetSegment("Follow", Icons.Rounded.BrightnessAuto),
                                    com.petal.browser.ui.containment.PetalSheetSegment("Dark", Icons.Rounded.DarkMode),
                                    com.petal.browser.ui.containment.PetalSheetSegment("Light", Icons.Rounded.LightMode)
                                ),
                                selectedIndex = schemeValues.indexOf(siteScheme).coerceAtLeast(0),
                                onSelect = { index ->
                                    val next = schemeValues[index]
                                    if (next != siteScheme) {
                                        siteScheme = next
                                        com.petal.browser.engine.gecko.PetalSiteColorScheme.set(sp, domain, next)
                                        // Re-applies the override for this tab and reloads the page.
                                        if (tabViewController != null) {
                                            tabViewController.onSiteColorSchemeChanged()
                                        } else if (mainAct != null) {
                                            mainAct.viewModel.activeTab.value?.id?.let { activeId ->
                                                com.petal.browser.engine.gecko.PetalGeckoRuntime.setSiteColorScheme(
                                                    activeId,
                                                    com.petal.browser.engine.gecko.PetalSiteColorScheme.toGecko(next),
                                                    sp
                                                )
                                            }
                                            mainAct.viewModel.reload()
                                        }
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        com.petal.browser.ui.containment.PetalSheetPill(
                            text = "Zap Element",
                            icon = Icons.Rounded.Bolt,
                            tone = com.petal.browser.ui.containment.PetalSheetTone.Tertiary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onDismissRequest()
                                com.petal.browser.privacy.PetalElementZapper.launch(mainAct, albumController)
                            }
                        )

                        Spacer(Modifier.height(6.dp))
                        var showBlockElementDialog by remember { mutableStateOf(false) }
                        var customCssSelector by remember { mutableStateOf("") }
                        com.petal.browser.ui.containment.PetalSheetPill(
                            text = "Block Page Element",
                            icon = Icons.Rounded.Block,
                            tone = com.petal.browser.ui.containment.PetalSheetTone.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showBlockElementDialog = true }
                        )

                        if (showBlockElementDialog) {
                            com.petal.browser.ui.containment.PetalAlertDialog(
                                onDismissRequest = { showBlockElementDialog = false },
                                title = "Block Element on $domain",
                                message = "Enter a CSS selector (e.g. '.newsletter-modal', '#paywall-banner') to permanently hide it from this domain.",
                                confirmText = "Block",
                                onConfirm = {
                                    if (customCssSelector.isNotBlank()) {
                                        com.petal.browser.privacy.PetalElementBlocker.addSelector(context, domain, customCssSelector.trim())
                                        tabViewController?.reload()
                                        showBlockElementDialog = false
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

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Cookies & Site Data Section ---
            Text(
                text = stringResource(R.string.ui_cookies_site_data),
                style = PetalText.sectionHeader,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )

            com.petal.browser.ui.containment.PetalHeroCard(
                shape = com.petal.browser.ui.containment.PetalContainmentShapes.Hero,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cookie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_stored_cookies_cache),
                            style = PetalText.cardTitle
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (cookieCount > 0) "$cookieCount active cookies" else "No active cookies stored",
                            style = PetalText.cardBody
                        )
                    }

                    var siteResetExpanded by remember { mutableStateOf(false) }
                    Box {
                        ExpressiveSplitButton(
                            label = stringResource(R.string.ui_reset),
                            onPrimaryClick = {
                                try {
                                    com.petal.browser.unit.PetalCacheManager.clearSiteData(context, domain, includeCookies = true, includePermissions = false)
                                    cookieCount = 0
                                    onResetSiteData()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            onMenuClick = { siteResetExpanded = !siteResetExpanded },
                            icon = Icons.Rounded.DeleteSweep,
                            isMenuExpanded = siteResetExpanded,
                            variant = SplitButtonVariant.TONAL,
                            height = 38.dp
                        )

                        com.petal.browser.ui.containment.PetalPopupMenu(
                            expanded = siteResetExpanded,
                            onDismissRequest = { siteResetExpanded = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = { Text(stringResource(R.string.ui_clear_cookies_only)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Cookie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    siteResetExpanded = false
                                    try {
                                        com.petal.browser.unit.PetalCacheManager.clearSiteData(context, domain, includeCookies = true, includePermissions = false)
                                        cookieCount = 0
                                    } catch (_: Exception) {}
                                }
                            )
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = { Text(stringResource(R.string.ui_reset_site_permissions)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    siteResetExpanded = false
                                    try {
                                        com.petal.browser.unit.PetalCacheManager.clearSiteData(context, domain, includeCookies = false, includePermissions = true)
                                        sp.edit()
                                            .remove(profile + "_camera")
                                            .remove(profile + "_microphone")
                                            .remove(profile + "_location")
                                            .remove("sp_notifications_$domain")
                                            .apply()
                                        isCameraAllowed = false
                                        isMicAllowed = false
                                        isLocationAllowed = false
                                        isNotificationsAllowed = true
                                    } catch (_: Exception) {}
                                }
                            )
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = { Text(stringResource(R.string.ui_clear_storage_cache)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    siteResetExpanded = false
                                    try {
                                        com.petal.browser.unit.PetalCacheManager.clearSiteCache(context, domain)
                                        onResetSiteData()
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Site Permissions Section ---
            Text(
                text = stringResource(R.string.ui_page_permissions),
                style = PetalText.sectionHeader,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Camera
                com.petal.browser.ui.containment.PetalGroupToggleRow(
                    title = stringResource(R.string.ui_camera_access),
                    subtitle = if (isCameraAllowed) "Allowed" else "Blocked",
                    checked = isCameraAllowed,
                    position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                    onCheckedChange = { allowed ->
                        isCameraAllowed = allowed
                        sp.edit().putBoolean(profile + "_camera", allowed).apply()
                        if (allowed && context is android.app.Activity) {
                            PetalAndroidUtils.grantPermissionsCamera(context)
                        }
                        tabViewController?.reload()
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = RoundedCornerShape(12.dp),
                            containerColor = if (isCameraAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isCameraAllowed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 38.dp,
                            iconSize = 18.dp
                        ) {
                            Icon(imageVector = Icons.Rounded.Videocam, contentDescription = null)
                        }
                    }
                )

                // Microphone
                com.petal.browser.ui.containment.PetalGroupToggleRow(
                    title = stringResource(R.string.ui_microphone_access),
                    subtitle = if (isMicAllowed) "Allowed" else "Blocked",
                    checked = isMicAllowed,
                    position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                    onCheckedChange = { allowed ->
                        isMicAllowed = allowed
                        sp.edit().putBoolean(profile + "_microphone", allowed).apply()
                        if (allowed && context is android.app.Activity) {
                            PetalAndroidUtils.grantPermissionsMic(context)
                        }
                        tabViewController?.reload()
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = RoundedCornerShape(12.dp),
                            containerColor = if (isMicAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isMicAllowed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 38.dp,
                            iconSize = 18.dp
                        ) {
                            Icon(imageVector = Icons.Rounded.Mic, contentDescription = null)
                        }
                    }
                )

                // Location
                com.petal.browser.ui.containment.PetalGroupToggleRow(
                    title = stringResource(R.string.ui_location_access),
                    subtitle = if (isLocationAllowed) "Allowed" else "Blocked",
                    checked = isLocationAllowed,
                    position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                    onCheckedChange = { allowed ->
                        isLocationAllowed = allowed
                        sp.edit().putBoolean(profile + "_location", allowed).apply()
                        if (allowed && context is android.app.Activity) {
                            PetalAndroidUtils.grantPermissionsLoc(context)
                        } else if (!allowed) {
                            try {
                                com.petal.browser.unit.PetalCacheManager.clearSiteData(context, domain, includeCookies = false, includePermissions = true)
                            } catch (ignored: Exception) {}
                        }
                        tabViewController?.reload()
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = RoundedCornerShape(12.dp),
                            containerColor = if (isLocationAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isLocationAllowed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 38.dp,
                            iconSize = 18.dp
                        ) {
                            Icon(imageVector = Icons.Rounded.MyLocation, contentDescription = null)
                        }
                    }
                )

                // Notifications
                com.petal.browser.ui.containment.PetalGroupToggleRow(
                    title = stringResource(R.string.ui_notifications),
                    subtitle = if (isNotificationsAllowed) "Allowed" else "Blocked",
                    checked = isNotificationsAllowed,
                    position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                    onCheckedChange = { allowed ->
                        isNotificationsAllowed = allowed
                        sp.edit().putBoolean("sp_notifications_$domain", allowed).apply()
                        if (allowed && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && context is android.app.Activity) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                androidx.core.app.ActivityCompat.requestPermissions(context, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                            }
                        }
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = RoundedCornerShape(12.dp),
                            containerColor = if (isNotificationsAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isNotificationsAllowed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 38.dp,
                            iconSize = 18.dp
                        ) {
                            Icon(imageVector = Icons.Rounded.Notifications, contentDescription = null)
                        }
                    }
                )

                // Desktop Site (Per-site override)
                com.petal.browser.ui.containment.PetalGroupToggleRow(
                    title = stringResource(R.string.ui_desktop_site_2),
                    subtitle = if (isDesktopSite) "Requesting desktop version" else "Mobile version",
                    checked = isDesktopSite,
                    position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                    onCheckedChange = { allowed ->
                        isDesktopSite = allowed
                        if (domain.isNotEmpty()) {
                            sp.edit().putBoolean("sp_desktop_site_$domain", allowed).apply()
                        }
                        tabViewController?.setDesktopMode(allowed)
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = RoundedCornerShape(12.dp),
                            containerColor = if (isDesktopSite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isDesktopSite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 38.dp,
                            iconSize = 18.dp
                        ) {
                            Icon(imageVector = Icons.Rounded.DesktopWindows, contentDescription = null)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
