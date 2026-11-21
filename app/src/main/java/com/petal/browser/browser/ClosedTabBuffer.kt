package com.petal.browser.browser

/**
 * Bounded, time-limited buffer of just-closed tab snapshots keyed by tab id (the pure part of
 * Undo Close). [take] hands a snapshot back exactly once.
 */
class ClosedTabBuffer<T>(private val max: Int, private val ttlMs: Long) {
    private class Entry<T>(val value: T, val atMs: Long)
    private val map = LinkedHashMap<String, Entry<T>>()

    @Synchronized
    fun put(id: String, value: T, nowMs: Long) {
        map.remove(id)
        map[id] = Entry(value, nowMs)
        val cutoff = nowMs - ttlMs
        val it = map.entries.iterator()
        while (it.hasNext()) { if (it.next().value.atMs < cutoff) it.remove() }
        while (map.size > max) {
            val oldest = map.entries.iterator()
            oldest.next(); oldest.remove()
        }
    }

    @Synchronized
    fun take(id: String): T? = map.remove(id)?.value

    @Synchronized
    fun size(): Int = map.size
}
