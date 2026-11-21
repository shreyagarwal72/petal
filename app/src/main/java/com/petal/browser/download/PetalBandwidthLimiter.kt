package com.petal.browser.download

import java.util.concurrent.atomic.AtomicLong

/**
 * Global download speed cap (token bucket) shared by every fetch pipeline Petal runs itself:
 * the parallel range client and the HLS/DASH segment engine. 0 = unlimited.
 *
 * Burst is one second of budget. [acquire] blocks the calling worker thread (never the main
 * thread) until the bytes are allowed. Clock and sleeper are injectable for tests.
 */
object PetalBandwidthLimiter {
    @Volatile private var bytesPerSecond = 0L
    private val lock = Any()
    private var tokens = 0.0
    private var lastRefillNanos = 0L

    @Volatile internal var nanoClock: () -> Long = System::nanoTime
    @Volatile internal var sleeper: (Long) -> Unit = { ms -> Thread.sleep(ms) }

    private val totalAcquired = AtomicLong(0)

    @JvmStatic
    fun setLimitKBps(kbps: Int) {
        val v = if (kbps <= 0) 0L else kbps * 1024L
        synchronized(lock) {
            if (v != bytesPerSecond) {
                bytesPerSecond = v
                tokens = v.toDouble() // start with a full second so a change takes effect smoothly
                lastRefillNanos = nanoClock()
            }
        }
    }

    @JvmStatic
    fun limitBytesPerSecond(): Long = bytesPerSecond

    /** Blocks until [bytes] may be consumed. Throws InterruptedException if the worker is cancelled. */
    @JvmStatic
    @Throws(InterruptedException::class)
    fun acquire(bytes: Int) {
        if (bytes <= 0) return
        totalAcquired.addAndGet(bytes.toLong())
        while (true) {
            val waitMs: Long
            synchronized(lock) {
                val rate = bytesPerSecond
                if (rate <= 0L) return
                val now = nanoClock()
                if (lastRefillNanos == 0L) lastRefillNanos = now
                val elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0
                lastRefillNanos = now
                tokens = minOf(rate.toDouble(), tokens + elapsedSec * rate)
                if (tokens >= bytes || tokens >= rate) {
                    // A request larger than one second of budget goes into debt instead of deadlocking.
                    tokens -= bytes
                    return
                }
                val missing = bytes - tokens
                waitMs = (missing / rate * 1000.0).toLong().coerceIn(1L, 1000L)
            }
            sleeper(waitMs)
        }
    }

    internal fun resetForTest() {
        synchronized(lock) {
            bytesPerSecond = 0L; tokens = 0.0; lastRefillNanos = 0L
        }
        nanoClock = System::nanoTime
        sleeper = { ms -> Thread.sleep(ms) }
    }
}
