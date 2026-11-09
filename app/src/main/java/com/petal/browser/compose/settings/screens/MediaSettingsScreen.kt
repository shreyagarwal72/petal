/*
 * MediaSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Media & Sniffer Settings Screen for Petal Browser.
 * Implements MEDIA ENGINE & LIVE ACTIONS CUTOUT ISLAND management suite.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.settings.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.petal.browser.ui.kit.TextButton
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
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.island.*
import com.petal.browser.compose.downloads.LiveUpdateNotificationManager
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalMaterialAlertDialog
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import kotlinx.coroutines.launch
import java.util.Locale

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

    // ── Expressive Cutout Island States ──
    var liveUpdates by remember { mutableStateOf(sp.getBoolean(IslandSettings.KEY_LIVE_UPDATES, true)) }
    var islandMode by remember { mutableStateOf((sp.getString(IslandSettings.KEY_MODE, IslandSettings.MODE_AUTO) ?: IslandSettings.MODE_AUTO).uppercase(Locale.US)) }
    var animStyle by remember { mutableStateOf(sp.getString(IslandSettings.KEY_STYLE, "EXPRESSIVE") ?: "EXPRESSIVE") }
    var animSpeed by remember { mutableStateOf(sp.getString(IslandSettings.KEY_SPEED, "DEFAULT") ?: "DEFAULT") }
    var animBounce by remember { mutableStateOf(sp.getString(IslandSettings.KEY_BOUNCE, "NORMAL") ?: "NORMAL") }
    var wavyProgress by remember { mutableStateOf(sp.getBoolean(IslandSettings.KEY_WAVY, true)) }
    var overlayEnabled by remember { mutableStateOf(sp.getBoolean(IslandSettings.KEY_OVERLAY, false)) }

    // ── Placement & Geometry States ──
    var alignmentMode by remember { mutableStateOf((sp.getString(IslandSettings.KEY_ALIGNMENT, IslandSettings.ALIGN_AUTO) ?: IslandSettings.ALIGN_AUTO).uppercase(Locale.US)) }
    var offsetXDp by remember { mutableIntStateOf(sp.getInt(IslandSettings.KEY_OFFSET_X, 0)) }
    var offsetYDp by remember { mutableIntStateOf(sp.getInt(IslandSettings.KEY_OFFSET_Y, 0)) }
    var isNotch by remember { mutableStateOf(sp.getBoolean(IslandSettings.KEY_IS_NOTCH, false)) }
    var notchFlareDp by remember { mutableIntStateOf(sp.getInt(IslandSettings.KEY_NOTCH_FLARE, 20)) }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPromotedSettingsDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            liveUpdates = true
            sp.edit().putBoolean(IslandSettings.KEY_LIVE_UPDATES, true).apply()
            if (Build.VERSION.SDK_INT >= 36 && !LiveUpdateNotificationManager.canPostPromotedNotifications(context)) {
                showPromotedSettingsDialog = true
            }
        } else {
            liveUpdates = false
            sp.edit().putBoolean(IslandSettings.KEY_LIVE_UPDATES, false).apply()
        }
    }

    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "media_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Media & Petal NowBar",
                subtitle = "Video player, media sniffer, live actions & Petal NowBar",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState, flingBehavior = com.petal.browser.ui.theme.layout.petalFlingBehavior())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
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
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        }
                    )
                }

                // ── Section 2: PETAL NOWBAR & LIVE ACTIONS ──────────────
                PetalSettingsSection(
                    title = "Petal NowBar & Live Actions",
                    icon = Icons.Rounded.DynamicFeed,
                    cardId = "live_actions_cutout_island",
                    targetHighlightId = targetHighlightItemId
                ) {
                    // Master Toggle
                    PetalGroup(rowCount = 1) { _, rowPosition ->
                        PetalSettingsToggleRow(
                            title = stringResource(R.string.ui_live_updates_alerts),
                            subtitle = "Promoted status bar chip on Android 16 & Petal NowBar on non-supported devices",
                            icon = Icons.Rounded.NotificationsActive,
                            checked = liveUpdates && notificationsAllowed,
                            position = rowPosition,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            liveUpdates = true
                                            sp.edit().putBoolean(IslandSettings.KEY_LIVE_UPDATES, true).apply()
                                            if (Build.VERSION.SDK_INT >= 36 && !LiveUpdateNotificationManager.canPostPromotedNotifications(context)) {
                                                showPromotedSettingsDialog = true
                                            }
                                        } else {
                                            showPermissionDialog = true
                                        }
                                    } else {
                                        liveUpdates = true
                                        sp.edit().putBoolean(IslandSettings.KEY_LIVE_UPDATES, true).apply()
                                    }
                                } else {
                                    liveUpdates = false
                                    sp.edit().putBoolean(IslandSettings.KEY_LIVE_UPDATES, false).apply()
                                }
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            }
                        )
                    }

                    if (liveUpdates && notificationsAllowed) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Over other apps (overlay mode)
                        var overlayGranted by remember { mutableStateOf(PetalIslandPermissions.canDrawOverlays(context)) }
                        DisposableEffect(Unit) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    overlayGranted = PetalIslandPermissions.canDrawOverlays(context)
                                    if (!overlayGranted && overlayEnabled) {
                                        overlayEnabled = false
                                        sp.edit().putBoolean(IslandSettings.KEY_OVERLAY, false).apply()
                                    }
                                }
                            }
                            val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
                            lifecycle?.addObserver(observer)
                            onDispose { lifecycle?.removeObserver(observer) }
                        }

                        PetalGroup(rowCount = 1) { _, rowPosition ->
                            PetalSettingsToggleRow(
                                title = "Show over other apps",
                                subtitle = if (overlayGranted) "Floating live pill appears when switching to other apps"
                                           else "Requires 'Display over other apps' permission",
                                icon = Icons.Rounded.Layers,
                                checked = overlayEnabled && overlayGranted,
                                position = rowPosition,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        if (PetalIslandPermissions.canDrawOverlays(context)) {
                                            overlayEnabled = true
                                            sp.edit().putBoolean(IslandSettings.KEY_OVERLAY, true).apply()
                                        } else {
                                            context.startActivity(PetalIslandPermissions.overlaySettingsIntent(context))
                                        }
                                    } else {
                                        overlayEnabled = false
                                        sp.edit().putBoolean(IslandSettings.KEY_OVERLAY, false).apply()
                                    }
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Interactive Live Preview
                        IslandAnimationPreviewCard(
                            style = animStyle,
                            speed = animSpeed,
                            bounce = animBounce,
                            wavy = wavyProgress,
                            isNotch = isNotch,
                            notchFlare = notchFlareDp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Placement & Geometry Calibration ──
                        Text(
                            text = "NowBar Alignment & Placement",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )

                        // Alignment Mode (Auto / Center / Left / Right)
                        PetalGroup(rowCount = 4) { index, position ->
                            val modes = listOf(
                                Triple(IslandSettings.ALIGN_AUTO, "Auto (Camera Cutout)", Icons.Rounded.Camera),
                                Triple(IslandSettings.ALIGN_CENTER, "Center", Icons.Rounded.FormatAlignCenter),
                                Triple(IslandSettings.ALIGN_LEFT, "Left Hole Punch", Icons.Rounded.FormatAlignLeft),
                                Triple(IslandSettings.ALIGN_RIGHT, "Right Hole Punch", Icons.Rounded.FormatAlignRight)
                            )
                            val (key, label, icon) = modes[index]
                            val isSelected = alignmentMode == key

                            com.petal.browser.ui.containment.PetalGroupRow(
                                icon = icon,
                                title = label,
                                position = position,
                                onClick = {
                                    alignmentMode = key
                                    sp.edit().putString(IslandSettings.KEY_ALIGNMENT, key).apply()
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                },
                                trailing = {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            alignmentMode = key
                                            sp.edit().putString(IslandSettings.KEY_ALIGNMENT, key).apply()
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                        }
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Notch Mode vs Floating Pill
                        PetalGroup(rowCount = 1) { _, position ->
                            PetalSettingsToggleRow(
                                title = "Notch Silhouette Mode",
                                subtitle = "Hang flush from the top edge with concave flare ears",
                                icon = Icons.Rounded.VerticalAlignTop,
                                checked = isNotch,
                                position = position,
                                onCheckedChange = { checked ->
                                    isNotch = checked
                                    sp.edit().putBoolean(IslandSettings.KEY_IS_NOTCH, checked).apply()
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Offset Sliders (X & Y)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Horizontal Offset X Slider
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Horizontal Fine-Tuning", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${offsetXDp}dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    PetalSlider(
                                        value = offsetXDp.toFloat(),
                                        onValueChange = {
                                            offsetXDp = it.toInt()
                                            sp.edit().putInt(IslandSettings.KEY_OFFSET_X, offsetXDp).apply()
                                        },
                                        valueRange = -100f..100f,
                                        steps = 40,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Vertical Offset Y Slider
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Vertical Margin Down", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${offsetYDp}dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    PetalSlider(
                                        value = offsetYDp.toFloat(),
                                        onValueChange = {
                                            offsetYDp = it.toInt()
                                            sp.edit().putInt(IslandSettings.KEY_OFFSET_Y, offsetYDp).apply()
                                        },
                                        valueRange = 0f..40f,
                                        steps = 20,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (isNotch) {
                                    // Notch Flare Slider
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Notch Flare Curvature", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("${notchFlareDp}dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        PetalSlider(
                                            value = notchFlareDp.toFloat(),
                                            onValueChange = {
                                                notchFlareDp = it.toInt()
                                                sp.edit().putInt(IslandSettings.KEY_NOTCH_FLARE, notchFlareDp).apply()
                                            },
                                            valueRange = 12f..36f,
                                            steps = 12,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Animation & Motion Tuning ──
                        Text(
                            text = "Motion & Dynamics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )

                        // Animation Style (Expressive Spring vs Ease In/Out)
                        PetalGroup(rowCount = 2) { index, position ->
                            val styles = listOf(
                                Pair("EXPRESSIVE", "Material 3 Expressive Springs"),
                                Pair("EASE_IN_OUT", "Standard Ease In/Out")
                            )
                            val (key, label) = styles[index]
                            val isSelected = animStyle.equals(key, ignoreCase = true)

                            com.petal.browser.ui.containment.PetalGroupRow(
                                icon = Icons.Rounded.Animation,
                                title = label,
                                position = position,
                                onClick = {
                                    animStyle = key
                                    sp.edit().putString(IslandSettings.KEY_STYLE, key).apply()
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                },
                                trailing = {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            animStyle = key
                                            sp.edit().putString(IslandSettings.KEY_STYLE, key).apply()
                                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                        }
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Speed & Bounce Row
                        PetalGroup(rowCount = 2) { index, position ->
                            if (index == 0) {
                                val speeds = listOf("SLOW", "DEFAULT", "FAST")
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.Speed,
                                    title = "Spring Velocity",
                                    position = position,
                                    onClick = {
                                        val nextIdx = (speeds.indexOf(animSpeed.uppercase(Locale.US)).coerceAtLeast(0) + 1) % speeds.size
                                        animSpeed = speeds[nextIdx]
                                        sp.edit().putString(IslandSettings.KEY_SPEED, animSpeed).apply()
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                    },
                                    trailing = {
                                        AssistChip(
                                            onClick = {
                                                val nextIdx = (speeds.indexOf(animSpeed.uppercase(Locale.US)).coerceAtLeast(0) + 1) % speeds.size
                                                animSpeed = speeds[nextIdx]
                                                sp.edit().putString(IslandSettings.KEY_SPEED, animSpeed).apply()
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                            },
                                            label = { Text(animSpeed) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = null
                                        )
                                    }
                                )
                            } else {
                                val bounces = listOf("SMALL", "NORMAL", "BIG")
                                com.petal.browser.ui.containment.PetalGroupRow(
                                    icon = Icons.Rounded.Refresh,
                                    title = "Spring Bounciness",
                                    position = position,
                                    onClick = {
                                        val nextIdx = (bounces.indexOf(animBounce.uppercase(Locale.US)).coerceAtLeast(0) + 1) % bounces.size
                                        animBounce = bounces[nextIdx]
                                        sp.edit().putString(IslandSettings.KEY_BOUNCE, animBounce).apply()
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                    },
                                    trailing = {
                                        AssistChip(
                                            onClick = {
                                                val nextIdx = (bounces.indexOf(animBounce.uppercase(Locale.US)).coerceAtLeast(0) + 1) % bounces.size
                                                animBounce = bounces[nextIdx]
                                                sp.edit().putString(IslandSettings.KEY_BOUNCE, animBounce).apply()
                                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                            },
                                            label = { Text(animBounce) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = null
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Liquid Wavy Progress Bar Toggle
                        PetalGroup(rowCount = 1) { _, position ->
                            PetalSettingsToggleRow(
                                title = "Liquid Wavy Progress Bar",
                                subtitle = "Animated sinusoidal wave along active media & download progress",
                                icon = Icons.Rounded.Waves,
                                checked = wavyProgress,
                                position = position,
                                onCheckedChange = { checked ->
                                    wavyProgress = checked
                                    sp.edit().putBoolean(IslandSettings.KEY_WAVY, checked).apply()
                                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
            title = { Text(text = stringResource(R.string.ui_enable_live_notifications)) },
            text = { Text(text = stringResource(R.string.ui_to_display_real_time_download)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text(stringResource(R.string.ui_grant_permission))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            }
        )
    }

    if (showPromotedSettingsDialog) {
        PetalMaterialAlertDialog(
            onDismissRequest = { showPromotedSettingsDialog = false },
            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
            title = { Text(text = stringResource(R.string.ui_promoted_live_updates)) },
            text = { Text(text = stringResource(R.string.ui_your_device_supports_promoted_status)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPromotedSettingsDialog = false
                        try {
                            context.startActivity(LiveUpdateNotificationManager.getLiveNotificationSettingsIntent(context))
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text(stringResource(R.string.ui_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromotedSettingsDialog = false }) {
                    Text(stringResource(R.string.ui_dismiss))
                }
            }
        )
    }
}

@Composable
private fun IslandAnimationPreviewCard(
    style: String,
    speed: String,
    bounce: String,
    wavy: Boolean,
    isNotch: Boolean = false,
    notchFlare: Int = 20
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }

    val motion = remember(style, speed, bounce) {
        val s = if ("EASE_IN_OUT".equals(style, ignoreCase = true)) IslandAnimationStyle.EASE_IN_OUT else IslandAnimationStyle.EXPRESSIVE
        val spd = when (speed.uppercase(Locale.US)) {
            "SLOW" -> IslandAnimationSpeed.SLOW
            "FAST" -> IslandAnimationSpeed.FAST
            else -> IslandAnimationSpeed.DEFAULT
        }
        val bnc = when (bounce.uppercase(Locale.US)) {
            "SMALL" -> IslandAnimationBounce.SMALL
            "BIG" -> IslandAnimationBounce.BIG
            else -> IslandAnimationBounce.NORMAL
        }
        IslandMotion(style = s, speed = spd, bounce = bnc)
    }

    val width by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 280.dp else 160.dp,
        animationSpec = motion.dp(),
        label = "previewWidth"
    )
    val height by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 96.dp else 38.dp,
        animationSpec = motion.dp(),
        label = "previewHeight"
    )
    val cornerRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 24.dp else 19.dp,
        animationSpec = motion.dp(),
        label = "previewCorner"
    )

    val previewShape: androidx.compose.ui.graphics.Shape = if (isNotch && !isExpanded) {
        NotchShape(flare = notchFlare.dp, bottomLeft = cornerRadius, bottomRight = cornerRadius)
    } else {
        RoundedCornerShape(cornerRadius)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Interactive Cutout Preview (Tap to expand)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(width, height)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .clip(previewShape)
                        .clickable {
                            scope.launch {
                                scale.animateTo(0.93f, motion.boop())
                                scale.animateTo(1f, motion.boop())
                            }
                            isExpanded = !isExpanded
                        },
                    shape = previewShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isExpanded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Now Playing",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Expressive Live Media",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        Icons.Rounded.Pause,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                PetalWavyProgressBar(
                                    progress = 0.65f,
                                    waveColor = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    isWavy = wavy,
                                    animated = wavy
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
