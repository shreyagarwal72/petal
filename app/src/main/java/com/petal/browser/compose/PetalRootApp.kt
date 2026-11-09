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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.bookmarks.PetalBookmarksScreen
import com.petal.browser.compose.downloads.PetalDownloadManagerScreen
import com.petal.browser.compose.engine.ComposeEngineViewport
import com.petal.browser.compose.history.PetalHistoryScreen
import com.petal.browser.compose.home.PetalHomeScreen
import com.petal.browser.compose.settings.PetalSettingsScreen
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.compose.tabs.PetalTabGridSwitcher
import com.petal.browser.compose.tabs.PetalTabGroupManager
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.ui.components.PetalAddressBar
import com.petal.browser.ui.components.PetalBottomNavBar
import com.petal.browser.ui.components.PetalNavTab
import com.petal.browser.ui.components.PetalOmniboxPage
import com.petal.browser.ui.components.PetalOverflowMenuSheet
import com.petal.browser.unit.PetalBrowserUtils

/**
 * PetalRootApp
 * ─────────────────────────────────────────────────────────────────────────────
 * The 100% Jetpack Compose Root Application Shell for Petal Browser.
 *
 * Replaces the legacy 450 KB BrowserActivity and provides unified back-navigation,
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

    // Local Omnibox Input Overlay
    var isOmniboxOpen by remember { mutableStateOf(false) }

    val isHome = activeUrl.isBlank() || PetalBrowserUtils.isHomePage(activeUrl)

    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    // ── Single Authoritative Predictive Back Handler ────────────────────────
    PredictiveBackHandler(enabled = true) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
                backSwipeEdge = backEvent.swipeEdge
            }
            backProgress = 0f
            when {
                isOmniboxOpen -> isOmniboxOpen = false
                viewModel.hasOpenOverlay -> viewModel.dismissTopOverlay()
                !viewModel.goBack() -> activity?.finish()
            }
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isOmniboxOpen && !viewModel.hasOpenOverlay) {
                PetalBottomNavBar(
                    selectedTab = if (isHome) PetalNavTab.HOME else PetalNavTab.SEARCH,
                    tabCount = tabCount,
                    isIncognito = isIncognito,
                    onHomeClick = { viewModel.loadUrl("about:home") },
                    onNewTabClick = { viewModel.openNewTab("about:home", selectTab = true, private = isIncognito) },
                    onTabsClick = { viewModel.toggleTabSwitcher() },
                    onMenuClick = { viewModel.toggleMenu() },
                    onSearchClick = { isOmniboxOpen = true },
                    onSwipeTabLeft = { viewModel.switchToNextTab() },
                    onSwipeTabRight = { viewModel.switchToPreviousTab() },
                    showSearch = !isHome,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val density = LocalDensity.current
        val predictiveScale = 1f - (0.08f * backProgress)
        val predictiveTranslationX = with(density) {
            (if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -24f else 24f) * backProgress
        }
        val predictiveCornerRadius = (28 * backProgress).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .graphicsLayer {
                    scaleX = predictiveScale
                    scaleY = predictiveScale
                    translationX = predictiveTranslationX
                    clip = backProgress > 0f
                    shape = RoundedCornerShape(predictiveCornerRadius)
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Primary Viewport (Home vs Gecko Engine) ─────────────────────
            if (isHome) {
                PetalHomeScreen(
                    onSearch = { query ->
                        viewModel.loadUrl(query)
                    },
                    onOpenShortcutUrl = { url ->
                        viewModel.loadUrl(url)
                    },
                    onOpenBookmarksAction = { viewModel.openBookmarks() },
                    onOpenHistoryAction = { viewModel.openHistory() },
                    onOpenDownloadsAction = { viewModel.openDownloads() },
                    onNewTabAction = { viewModel.openNewTab("about:home", selectTab = true) },
                    onOpenTabSwitcher = { viewModel.toggleTabSwitcher() }
                )
            } else {
                ComposeEngineViewport(
                    tab = activeTab,
                    isLoading = isLoading,
                    onRefresh = { viewModel.reload() },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Address Bar on web content
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
                    onAddressClick = { isOmniboxOpen = true },
                    onHardRefresh = { viewModel.reload() },
                    onSwipeNextTab = { viewModel.switchToNextTab() },
                    onSwipePrevTab = { viewModel.switchToPreviousTab() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .zIndex(2f)
                )
            }

            // ── Fullscreen Overlay Screens ──────────────────────────────────

            // Omnibox Query Page
            if (isOmniboxOpen && activity != null) {
                Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
                    PetalOmniboxPage(
                        activity = activity,
                        initialQuery = if (isHome) "" else activeUrl,
                        pageTitle = activeTitle,
                        pageUrl = activeUrl,
                        isIncognito = isIncognito,
                        onQuerySubmitted = { query ->
                            isOmniboxOpen = false
                            viewModel.loadUrl(query)
                        },
                        onBackPress = { isOmniboxOpen = false }
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
                    isBookmarked = false,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isDesktopSite = false,
                    onDismissRequest = { viewModel.setMenuOpen(false) },
                    onGoBack = { viewModel.goBack() },
                    onGoForward = { viewModel.goForward() },
                    onToggleBookmark = { viewModel.saveBookmark(activeTitle, activeUrl) },
                    onOpenDownloadsShortcut = { viewModel.openDownloads() },
                    onOpenPageInfo = {},
                    onReload = { viewModel.reload() },
                    onToggleDesktopSite = {},
                    onNewTab = { viewModel.openNewTab("about:home", selectTab = true) },
                    onNewIncognitoTab = { viewModel.openNewTab("about:home", selectTab = true, private = true) },
                    onOpenHistory = { viewModel.openHistory() },
                    onDeleteBrowsingData = {},
                    onOpenDownloads = { viewModel.openDownloads() },
                    onOpenBookmarks = { viewModel.openBookmarks() },
                    onInstallPwa = {},
                    onSearchOnSite = { viewModel.openFindInPage() },
                    onPrintPdf = {},
                    onSavePage = {},
                    onShareLink = { viewModel.shareLink(activeTitle, activeUrl) },
                    onViewSource = {},
                    onOpenSettings = { viewModel.openSettings(SettingsCategory.OVERVIEW) }
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
        }
    }
}
