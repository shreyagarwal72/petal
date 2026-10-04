/*
 * PetalPrivacyShieldSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Privacy & Tracker Shield HUD sheet for Petal Browser.
 * Redesigned with Petal Containment System (PetalSettingsSection, PetalGroup,
 * PetalGroupRow, PetalGroupToggleRow, PetalGroupControlRow, PetalSettingsToggleRow,
 * PetalHeroCard, M3ExpressiveVariableBackground) and live multi-toggle control
 * for AdBlock, Whitelist, Third-Party Cookie blocking, WebRTC leak shield,
 * Canvas/Audio anti-fingerprinting, DNT/GPC, and HTTPS-Only mode.
 */

package com.petal.browser.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WifiProtectedSetup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.R
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalContainmentShapes
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalHeroCard
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalPrivacyShieldSheet {

    @JvmStatic
    fun show(activity: ComponentActivity, pageUrl: String, onToggleAdBlock: (Boolean) -> Unit) {
        val host = try {
            Uri.parse(pageUrl).host ?: ""
        } catch (_: Exception) {
            ""
        }
        val cleanHost = host.removePrefix("www.")
        val isHttps = pageUrl.startsWith("https://", ignoreCase = true)
        val blockedCount = PetalAdBlockEngine.getBlockedCountForDomain(cleanHost)
        val totalBlocked = PetalAdBlockEngine.getTotalBlockedCount()

        val dialog = BottomSheetDialog(activity, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.window?.let { w ->
            w.setDimAmount(0.40f)
            w.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                var themeConfigStr by remember { mutableStateOf(sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM") }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId) }
                var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

                androidx.compose.runtime.DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_theme_config" -> themeConfigStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                            "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val darkTheme = remember(themeConfigStr, isSystemDark) {
                    val config = try { com.petal.browser.ui.theme.ThemeConfig.valueOf(themeConfigStr) } catch (_: Exception) { com.petal.browser.ui.theme.ThemeConfig.FOLLOW_SYSTEM }
                    when (config) {
                        com.petal.browser.ui.theme.ThemeConfig.FOLLOW_SYSTEM -> isSystemDark
                        com.petal.browser.ui.theme.ThemeConfig.LIGHT -> false
                        com.petal.browser.ui.theme.ThemeConfig.DARK -> true
                    }
                }
                val appFont = remember(fontName) {
                    AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try {
                        ColorStyle.valueOf(styleName)
                    } catch (_: Exception) {
                        ColorStyle.TONAL_SPOT
                    }
                }

                PetalExpressiveTheme(
                    darkTheme = darkTheme,
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    expressiveColors = isExpressiveColors,
                    appFont = appFont,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PrivacyShieldContent(
                        activity = activity,
                        domain = cleanHost.ifEmpty { "Current Website" },
                        pageUrl = pageUrl,
                        isHttps = isHttps,
                        blockedCount = blockedCount,
                        totalBlocked = totalBlocked,
                        onDismiss = { dialog.dismiss() },
                        onReloadRequested = {
                            if (activity is com.petal.browser.activity.BrowserActivity) {
                                val current = activity.currentAlbumController
                                if (current is com.petal.browser.browser.PetalTabViewController) {
                                    current.reload()
                                } else if (current is com.petal.browser.view.PetalGeckoView) {
                                    current.initPreferences(pageUrl)
                                    current.reload()
                                }
                            }
                        },
                        onToggleAdBlock = onToggleAdBlock
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
    }

    @Composable
    private fun PrivacyShieldContent(
        activity: ComponentActivity,
        domain: String,
        pageUrl: String,
        isHttps: Boolean,
        blockedCount: Int,
        totalBlocked: Long,
        onDismiss: () -> Unit,
        onReloadRequested: () -> Unit,
        onToggleAdBlock: (Boolean) -> Unit
    ) {
        val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
        val cleanHost = remember(domain) { domain.removePrefix("www.") }

        // Reactive Preference States
        var isWhitelisted by remember {
            mutableStateOf(PetalAdBlockEngine.isDomainWhitelisted(cleanHost))
        }
        var globalAdBlock by remember {
            mutableStateOf(sp.getBoolean("sp_ad_block", sp.getBoolean("profileStandard_adBlock", true)))
        }
        var blockCookies by remember {
            mutableStateOf(sp.getBoolean("sp_block_third_party_cookies", !sp.getBoolean("profileStandard_cookiesThirdParty", false)))
        }
        var fingerprintProtection by remember {
            mutableStateOf(sp.getBoolean("sp_fingerprint_protection", sp.getBoolean("profileStandard_fingerPrintProtection", true)))
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

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // ── Header Row ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        IconButton(
                            onClick = {
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                onReloadRequested()
                            }
                        ) {
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

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Hero Stat Cards ──────────────────────────────────────
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

                    // ── Connection Security Banner ───────────────────────────
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

                    // ── Site Protections Section ─────────────────────────────
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
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                if (checked) {
                                    PetalAdBlockEngine.addDomainToWhitelist(activity, cleanHost)
                                } else {
                                    PetalAdBlockEngine.removeDomainFromWhitelist(activity, cleanHost)
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
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                                sp.edit()
                                    .putBoolean("sp_ad_block", checked)
                                    .putBoolean("profileStandard_adBlock", checked)
                                    .apply()
                                PetalAdBlockEngine.setAdBlockEnabled(activity, checked)
                                PetalGeckoRuntime.syncPreferences(sp)
                                onToggleAdBlock(checked)
                                onReloadRequested()
                            },
                            position = PetalGroupPosition.BOTTOM
                        )
                    }

                    // ── Advanced Privacy Shields Section ─────────────────────
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
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
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
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
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
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
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
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
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
                                PetalHapticEngine.getInstance(activity).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
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
            }
        }
    }
}
