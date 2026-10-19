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
import androidx.compose.runtime.*
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
    var showPositionExplainer by remember { mutableStateOf(false) }

    PetalFeatureExplainerDialog(
        visible = showPositionExplainer,
        onDismissRequest = { showPositionExplainer = false },
        feature = PetalExplainerFeature(
            type = ExplainerFeatureType.ADDRESS_BAR_POSITION,
            title = "Address Bar Position",
            badge = "ERGONOMICS",
            subtitle = "Floating Island Reachability",
            bullets = listOf(
                "Bottom Bar: Optimized for modern tall phone displays for effortless one-handed thumb navigation.",
                "Top Bar: Classic desktop-style URL bar placement with maximum bottom viewport space.",
                "Transitions seamlessly with smooth squircle morphing animations.",
                "Supports swipe-up gesture to quickly open tabs or search."
            ),
            icon = Icons.Rounded.SwapVert,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.primary
        )
    )

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
                PetalSettingsSection(
                    title = "Position",
                    icon = Icons.Rounded.SwapVert,
                    cardId = "address_bar_position",
                    targetHighlightId = targetHighlightItemId,
                    onInfoClick = { showPositionExplainer = true }
                ) {
                    Text(stringResource(R.string.ui_choose_where_the_compact_address), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    com.petal.browser.ui.components.PetalChoiceButtonGroup(
                        options = listOf(
                            com.petal.browser.ui.components.PetalButtonGroupItem("TOP", "Top Bar"),
                            com.petal.browser.ui.components.PetalButtonGroupItem("BOTTOM", "Bottom Bar"),
                        ),
                        selected = if (position.equals("TOP", ignoreCase = true)) "TOP" else "BOTTOM",
                        onSelect = { selectedPos ->
                            viewModel.setPosition(selectedPos)
                            (context as? BrowserActivity)?.applyAddressBarPosition()
                            (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                        }
                    )
                }

                PetalSettingsSection("Size", icon = Icons.Rounded.ViewCompact, cardId = "address_bar_size", targetHighlightId = targetHighlightItemId) {
                    Text(stringResource(R.string.ui_compact_is_the_recommended_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    com.petal.browser.ui.components.PetalChoiceButtonGroup(
                        options = listOf(
                            com.petal.browser.ui.components.PetalButtonGroupItem("COMPACT", "Compact"),
                            com.petal.browser.ui.components.PetalButtonGroupItem("STANDARD", "Standard"),
                        ),
                        selected = height.uppercase(),
                        onSelect = { viewModel.setHeight(it) }
                    )
                }

                PetalSettingsSection("Right-side action", icon = Icons.Rounded.AutoAwesome, cardId = "address_bar_action", targetHighlightId = targetHighlightItemId) {
                    Text(stringResource(R.string.ui_choose_the_optional_action_shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    com.petal.browser.ui.components.PetalChoiceButtonGroup(
                        options = listOf(
                            com.petal.browser.ui.components.PetalButtonGroupItem("AI", "AI"),
                            com.petal.browser.ui.components.PetalButtonGroupItem("BOOKMARK", "Bookmark"),
                            com.petal.browser.ui.components.PetalButtonGroupItem("NONE", "None"),
                        ),
                        selected = action.uppercase(),
                        onSelect = { viewModel.setAction(it) }
                    )
                }

                PetalSettingsSection("Gestures & Quick Actions", icon = Icons.Rounded.TouchApp, cardId = "address_bar_gestures", targetHighlightId = targetHighlightItemId) {
                    val barsSp = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
                    var hideBarsOnScroll by remember { mutableStateOf(barsSp.getBoolean("sp_hide_bars_on_scroll", true)) }
                    com.petal.browser.ui.containment.PetalGroup(rowCount = 3) { index, position ->
                        when (index) {
                            0 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_address_bar_horizontal_swipe_to),
                                subtitle = stringResource(R.string.ui_swipe_left_or_right_across),
                                icon = Icons.Rounded.Swipe,
                                checked = swipeTabs,
                                position = position,
                                onCheckedChange = viewModel::setSwipeTabs
                            )
                            1 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_address_bar_long_press_quick),
                                subtitle = stringResource(R.string.ui_long_press_the_address_bar),
                                icon = Icons.Rounded.TouchApp,
                                checked = quickActions,
                                position = position,
                                onCheckedChange = viewModel::setQuickActions
                            )
                            2 -> com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                title = "Minimize bars on scroll",
                                subtitle = "Hide the address bar and bottom bar while you scroll down. Scroll up or tap the bubble to bring them back.",
                                icon = Icons.Rounded.SwapVert,
                                checked = hideBarsOnScroll,
                                position = position,
                                onCheckedChange = { on ->
                                    hideBarsOnScroll = on
                                    barsSp.edit().putBoolean("sp_hide_bars_on_scroll", on).apply()
                                    if (!on) (context as? BrowserActivity)?.animateAddressBarCollapse(false)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
