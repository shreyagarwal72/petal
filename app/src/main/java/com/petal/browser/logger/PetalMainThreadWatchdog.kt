package com.petal.browser.logger

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Diagnostic only. Every 500 ms it asks the main thread to run a tiny task.
 * If the task does not run within STALL_MS, the main thread's stack is written
 * to PetalAppLogger (and so to petal_live.log). That stack is the exact line
 * that froze the app, which the normal log never contains.
 */
object PetalMainThreadWatchdog {
    private const val TAG = "MainThreadWatchdog"
    private const val STALL_MS = 1500L
    private const val POLL_MS = 500L
    private const val REPEAT_DUMP_MS = 3000L

    @Volatile private var started = false
    @Volatile private var lastTick = SystemClock.uptimeMillis()

    @JvmStatic
    fun start() {
        if (started) return
        started = true
        val main = Looper.getMainLooper()
        val handler = Handler(main)
        val ping = object : Runnable {
            override fun run() { lastTick = SystemClock.uptimeMillis() }
        }
        Thread({
            var lastDump = 0L
            while (true) {
                try {
                    handler.post(ping)
                    Thread.sleep(POLL_MS)
                    val now = SystemClock.uptimeMillis()
                    val stalled = now - lastTick
                    if (stalled >= STALL_MS && now - lastDump >= REPEAT_DUMP_MS) {
                        lastDump = now
                        val sb = StringBuilder("Main thread stalled for ${stalled}ms. Stack:\n")
                        main.thread.stackTrace.forEach { sb.append("    at ").append(it).append('\n') }
                        PetalAppLogger.e(TAG, sb.toString())
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }, "petal-main-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }
}
