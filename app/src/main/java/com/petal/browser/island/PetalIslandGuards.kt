package com.petal.browser.island

/**
 * Per-key cooldown. [tryPass] returns true (and starts the cooldown) the first time a key is seen,
 * then false until [cooldownMs] has passed. Pure Kotlin with an injectable clock so it is unit tested.
 */
class PetalCooldownGate(
    private val cooldownMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun tryPass(key: String): Boolean {
        val now = clock()
        val prev = last[key]
        if (prev != null && now - prev < cooldownMs) return false
        last[key] = now
        return true
    }
}

/** Decides whether a one-time card (e.g. the Liquid Glass intro) may be shown right now. */
object PetalOneTimeGuard {
    /** Never twice, and never during app startup or a backup restore (the quiet period). */
    fun shouldShow(alreadySeen: Boolean, quiet: Boolean): Boolean = !alreadySeen && !quiet
}
