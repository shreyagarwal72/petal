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
                                enabled = isLiquidGlassEnabled("sp_liquid_glass_containments")
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
    val enabledExtensions = remember(firefoxExtensions) {
        firefoxExtensions.filter { it.enabled }
    }

    val context = LocalContext.current
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }

    PetalHeroCard(
        shape = PetalContainmentShapes.HeroInner,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Active Domain Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.ui_extensions),
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

                IconButton(
                    onClick = {
                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                        onDismiss()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(12.dp))

            // Body: Empty State vs Extension List
            if (firefoxExtensions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExtensionOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "No add-ons installed",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Install powerful Firefox extensions like AdGuard, Dark Reader, and Bitwarden to customize your browsing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    FilledTonalButton(
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.75f)
                            onOpenManager()
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_find_firefox_extensions))
                    }
                }
            } else if (enabledExtensions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "All add-ons are disabled",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onOpenManager) {
                        Text(stringResource(R.string.ui_manage_extensions))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(enabledExtensions, key = { _, item -> item.id }) { index, ext ->
                        val actionState = activeTabActions[ext.id]
                        val position = petalGroupPositionFor(index, enabledExtensions.size)

                        ExtensionMenuCardRow(
                            extension = ext,
                            actionState = actionState,
                            position = position,
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

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(10.dp))

            // Footer: Manage Add-ons & AMO Store Shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                        onOpenManager()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ui_manage_extensions),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                TextButton(
                    onClick = {
                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                        onDismiss()
                        if (activity is com.petal.browser.activity.BrowserActivity) {
                            activity.addAlbum("Firefox Add-ons", PetalExtensionManager.amoAndroidBrowseUrl, true)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "More on AMO",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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
    val displayName = actionState?.title?.ifBlank { null } ?: extension.name
    val badgeText = actionState?.badgeText?.trim()

    val badgeBgColor = remember(actionState?.badgeBackgroundColor) {
        val raw = actionState?.badgeBackgroundColor
        if (raw != null && raw != 0) Color(raw) else null
    }
    val badgeFgColor = remember(actionState?.badgeTextColor) {
        val raw = actionState?.badgeTextColor
        if (raw != null && raw != 0) Color(raw) else null
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onActionClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extension Icon (Action icon override -> Extension icon -> Fallback)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val actionIcon = actionState?.icon
                val extIcon = extension.icon
                if (actionIcon != null) {
                    Image(
                        bitmap = actionIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (extIcon != null) {
                    Image(
                        bitmap = extIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Title & Description/Status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (extension.version.isNotBlank()) {
                    Text(
                        text = "v${extension.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
            }

            // Live Action Badge (e.g. uBlock blocked count "14")
            if (!badgeText.isNullOrBlank()) {
                val bg = badgeBgColor ?: MaterialTheme.colorScheme.primary
                val fg = badgeFgColor ?: MaterialTheme.colorScheme.onPrimary
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = fg,
                        maxLines = 1
                    )
                }
            }

            // Extension Settings Button (44dp touch target)
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(ExpressiveButtonShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.ui_extension_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
