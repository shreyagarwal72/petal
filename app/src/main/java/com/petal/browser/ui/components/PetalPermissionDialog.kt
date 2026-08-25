package com.petal.browser.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
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

enum class PetalPermissionType(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    LOCATION(
        title = "Location Access",
        description = "wants to access your device's precise location for map and location features.",
        icon = Icons.Rounded.MyLocation
    ),
    CAMERA(
        title = "Camera Access",
        description = "wants to access your camera to capture photos, videos, or scan QR codes.",
        icon = Icons.Rounded.Videocam
    ),
    MICROPHONE(
        title = "Microphone Access",
        description = "wants to access your microphone for voice recording and audio input.",
        icon = Icons.Rounded.Mic
    ),
    DRM(
        title = "DRM Protected Media",
        description = "wants to access DRM protected media keys to stream encrypted video/audio.",
        icon = Icons.Rounded.Security
    )
}

/**
 * Material 3 Expressive UI permission prompt dialog overlay.
 */
@Composable
fun PetalPermissionDialog(
    type: PetalPermissionType,
    origin: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val cleanOrigin = origin.ifBlank { "Webpage" }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Badge Header
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = type.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Webpage Origin Pill Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = cleanOrigin,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = type.description,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Block",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Button(
                    onClick = onAllow,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Allow",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/**
 * Java Interop Bridge to present the Material 3 Expressive UI permission prompt dialogs.
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
        var currContext = context
        while (currContext is android.content.ContextWrapper) {
            if (currContext is androidx.activity.ComponentActivity) break
            currContext = currContext.baseContext
        }

        val activity = currContext as? androidx.activity.ComponentActivity ?: return

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
                        onAllow = {
                            try { dialog?.dismiss() } catch (ignored: Exception) {}
                            onAllow.run()
                        },
                        onDeny = {
                            try { dialog?.dismiss() } catch (ignored: Exception) {}
                            onDeny.run()
                        }
                    )
                }
            }
        }

        val builder = MaterialAlertDialogBuilder(activity)
        builder.setView(composeView)
        builder.setCancelable(true)
        builder.setOnCancelListener { onDeny.run() }

        dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
