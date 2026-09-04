package com.petal.browser.engine.petal

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

public object PetalBrowserInputDiagnostics {
    private const val TAG = "PetalTouch"
    private val enabled = Log.isLoggable(TAG, Log.VERBOSE)

    @JvmStatic
    fun activityDispatch(
        event: MotionEvent,
        handled: Boolean,
        hasWindowFocus: Boolean,
        focusedView: View?,
    ) {
        if (!enabled) return
        traceEvent(
            stage = "activity",
            event = event,
            handled = handled,
            hasWindowFocus = hasWindowFocus,
            detail = "focus=${focusedView?.javaClass?.simpleName ?: "none"}",
        )
    }

    @JvmStatic
    fun activityWindowFocus(hasWindowFocus: Boolean, focusedView: View?) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=activity-window-focus hasWindowFocus=$hasWindowFocus " +
                "focus=${focusedView?.javaClass?.simpleName ?: "none"}",
        )
    }

    @JvmStatic
    fun popupState(expanded: Boolean, popupVisible: Boolean) {
        if (!enabled) return
        Log.v(TAG, "stage=browser-menu expanded=$expanded popupVisible=$popupVisible")
    }

    @JvmStatic
    fun webViewCreated(tabId: String) {
        if (!enabled) return
        val provider = WebView.getCurrentWebViewPackage()
        Log.v(
            TAG,
            "stage=webview-created tab=$tabId " +
                "provider=${provider?.packageName ?: "unknown"} " +
                "version=${provider?.versionName ?: "unknown"}",
        )
    }

    @JvmStatic
    fun webViewDispatch(
        tabId: String,
        webView: WebView,
        event: MotionEvent,
        handled: Boolean,
    ) {
        if (!enabled) return
        traceEvent(
            stage = "webview",
            event = event,
            handled = handled,
            hasWindowFocus = webView.hasWindowFocus(),
            detail = "tab=$tabId viewFocus=${webView.hasFocus()} " +
                "attached=${webView.isAttachedToWindow} shown=${webView.isShown} " +
                "scrollY=${webView.scrollY}",
        )
    }

    @JvmStatic
    fun webViewWindowFocus(tabId: String, webView: WebView, hasWindowFocus: Boolean) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=webview-window-focus tab=$tabId hasWindowFocus=$hasWindowFocus " +
                "viewFocus=${webView.hasFocus()} attached=${webView.isAttachedToWindow} " +
                "shown=${webView.isShown} scrollY=${webView.scrollY}",
        )
    }

    @JvmStatic
    fun fullscreenCustomView(stage: String, tabId: String, detail: String) {
        if (!enabled) return
        Log.v(TAG, "stage=fullscreen-$stage tab=$tabId $detail")
    }

    @JvmStatic
    fun momentumRecovery(stage: String, tabId: String, detail: String) {
        if (!enabled) return
        Log.v(TAG, "stage=momentum-$stage tab=$tabId $detail")
    }

    private fun traceEvent(
        stage: String,
        event: MotionEvent,
        handled: Boolean,
        hasWindowFocus: Boolean,
        detail: String,
    ) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=$stage action=${MotionEvent.actionToString(event.actionMasked)} " +
                "downTime=${event.downTime} eventTime=${event.eventTime} " +
                "x=${event.x.toInt()} y=${event.y.toInt()} pointers=${event.pointerCount} " +
                "windowFocus=$hasWindowFocus handled=$handled $detail",
        )
    }
}

public data class PetalBrowserMomentumInterruption(
    val downX: Float,
    val downY: Float,
    var momentumEdgeScrollY: Int,
    val momentumDirection: Int,
    var manualCorrectionApplied: Boolean = false,
)

public data class PetalBrowserWebViewScrollMetrics(
    val offsetPx: Int,
    val extentPx: Int,
    val rangePx: Int,
)

public class PetalBrowserPointerSessionState {
    private var generation = 0L
    private var active = false

    fun begin() {
        generation++
        active = true
    }

    fun end() {
        generation++
        active = false
    }

    fun snapshot(): PetalBrowserPointerSessionSnapshot = PetalBrowserPointerSessionSnapshot(
        generation = generation,
        active = active,
    )

    fun accepts(captured: PetalBrowserPointerSessionSnapshot): Boolean = snapshot() == captured
}

public data class PetalBrowserPointerSessionSnapshot(
    val generation: Long,
    val active: Boolean,
)
