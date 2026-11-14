package com.petal.browser.ui.miuix.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.miuix.theme.LocalMiuiXColors
import com.petal.browser.ui.miuix.theme.MiuiXMotion
import com.petal.browser.ui.miuix.theme.MiuiXShapes
import kotlin.math.roundToInt

/**
 * Xiaomi HyperOS Segmented Pill / Tab Control.
 *
 * An inset continuous squircle track holding sliding spring indicator tabs.
 */
@Composable
fun <T> MiuiXSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String,
) {
    if (items.isEmpty()) return

    val colors = LocalMiuiXColors.current
    val actualIndex = selectedIndex.coerceIn(0, items.size - 1)

    val animatedIndex by animateFloatAsState(
        targetValue = actualIndex.toFloat(),
        animationSpec = MiuiXMotion.standard(),
        label = "miuix_segmented_tab_slide"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MiuiXShapes.Pill)
            .background(colors.track)
            .padding(3.dp)
    ) {
        // Layout with animated indicator under the tab labels
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                // Moving pill indicator
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .shadow(elevation = 2.dp, shape = MiuiXShapes.Pill, clip = false)
                        .clip(MiuiXShapes.Pill)
                        .background(colors.surface)
                )

                // Tab items
                items.forEachIndexed { index, item ->
                    val isSelected = index == actualIndex
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(MiuiXShapes.Pill)
                            .clickable(
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemSelected(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelProvider(item),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val count = items.size
            val tabWidth = constraints.maxWidth / count
            val height = constraints.maxHeight

            val indicatorPlaceable = measurables[0].measure(
                constraints.copy(
                    minWidth = tabWidth,
                    maxWidth = tabWidth,
                    minHeight = height,
                    maxHeight = height
                )
            )

            val tabPlaceables = measurables.drop(1).map { measurable ->
                measurable.measure(
                    constraints.copy(
                        minWidth = tabWidth,
                        maxWidth = tabWidth,
                        minHeight = height,
                        maxHeight = height
                    )
                )
            }

            layout(constraints.maxWidth, height) {
                // Place animated indicator at fractional x
                val indicatorX = (animatedIndex * tabWidth).roundToInt()
                indicatorPlaceable.placeRelative(indicatorX, 0)

                // Place tabs
                tabPlaceables.forEachIndexed { i, placeable ->
                    placeable.placeRelative(i * tabWidth, 0)
                }
            }
        }
    }
}
