package com.petal.browser.compose.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import com.petal.browser.ui.kit.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome

/**
 * Returns the corresponding ImageVector for a curated Zen Workspace icon name.
 */
fun getWorkspaceIconVector(iconName: String): ImageVector {
    return when (iconName) {
        "Home" -> Icons.Rounded.Home
        "Work" -> Icons.Rounded.Work
        "Code" -> Icons.Rounded.Code
        "MenuBook" -> Icons.Rounded.MenuBook
        "Favorite" -> Icons.Rounded.Favorite
        "Star" -> Icons.Rounded.Star
        "ShoppingCart" -> Icons.Rounded.ShoppingCart
        "Explore" -> Icons.Rounded.Explore
        "School" -> Icons.Rounded.School
        "SportsEsports" -> Icons.Rounded.SportsEsports
        "Palette" -> Icons.Rounded.Palette
        "Lightbulb" -> Icons.Rounded.Lightbulb
        else -> Icons.Rounded.Folder
    }
}

/**
 * Zen Browser-style Workspace Carousel.
 * Provides fast horizontal context switching between tab workspaces with Liquid Glass styling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetalWorkspaceCarousel(
    workspaces: List<PetalWorkspace>,
    activeWorkspaceId: String,
    tabCountsByWorkspace: Map<String, Int> = emptyMap(),
    onSelectWorkspace: (String) -> Unit,
    onEditWorkspace: (PetalWorkspace) -> Unit,
    onCreateWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(workspaces, key = { it.id }) { workspace ->
            val isActive = (workspace.id == activeWorkspaceId)
            val wsColor = workspace.parseColor()
            val tabCount = tabCountsByWorkspace[workspace.id] ?: 0

            val containerColor by animateColorAsState(
                targetValue = if (isActive) {
                    wsColor.copy(alpha = if (isGlass) 0.22f else 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isGlass) 0.45f else 0.70f)
                },
                animationSpec = spring(),
                label = "ws_container_color"
            )

            val borderColor by animateColorAsState(
                targetValue = if (isActive) {
                    wsColor.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                animationSpec = spring(),
                label = "ws_border_color"
            )

            val borderWidth by animateDpAsState(
                targetValue = if (isActive) 1.5.dp else 1.dp,
                animationSpec = spring(),
                label = "ws_border_width"
            )

            val shape = RoundedCornerShape(20.dp)

            Surface(
                modifier = Modifier
                    .height(42.dp)
                    .clip(shape)
                    .combinedClickable(
                        role = Role.Tab,
                        onClick = { onSelectWorkspace(workspace.id) },
                        onLongClick = { onEditWorkspace(workspace) }
                    )
                    .then(
                        if (isGlass) {
                            Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = if (isActive) 0.5f else 0.25f)
                        } else {
                            Modifier
                        }
                    ),
                shape = shape,
                color = containerColor,
                border = BorderStroke(borderWidth, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = getWorkspaceIconVector(workspace.iconName),
                        contentDescription = null,
                        tint = if (isActive) wsColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = workspace.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (tabCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isActive) wsColor.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    CircleShape
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (isActive) wsColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item(key = "create_workspace_button") {
            val addShape = CircleShape
            val addContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isGlass) 0.45f else 0.70f)

            IconButton(
                onClick = onCreateWorkspace,
                modifier = Modifier
                    .size(42.dp)
                    .clip(addShape)
                    .then(
                        if (isGlass) {
                            Modifier.liquidGlassChrome(shape = addShape, enabled = true, sheenIntensity = 0.3f)
                        } else {
                            Modifier
                        }
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = addContainerColor
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Create Workspace",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
