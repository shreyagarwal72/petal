package com.petal.browser.ui.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
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
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

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
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
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
    com.petal.browser.ui.containment.PetalDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.modalScaleIn(),
        shape = shape,
        containerColor = containerColor,
        properties = properties,
        content = content,
    )
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
fun PetalExpressiveMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (() -> Unit))? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    enabled: Boolean = true
) {
    com.petal.browser.ui.containment.PetalPopupMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
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
                    Text(stringResource(R.string.ui_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(textState.value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.ui_ok), fontWeight = FontWeight.Bold)
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
                    label = { Text(stringResource(R.string.ui_username)) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = passwordState.value,
                onValueChange = { passwordState.value = it },
                label = { Text(stringResource(R.string.ui_password)) },
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
                    Text(stringResource(R.string.ui_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(usernameState.value, passwordState.value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.ui_sign_in), fontWeight = FontWeight.Bold)
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
                        confirmText = stringResource(R.string.ui_ok),
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
                        confirmText = stringResource(R.string.ui_ok),
                        onConfirm = {
                            try { dialog?.dismiss() } catch (_: Exception) {}
                            onConfirm.run()
                        },
                        dismissText = stringResource(R.string.ui_cancel),
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
