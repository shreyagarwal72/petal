package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalText
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
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
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
import com.petal.browser.activity.MainActivity
import com.petal.browser.compose.settings.viewmodel.AddressBarSettingsViewModel
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.*
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.glass.rememberGlassSheen

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

    val floatingTabBar by viewModel.floatingTabBar.collectAsStateWithLifecycle()
    val showNavLabels by viewModel.showNavLabels.collectAsStateWithLifecycle()
    val hideBarsOnScroll by viewModel.hideBarsOnScroll.collectAsStateWithLifecycle()
    val extensionChips by viewModel.extensionChips.collectAsStateWithLifecycle()
    val urlDisplayMode by viewModel.urlDisplayMode.collectAsStateWithLifecycle()
    val progressStyle by viewModel.progressStyle.collectAsStateWithLifecycle()
    val classicOuterAction by viewModel.classicOuterAction.collectAsStateWithLifecycle()
    val classicShowReload by viewModel.classicShowReload.collectAsStateWithLifecycle()
    val navSearchStyle by viewModel.navSearchStyle.collectAsStateWithLifecycle()
    val navSwipeTabs by viewModel.navSwipeTabs.collectAsStateWithLifecycle()
    val navShowOnPages by viewModel.navShowOnPages.collectAsStateWithLifecycle()
    val navMidSlot by viewModel.navMidSlot.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showPositionExplainer by remember { mutableStateOf(false) }

    val isMerge = style == AddressBarStyle.MERGE

    val leadingActions = remember(mergeLeadingRaw) {
        BarAction.parseActionList(mergeLeadingRaw)
    }
    val trailingActions = remember(mergeTrailingRaw) {
        BarAction.parseActionList(mergeTrailingRaw)
    }
    val totalButtons = leadingActions.size + trailingActions.size

    var showAddActionSheetForLeading by remember { mutableStateOf<Boolean?>(null) }

    // Interactive live preview controls
    var previewIsHome by remember { mutableStateOf(false) }
    var previewIncognito by remember { mutableStateOf(false) }
    var previewLoading by remember { mutableStateOf(false) }
    var previewCollapsed by remember { mutableStateOf(false) }
    var previewNavTab by remember { mutableStateOf(PetalNavTab.TABS) }
    LaunchedEffect(previewIsHome) {
        previewNavTab = if (previewIsHome) PetalNavTab.HOME else PetalNavTab.TABS
    }

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
        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            pageBackground = { com.petal.browser.ui.components.PetalPageBackground(pageKey = "address_bar_settings", modifier = Modifier.fillMaxSize()) },
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = "Address Bar",
                    subtitle = "Toolbar layout, merge buttons, position and minimize style",
                    onBack = onNavigateBack
                )
            }
        ) { topInset ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = topInset + 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Live Interactive Stage Preview (Address Bar + Bottom Navbar) ──────
                PetalSettingsSection(
                    title = "Live Interactive Preview",
                    icon = Icons.Rounded.Preview,
                    cardId = "address_bar_preview"
                ) {
                    Text(
                        text = if (isMerge) "Interactive stage showing Merge Bar + Bottom Navbar state in real time"
                        else "Interactive stage showing Classic Address Bar + Bottom Navbar together",
                        style = PetalText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    ScrollFadeRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = previewIsHome,
                            onClick = { previewIsHome = !previewIsHome },
                            label = { Text(if (previewIsHome) "Surface: Home" else "Surface: Web") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (previewIsHome) Icons.Rounded.Home else Icons.Rounded.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = previewIncognito,
                            onClick = { previewIncognito = !previewIncognito },
                            label = { Text(if (previewIncognito) "Incognito" else "Normal") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (previewIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = previewLoading,
                            onClick = { previewLoading = !previewLoading },
                            label = { Text("Loading") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = previewCollapsed && hideBarsOnScroll,
                            enabled = hideBarsOnScroll && !previewIsHome,
                            onClick = { previewCollapsed = !previewCollapsed },
                            label = { Text(if (collapseStyle == BarCollapseStyle.PILL) "Scrolled Pill" else "Scrolled Hidden") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    val previewUrl = if (previewIsHome) "about:home" else "https://petal.browser.org/docs/guide?ref=bar"
                    val previewTitle = if (previewIsHome) "Petal Home" else "Petal Browser — Guide"
                    val isBottomPos = position == AddressBarPosition.BOTTOM
                    val showPreviewNav = previewIsHome || (!isMerge && navShowOnPages)
                    val isCollapsedNow = previewCollapsed && hideBarsOnScroll && !previewIsHome

                    @Composable
                    fun PreviewAddressBarSlot() {
                        if (isCollapsedNow) {
                            if (collapseStyle == BarCollapseStyle.PILL) {
                                PetalMinimizedPill(
                                    domain = "petal.browser.org",
                                    isHttps = true,
                                    isIncognito = previewIncognito,
                                    isBottom = isBottomPos,
                                    progress = if (previewLoading) 0.65f else 0f,
                                    isLoading = previewLoading,
                                    isPreview = true,
                                    onPillClick = { previewCollapsed = false },
                                    onExpand = { previewCollapsed = false },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    onClick = { previewCollapsed = false },
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Bars hidden on scroll · Tap to expand",
                                        style = PetalText.badge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        } else if (isMerge) {
                            PetalMergedBar(
                                url = previewUrl,
                                title = previewTitle,
                                progress = if (previewLoading) 0.65f else 0f,
                                isIncognito = previewIncognito,
                                isLoading = previewLoading,
                                canGoBack = !previewIsHome,
                                canGoForward = false,
                                tabCount = 3,
                                onBackClick = {},
                                onForwardClick = {},
                                onReloadClick = { previewLoading = !previewLoading },
                                onStopClick = { previewLoading = false },
                                onShareClick = {},
                                onAddressClick = {},
                                onHomeClick = { previewIsHome = true },
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
                                isReaderEligible = !previewIsHome,
                                isBottom = isBottomPos,
                                isPreview = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            PetalAddressBar(
                                url = previewUrl,
                                title = previewTitle,
                                progress = if (previewLoading) 0.65f else 0f,
                                isIncognito = previewIncognito,
                                isLoading = previewLoading,
                                canGoBack = !previewIsHome,
                                onBackClick = {},
                                onReloadClick = { previewLoading = !previewLoading },
                                onStopClick = { previewLoading = false },
                                onShareClick = {},
                                onAddressClick = {},
                                onNewTabClick = {},
                                onBookmarksClick = {},
                                onFindInPageClick = {},
                                onDownloadsClick = {},
                                onHistoryClick = {},
                                onAiResearchClick = {},
                                onMediaClick = {},
                                onSwipeNextTab = {},
                                onSwipePrevTab = {},
                                isReaderEligible = !previewIsHome,
                                isBottom = isBottomPos,
                                hasBottomNav = showPreviewNav,
                                isPreview = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = if (previewIncognito) com.petal.browser.ui.theme.IncognitoSurfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isBottomPos) {
                                PreviewAddressBarSlot()
                            }

                            // Mini simulated viewport content so users see contrast, rounding & stacking
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (previewIncognito) Color(0xFF1F1B24) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (previewIsHome) "Petal Home Wallpaper & Shortcuts" else "petal.browser.org — Article Viewport",
                                            style = PetalText.badge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = buildString {
                                                append(if (isMerge) "Merge" else "Classic")
                                                append(" · ")
                                                append(if (isBottomPos) "Bottom" else "Top")
                                                append(" · ")
                                                append(height.barDp)
                                                append("dp")
                                                if (showPreviewNav) {
                                                    append(if (floatingTabBar && !isBottomPos) " · Floating Nav" else " · Docked Nav")
                                                }
                                            },
                                            style = PetalText.caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (isCollapsedNow) "SCROLLED" else "ACTIVE",
                                        style = PetalText.badge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (isBottomPos) {
                                PreviewAddressBarSlot()
                            }

                            if (showPreviewNav && !isCollapsedNow) {
                                PetalBottomNavBar(
                                    selectedTab = previewNavTab,
                                    tabCount = 3,
                                    isIncognito = previewIncognito,
                                    isFloatingStyle = floatingTabBar,
                                    isBottomAddressBar = isBottomPos,
                                    onHomeClick = { previewNavTab = PetalNavTab.HOME; previewIsHome = true },
                                    onAiClick = { previewNavTab = PetalNavTab.AI },
                                    onNewTabClick = { previewNavTab = PetalNavTab.NEW_TAB },
                                    onTabsClick = { previewNavTab = PetalNavTab.TABS; previewIsHome = false },
                                    onMenuClick = { previewNavTab = PetalNavTab.MENU },
                                    onSearchClick = { previewNavTab = PetalNavTab.SEARCH },
                                    showSearch = true,
                                    isPreview = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // ── Bar Style Selection (Classic vs Merge) ──────────────────────────
                PetalSettingsSection(
                    title = "Bar Style & URL Display",
                    icon = Icons.Rounded.DashboardCustomize,
                    cardId = "address_bar_style",
                    highlightIds = listOf("address_bar"),
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = "Choose between separate address/bottom bars or a unified Merge bar.",
                        style = PetalText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem(AddressBarStyle.CLASSIC.pref, "Classic (Split)"),
                            PetalButtonGroupItem(AddressBarStyle.MERGE.pref, "Merge (Unified)")
                        ),
                        selected = style.pref,
                        onSelect = { selectedStyle ->
                            viewModel.setStyle(AddressBarStyle.fromPref(selectedStyle))
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Toolbar Height",
                        style = PetalText.sectionHeader,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem(AddressBarHeight.COMPACT.pref, "Compact (50dp)"),
                            PetalButtonGroupItem(AddressBarHeight.STANDARD.pref, "Standard (56dp)")
                        ),
                        selected = height.pref,
                        onSelect = { selectedH ->
                            viewModel.setHeight(AddressBarHeight.fromPref(selectedH))
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "URL Display Format",
                        style = PetalText.sectionHeader,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Full shows full link + security label; Compact hides path; Domain Only hides path and lock icon on HTTPS.",
                        style = PetalText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem(UrlDisplayMode.FULL.pref, "Full URL"),
                            PetalButtonGroupItem(UrlDisplayMode.COMPACT.pref, "Compact"),
                            PetalButtonGroupItem(UrlDisplayMode.DOMAIN_ONLY.pref, "Domain Only")
                        ),
                        selected = urlDisplayMode.pref,
                        onSelect = { selectedMode ->
                            viewModel.setUrlDisplayMode(UrlDisplayMode.fromPref(selectedMode))
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Loading Progress Indicator",
                        style = PetalText.sectionHeader,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem(BarProgressStyle.LINE.pref, "Progress Line"),
                            PetalButtonGroupItem(BarProgressStyle.HIDDEN.pref, "Hidden")
                        ),
                        selected = progressStyle.pref,
                        onSelect = { selectedProg ->
                            viewModel.setProgressStyle(BarProgressStyle.fromPref(selectedProg))
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
                    Text(stringResource(R.string.ui_choose_where_the_compact_address), style = PetalText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem(AddressBarPosition.TOP.pref, "Top Bar"),
                            PetalButtonGroupItem(AddressBarPosition.BOTTOM.pref, "Bottom Bar"),
                        ),
                        selected = position.pref,
                        onSelect = { selectedPos ->
                            viewModel.setPosition(AddressBarPosition.fromPref(selectedPos))
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
                            style = PetalText.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        PetalChoiceButtonGroup(
                            options = listOf(
                                PetalButtonGroupItem(MergeLayout.SINGLE.pref, "Single Row"),
                                PetalButtonGroupItem(MergeLayout.STACKED.pref, "Stacked (2-Row)")
                            ),
                            selected = mergeLayout.pref,
                            onSelect = { selectedLayout ->
                                viewModel.setMergeLayout(MergeLayout.fromPref(selectedLayout))
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
                            style = PetalText.rowSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        // Leading List Section
                        Text(
                            text = "Leading Buttons (${leadingActions.size})",
                            style = PetalText.sectionHeader,
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
                            val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
                            FilledTonalButton(
                                onClick = {
                                    showAddActionSheetForLeading = true
                                },
                                enabled = totalButtons < BarPrefs.MAX_MERGE_ACTIONS_TOTAL,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.then(
                                    if (isGlass) Modifier.liquidGlassChrome(
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = true,
                                        sheenIntensity = 0.40f,
                                        pressFx = true
                                    ) else Modifier
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), glass = false
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Leading", style = PetalText.button)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Trailing List Section
                        Text(
                            text = "Trailing Buttons (${trailingActions.size})",
                            style = PetalText.sectionHeader,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
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

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
                            TextButton(
                                onClick = {
                                    viewModel.setMergeLeading(BarPrefs.DEFAULT_MERGE_LEADING)
                                    viewModel.setMergeTrailing(BarPrefs.DEFAULT_MERGE_TRAILING)
                                    Toast.makeText(context, "Reset toolbar buttons to defaults", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset Defaults")
                            }

                            FilledTonalButton(
                                onClick = {
                                    showAddActionSheetForLeading = false
                                },
                                enabled = totalButtons < BarPrefs.MAX_MERGE_ACTIONS_TOTAL,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.then(
                                    if (isGlass) Modifier.liquidGlassChrome(
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = true,
                                        sheenIntensity = 0.40f,
                                        pressFx = true
                                    ) else Modifier
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), glass = false
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Trailing", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ── Classic Toolbar Buttons (Classic bar only) ──────────────────────
                if (!isMerge) {
                    PetalSettingsSection("Classic Toolbar Buttons", icon = Icons.Rounded.AutoAwesome, cardId = "address_bar_action", targetHighlightId = targetHighlightItemId) {
                        Text("Pill Right Action", style = PetalText.sectionHeader, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.ui_choose_the_optional_action_shown), style = PetalText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                        Spacer(Modifier.height(12.dp))
                        Text("Far-Right Utility Button", style = PetalText.sectionHeader, color = MaterialTheme.colorScheme.onSurface)
                        Text("Customize the rightmost icon beside the URL pill.", style = PetalText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        ScrollFadeRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                ClassicOuterAction.SHARE to "Share",
                                ClassicOuterAction.NEW_TAB to "New Tab",
                                ClassicOuterAction.BOOKMARKS to "Bookmarks",
                                ClassicOuterAction.FIND_IN_PAGE to "Find",
                                ClassicOuterAction.DOWNLOADS to "Downloads"
                            ).forEach { (item, label) ->
                                FilterChip(
                                    selected = classicOuterAction == item,
                                    onClick = { viewModel.setClassicOuterAction(item) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        PetalGroup(rowCount = 1) { _, groupPos ->
                            PetalSettingsToggleRow(
                                title = "Show Reload / Stop button",
                                subtitle = "Show refresh and stop button beside the outer action button",
                                icon = Icons.Rounded.Refresh,
                                checked = classicShowReload,
                                position = groupPos,
                                onCheckedChange = viewModel::setClassicShowReload
                            )
                        }
                    }
                }

                // ── Bottom Navbar Customization ──────────────────────────────────────
                PetalSettingsSection(
                    title = "Bottom Navbar",
                    icon = Icons.Rounded.SpaceBar,
                    cardId = "bottom_navbar_custom",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = if (position == AddressBarPosition.BOTTOM) "Bottom position automatically uses the Docked navbar layout."
                        else "Customize the floating pill vs docked bottom navigation bar.",
                        style = PetalText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    PetalGroup(rowCount = 4) { index, groupPos ->
                        when (index) {
                            0 -> PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_floating_tab_bar),
                                subtitle = stringResource(R.string.ui_show_the_bottom_bar_as),
                                icon = Icons.Rounded.SpaceBar,
                                checked = floatingTabBar,
                                position = groupPos,
                                onCheckedChange = viewModel::setFloatingTabBar
                            )
                            1 -> PetalSettingsToggleRow(
                                title = stringResource(R.string.ui_show_nav_labels),
                                subtitle = stringResource(R.string.ui_show_nav_labels_desc),
                                icon = Icons.Rounded.Label,
                                checked = showNavLabels,
                                position = groupPos,
                                onCheckedChange = viewModel::setShowNavLabels
                            )
                            2 -> PetalSettingsToggleRow(
                                title = "Show navbar on web pages",
                                subtitle = "When off, classic mode shows the bottom navbar only on Home",
                                icon = Icons.Rounded.Layers,
                                checked = navShowOnPages,
                                position = groupPos,
                                onCheckedChange = viewModel::setNavShowOnPages
                            )
                            3 -> PetalSettingsToggleRow(
                                title = "Swipe navbar to switch tabs",
                                subtitle = "Swipe horizontally across floating or docked bottom navbar",
                                icon = Icons.Rounded.Swipe,
                                checked = navSwipeTabs,
                                position = groupPos,
                                onCheckedChange = viewModel::setNavSwipeTabs
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Search Button Placement", style = PetalText.sectionHeader, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    PetalChoiceButtonGroup(
                        options = listOf(
                            PetalButtonGroupItem(NavSearchButtonStyle.SEPARATE.pref, "Separate"),
                            PetalButtonGroupItem(NavSearchButtonStyle.INTEGRATED.pref, "Integrated"),
                            PetalButtonGroupItem(NavSearchButtonStyle.HIDDEN.pref, "Hidden")
                        ),
                        selected = navSearchStyle.pref,
                        onSelect = { viewModel.setNavSearchStyle(NavSearchButtonStyle.fromPref(it)) }
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("Middle Shortcut Slot", style = PetalText.sectionHeader, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    ScrollFadeRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            NavMidSlot.NEW_TAB to "New Tab",
                            NavMidSlot.BOOKMARKS to "Bookmarks",
                            NavMidSlot.HISTORY to "History",
                            NavMidSlot.DOWNLOADS to "Downloads"
                        ).forEach { (slot, label) ->
                            FilterChip(
                                selected = navMidSlot == slot,
                                onClick = { viewModel.setNavMidSlot(slot) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // ── Minimize on Scroll & Gestures ─────────────────────────────────────
                PetalSettingsSection(
                    title = "Gestures & Minimization",
                    icon = Icons.Rounded.TouchApp,
                    cardId = "address_bar_gestures",
                    targetHighlightId = targetHighlightItemId
                ) {
                    PetalGroup(rowCount = 4) { index, groupPos ->
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
                                    viewModel.setHideBarsOnScroll(on)
                                    if (!on) (context as? MainActivity)?.animateAddressBarCollapse(false)
                                }
                            )
                            3 -> PetalSettingsToggleRow(
                                title = "Extension buttons in address bar",
                                subtitle = "Show extension actions for the current page next to the address.",
                                icon = Icons.Rounded.Extension,
                                checked = extensionChips,
                                position = groupPos,
                                onCheckedChange = viewModel::setExtensionChips
                            )
                        }
                    }

                    if (hideBarsOnScroll) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Scroll Minimize Style",
                            style = PetalText.sectionHeader,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        PetalChoiceButtonGroup(
                            options = listOf(
                                PetalButtonGroupItem(BarCollapseStyle.PILL.pref, "Pill (Domain Chip)"),
                                PetalButtonGroupItem(BarCollapseStyle.HIDE.pref, "Hide (Full Slide)")
                            ),
                            selected = collapseStyle.pref,
                            onSelect = { selectedStyle ->
                                viewModel.setCollapseStyle(BarCollapseStyle.fromPref(selectedStyle))
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
        val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
        val sheen = rememberGlassSheen(isGlass)

        com.petal.browser.ui.containment.PetalSheet(
            onDismissRequest = { showAddActionSheetForLeading = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                // Expressive Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(38.dp)
                                .then(
                                    if (isGlass) Modifier.liquidGlassChrome(
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = true,
                                        sheenIntensity = 0.40f
                                    ) else Modifier
                                )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AddCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (forLeading) "Add Leading Button" else "Add Trailing Button",
                                style = PetalText.sheetTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (forLeading) "Placed before the URL address pill" else "Placed after the URL address pill",
                                style = PetalText.pageSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.then(
                            if (isGlass) Modifier.liquidGlassChrome(
                                shape = RoundedCornerShape(50),
                                enabled = true,
                                sheenIntensity = 0.35f
                            ) else Modifier
                        )
                    ) {
                        Text(
                            text = "${availableCandidates.size} available",
                            style = PetalText.badge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (availableCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All available actions have already been added.",
                            style = PetalText.emptyState,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Scrollable Candidates List Container: Resolves overflow & clipping!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCandidates.forEach { candidate ->
                            val cardShape = RoundedCornerShape(18.dp)
                            Surface(
                                shape = cardShape,
                                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .then(
                                        if (isGlass) Modifier.liquidGlassChrome(
                                            shape = cardShape,
                                            enabled = true,
                                            sheenIntensity = sheen,
                                            pressFx = true
                                        ) else Modifier
                                    )
                                    .clickable {
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
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = candidate.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.label,
                                            style = PetalText.rowTitle,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = getActionDescription(candidate),
                                            style = PetalText.rowSubtitle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(ExpressiveButtonShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

private fun getActionDescription(action: BarAction): String = when (action) {
    BarAction.AI -> "Ask Petal AI assistant about this page"
    BarAction.TABS -> "Switch, view and manage open tabs"
    BarAction.NEW_TAB -> "Instantly open a fresh new tab"
    BarAction.HOME -> "Navigate directly to your home start page"
    BarAction.SEARCH -> "Focus search input and engine picker"
    BarAction.BACK -> "Navigate back to previous web page"
    BarAction.FORWARD -> "Navigate forward in browsing history"
    BarAction.RELOAD -> "Refresh and reload active web page"
    BarAction.SHARE -> "Share current web link with other apps"
    BarAction.BOOKMARK -> "Save current web page to bookmarks"
    BarAction.AI_RESEARCH -> "Deep AI web research and summary"
    BarAction.READER -> "Distraction-free clean reader mode"
    BarAction.DOWNLOADS -> "Open downloaded files manager"
    BarAction.FIND_IN_PAGE -> "Find text and keywords on current page"
    BarAction.HISTORY -> "Open browsing history"
    BarAction.MENU -> "Open browser main overflow menu"
}

/**
 * Renders an editable action item row with move up/down and remove buttons in Petal Containment design.
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
            style = PetalText.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        actions.forEachIndexed { index, action ->
            val isMenu = action == BarAction.MENU
            val cardShape = RoundedCornerShape(14.dp)
            Surface(
                shape = cardShape,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGlass) Modifier.liquidGlassChrome(
                            shape = cardShape,
                            enabled = true,
                            sheenIntensity = sheen
                        ) else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = action.localizedLabel(),
                        style = PetalText.rowTitle,
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
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Required",
                                style = PetalText.badge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
