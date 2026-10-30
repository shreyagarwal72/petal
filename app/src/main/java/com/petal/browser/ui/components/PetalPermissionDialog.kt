package com.petal.browser.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.glass.PetalGlassContainer
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.rememberGlassSheen

enum class PetalPermissionType(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    LOCATION(
        title = "Location",
        description = "Provides local weather, nearby search results, and accurate directions.",
        icon = Icons.Rounded.MyLocation,
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
        icon = Icons.Rounded.Notifications,
        accentColor = Color(0xFF2979FF)
    ),
    DRM(
        title = "Protected Media",
        description = "Enables DRM protected media keys to stream encrypted high-definition video.",
        icon = Icons.Rounded.Key,
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
 * Material 3 Expressive UI & Live Liquid Glass permission prompt dialog overlay.
 * Replaces stock Mozilla DialogFragment with real-time webpage backdrop blur,
 * specular edge refraction, and clean site origin verification.
 */
@Composable
fun PetalPermissionDialog(
    type: PetalPermissionType,
    origin: String,
    onAllow: (remember: Boolean) -> Unit,
    onDeny: (remember: Boolean) -> Unit
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val shape = RoundedCornerShape(32.dp)

    val rememberChoiceState = remember { mutableStateOf(true) }

    val displayHost = remember(origin) {
        try {
            val parsed = android.net.Uri.parse(origin)
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

    PetalGlassContainer(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight(),
        enabled = isGlass,
        live = true
    ) {
        Surface(
            shape = shape,
            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = if (isGlass) 0.dp else 16.dp,
            border = if (isGlass) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category-Themed Squircle Icon Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = type.accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.5.dp, type.accentColor.copy(alpha = 0.40f)),
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
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isGlass) 0.5f else 0.8f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isHttps) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = if (isHttps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = displayHost,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Title matching exact user prompt
                Text(
                    text = promptTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 28.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Subtitle explanation
                Text(
                    text = type.description,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // "Remember decision for this site" Checkbox Row
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isGlass) 0.4f else 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .bouncyClickable { rememberChoiceState.value = !rememberChoiceState.value }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Checkbox(
                            checked = rememberChoiceState.value,
                            onCheckedChange = { rememberChoiceState.value = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Remember decision for this site",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Expressive Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onDeny(rememberChoiceState.value) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .bouncyClickable()
                    ) {
                        Text(
                            text = "Don't allow",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Button(
                        onClick = { onAllow(rememberChoiceState.value) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .bouncyClickable()
                    ) {
                        Text(
                            text = "Allow",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
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

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                PetalExpressiveTheme {
                    PetalPermissionDialog(
                        type = type,
                        origin = origin,
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

        val builder = MaterialAlertDialogBuilder(activity)
        builder.setView(composeView)
        builder.setCancelable(true)
        builder.setOnCancelListener { onDeny(false) }

        dialog = builder.create().apply {
            window?.let { win ->
                win.setBackgroundDrawableResource(android.R.color.transparent)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0.35f)
            }
        }
        try {
            dialog.show()
        } catch (_: Exception) {}
    }
}
