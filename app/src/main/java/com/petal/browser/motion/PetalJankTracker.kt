package com.petal.browser.motion

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.preference.PreferenceManager
import com.petal.browser.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * PetalJankTracker
 * ─────────────────────────────────────────────────────────────────────────────
 * Tracks and logs janky frames per transition name, backed by Android's FrameMetrics
 * (JankStats architecture). Active when [BuildConfig.DEBUG] is true or when the debug
 * preference "sp_motion_jank_stats_enabled" is set.
 */
object PetalJankTracker {
    private const val TAG = "PetalJankStats"
    private val metricsThread by lazy { HandlerThread("PetalJankMetrics").apply { start() } }
    private val metricsHandler by lazy { Handler(metricsThread.looper) }

    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null
    private var isAttached = false
    private var isEnabled = false

    private class TransitionMetrics(val name: String) {
        val totalFrames = AtomicInteger(0)
        val jankFrames = AtomicInteger(0)
        var maxDurationMs = 0.0
    }

    private val activeTransitions = ConcurrentHashMap<String, TransitionMetrics>()

    @JvmStatic
    fun attach(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || isAttached) return
        val sp = try {
            PreferenceManager.getDefaultSharedPreferences(activity)
        } catch (_: Throwable) {
            null
        }
        isEnabled = BuildConfig.DEBUG || (sp?.getBoolean("sp_motion_jank_stats_enabled", false) == true)
        if (!isEnabled) return

        frameMetricsListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
            if (activeTransitions.isEmpty()) return@OnFrameMetricsAvailableListener
            val totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val deadlineNs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                frameMetrics.getMetric(FrameMetrics.DEADLINE)
            } else {
                16_666_667L // 60Hz fallback
            }
            val durationMs = totalDurationNs / 1_000_000.0
            val isJank = totalDurationNs > deadlineNs

            for (m in activeTransitions.values) {
                m.totalFrames.incrementAndGet()
                if (isJank) {
                    m.jankFrames.incrementAndGet()
                }
                synchronized(m) {
                    if (durationMs > m.maxDurationMs) {
                        m.maxDurationMs = durationMs
                    }
                }
            }
        }
        try {
            activity.window.addOnFrameMetricsAvailableListener(frameMetricsListener, metricsHandler)
            isAttached = true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to attach FrameMetrics listener", t)
        }
    }

    @JvmStatic
    fun startTransition(name: String) {
        if (!isEnabled) return
        activeTransitions.computeIfAbsent(name) { TransitionMetrics(name) }
    }

    @JvmStatic
    fun endTransition(name: String) {
        if (!isEnabled) return
        val metrics = activeTransitions.remove(name) ?: return
        val total = metrics.totalFrames.get()
        val jank = metrics.jankFrames.get()
        val jankPct = if (total > 0) (jank * 100f / total) else 0f
        Log.i(
            TAG,
            "[JankStats] Transition '$name': totalFrames=$total, jankFrames=$jank (${"%.1f".format(jankPct)}%), maxDuration=${"%.2f".format(metrics.maxDurationMs)}ms"
        )
    }
}
