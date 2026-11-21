package com.petal.browser.download

import java.util.TreeMap

/**
 * Byte-range bookkeeping for the segmented downloader (pure, unit-testable).
 *
 * Segments are handed out front to back with an adaptive size. Once everything is assigned, an
 * idle worker calls [splitSlowest]: the in-flight segment with the most unread bytes is cut in
 * two and the tail becomes a new segment, so one slow connection can no longer hold the download
 * back at the end.
 */
internal class SegmentTable(val start: Long, val total: Long, private val firstSize: Long) {

    class Seg(val from: Long, to: Long) {
        @Volatile var to: Long = to
        /** Next byte the owning worker will read. */
        @Volatile var pos: Long = from
        @Volatile var inFlight: Boolean = false
        val length: Long get() = to - from + 1
        val remaining: Long get() = to - pos + 1
    }

    private val segments = TreeMap<Long, Seg>()
    private var assignedEnd = start

    @Synchronized fun allocate(size: Long): Seg? {
        if (assignedEnd >= total) return null
        val sz = if (segments.isEmpty()) firstSize else size
        val to = minOf(assignedEnd + sz.coerceAtLeast(1) - 1, total - 1)
        val seg = Seg(assignedEnd, to).also { it.inFlight = true }
        segments[seg.from] = seg
        assignedEnd = to + 1
        return seg
    }

    @get:Synchronized val allAssigned: Boolean get() = assignedEnd >= total
    @get:Synchronized val assignedEndOffset: Long get() = assignedEnd

    /** Cuts the in-flight segment with the most unread bytes; returns the new tail (already in flight), or null. */
    @Synchronized fun splitSlowest(minTail: Long): Seg? {
        var best: Seg? = null
        var bestRemaining = 0L
        for (s in segments.values) {
            if (!s.inFlight) continue
            val rem = s.remaining
            if (rem > bestRemaining) { best = s; bestRemaining = rem }
        }
        val b = best ?: return null
        if (bestRemaining < 2 * minTail) return null
        val mid = b.pos + bestRemaining / 2
        val tail = Seg(mid, b.to).also { it.inFlight = true }
        b.to = mid - 1
        segments[tail.from] = tail
        return tail
    }

    /** Marks [seg] as no longer being read and returns its final length (stable from here on). */
    @Synchronized fun finish(seg: Seg): Long {
        seg.inFlight = false
        return seg.length
    }

    @Synchronized fun release(seg: Seg) {
        seg.inFlight = false
    }

    @Synchronized fun reacquire(seg: Seg) {
        seg.inFlight = true
    }

    companion object {
        const val MIN_SEGMENT = 512L * 1024
        const val MAX_SEGMENT = 8L * 1024 * 1024
        const val DEFAULT_SEGMENT = 2L * 1024 * 1024
        /** Target time one segment should take, so slow links get small segments and fast links big ones. */
        const val TARGET_SEGMENT_SECONDS = 2.0

        /** Segment size for a connection moving [perConnectionBytesPerSec]; <= 0 means "not measured yet". */
        @JvmStatic
        fun adaptiveSize(perConnectionBytesPerSec: Double): Long {
            if (perConnectionBytesPerSec <= 0.0) return DEFAULT_SEGMENT
            return (perConnectionBytesPerSec * TARGET_SEGMENT_SECONDS).toLong().coerceIn(MIN_SEGMENT, MAX_SEGMENT)
        }
    }
}
