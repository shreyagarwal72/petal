package com.petal.browser.ui.components

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar
import com.petal.browser.ui.theme.PetalExpressiveTheme

enum class PetalPermissionType(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    LOCATION(
        title = "Location",
        description = "Provides local weather, nearby search results, and accurate directions.",
        icon = Icons.Rounded.NearMe,
        accentColor = Color(0xFF00B4D8)
    ),
    CAMERA(
        title = "Camera",
        description = "Allows taking photos, recording videos, or scanning QR codes directly on page.",
        icon = Icons.Rounded.Videocam,
        accentColor = Color(0xFFFF9400)
    ),
    MICROPHONE(
        title = "Microphone",
        description = "Enables voice search, microphone recording, and web audio calls.",
        icon = Icons.Rounded.Mic,
        accentColor = Color(0xFFAB47BC)
    ),
    NOTIFICATION(
        title = "Web Notifications",
        description = "Sends instant updates, message alerts, and web news directly to your device.",
        icon = Icons.Rounded.NotificationsActive,
        accentColor = Color(0xFF2979FF)
    ),
    DRM(
        title = "Protected Media",
        description = "Enables DRM protected media keys to stream encrypted high-definition video.",
        icon = Icons.Rounded.VpnKey,
        accentColor = Color(0xFF00C853)
    ),
    STORAGE(
        title = "Storage Access",
        description = "Allows saving offline data, persistent site settings, and storage cache.",
        icon = Icons.Rounded.Storage,
        accentColor = Color(0xFFE91E63)
    )
}

/**
 * Material 3 Expressive & Live Liquid Glass Permission Dialog.
 *
 * Features:
 * - Live real-time backdrop refraction & liquid glass sampling of the active webpage
 * - Category-specific squircle icon badge with vibrant tonal gradient
 * - Origin verification lock chip
 * - Expressive typography and spring-animated M3 Switch toggle for saving decisions
 * - Expressive rounded pill action buttons (Allow / Don't allow) with haptics
 */
@Composable
fun PetalPermissionDialog(
    type: PetalPermissionType,
    origin: String,
    glassSource: View? = null,
    onAllow: (remember: Boolean) -> Unit,
    onDeny: (remember: Boolean) -> Unit
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val isLiquidGlass = (sp.getBoolean("sp_liquid_glass_enabled", false) &&
            (isLiquidGlassEnabled("sp_liquid_glass_containments") || isLiquidGlassEnabled("sp_liquid_glass_address_bar")))

    val glassBar = rememberGlassBar(if (isLiquidGlass) glassSource else null)
    val baseGlassColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(32.dp)

    var rememberDecision by remember { mutableStateOf(true) }

    val displayHost = remember(origin) {
        try {
            val parsed = Uri.parse(origin)
            val h = parsed.host
            if (!h.isNullOrBlank()) h else origin.ifBlank { "This website" }
        } catch (_: Exception) {
            origin.ifBlank { "This website" }
        }
    }
    val isHttps = origin.startsWith("https://", ignoreCase = true)

    val promptTitle = remember(type, displayHost) {
        when (type) {
            PetalPermissionType.LOCATION -> "Allow $displayHost to use your location?"
            PetalPermissionType.CAMERA -> "Allow $displayHost to use your camera?"
            PetalPermissionType.MICROPHONE -> "Allow $displayHost to use your microphone?"
            PetalPermissionType.NOTIFICATION -> "Allow $displayHost to send you notifications?"
            PetalPermissionType.DRM -> "Allow $displayHost to play protected media?"
            PetalPermissionType.STORAGE -> "Allow $displayHost to store local site data?"
        }
    }

    val containerColor = if (isLiquidGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineBorder = if (isLiquidGlass) {
        BorderStroke(1.2.dp, type.accentColor.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
    }

    Surface(
        shape = shape,
        color = containerColor,
        tonalElevation = if (isLiquidGlass) 0.dp else 6.dp,
        shadowElevation = if (isLiquidGlass) 0.dp else 12.dp,
        border = outlineBorder,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight()
            .then(
                if (isLiquidGlass) Modifier.liquidGlass(shape, baseGlassColor, glassBar)
                else Modifier
            )
            .then(
                if (isLiquidGlass) Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = 0.65f)
                else Modifier
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category-Themed Expressive Squircle Icon Badge
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = type.accentColor.copy(alpha = 0.16f),
                border = BorderStroke(1.5.dp, type.accentColor.copy(alpha = 0.45f)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.title,
                        tint = type.accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Origin Security Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLiquidGlass) 0.50f else 0.85f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = if (isHttps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = displayHost,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Expressive Prompt Title
            Text(
                text = promptTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            // Subtitle Description
            Text(
                text = type.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Modern M3 Expressive "Remember decision" Card with Switch
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isLiquidGlass) 0.45f else 0.80f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                        rememberDecision = !rememberDecision
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remember for this site",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Apply this choice to future visits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rememberDecision,
                        onCheckedChange = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                            rememberDecision = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Action Buttons Row: Expressive Pill Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Don't Allow Button
                FilledTonalButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                        onDeny(rememberDecision)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(
                        text = "Don't allow",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                // Allow Button
                Button(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.8f)
                        onAllow(rememberDecision)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(
                        text = "Allow",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Java Interop Bridge to present the Material 3 Expressive & Live Liquid Glass UI permission prompt dialogs.
 */
object PetalPermissionDialogBridge {

    @JvmStatic
    fun showPermissionPrompt(
        context: Context,
        type: PetalPermissionType,
        origin: String,
        onAllow: Runnable,
        onDeny: Runnable
    ) {
        showPermissionPrompt(context, type, origin, { onAllow.run() }, { onDeny.run() })
    }

    @JvmStatic
    fun showPermissionPrompt(
        context: Context,
        type: PetalPermissionType,
        origin: String,
        onAllow: (remember: Boolean) -> Unit,
        onDeny: (remember: Boolean) -> Unit
    ) {
        var currContext = context
        while (currContext is android.content.ContextWrapper) {
            if (currContext is androidx.activity.ComponentActivity) break
            currContext = currContext.baseContext
        }

        val activity = currContext as? androidx.activity.ComponentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        var dialog: AlertDialog? = null

        // Sample the webpage content for genuine live liquid glass refraction
        val glassSource = activity.findViewById<View>(com.petal.browser.R.id.main_content)
            ?: activity.findViewById(com.petal.browser.R.id.main)

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                PetalExpressiveTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PetalPermissionDialog(
                            type = type,
                            origin = origin,
                            glassSource = glassSource,
                            onAllow = { remember ->
                                try { dialog?.dismiss() } catch (_: Exception) {}
                                onAllow(remember)
                            },
                            onDeny = { remember ->
                                try { dialog?.dismiss() } catch (_: Exception) {}
                                onDeny(remember)
                            }
                        )
                    }
                }
            }
        }

        val builder = MaterialAlertDialogBuilder(activity)
        builder.setView(composeView)
        builder.setCancelable(true)
        builder.setOnCancelListener { onDeny(false) }

        dialog = builder.create().apply {
            window?.let { win ->
                win.setBackgroundDrawableResource(android.R.color.transparent)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                // Use subtle 0.12f dim so live webpage remains vibrant and clear through the glass
                win.setDimAmount(0.12f)
            }
        }
        try {
            dialog.show()
        } catch (_: Exception) {}
    }
}
