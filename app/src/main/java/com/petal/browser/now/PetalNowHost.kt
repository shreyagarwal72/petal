package com.petal.browser.now

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.activity.FileViewerActivity
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.browser.viewmodel.PetalBrowserViewModel
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.compose.downloads.PetalLiveAlertManager
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.live.LiveActionActionListener
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.media.PetalMediaSessionService
import com.petal.browser.passwords.PetalPasswordsActivity
import com.petal.browser.ui.components.BarCollapseState
import com.petal.browser.ui.components.BarMetrics
import com.petal.browser.ui.components.rememberReduceMotion
import com.petal.browser.view.PetalToast

/**
 * In-app Material 3 Expressive host layer for Petal Now.
 *
 * Mounted in `PetalRootApp` above page/bar content and below dialogs/modals.
 */
@Composable
fun PetalNowHost(
    viewModel: PetalBrowserViewModel,
    activeUrl: String,
    isHome: Boolean,
    isAdBlockEnabled: Boolean,
    isBottomAddressBar: Boolean,
    isFullscreen: Boolean,
    suppressForOverlay: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val prefs = rememberPetalNowPrefs()
    val motion = remember(prefs, reduceMotion) { PetalNowMotion(prefs, reduceMotion) }

    val busState by PetalLiveActionBus.state.collectAsStateWithLifecycle()
    val adBlockRevision by PetalAdBlockEngine.stateVersion.collectAsStateWithLifecycle()

    // Bind live action listener so every download/media/vault/file/profile button actually works.
    DisposableEffect(context, viewModel) {
        val listener = object : LiveActionActionListener {
            override fun onDownloadTogglePause(downloadId: Long) {
                PetalFetchDownloadBridge.togglePauseResume(context, downloadId)
            }

            override fun onDownloadCancel(downloadId: Long) {
                PetalFetchDownloadBridge.cancel(context, downloadId)
                PetalLiveAlertManager.stopTracking(context, downloadId)
                PetalLiveActionBus.dismiss("dl_$downloadId")
            }

            override fun onMediaTogglePlayPause() {
                val media = PetalLiveActionBus.getCurrentState().primary as? com.petal.browser.live.LiveActionItem.Media
                    ?: PetalLiveActionBus.getCurrentState().secondary as? com.petal.browser.live.LiveActionItem.Media
                val action = if (media?.isPlaying == true) {
                    PetalMediaSessionService.ACTION_PAUSE
                } else {
                    PetalMediaSessionService.ACTION_PLAY
                }
                sendMediaCommand(context, action)
            }

            override fun onMediaSkip(seconds: Int) {
                val action = if (seconds < 0) {
                    PetalMediaSessionService.ACTION_SKIP_BACKWARD
                } else {
                    PetalMediaSessionService.ACTION_SKIP_FORWARD
                }
                sendMediaCommand(context, action)
            }

            override fun onOpenDownloads() {
                viewModel.openDownloads()
            }

            override fun onOpenBrowser() {
                viewModel.dismissTopOverlay()
            }

            override fun onOpenFileViewer() {
                viewModel.openDownloads()
            }

            override fun onOpenProfile() {
                viewModel.openAccountSync()
            }

            override fun onOpenPasswords() {
                try {
                    val intent = Intent(context, PetalPasswordsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    viewModel.openSettings(SettingsCategory.PRIVACY)
                }
            }

            override fun onCopyText(text: String, label: String) {
                try {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
                    PetalToast.show(context, "Copied to clipboard")
                } catch (_: Throwable) {
                }
            }

            override fun onOpenDownloadedFile(localUri: String, fileName: String) {
                if (localUri.isBlank()) {
                    viewModel.openDownloads()
                    return
                }
                try {
                    val intent = Intent(context, FileViewerActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse(localUri)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (_: Throwable) {
                    viewModel.openDownloads()
                }
            }
        }
        PetalLiveActionBus.setActionListener(listener)
        onDispose {
            if (PetalLiveActionBus.getActionListener() === listener) {
                PetalLiveActionBus.setActionListener(null)
            }
            PetalNowAnchor.clear()
        }
    }

    val pageContext = remember(activeUrl, isHome, isAdBlockEnabled, adBlockRevision) {
        PetalNowPageContext(
            activeUrl = activeUrl,
            isHome = isHome,
            globalAdBlockEnabled = isAdBlockEnabled,
            adBlockRevision = adBlockRevision,
        )
    }

    val onToggleAdBlock = remember(viewModel) {
        { enabled: Boolean ->
            viewModel.toggleAdBlock(enabled)
        }
    }

    val onToggleWhitelist = remember(context, viewModel) {
        { domain: String, allowOnSite: Boolean ->
            if (allowOnSite) {
                PetalAdBlockEngine.addDomainToWhitelist(context, domain)
            } else {
                PetalAdBlockEngine.removeDomainFromWhitelist(context, domain)
            }
            viewModel.reload()
        }
    }

    val onOpenAdSettings = remember(viewModel) {
        { viewModel.openSettings(SettingsCategory.PRIVACY) }
    }

    val slots = remember(busState, prefs, pageContext, context) {
        PetalNowBusMapper.map(
            context = context,
            busState = busState,
            prefs = prefs,
            pageContext = pageContext,
            onToggleAdBlock = onToggleAdBlock,
            onToggleDomainWhitelist = onToggleWhitelist,
            onOpenAdBlockSettings = onOpenAdSettings,
        )
    }

    val primary = slots.primary
    var displayState by remember(primary?.slotKey) {
        mutableStateOf(primary?.defaultState ?: PetalNowDisplayState.COMPACT)
    }

    // Keep resting vs active display state synchronized when primary switches mode in place.
    LaunchedEffect(primary?.isHeroResting) {
        if (primary != null && displayState != PetalNowDisplayState.EXPANDED) {
            displayState = primary.defaultState
        }
    }

    val shouldShow = primary != null && !isFullscreen && !suppressForOverlay && prefs.mode != PetalNowMode.OFF

    LaunchedEffect(shouldShow) {
        if (!shouldShow) {
            PetalNowAnchor.clear()
            if (primary != null) displayState = primary.defaultState
        }
    }

    val statusBarTopInset = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues()
        .calculateTopPadding()

    // Sit cleanly below status bar, and ride beneath any visible top toolbar / tab strip.
    val topBarClearance = if (!isHome && !isBottomAddressBar) {
        val measuredTopDp = with(density) { BarMetrics.topBarPx.toDp() }
        val expandedTop = if (measuredTopDp > 0.dp) measuredTopDp + 6.dp else statusBarTopInset + 66.dp
        val collapsedTop = statusBarTopInset + 46.dp
        val frac = BarCollapseState.collapsedFraction.coerceIn(0f, 1f)
        androidx.compose.ui.unit.lerp(expandedTop, collapsedTop, frac)
    } else {
        statusBarTopInset + 6.dp
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrimless outside-tap catcher only while the capsule is expanded so it never steals page touches.
        if (shouldShow && displayState == PetalNowDisplayState.EXPANDED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(primary?.slotKey) {
                        detectTapGestures(
                            onTap = {
                                if (primary != null) displayState = primary.defaultState
                            }
                        )
                    }
            )
        }

        AnimatedVisibility(
            visible = shouldShow,
            enter = fadeIn(motion.fade(190)) +
                scaleIn(initialScale = if (reduceMotion) 1f else 0.88f, animationSpec = motion.float(210)) +
                slideInVertically(animationSpec = motion.intOffset(210)) { -it / 3 },
            exit = fadeOut(motion.fade(140)) +
                scaleOut(targetScale = if (reduceMotion) 1f else 0.88f, animationSpec = motion.fade(140)) +
                slideOutVertically(animationSpec = motion.intOffset(140)) { -it / 3 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topBarClearance),
        ) {
            if (primary != null) {
                PetalNowCapsule(
                    entry = primary,
                    satellite = slots.satellite,
                    displayState = displayState,
                    motion = motion,
                    onChangeDisplayState = { next -> displayState = next },
                    onSwapSatelliteToPrimary = {
                        PetalLiveActionBus.swapSlots()
                        displayState = PetalNowDisplayState.EXPANDED
                    },
                    onDismiss = {
                        val busId = primary.rawBusId
                        if (busId != null) {
                            PetalLiveActionBus.dismiss(busId)
                        } else {
                            displayState = PetalNowDisplayState.COLLAPSED
                        }
                    },
                    onBoundsChanged = { rect ->
                        PetalNowAnchor.update(rect, visible = shouldShow)
                    },
                )
            }
        }
    }
}

private fun sendMediaCommand(context: Context, action: String) {
    try {
        val intent = Intent(context, PetalMediaSessionService::class.java).apply {
            this.action = action
        }
        if (action == PetalMediaSessionService.ACTION_PLAY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    } catch (_: Throwable) {
    }
}
