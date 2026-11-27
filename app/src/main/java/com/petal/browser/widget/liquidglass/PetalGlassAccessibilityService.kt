package com.petal.browser.widget.liquidglass

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

/**
 * Finds Petal's liquid glass widgets on the launcher and photographs the REAL pixels behind them.
 *
 * How it works:
 *  1. Each glass widget tags its background view with "petal-lg:<widgetId>" (content description),
 *     so the exact on-screen rectangle of every widget is read from the launcher's view tree.
 *  2. When a rectangle is new/changed and has stayed still for a moment, the widget is cleared,
 *     the screen is captured (API 30+), the widget area is cropped, the widget is restored and
 *     the glass is baked from that crop. Works with live, video and scrolling wallpapers.
 *
 * Only the cropped widget area is kept. Nothing leaves the device.
 */
class PetalGlassAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PetalGlassA11y"
        private const val SCAN_THROTTLE_MS = 300L
        private const val SETTLE_MS = 700L        // rectangle must be still this long
        private const val CLEAR_WAIT_MS = 450L    // let the launcher redraw the cleared widget
        private const val WATCHDOG_MS = 5000L
        private const val MAX_TRIES = 3

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var instance: PetalGlassAccessibilityService? = null

        /** Captures are stale (wallpaper changed / widget resized / user pressed refresh). */
        fun onCapturesInvalidated() {
            instance?.let { svc -> svc.handler.post { svc.failures.clear(); svc.scheduleScan() } }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()

    private val pending = HashMap<Int, Rect>()          // id -> rect we are waiting to settle on
    private val settleRunnables = HashMap<Int, Runnable>()
    private val failures = HashMap<Int, Int>()
    private val queue = ArrayList<Int>()
    private var capturing = false
    private var editModeSeen = false
    private var captureToken = 0
    private var scanScheduled = false
    private var ignoreEventsUntil = 0L
    private var lastColorsInvalidate = 0L
    @Volatile
    private var destroyed = false

    private val scanRunnable = Runnable {
        scanScheduled = false
        scanNow()
    }

    private val colorsListener: Any? =
        if (Build.VERSION.SDK_INT >= 27) {
            WallpaperManager.OnColorsChangedListener { _, _ ->
                val now = System.currentTimeMillis()
                if (now - lastColorsInvalidate > 5000L) {
                    lastColorsInvalidate = now
                    PetalWallpaperBlurHelper.invalidateCaptures(applicationContext)
                }
            }
        } else null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
        // Also set in code: an already-enabled service may keep an old cached config.
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            eventTypes = eventTypes or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        try {
            if (Build.VERSION.SDK_INT >= 27) {
                @Suppress("UNCHECKED_CAST")
                WallpaperManager.getInstance(this).addOnColorsChangedListener(
                    colorsListener as WallpaperManager.OnColorsChangedListener, handler
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "wallpaper listener failed", t)
        }
        scheduleScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || capturing) return
        if (System.currentTimeMillis() < ignoreEventsUntil) return
        if (event.packageName?.toString() == packageName) return // our own app windows
        scheduleScan()
    }

    private fun scheduleScan() {
        if (scanScheduled) return
        scanScheduled = true
        handler.postDelayed(scanRunnable, SCAN_THROTTLE_MS)
    }

    // ------------------------------------------------------------------ scanning

    private fun managedIds(): Set<Int> {
        val s = HashSet<Int>()
        PetalLiquidGlassWidgetBinder.searchIds(this).forEach { s.add(it) }
        PetalLiquidGlassWidgetBinder.pillIds(this).forEach { s.add(it) }
        return s
    }

    /** Returns id -> on-screen rect for every glass widget currently visible. */
    private fun findWidgets(ids: Set<Int>): Map<Int, Rect> {
        val out = HashMap<Int, Rect>()
        val unlabeled = ArrayList<Rect>()
        val root = rootInActiveWindow
        if (root != null) {
            try {
                collect(root, out, unlabeled, 0)
            } finally {
                @Suppress("DEPRECATION") root.recycle()
            }
        }
        // Fallback across interactive windows if overlays/system UI intercepted rootInActiveWindow.
        if (out.isEmpty()) {
            val winList = try { windows } catch (_: Throwable) { null }
            winList?.forEach { win ->
                if (win.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) return@forEach
                val winRoot = try { win.root } catch (_: Throwable) { null } ?: return@forEach
                try {
                    val pkg = winRoot.packageName?.toString()
                    if (pkg != packageName && pkg != "com.android.systemui") {
                        collect(winRoot, out, unlabeled, 0)
                    }
                } finally {
                    @Suppress("DEPRECATION") winRoot.recycle()
                }
            }
        }
        // Fallback for launchers that drop content descriptions: only safe with ONE widget.
        if (out.isEmpty() && ids.size == 1 && unlabeled.size == 1) out[ids.first()] = unlabeled[0]
        return out
    }

    private fun collect(node: AccessibilityNodeInfo, out: HashMap<Int, Rect>, unlabeled: ArrayList<Rect>, depth: Int) {
        if (depth > 40) return
        try {
            val desc = node.contentDescription?.toString()
            val isBg = desc != null && desc.startsWith(PetalLiquidGlassWidgetBinder.MARKER_PREFIX)
            val vid = node.viewIdResourceName
            if (vid != null && (vid.endsWith(":id/widget_resize_frame") || vid.endsWith(":id/drop_target_bar"))) {
                editModeSeen = true
            }
            if (isBg) {
                val id = desc!!.substring(PetalLiquidGlassWidgetBinder.MARKER_PREFIX.length).toIntOrNull()
                val r = Rect()
                node.getBoundsInScreen(r)
                if (id != null && r.width() > 50 && r.height() > 30) out[id] = r
            } else if (node.viewIdResourceName?.endsWith(":id/widget_glass_bg") == true &&
                node.packageName?.toString() != packageName
            ) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (r.width() > 50 && r.height() > 30) unlabeled.add(r)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collect(child, out, unlabeled, depth + 1)
                @Suppress("DEPRECATION") child.recycle()
            }
        } catch (_: Throwable) {
        }
    }

    private fun scanNow() {
        if (capturing) return
        try {
            val ids = managedIds()
            if (ids.isEmpty()) return
            val (sw, sh) = PetalWallpaperBlurHelper.getScreenSize(this)
            editModeSeen = false
            val seen = findWidgets(ids)
            if (editModeSeen) { scheduleScan(); return } // resizing/dragging: wait until it ends
            for ((id, rect) in seen) {
                if (id !in ids) continue
                // Ignore widgets that are only partly on screen (mid-scroll / other pages)
                if (rect.left < -2 || rect.top < -2 || rect.right > sw + 2 || rect.bottom > sh + 2) continue
                onRectSeen(id, rect)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "scan failed", t)
        }
    }

    private fun onRectSeen(id: Int, rect: Rect) {
        val saved = PetalWallpaperBlurHelper.getWidgetRect(this, id)
        if (saved != rect) {
            PetalWallpaperBlurHelper.saveWidgetRect(this, id, rect)
            failures.remove(id)
        }
        if (PetalWallpaperBlurHelper.cleanMatches(this, id, rect)) {
            pending.remove(id)
            settleRunnables.remove(id)?.let { handler.removeCallbacks(it) }
            return
        }
        if ((failures[id] ?: 0) >= MAX_TRIES) return
        if (pending[id] == rect && settleRunnables.containsKey(id)) return // already waiting
        settleRunnables.remove(id)?.let { handler.removeCallbacks(it) }
        pending[id] = Rect(rect)
        val r = Runnable {
            settleRunnables.remove(id)
            if (!queue.contains(id)) queue.add(id)
            pump()
        }
        settleRunnables[id] = r
        handler.postDelayed(r, SETTLE_MS)
        scheduleScan() // keep watching so a moving widget cancels the pending capture
    }

    // ------------------------------------------------------------------ capture

    private fun pump() {
        if (capturing || queue.isEmpty()) return
        startCapture(queue.removeAt(0))
    }

    private fun finishCapture(id: Int, token: Int) {
        handler.post {
            if (token != captureToken) return@post
            captureToken++
            capturing = false
            ignoreEventsUntil = System.currentTimeMillis() + 800L
            handler.postDelayed({ pump() }, 400L)
            handler.postDelayed({ scheduleScan() }, 900L)
        }
    }

    private fun restore(id: Int) {
        if (destroyed) return
        val applyRestore = Runnable {
            if (destroyed) return@Runnable
            try {
                val mgr = AppWidgetManager.getInstance(this)
                PetalLiquidGlassWidgetBinder.update(this, mgr, id, PetalLiquidGlassWidgetBinder.isPill(this, id))
            } catch (t: Throwable) {
                Log.w(TAG, "restore failed", t)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyRestore.run()
        } else {
            handler.post(applyRestore)
        }
    }

    private fun fallbackBake(id: Int) {
        val isPill = PetalLiquidGlassWidgetBinder.isPill(this, id)
        PetalWallpaperBlurHelper.bakeWidget(this, id, isPill, PetalBlurConfig.load(this))
        restore(id)
    }

    private fun startCapture(id: Int) {
        if (id !in managedIds()) { pump(); return }
        val rect = PetalWallpaperBlurHelper.getWidgetRect(this, id)
        if (rect == null) { pump(); return }
        if (Build.VERSION.SDK_INT < 30) {
            bg.execute { fallbackBake(id) } // no screenshot API: best effort from wallpaper
            failures[id] = MAX_TRIES
            pump()
            return
        }
        capturing = true
        val token = ++captureToken
        val isPill = PetalLiquidGlassWidgetBinder.isPill(this, id)
        PetalLiquidGlassWidgetBinder.update(this, AppWidgetManager.getInstance(this), id, isPill, clearForCapture = true)
        // Watchdog: never leave a widget blank if the screenshot callback never arrives.
        handler.postDelayed({
            if (capturing && token == captureToken) {
                Log.w(TAG, "capture watchdog fired for $id")
                failures[id] = (failures[id] ?: 0) + 1
                restore(id)
                finishCapture(id, token)
            }
        }, WATCHDOG_MS)
        handler.postDelayed({ takeShot(id, rect, token) }, CLEAR_WAIT_MS)
    }

    private fun takeShot(id: Int, rect: Rect, token: Int) {
        if (token != captureToken || !capturing) return
        try {
            // Still where we think it is? (user may have scrolled during the wait)
            val now = findWidgets(managedIds())[id]
            if (now == null || now != rect) {
                restore(id)
                finishCapture(id, token)
                return
            }
            if (Build.VERSION.SDK_INT < 30) return
            takeScreenshot(Display.DEFAULT_DISPLAY, bg, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    var ok = false
                    try {
                        val hb = result.hardwareBuffer
                        try {
                            val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                            val full = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hw?.recycle()
                            if (full != null) {
                                val (sw, sh) = PetalWallpaperBlurHelper.getScreenSize(this@PetalGlassAccessibilityService)
                                val fx = full.width.toFloat() / sw
                                val fy = full.height.toFloat() / sh
                                val l = (rect.left * fx).toInt().coerceIn(0, full.width - 1)
                                val t = (rect.top * fy).toInt().coerceIn(0, full.height - 1)
                                val w = (rect.width() * fx).toInt().coerceIn(1, full.width - l)
                                val h = (rect.height() * fy).toInt().coerceIn(1, full.height - t)
                                val crop = Bitmap.createBitmap(full, l, t, w, h)
                                full.recycle()
                                PetalWallpaperBlurHelper.saveCleanBitmap(applicationContext, id, crop, rect)
                                crop.recycle()
                                ok = true
                            }
                        } finally {
                            hb.close()
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "screenshot processing failed", t)
                    }
                    if (ok) {
                        PetalWallpaperBlurHelper.bakeWidget(
                            applicationContext, id, PetalLiquidGlassWidgetBinder.isPill(applicationContext, id),
                            PetalBlurConfig.load(applicationContext)
                        )
                    } else {
                        handler.post { failures[id] = (failures[id] ?: 0) + 1 }
                    }
                    restore(id)
                    finishCapture(id, token)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed: $errorCode")
                    handler.post {
                        // "too soon after the last screenshot" is not a real failure: just retry
                        if (errorCode != AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                            failures[id] = (failures[id] ?: 0) + 1
                        }
                    }
                    restore(id)
                    finishCapture(id, token)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "takeShot failed", t)
            failures[id] = (failures[id] ?: 0) + 1
            restore(id)
            finishCapture(id, token)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
        isRunning = false
        instance = null
        handler.removeCallbacksAndMessages(null)
        try {
            if (Build.VERSION.SDK_INT >= 27 && colorsListener != null) {
                WallpaperManager.getInstance(this)
                    .removeOnColorsChangedListener(colorsListener as WallpaperManager.OnColorsChangedListener)
            }
        } catch (_: Throwable) {
        }
        bg.shutdown()
    }
}
