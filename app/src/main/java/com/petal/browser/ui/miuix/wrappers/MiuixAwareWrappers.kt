package com.petal.browser.ui.miuix.wrappers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isMiuixEnabled
import com.petal.browser.ui.miuix.sheet.MiuiXBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.ModalBottomSheet as MaterialModalBottomSheet
import androidx.compose.material3.Switch as MaterialSwitch

/*
 * Drop-in, same-named wrappers for raw Material widgets. Call sites only change their import.
 * In Miuix mode they render the real Miuix component; in every other mode they delegate to the
 * Material widget with the arguments unchanged, so Material 3 Expressive and Liquid Glass modes
 * behave exactly as before.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isMiuixEnabled()) {
        MiuiXBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            showCloseButton = false,
            content = content,
        )
    } else {
        MaterialModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
) {
    if (isMiuixEnabled()) {
        WindowDialog(
            show = true,
            modifier = modifier,
            onDismissRequest = onDismissRequest,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                icon?.let { it(); Spacer(Modifier.height(12.dp)) }
                title?.let { it(); Spacer(Modifier.height(8.dp)) }
                text?.let { it(); Spacer(Modifier.height(16.dp)) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    } else {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
        )
    }
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (isMiuixEnabled()) {
        top.yukonga.miuix.kmp.basic.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    } else {
        MaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }
}
