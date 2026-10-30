package com.petal.browser.compose.settings.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.settings.viewmodel.AddressBarSettingsViewModel
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.*
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow

@OptIn(ExperimentalMaterial3Api::class)
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

    val style by viewModel.style.collectAsStateWithLifecycle()
    val mergeLeadingRaw by viewModel.mergeLeading.collectAsStateWithLifecycle()
    val mergeTrailingRaw by viewModel.mergeTrailing.collectAsStateWithLifecycle()
    val mergeLayout by viewModel.mergeLayout.collectAsStateWithLifecycle()
    val collapseStyle by viewModel.collapseStyle.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = remember { PetalHapticEngine.getInstance(context) }
    var showPositionExplainer by remember { mutableStateOf(false) }

    val isMerge = BarPrefs.STYLE_MERGE.equals(style, ignoreCase = true)

    val leadingActions = remember(mergeLeadingRaw) {
        BarAction.parseActionList(mergeLeadingRaw)
    }
    val trailingActions = remember(mergeTrailingRaw) {
        BarAction.parseActionList(mergeTrailingRaw)
    }
    val totalButtons = leadingActions.size + trailingActions.size

    var showAddActionSheetForLeading by remember { mutableStateOf<Boolean?>(null) }

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
                "Merge Bar: Combines URL and navigation controls into a single streamlined row.",
                "Chrome-Style Pill: Shrinks to a small URL chip on scroll down."
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
                subtitle = "Toolbar layout, merge buttons, position and minimize style",
                onBack = onNavigateBack
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Live Interactive Toolbar Preview ─────────────────────────────────
                PetalSettingsSection(
                    title = if (isMerge) "Live Merge Bar Preview" else "Toolbar Preview",
                    icon = Icons.Rounded.Preview,
                    cardId = "address_bar_preview"
                ) {
                    Text(
                        text = if (isMerge) "Real-time preview with current button layout and styling"
                        else "Previewing standard split bar layout",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isMerge) {
                            PetalMergedBar(
                                url = "https://petal.browser.org/features",
                                title = "Petal Browser",
                                progress = 0f,
                                isIncognito = false,
                                isLoading = false,
                                canGoBack = true,
                                canGoForward = true,
                                tabCount = 3,
                                onBackClick = {},
                                onForwardClick = {},
                                onReloadClick = {},
                                onShareClick = {},
                                onAddressClick = {},
                                onHomeClick = {},
                                onNewTabClick = {},
                                onTabsClick = {},
                                onMenuClick = {},
                                onSearchClick = {},
                                onDownloadsClick = {},
                                onFindInPageClick = {},
                                onBookmarkClick = {},
                                onAiResearchClick = {},
                                onMediaClick = {},
                                onSwipeNextTab = {},
                                onSwipePrevTab = {},
                                isReaderEligible = true,
                                isBottom = "BOTTOM".equals(position, ignoreCase = true),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            PetalAddressBar(
                                url = "https://petal.browser.org/features",
                                title = "Petal Browser",
                                progress = 0f,
                                isIncognito = false,
                                isLoading = false,
                                canGoBack = true,
                                onBackClick = {},
                                onShareClick = {},
                                onAddressClick = {},
                                onAiResearchClick = {},
                                onMediaClick = {},
                                onSwipeNextTab = {},
                                onSwipePrevTab = {},
                                isReaderEligible = true,
                                isBottom = "BOTTOM".equals(position, ignoreCase = true),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ── Bar Style Selection (Classic vs Merge) ──────────────────────────
                PetalSettingsSection(
                    title = "Bar Style",
                    icon = Icons.Rounded.DashboardCustomize,
                    cardId = "address_bar_style"
                ) {
                    Text(
                        text = "Choose between separate address/bottom bars or a unified Merge bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem(BarPrefs.STYLE_CLASSIC, "Classic (Split)"),
                            PetalButtonGroupItem(BarPrefs.STYLE_MERGE, "Merge (Unified)")
                        ),
                        selected = if (isMerge) BarPrefs.STYLE_MERGE else BarPrefs.STYLE_CLASSIC,
                        onSelect = { selectedStyle ->
                            haptics.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                            viewModel.setStyle(selectedStyle)
                            (context as? BrowserActivity)?.let { act ->
                                act.applyAddressBarPosition()
                                act.updatePersistentBottomNav()
                            }
                        }
                    )
                }

                // ── Position: Top or Bottom ──────────────────────────────────────────
                PetalSettingsSection(
                    title = "Position",
                    icon = Icons.Rounded.SwapVert,
                    cardId = "address_bar_position",
                    targetHighlightId = targetHighlightItemId,
                    onInfoClick = { showPositionExplainer = true }
                ) {
                    Text(stringResource(R.string.ui_choose_where_the_compact_address), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem("TOP", "Top Bar"),
                            PetalButtonGroupItem("BOTTOM", "Bottom Bar"),
                        ),
                        selected = if (position.equals("TOP", ignoreCase = true)) "TOP" else "BOTTOM",
                        onSelect = { selectedPos ->
                            haptics.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                            viewModel.setPosition(selectedPos)
                            (context as? BrowserActivity)?.applyAddressBarPosition()
                            (context as? BrowserActivity)?.window?.decorView?.post { (context as? BrowserActivity)?.applyAddressBarPosition() }
                        }
                    )
                }

                // ── Merge Bar Customization (Visible only when Merge style is chosen) ──
                if (isMerge) {
                    PetalSettingsSection(
                        title = "Merge Layout",
                        icon = Icons.Rounded.TableRows,
                        cardId = "merge_layout"
                    ) {
                        Text(
                            text = "Single row fits controls side-by-side; Stacked places URL above controls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        PetalChoiceButtonGroup(
                            options = listOf(
                                PetalButtonGroupItem(BarPrefs.LAYOUT_SINGLE, "Single Row"),
                                PetalButtonGroupItem(BarPrefs.LAYOUT_STACKED, "Stacked (2-Row)")
                            ),
                            selected = if (BarPrefs.LAYOUT_STACKED.equals(mergeLayout, ignoreCase = true)) BarPrefs.LAYOUT_STACKED else BarPrefs.LAYOUT_SINGLE,
                            onSelect = { selectedLayout ->
                                haptics.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                viewModel.setMergeLayout(selectedLayout)
                                (context as? BrowserActivity)?.applyAddressBarPosition()
                            }
                        )
                    }

                    // Button Customizer: Leading & Trailing Lists
                    PetalSettingsSection(
                        title = "Toolbar Buttons ($totalButtons / ${BarPrefs.MAX_MERGE_ACTIONS_TOTAL})",
                        icon = Icons.Rounded.Tune,
                        cardId = "merge_buttons"
                    ) {
                        Text(
                            text = "Customize action buttons shown on either side of the URL pill (max 5 total). Menu is always required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        // Leading List Section
                        Text(
                            text = "Leading Buttons (${leadingActions.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        ActionsEditorList(
                            actions = leadingActions,
                            onMoveUp = { idx ->
                                if (idx > 0) {
                                    val list = leadingActions.toMutableList()
                                    val item = list.removeAt(idx)
                                    list.add(idx - 1, item)
                                    viewModel.setMergeLeading(BarAction.serializeActionList(list))
                                }
                            },
                            onMoveDown = { idx ->
                                if (idx < leadingActions.size - 1) {
                                    val list = leadingActions.toMutableList()
                                    val item = list.removeAt(idx)
                                    list.add(idx + 1, item)
                                    viewModel.setMergeLeading(BarAction.serializeActionList(list))
                                }
                            },
                            onRemove = { action ->
                                val list = leadingActions.toMutableList()
                                list.remove(action)
                                viewModel.setMergeLeading(BarAction.serializeActionList(list))
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            FilledTonalButton(
                                onClick = { showAddActionSheetForLeading = true },
                                enabled = totalButtons < BarPrefs.MAX_MERGE_ACTIONS_TOTAL,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Leading")
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Trailing List Section
                        Text(
                            text = "Trailing Buttons (${trailingActions.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        ActionsEditorList(
                            actions = trailingActions,
                            onMoveUp = { idx ->
                                if (idx > 0) {
                                    val list = trailingActions.toMutableList()
                                    val item = list.removeAt(idx)
                                    list.add(idx - 1, item)
                                    viewModel.setMergeTrailing(BarAction.serializeActionList(list))
                                }
                            },
                            onMoveDown = { idx ->
                                if (idx < trailingActions.size - 1) {
                                    val list = trailingActions.toMutableList()
                                    val item = list.removeAt(idx)
                                    list.add(idx + 1, item)
                                    viewModel.setMergeTrailing(BarAction.serializeActionList(list))
                                }
                            },
                            onRemove = { action ->
                                val list = trailingActions.toMutableList()
                                list.remove(action)
                                viewModel.setMergeTrailing(BarAction.serializeActionList(list))
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    haptics.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                    viewModel.setMergeLeading(BarPrefs.DEFAULT_MERGE_LEADING)
                                    viewModel.setMergeTrailing(BarPrefs.DEFAULT_MERGE_TRAILING)
                                    Toast.makeText(context, "Reset toolbar buttons to defaults", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset Defaults")
                            }

                            FilledTonalButton(
                                onClick = { showAddActionSheetForLeading = false },
                                enabled = totalButtons < BarPrefs.MAX_MERGE_ACTIONS_TOTAL,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Trailing")
                            }
                        }
                    }
                }

                // ── Size ─────────────────────────────────────────────────────────────
                PetalSettingsSection("Size", icon = Icons.Rounded.ViewCompact, cardId = "address_bar_size", targetHighlightId = targetHighlightItemId) {
                    Text(stringResource(R.string.ui_compact_is_the_recommended_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem("COMPACT", "Compact"),
                            PetalButtonGroupItem("STANDARD", "Standard"),
                        ),
                        selected = height.uppercase(),
                        onSelect = { viewModel.setHeight(it) }
                    )
                }

                // ── Right-Side Action (Classic bar only) ──────────────────────────────
                if (!isMerge) {
                    PetalSettingsSection("Right-side action", icon = Icons.Rounded.AutoAwesome, cardId = "address_bar_action", targetHighlightId = targetHighlightItemId) {
                        Text(stringResource(R.string.ui_choose_the_optional_action_shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        PetalChoiceButtonGroup(
                            options = listOf(
                                PetalButtonGroupItem("AI", "AI"),
                                PetalButtonGroupItem("BOOKMARK", "Bookmark"),
                                PetalButtonGroupItem("NONE", "None"),
                            ),
                            selected = action.uppercase(),
                            onSelect = { viewModel.setAction(it) }
                        )
                    }
                }

                // ── Minimize on Scroll & Gestures ─────────────────────────────────────
                PetalSettingsSection(
                    title = "Gestures & Minimization",
                    icon = Icons.Rounded.TouchApp,
                    cardId = "address_bar_gestures",
                    targetHighlightId = targetHighlightItemId
                ) {
                    val barsSp = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
                    var hideBarsOnScroll by remember { mutableStateOf(barsSp.getBoolean(BarPrefs.KEY_HIDE_BARS_ON_SCROLL, true)) }

                    PetalGroup(rowCount = 3) { index, groupPos ->
                        when (index) {
                            0 -> PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_address_bar_horizontal_swipe_to),
                                subtitle = stringResource(R.string.ui_swipe_left_or_right_across),
                                icon = Icons.Rounded.Swipe,
                                checked = swipeTabs,
                                position = groupPos,
                                onCheckedChange = viewModel::setSwipeTabs
                            )
                            1 -> PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_address_bar_long_press_quick),
                                subtitle = stringResource(R.string.ui_long_press_the_address_bar),
                                icon = Icons.Rounded.TouchApp,
                                checked = quickActions,
                                position = groupPos,
                                onCheckedChange = viewModel::setQuickActions
                            )
                            2 -> PetalSettingsToggleRow(
                                title = "Minimize bars on scroll",
                                subtitle = "Shrink or hide toolbar while scrolling down on websites.",
                                icon = Icons.Rounded.SwapVert,
                                checked = hideBarsOnScroll,
                                position = groupPos,
                                onCheckedChange = { on ->
                                    hideBarsOnScroll = on
                                    barsSp.edit().putBoolean(BarPrefs.KEY_HIDE_BARS_ON_SCROLL, on).apply()
                                    if (!on) (context as? BrowserActivity)?.animateAddressBarCollapse(false)
                                }
                            )
                        }
                    }

                    if (hideBarsOnScroll) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Scroll Minimize Style",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        PetalChoiceButtonGroup(
                            options = listOf(
                                PetalButtonGroupItem(BarPrefs.COLLAPSE_PILL, "Pill (Domain Chip)"),
                                PetalButtonGroupItem(BarPrefs.COLLAPSE_HIDE, "Hide (Full Slide)")
                            ),
                            selected = if (BarPrefs.COLLAPSE_HIDE.equals(collapseStyle, ignoreCase = true)) BarPrefs.COLLAPSE_HIDE else BarPrefs.COLLAPSE_PILL,
                            onSelect = { selectedStyle ->
                                haptics.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                viewModel.setCollapseStyle(selectedStyle)
                                (context as? BrowserActivity)?.applyAddressBarPosition()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ── Add Button Bottom Sheet ──────────────────────────────────────────────
    if (showAddActionSheetForLeading != null) {
        val forLeading = showAddActionSheetForLeading == true
        val availableCandidates = BarAction.entries.filter { action ->
            action.isAvailable && !leadingActions.contains(action) && !trailingActions.contains(action)
        }

        com.petal.browser.ui.containment.PetalSheet(
            onDismissRequest = { showAddActionSheetForLeading = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (forLeading) "Add Leading Button" else "Add Trailing Button",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (availableCandidates.isEmpty()) {
                    Text(
                        text = "All available actions have already been added.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableCandidates.forEach { candidate ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        haptics.play(PetalHapticEngine.Pattern.CLICK, 0.7f)
                                        showAddActionSheetForLeading = null
                                        if (forLeading) {
                                            val list = leadingActions.toMutableList()
                                            list.add(candidate)
                                            viewModel.setMergeLeading(BarAction.serializeActionList(list))
                                        } else {
                                            val list = trailingActions.toMutableList()
                                            list.add(candidate)
                                            viewModel.setMergeTrailing(BarAction.serializeActionList(list))
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = candidate.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = candidate.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Renders an editable action item row with move up/down and remove buttons.
 */
@Composable
private fun ActionsEditorList(
    actions: List<BarAction>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (BarAction) -> Unit
) {
    if (actions.isEmpty()) {
        Text(
            text = "None",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        actions.forEachIndexed { index, action ->
            val isMenu = action == BarAction.MENU
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Move Up
                    IconButton(
                        onClick = { onMoveUp(index) },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                    }

                    // Move Down
                    IconButton(
                        onClick = { onMoveDown(index) },
                        enabled = index < actions.size - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                    }

                    // Remove
                    if (isMenu) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Required",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onRemove(action) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
