package com.petal.browser.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

/**
 * Material 3 Expressive Confirmation Bottom Sheet for Petal Browser.
 * Used for tab closing, quit confirmation, and bottom dialogs.
 */
@Composable
fun PetalConfirmSheetContent(
    icon: ImageVector = Icons.Rounded.Close,
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Drag handle bar
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )

        // Header Icon Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Title & Description
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.4f)
                    onCancel()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(cancelText)
            }

            Button(
                onClick = {
                    PetalHapticEngine.getInstance(context).play(
                        if (isDestructive) PetalHapticEngine.Pattern.HEAVY_CLICK else PetalHapticEngine.Pattern.CLICK,
                        0.8f
                    )
                    onConfirm()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

object PetalConfirmSheetBridge {

    @JvmStatic
    fun showConfirmSheet(
        activity: ComponentActivity,
        icon: ImageVector = Icons.Rounded.Close,
        title: String,
        message: String,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
        isDestructive: Boolean = false,
        onConfirm: Runnable
    ) {
        if (activity.isFinishing) return

        val dialog = BottomSheetDialog(activity)
        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
            val isAmoled = sp.getBoolean("sp_amoled", false)
            val useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

            setContent {
                PetalExpressiveTheme(
                    paletteId = paletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PetalConfirmSheetContent(
                            icon = icon,
                            title = title,
                            message = message,
                            confirmText = confirmText,
                            cancelText = cancelText,
                            isDestructive = isDestructive,
                            onConfirm = {
                                dialog.dismiss()
                                onConfirm.run()
                            },
                            onCancel = {
                                dialog.dismiss()
                            }
                        )
                    }
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    @JvmStatic
    fun showTabCloseConfirmation(activity: ComponentActivity, tabTitle: String?, onConfirm: Runnable) {
        val title = "Close Tab?"
        val displayTitle = if (!tabTitle.isNullOrBlank() && !tabTitle.equals("about:blank", ignoreCase = true)) "\"$tabTitle\"" else "this tab"
        val message = "Are you sure you want to close $displayTitle? Unsaved webpage progress will be lost."

        showConfirmSheet(
            activity = activity,
            icon = Icons.Rounded.Close,
            title = title,
            message = message,
            confirmText = "Close Tab",
            cancelText = "Keep Open",
            isDestructive = true,
            onConfirm = onConfirm
        )
    }

    @JvmStatic
    fun showCloseAllTabsConfirmation(activity: ComponentActivity, tabCount: Int, onConfirm: Runnable) {
        val title = "Close All Tabs?"
        val message = "Are you sure you want to close all $tabCount active tabs?"

        showConfirmSheet(
            activity = activity,
            icon = Icons.Rounded.LayersClear,
            title = title,
            message = message,
            confirmText = "Close All",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = onConfirm
        )
    }

    @JvmStatic
    fun showQuitBrowserConfirmation(activity: ComponentActivity, onConfirm: Runnable) {
        showConfirmSheet(
            activity = activity,
            icon = Icons.Rounded.ExitToApp,
            title = "Quit Petal Browser?",
            message = "Are you sure you want to exit Petal Browser and close your active session?",
            confirmText = "Quit",
            cancelText = "Stay",
            isDestructive = false,
            onConfirm = onConfirm
        )
    }
}
