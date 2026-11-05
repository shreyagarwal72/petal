package com.petal.browser.motion

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * MotionGate
 * ─────────────────────────────────────────────────────────────────────────────
 * Central motion coordinator and single source of truth for motion lifecycle.
 *
 * 1. Exposes [isAnimating] (counter-based, so multiple overlapping transitions are tracked safely).
 * 2. Exposes [isReduceMotion] and [isLowEndDevice] detection.
 * 3. While [isAnimating] is true (or on low-end devices), heavy graphics like liquid glass and dynamic blur
 *    must drop to a cheap static fill and restore when the motion settles.
 * 4. Manages the snapshot policy for heavy content: capture once before transition starts,
 *    reuse during transition, release after. Defers thumbnail capture, suggestion list updates,
 *    and favicon loads until all motion completes.
 */
object MotionGate {

    private val activeTransitionCount = mutableIntStateOf(0)
    private val rawCounter = AtomicInteger(0)

    /** Compose-observable state of whether any motion transition is actively in flight. */
    val isAnimating: State<Boolean> = derivedStateOf { activeTransitionCount.intValue > 0 }

    /** Thread-safe snapshot of active animation state for non-Compose callers. */
    @JvmStatic
    val isRunning: Boolean
        get() = rawCounter.get() > 0

    /**
     * Acquired handle for a transition. Invoking [release] decrements the active transition counter.
     */
    fun interface TransitionHandle {
        fun release()
    }

    /**
     * Starts tracking a transition. Must be paired with a call to [endTransition]
     * or [TransitionHandle.release].
     *
     * @param name Optional transition identifier for JankStats tracking.
     */
    @JvmStatic
    fun startTransition(name: String = "unnamed"): TransitionHandle {
        rawCounter.incrementAndGet()
        activeTransitionCount.intValue++
        PetalJankTracker.startTransition(name)
        var released = false
        return TransitionHandle {
            if (!released) {
                released = true
                endTransition(name)
            }
        }
    }

    /**
     * Ends tracking a transition.
     */
    @JvmStatic
    fun endTransition(name: String = "unnamed") {
        PetalJankTracker.endTransition(name)
        if (rawCounter.get() > 0) {
            rawCounter.decrementAndGet()
        }
        if (activeTransitionCount.intValue > 0) {
            activeTransitionCount.intValue--
        }
        if (rawCounter.get() == 0) {
            // All animations settled: flush deferred work and clear active transition snapshots
            flushDeferredTasks()
        }
    }

    /**
     * Convenience helper to run a block within an active motion gate.
     */
    inline fun <R> withMotion(name: String = "unnamed", block: () -> R): R {
        val handle = startTransition(name)
        try {
            return block()
        } finally {
            handle.release()
        }
    }

    /**
     * Convenience suspend helper to run a coroutine block within an active motion gate.
     */
    suspend fun <R> withMotionSuspend(name: String = "unnamed", block: suspend () -> R): R {
        val handle = startTransition(name)
        try {
            return block()
        } finally {
            handle.release()
        }
    }

    /**
     * True if system animations are disabled or reduced motion is requested.
     */
    @JvmStatic
    fun isReduceMotion(context: Context): Boolean {
        return try {
            val animatorsDisabled = !android.animation.ValueAnimator.areAnimatorsEnabled()
            val durationScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            val transitionScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            animatorsDisabled || durationScale <= 0f || transitionScale <= 0f
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True on low-RAM or constrained hardware devices.
     */
    @JvmStatic
    fun isLowEndDevice(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true || Runtime.getRuntime().availableProcessors() <= 4
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true if liquid glass or expensive real-time blur should be simplified
     * to a cheap static translucent fill (during transitions or on low-end devices).
     */
    @Composable
    fun shouldSimplifyEffects(): Boolean {
        val context = LocalContext.current
        return isAnimating.value || remember(context) { isLowEndDevice(context) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Snapshot Policy & Deferred Work for Heavy Content (Requirement 3)
    // ─────────────────────────────────────────────────────────────────────────

    @Volatile
    private var transitionSnapshot: Bitmap? = null

    /**
     * Captures a heavy page/content snapshot once before a transition begins.
     * Reused for the entire duration of the transition and released when all transitions settle.
     */
    @JvmStatic
    fun acquireTransitionSnapshot(captureBlock: () -> Bitmap?): Bitmap? {
        val existing = transitionSnapshot
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        val fresh = captureBlock()
        transitionSnapshot = fresh
        return fresh
    }

    /** Returns the currently cached transition snapshot, or null if not in a transition. */
    @JvmStatic
    fun getTransitionSnapshot(): Bitmap? {
        val bmp = transitionSnapshot
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    /** Manually releases the transition snapshot. */
    @JvmStatic
    fun releaseTransitionSnapshot() {
        transitionSnapshot = null
    }

    private val deferredTasks = CopyOnWriteArrayList<Runnable>()

    /**
     * Defers execution of a non-critical task (such as thumbnail cache write, suggestion search,
     * or favicon loading) until all active transitions have settled.
     * If no transition is in flight, executes immediately.
     */
    @JvmStatic
    fun postAfterTransition(task: Runnable) {
        if (!isRunning) {
            task.run()
        } else {
            deferredTasks.add(task)
        }
    }

    private fun flushDeferredTasks() {
        releaseTransitionSnapshot()
        if (deferredTasks.isNotEmpty()) {
            val tasks = ArrayList(deferredTasks)
            deferredTasks.clear()
            for (task in tasks) {
                try {
                    task.run()
                } catch (_: Throwable) {}
            }
        }
    }
}
