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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.BarCollapseState
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.components.PetalOmniboxCommand
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.petal.browser.browser.PetalTabHistoryState
import com.petal.browser.ui.components.PetalTabHistorySheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.petal.browser.predictive.PetalBackInput
import com.petal.browser.predictive.PetalBackMotion
import com.petal.browser.predictive.PetalPredictiveJunction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import com.petal.browser.account.PetalAccountScreen
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.bookmarks.PetalBookmarksScreen
import com.petal.browser.compose.downloads.PetalDownloadManagerScreen
import com.petal.browser.compose.engine.ComposeEngineViewport
import com.petal.browser.compose.extensions.PetalExtensionPopupScreen
import com.petal.browser.compose.extensions.PetalExtensionsMenuSheet
import com.petal.browser.compose.extensions.PetalExtensionsScreen
import com.petal.browser.compose.history.PetalHistoryScreen
import com.petal.browser.compose.home.PetalHomeScreen
import com.petal.browser.compose.incognito.PetalIncognitoHomeScreen
import com.petal.browser.compose.incognito.PetalIncognitoSessionManager
import com.petal.browser.compose.find.PetalFindInPageBar
import com.petal.browser.compose.settings.PetalDeleteScreen
import com.petal.browser.compose.settings.PetalSettingsScreen
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.privacy.SafeLockerScreen
import com.petal.browser.compose.tabs.PetalTabGridSwitcher
import com.petal.browser.compose.tabs.PetalTabGroupManager
import com.petal.browser.compose.tabs.PetalDesktopTabBar
import com.petal.browser.compose.tabs.PetalDesktopTabBarBridge
import com.petal.browser.compose.tabs.PetalDesktopTabBarHandler
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.ui.components.PetalAddressBar
import com.petal.browser.ui.components.PetalMergedBar
import com.petal.browser.ui.components.BarPrefs
import com.petal.browser.appleduo.appleDuoFoldEffect
import com.petal.browser.ui.components.PetalAiChatSheet
import com.petal.browser.ui.components.PetalAiSearchResultSheet
import com.petal.browser.ui.components.PetalBottomNavBar
import com.petal.browser.ui.components.PetalNavTab
import com.petal.browser.ui.components.PetalOmniboxPage
import com.petal.browser.ui.components.PetalOverflowMenuSheet
import com.petal.browser.ui.components.PetalOverflowMenuSheetBeta
import com.petal.browser.ui.components.PREF_NEW_MAIN_MENU_BETA
import com.petal.browser.ui.components.PREF_NEW_MAIN_MENU_BETA_PENDING
import com.petal.browser.ui.components.shouldUsePetalBetaMenu
import com.petal.browser.ui.components.PetalPrivacyShieldBottomSheet
import com.petal.browser.ui.components.PetalPrivacyShieldSheet
import com.petal.browser.ui.components.PetalSiteInfoBridge
import com.petal.browser.tools.PetalDevConsoleSheet
import com.petal.browser.activity.MainActivity
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.unit.TabThumbnailCache

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
    val density = LocalDensity.current

    // Observe Browser ViewModel State
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val activeUrl by viewModel.activeUrl.collectAsStateWithLifecycle()
    val activeTitle by viewModel.activeTitle.collectAsStateWithLifecycle()
    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val storeCanGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    // The live tab controller knows about Gecko history the store can lag behind on (restored
    // sessions, SPA pushState, Petal-owned GeckoSession), so it is the source of truth for Back.
    val liveTabController = (activity as? MainActivity)?.currentAlbumController
        as? com.petal.browser.browser.PetalTabViewController
    val canGoBack = storeCanGoBack || (liveTabController?.canGoBack() == true)
    // Routes Back through the live controller first (real GeckoSession.goBack + URL-trail
    // fallback); only falls back to the store use case when no controller is bound.
    val webGoBack: () -> Boolean = {
        val ctrl = (activity as? MainActivity)?.currentAlbumController
            as? com.petal.browser.browser.PetalTabViewController
        if (viewModel.hasOpenOverlay) {
            viewModel.goBack()
        } else if (ctrl != null && ctrl.canGoBack()) {
            ctrl.goBackChecked { viewModel.goHomeFromStuckBack() }
            true
        } else {
            viewModel.goBack()
        }
    }
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
    // Find bar closes (and finder.clear() runs) on tab switch and navigation.
    LaunchedEffect(activeTab?.id, activeUrl) {
        if (viewModel.isFindInPageOpen.value) viewModel.clearFindMatches()
    }
    // History popup belongs to one tab.
    LaunchedEffect(activeTab?.id) { PetalTabHistoryState.dismiss() }

    val findInPageQuery by viewModel.findInPageQuery.collectAsStateWithLifecycle()
    val isAiSheetOpen by viewModel.isAiSheetOpen.collectAsStateWithLifecycle()
    val aiSheetQuery by viewModel.aiSheetQuery.collectAsStateWithLifecycle()
    val isDesktopMode by viewModel.isDesktopMode.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentUrlBookmarked.collectAsStateWithLifecycle()
    val isAdBlockEnabled by viewModel.isAdBlockEnabled.collectAsStateWithLifecycle()
    val isDevConsoleOpen by viewModel.isDevConsoleOpen.collectAsStateWithLifecycle()
    val isMediaPlaying = (activity as? MainActivity)?.isMediaPlaying == true
    val pendingExtensionPopup by PetalExtensionManager.pendingPopup.collectAsStateWithLifecycle()
    val settingsCategory by viewModel.settingsCategory.collectAsStateWithLifecycle()

    val rootView = remember(activity) {
        activity?.findViewById<android.view.View>(android.R.id.content) ?: activity?.window?.decorView
    }
    var activeWebEngineView by remember { mutableStateOf<android.view.View?>(null) }

    val tabSwitcherSnapshot = remember(isTabSwitcherOpen) {
        if (isTabSwitcherOpen && rootView != null) {
            PetalContentSnapshot.capture(rootView, forceFresh = true)
            PetalContentSnapshot.current?.asImageBitmap()
        } else null
    }

    // Take the page snapshot BEFORE the switcher opens. capture() above runs after the page is
    // already hidden and falls back to a software draw that cannot see the GeckoView surface,
    // which is why the page used to turn black the moment Tabs was tapped.
    // When the active tab is a home surface, store the captured snapshot into TabThumbnailCache
    // so home tabs display real thumbnails rather than placeholder cards.
    DisposableEffect(viewModel, rootView) {
        viewModel.beforeTabSwitcherOpen = {
            rootView?.let { rv ->
                PetalContentSnapshot.capturePreview(rv)
                val curTab = viewModel.activeTab.value
                if (curTab != null && PetalBrowserUtils.isHomePage(curTab.content.url)) {
                    val bmp = PetalContentSnapshot.capture(rv, forceFresh = true)
                    if (!bmp.isRecycled && bmp.width > 1 && bmp.height > 1) {
                        val targetWidth = Math.min(bmp.width, 480)
                        val targetHeight = Math.max(1, (bmp.height.toFloat() * targetWidth / bmp.width).toInt())
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
                        TabThumbnailCache.put(curTab.id, scaled, curTab.content.private, allowUniform = true)
                    }
                }
            }
        }
        onDispose { viewModel.beforeTabSwitcherOpen = null }
    }

    // Keep the switcher mounted briefly after it closes. It used to be removed the instant
    // isTabSwitcherOpen went false, so the close morph ended on a hole (page surface and bars
    // still coming back = black flash). Now the full-screen hero stays on top while the page
    // returns underneath, then the whole layer fades out.
    val switcherHold = remember { TabSwitcherHold() }
    if (isTabSwitcherOpen != switcherHold.wasOpen) {
        switcherHold.wasOpen = isTabSwitcherOpen
        if (isTabSwitcherOpen) {
            switcherHold.snapshot = tabSwitcherSnapshot
            switcherHold.generation++
        }
    }
    var switcherMounted by remember { mutableStateOf(false) }
    val switcherAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isTabSwitcherOpen) {
        if (isTabSwitcherOpen) {
            switcherMounted = true
            switcherAlpha.snapTo(1f)
        } else if (switcherMounted) {
            kotlinx.coroutines.delay(60)
            switcherAlpha.animateTo(0f, PetalSpring.calm())
            switcherMounted = false
            switcherHold.snapshot = null
        }
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
    // One shared, observable source for every bar preference (single listener, COMPACT default).
    val barSettings = com.petal.browser.ui.components.rememberBarSettings()
    val floatingTabBar = barSettings.floatingNav
    val isBottomAddressBar = barSettings.position == com.petal.browser.ui.components.AddressBarPosition.BOTTOM
    val isMerge = barSettings.style == com.petal.browser.ui.components.AddressBarStyle.MERGE
    val effectiveFloating = floatingTabBar && !isBottomAddressBar
    val haptic = remember(context) { PetalHapticEngine.getInstance(context) }

    // Window security in incognito mode: prevent screenshots, screen recordings and app switcher previews
    DisposableEffect(isIncognito, activity) {
        if (activity != null && isIncognito) {
            PetalIncognitoSessionManager.enableIncognitoSecurity(activity)
        }
        onDispose {
            if (activity != null && isIncognito) {
                PetalIncognitoSessionManager.disableIncognitoSecurity(activity)
            }
        }
    }

    LaunchedEffect(tabItems) {
        PetalIncognitoSessionManager.syncIncognitoState(context)
    }

    // Synchronize page state with BarCollapseState
    LaunchedEffect(activeUrl, isIncognito) {
        BarCollapseState.updatePage(activeUrl, isIncognito)
        (activity as? MainActivity)?.animateAddressBarCollapse(false) ?: BarCollapseState.setFraction(0f)
    }
    LaunchedEffect(Unit) {
        BarCollapseState.onExpandRequested = {
            (activity as? MainActivity)?.animateAddressBarCollapse(false) ?: BarCollapseState.setFraction(0f)
        }
    }

    // Omnibox Input Overlay State (Single authoritative source of truth via ViewModel)
    val isOmniboxOpen by viewModel.isOmniboxOpen.collectAsStateWithLifecycle()
    val omniboxInitialQuery by viewModel.omniboxInitialQuery.collectAsStateWithLifecycle()

    val isHome = activeUrl.isBlank() || PetalBrowserUtils.isHomePage(activeUrl)

    val junctionPredictiveEnabled by PetalPredictiveJunction.isPredictiveBackEnabled.collectAsStateWithLifecycle()
    val predictiveBackStyle by PetalPredictiveJunction.backStyle.collectAsStateWithLifecycle()
    val predictiveBackExitDirection by PetalPredictiveJunction.backExitDirection.collectAsStateWithLifecycle()
    val predictiveBlurEnabled by PetalPredictiveJunction.isDepthBlurEnabled.collectAsStateWithLifecycle()

    val lastExitBackAt = remember { longArrayOf(0L) }
    val progressAnim = remember { Animatable(0f) }
    // Back gesture progress for overlays that have no predictive surface of their own (menu, AI
    // sheet, omnibox, dev console, extension popup). The page itself must not shrink for these.
    val overlayBackProgress = remember { Animatable(0f) }
    var overlayBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var overlayCommitting by remember { mutableStateOf(false) }
    var overlayRelease by remember { mutableFloatStateOf(0f) }
    var overlayTouchY by remember { mutableFloatStateOf(0f) }
    // Reduce-motion (or predictive back off) means plain instant back: no pose, no settle, no hold.
    val reduceMotionNow = com.petal.browser.ui.components.rememberReduceMotion()
    val poseEnabledState = androidx.compose.runtime.rememberUpdatedState(!reduceMotionNow)
    val overlayPose = remember {
        OverlayPoseSource(
            progress = { overlayBackProgress.value },
            edge = { overlayBackEdge },
            committing = { overlayCommitting },
            release = { overlayRelease },
            touchY = { overlayTouchY },
            enabled = { PetalPredictiveJunction.isPredictiveBackEnabled.value && poseEnabledState.value },
        )
    }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var backTouchY by remember { mutableFloatStateOf(0f) }
    var initialTouchY by remember { mutableFloatStateOf(0f) }
    var isBackActive by remember { mutableStateOf(false) }
    var isCommitting by remember { mutableStateOf(false) }
    var releaseProgress by remember { mutableFloatStateOf(0f) }
    var settleTime by remember { mutableFloatStateOf(0f) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }

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
            isAiSheetOpen ||
            pendingExtensionPopup != null

    val isBottomBarVisible = !isOverlayShowing && (!isMerge || isHome)

    // Live underlay: while a full-screen overlay (Settings, History, Account...) is dragged away, the
    // real page below animates with it (parallax, dim, depth blur) instead of relying on a snapshot,
    // which was null or stale for most of these screens so they showed no blur at all.
    var liveBehind by remember { mutableStateOf<FloatArray?>(null) }
    val liveEligible by androidx.compose.runtime.rememberUpdatedState(
        // Screens mounted as Android Views over this root (presentComposeScreen: bridges, viewers) sit
        // above the same live page, so they qualify too instead of falling back to a snapshot.
        (isOverlayShowing || (activity as? MainActivity)?.hasPresentedScreens == true) &&
            !isOmniboxOpen && !isTabSwitcherOpen && !isAiSheetOpen &&
            !isSafeLockerOpen && pendingExtensionPopup == null
    )

    // A GeckoView SurfaceView is composited by the system outside of Compose, so a RenderEffect cannot
    // blur it. Over a web page the overlay therefore needs a real snapshot of the page (taken before the
    // overlay covers it) that it blurs itself. The home page is Compose, so it blurs live.
    val overlayHold = remember { OverlaySnapshotHold() }
    val wantsPageSnapshot = isOverlayShowing && !isHome && !isOmniboxOpen && !isTabSwitcherOpen &&
        !isAiSheetOpen && !isSafeLockerOpen && pendingExtensionPopup == null &&
        predictiveBlurEnabled && junctionPredictiveEnabled
    val menuWasOpen = overlayHold.menuOpen
    overlayHold.menuOpen = isMenuOpen
    if (wantsPageSnapshot != overlayHold.active) {
        overlayHold.active = wantsPageSnapshot
        overlayHold.snapshot = if (wantsPageSnapshot && rootView != null) {
            // Composition runs before the overlay's first frame, so the window still shows the page.
            // If the overflow menu was just open, the last frame contains it, so copy the page surface only.
            try {
                if (menuWasOpen) {
                    PetalContentSnapshot.capturePageSurface(activeWebEngineView)?.asImageBitmap()
                } else {
                    PetalContentSnapshot.capturePreview(rootView)
                    PetalContentSnapshot.current?.asImageBitmap()
                }
            } catch (_: Throwable) { null }
        } else null
    }
    val overlayPageSnapshot = overlayHold.snapshot
    val overlayPageSnapshotState by androidx.compose.runtime.rememberUpdatedState(overlayPageSnapshot)
    val isHomeState by androidx.compose.runtime.rememberUpdatedState(isHome)
    val webViewState by androidx.compose.runtime.rememberUpdatedState(activeWebEngineView)
    val blurOnState by androidx.compose.runtime.rememberUpdatedState(predictiveBlurEnabled)

    /** True when the page behind can take a live RenderEffect blur (Compose home or a TextureView-backed Gecko). */
    fun liveBlurCapable(): Boolean {
        if (isHomeState) return true
        fun hasTexture(v: android.view.View?): Boolean {
            if (v == null) return false
            if (v is android.view.TextureView) return true
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (hasTexture(v.getChildAt(i))) return true
            }
            return false
        }
        return hasTexture(webViewState)
    }

    DisposableEffect(Unit) {
        val host = object : com.petal.browser.predictive.PetalLiveUnderlay.Host {
            // Live path only where it can really blur (or blur is off / no snapshot exists). Otherwise the
            // screen falls back to its own blurred snapshot, which fixes "no blur over web pages".
            override fun hasLiveUnderlay() = liveEligible &&
                (!blurOnState || liveBlurCapable() || overlayPageSnapshotState == null)
            override fun onGestureStart(swipeEdge: Int) {
                liveBehind = com.petal.browser.predictive.PetalLiveUnderlay.behindValues().copyOf()
            }
            override fun onGestureProgress(progress: Float, swipeEdge: Int) {
                liveBehind = com.petal.browser.predictive.PetalLiveUnderlay.behindValues().copyOf()
            }
            override fun onGestureEnd(committed: Boolean) { liveBehind = null }
        }
        com.petal.browser.predictive.PetalLiveUnderlay.attach(host)
        onDispose {
            com.petal.browser.predictive.PetalLiveUnderlay.detach(host)
            liveBehind = null
        }
    }

    val selectedTab = when {
        isAiSheetOpen -> PetalNavTab.AI
        isMenuOpen -> PetalNavTab.MENU
        isHome -> PetalNavTab.HOME
        else -> PetalNavTab.TABS
    }

    // ── Single Authoritative Predictive Back Handler ────────────────────────
    // At the home root with nothing to close, Petal steps aside so Android plays its own back-to-launcher
    // animation (API 34+). With "double back to exit" on, the first Back is still ours (toast); once armed,
    // the second Back within 2s goes to the system. Everything observable here, so the handler re-registers.
    var exitArmed by remember { mutableStateOf(false) }
    val doubleBackExit = remember(sp, exitArmed) { sp.getBoolean("sp_double_back_exit", true) }
    val presentedCount = (activity as? MainActivity)?.presentedScreenCount ?: 0
    val atExitRoot = isHome && !isLoading && !canGoBack && !isOverlayShowing && !isFindInPageOpen && !isDevConsoleOpen &&
        pendingExtensionPopup == null && presentedCount == 0 && tabCount <= 1
    val systemHandlesExit = atExitRoot && PetalPredictiveJunction.isDeviceSupported &&
        junctionPredictiveEnabled && poseEnabledState.value && (!doubleBackExit || exitArmed)
    LaunchedEffect(exitArmed, atExitRoot) {
        if (exitArmed) {
            kotlinx.coroutines.delay(2000L)
            exitArmed = false
        }
    }
    LaunchedEffect(atExitRoot) { if (!atExitRoot) exitArmed = false }

    PredictiveBackHandler(enabled = !systemHandlesExit) { progressFlow ->
        val currentStyle = PetalPredictiveJunction.backStyle.value
        var gestureStarted = false
        var lastProgress = 0f
        var lastNanos = 0L
        var velocity = 0f
        var exiting = false
        var overlayMode = false

        try {
            progressFlow.collect { backEvent ->
                val now = System.nanoTime()
                if (!gestureStarted) {
                    gestureStarted = true
                    overlayMode = (activity as? MainActivity)?.hasPresentedScreens == true ||
                        pendingExtensionPopup != null || isOmniboxOpen || isAiSheetOpen ||
                        viewModel.hasOpenOverlay
                    initialTouchY = backEvent.touchY
                    lastProgress = backEvent.progress
                    lastNanos = now
                } else {
                    val dt = (now - lastNanos) / 1_000_000_000f
                    if (dt > 0.001f) {
                        val instant = (backEvent.progress - lastProgress) / dt
                        velocity = velocity * 0.5f + instant * 0.5f
                        lastProgress = backEvent.progress
                        lastNanos = now
                    }
                }
                if (overlayMode) {
                    overlayBackEdge = backEvent.swipeEdge
                    overlayTouchY = backEvent.touchY
                    overlayCommitting = false
                    overlayBackProgress.snapTo(backEvent.progress)
                    val presented = (activity as? MainActivity)?.presentedScreens?.lastOrNull()
                    if (presented != null && junctionPredictiveEnabled && poseEnabledState.value) {
                        val p = backEvent.progress
                        val style = PetalPredictiveJunction.backStyle.value
                        val frame = PetalBackMotion.frame(
                            style,
                            PetalBackInput(
                                progress = p,
                                swipeEdge = backEvent.swipeEdge,
                                touchY = backEvent.touchY,
                                initialTouchY = initialTouchY,
                                widthPx = presented.width.toFloat().takeIf { it > 0f } ?: (context.resources.displayMetrics.widthPixels.toFloat()),
                                heightPx = presented.height.toFloat().takeIf { it > 0f } ?: (context.resources.displayMetrics.heightPixels.toFloat()),
                                density = density.density,
                                blurEnabled = false,
                                exitDirection = predictiveBackExitDirection,
                            )
                        ).top
                        presented.scaleX = frame.scale
                        presented.scaleY = frame.scale
                        presented.translationX = frame.translationX
                        presented.translationY = frame.translationY
                        presented.alpha = frame.alpha
                    }
                    return@collect
                }
                if (!junctionPredictiveEnabled || !poseEnabledState.value) return@collect
                backSwipeEdge = backEvent.swipeEdge
                backTouchY = backEvent.touchY
                releaseVelocity = velocity
                isBackActive = true
                isCommitting = false
                progressAnim.snapTo(backEvent.progress)
            }

            // Finger lifted and gesture committed
            val release = progressAnim.value
            releaseProgress = release
            val hasOpenOverlay = (activity as? MainActivity)?.hasPresentedScreens == true ||
                    pendingExtensionPopup != null ||
                    isOmniboxOpen ||
                    isAiSheetOpen ||
                    viewModel.hasOpenOverlay

            if (hasOpenOverlay) {
                if (overlayMode) {
                    // Finish the sheet's own dismiss pose before the state flips so there is no jump.
                    // Settle with the selected style's own commit curve (spring for Petal).
                    if (junctionPredictiveEnabled && poseEnabledState.value) {
                        overlayRelease = overlayBackProgress.value
                        overlayCommitting = true
                        if (currentStyle.usesSpringCommit) {
                            overlayBackProgress.animateTo(
                                1f,
                                PetalSpring.quick()
                            )
                        } else {
                            overlayBackProgress.animateTo(
                                1f,
                                tween(currentStyle.commitDurationMs, easing = currentStyle.commitEasing)
                            )
                        }
                    }
                }
                when {
                    (activity as? MainActivity)?.hasPresentedScreens == true -> (activity as? MainActivity)?.dismissTopPresentedScreen()
                    pendingExtensionPopup != null -> PetalExtensionManager.dismissPopup()
                    isOmniboxOpen -> viewModel.setOmniboxOpen(false)
                    isAiSheetOpen -> viewModel.setAiSheetOpen(false)
                    viewModel.hasOpenOverlay -> viewModel.dismissTopOverlay()
                }
                if (overlayMode) {
                    // Park the pose until the sheet's own exit animation has finished.
                    if (junctionPredictiveEnabled && poseEnabledState.value) kotlinx.coroutines.delay(400)
                    overlayCommitting = false
                    overlayBackProgress.snapTo(0f)
                } else {
                    progressAnim.animateTo(0f, PetalSpring.calm())
                }
            } else if (webGoBack()) {
                progressAnim.animateTo(0f, PetalSpring.calm())
            } else {
                // "Double Back to Exit" (sp_double_back_exit): first back shows a hint,
                // a second back within 2s exits. Read live so the toggle applies instantly.
                val doubleBack = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("sp_double_back_exit", true)
                val now = android.os.SystemClock.elapsedRealtime()
                if (doubleBack && now - lastExitBackAt[0] > 2000L) {
                    lastExitBackAt[0] = now
                    exitArmed = true
                    com.petal.browser.view.PetalToast.show(
                        context,
                        com.petal.browser.R.string.ui_press_back_twice_quickly_to
                    )
                    progressAnim.animateTo(0f, PetalSpring.calm())
                } else {
                    isCommitting = true
                    if (!junctionPredictiveEnabled || !poseEnabledState.value) {
                        // Plain instant back: no settle animation before finishing.
                    } else if (currentStyle.usesSpringCommit) {
                        progressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = PetalSpring.quick()
                        )
                    } else {
                        progressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(currentStyle.commitDurationMs, easing = currentStyle.commitEasing)
                        ) {
                            settleTime = ((value - release) / (1f - release).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                        }
                    }
                    // Stay parked in the final pose: resetting here made the page flash back to full
                    // size for a frame before the system's close animation took over.
                    exiting = true
                    activity?.finish()
                }
            }
        } catch (_: CancellationException) {
            isCommitting = false
            overlayCommitting = false
            if (overlayMode) {
                val presented = (activity as? MainActivity)?.presentedScreens?.lastOrNull()
                presented?.animate()?.scaleX(1f)?.scaleY(1f)?.translationX(0f)?.translationY(0f)?.alpha(1f)?.setDuration(200L)?.start()
                overlayBackProgress.animateTo(0f, PetalSpring.calm())
            } else {
                progressAnim.animateTo(0f, PetalSpring.calm())
            }
        } finally {
            if (!exiting) {
                isCommitting = false
                if (progressAnim.value < 0.001f) {
                    isBackActive = false
                }
            }
        }
    }

    val navBarBottomInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Primary Viewport (Home vs Gecko Engine) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isHome && !effectiveFloating && isBottomBarVisible) {
                        Modifier.padding(
                            bottom = com.petal.browser.ui.components.BarMetrics.bottomNavPx.let {
                                if (it > 0) with(density) { it.toDp() } else 64.dp + navBarBottomInset
                            }
                        )
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    val p = progressAnim.value
                    if (junctionPredictiveEnabled && p > 0.001f) {
                        val input = PetalBackInput(
                            progress = p,
                            swipeEdge = backSwipeEdge,
                            touchY = backTouchY,
                            initialTouchY = initialTouchY,
                            widthPx = size.width,
                            heightPx = size.height,
                            density = density.density,
                            blurEnabled = predictiveBlurEnabled,
                            committing = isCommitting,
                            releaseProgress = releaseProgress,
                            releaseVelocity = releaseVelocity,
                            settleTime = settleTime,
                            exitDirection = predictiveBackExitDirection,
                        )
                        val backFrame = PetalBackMotion.frame(predictiveBackStyle, input)
                        val topLayer = backFrame.top
                        scaleX = topLayer.scale
                        scaleY = topLayer.scale
                        transformOrigin = TransformOrigin(topLayer.originX, topLayer.originY)
                        translationX = topLayer.translationX
                        translationY = topLayer.translationY
                        alpha = topLayer.alpha
                        if (topLayer.cornerDp > 0.5f) {
                            shape = RoundedCornerShape(topLayer.cornerDp.dp)
                            clip = true
                            shadowElevation = topLayer.shadowDp.dp.toPx()
                        } else {
                            clip = false
                            shadowElevation = 0f
                        }
                    } else {
                        val lb = liveBehind
                        if (lb != null) {
                            scaleX = lb[0]
                            scaleY = lb[0]
                            translationX = lb[1]
                            translationY = lb[2]
                            // A GeckoView surface cannot be blurred by a render effect, so depth blur only
                            // applies to the Compose home page; web pages still get parallax and dim.
                            if (lb[4] > 0.5f && liveBlurCapable()) {
                                val px = lb[4].dp.toPx()
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                renderEffect = androidx.compose.ui.graphics.BlurEffect(
                                    px, px, androidx.compose.ui.graphics.TileMode.Clamp
                                )
                            } else {
                                renderEffect = null
                            }
                        } else {
                            scaleX = 1f
                            scaleY = 1f
                            translationX = 0f
                            translationY = 0f
                            renderEffect = null
                        }
                        alpha = 1f
                        clip = false
                        shadowElevation = 0f
                    }
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isHome) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .appleDuoFoldEffect(isWebsite = false)
                ) {
                    if (isIncognito) {
                        PetalIncognitoHomeScreen(
                            tabCount = tabItems.count { it.isIncognito },
                            onSearchClick = { viewModel.openOmnibox() },
                            onCloseIncognito = { viewModel.closeAllTabs(isIncognitoOnly = true) }
                        )
                    } else {
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
                }
            } else {
                val statusBarTopInset = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .asPaddingValues().calculateTopPadding()
                val isCompact = barSettings.height == com.petal.browser.ui.components.AddressBarHeight.COMPACT
                val barBaseHeight = if (isCompact) 50.dp else 56.dp

                val desktopTabBarMode = sp.getString(
                    PetalDesktopTabBarBridge.PREF_KEY,
                    PetalDesktopTabBarBridge.MODE_AUTO
                ) ?: PetalDesktopTabBarBridge.MODE_AUTO
                val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                val isDesktopTabBarActive = remember(desktopTabBarMode, screenWidthDp) {
                    when (desktopTabBarMode) {
                        PetalDesktopTabBarBridge.MODE_ALWAYS -> true
                        PetalDesktopTabBarBridge.MODE_NEVER -> false
                        else -> screenWidthDp >= 600
                    }
                }
                val showDesktopTabBar = isDesktopTabBarActive && !isHome
                val desktopBarHeight = if (showDesktopTabBar) PetalDesktopTabBarBridge.BAR_HEIGHT_DP.dp else 0.dp

                val desktopTabHandler = remember(viewModel, activity) {
                    object : PetalDesktopTabBarHandler {
                        override fun onTabSelect(tabId: String) { viewModel.selectTab(tabId) }

                        override fun onTabClose(tabId: String) { viewModel.closeTab(tabId) }

                        override fun onNewTab(incognito: Boolean) {
                            val mainAct = activity as? MainActivity
                            if (mainAct != null) {
                                mainAct.addAlbum(context.getString(R.string.app_name), "about:home", true, incognito)
                            } else {
                                viewModel.openNewTab("about:home", selectTab = true, private = incognito)
                            }
                        }
                    }
                }

                val barTotalTopHeight = statusBarTopInset + desktopBarHeight + barBaseHeight + (if (isCompact) 6.dp else 10.dp)
                val barTotalBottomHeight = navBarBottomInset + barBaseHeight + (if (isCompact) 6.dp else 10.dp)

                // Page insets come from the bars' REAL measured sizes (BarMetrics); the old constants
                // are only estimates for the very first frame, before the bars have reported.
                val navVisibleHere = isBottomBarVisible
                val navEstimate = if (isBottomAddressBar) 64.dp else 64.dp + navBarBottomInset
                val pagePad = with(density) {
                    com.petal.browser.ui.components.BarMetrics.pagePadding(
                        isBottomAddressBar = isBottomAddressBar,
                        measuredTop = com.petal.browser.ui.components.BarMetrics.topBarPx,
                        estimatedTop = barTotalTopHeight.roundToPx(),
                        measuredBottomAddress = com.petal.browser.ui.components.BarMetrics.bottomAddressPx,
                        estimatedBottomAddress = barTotalBottomHeight.roundToPx(),
                        measuredBottomNav = com.petal.browser.ui.components.BarMetrics.bottomNavPx,
                        estimatedBottomNav = navEstimate.roundToPx(),
                        navVisible = navVisibleHere,
                        topOverlayPx = (statusBarTopInset + desktopBarHeight).roundToPx(),
                        navBarInset = navBarBottomInset.roundToPx()
                    )
                }
                val effectiveTopPadding = with(density) { pagePad.top.toDp() }
                val effectiveBottomPadding = with(density) { pagePad.bottom.toDp() }

                ComposeEngineViewport(
                    tab = activeTab,
                    isLoading = isLoading,
                    onRefresh = { viewModel.reload() },
                    topPadding = effectiveTopPadding,
                    bottomPadding = effectiveBottomPadding,
                    // Minimized bars: page keeps only the status bar gap on top and runs edge to edge below.
                    collapsedTopPadding = statusBarTopInset + desktopBarHeight,
                    collapsedBottomPadding = 0.dp,
                    onEngineViewReady = { activeWebEngineView = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .appleDuoFoldEffect(isWebsite = true)
                )

                androidx.compose.runtime.CompositionLocalProvider(
                    com.petal.browser.ui.glass.LocalPetalGlassSource provides (if (isHome) null else (activeWebEngineView ?: (activity as? android.app.Activity)?.findViewById(com.petal.browser.R.id.main_content)))
                ) {
                    if (showDesktopTabBar && isBottomAddressBar) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .zIndex(2f)
                        ) {
                            PetalDesktopTabBar(
                                handler = desktopTabHandler,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (showDesktopTabBar && !isBottomAddressBar) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .zIndex(2f)
                                .onSizeChanged { com.petal.browser.ui.components.BarMetrics.reportTop(it.height) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                            ) {
                                PetalDesktopTabBar(
                                    handler = desktopTabHandler,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
                                    onBackClick = { webGoBack() },
                                    onForwardClick = { viewModel.goForward() },
                                    onReloadClick = { viewModel.reload() },
                                    onStopClick = { viewModel.stopLoading() },
                                    onShareClick = {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, activeUrl)
                                        }
                                        activity?.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.bar_share_link)))
                                    },
                                    onAddressClick = { viewModel.openOmnibox() },
                                    onHomeClick = {
                                        viewModel.goHome(isIncognito = isIncognito)
                                    },
                                    onNewTabClick = {
                                        viewModel.openNewTab(selectTab = true, private = isIncognito)
                                    },
                                    onTabsClick = {
                                        viewModel.toggleTabSwitcher()
                                    },
                                    onMenuClick = {
                                        viewModel.toggleMenu()
                                    },
                                    onSearchClick = { viewModel.openOmnibox() },
                                    onDownloadsClick = { viewModel.openDownloads() },
                                    onFindInPageClick = { viewModel.openFindInPage() },
                                    onBookmarkClick = { viewModel.toggleBookmark(activeTitle, activeUrl) },
                                    onHistoryClick = { viewModel.openHistory() },
                                    onBookmarksManagerClick = { viewModel.openBookmarks() },
                                    onAiResearchClick = { viewModel.openAiSheet(activeTitle) },
                                    onMediaClick = {
                                        com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.openMediaSheet()
                                    },
                                    onSwipeNextTab = { viewModel.switchToNextTab() },
                                    onSwipePrevTab = { viewModel.switchToPreviousTab() },
                                    onSwipeUp = { viewModel.toggleTabSwitcher() },
                                    onHardRefresh = { viewModel.reload() },
                                    onReaderClick = { (activity as? MainActivity)?.showReaderMode() },
                                    onCustomizeClick = { viewModel.openSettings(com.petal.browser.compose.settings.SettingsCategory.ADDRESS_BAR) },
                                    isBottom = false,
                                    suppressStatusBarTopInset = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                PetalAddressBar(
                                    url = activeUrl,
                                    title = activeTitle.ifBlank { activeUrl },
                                    progress = loadProgress / 100f,
                                    isIncognito = isIncognito,
                                    isLoading = isLoading,
                                    canGoBack = canGoBack,
                                    onBackClick = { webGoBack() },
                                    onReloadClick = { viewModel.reload() },
                                    onStopClick = { viewModel.stopLoading() },
                                    onShareClick = {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, activeUrl)
                                        }
                                        activity?.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.bar_share_link)))
                                    },
                                    onAddressClick = { viewModel.openOmnibox() },
                                    onAiResearchClick = { viewModel.openAiSheet(activeTitle) },
                                    onMediaClick = {
                                        com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.openMediaSheet()
                                    },
                                    onReaderClick = { (activity as? MainActivity)?.showReaderMode() },
                                    onHardRefresh = { viewModel.reload() },
                                    onSwipeNextTab = { viewModel.switchToNextTab() },
                                    onSwipePrevTab = { viewModel.switchToPreviousTab() },
                                    onSwipeUp = { viewModel.toggleTabSwitcher() },
                                    isBottom = false,
                                    hasBottomNav = isBottomBarVisible,
                                    suppressStatusBarTopInset = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else if (isMerge) {
                        PetalMergedBar(
                            url = activeUrl,
                            title = activeTitle.ifBlank { activeUrl },
                            progress = loadProgress / 100f,
                            isIncognito = isIncognito,
                            isLoading = isLoading,
                            canGoBack = canGoBack,
                            canGoForward = canGoForward,
                            tabCount = tabCount,
                            onBackClick = { webGoBack() },
                            onForwardClick = { viewModel.goForward() },
                            onReloadClick = { viewModel.reload() },
                            onStopClick = { viewModel.stopLoading() },
                            onShareClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, activeUrl)
                                }
                                activity?.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.bar_share_link)))
                            },
                            onAddressClick = { viewModel.openOmnibox() },
                            onHomeClick = {
                                viewModel.goHome(isIncognito = isIncognito)
                            },
                            onNewTabClick = {
                                viewModel.openNewTab(selectTab = true, private = isIncognito)
                            },
                            onTabsClick = {
                                viewModel.toggleTabSwitcher()
                            },
                            onMenuClick = {
                                viewModel.toggleMenu()
                            },
                            onSearchClick = { viewModel.openOmnibox() },
                            onDownloadsClick = { viewModel.openDownloads() },
                            onFindInPageClick = { viewModel.openFindInPage() },
                            onBookmarkClick = { viewModel.toggleBookmark(activeTitle, activeUrl) },
                            onHistoryClick = { viewModel.openHistory() },
                            onBookmarksManagerClick = { viewModel.openBookmarks() },
                            onAiResearchClick = { viewModel.openAiSheet(activeTitle) },
                            onMediaClick = {
                                com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.openMediaSheet()
                            },
                            onSwipeNextTab = { viewModel.switchToNextTab() },
                            onSwipePrevTab = { viewModel.switchToPreviousTab() },
                            onSwipeUp = { viewModel.toggleTabSwitcher() },
                            onHardRefresh = { viewModel.reload() },
                            onReaderClick = { (activity as? MainActivity)?.showReaderMode() },
                            onCustomizeClick = { viewModel.openSettings(com.petal.browser.compose.settings.SettingsCategory.ADDRESS_BAR) },
                            isBottom = isBottomAddressBar,
                            modifier = if (isBottomAddressBar) {
                                // Stacked directly above the bottom nav (measured), never overlapping it.
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = with(density) { (if (navVisibleHere) com.petal.browser.ui.components.BarMetrics.bottomNavPx else 0).toDp() })
                                    .zIndex(2f)
                                    .onSizeChanged { com.petal.browser.ui.components.BarMetrics.reportBottomAddress(it.height) }
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .zIndex(2f)
                                    .onSizeChanged { com.petal.browser.ui.components.BarMetrics.reportTop(it.height) }
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
                            onBackClick = { webGoBack() },
                            onReloadClick = { viewModel.reload() },
                            onStopClick = { viewModel.stopLoading() },
                            onShareClick = {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, activeUrl)
                                }
                                activity?.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.bar_share_link)))
                            },
                            onAddressClick = { viewModel.openOmnibox() },
                            onAiResearchClick = { viewModel.openAiSheet(activeTitle) },
                            onMediaClick = {
                                com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.openMediaSheet()
                            },
                            onReaderClick = { (activity as? MainActivity)?.showReaderMode() },
                            onHardRefresh = { viewModel.reload() },
                            onSwipeNextTab = { viewModel.switchToNextTab() },
                            onSwipePrevTab = { viewModel.switchToPreviousTab() },
                            onSwipeUp = { viewModel.toggleTabSwitcher() },
                            isBottom = isBottomAddressBar,
                            hasBottomNav = isBottomBarVisible,
                            modifier = if (isBottomAddressBar) {
                                // Stacked directly above the bottom nav (measured), never overlapping it.
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = with(density) { (if (navVisibleHere) com.petal.browser.ui.components.BarMetrics.bottomNavPx else 0).toDp() })
                                    .zIndex(2f)
                                    .onSizeChanged { com.petal.browser.ui.components.BarMetrics.reportBottomAddress(it.height) }
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .zIndex(2f)
                                    .onSizeChanged { com.petal.browser.ui.components.BarMetrics.reportTop(it.height) }
                            }
                        )
                    }
                }
            }
        }

        // ── Persistent Bottom Navigation Bar ────────────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = isBottomBarVisible,
            enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                    androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 2 },
            exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                   androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 2 },
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
                glassSource = if (isHome) null else (activeWebEngineView ?: (activity as? android.app.Activity)?.findViewById(com.petal.browser.R.id.main_content)),
                onHomeClick = {
                    viewModel.goHome(isIncognito = isIncognito)
                },
                onAiClick = {
                    viewModel.openAiSheet()
                },
                onNewTabClick = {
                    viewModel.openNewTab(selectTab = true, private = isIncognito)
                },
                onTabsClick = {
                    viewModel.toggleTabSwitcher()
                },
                onMenuClick = {
                    viewModel.toggleMenu()
                },
                onSearchClick = {
                    viewModel.openOmnibox()
                },
                onHistoryClick = {
                    viewModel.openHistory()
                },
                onBookmarksClick = {
                    viewModel.openBookmarks()
                },
                onSwipeTabLeft = {
                    viewModel.switchToPreviousTab()
                },
                onSwipeTabRight = {
                    viewModel.switchToNextTab()
                },
                showSearch = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { com.petal.browser.ui.components.BarMetrics.reportBottomNav(it.height) }
            )
        }
        androidx.compose.runtime.LaunchedEffect(isBottomBarVisible) {
            if (!isBottomBarVisible) com.petal.browser.ui.components.BarMetrics.clearBottomNav()
        }

            // Dim over the live page while an overlay is being dragged away (below every overlay).
            if (liveBehind != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(7f)
                        .graphicsLayer { alpha = (liveBehind?.getOrNull(3) ?: 0f).coerceIn(0f, 1f) }
                        .background(androidx.compose.ui.graphics.Color.Black)
                )
            }

            // ── Fullscreen Overlay Screens ──────────────────────────────────

            // First-run setup. Topmost layer; only composed while the gate says so (fresh installs / replay).
            if (activity != null) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    com.petal.browser.setup.PetalSetupGate.evaluate(activity)
                }
                if (com.petal.browser.setup.PetalSetupGate.visible) {
                    Box(Modifier.fillMaxSize().zIndex(100f)) {
                        com.petal.browser.setup.PetalSetupFlow(
                            activity = activity,
                            onFinished = { com.petal.browser.setup.PetalSetupGate.complete(activity) }
                        )
                    }
                }
            }

            // Omnibox Query Page with Seamless Enter/Exit Transition
            if (isOmniboxOpen && activity != null) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isOmniboxOpen,
                    enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                            androidx.compose.animation.scaleIn(
                                initialScale = 0.94f,
                                animationSpec = PetalSpring.calm()
                            ),
                    exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                           androidx.compose.animation.scaleOut(
                               targetScale = 0.94f,
                               animationSpec = PetalSpring.calm()
                           ),
                    modifier = Modifier.fillMaxSize().zIndex(10f)
                        .overlayBackPose(overlayPose)
                ) {
                    PetalOmniboxPage(
                        handleBackInternally = false,
                        activity = activity!!,
                        initialQuery = omniboxInitialQuery ?: if (isHome) "" else activeUrl,
                        pageTitle = activeTitle,
                        pageUrl = activeUrl,
                        isIncognito = isIncognito,
                        onQuerySubmitted = { query ->
                            viewModel.setOmniboxOpen(false)
                            viewModel.loadUrl(query)
                        },
                        onBackPress = { viewModel.setOmniboxOpen(false) },
                        onCommand = { command ->
                            viewModel.setOmniboxOpen(false)
                            when (command) {
                                PetalOmniboxCommand.TABS -> viewModel.setTabSwitcherOpen(true)
                                PetalOmniboxCommand.NEW_TAB ->
                                    viewModel.openNewTab("about:home", selectTab = true)
                                PetalOmniboxCommand.NEW_INCOGNITO_TAB ->
                                    viewModel.openNewTab("about:home", selectTab = true, private = true)
                                PetalOmniboxCommand.HISTORY -> viewModel.openHistory()
                                PetalOmniboxCommand.BOOKMARKS -> viewModel.openBookmarks()
                                PetalOmniboxCommand.DOWNLOADS -> viewModel.openDownloads()
                                PetalOmniboxCommand.SETTINGS -> viewModel.openSettings(SettingsCategory.OVERVIEW)
                                PetalOmniboxCommand.READER_MODE -> (activity as? MainActivity)?.showReaderMode()
                                else -> Unit
                            }
                        }
                    )
                }
            }

            // Tab Switcher Grid
            if (isTabSwitcherOpen || switcherMounted) {
              androidx.compose.runtime.key(switcherHold.generation) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9f)
                        .graphicsLayer { alpha = switcherAlpha.value }
                        .then(
                            if (!isTabSwitcherOpen) {
                                // Closing: swallow touches so the fading layer can't be tapped.
                                Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            } else Modifier
                        )
                ) {
                    PetalTabGridSwitcher(
                        backgroundSnapshot = switcherHold.snapshot,
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
                        onCloseAllTabs = { isIncognitoOnly ->
                            viewModel.closeAllTabs(isIncognitoOnly = isIncognitoOnly)
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
            }

            // Overflow Menu Sheet
            LaunchedEffect(isMenuOpen) {
                if (isMenuOpen && activity != null) {
                    val rv = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
                    com.petal.browser.predictive.PetalContentSnapshot.capture(rv)
                }
            }
            if (isMenuOpen) {
                val menuSource = if (isHome) null else (activeWebEngineView ?: (activity as? android.app.Activity)?.findViewById(com.petal.browser.R.id.main_content))
                androidx.compose.runtime.CompositionLocalProvider(
                    com.petal.browser.ui.glass.LocalPetalGlassSource provides menuSource
                ) {
                // The menu lives in its own window (like every other blurred surface). Window blur
                // only blurs what is BEHIND a window, so drawing the menu inside the activity window
                // left nothing to blur, and the sheet also skipped its own dim: no blur, no scrim.
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { viewModel.setMenuOpen(false) },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                        dismissOnClickOutside = false
                    )
                ) {
                val menuWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                androidx.compose.runtime.SideEffect {
                    menuWindow?.let { com.petal.browser.ui.blur.keepTransparentWindowBackground(it) }
                }
                // Owns the window dim. fallbackDim is 0 because the sheet draws its own plain dim
                // when blur is off or unavailable.
                com.petal.browser.ui.blur.PetalWindowBlurEffect(
                    state = com.petal.browser.ui.blur.rememberPetalBlurState(com.petal.browser.ui.blur.PetalBlurSurface.OVERFLOW_MENU),
                    fallbackDim = 0f,
                    visible = true
                )
                Box(
                    Modifier.fillMaxSize()
                        .overlayBackPose(overlayPose, 0.9f, 0.12f)
                ) {
                // Stable stays the default. The Beta setting is read once per open, so a change applies
                // the next time the menu opens (no restart). A failed/unrenderable beta falls back to stable.
                var betaMenuFailed by remember { mutableStateOf(false) }
                val useBetaMenu = remember { shouldUsePetalBetaMenu(context, sp) } && !betaMenuFailed
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose { sp.edit().putBoolean(PREF_NEW_MAIN_MENU_BETA_PENDING, false).apply() }
                }
                if (useBetaMenu) {
                PetalOverflowMenuSheetBeta(
                    pageTitle = activeTitle,
                    pageUrl = activeUrl,
                    isBookmarked = isBookmarked,
                    canGoBack = canGoBack,
                    isDesktopSite = isDesktopMode,
                    isAdBlockEnabled = isAdBlockEnabled,
                    isMediaPlaying = isMediaPlaying,
                    onDismissRequest = { viewModel.setMenuOpen(false) },
                    onGoBack = { webGoBack() },
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
                    onToggleAdBlock = { enabled ->
                        viewModel.toggleAdBlock(enabled)
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
                    onShowReadingMode = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.showReaderMode()
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
                    onCopyCleanLink = {
                        viewModel.setMenuOpen(false)
                        viewModel.copyCleanLink(activeUrl)
                    },
                    onOpenShieldHud = {
                        viewModel.setMenuOpen(false)
                        activity?.let { act ->
                            PetalPrivacyShieldSheet.show(act, activeUrl) { enabled ->
                                viewModel.toggleAdBlock(enabled)
                            }
                        }
                    },
                    onViewSource = {
                        viewModel.setMenuOpen(false)
                        if (activeUrl.isNotBlank() && !activeUrl.startsWith("about:") && !activeUrl.startsWith("view-source:")) {
                            viewModel.loadUrl("view-source:$activeUrl")
                        }
                    },
                    onOpenSettings = {
                        viewModel.setMenuOpen(false)
                        viewModel.openSettings(SettingsCategory.OVERVIEW)
                    },
                    onTriggerMediaMode = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.triggerSystemPipMode()
                    },
                    onOpenPetalAi = {
                        viewModel.setMenuOpen(false)
                        viewModel.openAiSheet()
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
                    onOpenExtensionAction = { extensionId ->
                        viewModel.setMenuOpen(false)
                        activity?.let { PetalExtensionManager.triggerBrowserAction(extensionId, it) }
                    },
                    onOpenExtensionSettings = { extensionId ->
                        viewModel.setMenuOpen(false)
                        activity?.let { PetalExtensionManager.openOptionsPage(extensionId, it) }
                    },
                    onOpenDevConsole = {
                        viewModel.setMenuOpen(false)
                        viewModel.openDevConsole()
                    },
                    onOpenSafeLocker = {
                        viewModel.setMenuOpen(false)
                        viewModel.openSafeLocker()
                    },
                    onOpenQuickTools = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.showQuickTools()
                    },
                    handleBackInternally = true,
                    onRenderConfirmed = {
                        sp.edit().putBoolean(PREF_NEW_MAIN_MENU_BETA_PENDING, false).apply()
                    },
                    onRenderFailed = {
                        // Could not render: back to the stable menu now and stop retrying the beta.
                        sp.edit()
                            .putBoolean(PREF_NEW_MAIN_MENU_BETA_PENDING, false)
                            .putBoolean(PREF_NEW_MAIN_MENU_BETA, false)
                            .apply()
                        betaMenuFailed = true
                    }
                )
                } else {
                PetalOverflowMenuSheet(
                    pageTitle = activeTitle,
                    pageUrl = activeUrl,
                    isBookmarked = isBookmarked,
                    canGoBack = canGoBack,
                    isDesktopSite = isDesktopMode,
                    isAdBlockEnabled = isAdBlockEnabled,
                    isMediaPlaying = isMediaPlaying,
                    onDismissRequest = { viewModel.setMenuOpen(false) },
                    onGoBack = { webGoBack() },
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
                    onToggleAdBlock = { enabled ->
                        viewModel.toggleAdBlock(enabled)
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
                    onShowReadingMode = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.showReaderMode()
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
                    onCopyCleanLink = {
                        viewModel.setMenuOpen(false)
                        viewModel.copyCleanLink(activeUrl)
                    },
                    onOpenShieldHud = {
                        viewModel.setMenuOpen(false)
                        activity?.let { act ->
                            PetalPrivacyShieldSheet.show(act, activeUrl) { enabled ->
                                viewModel.toggleAdBlock(enabled)
                            }
                        }
                    },
                    onViewSource = {
                        viewModel.setMenuOpen(false)
                        if (activeUrl.isNotBlank() && !activeUrl.startsWith("about:") && !activeUrl.startsWith("view-source:")) {
                            viewModel.loadUrl("view-source:$activeUrl")
                        }
                    },
                    onOpenSettings = {
                        viewModel.setMenuOpen(false)
                        viewModel.openSettings(SettingsCategory.OVERVIEW)
                    },
                    onTriggerMediaMode = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.triggerSystemPipMode()
                    },
                    onOpenPetalAi = {
                        viewModel.setMenuOpen(false)
                        viewModel.openAiSheet()
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
                    onOpenExtensionAction = { extensionId ->
                        viewModel.setMenuOpen(false)
                        activity?.let { PetalExtensionManager.triggerBrowserAction(extensionId, it) }
                    },
                    onOpenExtensionSettings = { extensionId ->
                        viewModel.setMenuOpen(false)
                        activity?.let { PetalExtensionManager.openOptionsPage(extensionId, it) }
                    },
                    onOpenDevConsole = {
                        viewModel.setMenuOpen(false)
                        viewModel.openDevConsole()
                    },
                    onOpenSafeLocker = {
                        viewModel.setMenuOpen(false)
                        viewModel.openSafeLocker()
                    },
                    onOpenQuickTools = {
                        viewModel.setMenuOpen(false)
                        (activity as? MainActivity)?.showQuickTools()
                    },
                    handleBackInternally = true
                )
                }
                }
                }
                }
            }

            // Bookmarks Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isBookmarksOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                PetalBookmarksScreen(
                    backgroundSnapshot = overlayPageSnapshot,
                    onOpenUrl = { url ->
                        viewModel.setBookmarksOpen(false)
                        viewModel.loadUrl(url)
                    },
                    onBookmarksChanged = { viewModel.notifyBookmarksChanged() },
                    onDismiss = { viewModel.setBookmarksOpen(false) },
                    isIncognito = isIncognito
                )
            }

            // History Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isHistoryOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                PetalHistoryScreen(
                    backgroundSnapshot = overlayPageSnapshot,
                    onOpenUrl = { url ->
                        viewModel.setHistoryOpen(false)
                        viewModel.loadUrl(url)
                    },
                    onOpenUrlInNewTab = { url ->
                        viewModel.setHistoryOpen(false)
                        viewModel.openNewTab(url, selectTab = true)
                    },
                    onClearBrowsingData = {
                        viewModel.setHistoryOpen(false)
                        viewModel.openDelete()
                    },
                    onDismiss = { viewModel.setHistoryOpen(false) },
                    isIncognito = isIncognito
                )
            }

            // Downloads Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isDownloadsOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                PetalDownloadManagerScreen(
                    backgroundSnapshot = overlayPageSnapshot,
                    onBackPress = { viewModel.setDownloadsOpen(false) }
                )
            }

            // Settings Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isSettingsOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                PetalSettingsScreen(
                    backgroundSnapshot = overlayPageSnapshot,
                    initialCategory = settingsCategory,
                    onBackPress = {
                        viewModel.setSettingsOpen(false)
                        val active = viewModel.activeTab.value
                        if (active != null && !active.content.private && (active.content.url.isBlank() || active.content.url == "about:home")) {
                            val effectiveHome = PetalBrowserUtils.getEffectiveHomepageUrl(context)
                            if (effectiveHome != "about:home" && effectiveHome != "about:blank") {
                                viewModel.loadUrl(effectiveHome)
                            }
                        }
                    }
                )
            }

            // Extensions Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isExtensionsOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                PetalExtensionsScreen(
                    backgroundSnapshot = overlayPageSnapshot ?: tabSwitcherSnapshot,
                    onDismiss = { viewModel.setExtensionsOpen(false) }
                )
            }

            // Account & Sync Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isAccountSyncOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                PetalAccountScreen(
                    backgroundSnapshot = overlayPageSnapshot ?: tabSwitcherSnapshot,
                    onBack = { viewModel.setAccountSyncOpen(false) }
                )
            }

            // Delete Browsing Data Screen Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isDeleteOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                PetalDeleteScreen(
                    backgroundSnapshot = overlayPageSnapshot ?: tabSwitcherSnapshot,
                    onBackPress = { viewModel.setDeleteOpen(false) }
                )
            }

            // Biometric Safe Locker Screen Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isSafeLockerOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                       androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 10 },
                modifier = Modifier.fillMaxSize().zIndex(8f)
            ) {
                SafeLockerScreen(
                    onBackPress = { viewModel.setSafeLockerOpen(false) }
                )
            }

            // WebExtension Interactive Popup Overlay
            if (pendingExtensionPopup != null) {
                Box(
                    modifier = Modifier.fillMaxSize().zIndex(12f)
                        .overlayBackPose(overlayPose)
                ) {
                    PetalExtensionPopupScreen(
                        popup = pendingExtensionPopup!!,
                        onDismiss = { PetalExtensionManager.dismissPopup() }
                    )
                }
            }

            // Tab back/forward history popup (long-press Back)
            val historyPopupOpen by PetalTabHistoryState.popupOpen.collectAsStateWithLifecycle()
            if (historyPopupOpen && activeTab != null) {
                PetalTabHistorySheet(
                    tabId = activeTab!!.id,
                    isPrivate = activeTab!!.content.private,
                    onNavigate = { index -> liveTabController?.gotoHistoryIndex(index) },
                    onDismiss = { PetalTabHistoryState.dismiss() }
                )
            }

            // Privacy Shield HUD bottom sheet (hosted in root, opened via PetalPrivacyShieldSheet.show)
            val shieldRequest by PetalPrivacyShieldSheet.request.collectAsStateWithLifecycle()
            LaunchedEffect(activeTab?.id) { PetalPrivacyShieldSheet.dismiss() }
            shieldRequest?.let { req ->
                PetalPrivacyShieldBottomSheet(
                    request = req,
                    activeTab = activeTab,
                    tabController = liveTabController,
                    onToggleAdBlock = { enabled ->
                        viewModel.toggleAdBlock(enabled)
                        req.onToggleAdBlock(enabled)
                    },
                    onDismiss = { PetalPrivacyShieldSheet.dismiss() }
                )
            }

            // Find In Page Bar Overlay
            if (isFindInPageOpen) {
                val findState by viewModel.findState.collectAsStateWithLifecycle()
                val findMatchCase by viewModel.findMatchCase.collectAsStateWithLifecycle()
                val findWholeWord by viewModel.findWholeWord.collectAsStateWithLifecycle()
                PetalFindInPageBar(
                    visible = isFindInPageOpen,
                    query = findInPageQuery,
                    currentMatch = findState.current,
                    totalMatches = findState.total,
                    searched = findState.searched,
                    matchCase = findMatchCase,
                    wholeWord = findWholeWord,
                    onMatchCaseChange = { viewModel.setFindMatchCase(it) },
                    onWholeWordChange = { viewModel.setFindWholeWord(it) },
                    onClear = { viewModel.findAll("") },
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

            // AI Chat Bottom Sheet Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isAiSheetOpen,
                enter = androidx.compose.animation.fadeIn(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideInVertically(animationSpec = PetalSpring.calm()) { it / 2 },
                exit = androidx.compose.animation.fadeOut(animationSpec = PetalSpring.calm()) +
                        androidx.compose.animation.slideOutVertically(animationSpec = PetalSpring.calm()) { it / 2 },
                modifier = Modifier.fillMaxSize().zIndex(9f)
                    .overlayBackPose(overlayPose, 0.5f, 1f)
            ) {
                PetalAiChatSheet(
                    viewModel = viewModel,
                    activeTabId = activeTab?.id.orEmpty(),
                    activeTitle = activeTitle,
                    activeUrl = activeUrl,
                    isPrivate = isIncognito || (activeTab?.content?.private == true),
                    onDismiss = { viewModel.setAiSheetOpen(false) }
                )
            }

            // In-App Developer Console Sheet Overlay
            if (isDevConsoleOpen) {
                Box(
                    modifier = Modifier.fillMaxSize().zIndex(11f)
                        .overlayBackPose(overlayPose, 0.5f, 1f)
                ) {
                    PetalDevConsoleSheet(
                        onExecuteScript = { script, callback ->
                            (activity as? MainActivity)?.evaluateJavascriptInActiveTab(script, callback)
                        },
                        onDismissRequest = { viewModel.setDevConsoleOpen(false) }
                    )
                }
            }

            // Media Grabber sheet: opened by the address bar / quick tools / menu media buttons.
            val mediaSheetRequested by com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.isSheetForcedOpen
            if (mediaSheetRequested) {
                com.petal.browser.media.ytdlp.PetalMediaGrabberSheet(
                    pageUrl = activeUrl,
                    onDismiss = {
                        com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.isSheetForcedOpen.value = false
                    },
                    onPlayVideo = { videoUrl ->
                        activity?.let {
                            com.petal.browser.media.sniffer.PetalMediaSnifferOverlayBridge.openPlayer(
                                it,
                                com.petal.browser.media.sniffer.MediaInterceptor.MediaPlaybackRequest(
                                    url = videoUrl,
                                    mimeType = "video/mp4",
                                    mediaType = com.petal.browser.media.sniffer.MediaInterceptor.MediaType.MP4
                                )
                            )
                        }
                    }
                )
            }
    }
}

/** Plain holder (not Compose state): page snapshot taken as a full-screen overlay opens over a web page. */
private class OverlaySnapshotHold {
    var active: Boolean = false
    var menuOpen: Boolean = false
    var snapshot: androidx.compose.ui.graphics.ImageBitmap? = null
}

/** Plain holder (not Compose state): keeps the open-time snapshot alive while the switcher fades out. */
private class TabSwitcherHold {
    var wasOpen: Boolean = false
    var snapshot: androidx.compose.ui.graphics.ImageBitmap? = null
    var generation: Int = 0
}


/** Gesture state of the root overlay pose, read in the draw phase so a gesture never recomposes. */
private class OverlayPoseSource(
    val progress: () -> Float,
    val edge: () -> Int,
    val committing: () -> Boolean,
    val release: () -> Float,
    val touchY: () -> Float,
    val enabled: () -> Boolean,
)

/**
 * Predictive-back pose for a sheet/popup that has no surface of its own. The pose comes from the
 * selected [com.petal.browser.predictive.PetalBackStyle] through [PetalBackMotion.frame] (top layer), so
 * it honours the style, exit direction and swipe edge like every full-screen page. [originX]/[originY]
 * only place the pivot for styles that do not define their own (Scale does). Reduce motion or
 * predictive back off yields no pose at all (plain instant back).
 */
private fun Modifier.overlayBackPose(
    src: OverlayPoseSource,
    originX: Float = 0.5f,
    originY: Float = 0.5f,
): Modifier = graphicsLayer {
    val p = src.progress().coerceIn(0f, 1f)
    if (p > 0.001f && src.enabled()) {
        val style = PetalPredictiveJunction.backStyle.value
        val touchY = if (src.touchY() == 0f) size.height / 2f else src.touchY()
        val f = PetalBackMotion.frame(
            style,
            PetalBackInput(
                progress = p,
                swipeEdge = src.edge(),
                touchY = touchY,
                initialTouchY = size.height / 2f,
                widthPx = size.width,
                heightPx = size.height,
                density = density,
                blurEnabled = false,
                committing = src.committing(),
                releaseProgress = src.release(),
                settleTime = if (src.committing())
                    ((p - src.release()) / (1f - src.release()).coerceAtLeast(0.01f)).coerceIn(0f, 1f) else 0f,
                exitDirection = PetalPredictiveJunction.backExitDirection.value,
            ),
        ).top
        scaleX = f.scale
        scaleY = f.scale
        val useLayerOrigin = style == com.petal.browser.predictive.PetalBackStyle.Scale
        transformOrigin = TransformOrigin(
            if (useLayerOrigin) f.originX else originX,
            if (useLayerOrigin) f.originY else originY,
        )
        translationX = f.translationX
        translationY = f.translationY
        alpha = f.alpha
        if (f.cornerDp > 0.5f) {
            shape = RoundedCornerShape(f.cornerDp.dp)
            clip = true
        }
    }
}
