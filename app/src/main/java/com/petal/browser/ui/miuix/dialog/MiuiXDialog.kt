package com.petal.browser.ui.miuix.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.miuix.glass.miuixAcrylicGlass
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXDimensions
import com.petal.browser.ui.miuix.theme.MiuiXShapes

/**
 * Xiaomi HyperOS Continuous Squircle Dialog.
 *
 * Implements HyperOS dialog layout:
 * - 24dp continuous squircle container.
 * - Flagship acrylic blur surface.
 * - Seamless action button bar with hairline dividers.
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
    backdrop: PetalBackdrop? = null,
    glassEnabled: Boolean = true,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable () -> Unit)? = null,
) {
    val colors = LocalMiuiXColors.current
    val dialogShape = MiuiXShapes.Large

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .miuixAcrylicGlass(
                    shape = dialogShape,
                    isDark = colors.isDark,
                    backdrop = backdrop,
                    enabled = glassEnabled,
                    drawBorder = true
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header & Message Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        ),
                        color = colors.onSurface
                    )

                    if (!message.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            ),
                            color = colors.onSurfaceVariant
                        )
                    }

                    if (content != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        content()
                    }
                }

                // Divider before action buttons
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = colors.divider
                )

                // Button row (HyperOS hairline segmented style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissText != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clickable(
                                    role = Role.Button,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss ?: onDismissRequest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dismissText,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                ),
                                color = colors.onSurfaceVariant
                            )
                        }

                        VerticalDivider(
                            thickness = 0.5.dp,
                            color = colors.divider
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable(
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onConfirm
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = confirmText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            ),
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}
