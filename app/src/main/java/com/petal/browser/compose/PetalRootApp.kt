package com.petal.browser.compose

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.preference.PreferenceManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.BarCollapseState
import com.petal.browser.ui.components.PetalSpring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import com.petal.browser.account.mozilla.FirefoxAccountSyncScreen
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.bookmarks.PetalBookmarksScreen
import com.petal.browser.compose.downloads.PetalDownloadManagerScreen
import com.petal.browser.compose.engine.ComposeEngineViewport
import com.petal.browser.compose.extensions.PetalExtensionPopupScreen
import com.petal.browser.compose.extensions.PetalExtensionsMenuSheet
import com.petal.browser.compose.extensions.PetalExtensionsScreen
import com.petal.browser.compose.history.PetalHistoryScreen
import com.petal.browser.compose.home.PetalHomeScreen
import com.petal.browser.compose.find.PetalFindInPageBar
import com.petal.browser.compose.settings.PetalDeleteScreen
import com.petal.browser.compose.settings.PetalSettingsScreen
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.privacy.SafeLockerScreen
import com.petal.browser.compose.tabs.PetalTabGridSwitcher
import com.petal.browser.compose.tabs.PetalTabGroupManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.ui.components.PetalAddressBar
import com.petal.browser.ui.components.PetalMergedBar
import com.petal.browser.ui.components.BarPrefs
import com.petal.browser.appleduo.appleDuoFoldEffect
import com.petal.browser.ui.components.PetalAiSearchResultSheet
import com.petal.browser.ui.components.PetalBottomNavBar
import com.petal.browser.ui.components.PetalNavTab
import com.petal.browser.ui.components.PetalOmniboxPage
import com.petal.browser.ui.components.PetalOverflowMenuSheet
import com.petal.browser.ui.components.PetalSiteInfoBridge
import com.petal.browser.activity.MainActivity
import com.petal.browser.unit.PetalBrowserUtils

/**
 * PetalRootApp
 * ─────────────────────────────────────────────────────────────────────────────
 * The 100% Jetpack Compose Root Application Shell for Petal Browser.
 *
 * Replaces the legacy 450 KB MainActivity and provides unified back-navigation,
 * Material 3 Expressive motion curves, and Liquid Glass composition over the
 * Mozilla GeckoView engine.
 */
@Composable
fun PetalRootApp(
    viewModel: PetalBrowserViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Observe Browser ViewModel State
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val activeUrl by viewModel.activeUrl.collectAsStateWithLifecycle()
    val activeTitle by viewModel.activeTitle.collectAsStateWithLifecycle()
    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val isIncognito by viewModel.isIncognito.collectAsStateWithLifecycle()
    val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()
    val tabItems by viewModel.tabItems.collectAsStateWithLifecycle()

    // Overlay visibility states
    val isTabSwitcherOpen by viewModel.isTabSwitcherOpen.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isBookmarksOpen by viewModel.isBookmarksOpen.collectAsStateWithLifecycle()
    val isHistoryOpen by viewModel.isHistoryOpen.collectAsStateWithLifecycle()
    val isDownloadsOpen by viewModel.isDownloadsOpen.collectAsStateWithLifecycle()
    val isMenuOpen by viewModel.isMenuOpen.collectAsStateWithLifecycle()
    val isExtensionsOpen by viewModel.isExtensionsOpen.collectAsStateWithLifecycle()
    val isAccountSyncOpen by viewModel.isAccountSyncOpen.collectAsStateWithLifecycle()
    val isDeleteOpen by viewModel.isDeleteOpen.collectAsStateWithLifecycle()
    val isSafeLockerOpen by viewModel.isSafeLockerOpen.collectAsStateWithLifecycle()
    val isFindInPageOpen by viewModel.isFindInPageOpen.collectAsStateWithLifecycle()
    val findInPageQuery by viewModel.findInPageQuery.collectAsStateWithLifecycle()
    val isAiSheetOpen by viewModel.isAiSheetOpen.collectAsStateWithLifecycle()
    val aiSheetQuery by viewModel.aiSheetQuery.collectAsStateWithLifecycle()
    val isDesktopMode by viewModel.isDesktopMode.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentUrlBookmarked.collectAsStateWithLifecycle()
    val pendingExtensionPopup by PetalExtensionManager.pendingPopup.collectAsStateWithLifecycle()
    val settingsCategory by viewModel.settingsCategory.collectAsStateWithLifecycle()

    val rootView = remember(activity) {
        activity?.findViewById<android.view.View>(android.R.id.content) ?: activity?.window?.decorView
    }

    val tabSwitcherSnapshot = remember(isTabSwitcherOpen) {
        if (isTabSwitcherOpen && rootView != null) {
            PetalContentSnapshot.capture(rootView)
            PetalContentSnapshot.current?.asImageBitmap()
        } else null
    }

    DisposableEffect(isTabSwitcherOpen) {
        onDispose {
            if (!isTabSwitcherOpen) {
                PetalContentSnapshot.clear()
            }
        }
    }

    // Preferences for Bottom Nav Bar and Address Bar
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var floatingTabBar by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }
    var addressBarPosition by remember { mutableStateOf(sp.getString("sp_address_bar_position", "TOP") ?: "TOP") }
    var addressBarStyle by remember { mutableStateOf(sp.getString(BarPrefs.KEY_ADDRESS_BAR_STYLE, BarPrefs.STYLE_CLASSIC) ?: BarPrefs.STYLE_CLASSIC) }

    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sp_floating_tab_bar" -> floatingTabBar = sp.getBoolean("sp_floating_tab_bar", true)
                "sp_address_bar_position", BarPrefs.KEY_ADDRESS_BAR_POSITION -> {
                    addressBarPosition = sp.getString("sp_address_bar_position", "TOP") ?: "TOP"
                }
                BarPrefs.KEY_ADDRESS_BAR_STYLE -> {
                    addressBarStyle = sp.getString(BarPrefs.KEY_ADDRESS_BAR_STYLE, BarPrefs.STYLE_CLASSIC) ?: BarPrefs.STYLE_CLASSIC
                }
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val isBottomAddressBar = "BOTTOM".equals(addressBarPosition, ignoreCase = true)
    val isMerge = BarPrefs.STYLE_MERGE.equals(addressBarStyle, ignoreCase = true)
    val effectiveFloating = floatingTabBar && !isBottomAddressBar
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }

    // Synchronize page state with BarCollapseState
    LaunchedEffect(activeUrl, isIncognito) {
        BarCollapseState.updatePage(activeUrl, isIncognito)
        BarCollapseState.setFraction(0f)
    }
    LaunchedEffect(Unit) {
        BarCollapseState.onExpandRequested = {
            BarCollapseState.setFraction(0f)
        }
    }

    // Omnibox Input Overlay State (Single authoritative source of truth via ViewModel)
    val isOmniboxOpen by viewModel.isOmniboxOpen.collectAsStateWithLifecycle()

    val isHome = activeUrl.isBlank() || PetalBrowserUtils.isHomePage(activeUrl)

    val lastExitBackAt = remember { longArrayOf(0L) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    // Overlay tracking and Bottom Nav visibility
    val isOverlayShowing = isOmniboxOpen ||
            isTabSwitcherOpen ||
            isBookmarksOpen ||
            isHistoryOpen ||
            isDownloadsOpen ||
            isSettingsOpen ||
            isExtensionsOpen ||
            isAccountSyncOpen ||
            isDeleteOpen ||
            isSafeLockerOpen ||
            pendingExtensionPopup != null

    val isBottomBarVisible = !isOverlayShowing && (!isMerge || isHome)

    val selectedTab = when {
        isMenuOpen -> PetalNavTab.MENU
        isHome -> PetalNavTab.HOME
        else -> PetalNavTab.TABS
    }

    // ── Single Authoritative Predictive Back Handler ────────────────────────
    PredictiveBackHandler(enabled = !isOmniboxOpen) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
                backSwipeEdge = backEvent.swipeEdge
            }
            backProgress = 0f
            when {
                (activity as? MainActivity)?.hasPresentedScreens == true -> (activity as? MainActivity)?.dismissTopPresentedScreen()
                pendingExtensionPopup != null -> PetalExtensionManager.dismissPopup()
                isOmniboxOpen -> viewModel.setOmniboxOpen(false)
                viewModel.hasOpenOverlay -> viewModel.dismissTopOverlay()
                !viewModel.goBack() -> {
                    // "Double Back to Exit" (sp_double_back_exit): first back shows a hint,
                    // a second back within 2s exits. Read live so the toggle applies instantly.
                    val doubleBack = PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean("sp_double_back_exit", true)
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (doubleBack && now - lastExitBackAt[0] > 2000L) {
                        lastExitBackAt[0] = now
                        com.petal.browser.view.PetalToast.show(
                            context,
                            com.petal.browser.R.string.ui_press_back_twice_quickly_to
                        )
                    } else {
                        activity?.finish()
                    }
                }
            }
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    val density = LocalDensity.current
    val navBarBottomInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val predictiveScale = 1f - (0.08f * backProgress)
    val predictiveTranslationX = with(density) {
        (if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -24f else 24f) * backProgress
    }
    val predictiveCornerRadius = (28 * backProgress).dp

    Box(modifier = modifier.fillMaxSize()) {
        // ── Primary Viewport (Home vs Gecko Engine) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!effectiveFloating && isBottomBarVisible) {
                        Modifier.padding(bottom = 64.dp + navBarBottomInset)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    scaleX = predictiveScale
                    scaleY = predictiveScale
                    translationX = predictiveTranslationX
                    clip = backProgress > 0f
                    shape = RoundedCornerShape(predictiveCornerRadius)
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isHome) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .appleDuoFoldEffect(isWebsite = false)
                ) {
                    PetalHomeScreen(
                        onSearch = { query ->
                            if (query.isBlank()) {
                                viewModel.openOmnibox()
                            } else {
                                viewModel.loadUrl(query)
                            }
                        },
                        onOpenShortcutUrl = { url ->
                            viewModel.loadUrl(url)
                        },
                        onOpenAccountSync = { viewModel.openAccountSync() },
                        onOpenBookmarksAction = { viewModel.openBookmarks() },
                        onOpenHistoryAction = { viewModel.openHistory() },
                        onOpenDownloadsAction = { viewModel.openDownloads() },
                        onNewTabAction = { viewModel.openNewTab("about:home", selectTab = true) },
                        onOpenTabSwitcher = { viewModel.toggleTabSwitcher() }
                    )
                }
            } else {
                ComposeEngineViewport(
                    tab = activeTab,
                    isLoading = isLoading,
                    onRefresh = { viewModel.reload() },
                    modifier = Modifier
                        .fillMaxSize()
                        .appleDuoFoldEffect(isWebsite = true)
                )

                if (isMerge) {
                    PetalMergedBar(
                        url = activeUrl,
                        title = activeTitle.ifBlank { activeUrl },
                        progress = loadProgress / 100f,
                        isIncognito = isIncognito,
                        isLoading = isLoading,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        tabCount = tabCount,
                        onBackClick = { viewModel.goBack() },
                        onForwardClick = { viewModel.goForward() },
                        onReloadClick = { viewModel.reload() },
                        onShareClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, activeUrl)
                            }
                            activity?.startActivity(android.content.Intent.createChooser(shareIntent, "Share Link"))
                        },
                        onAddressClick = { viewModel.openOmnibox() },
                        onHomeClick = {
                            haptic.playClick(context)
                            viewModel.loadUrl("about:home")
                        },
                        onNewTabClick = {
                            haptic.playClick(context)
                            viewModel.openNewTab("about:home", selectTab = true, private = isIncognito)
                        },
                        onTabsClick = {
                            haptic.playClick(context)
                            viewModel.toggleTabSwitcher()
                        },
                        onMenuClick = {
                            haptic.playClick(context)
                            viewModel.toggleMenu()
                        },
                        onSearchClick = { viewModel.openOmnibox() },
                        onDownloadsClick = { viewModel.openDownloads() },
                        onFindInPageClick = { viewModel.openFindInPage() },
                        onBookmarkClick = { viewModel.toggleBookmark(activeTitle, activeUrl) },
                        onAiResearchClick = { viewModel.openAiSheet(activeTitle) },
                        onMediaClick = {
                            com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.openMediaSheet()
                        },
                        onSwipeNextTab = { viewModel.switchToNextTab() },
                        onSwipePrevTab = { viewModel.switchToPreviousTab() },
                        onHardRefresh = { viewModel.reload() },
                        onReaderClick = { (activity as? MainActivity)?.showReaderMode() },
                        onCustomizeClick = { viewModel.openSettings(com.petal.browser.compose.settings.SettingsCategory.ADDRESS_BAR) },
                        isBottom = isBottomAddressBar,
                        modifier = if (isBottomAddressBar) {
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .zIndex(2f)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .zIndex(2f)
                        }
                    )
                } else {
                    PetalAddressBar(
                        url = activeUrl,
                        title = activeTitle.ifBlank { activeUrl },
                        progress = loadProgress / 100f,
                        isIncognito = isIncognito,
                        isLoading = isLoading,
                        canGoBack = canGoBack,
                        onBackClick = { viewModel.goBack() },
                        onShareClick = {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, activeUrl)
                            }
                            activity?.startActivity(android.content.Intent.createChooser(shareIntent, "Share Link"))
                        },
                        onAddressClick = { viewModel.openOmnibox() },
                        onAiResearchClick = { viewModel.openAiSheet(activeTitle) },
                        onReaderClick = { (activity as? MainActivity)?.showReaderMode() },
                        onHardRefresh = { viewModel.reload() },
                        onSwipeNextTab = { viewModel.switchToNextTab() },
                        onSwipePrevTab = { viewModel.switchToPreviousTab() },
                        isBottom = isBottomAddressBar,
                        hasBottomNav = isBottomBarVisible,
                        modifier = if (isBottomAddressBar) {
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (isBottomBarVisible) 64.dp + navBarBottomInset else 0.dp)
                                .zIndex(2f)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .zIndex(2f)
                        }
                    )
                }
            }
        }

        // ── Persistent Bottom Navigation Bar ────────────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = isBottomBarVisible,
            enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.fadeIn()) +
                    androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.enter()) { it / 2 },
            exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.fadeOut()) +
                   androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.exit()) { it / 2 },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(5f)
        ) {
            PetalBottomNavBar(
                selectedTab = selectedTab,
                tabCount = tabCount,
                isIncognito = isIncognito,
                isFloatingStyle = floatingTabBar,
                isBottomAddressBar = isBottomAddressBar,
                glassSource = rootView,
                onHomeClick = {
                    haptic.playClick(context)
                    viewModel.loadUrl("about:home")
                },
                onNewTabClick = {
                    haptic.playClick(context)
                    viewModel.openNewTab("about:home", selectTab = true, private = isIncognito)
                },
                onTabsClick = {
                    haptic.playClick(context)
                    viewModel.toggleTabSwitcher()
                },
                onMenuClick = {
                    haptic.playClick(context)
                    viewModel.toggleMenu()
                },
                onSearchClick = {
                    haptic.playClick(context)
                    viewModel.openOmnibox()
                },
                onSwipeTabLeft = {
                    haptic.play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                    viewModel.switchToPreviousTab()
                },
                onSwipeTabRight = {
                    haptic.play(PetalHapticEngine.Pattern.CLICK, 0.6f)
                    viewModel.switchToNextTab()
                },
                showSearch = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

            // ── Fullscreen Overlay Screens ──────────────────────────────────

            // Omnibox Query Page with Seamless Enter/Exit Transition
            if (isOmniboxOpen && activity != null) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isOmniboxOpen,
                    enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.fadeIn()) +
                            androidx.compose.animation.scaleIn(
                                initialScale = 0.94f,
                                animationSpec = PetalSpring.enter()
                            ),
                    exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.fadeOut()) +
                           androidx.compose.animation.scaleOut(
                               targetScale = 0.94f,
                               animationSpec = PetalSpring.exit()
                           ),
                    modifier = Modifier.fillMaxSize().zIndex(10f)
                ) {
                    PetalOmniboxPage(
                        activity = activity!!,
                        initialQuery = if (isHome) "" else activeUrl,
                        pageTitle = activeTitle,
                        pageUrl = activeUrl,
                        isIncognito = isIncognito,
                        onQuerySubmitted = { query ->
                            viewModel.setOmniboxOpen(false)
                            viewModel.loadUrl(query)
                        },
                        onBackPress = { viewModel.setOmniboxOpen(false) }
                    )
                }
            }

            // Tab Switcher Grid
            if (isTabSwitcherOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(9f)) {
                    PetalTabGridSwitcher(
                        backgroundSnapshot = tabSwitcherSnapshot,
                        tabs = tabItems,
                        onTabSelect = { tab ->
                            viewModel.selectTab(tab.id)
                        },
                        onTabClose = { tab ->
                            viewModel.closeTab(tab.id)
                        },
                        onNewTab = { privateMode ->
                            viewModel.openNewTab("about:home", selectTab = true, private = privateMode)
                        },
                        onCloseAllTabs = {
                            viewModel.closeAllTabs()
                        },
                        onRestoreTab = { tab ->
                            viewModel.restoreTab(tab)
                        },
                        onDuplicateTab = { tab ->
                            viewModel.duplicateTab(tab)
                        },
                        onCloseOtherTabs = { tab ->
                            viewModel.closeOtherTabs(tab)
                        },
                        onTabVisible = { tab ->
                            viewModel.loadTabThumbnailAsync(tab.id, tab.isIncognito)
                        },
                        onBookmarkTab = { tab ->
                            viewModel.saveBookmark(tab.title, tab.url)
                        },
                        onShareTab = { tab ->
                            viewModel.shareLink(tab.title, tab.url)
                        },
                        onCreateGroup = { tab ->
                            PetalTabGroupManager.createGroupWithTab(context, tab)
                            viewModel.notifyMetadataChanged()
                        },
                        onOpenSettings = {
                            viewModel.openSettings(SettingsCategory.TABS)
                        },
                        onBack = { viewModel.setTabSwitcherOpen(false) }
                    )
                }
            }

            // Overflow Menu Sheet
            if (isMenuOpen) {
                PetalOverflowMenuSheet(
                    pageTitle = activeTitle,
                    pageUrl = activeUrl,
                    isBookmarked = isBookmarked,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isDesktopSite = isDesktopMode,
                    onDismissRequest = { viewModel.setMenuOpen(false) },
                    onGoBack = { viewModel.goBack() },
                    onGoForward = { viewModel.goForward() },
                    onToggleBookmark = { viewModel.toggleBookmark(activeTitle, activeUrl) },
                    onOpenDownloadsShortcut = {
                        viewModel.setMenuOpen(false)
                        viewModel.openDownloads()
                    },
                    onOpenPageInfo = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.let { act ->
                            PetalSiteInfoBridge.showSiteInfoBottomSheet(act, act.currentAlbumController) {}
                        }
                    },
                    onReload = {
                        viewModel.setMenuOpen(false)
                        viewModel.reload()
                    },
                    onToggleDesktopSite = { enabled ->
                        viewModel.toggleDesktopMode(enabled)
                    },
                    onNewTab = {
                        viewModel.setMenuOpen(false)
                        viewModel.openNewTab("about:home", selectTab = true)
                    },
                    onNewIncognitoTab = {
                        viewModel.setMenuOpen(false)
                        viewModel.openNewTab("about:home", selectTab = true, private = true)
                    },
                    onOpenHistory = {
                        viewModel.setMenuOpen(false)
                        viewModel.openHistory()
                    },
                    onDeleteBrowsingData = {
                        viewModel.setMenuOpen(false)
                        viewModel.openDelete()
                    },
                    onOpenDownloads = {
                        viewModel.setMenuOpen(false)
                        viewModel.openDownloads()
                    },
                    onOpenBookmarks = {
                        viewModel.setMenuOpen(false)
                        viewModel.openBookmarks()
                    },
                    onInstallPwa = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.installPwaShortcut()
                    },
                    onSearchOnSite = {
                        viewModel.setMenuOpen(false)
                        viewModel.openFindInPage()
                    },
                    onPrintPdf = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.createWebPrintJob(null)
                    },
                    onSavePage = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.savePageOffline()
                    },
                    onShareLink = {
                        viewModel.setMenuOpen(false)
                        viewModel.shareLink(activeTitle, activeUrl)
                    },
                    onViewSource = {
                        viewModel.setMenuOpen(false)
                        if (activeUrl.isNotBlank() && !activeUrl.startsWith("about:") && !activeUrl.startsWith("view-source:")) {
                            viewModel.loadUrl("view-source:$activeUrl")
                        }
                    },
                    onOpenExtensions = {
                        viewModel.setMenuOpen(false)
                        viewModel.openExtensions()
                    },
                    onOpenExtensionsMenu = {
                        viewModel.setMenuOpen(false)
                        activity?.let {
                            PetalExtensionsMenuSheet.show(it, activeUrl) {
                                viewModel.openExtensions()
                            }
                        } ?: viewModel.openExtensions()
                    },
                    onOpenExtensionSettings = { extensionId ->
                        viewModel.setMenuOpen(false)
                        activity?.let { PetalExtensionManager.openOptionsPage(extensionId, it) }
                    },
                    onOpenSettings = {
                        viewModel.setMenuOpen(false)
                        viewModel.openSettings(SettingsCategory.OVERVIEW)
                    }
                )
            }

            // Bookmarks Screen
            if (isBookmarksOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    PetalBookmarksScreen(
                        onOpenUrl = { url ->
                            viewModel.setBookmarksOpen(false)
                            viewModel.loadUrl(url)
                        },
                        onDismiss = { viewModel.setBookmarksOpen(false) }
                    )
                }
            }

            // History Screen
            if (isHistoryOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    PetalHistoryScreen(
                        onOpenUrl = { url ->
                            viewModel.setHistoryOpen(false)
                            viewModel.loadUrl(url)
                        },
                        onDismiss = { viewModel.setHistoryOpen(false) }
                    )
                }
            }

            // Downloads Screen
            if (isDownloadsOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    PetalDownloadManagerScreen(
                        onBackPress = { viewModel.setDownloadsOpen(false) }
                    )
                }
            }

            // Settings Screen
            if (isSettingsOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    PetalSettingsScreen(
                        initialCategory = settingsCategory,
                        onBackPress = { viewModel.setSettingsOpen(false) }
                    )
                }
            }

            // Extensions Screen
            if (isExtensionsOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    PetalExtensionsScreen(
                        backgroundSnapshot = tabSwitcherSnapshot,
                        onDismiss = { viewModel.setExtensionsOpen(false) }
                    )
                }
            }

            // Firefox Account & Sync Screen
            if (isAccountSyncOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    FirefoxAccountSyncScreen(
                        backgroundSnapshot = tabSwitcherSnapshot,
                        onBack = { viewModel.setAccountSyncOpen(false) },
                        onOpenOAuth = { shortcut ->
                            viewModel.setAccountSyncOpen(false)
                            viewModel.loadUrl(shortcut.url)
                        }
                    )
                }
            }

            // Delete Browsing Data Screen Overlay
            if (isDeleteOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    PetalDeleteScreen(
                        backgroundSnapshot = tabSwitcherSnapshot,
                        onBackPress = { viewModel.setDeleteOpen(false) }
                    )
                }
            }

            // Biometric Safe Locker Screen Overlay
            if (isSafeLockerOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(8f)) {
                    SafeLockerScreen(
                        onBackPress = { viewModel.setSafeLockerOpen(false) }
                    )
                }
            }

            // WebExtension Interactive Popup Overlay
            if (pendingExtensionPopup != null) {
                Box(modifier = Modifier.fillMaxSize().zIndex(12f)) {
                    PetalExtensionPopupScreen(
                        popup = pendingExtensionPopup!!,
                        onDismiss = { PetalExtensionManager.dismissPopup() }
                    )
                }
            }

            // Find In Page Bar Overlay
            if (isFindInPageOpen) {
                PetalFindInPageBar(
                    visible = isFindInPageOpen,
                    query = findInPageQuery,
                    onQueryChange = { query ->
                        viewModel.findAll(query)
                    },
                    onFindNext = {
                        viewModel.findNext(true)
                    },
                    onFindPrevious = {
                        viewModel.findNext(false)
                    },
                    onClose = {
                        viewModel.clearFindMatches()
                    },
                    modifier = Modifier.fillMaxWidth().zIndex(11f)
                )
            }

            // AI Search Result Bottom Sheet Overlay
            if (isAiSheetOpen) {
                Box(modifier = Modifier.fillMaxSize().zIndex(9f)) {
                    PetalAiSearchResultSheet(
                        query = aiSheetQuery,
                        onDismiss = { viewModel.setAiSheetOpen(false) }
                    )
                }
            }
    }
}
