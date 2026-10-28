package com.petal.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

data class FabWheelAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * M3 Expressive Morphing FAB Wheel component.
 * Morphs shape from circular FAB to extended rounded-rect with radial spring actions.
 */
@Composable
fun ExpressiveFabWheel(
    actions: List<FabWheelAction>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "FabWheelRotation"
    )

    val scaleProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy
        ),
        label = "FabWheelExpansion"
    )

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val fabShape = if (isExpanded) RoundedCornerShape(20.dp) else CircleShape

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        if (scaleProgress > 0.01f) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .scale(scaleProgress)
                    .padding(bottom = 4.dp)
            ) {
                actions.forEach { action ->
                    val actionShape = RoundedCornerShape(20.dp)
                    Surface(
                        onClick = {
                            isExpanded = false
                            action.onClick()
                        },
                        shape = actionShape,
                        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = if (isGlass) 0.dp else 6.dp,
                        tonalElevation = if (isGlass) 0.dp else 2.dp,
                        modifier = Modifier.then(
                            if (isGlass) Modifier.liquidGlassChrome(actionShape, enabled = true, sheenIntensity = 0.60f, pressFx = true)
                            else Modifier
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Primary FAB Trigger
        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
            shape = fabShape,
            containerColor = if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f) else MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (isGlass) 0.dp else 8.dp,
                pressedElevation = if (isGlass) 0.dp else 4.dp
            ),
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (isGlass) Modifier.liquidGlassChrome(
                        fabShape,
                        enabled = true,
                        sheenIntensity = 0.75f,
                        tintColor = MaterialTheme.colorScheme.primaryContainer,
                        pressFx = true
                    ) else Modifier
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = if (isExpanded) "Close actions" else "Quick actions",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}
