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

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.ExtensionOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.blur.PetalBlurSurface
import com.petal.browser.ui.containment.PetalContainmentShapes
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.PetalSheetHeader
import com.petal.browser.ui.containment.PetalSheetIconPill
import com.petal.browser.ui.containment.PetalSheetListRow
import com.petal.browser.ui.containment.PetalSheetPill
import com.petal.browser.ui.containment.PetalSheetTone
import com.petal.browser.ui.containment.petalSheetSurface
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.SmoothRoundedShape
import kotlinx.coroutines.launch

/**
 * Extensions menu as a real bottom sheet ([PetalSheet]) in the Petal sheet language, replacing the old
 * floating dialog. Same features: live toolbar badges, 1-tap browser action, options-page shortcut,
 * empty / all-disabled states, Manage extensions and "More on AMO". Liquid Glass and Background blur
 * ("Bottom sheets") are handled by [PetalSheet] and the shared sheet parts.
 */
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
                val decorView = activity.window.decorView as? ViewGroup ?: return@runOnUiThread
                val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: decorView
                lateinit var composeView: ComposeView
                val remove: () -> Unit = {
                    if (composeView.parent != null) decorView.removeView(composeView)
                }

                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
                        val hostSource = activity.findViewById<android.view.View>(R.id.main) ?: rootView

                        CompositionLocalProvider(LocalPetalGlassSource provides hostSource) {
                            PetalExpressiveTheme(
                                darkTheme = darkTheme,
                                dynamicColor = dynamicColor,
                                useAmoled = isAmoled,
                                appFont = appFont,
                                colorStyle = colorStyle,
                                paletteId = paletteId
                            ) {
                                ExtensionsMenuSheetContent(
                                    activity = activity,
                                    domain = host.ifEmpty { "Current Page" },
                                    onClosed = remove,
                                    onOpenManager = onOpenManager
                                )
                            }
                        }
                    }
                }
                decorView.addView(
                    composeView,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            } catch (t: Throwable) {
                android.util.Log.e("PetalExtensionsMenu", "Failed to show extensions menu sheet", t)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionsMenuSheetContent(
    activity: ComponentActivity,
    domain: String,
    onClosed: () -> Unit,
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.5f).dp

    /** Animate the sheet away, remove the host view, then run [then]. */
    fun closeThen(then: (() -> Unit)? = null) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onClosed()
            then?.invoke()
        }
    }

    PetalSheet(
        onDismissRequest = onClosed,
        blurSurface = PetalBlurSurface.SHEETS,
        sheetState = sheetState
    ) {
        Column(Modifier.fillMaxWidth()) {
            PetalSheetHeader(
                title = stringResource(R.string.ui_extensions),
                subtitle = domain,
                modifier = Modifier.padding(horizontal = 16.dp),
                leading = {
                    Box(
                        Modifier
                            .size(80.dp)
                            .petalSheetSurface(SmoothRoundedShape(24.dp), MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                },
                trailing = {
                    PetalSheetIconPill(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close),
                        modifier = Modifier.size(48.dp),
                        minHeight = 48.dp,
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                            closeThen()
                        }
                    )
                }
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBody + 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    firefoxExtensions.isEmpty() -> PetalSheetListRow(
                        title = stringResource(R.string.ui_find_firefox_extensions),
                        subtitle = "No add-ons installed yet",
                        icon = Icons.Rounded.Add,
                        tone = PetalSheetTone.Primary,
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {
                            haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.75f)
                            closeThen { onOpenManager() }
                        }
                    )
                    enabledExtensions.isEmpty() -> PetalSheetListRow(
                        title = "All add-ons are disabled",
                        subtitle = stringResource(R.string.ui_manage_extensions),
                        icon = Icons.Rounded.ExtensionOff,
                        tone = PetalSheetTone.Neutral,
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { closeThen { onOpenManager() } }
                    )
                    else -> enabledExtensions.forEach { ext ->
                        ExtensionSheetRow(
                            extension = ext,
                            actionState = activeTabActions[ext.id],
                            onActionClick = {
                                haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.8f)
                                closeThen {
                                    activity.window.decorView.post {
                                        PetalExtensionManager.triggerBrowserAction(ext.id, activity)
                                    }
                                }
                            },
                            onSettingsClick = {
                                haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                closeThen {
                                    activity.window.decorView.post {
                                        PetalExtensionManager.openOptionsPage(ext.id, activity)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PetalSheetPill(
                    text = stringResource(R.string.ui_manage_extensions),
                    icon = Icons.Rounded.Tune,
                    tone = PetalSheetTone.Primary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                        closeThen { onOpenManager() }
                    }
                )
                PetalSheetPill(
                    text = "More on AMO",
                    icon = Icons.Rounded.OpenInNew,
                    tone = PetalSheetTone.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        haptic.playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                        closeThen {
                            if (activity is com.petal.browser.activity.MainActivity) {
                                activity.addAlbum("Firefox Add-ons", PetalExtensionManager.amoAndroidBrowseUrl, true)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExtensionSheetRow(
    extension: PetalExtensionManager.InstalledExtension,
    actionState: PetalExtensionManager.ExtensionActionState?,
    onActionClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val displayName = actionState?.title?.ifBlank { null } ?: extension.name
    val badgeText = actionState?.badgeText?.trim()
    val actionEnabled = actionState?.enabled ?: true

    val badgeBg = actionState?.badgeBackgroundColor?.takeIf { it != 0 }?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary
    val badgeFg = actionState?.badgeTextColor?.takeIf { it != 0 }?.let { Color(it) }
        ?: MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .alpha(if (actionEnabled) 1f else 0.5f)
            .petalSheetSurface(
                RoundedCornerShape(36.dp),
                MaterialTheme.colorScheme.surfaceContainerHighest,
                if (actionEnabled) onActionClick else null
            )
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .petalSheetSurface(CircleShape, MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val bmp = actionState?.icon ?: extension.icon
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(30.dp).clip(CircleShape))
            } else {
                Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
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
                    .padding(horizontal = 10.dp, vertical = 4.dp),
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
            PetalSheetIconPill(
                icon = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.ui_extension_settings),
                tone = PetalSheetTone.Neutral,
                minHeight = 44.dp,
                modifier = Modifier.size(44.dp),
                onClick = onSettingsClick
            )
        }
    }
}
