package com.petal.browser.ui.miuix.containment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.miuix.glass.miuixAcrylicGlass
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXDimensions
import com.petal.browser.ui.miuix.theme.MiuiXShapes

/**
 * Xiaomi HyperOS Inset Card Group Container.
 *
 * Implements the signature HyperOS containment pattern:
 * - 16dp horizontal inset margins with 20dp continuous squircle corners.
 * - Uppercase section header with optional trailing action/caption.
 * - Flagship acrylic glass surface when blur is enabled.
 * - Optional bottom caption footer.
 */
@Composable
fun MiuiXGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    backdrop: PetalBackdrop? = null,
    glassEnabled: Boolean = true,
    shape: Shape = MiuiXShapes.Card,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMiuiXColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MiuiXDimensions.ScreenMarginHorizontal)
    ) {
        // Section Header
        if (title != null || headerAction != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontSize = 11.sp
                        ),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                headerAction?.invoke()
            }
        }

        // Inset Squircle Card Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .miuixAcrylicGlass(
                    shape = shape,
                    isDark = colors.isDark,
                    backdrop = backdrop,
                    enabled = glassEnabled,
                    drawBorder = true
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }

        // Section Footer Caption
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
