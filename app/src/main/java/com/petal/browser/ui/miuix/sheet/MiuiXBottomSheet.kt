package com.petal.browser.ui.miuix.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.miuix.components.MiuiXIconButton
import com.petal.browser.ui.miuix.glass.miuixAcrylicGlass
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXDimensions
import com.petal.browser.ui.miuix.theme.MiuiXShapes
import com.petal.browser.ui.miuix.theme.MiuiXSquircleShape

/**
 * Xiaomi HyperOS Continuous Squircle Bottom Sheet.
 *
 * Implements HyperOS flagship bottom sheet with:
 * - 28dp top continuous squircle corners.
 * - 38dp x 4dp subtle rounded pill drag handle.
 * - Flagship acrylic blur glass surface.
 * - Standard navigation & keyboard insets handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiXBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    backdrop: PetalBackdrop? = null,
    glassEnabled: Boolean = true,
    showCloseButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMiuiXColors.current

    val sheetShape = MiuiXSquircleShape(
        smoothing = 0.68f,
        topStart = CornerSize(MiuiXDimensions.LargeSheetCorner),
        topEnd = CornerSize(MiuiXDimensions.LargeSheetCorner),
        bottomEnd = CornerSize(0.dp),
        bottomStart = CornerSize(0.dp),
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.45f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .miuixAcrylicGlass(
                    shape = sheetShape,
                    isDark = colors.isDark,
                    backdrop = backdrop,
                    enabled = glassEnabled,
                    drawBorder = true
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // HyperOS Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(MiuiXDimensions.DragHandleWidth)
                            .height(MiuiXDimensions.DragHandleHeight)
                            .clip(MiuiXShapes.Pill)
                            .background(colors.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }

                // Optional Header Title & Close
                if (title != null || showCloseButton) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                ),
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        if (showCloseButton) {
                            MiuiXIconButton(
                                onClick = onDismissRequest,
                                size = 32.dp,
                                tint = colors.onSurfaceVariant
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Sheet Content
                content()
            }
        }
    }
}
