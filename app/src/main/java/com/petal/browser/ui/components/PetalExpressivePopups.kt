package com.petal.browser.ui.components

import android.os.Build
import android.view.WindowManager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Shared popup/dialog language for Petal.
 *
 * All transient surfaces use the same M3 Expressive containment rules:
 * - large 28–32dp shape
 * - tonal surface containers instead of flat white/black cards
 * - subtle outline for separation on AMOLED/dark themes
 * - generous 56dp menu rows and spring/motion supplied by MaterialExpressiveTheme
 */
object PetalExpressivePopupDefaults {
    val dialogShape: Shape = RoundedCornerShape(32.dp)
    val menuShape: Shape = RoundedCornerShape(24.dp)
    val menuContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val dialogContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
}

@Composable
fun PetalExpressiveDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PetalExpressivePopupDefaults.dialogShape,
    containerColor: Color = PetalExpressivePopupDefaults.dialogContainerColor,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialogWindow?.attributes = dialogWindow?.attributes?.apply { dimAmount = 0.58f }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                dialogWindow?.setBackgroundBlurRadius(34)
            }
            onDispose {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dialogWindow?.setBackgroundBlurRadius(0)
                dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        // Compose can measure an AnimatedVisibility first frame at zero size on some
        // Android versions. Keep the dialog surface mounted immediately so it never
        // becomes a blank, touch-blocking window.
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .modalScaleIn(),
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, PetalExpressivePopupDefaults.outline)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun PetalExpressiveAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Rounded.Info,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    confirmText: String = "OK",
    onConfirm: () -> Unit,
    dismissText: String? = "Cancel",
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false
) {
    PetalExpressiveDialog(onDismissRequest = onDismissRequest) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (destructive) MaterialTheme.colorScheme.errorContainer else iconContainerColor,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else iconContentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dismissText != null) {
                TextButton(
                    onClick = { (onDismiss ?: onDismissRequest)() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(dismissText)
                }
                Spacer(Modifier.width(8.dp))
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else ButtonDefaults.buttonColors()
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PetalExpressiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = PetalExpressivePopupDefaults.menuShape,
        containerColor = PetalExpressivePopupDefaults.menuContainerColor,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, PetalExpressivePopupDefaults.outline),
        content = content
    )
}

@Composable
fun PetalExpressiveMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (() -> Unit))? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    )
}

/**
 * Material 3 Expressive Text Input Prompt Dialog (JavaScript prompt()).
 */
@Composable
fun PetalExpressiveTextPromptDialog(
    title: String,
    message: String?,
    defaultValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textState = remember { mutableStateOf(defaultValue) }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = textState.value,
                onValueChange = { textState.value = it },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(textState.value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Authentication Prompt Dialog (HTTP Basic / Digest Auth).
 */
@Composable
fun PetalExpressiveAuthPromptDialog(
    title: String,
    message: String?,
    isPasswordOnly: Boolean,
    initialUsername: String = "",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val usernameState = remember { mutableStateOf(initialUsername) }
    val passwordState = remember { mutableStateOf("") }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isPasswordOnly) {
                OutlinedTextField(
                    value = usernameState.value,
                    onValueChange = { usernameState.value = it },
                    label = { Text("Username") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = passwordState.value,
                onValueChange = { passwordState.value = it },
                label = { Text("Password") },
                shape = RoundedCornerShape(16.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(usernameState.value, passwordState.value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Sign In", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Java Interop Bridge to render pure Material 3 Expressive Prompts directly from GeckoView.
 */
object PetalExpressivePromptBridge {

    @JvmStatic
    fun showAlert(
        context: android.content.Context,
        title: String,
        message: String?,
        onConfirm: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveAlertDialog(
                        onDismissRequest = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.run()
                        },
                        title = title,
                        message = message,
                        confirmText = "OK",
                        onConfirm = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.run()
                        },
                        dismissText = null
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onConfirm.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    @JvmStatic
    fun showConfirm(
        context: android.content.Context,
        title: String,
        message: String?,
        onConfirm: Runnable,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveAlertDialog(
                        onDismissRequest = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        },
                        title = title,
                        message = message,
                        confirmText = "OK",
                        onConfirm = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.run()
                        },
                        dismissText = "Cancel",
                        onDismiss = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        }
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onCancel.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    @JvmStatic
    fun showPrompt(
        context: android.content.Context,
        title: String,
        message: String?,
        defaultValue: String?,
        onConfirm: java.util.function.Consumer<String>,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveTextPromptDialog(
                        title = title,
                        message = message,
                        defaultValue = defaultValue ?: "",
                        onConfirm = { value ->
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.accept(value)
                        },
                        onDismiss = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        }
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onCancel.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    @JvmStatic
    fun showAuth(
        context: android.content.Context,
        title: String,
        message: String?,
        isPasswordOnly: Boolean,
        initialUsername: String?,
        onConfirm: java.util.function.BiConsumer<String, String>,
        onCancel: Runnable
    ) {
        val activity = findActivity(context) ?: return
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.petal.browser.ui.theme.PetalExpressiveTheme {
                    PetalExpressiveAuthPromptDialog(
                        title = title,
                        message = message,
                        isPasswordOnly = isPasswordOnly,
                        initialUsername = initialUsername ?: "",
                        onConfirm = { user, pass ->
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.accept(user, pass)
                        },
                        onDismiss = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onCancel.run()
                        }
                    )
                }
            }
        }
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setView(composeView)
            .setCancelable(true)
            .setOnCancelListener { onCancel.run() }
        dialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    private fun findActivity(context: android.content.Context): androidx.activity.ComponentActivity? {
        var curr = context
        while (curr is android.content.ContextWrapper) {
            if (curr is androidx.activity.ComponentActivity) return curr
            curr = curr.baseContext
        }
        return null
    }
}
