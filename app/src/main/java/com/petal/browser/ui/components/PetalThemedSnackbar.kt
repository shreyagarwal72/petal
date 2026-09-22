package com.petal.browser.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.petal.browser.ui.theme.PetalBrowserShapes

/**
 * Shared Material 3 Expressive themed Snackbar composable.
 * Dynamically resolves [MaterialTheme.colorScheme.surfaceContainerHighest] for background container,
 * [MaterialTheme.colorScheme.onSurface] for text message, and [MaterialTheme.colorScheme.primary]
 * for action labels (like "Undo"), seamlessly switching between Light and Dark mode.
 */
@Composable
fun PetalThemedSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    shape: Shape = PetalBrowserShapes.Snackbar,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actionColor: Color = MaterialTheme.colorScheme.primary,
    dismissActionColor: Color = MaterialTheme.colorScheme.onSurface
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(text = snackbarData.visuals.message, modifier = Modifier.weight(1f), color = contentColor, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            snackbarData.visuals.actionLabel?.let { label ->
                TextButton(onClick = { snackbarData.performAction() }) { Text(label, color = actionColor, fontWeight = FontWeight.Bold) }
            }
            if (snackbarData.visuals.withDismissAction) {
                IconButton(onClick = { snackbarData.dismiss() }) {
                    Icon(Icons.Rounded.Close, "Dismiss", tint = dismissActionColor)
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
    shape: Shape = PetalBrowserShapes.Snackbar,
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
