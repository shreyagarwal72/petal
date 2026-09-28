/*

import com.petal.browser.ui.containment.PetalSettingsSection
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
import com.petal.browser.ui.containment.PetalSettingsSection
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

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
                PetalSettingsSection(
                    title = stringResource(R.string.ui_media_engine),
                    icon = Icons.Rounded.VideoLibrary,
                    cardId = "media_engine",
                    targetHighlightId = targetHighlightItemId
                ) {
                    // Native Video Player Toggle
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_native_video_player),
                        subtitle = stringResource(R.string.ui_bypass_web_player_and_launch),
                        icon = Icons.Rounded.PlayCircle,
                        checked = nativeVideoPlayer,
                        onCheckedChange = { checked ->
                            nativeVideoPlayer = checked
                            sp.edit().putBoolean("sp_native_video_player", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Media Sniffer / Fetcher
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_detect_media_in_background),
                        subtitle = stringResource(R.string.ui_continuously_sniff_video_audio_streams),
                        icon = Icons.Rounded.Sensors,
                        checked = detectBackground,
                        onCheckedChange = { checked ->
                            detectBackground = checked
                            sp.edit().putBoolean("sp_media_detect_background", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Show Media Button in address bar
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_show_media_button),
                        subtitle = stringResource(R.string.ui_display_quick_access_media_sniffer),
                        icon = Icons.Rounded.SmartDisplay,
                        checked = showMediaButton,
                        onCheckedChange = { checked ->
                            showMediaButton = checked
                            sp.edit().putBoolean("sp_media_button_address_bar", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Automatically open media panel
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_automatically_open_media_panel),
                        subtitle = stringResource(R.string.ui_pop_up_the_media_fetcher),
                        icon = Icons.Rounded.OpenInNew,
                        checked = autoOpenPanel,
                        onCheckedChange = { checked ->
                            autoOpenPanel = checked
                            sp.edit().putBoolean("sp_media_auto_open_panel", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // Validate media before showing
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_validate_media_before_showing),
                        subtitle = stringResource(R.string.ui_perform_lightweight_head_check_on),
                        icon = Icons.Rounded.Verified,
                        checked = validateStreams,
                        onCheckedChange = { checked ->
                            validateStreams = checked
                            sp.edit().putBoolean("sp_media_validate_streams", checked).apply()
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )

                    // AI Blocker Toggle
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_ai_blocker),
                        subtitle = stringResource(R.string.ui_automatically_clean_search_results_by),
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
                PetalSettingsSection(
                    title = stringResource(R.string.ui_sync_ecosystem),
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
                                        text = stringResource(R.string.ui_petal_sync),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_experimental),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.ui_zero_cloud_end_to_end),
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
