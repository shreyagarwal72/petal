package com.petal.browser.compose.menu

import android.app.Dialog
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalMotion
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.containment.rememberPetalGroupPressScale
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.glass.PetalGlassContainer
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PetalPopupBlockedSheet
 * ─────────────────────────────────────────────────────────────────────────────
 * Brave-inspired pop-up blocked sheet built with Material 3 Expressive and Liquid Glass.
 * Surfaces blocked pop-ups with immediate actions: Always Allow, Open in New Tab,
 * Background Tab, Private Tab, Copy Link, or Dismiss.
 */
@Composable
fun PetalPopupBlockedSheet(
    targetUrl: String,
    isIncognito: Boolean,
    onDismiss: () -> Unit,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onOpenInBackground: () -> Unit,
    onOpenInIncognito: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }

    val domain = remember(targetUrl) {
        try {
            Uri.parse(targetUrl).host?.takeIf { it.isNotBlank() } ?: PetalAndroidUtils.domain(targetUrl)
        } catch (_: Throwable) {
            PetalAndroidUtils.domain(targetUrl)
        }
    }

    fun choose(id: String, action: () -> Unit) {
        if (selectedId != null) return
        selectedId = id
        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.75f)
        scope.launch {
            delay(120)
            onDismiss()
            action()
        }
    }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val menuShape = RoundedCornerShape(32.dp)

    PetalGlassContainer(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .heightIn(max = 520.dp),
        enabled = isGlass,
        live = true,
    ) {
        Surface(
            shape = menuShape,
            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = if (isGlass) 0.dp else 2.dp,
            shadowElevation = if (isGlass) 0.dp else 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isGlass) Modifier.liquidGlassChrome(menuShape, enabled = true) else Modifier),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PetalGroupIconBadge(
                        icon = Icons.Rounded.Security,
                        variant = PetalBadgeVariant.PRIMARY,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pop-up blocked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = domain ?: targetUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions Group
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    PopupBlockedRow(
                        id = "always_allow",
                        label = "Always allow pop-ups from this site",
                        icon = Icons.Rounded.CheckCircle,
                        variant = PetalBadgeVariant.PRIMARY,
                        position = PetalGroupPosition.TOP,
                        selected = selectedId == "always_allow",
                        onClick = { choose("always_allow", onAlwaysAllow) }
                    )
                    PopupBlockedRow(
                        id = "open_new_tab",
                        label = "Open in new tab",
                        icon = Icons.Rounded.Tab,
                        variant = PetalBadgeVariant.SECONDARY,
                        position = PetalGroupPosition.MIDDLE,
                        selected = selectedId == "open_new_tab",
                        onClick = { choose("open_new_tab", onAllowOnce) }
                    )
                    PopupBlockedRow(
                        id = "open_bg_tab",
                        label = "Open in background tab",
                        icon = Icons.Rounded.OpenInBrowser,
                        variant = PetalBadgeVariant.SECONDARY,
                        position = PetalGroupPosition.MIDDLE,
                        selected = selectedId == "open_bg_tab",
                        onClick = { choose("open_bg_tab", onOpenInBackground) }
                    )
                    PopupBlockedRow(
                        id = "open_incognito_tab",
                        label = "Open in private tab",
                        icon = Icons.Rounded.VisibilityOff,
                        variant = PetalBadgeVariant.TERTIARY,
                        position = PetalGroupPosition.MIDDLE,
                        selected = selectedId == "open_incognito_tab",
                        onClick = { choose("open_incognito_tab", onOpenInIncognito) }
                    )
                    PopupBlockedRow(
                        id = "copy_url",
                        label = "Copy link address",
                        icon = Icons.Rounded.Link,
                        variant = PetalBadgeVariant.NEUTRAL,
                        position = PetalGroupPosition.BOTTOM,
                        selected = selectedId == "copy_url",
                        onClick = {
                            choose("copy_url") {
                                PetalAndroidUtils.copy(context, targetUrl)
                                PetalToast.show(context, "Pop-up link copied")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dismiss
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    PopupBlockedRow(
                        id = "dismiss",
                        label = "Keep blocking",
                        icon = Icons.Rounded.Close,
                        variant = PetalBadgeVariant.NEUTRAL,
                        position = PetalGroupPosition.SOLO,
                        selected = selectedId == "dismiss",
                        onClick = { choose("dismiss") { /* no-op */ } }
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupBlockedRow(
    id: String,
    label: String,
    icon: ImageVector,
    variant: PetalBadgeVariant,
    position: PetalGroupPosition,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pressScale = rememberPetalGroupPressScale()
    PetalGroup(
        position = position,
        onClick = onClick,
        selected = selected,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetalGroupIconBadge(
                icon = icon,
                variant = variant,
                size = 36.dp,
                iconSize = 20.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

object PetalPopupBlockedBridge {
    @JvmStatic
    fun show(
        activity: ComponentActivity,
        targetUrl: String,
        isIncognito: Boolean,
        onAllowOnce: () -> Unit,
        onAlwaysAllow: () -> Unit,
    ) {
        activity.runOnUiThread {
            try {
                val dialog = Dialog(activity, R.style.PetalContextMenuDialogTheme)
                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                        val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                        val isAmoled = sp.getBoolean("sp_amoled", false)
                        val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                        val appFont = remember(fontName) { AppFont.fromName(fontName) }
                        val colorStyle = remember(styleName) {
                            try { ColorStyle.valueOf(styleName) } catch (_: Throwable) { ColorStyle.TONAL_SPOT }
                        }

                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId,
                        ) {
                            val hostSource = activity.findViewById<android.view.View>(R.id.main)
                                ?: activity.findViewById(android.R.id.content)
                            CompositionLocalProvider(
                                LocalPetalGlassSource provides hostSource
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PetalPopupBlockedSheet(
                                        targetUrl = targetUrl,
                                        isIncognito = isIncognito,
                                        onDismiss = { dialog.dismiss() },
                                        onAllowOnce = onAllowOnce,
                                        onAlwaysAllow = onAlwaysAllow,
                                        onOpenInBackground = {
                                            (activity as? com.petal.browser.activity.MainActivity)?.addAlbum(null, targetUrl, false, isIncognito)
                                        },
                                        onOpenInIncognito = {
                                            (activity as? com.petal.browser.activity.MainActivity)?.addAlbum(null, targetUrl, true, true)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                dialog.setContentView(composeView)
                dialog.window?.let { window ->
                    window.setBackgroundDrawableResource(android.R.color.transparent)
                    window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    window.setGravity(Gravity.CENTER)
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setDimAmount(0.55f)
                }
                dialog.setCanceledOnTouchOutside(true)
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
