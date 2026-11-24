package com.petal.browser.scanner

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global mutex guarding against opening multiple camera scanner instances concurrently.
 */
object ScannerGuard {

    private val isScannerActive = AtomicBoolean(false)

    /**
     * Atomically acquires the scanner lock.
     * @return true if acquired successfully, false if another scanner is already active.
     */
    fun tryAcquire(): Boolean {
        return isScannerActive.compareAndSet(false, true)
    }

    /**
     * Releases the active scanner lock.
     */
    fun release() {
        isScannerActive.set(false)
    }

    /**
     * Checks whether a scanner instance is currently active.
     */
    fun isActive(): Boolean {
        return isScannerActive.get()
    }
}
