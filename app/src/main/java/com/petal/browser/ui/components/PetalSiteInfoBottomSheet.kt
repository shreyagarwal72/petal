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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // --- Domain & Security Header ---
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PetalShapeIconBadge(
                        shape = com.petal.browser.ui.theme.PetalMaterialShapes.Sunny.toShape(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 50.dp,
                        iconSize = 26.dp
                    ) {
                        if (favicon != null) {
                            Image(
                                bitmap = favicon.asImageBitmap(),
                                contentDescription = "Site Favicon",
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
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PetalShapeIconBadge(
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
                text = "Enhanced Tracking Protection",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                        PetalShapeIconBadge(
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
                                label = { Text("Cross-site Cookies", fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Cryptominers", fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Fingerprinters", fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- Cookies & Site Data Section ---
            Text(
                text = "Cookies & Site Data",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                            text = "Stored Cookies & Cache",
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
                            label = "Reset",
                            onPrimaryClick = {
                                try {
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

                        DropdownMenu(
                            expanded = siteResetExpanded,
                            onDismissRequest = { siteResetExpanded = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Cookies Only") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Cookie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    siteResetExpanded = false
                                    try {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
                                        cookieCount = 0
                                    } catch (_: Exception) {}
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Site Permissions") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    siteResetExpanded = false
                                    try {
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
                            DropdownMenuItem(
                                text = { Text("Clear Storage & Cache") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    siteResetExpanded = false
                                    try {
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
                text = "Page Permissions",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )

            SettingsTileGroup(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                // Camera
                SettingsTileSwitchRow(
                    title = "Camera Access",
                    subtitle = if (isCameraAllowed) "Allowed" else "Blocked",
                    checked = isCameraAllowed,
                    onCheckedChange = { allowed ->
                        isCameraAllowed = allowed
                        sp.edit().putBoolean(profile + "_camera", allowed).apply()
                        if (allowed && context is android.app.Activity) {
                            HelperUnit.grantPermissionsCamera(context)
                        }
                        geckoView?.reloadWithoutInit()
                    },
                    leadingIcon = {
                        PetalShapeIconBadge(
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

                SettingsTileDivider(startPadding = 64.dp)

                // Microphone
                SettingsTileSwitchRow(
                    title = "Microphone Access",
                    subtitle = if (isMicAllowed) "Allowed" else "Blocked",
                    checked = isMicAllowed,
                    onCheckedChange = { allowed ->
                        isMicAllowed = allowed
                        sp.edit().putBoolean(profile + "_microphone", allowed).apply()
                        if (allowed && context is android.app.Activity) {
                            HelperUnit.grantPermissionsMic(context)
                        }
                        geckoView?.reloadWithoutInit()
                    },
                    leadingIcon = {
                        PetalShapeIconBadge(
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

                SettingsTileDivider(startPadding = 64.dp)

                // Location
                SettingsTileSwitchRow(
                    title = "Location Access",
                    subtitle = if (isLocationAllowed) "Allowed" else "Blocked",
                    checked = isLocationAllowed,
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
                        PetalShapeIconBadge(
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

                SettingsTileDivider(startPadding = 64.dp)

                // Notifications
                SettingsTileSwitchRow(
                    title = "Notifications",
                    subtitle = if (isNotificationsAllowed) "Allowed" else "Blocked",
                    checked = isNotificationsAllowed,
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
                        PetalShapeIconBadge(
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

                SettingsTileDivider(startPadding = 64.dp)

                // Desktop Site (Per-site override)
                SettingsTileSwitchRow(
                    title = "Desktop Site",
                    subtitle = if (isDesktopSite) "Requesting desktop version" else "Mobile version",
                    checked = isDesktopSite,
                    onCheckedChange = { allowed ->
                        isDesktopSite = allowed
                        if (domain.isNotEmpty()) {
                            sp.edit().putBoolean("sp_desktop_site_$domain", allowed).apply()
                        }
                        geckoView?.setDesktopMode(allowed)
                    },
                    leadingIcon = {
                        PetalShapeIconBadge(
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
