package com.petal.browser.island

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.BuildConfig
import com.petal.browser.live.LiveActionBusState
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.ui.theme.PetalExpressiveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Draws the NowBar in its own window, in one of two places:
 *
 *  - ACCESSIBILITY: a TYPE_ACCESSIBILITY_OVERLAY window owned by [PetalIslandAccessibilityService].
 *    Sits inside the status bar / camera cutout (y = 0), above every app, shown even while Petal
 *    is open, and needs no "Display over other apps" permission. Non-Play builds only.
 *  - OVERLAY: a TYPE_APPLICATION_OVERLAY window ("Display over other apps"). Only while Petal is
 *    NOT on screen, because inside Petal the in-app bar ([PetalNowBar]) is used.
 *
 * In IN_APP placement this controller keeps no window at all.
 *
 * The window is WRAP_CONTENT, so touches outside the island go straight to whatever is under it.
 * Because a wrap-content window clips anything drawn outside its bounds, the user's X/Y offset
 * and the camera alignment are applied through the window position (x / y), not inside Compose.
 */
object PetalLiveOverlayController {

    private const val TAG = "PetalLiveOverlay"
    private const val EXIT_ANIMATION_MS = 700L

    private data class OverlayUi(val bus: LiveActionBusState, val settings: IslandSettings)
    private data class Inputs(
        val settings: IslandSettings,
        val bus: LiveActionBusState,
        val foreground: Boolean,
        val a11y: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ui = MutableStateFlow(OverlayUi(LiveActionBusState(), IslandSettings()))

    private lateinit var app: Application
    private var started = false
    private var view: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: PetalOverlayLifecycleOwner? = null
    private var removeJob: Job? = null
    private var hostedInA11y = false
    private var expanded = false
    private var lastSettings = IslandSettings()

    /** Call once from Application.onCreate() on the main process. Idle and cheap until needed. */
    fun init(application: Application) {
        if (started) return
        started = true
        app = application
        scope.launch {
            combine(
                IslandSettings.flow(app),
                PetalLiveActionBus.state,
                PetalForegroundTracker.inForeground,
                PetalIslandAccessibilityService.connected
            ) { settings, bus, foreground, a11y -> Inputs(settings, bus, foreground, a11y) }
                .collect { evaluate(it) }
        }
    }

    /** The placement the bar is using right now. Used by the in-app host to know when to step aside. */
    fun resolvePlacement(context: Context, settings: IslandSettings, a11yConnected: Boolean): IslandPlacement =
        PetalIslandPlacementResolver.resolve(
            mode = settings.placementMode,
            overlayEnabledLegacy = settings.overlayEnabled,
            accessibilityConnected = a11yConnected,
            overlayGranted = PetalIslandPermissions.canDrawOverlays(context),
            isPlayBuild = BuildConfig.IS_PLAY_STORE_BUILD || !BuildConfig.ENABLE_ACCESSIBILITY_ISLAND
        )

    private fun evaluate(input: Inputs) {
        val (settings, bus, foreground, a11yConnected) = input
        lastSettings = settings
        val placement = resolvePlacement(app, settings, a11yConnected)
        val wantsWindow = when (placement) {
            IslandPlacement.ACCESSIBILITY -> true
            IslandPlacement.OVERLAY -> !foreground
            IslandPlacement.IN_APP -> false
        }
        val allowed = wantsWindow && PetalIslandPermissions.shouldShowIsland(app, settings)

        // Switched between the accessibility and app-overlay hosts: the old window cannot be reused.
        if (view != null && hostedInA11y != (placement == IslandPlacement.ACCESSIBILITY)) {
            removeJob?.cancel()
            detach()
        }

        if (allowed && bus.hasActiveAction) {
            removeJob?.cancel()
            removeJob = null
            if (ensureAttached(placement == IslandPlacement.ACCESSIBILITY, settings)) {
                // Offsets live in the window position; Compose must not apply them a second time.
                ui.value = OverlayUi(bus, settings.copy(offsetYDp = 0))
                applyPosition()
            }
        } else if (view != null) {
            if (!allowed) {
                // Placement or settings no longer want a window: drop it at once.
                removeJob?.cancel()
                detach()
                return
            }
            // Let the island animate out, then drop the window.
            ui.value = OverlayUi(LiveActionBusState(), settings.copy(offsetYDp = 0))
            if (removeJob?.isActive != true) {
                removeJob = scope.launch {
                    delay(EXIT_ANIMATION_MS)
                    detach()
                }
            }
        }
    }

    private fun ensureAttached(a11y: Boolean, settings: IslandSettings): Boolean {
        if (view != null) return true
        val hostContext: Context = if (a11y) {
            PetalIslandAccessibilityService.instance ?: return false
        } else app
        val wm = hostContext.getSystemService<WindowManager>() ?: return false

        val lifecycleOwner = PetalOverlayLifecycleOwner().also { it.onCreate() }
        val actions = PetalOverlayActions(app)

        val composeView = ComposeView(hostContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PetalExpressiveTheme {
                    val current by ui.collectAsStateWithLifecycle()
                    PetalLiveIsland(
                        state = current.bus,
                        settings = current.settings,
                        listener = actions,
                        onExpandedChange = { isExpanded ->
                            expanded = isExpanded
                            applyPosition()
                        }
                    )
                }
            }
        }

        val lp = layoutParams(a11y, settings)
        return try {
            wm.addView(composeView, lp)
            view = composeView
            windowManager = wm
            params = lp
            owner = lifecycleOwner
            hostedInA11y = a11y
            expanded = false
            // The camera position is only known once the view has a window, so re-place shortly after.
            composeView.postDelayed({ applyPosition() }, 250L)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not show NowBar window", t)
            lifecycleOwner.onDestroy()
            false
        }
    }

    /** Moves the existing window: camera alignment + user offsets while collapsed, centred while expanded. */
    private fun applyPosition() {
        val v = view ?: return
        val lp = params ?: return
        val wm = windowManager ?: return
        val s = lastSettings
        val density = app.resources.displayMetrics.density
        val baseY = if (s.anchorStatusBar) 0 else statusBarHeightPx()

        val newX: Int
        val newY: Int
        if (expanded) {
            newX = 0
            newY = baseY
        } else {
            val hw = CutoutMetrics.cutoutHorizontalOffsetDp(v)
            newX = (PetalIslandPlacementMath.effectiveOffsetXDp(s.alignmentMode, hw, s.offsetXDp) * density).roundToInt()
            newY = baseY + (s.offsetYDp * density).roundToInt()
        }
        val flagsNew = windowFlags(s)
        if (lp.x != newX || lp.y != newY || lp.flags != flagsNew) {
            lp.x = newX
            lp.y = newY
            lp.flags = flagsNew
            try { wm.updateViewLayout(v, lp) } catch (t: Throwable) { Log.w(TAG, "updateViewLayout failed", t) }
        }
    }

    private fun detach() {
        val v = view ?: return
        try {
            windowManager?.removeViewImmediate(v)
        } catch (t: Throwable) {
            Log.w(TAG, "removeView failed", t)
        }
        try { v.disposeComposition() } catch (_: Throwable) {}
        owner?.onDestroy()
        view = null
        owner = null
        params = null
        windowManager = null
        removeJob = null
        expanded = false
    }

    private fun windowFlags(settings: IslandSettings): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        // NO_LIMITS lets the window sit over the status bar and cutout instead of below them.
        if (settings.anchorStatusBar) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return flags
    }

    private fun layoutParams(a11y: Boolean, settings: IslandSettings): WindowManager.LayoutParams {
        val type = if (a11y) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            windowFlags(settings),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = if (settings.anchorStatusBar) 0 else statusBarHeightPx()
            title = "PetalNowBar"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun statusBarHeightPx(): Int {
        val res = app.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else (24 * res.displayMetrics.density).toInt()
    }
}
