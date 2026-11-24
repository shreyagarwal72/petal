/*
 * MediaSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Media & Sniffer Settings Screen for Petal Browser.
 * Implements MEDIA ENGINE management suite.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.settings.screens

import android.content.Context
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import kotlinx.coroutines.launch

@Composable
fun MediaSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // ── Media Engine States ──
    var nativeVideoPlayer by remember { mutableStateOf(sp.getBoolean("sp_native_video_player", false)) }
    var backgroundPlay by remember { mutableStateOf(sp.getBoolean("sp_background_play", false)) }
    var autoPip by remember { mutableStateOf(sp.getBoolean("sp_auto_pip", true)) }
    var detectBackground by remember { mutableStateOf(sp.getBoolean("sp_media_detect_background", true)) }
    var showMediaButton by remember { mutableStateOf(sp.getBoolean("sp_media_button_address_bar", true)) }
    var autoOpenPanel by remember { mutableStateOf(sp.getBoolean("sp_media_auto_open_panel", false)) }
    var validateStreams by remember { mutableStateOf(sp.getBoolean("sp_media_validate_streams", true)) }
    var playerStyle by remember { mutableStateOf(sp.getString("sp_native_player_style", "LIQUID_GLASS") ?: "LIQUID_GLASS") }

    Box(modifier = modifier.fillMaxSize()) {
        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            pageBackground = { com.petal.browser.ui.components.PetalPageBackground(pageKey = "media_settings", modifier = Modifier.fillMaxSize()) },
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = "Media & Playback",
                    subtitle = "Video player, media sniffer & picture-in-picture",
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
                // ── Section 1: MEDIA ENGINE ──────────────────────────────────────────
                PetalSettingsSection(
                    title = stringResource(R.string.ui_media_engine),
                    icon = Icons.Rounded.VideoLibrary,
                    cardId = "media_engine",
                    targetHighlightId = targetHighlightItemId
                ) {
                    // Native Video Player Toggle
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.TOP,
                        title = stringResource(R.string.ui_native_video_player),
                        subtitle = stringResource(R.string.ui_bypass_web_player_and_launch),
                        icon = Icons.Rounded.PlayCircle,
                        checked = nativeVideoPlayer,
                        onCheckedChange = { checked ->
                            nativeVideoPlayer = checked
                            sp.edit().putBoolean("sp_native_video_player", checked).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )

                    // Media Player Style
                    val isLiquidGlass = playerStyle == "LIQUID_GLASS"
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.MIDDLE,
                        title = stringResource(R.string.ui_media_player_style),
                        subtitle = if (isLiquidGlass) stringResource(R.string.ui_style_liquid_glass) else stringResource(R.string.ui_style_material_expressive),
                        icon = if (isLiquidGlass) Icons.Rounded.BlurOn else Icons.Rounded.Palette,
                        checked = isLiquidGlass,
                        onCheckedChange = { checked ->
                            val newStyle = if (checked) "LIQUID_GLASS" else "EXPRESSIVE"
                            playerStyle = newStyle
                            sp.edit().putString("sp_native_player_style", newStyle).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )

                    // Media Sniffer / Fetcher
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.MIDDLE,
                        title = stringResource(R.string.ui_detect_media_in_background),
                        subtitle = stringResource(R.string.ui_continuously_sniff_video_audio_streams),
                        icon = Icons.Rounded.Sensors,
                        checked = detectBackground,
                        onCheckedChange = { checked ->
                            detectBackground = checked
                            sp.edit().putBoolean("sp_media_detect_background", checked).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )

                    // Show Media Button in address bar
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.MIDDLE,
                        title = stringResource(R.string.ui_show_media_button),
                        subtitle = stringResource(R.string.ui_display_quick_access_media_sniffer),
                        icon = Icons.Rounded.SmartDisplay,
                        checked = showMediaButton,
                        onCheckedChange = { checked ->
                            showMediaButton = checked
                            sp.edit().putBoolean("sp_media_button_address_bar", checked).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )

                    // Automatically open media panel
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.MIDDLE,
                        title = stringResource(R.string.ui_automatically_open_media_panel),
                        subtitle = stringResource(R.string.ui_pop_up_the_media_fetcher),
                        icon = Icons.Rounded.OpenInNew,
                        checked = autoOpenPanel,
                        onCheckedChange = { checked ->
                            autoOpenPanel = checked
                            sp.edit().putBoolean("sp_media_auto_open_panel", checked).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )

                    // Validate media before showing
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.MIDDLE,
                        title = stringResource(R.string.ui_validate_media_before_showing),
                        subtitle = stringResource(R.string.ui_perform_lightweight_head_check_on),
                        icon = Icons.Rounded.Verified,
                        checked = validateStreams,
                        onCheckedChange = { checked ->
                            validateStreams = checked
                            sp.edit().putBoolean("sp_media_validate_streams", checked).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )

                    // Background Media Playback
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.MIDDLE,
                        title = "Background Media Playback",
                        subtitle = "Keep audio playing when switching apps, minimizing Petal, or locking the device",
                        icon = Icons.Rounded.GraphicEq,
                        checked = backgroundPlay,
                        onCheckedChange = { checked ->
                            backgroundPlay = checked
                            sp.edit().putBoolean("sp_background_play", checked).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )

                    // Picture-in-Picture (Auto-PiP)
                    PetalSettingsToggleRow(
                        position = PetalGroupPosition.BOTTOM,
                        title = "Picture-in-Picture (Auto-PiP)",
                        subtitle = "Automatically pop video or music into a floating mini-player when returning home",
                        icon = Icons.Rounded.PictureInPictureAlt,
                        checked = autoPip,
                        onCheckedChange = { checked ->
                            autoPip = checked
                            sp.edit().putBoolean("sp_auto_pip", checked).apply()
                            com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                        }
                    )
                }
            }
        }
    }
}
