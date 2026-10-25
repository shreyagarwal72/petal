/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.predictive

/**
 * Bridge between a Compose predictive-back gesture (PetalPredictiveBackSurface) and the
 * host Activity that owns the real, live view hierarchy behind the overlay.
 *
 * Instead of sliding a PixelCopy screenshot of the browser behind the dismissing screen,
 * the host re-reveals the real tab surface under the overlay for the duration of the
 * gesture and drives its parallax / blur / dim from the same progress value.
 *
 * If no host is attached, the feature flag is off, or the surface behind the overlay is
 * not a retained live surface, [begin] returns false and the legacy snapshot underlay is used.
 */
object PetalLiveUnderlay {
    interface Host {
        fun hasLiveUnderlay(): Boolean
        fun onGestureStart(swipeEdge: Int)
        fun onGestureProgress(progress: Float, swipeEdge: Int)
        fun onGestureEnd(committed: Boolean)
    }

    @Volatile
    private var host: Host? = null

    /** Latest behind-layer pose from the active style: scale, tx, ty, dim alpha, blur dp. */
    @Volatile
    private var behind: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f)

    @JvmStatic
    fun publish(values: FloatArray) { behind = values }

    @JvmStatic
    fun behindValues(): FloatArray = behind

    @JvmStatic
    fun attach(h: Host) { host = h }

    @JvmStatic
    fun detach(h: Host) { if (host === h) host = null }

    /** @return true when a live surface took over the underlay for this gesture. */
    fun begin(swipeEdge: Int): Boolean {
        val h = host ?: return false
        if (!PetalPredictiveJunction.isLiveUnderlayEnabled.value) return false
        behind = floatArrayOf(1f, 0f, 0f, 0f, 0f)
        return try {
            if (!h.hasLiveUnderlay()) false else { h.onGestureStart(swipeEdge); true }
        } catch (t: Throwable) { false }
    }

    fun progress(progress: Float, swipeEdge: Int) {
        try { host?.onGestureProgress(progress, swipeEdge) } catch (_: Throwable) {}
    }

    fun end(committed: Boolean) {
        try { host?.onGestureEnd(committed) } catch (_: Throwable) {}
    }
}
