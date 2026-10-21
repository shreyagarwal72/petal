package com.petal.browser.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.petal.browser.unit.HelperUnit
import com.petal.browser.browser.AlbumController
import com.petal.browser.view.PetalGeckoView
import com.petal.browser.ui.glass.captureGlassWallpaper
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
    val geckoView = albumController as? PetalGeckoView
    
    val currentUrl = albumController?.url ?: ""
    val domain = remember(currentUrl) { HelperUnit.domain(currentUrl) }
    val favicon: Bitmap? = geckoView?.getFavicon()

    // Check GeckoView SecurityInformation if available, plus URL scheme
    val geckoSecurity = geckoView?.currentSecurityInfo
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
                val cookies = CookieManager.getInstance().getCookie(currentUrl)
                if (!cookies.isNullOrEmpty()) cookies.split(";").size else 0
            } catch (e: Exception) { 0 }
        )
    }

    // SharedPreferences for site permission states
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val profile = remember { PetalGeckoView.getProfile(context) }

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
    val initialDomainMode = remember(domain) { com.petal.browser.browser.PetalAdBlockEngine.getEffectiveMode(context, domain) }
    var siteShieldMode by remember(domain, initialDomainMode) {
        mutableStateOf(if (initialDomainMode == com.petal.browser.browser.PetalAdBlockMode.AGGRESSIVE) com.petal.browser.browser.PetalAdBlockMode.AGGRESSIVE else com.petal.browser.browser.PetalAdBlockMode.STANDARD)
    }

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
                        shape = com.petal.browser.ui.theme.PetalMaterialShapes.Sunny.toShape(),
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
                                    .clip(CircleShape)
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
                        shape = com.petal.browser.ui.theme.PetalMaterialShapes.SoftBoom.toShape(),
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
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Clover4Leaf.toShape(),
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

                        Switch(
                            checked = trackingProtectionEnabled,
                            onCheckedChange = { enabled ->
                                trackingProtectionEnabled = enabled
                                if (enabled) {
                                    com.petal.browser.browser.PetalAdBlockEngine.removeDomainFromWhitelist(context, domain)
                                } else {
                                    com.petal.browser.browser.PetalAdBlockEngine.addDomainToWhitelist(context, domain)
                                }
                                geckoView?.reloadWithoutInit()
                            }
                        )
                    }

                    if (trackingProtectionEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.ui_cross_site_cookies), fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            SuggestionChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.ui_cryptominers), fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            SuggestionChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.ui_fingerprinters), fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        var isForgetful by remember {
                            mutableStateOf(com.petal.browser.privacy.ForgetfulBrowsingManager.isDomainForgetful(context, domain))
                        }
                        com.petal.browser.ui.containment.PetalSettingsToggleRow(
                            position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                            title = "Forgetful Browsing",
                            subtitle = "Clear cookies, storage and cache when tab is closed",
                            icon = Icons.Rounded.DeleteSweep,
                            checked = isForgetful,
                            onCheckedChange = { checked ->
                                isForgetful = checked
                                com.petal.browser.privacy.ForgetfulBrowsingManager.setDomainForgetful(context, domain, checked)
                            }
                        )

                        Spacer(Modifier.height(8.dp))
                        var showBlockElementDialog by remember { mutableStateOf(false) }
                        var customCssSelector by remember { mutableStateOf("") }
                        OutlinedButton(
                            onClick = { showBlockElementDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Block Page Element (Cosmetic Filter)", fontSize = 12.sp)
                        }

                        if (showBlockElementDialog) {
                            com.petal.browser.ui.containment.PetalMaterialAlertDialog(
                                onDismissRequest = { showBlockElementDialog = false },
                                title = { Text("Block Element on $domain", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Enter a CSS selector (e.g. '.newsletter-modal', '#paywall-banner') to permanently hide it from this domain.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = customCssSelector,
                                            onValueChange = { customCssSelector = it },
                                            placeholder = { Text("e.g. .popup-overlay") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (customCssSelector.isNotBlank()) {
                                                com.petal.browser.privacy.PetalElementBlocker.addSelector(context, domain, customCssSelector.trim())
                                                geckoView?.reloadWithoutInit()
                                                showBlockElementDialog = false
                                            }
                                        },
                                        enabled = customCssSelector.isNotBlank()
                                    ) {
                                        Text("Block")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showBlockElementDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mode for this site:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilterChip(
                                selected = siteShieldMode == com.petal.browser.browser.PetalAdBlockMode.STANDARD,
                                onClick = {
                                    siteShieldMode = com.petal.browser.browser.PetalAdBlockMode.STANDARD
                                    com.petal.browser.browser.PetalAdBlockEngine.setDomainOverride(context, domain, com.petal.browser.browser.PetalAdBlockMode.STANDARD)
                                    geckoView?.reloadWithoutInit()
                                },
                                label = { Text("Standard", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            FilterChip(
                                selected = siteShieldMode == com.petal.browser.browser.PetalAdBlockMode.AGGRESSIVE,
                                onClick = {
                                    siteShieldMode = com.petal.browser.browser.PetalAdBlockMode.AGGRESSIVE
                                    com.petal.browser.browser.PetalAdBlockEngine.setDomainOverride(context, domain, com.petal.browser.browser.PetalAdBlockMode.AGGRESSIVE)
                                    geckoView?.reloadWithoutInit()
                                },
                                label = { Text("Aggressive", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
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
                                    CookieManager.getInstance().removeAllCookies(null)
                                    CookieManager.getInstance().flush()
                                    WebStorage.getInstance().deleteAllData()
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
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
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
                                        WebStorage.getInstance().deleteAllData()
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
                            HelperUnit.grantPermissionsCamera(context)
                        }
                        geckoView?.reloadWithoutInit()
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Cookie6Sided.toShape(),
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
                            HelperUnit.grantPermissionsMic(context)
                        }
                        geckoView?.reloadWithoutInit()
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Clover4Leaf.toShape(),
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
                            HelperUnit.grantPermissionsLoc(context)
                        } else if (!allowed) {
                            try {
                                GeolocationPermissions.getInstance().clear(domain)
                            } catch (ignored: Exception) {}
                        }
                        geckoView?.reloadWithoutInit()
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Sunny.toShape(),
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
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.SoftBurst.toShape(),
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
                        geckoView?.setDesktopMode(allowed)
                    },
                    leadingIcon = {
                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Sunny.toShape(),
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
