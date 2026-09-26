package com.petal.browser.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.ui.theme.PetalBrowserShapes

/**
 * Shared Material 3 Expressive themed Snackbar composable.
 * Delegates to the unified [PetalSnackbar] component with leading status icons,
 * pill shape, and PetalTheme tokens.
 */
@Composable
fun PetalThemedSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actionColor: Color = MaterialTheme.colorScheme.primary,
    dismissActionColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val message = snackbarData.visuals.message
    val type = when {
        message.contains("failed", ignoreCase = true) ||
        message.contains("error", ignoreCase = true) ||
        message.contains("disabled", ignoreCase = true) ||
        message.contains("crashed", ignoreCase = true) -> PetalSnackbarType.WARNING

        message.contains("success", ignoreCase = true) ||
        message.contains("saved", ignoreCase = true) ||
        message.contains("done", ignoreCase = true) ||
        message.contains("installed", ignoreCase = true) ||
        message.contains("enabled", ignoreCase = true) -> PetalSnackbarType.SUCCESS

        else -> PetalSnackbarType.INFO
    }

    val (iconRes, iconTint) = when (type) {
        PetalSnackbarType.INFO -> Pair(R.drawable.icon_info, actionColor)
        PetalSnackbarType.SUCCESS -> Pair(R.drawable.icon_check, MaterialTheme.colorScheme.tertiary)
        PetalSnackbarType.WARNING -> Pair(R.drawable.icon_alert, MaterialTheme.colorScheme.error)
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = type.name,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                ),
                color = contentColor,
                maxLines = 2
            )

            snackbarData.visuals.actionLabel?.let { label ->
                TextButton(
                    onClick = { snackbarData.performAction() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = actionColor)
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }

            if (snackbarData.visuals.withDismissAction) {
                IconButton(
                    onClick = { snackbarData.dismiss() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = dismissActionColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Shared Material 3 Expressive themed [SnackbarHost] wrapper component.
 * Features full slide-to-hide support via horizontal swipe and downward drag gestures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalThemedSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actionColor: Color = MaterialTheme.colorScheme.primary
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    data.dismiss()
                    true
                } else {
                    false
                }
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            modifier = Modifier.pointerInput(data) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 12f) { // Swiped downwards to dismiss
                        data.dismiss()
                    }
                }
            }
        ) {
            PetalThemedSnackbar(
                snackbarData = data,
                shape = shape,
                containerColor = containerColor,
                contentColor = contentColor,
                actionColor = actionColor
            )
        }
    }
}
