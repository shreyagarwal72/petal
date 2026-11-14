package com.petal.browser.ui.miuix.containment

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.PetalBackdrop
import com.petal.browser.ui.miuix.glass.miuixAcrylicGlass
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXMotion
import com.petal.browser.ui.miuix.theme.MiuiXShapes

/**
 * Xiaomi HyperOS Standalone Squircle Card.
 *
 * Implements a standalone squircle surface with continuous G2 curvature,
 * acrylic blur or solid surface elevation, and spring press feedback.
 */
@Composable
fun MiuiXCard(
    modifier: Modifier = Modifier,
    shape: Shape = MiuiXShapes.Card,
    color: Color? = null,
    backdrop: PetalBackdrop? = null,
    glassEnabled: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalMiuiXColors.current
    val surfaceColor = color ?: colors.surface
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null && enabled) MiuiXMotion.PRESS_SCALE else 1f,
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_card_press_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .then(
                if (glassEnabled && backdrop != null) {
                    Modifier.miuixAcrylicGlass(
                        shape = shape,
                        isDark = colors.isDark,
                        backdrop = backdrop,
                        enabled = true,
                        drawBorder = true
                    )
                } else {
                    val borderStroke = if (colors.isDark) Color(0x22FFFFFF) else Color(0x0F000000)
                    Modifier
                        .clip(shape)
                        .background(surfaceColor, shape)
                        .border(BorderStroke(0.5.dp, borderStroke), shape)
                }
            )
    ) {
        content()
    }
}
