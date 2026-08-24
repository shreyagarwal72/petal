package com.petal.browser.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actionColor: Color = MaterialTheme.colorScheme.primary,
    dismissActionColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        actionColor = actionColor,
        dismissActionContentColor = dismissActionColor
    )
}

/**
 * Shared Material 3 Expressive themed [SnackbarHost] wrapper component.
 */
@Composable
fun PetalThemedSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actionColor: Color = MaterialTheme.colorScheme.primary
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        PetalThemedSnackbar(
            snackbarData = data,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            actionColor = actionColor
        )
    }
}
