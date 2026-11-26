package com.petal.browser.ui.containment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.R
import com.petal.browser.ui.theme.PetalText

/** One entry in a [PetalSelectionBar]. Keep [label] to a single short word. */
data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Shared multi-select bottom bar. Actions share the width equally (no clipping), a destructive
 * action is always pinned last and visible, and anything beyond [maxVisible] slots moves into a
 * "More" sheet. The bar itself adds no extra padding: [PetalFloatingToolbar] owns it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSelectionBar(
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
) {
    if (actions.isEmpty()) return
    var showMore by remember { mutableStateOf(false) }

    val destructive = actions.filter { it.destructive }
    val regular = actions.filter { !it.destructive }
    val regularSlots = (maxVisible - destructive.size).coerceAtLeast(1)
    val needsOverflow = regular.size > regularSlots
    val inline = if (needsOverflow) regular.take(regularSlots - 1) else regular
    val overflow = if (needsOverflow) regular.drop(regularSlots - 1) else emptyList()

    PetalFloatingToolbar(modifier = modifier.fillMaxWidth().navigationBarsPadding(), elevation = 6.dp) {
        inline.forEach { SelectionBarItem(it) }
        if (needsOverflow) {
            SelectionBarItem(
                SelectionAction(Icons.Rounded.MoreHoriz, stringResource(R.string.ui_more)) { showMore = true }
            )
        }
        destructive.forEach { SelectionBarItem(it) }
    }

    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Column(Modifier.padding(bottom = 16.dp)) {
                overflow.forEach { action ->
                    ListItem(
                        headlineContent = { Text(action.label) },
                        leadingContent = { Icon(action.icon, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = action.enabled) {
                            showMore = false
                            action.onClick()
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RowScope.SelectionBarItem(action: SelectionAction) {
    val base = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val textColor = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val alpha = if (action.enabled) 1f else 0.38f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = action.enabled, onClick = action.onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(
            action.icon,
            contentDescription = action.label,
            tint = base.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            action.label,
            style = PetalText.chip,
            color = textColor.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
