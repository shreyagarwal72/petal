/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */
package com.petal.browser.ui.components

import android.os.SystemClock
import androidx.compose.ui.geometry.Rect

/**
 * Remembers where the bar the user just tapped sits on screen, so the full-screen omnibox page
 * can grow out of it (container transform) and collapse back into it.
 *
 * Compose bars call [mark] in their click handler; MainActivity.showOmniboxPage() calls
 * [take] once. A mark older than [MAX_AGE_MS] is ignored so a stale position is never reused.
 */
object PetalOmniboxOrigin {
    private const val MAX_AGE_MS = 1500L

    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f
    private var radiusPx = -1f
    private var markedAt = 0L
    private var valid = false

    /** [boundsInWindow] is the bar's bounds in window coordinates; [radiusPx] < 0 means fully round. */
    @JvmStatic
    fun mark(boundsInWindow: Rect?, radiusPx: Float = -1f) {
        if (boundsInWindow == null || boundsInWindow.isEmpty) return
        left = boundsInWindow.left
        top = boundsInWindow.top
        right = boundsInWindow.right
        bottom = boundsInWindow.bottom
        this.radiusPx = radiusPx
        markedAt = SystemClock.uptimeMillis()
        valid = true
    }

    /** Returns [left, top, right, bottom, radiusPx] in window coordinates, or null. Clears the mark. */
    @JvmStatic
    fun take(): FloatArray? {
        val ok = valid && SystemClock.uptimeMillis() - markedAt <= MAX_AGE_MS
        valid = false
        return if (ok) floatArrayOf(left, top, right, bottom, radiusPx) else null
    }
}
