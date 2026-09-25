/*
 * PetalConfigSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * petal:config / omni:config Advanced Engine Flags & Preferences Viewer.
 * Exposes GeckoView runtime flags and internal engine toggles.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager

data class ConfigFlag(
    val key: String,
    val title: String,
    val description: String,
    val defaultValue: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalConfigSheet(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var searchQuery by remember { mutableStateOf("") }

    val flags = remember {
        listOf(
            ConfigFlag("sp_javascript", "javascript.enabled", "Enable or disable JavaScript execution", true),
            ConfigFlag("sp_webgl", "webgl.enabled", "Hardware-accelerated 3D WebGL graphics pipeline", true),
            ConfigFlag("sp_webrtc_protection", "media.peerconnection.enabled", "WebRTC IP leak shield & candidate gathering", true),
            ConfigFlag("sp_dnt_gpc", "privacy.globalprivacycontrol.enabled", "Do Not Track and Global Privacy Control signals", true),
            ConfigFlag("sp_https_only", "dom.security.https_only_mode", "Enforce HTTPS encryption on all connections", true),
            ConfigFlag("sp_cookies_isolate", "network.cookie.cookieBehavior", "Strict first-party cookie isolation (Total Cookie Protection)", true),
            ConfigFlag("sp_high_refresh_rate", "layout.frame_rate", "Force 90Hz / 120Hz display refresh rate", true),
            ConfigFlag("sp_force_zoom", "browser.viewport.force_zoom", "Allow pinch-to-zoom on sites with viewport restrictions", true),
            ConfigFlag("sp_native_video_player", "media.native_player.enabled", "Route web videos into native hardware ExoPlayer", true),
            ConfigFlag("sp_media_detect_background", "media.sniffer.background_detection", "Silently detect playable streams without interrupting", true),
            ConfigFlag("sp_ai_blocker", "privacy.ai_blocker.enabled", "Strip generative AI overviews from search engines", true),
            ConfigFlag("sp_fingerprint_protection", "privacy.resistFingerprinting", "Resist browser canvas, audio, and font fingerprinting", true)
        )
    }

    val filteredFlags = remember(searchQuery) {
        if (searchQuery.isBlank()) flags else flags.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.key.contains(searchQuery, ignoreCase = true)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "petal:config",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "GeckoView Engine Flags & Internal Preferences",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search flags...", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFlags, key = { it.key }) { flag ->
                    var isEnabled by remember {
                        mutableStateOf(sp.getBoolean(flag.key, flag.defaultValue))
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = flag.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = flag.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    isEnabled = it
                                    sp.edit().putBoolean(flag.key, it).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Programmatic bridge to open [PetalConfigSheet] from non-Compose contexts (e.g. URL interception).
 * Follows the BottomSheetDialog pattern used by other Petal sheets.
 */
object PetalConfigSheet {
    @JvmStatic
    fun show(activity: androidx.activity.ComponentActivity) {
        try {
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.let { win ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", "") ?: ""
                    val dynamicColor = sp.getBoolean("useDynamicColor", false)
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val appFont = remember(fontName) {
                        com.petal.browser.ui.theme.AppFont.fromName(fontName)
                    }
                    val colorStyle = remember(styleName) {
                        try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) }
                        catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                    }
                    com.petal.browser.ui.theme.PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        PetalConfigSheet(onDismissRequest = {
                            try { dialog.dismiss() } catch (_: Exception) {}
                        })
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
