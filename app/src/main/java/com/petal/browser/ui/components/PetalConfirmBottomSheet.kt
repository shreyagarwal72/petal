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
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalConfirmBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    icon: ImageVector = Icons.Rounded.Close,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit = onDismissRequest
) {
    if (visible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = BottomSheetDefaults.ExpandedShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = { WindowInsets.navigationBars }
        ) {
            PetalConfirmSheetContent(
                icon = icon,
                title = title,
                message = message,
                confirmText = confirmText,
                cancelText = cancelText,
                isDestructive = isDestructive,
                showDragHandle = false,
                onConfirm = onConfirm,
                onCancel = onCancel
            )
        }
    }
}

/**
 * Material 3 Expressive Confirmation Bottom Sheet for Petal Browser.
 * Used for tab closing, quit confirmation, and bottom dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalConfirmSheetContent(
    icon: ImageVector = Icons.Rounded.Close,
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    showDragHandle: Boolean = true,
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
        if (showDragHandle) {
            BottomSheetDefaults.DragHandle()
        }

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

        // Action Buttons Row — connected segmented action row (0 borders)
        com.petal.browser.ui.containment.PetalActionRow(
            confirmText = confirmText,
            onConfirm = {
                PetalHapticEngine.getInstance(context).play(
                    if (isDestructive) PetalHapticEngine.Pattern.HEAVY_CLICK else PetalHapticEngine.Pattern.CLICK,
                    0.8f
                )
                onConfirm()
            },
            dismissText = cancelText,
            onDismiss = {
                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.4f)
                onCancel()
            },
            destructive = isDestructive
        )

        Spacer(Modifier.height(12.dp))
    }
}

object PetalConfirmSheetBridge {
    private var activeComposeView: ComposeView? = null

    @JvmStatic
    @JvmOverloads
    fun showConfirmSheet(
        activity: ComponentActivity,
        icon: ImageVector = Icons.Rounded.Close,
        title: String,
        message: String,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
        isDestructive: Boolean = false,
        onConfirm: Runnable,
        onCancel: Runnable? = null
    ) {
        if (activity.isFinishing) return

        activity.runOnUiThread {
            activeComposeView?.let {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
            }
            activeComposeView = null

            val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return@runOnUiThread
            var composeView: ComposeView? = null
            var actionHandled = false

            fun cleanup() {
                composeView?.let { cv ->
                    (cv.parent as? android.view.ViewGroup)?.removeView(cv)
                    if (activeComposeView == cv) {
                        activeComposeView = null
                    }
                }
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
            val isAmoled = sp.getBoolean("sp_amoled", false)
            val useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

            composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

                setContent {
                    PetalExpressiveTheme(
                        paletteId = paletteId,
                        useAmoled = isAmoled,
                        dynamicColor = useDynamic
                    ) {
                        PetalConfirmBottomSheet(
                            visible = true,
                            onDismissRequest = {
                                if (!actionHandled) {
                                    actionHandled = true
                                    cleanup()
                                    onCancel?.run()
                                }
                            },
                            icon = icon,
                            title = title,
                            message = message,
                            confirmText = confirmText,
                            cancelText = cancelText,
                            isDestructive = isDestructive,
                            onConfirm = {
                                if (!actionHandled) {
                                    actionHandled = true
                                    cleanup()
                                    onConfirm.run()
                                }
                            },
                            onCancel = {
                                if (!actionHandled) {
                                    actionHandled = true
                                    cleanup()
                                    onCancel?.run()
                                }
                            }
                        )
                    }
                }
            }

            activeComposeView = composeView
            rootView.addView(
                composeView,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
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
            title = activity.getString(R.string.dialog_quit_title),
            message = activity.getString(R.string.dialog_quit_message),
            confirmText = activity.getString(R.string.dialog_quit_confirm),
            cancelText = activity.getString(R.string.dialog_quit_cancel),
            isDestructive = false,
            onConfirm = onConfirm
        )
    }

    @JvmStatic
    fun showRestartConfirmation(activity: ComponentActivity, onConfirm: Runnable) {
        showRestartConfirmation(activity, onConfirm, null)
    }

    @JvmStatic
    fun showRestartConfirmation(
        activity: ComponentActivity,
        onConfirm: Runnable,
        onCancel: Runnable?
    ) {
        showConfirmSheet(
            activity = activity,
            icon = Icons.Rounded.RestartAlt,
            title = activity.getString(R.string.dialog_restart_title),
            message = activity.getString(R.string.dialog_restart_message),
            confirmText = activity.getString(R.string.dialog_restart_now),
            cancelText = activity.getString(R.string.dialog_restart_later),
            isDestructive = false,
            onConfirm = onConfirm,
            onCancel = onCancel
        )
    }

    @JvmStatic
    fun showClearDatabaseConfirmation(activity: ComponentActivity, title: String, message: String, onConfirm: Runnable) {
        showConfirmSheet(
            activity = activity,
            icon = Icons.Rounded.DeleteForever,
            title = title,
            message = message,
            confirmText = "Clear All",
            cancelText = "Cancel",
            isDestructive = true,
            onConfirm = onConfirm
        )
    }

    @JvmStatic
    fun showGoogleSearchFixerPrompt(
        activity: ComponentActivity,
        currentlyEnabled: Boolean,
        onChoice: java.util.function.Consumer<Boolean>
    ) {
        val title = "Google Search Fixer"
        val message = if (currentlyEnabled) {
            "Google Search Fixer is currently ON (spoofs Chrome UA for modern widgets). Keep it ON, or turn it OFF to prevent Google Captchas?"
        } else {
            "Google Search Fixer is currently OFF (authentic Gecko headers). Turn it ON for modern Google mobile layout, or keep it OFF to prevent Captchas?"
        }

        showConfirmSheet(
            activity = activity,
            icon = Icons.Rounded.Search,
            title = title,
            message = message,
            confirmText = "Turn ON",
            cancelText = "Turn OFF",
            isDestructive = false,
            onConfirm = Runnable {
                onChoice.accept(true)
            },
            onCancel = Runnable {
                onChoice.accept(false)
            }
        )
    }
}
