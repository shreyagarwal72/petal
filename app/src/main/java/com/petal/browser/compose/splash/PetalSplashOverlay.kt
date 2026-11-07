package com.petal.browser.compose.splash

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-level full-screen launch splash overlay that plays once per process cold-start
 * across all surfaces (restored tabs, external links, and Petal Home).
 */
object PetalSplashOverlay {

    private const val OVERLAY_TAG = "petal_app_launch_splash_overlay"
    private var activeBackCallback: OnBackPressedCallback? = null
    private val isDismissed = AtomicBoolean(true)

    @JvmStatic
    @JvmOverloads
    fun show(activity: ComponentActivity, onFinish: Runnable? = null) {
        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!PetalSplashState.shouldPlayLaunchSplash(sp)) {
            PetalSplashState.splashActive.value = false
            onFinish?.run()
            return
        }

        isDismissed.set(false)
        PetalSplashState.splashActive.value = true

        val rootDecor = activity.window.decorView as? ViewGroup ?: return

        // Clean up any stale overlay first
        val old = rootDecor.findViewWithTag<View>(OVERLAY_TAG)
        if (old != null) {
            rootDecor.removeView(old)
        }
        activeBackCallback?.remove()

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Ignore back presses while splash animation plays
            }
        }
        activeBackCallback = backCallback
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)

        val composeView = ComposeView(activity).apply {
            tag = OVERLAY_TAG
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            translationZ = 2000f
            elevation = 2000f
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

            setContent {
                var visible by remember { mutableStateOf(true) }

                PetalExpressiveTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 1.08f, animationSpec = tween(400))
                        ) {
                            PetalSplashScreen(
                                onSplashComplete = {
                                    visible = false
                                },
                                durationMs = 1800L
                            )
                        }

                        LaunchedEffect(visible) {
                            if (!visible) {
                                kotlinx.coroutines.delay(420L)
                                dismiss(activity, onFinish)
                            }
                        }
                    }
                }
            }
        }

        val lp = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        rootDecor.addView(composeView, lp)
        composeView.bringToFront()

        // Fallback safety timeout (3.5 seconds) ensuring the splash overlay is always dismissed
        // even if coroutine or animation is cancelled
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDismissed.get()) {
                dismiss(activity, onFinish)
            }
        }, 3500L)
    }

    @JvmStatic
    @JvmOverloads
    fun dismiss(
        activity: Activity,
        onFinish: Runnable? = null
    ) {
        PetalSplashState.hasShownThisSession = true
        PetalSplashState.splashActive.value = false

        activeBackCallback?.remove()
        activeBackCallback = null

        val rootDecor = activity.window.decorView as? ViewGroup
        val targetView = rootDecor?.findViewWithTag<View>(OVERLAY_TAG)
        if (targetView != null && targetView.parent is ViewGroup) {
            (targetView.parent as ViewGroup).removeView(targetView)
        }

        if (isDismissed.compareAndSet(false, true)) {
            onFinish?.run()
        }
    }
}
