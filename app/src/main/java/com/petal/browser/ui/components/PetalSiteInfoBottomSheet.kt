package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.ui.glass.captureGlassWallpaper
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.containment.PetalFilterChip
import com.petal.browser.ui.containment.PetalSuggestionChip
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSiteInfoBottomSheet(
    albumController: AlbumController?,
    onDismissRequest: () -> Unit,
    onResetSiteData: () -> Unit
) {
    val context = LocalContext.current
    val tabViewController = albumController as? PetalTabViewController
    
    val currentUrl = albumController?.url ?: ""
    val domain = remember(currentUrl) { PetalAndroidUtils.domain(currentUrl) }
    val favicon: Bitmap? = tabViewController?.getFavicon()

    // Check GeckoView SecurityInformation if available, plus URL scheme
    val geckoSecurity = tabViewController?.currentSecurityInfo
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
    val isHttps = urlScheme == "https" || geckoSecurity?.isSecure == true
    val isHttp = urlScheme == "http"
    val isInternalPage = currentUrl.startsWith("petal:") || currentUrl.startsWith("about:")
    val sslCertificate: SslCertificate? = null
    val isSecure = isHttps || (isInternalPage && currentUrl.isNotEmpty())

    // Cookie Count for domain
    var cookieCount by remember(currentUrl) {
        mutableIntStateOf(
            try {
                val cookies = com.petal.browser.download.PetalGeckoCookieBridge.getCookies(currentUrl)
                    ?: CookieManager.getInstance().getCookie(currentUrl)
                if (!cookies.isNullOrEmpty()) cookies.split(";").size else 0
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
                sp.getBoolean("${profile}_desktop", false)
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
    val wallpaper = com.petal.browser.ui.glass.rememberPetalGlassWallpaper()
    val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current }

    CompositionLocalProvider(com.petal.browser.ui.glass.LocalPetalGlassWallpaper provides wallpaper) {
        com.petal.browser.ui.containment.PetalSheet(
            onDismissRequest = onDismissRequest,
            ownsGlassBackdrop = false,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isLiquidGlass) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .captureGlassWallpaper(wallpaper)
                    ) {
                        if (snapshotBitmap != null && !snapshotBitmap.isRecycled) {
                            Image(
                                bitmap = snapshotBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            com.petal.browser.ui.glass.PetalGlassPattern(Modifier.fillMaxSize())
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
            // --- Domain & Security Header ---
            com.petal.browser.ui.containment.PetalHeroCard(
                shape = com.petal.browser.ui.containment.PetalContainmentShapes.Hero,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 50.dp,
                        iconSize = 26.dp
                    ) {
                        if (favicon != null) {
                            Image(
                                bitmap = favicon.asImageBitmap(),
                                contentDescription = stringResource(R.string.ui_site_favicon),
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(ExpressiveButtonShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = domain.ifEmpty { "Current Website" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                isHttps -> "Connection is secure"
                                isInternalPage -> "Internal browser page"
                                isHttp -> "Connection not secure"
                                else -> "Connection status unavailable"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isHttps || isInternalPage -> Color(0xFF2E7D32)
                                isHttp -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- SSL & Connection Security Card ---
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
                    com.petal.browser.ui.containment.PetalGroupIconBadge(
                        shape = RoundedCornerShape(12.dp),
                        containerColor = when {
                            isHttps || isInternalPage -> MaterialTheme.colorScheme.primaryContainer
                            isHttp -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = when {
                            isHttps || isInternalPage -> MaterialTheme.colorScheme.onPrimaryContainer
                            isHttp -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        size = 50.dp,
                        iconSize = 24.dp
                    ) {
                        Icon(
                            imageVector = when {
                                isHttps || isInternalPage -> Icons.Rounded.Lock
                                isHttp -> Icons.Rounded.LockOpen
                                else -> Icons.Rounded.HelpOutline
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isHttps && sslCertificate != null -> "Valid Security Certificate"
                                isHttps -> "Encrypted Connection"
                                isInternalPage -> "Secure Local Origin"
                                isHttp -> "Unencrypted Connection"
                                else -> "Connection status unavailable"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val certDetails = remember(sslCertificate, isHttps, isHttp, isInternalPage, geckoSecurity) {
                            when {
                                sslCertificate != null && isHttps ->
                                    "Issued to: ${sslCertificate.issuedTo.cName}\nIssued by: ${sslCertificate.issuedBy.oName}"
                                geckoSecurity?.host != null && isHttps ->
                                    "Host: ${geckoSecurity.host}\nYour connection is encrypted and verified."
                                isHttps ->
                                    "Your information is encrypted when sent to this site."
                                isInternalPage ->
                                    "This page is part of Petal Browser and does not send network data."
                                isHttp ->
                                    "You should not enter sensitive info on this site (unencrypted HTTP)."
                                else ->
                                    "Security information is unavailable for this page."
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = certDetails,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Enhanced Tracking Protection (ETP Shield) Section ---
            Text(
                text = stringResource(R.string.ui_enhanced_tracking_protection),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
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
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (trackingProtectionEnabled) {
                                    if (blockedCount > 0) "$blockedCount trackers & ads blocked on this site" else "Blocking known cross-site trackers & ads"
                                } else {
                                    "Protection is disabled for this domain"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                onDismissRequest()
                                com.petal.browser.privacy.PetalElementZapper.launch(albumController)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Zap Element (Interactive Visual Picker)", fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(6.dp))
                        var showBlockElementDialog by remember { mutableStateOf(false) }
                        var customCssSelector by remember { mutableStateOf("") }
                        FilledTonalButton(
                            onClick = { showBlockElementDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Block Page Element (Cosmetic Filter)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

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
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
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
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (cookieCount > 0) "$cookieCount active cookies" else "No active cookies stored",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        GeolocationPermissions.getInstance().clear(domain)
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
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
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
                                GeolocationPermissions.getInstance().clear(domain)
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
    }
}
