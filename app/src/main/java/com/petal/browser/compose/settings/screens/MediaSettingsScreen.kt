/*
 * MediaSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Media & Sniffer Settings Screen for Petal Browser.
 * Implements MEDIA & SYNC & ECOSYSTEM categories matching official Firefox
 * GeckoView & Omni Browser architecture.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.account.mozilla.FirefoxAccountSyncScreen
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground

@Composable
fun MediaSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var nativeVideoPlayer by remember { mutableStateOf(sp.getBoolean("sp_native_video_player", false)) }
    var detectBackground by remember { mutableStateOf(sp.getBoolean("sp_media_detect_background", true)) }
    var showMediaButton by remember { mutableStateOf(sp.getBoolean("sp_media_button_address_bar", true)) }
    var autoOpenPanel by remember { mutableStateOf(sp.getBoolean("sp_media_auto_open_panel", false)) }
    var validateStreams by remember { mutableStateOf(sp.getBoolean("sp_media_validate_streams", true)) }
    var aiBlocker by remember { mutableStateOf(sp.getBoolean("sp_ai_blocker", sp.getBoolean("petal_builtin_ai_blocker", true))) }

    var showSyncScreen by remember { mutableStateOf(false) }

    if (showSyncScreen) {
        FirefoxAccountSyncScreen(
            onBack = { showSyncScreen = false }
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "media_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Media & Sync",
                subtitle = "Video player, stream sniffer, offline AI & Firefox Sync ecosystem",
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
                // ── Section 1: MEDIA ──────────────────────────────────────────
                SettingsCategoryCard(
                    title = "Media Engine",
                    icon = Icons.Rounded.VideoLibrary,
                    cardId = "media_engine",
                    targetHighlightId = targetHighlightItemId
                ) {
                    // Download Settings navigation row
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                onNavigateToDownloads()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download Settings",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Engine, auto-preview, multi-part & external downloaders",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Native Video Player Toggle
                    ToggleRow(
                        title = "Native Video Player",
                        subtitle = "Bypass web player and launch streams directly into hardware-accelerated ExoPlayer with gestures & background audio",
                        icon = Icons.Rounded.PlayCircle,
                        checked = nativeVideoPlayer,
                        onCheckedChange = { checked ->
                            nativeVideoPlayer = checked
                            sp.edit().putBoolean("sp_native_video_player", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Media Sniffer / Fetcher
                    ToggleRow(
                        title = "Detect Media in Background",
                        subtitle = "Continuously sniff video/audio streams and M3U8/MPD playlists without interrupting browsing",
                        icon = Icons.Rounded.Sensors,
                        checked = detectBackground,
                        onCheckedChange = { checked ->
                            detectBackground = checked
                            sp.edit().putBoolean("sp_media_detect_background", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Show Media Button in address bar
                    ToggleRow(
                        title = "Show Media Button",
                        subtitle = "Display quick-access media sniffer button on the address bar when playable streams are found",
                        icon = Icons.Rounded.SmartDisplay,
                        checked = showMediaButton,
                        onCheckedChange = { checked ->
                            showMediaButton = checked
                            sp.edit().putBoolean("sp_media_button_address_bar", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Automatically open media panel
                    ToggleRow(
                        title = "Automatically Open Media Panel",
                        subtitle = "Pop up the media fetcher sheet automatically when new video sources are extracted",
                        icon = Icons.Rounded.OpenInNew,
                        checked = autoOpenPanel,
                        onCheckedChange = { checked ->
                            autoOpenPanel = checked
                            sp.edit().putBoolean("sp_media_auto_open_panel", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Validate media before showing
                    ToggleRow(
                        title = "Validate Media Before Showing",
                        subtitle = "Perform lightweight HEAD check on sniffing URLs to filter out expired or non-playable links",
                        icon = Icons.Rounded.Verified,
                        checked = validateStreams,
                        onCheckedChange = { checked ->
                            validateStreams = checked
                            sp.edit().putBoolean("sp_media_validate_streams", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // AI Blocker Toggle
                    ToggleRow(
                        title = "AI Blocker",
                        subtitle = "Automatically clean search results by stripping cluttered generative AI overviews and promoted summaries",
                        icon = Icons.Rounded.Block,
                        checked = aiBlocker,
                        onCheckedChange = { checked ->
                            aiBlocker = checked
                            sp.edit()
                                .putBoolean("sp_ai_blocker", checked)
                                .putBoolean("petal_builtin_ai_blocker", checked)
                                .apply()
                            com.petal.browser.extensions.PetalBuiltInExtensionManager.setEnabled(
                                context, "petal_builtin_ai_blocker", checked
                            )
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )
                }

                // ── Section 2: SYNC & ECOSYSTEM ──────────────────────────────
                SettingsCategoryCard(
                    title = "Sync & Ecosystem",
                    icon = Icons.Rounded.Sync,
                    cardId = "sync_ecosystem",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                                showSyncScreen = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Petal Sync",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = "Experimental",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Zero-cloud end-to-end encrypted (E2EE) sync across devices, powered by Firefox Accounts & Mozilla Sync protocol",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
