/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */
package com.petal.browser.ui.components

import android.os.SystemClock
import androidx.compose.ui.geometry.Rect

/**
 * Remembers the on-screen bounds of the "+" / new tab button or tab-count button,
 * so the new page grows out of it (container morph) and collapses back into it
 * when closing the last tab or going back.
 */
object PetalNewTabOrigin {
    private const val MAX_AGE_MS = 2500L

    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f
    private var radiusPx = -1f
    private var markedAt = 0L
    private var valid = false

    /**
     * Records origin coordinates and corner radius.
     * [boundsInWindow] is the button's bounds in window coordinates; [radiusPx] < 0 means half-height (fully rounded).
     */
    @JvmStatic
    fun mark(boundsInWindow: Rect?, radiusPx: Float = -1f) {
        if (boundsInWindow == null || boundsInWindow.isEmpty) return
        left = boundsInWindow.left
        top = boundsInWindow.top
        right = boundsInWindow.right
        bottom = boundsInWindow.bottom
        this.radiusPx = if (radiusPx < 0f) boundsInWindow.height / 2f else radiusPx
        markedAt = SystemClock.uptimeMillis()
        valid = true
    }

    /** Returns current bounds without consuming if valid. */
    @JvmStatic
    fun peek(): Rect? {
        val ok = valid && SystemClock.uptimeMillis() - markedAt <= MAX_AGE_MS
        return if (ok) Rect(left, top, right, bottom) else null
    }

    @JvmStatic
    fun getRadius(): Float = radiusPx

    /**
     * Returns [left, top, right, bottom, radiusPx] in window coordinates, or null. Clears the mark.
     */
    @JvmStatic
    fun take(): FloatArray? {
        val ok = valid && SystemClock.uptimeMillis() - markedAt <= MAX_AGE_MS
        valid = false
        return if (ok) floatArrayOf(left, top, right, bottom, radiusPx) else null
    }

    @JvmStatic
    fun clear() {
        valid = false
    }
}
