package com.petal.browser.now

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Public spatial anchor for the live Petal Now capsule in window coordinates.
 *
 * Modal sheets such as password save/update sheets query [computeMorphDelta] so their
 * handoff animation aims directly at the currently rendered capsule instead of relying
 * on stale hardcoded cutout coordinates.
 */
object PetalNowAnchor {

    var boundsInWindow: Rect? by mutableStateOf(null)
        private set

    var isVisible: Boolean by mutableStateOf(false)
        private set

    fun update(bounds: Rect, visible: Boolean) {
        boundsInWindow = if (visible && !bounds.isEmpty) bounds else null
        isVisible = visible && !bounds.isEmpty
    }

    fun clear() {
        boundsInWindow = null
        isVisible = false
    }

    /**
     * Computes the translation delta from [sheetRestingBounds] to the live capsule center.
     * Returns `null` when Petal Now is not currently visible or bounds are unavailable so callers
     * can gracefully fall back to a plain dismiss transition.
     */
    fun computeMorphDelta(sheetRestingBounds: Rect?): Offset? {
        val target = boundsInWindow
        if (!isVisible || target == null || target.isEmpty || sheetRestingBounds == null || sheetRestingBounds.isEmpty) {
            return null
        }
        val targetCenter = target.center
        val sheetCenter = sheetRestingBounds.center
        return Offset(
            x = targetCenter.x - sheetCenter.x,
            y = targetCenter.y - sheetCenter.y,
        )
    }
}
