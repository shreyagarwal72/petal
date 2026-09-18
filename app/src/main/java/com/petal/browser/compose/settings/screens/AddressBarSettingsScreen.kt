package com.petal.browser.compose.settings.screens

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
                SettingsCategoryCard("Position", icon = Icons.Rounded.SwapVert, cardId = "address_bar_position", targetHighlightId = targetHighlightItemId) {
                    Text("Choose where the compact address bar is placed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    com.petal.browser.ui.components.WireframeOptionPicker(
                        options = listOf(
                            com.petal.browser.ui.components.WireframeOption(
                                value = "TOP",
                                label = "Top Bar",
                                previewContent = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Top pill
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        // Content dummy lines
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.7f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            ),
                            com.petal.browser.ui.components.WireframeOption(
                                value = "BOTTOM",
                                label = "Bottom Bar",
                                previewContent = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.7f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        )
                                        // Bottom pill
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            )
                        ),
                        selected = position.uppercase(),
                        onOptionSelected = {
                            viewModel.setPosition(it)
                            (context as? BrowserActivity)?.applyAddressBarPosition()
                            (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                        }
                    )
                }

                SettingsCategoryCard("Size", icon = Icons.Rounded.ViewCompact, cardId = "address_bar_size", targetHighlightId = targetHighlightItemId) {
                    Text("Compact is the recommended short-height layout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    ChoiceRow(
                        options = listOf("COMPACT" to "Compact", "STANDARD" to "Standard"),
                        selected = height,
                        onSelected = { viewModel.setHeight(it) }
                    )
                }

                SettingsCategoryCard("Right-side action", icon = Icons.Rounded.AutoAwesome, cardId = "address_bar_action", targetHighlightId = targetHighlightItemId) {
                    Text("Choose the optional action shown beside the address field.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    ChoiceRow(
                        options = listOf("AI" to "AI", "BOOKMARK" to "Bookmark", "NONE" to "None"),
                        selected = action,
                        onSelected = { viewModel.setAction(it) }
                    )
                }

                SettingsCategoryCard("Gestures & Quick Actions", icon = Icons.Rounded.TouchApp, cardId = "address_bar_gestures", targetHighlightId = targetHighlightItemId) {
                    ToggleRow(
                        title = "Address Bar Horizontal Swipe to Switch Tabs",
                        subtitle = "Swipe left or right across the address bar pill to fluidly switch between open tabs",
                        icon = Icons.Rounded.Swipe,
                        checked = swipeTabs,
                        onCheckedChange = viewModel::setSwipeTabs
                    )
                    ToggleRow(
                        title = "Address Bar Long-Press Quick Actions",
                        subtitle = "Long press the address bar for quick actions: Clean Copy, Paste & Go, Bookmark, and Hard Refresh",
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
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected.equals(value, true), onClick = { onSelected(value) }, label = { androidx.compose.material3.Text(label) }, leadingIcon = if (selected.equals(value, true)) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null)
        }
    }
}
