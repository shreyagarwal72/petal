package com.petal.browser.download

/**
 * Caps simultaneous HTTP connections across all downloads: [DownloadPrefs.GLOBAL_CONNECTION_CAP]
 * in total and [DownloadPrefs.PER_HOST_CONNECTION_CAP] per host. A download always gets at least
 * one connection, so the budget can only slow a download down, never block it.
 */
object PetalConnectionBudget {
    private val lock = Any()
    private var total = 0
    private val perHost = HashMap<String, Int>()

    @Volatile internal var globalCap = DownloadPrefs.GLOBAL_CONNECTION_CAP
    @Volatile internal var hostCap = DownloadPrefs.PER_HOST_CONNECTION_CAP

    /** @return how many connections were granted (1..[requested]). Release the same number later. */
    @JvmStatic
    fun acquire(host: String, requested: Int): Int = synchronized(lock) {
        val h = host.lowercase()
        val hostFree = (hostCap - (perHost[h] ?: 0)).coerceAtLeast(0)
        val globalFree = (globalCap - total).coerceAtLeast(0)
        val granted = minOf(requested, hostFree, globalFree).coerceAtLeast(1)
        total += granted
        perHost[h] = (perHost[h] ?: 0) + granted
        granted
    }

    @JvmStatic
    fun release(host: String, count: Int) = synchronized(lock) {
        val h = host.lowercase()
        total = (total - count).coerceAtLeast(0)
        val left = (perHost[h] ?: 0) - count
        if (left <= 0) perHost.remove(h) else perHost[h] = left
    }

    @JvmStatic
    fun activeConnections(): Int = synchronized(lock) { total }

    internal fun resetForTest() = synchronized(lock) {
        total = 0; perHost.clear()
        globalCap = DownloadPrefs.GLOBAL_CONNECTION_CAP; hostCap = DownloadPrefs.PER_HOST_CONNECTION_CAP
    }
}
