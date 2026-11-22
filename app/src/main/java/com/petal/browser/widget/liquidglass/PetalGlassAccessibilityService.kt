package com.petal.browser.widget.liquidglass

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service that automatically detects the exact on-screen pixel coordinates
 * (left, top, right, bottom) of Petal's liquid glass widgets on the launcher home screen.
 *
 * This completely eliminates the need for manual alignment screens or coordinate guessing.
 */
class PetalGlassAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PetalGlassA11y"
        private const val REBAKE_DEBOUNCE_MS = 350L

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastScanTime: Long = 0L

    private val rebakeRunnable = Runnable {
        PetalWallpaperBlurHelper.rebakeAllFromWallpaper(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "PetalGlassAccessibilityService connected")

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
        }

        scheduleScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        if (now - lastScanTime < 250L) return
        lastScanTime = now
        scanWidgets()
    }

    private fun scheduleScan() {
        handler.postDelayed({ scanWidgets() }, 500)
    }

    private fun scanWidgets() {
        try {
            val root = rootInActiveWindow ?: return
            val packageName = packageName
            var positionChanged = false

            val searchIds = AppWidgetManager.getInstance(this).getAppWidgetIds(
                ComponentName(this, PetalLiquidGlassSearchWidgetReceiver::class.java)
            ) ?: IntArray(0)
            val pillIds = AppWidgetManager.getInstance(this).getAppWidgetIds(
                ComponentName(this, PetalLiquidGlassPillWidgetReceiver::class.java)
            ) ?: IntArray(0)
            val allIds = searchIds + pillIds

            if (allIds.isEmpty()) {
                root.recycle()
                return
            }

            val foundRects = ArrayList<Rect>()
            findWidgetNodes(root, packageName, foundRects)
            root.recycle()

            if (foundRects.isNotEmpty()) {
                for (i in 0 until minOf(allIds.size, foundRects.size)) {
                    val id = allIds[i]
                    val rect = foundRects[i]
                    val existing = PetalWallpaperBlurHelper.getWidgetRect(this, id)
                    if (existing == null || existing != rect) {
                        PetalWallpaperBlurHelper.saveWidgetRect(this, id, rect)
                        positionChanged = true
                    }
                }
            }

            if (positionChanged) {
                handler.removeCallbacks(rebakeRunnable)
                handler.postDelayed(rebakeRunnable, REBAKE_DEBOUNCE_MS)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "scanWidgets failed", t)
        }
    }

    private fun findWidgetNodes(node: AccessibilityNodeInfo?, targetPkg: String, outRects: ArrayList<Rect>) {
        if (node == null) return
        try {
            val viewId = node.viewIdResourceName
            if (viewId != null && viewId.contains(targetPkg) && (viewId.contains("widget_glass_bg") || viewId.contains("petal_liquid_glass"))) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 50 && rect.height() > 30) {
                    outRects.add(rect)
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                findWidgetNodes(child, targetPkg, outRects)
                child.recycle()
            }
        } catch (_: Throwable) {
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "PetalGlassAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "PetalGlassAccessibilityService destroyed")
    }
}
