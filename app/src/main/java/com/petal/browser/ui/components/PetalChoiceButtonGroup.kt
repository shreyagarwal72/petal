package com.petal.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.liquidGlassChrome

/**
 * Option item model for [PetalChoiceButtonGroup].
 */
data class PetalButtonGroupItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val enabled: Boolean = true,
)

/**
 * Universal pick-one connected toggle button group following Material 3 Expressive guidelines,
 * mirroring the connected button group style in Essentials.
 *
 * Features:
 * - Built as a Row spaced with [ButtonGroupDefaults.ConnectedSpaceBetween].
 * - Uses M3 Expressive [ToggleButton] per option.
 * - Connected shape transitions via [ButtonGroupDefaults.connectedLeadingButtonShapes],
 *   [ButtonGroupDefaults.connectedMiddleButtonShapes], and [ButtonGroupDefaults.connectedTrailingButtonShapes].
 *   The selected button morphs into a full pill shape with spring physics.
 * - Selected container uses primaryContainer / onPrimaryContainer colors.
 * - Unselected container uses near-black dark surface / onSurface colors.
 * - Minimum touch height of 56 dp.
 * - Supports text-only and icon-plus-text options (icon left of label, selected label bold).
 * - Light haptic tick on every selection.
 * - Accessibility semantics role set to [Role.RadioButton].
 * - When items exceed 4 or labels are long, enables smooth horizontal scrolling without text truncation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> PetalChoiceButtonGroup(
    options: List<PetalButtonGroupItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 48.dp,
    scrollableIfCrowded: Boolean = true,
) {
    if (options.isEmpty()) return

    val context = LocalContext.current
    val shouldScroll = scrollableIfCrowded && (options.size > 4 || options.any { it.label.length > 12 })

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")

    val unselectedContainer = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLowest
    val unselectedContent = MaterialTheme.colorScheme.onSurface
    val selectedContainer = if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primaryContainer
    val selectedContent = MaterialTheme.colorScheme.onPrimaryContainer

    val rowModifier = if (shouldScroll) {
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    } else {
        modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val count = options.size
        options.forEachIndexed { index, option ->
            val isChecked = (option.value == selected)

            val baseModifier = if (shouldScroll) {
                Modifier
                    .defaultMinSize(minHeight = minHeight)
                    .semantics { role = Role.RadioButton }
            } else {
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = minHeight)
                    .semantics { role = Role.RadioButton }
            }

            val shapes = when {
                count == 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }

            val currentShape = if (isChecked) shapes.checkedShape else shapes.shape
            val buttonModifier = if (isGlass) {
                baseModifier.liquidGlassChrome(
                    shape = currentShape,
                    enabled = true,
                    sheenIntensity = if (isChecked) 0.80f else 0.45f
                )
            } else {
                baseModifier
            }

            ToggleButton(
                checked = isChecked,
                onCheckedChange = {
                    try {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.TICK, 0.4f)
                    } catch (_: Exception) {}
                    onSelect(option.value)
                },
                modifier = buttonModifier,
                enabled = option.enabled,
                shapes = shapes,
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = unselectedContainer,
                    contentColor = unselectedContent,
                    checkedContainerColor = selectedContainer,
                    checkedContentColor = selectedContent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (option.icon != null) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (option.iconRes != null) {
                        Icon(
                            painter = painterResource(id = option.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (option.label.isNotEmpty()) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overloaded variant for index-based selection with simple label and optional icon lists.
 */
@Composable
fun PetalChoiceButtonGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
    minHeight: androidx.compose.ui.unit.Dp = 48.dp,
    scrollableIfCrowded: Boolean = true,
) {
    val options = labels.mapIndexed { index, label ->
        PetalButtonGroupItem(
            value = index,
            label = label,
            icon = icons?.getOrNull(index)
        )
    }
    PetalChoiceButtonGroup(
        options = options,
        selected = selectedIndex,
        onSelect = onSelectIndex,
        modifier = modifier,
        minHeight = minHeight,
        scrollableIfCrowded = scrollableIfCrowded
    )
}
