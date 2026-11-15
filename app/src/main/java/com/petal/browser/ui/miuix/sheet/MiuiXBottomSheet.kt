package com.petal.browser.ui.miuix.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.petal.browser.ui.containment.PetalBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * Real Miuix bottom sheet ([WindowBottomSheet]: dim, drag handle, spring in/out, nested scroll)
 * with the InstallerX close action. Signature kept for source compatibility; [sheetState],
 * [backdrop] and [glassEnabled] are unused because the library owns state and surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiXBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    @Suppress("UNUSED_PARAMETER") backdrop: PetalBackdrop? = null,
    @Suppress("UNUSED_PARAMETER") glassEnabled: Boolean = true,
    showCloseButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowBottomSheet(
        show = true,
        modifier = modifier,
        title = title,
        onDismissRequest = onDismissRequest,
        startAction = if (showCloseButton) {
            {
                val dismissState = LocalDismissState.current
                IconButton(onClick = { dismissState?.invoke() ?: onDismissRequest() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Close,
                        contentDescription = "Close",
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
        } else {
            null
        },
    ) {
        Column(content = content)
    }
}
