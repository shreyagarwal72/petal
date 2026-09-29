package com.petal.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalStatusHeroCard
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Thin forwarding helpers kept so older call sites keep compiling after the move to
 * the `ui.containment` components.
 */

@Composable
fun PetalShapeIconBadge(
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PetalGroupIconBadge(
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        size = size,
        iconSize = iconSize,
        modifier = modifier,
        content = content,
    )
}

/** A rounded "cookie" outline with [lobes] scallops. [depth] is 0f..0.5f (how deep each dip goes). */
class ScallopedShape(private val lobes: Int = 8, private val depth: Float = 0.16f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f
        val steps = 180
        val d = depth.coerceIn(0f, 0.5f)
        val path = Path()
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (2f * PI.toFloat())
            val r = radius * (1f - d * 0.5f * (1f - cos(lobes * t)))
            val x = cx + r * cos(t)
            val y = cy + r * sin(t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun ExpressiveHeroBanner(
    title: String,
    subtitle: String,
    statusText: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    statusActive: Boolean = true,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    PetalStatusHeroCard(
        title = title,
        subtitle = subtitle,
        statusText = statusText,
        icon = icon,
        modifier = modifier,
        statusActive = statusActive,
        actionLabel = actionLabel,
        onActionClick = onActionClick,
    )
}

data class ActionMatrixItem(
    val icon: ImageVector,
    val label: String,
    val shape: Shape,
    val onClick: () -> Unit,
    val isActive: Boolean = false,
)

@Composable
fun ActionMatrixGrid(
    actions: List<ActionMatrixItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        actions.forEach { action ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = action.onClick)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PetalShapeIconBadge(
                    shape = action.shape,
                    containerColor = if (action.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (action.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 52.dp,
                    iconSize = 24.dp,
                ) {
                    Icon(action.icon, contentDescription = null)
                }
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SettingsTileGroup(
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun SettingsTileSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
        )
    }
}

@Composable
fun SettingsTileDivider(startPadding: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startPadding, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}
