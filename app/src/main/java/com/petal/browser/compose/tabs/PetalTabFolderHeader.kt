package com.petal.browser.compose.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome

/**
 * Zen Browser-style Collapsible Tab Folder Header.
 * Renders an accordion header displaying folder dot, title, count pill, and rotating chevron.
 */
@Composable
fun PetalTabFolderHeader(
    group: PetalTabGroup,
    isCollapsed: Boolean,
    tabCount: Int,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
    val groupColor = group.parseColor()
    val shape = RoundedCornerShape(14.dp)

    val rotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = spring(),
        label = "folder_chevron_rot"
    )

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isGlass) 0.50f else 0.75f),
        border = BorderStroke(1.dp, groupColor.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onToggleCollapse)
            .then(
                if (isGlass) {
                    Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = 0.3f)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Surface(
                    shape = CircleShape,
                    color = groupColor,
                    modifier = Modifier.size(10.dp)
                ) {}

                Text(
                    text = group.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = groupColor.copy(alpha = 0.16f),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(
                        text = if (isCollapsed) "$tabCount tabs (collapsed)" else "$tabCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = groupColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (isCollapsed) "Expand Folder" else "Collapse Folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}
