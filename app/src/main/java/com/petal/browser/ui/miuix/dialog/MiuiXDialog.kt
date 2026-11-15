package com.petal.browser.ui.miuix.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.petal.browser.ui.containment.PetalBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Real Miuix dialog (WindowDialog: dim + blur backdrop, spring animations from the library)
 * with the InstallerX button layout: equal-weight TextButtons, primary style for confirm.
 * Existing call sites keep working; [backdrop], [glassEnabled] and [properties] are accepted
 * for source compatibility (WindowDialog owns dimming and dismissal).
 */
@Composable
fun MiuiXDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmText: String = "OK",
    onConfirm: () -> Unit = onDismissRequest,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") backdrop: PetalBackdrop? = null,
    @Suppress("UNUSED_PARAMETER") glassEnabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") properties: DialogProperties = DialogProperties(),
    content: (@Composable () -> Unit)? = null,
) {
    WindowDialog(
        show = true,
        modifier = modifier,
        title = title,
        summary = message,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            if (content != null) {
                content()
                Spacer(modifier = Modifier.height(16.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (dismissText != null) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = dismissText,
                        onClick = onDismiss ?: onDismissRequest,
                    )
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = confirmText,
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
