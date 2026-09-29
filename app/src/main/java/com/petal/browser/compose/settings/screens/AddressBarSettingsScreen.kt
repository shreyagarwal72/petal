package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.containment.PetalSettingsSection

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.settings.viewmodel.AddressBarSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun AddressBarSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AddressBarSettingsViewModel = hiltViewModel()
) {
    val position by viewModel.position.collectAsStateWithLifecycle()
    val height by viewModel.height.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val swipeTabs by viewModel.swipeTabs.collectAsStateWithLifecycle()
    val quickActions by viewModel.quickActions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "address_bar_settings")
        Column(Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Address Bar",
                subtitle = "Position, size, gestures and toolbar actions",
                onBack = onNavigateBack
            )
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PetalSettingsSection("Position", icon = Icons.Rounded.SwapVert, cardId = "address_bar_position", targetHighlightId = targetHighlightItemId) {
                    Text(stringResource(R.string.ui_choose_where_the_compact_address), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    com.petal.browser.ui.containment.PetalConnectedButtonGroup(
                        items = listOf(
                            com.petal.browser.ui.containment.PetalConnectedButtonItem("Top Bar"),
                            com.petal.browser.ui.containment.PetalConnectedButtonItem("Bottom Bar"),
                        ),
                        selectedIndex = if (position.equals("TOP", ignoreCase = true)) 0 else 1,
                        onSelect = { selected ->
                            viewModel.setPosition(if (selected == 0) "TOP" else "BOTTOM")
                            (context as? BrowserActivity)?.applyAddressBarPosition()
                            (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                        }
                    )
                }

                PetalSettingsSection("Size", icon = Icons.Rounded.ViewCompact, cardId = "address_bar_size", targetHighlightId = targetHighlightItemId) {
                    Text(stringResource(R.string.ui_compact_is_the_recommended_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    ChoiceRow(
                        options = listOf("COMPACT" to "Compact", "STANDARD" to "Standard"),
                        selected = height,
                        onSelected = { viewModel.setHeight(it) }
                    )
                }

                PetalSettingsSection("Right-side action", icon = Icons.Rounded.AutoAwesome, cardId = "address_bar_action", targetHighlightId = targetHighlightItemId) {
                    Text(stringResource(R.string.ui_choose_the_optional_action_shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    ChoiceRow(
                        options = listOf("AI" to "AI", "BOOKMARK" to "Bookmark", "NONE" to "None"),
                        selected = action,
                        onSelected = { viewModel.setAction(it) }
                    )
                }

                PetalSettingsSection("Gestures & Quick Actions", icon = Icons.Rounded.TouchApp, cardId = "address_bar_gestures", targetHighlightId = targetHighlightItemId) {
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_address_bar_horizontal_swipe_to),
                        subtitle = stringResource(R.string.ui_swipe_left_or_right_across),
                        icon = Icons.Rounded.Swipe,
                        checked = swipeTabs,
                        onCheckedChange = viewModel::setSwipeTabs
                    )
                    com.petal.browser.ui.containment.PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_address_bar_long_press_quick),
                        subtitle = stringResource(R.string.ui_long_press_the_address_bar),
                        icon = Icons.Rounded.TouchApp,
                        checked = quickActions,
                        onCheckedChange = viewModel::setQuickActions
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    com.petal.browser.ui.containment.PetalConnectedButtonGroup(
        items = options.map { (_, label) -> com.petal.browser.ui.containment.PetalConnectedButtonItem(label) },
        selectedIndex = options.indexOfFirst { it.first.equals(selected, true) },
        onSelect = { index -> options.getOrNull(index)?.first?.let(onSelected) },
    )
}
