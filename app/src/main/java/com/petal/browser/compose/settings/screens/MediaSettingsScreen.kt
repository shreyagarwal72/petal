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

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "media_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Media",
                subtitle = "Video player, media sniffer & background detection",
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
                }
            }
        }
    }
}
