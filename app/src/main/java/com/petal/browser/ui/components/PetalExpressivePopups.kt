package com.petal.browser.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, PetalExpressivePopupDefaults.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
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
