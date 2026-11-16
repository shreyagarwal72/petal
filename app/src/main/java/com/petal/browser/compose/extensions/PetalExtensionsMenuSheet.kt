/*
 * PetalExtensionsMenuSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Official Firefox/Fenix-grade in-browser Extensions Menu sheet for Petal Browser.
 *
 * Displays active Firefox WebExtensions on the current webpage, complete with live
 * toolbar badges (blocked tracker counters, status alerts), 1-tap browser action popup
 * triggers, direct options page shortcuts, and navigation to the full Add-on Manager.
 */

package com.petal.browser.compose.extensions

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalExtensionsMenuSheet {

    @JvmStatic
    @JvmOverloads
    fun show(
        activity: ComponentActivity,
        pageUrl: String,
        onOpenManager: () -> Unit
    ) {
        val host = try {
            Uri.parse(pageUrl).host.orEmpty().removePrefix("www.")
        } catch (_: Exception) {
            ""
        }

        activity.runOnUiThread {
            try {
                val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
                com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)

                val dialog = android.app.Dialog(activity, com.petal.browser.R.style.PetalContextMenuDialogTheme).apply {
                    setCanceledOnTouchOutside(true)
                    setCancelable(true)
                }
                dialog.setOnDismissListener {
                    com.petal.browser.predictive.PetalContentSnapshot.clear()
                }

                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setContent {
                        val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                        val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                        val themeConfigStr = sp.getString("sp_theme_config", "FOLLOW_SYSTEM") ?: "FOLLOW_SYSTEM"
                        val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                        val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                        val isAmoled = sp.getBoolean("sp_amoled", false)

                        val darkTheme = remember(themeConfigStr, isSystemDark) {
                            when (themeConfigStr) {
                                "LIGHT" -> false
                                "DARK" -> true
                                else -> isSystemDark
                            }
                        }
                        val appFont = remember(fontName) { AppFont.fromName(fontName) }
                        val colorStyle = remember(styleName) {
                            try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                        }

                        val hostSource = activity.findViewById<android.view.View>(com.petal.browser.R.id.main) ?: rootView
                        CompositionLocalProvider(LocalPetalGlassSource provides hostSource) {
                            PetalExpressiveTheme(
                                darkTheme = darkTheme,
                                dynamicColor = dynamicColor,
                                useAmoled = isAmoled,
                                appFont = appFont,
                                colorStyle = colorStyle,
                                paletteId = paletteId
                            ) {
                                com.petal.browser.ui.glass.PetalGlassContainer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    enabled = isLiquidGlassEnabled("sp_liquid_glass_containments"),
                                    live = true
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .navigationBarsPadding()
                                            .padding(horizontal = 16.dp, vertical = 20.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        ExtensionsMenuContent(
                                            activity = activity,
                                            domain = host.ifEmpty { "Current Page" },
                                            onDismiss = { dialog.dismiss() },
                                            onOpenManager = {
                                                dialog.dismiss()
                                                onOpenManager()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                dialog.setContentView(composeView)
                dialog.show()
            } catch (t: Throwable) {
                android.util.Log.e("PetalExtensionsMenu", "Failed to show extensions menu sheet", t)
            }
        }
    }
}

@Composable
private fun ExtensionsMenuContent(
    activity: ComponentActivity,
    domain: String,
    onDismiss: () -> Unit,
    onOpenManager: () -> Unit
) {
    val extensions by PetalExtensionManager.extensions.collectAsStateWithLifecycle()
    val activeTabActions by PetalExtensionManager.activeTabActionStates.collectAsStateWithLifecycle()

    val firefoxExtensions = remember(extensions) {
        extensions.filter { !PetalBuiltInExtensionManager.isBuiltIn(it.id) }
    }
    val enabledExtensions = remember(firefoxExtensions) { firefoxExtensions.filter { it.enabled } }

    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }

    PetalHeroCard(
        shape = PetalContainmentShapes.Hero,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: expressive badge, title, current site pill, close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PetalGroupIconBadge(Icons.Rounded.Extension, PetalBadgeVariant.PRIMARY, size = 48.dp, iconSize = 24.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_extensions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    onClick = {
                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                        onDismiss()
                    },
                    shape = petalBadgeShape("extensions_close"),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.ui_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Body
            when {
                firefoxExtensions.isEmpty() -> PetalGroupRow(
                    icon = Icons.Rounded.Add,
                    title = stringResource(R.string.ui_find_firefox_extensions),
                    subtitle = "No add-ons installed yet",
                    position = PetalGroupPosition.SINGLE,
                    onClick = {
                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.75f)
                        onOpenManager()
                    }
                )
                enabledExtensions.isEmpty() -> PetalGroupRow(
                    icon = Icons.Rounded.ExtensionOff,
                    title = "All add-ons are disabled",
                    subtitle = stringResource(R.string.ui_manage_extensions),
                    position = PetalGroupPosition.SINGLE,
                    iconContainer = MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onOpenManager
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(enabledExtensions, key = { _, item -> item.id }) { index, ext ->
                        ExtensionMenuCardRow(
                            extension = ext,
                            actionState = activeTabActions[ext.id],
                            position = petalGroupPositionFor(index, enabledExtensions.size),
                            onActionClick = {
                                haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.8f)
                                onDismiss()
                                activity.window.decorView.post {
                                    PetalExtensionManager.triggerBrowserAction(ext.id, activity)
                                }
                            },
                            onSettingsClick = {
                                haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                onDismiss()
                                activity.window.decorView.post {
                                    PetalExtensionManager.openOptionsPage(ext.id, activity)
                                }
                            }
                        )
                    }
                }
            }

            // Footer: connected buttons never wrap (single line, ellipsized)
            PetalConnectedButtonGroup(
                items = listOf(
                    PetalConnectedButtonItem(stringResource(R.string.ui_manage_extensions), Icons.Rounded.Tune),
                    PetalConnectedButtonItem("More on AMO", Icons.Rounded.OpenInNew)
                ),
                selectedIndex = -1,
                onSelect = { index ->
                    haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                    if (index == 0) {
                        onOpenManager()
                    } else {
                        onDismiss()
                        if (activity is com.petal.browser.activity.MainActivity) {
                            activity.addAlbum("Firefox Add-ons", PetalExtensionManager.amoAndroidBrowseUrl, true)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtensionMenuCardRow(
    extension: PetalExtensionManager.InstalledExtension,
    actionState: PetalExtensionManager.ExtensionActionState?,
    position: PetalGroupPosition,
    onActionClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val shape = rememberPetalGroupShape(position)
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val displayName = actionState?.title?.ifBlank { null } ?: extension.name
    val badgeText = actionState?.badgeText?.trim()
    val actionEnabled = actionState?.enabled ?: true

    val badgeBg = actionState?.badgeBackgroundColor?.takeIf { it != 0 }?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary
    val badgeFg = actionState?.badgeTextColor?.takeIf { it != 0 }?.let { Color(it) }
        ?: MaterialTheme.colorScheme.onPrimary

    Surface(
        onClick = onActionClick,
        enabled = actionEnabled,
        shape = shape,
        color = petalGroupSurfaceColor(),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen, pressFx = true)
            .alpha(if (actionEnabled) 1f else 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetalGroupIconBadge(
                shape = petalBadgeShape(extension.id),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                iconSize = 28.dp
            ) {
                val bmp = actionState?.icon ?: extension.icon
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (extension.version.isNotBlank()) {
                    Text(
                        text = "v${extension.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(PetalContainmentShapes.Pill)
                        .background(badgeBg)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        maxLines = 1
                    )
                }
            }

            if (extension.optionsPageUrl != null) {
                Surface(
                    onClick = onSettingsClick,
                    shape = petalBadgeShape("extension_row_settings"),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.ui_extension_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
