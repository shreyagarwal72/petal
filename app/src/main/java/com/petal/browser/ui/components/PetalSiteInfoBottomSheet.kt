package com.petal.browser.ui.components

import android.graphics.Bitmap
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
import com.petal.browser.view.NinjaWebView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSiteInfoBottomSheet(
    webView: NinjaWebView?,
    onDismissRequest: () -> Unit,
    onResetSiteData: () -> Unit
) {
    val context = LocalContext.current
    val currentUrl = webView?.url ?: ""
    val domain = remember(currentUrl) { HelperUnit.domain(currentUrl) }
    val favicon: Bitmap? = webView?.favicon

    val sslCertificate: SslCertificate? = webView?.certificate
    val isHttps = currentUrl.startsWith("https://")
    val isSecure = isHttps && sslCertificate != null

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
    val profile = remember { NinjaWebView.getProfile() }

    var isCameraAllowed by remember { mutableStateOf(sp.getBoolean(profile + "_camera", false)) }
    var isMicAllowed by remember { mutableStateOf(sp.getBoolean(profile + "_microphone", false)) }
    var isLocationAllowed by remember { mutableStateOf(sp.getBoolean(profile + "_location", false)) }
    var isNotificationsAllowed by remember { mutableStateOf(sp.getBoolean("sp_notifications_$domain", true)) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (favicon != null) {
                            Image(
                                bitmap = favicon.asImageBitmap(),
                                contentDescription = "Site Favicon",
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
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
                            text = if (isSecure) "Connection is secure" else if (isHttps) "SSL Encrypted" else "Connection not secure",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSecure || isHttps) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSecure || isHttps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSecure || isHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSecure) "Valid Security Certificate" else if (isHttps) "HTTPS Encrypted Connection" else "Unencrypted Connection",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val certDetails = remember(sslCertificate) {
                            if (sslCertificate != null) {
                                "Issued to: ${sslCertificate.issuedTo.cName}\nIssued by: ${sslCertificate.issuedBy.oName}"
                            } else if (isHttps) {
                                "Your information is private when sent to this site."
                            } else {
                                "You should not enter sensitive info on this site."
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

                    OutlinedButton(
                        onClick = {
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
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Camera
                SwitchSettingItem(
                    title = "Camera Access",
                    subtitle = if (isCameraAllowed) "Allowed" else "Blocked",
                    checked = isCameraAllowed,
                    onCheckedChange = { allowed ->
                        isCameraAllowed = allowed
                        sp.edit().putBoolean(profile + "_camera", allowed).apply()
                        if (allowed && context is android.app.Activity) {
                            HelperUnit.grantPermissionsCamera(context)
                        }
                        webView?.reloadWithoutInit()
                    },
                    shape = getGroupItemShape(0, 4),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                )

                // Microphone
                SwitchSettingItem(
                    title = "Microphone Access",
                    subtitle = if (isMicAllowed) "Allowed" else "Blocked",
                    checked = isMicAllowed,
                    onCheckedChange = { allowed ->
                        isMicAllowed = allowed
                        sp.edit().putBoolean(profile + "_microphone", allowed).apply()
                        if (allowed && context is android.app.Activity) {
                            HelperUnit.grantPermissionsMic(context)
                        }
                        webView?.reloadWithoutInit()
                    },
                    shape = getGroupItemShape(1, 4),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                )

                // Location
                SwitchSettingItem(
                    title = "Location Access",
                    subtitle = if (isLocationAllowed) "Allowed" else "Blocked",
                    checked = isLocationAllowed,
                    onCheckedChange = { allowed ->
                        isLocationAllowed = allowed
                        sp.edit().putBoolean(profile + "_location", allowed).apply()
                        webView?.getSettings()?.setGeolocationEnabled(allowed)
                        if (allowed && context is android.app.Activity) {
                            HelperUnit.grantPermissionsLoc(context)
                        } else if (!allowed) {
                            try {
                                GeolocationPermissions.getInstance().clear(domain)
                            } catch (ignored: Exception) {}
                        }
                        webView?.reloadWithoutInit()
                    },
                    shape = getGroupItemShape(2, 4),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                )

                // Notifications
                SwitchSettingItem(
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
                    shape = getGroupItemShape(3, 4),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
