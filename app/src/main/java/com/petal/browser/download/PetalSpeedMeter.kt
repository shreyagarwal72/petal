package com.petal.browser.download

/**
 * Sliding-window throughput meter.
 *
 * The previous sampler computed an instant speed over ~500 ms and blended it 60/40 with the
 * last value, which made the readout jump around whenever a chunk arrived late, and it never
 * decayed when a transfer stalled (the last speed simply stayed on screen). This meter measures
 * over a multi-second window and applies an exponential moving average on top, so the number
 * is steady while the transfer is steady, and falls smoothly towards zero when it stops.
 *
 * Feed it with [update] on every progress tick (the bridge also ticks it once a second so a
 * stalled download still decays). Not tied to Android, so it is trivially unit-testable.
 */
class PetalSpeedMeter(
    private val windowMs: Long = 6_000L,
    private val smoothing: Double = 0.3
) {
    private val samples = java.util.ArrayDeque<LongArray>() // [timeMs, totalBytes]
    private var smoothed = 0.0

    /** Records the cumulative [totalBytes] seen at [nowMs] and returns smoothed bytes/second. */
    @Synchronized
    fun update(nowMs: Long, totalBytes: Long): Long {
        val last = samples.peekLast()
        if (last != null && totalBytes < last[1]) {
            // Counter went backwards: the download restarted from an earlier offset.
            samples.clear()
            smoothed = 0.0
        }
        samples.addLast(longArrayOf(nowMs, totalBytes))
        while (samples.size > 2 && nowMs - samples.peekFirst()!![0] > windowMs) {
            samples.removeFirst()
        }
        val first = samples.peekFirst()!!
        val span = nowMs - first[0]
        if (span < MIN_SPAN_MS) return smoothed.toLong()

        val windowSpeed = (totalBytes - first[1]) * 1000.0 / span
        smoothed = if (smoothed <= 0.0) windowSpeed else smoothed + smoothing * (windowSpeed - smoothed)
        if (smoothed < 1.0) smoothed = 0.0
        return smoothed.toLong()
    }

    @Synchronized
    fun reset() {
        samples.clear()
        smoothed = 0.0
    }

    companion object {
        private const val MIN_SPAN_MS = 400L

        /** Seconds left at [bytesPerSecond]; 0 when it cannot be estimated yet. */
        @JvmStatic
        fun etaSeconds(remainingBytes: Long, bytesPerSecond: Long): Long =
            if (remainingBytes > 0L && bytesPerSecond > 0L) remainingBytes / bytesPerSecond else 0L
    }
}
