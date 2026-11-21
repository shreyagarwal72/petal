package com.petal.browser.download.queue

/** Pure scheduling rules for the download queue (no Android, no I/O), so they can be unit-tested. */
object QueueScheduler {

    data class Network(val online: Boolean, val metered: Boolean)
    data class Limits(val maxConcurrent: Int, val wifiOnlyGlobal: Boolean)

    const val MAX_ATTEMPTS = 6

    /**
     * Entries that may start now. [activeCount] counts everything already transferring (queue-started
     * or not); [activeUrls] are URLs already downloading, so the same URL never runs twice at once.
     */
    @JvmStatic
    fun pickToStart(
        entries: List<QueuedDownload>,
        activeCount: Int,
        limits: Limits,
        network: Network,
        now: Long,
        activeUrls: Set<String> = emptySet()
    ): List<QueuedDownload> {
        val slots = limits.maxConcurrent - activeCount
        if (slots <= 0 || !network.online) return emptyList()
        val picked = ArrayList<QueuedDownload>()
        val urls = HashSet(activeUrls)
        val ordered = entries.sortedWith(
            compareByDescending<QueuedDownload> { it.priority }.thenBy { it.createdAt }.thenBy { it.id }
        )
        for (e in ordered) {
            if (picked.size >= slots) break
            if (e.state != QueuedDownload.STATE_QUEUED) continue
            if (e.scheduledAt > now || e.nextAttemptAt > now) continue
            if ((e.wifiOnly || limits.wifiOnlyGlobal) && network.metered) continue
            if (e.storeId == null && !urls.add(e.url)) continue
            picked.add(e)
        }
        return picked
    }

    /** Exponential backoff: 2 s, 4 s, 8 s ... capped at 60 s. [attempts] is the number of failures so far (>= 1). */
    @JvmStatic
    fun backoffMs(attempts: Int): Long = (1000L shl attempts.coerceIn(1, 6)).coerceAtMost(60_000L)

    @JvmStatic
    fun canRetry(attempts: Int): Boolean = attempts < MAX_ATTEMPTS
}
