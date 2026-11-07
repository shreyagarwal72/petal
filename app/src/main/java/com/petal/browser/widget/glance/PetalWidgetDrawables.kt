package com.petal.browser.widget.glance

import androidx.annotation.DrawableRes
import com.petal.browser.R

/**
 * Tintable shape drawables for the widget bars. RemoteViews can't set a corner radius, and Glance's
 * `cornerRadius` modifier only works on Android 12+, so the bar is drawn from these (white, tinted at draw time),
 * which behaves the same on every API level and every launcher.
 *
 * Generated list: radii 0..40dp in steps of 4, plus "full" (a pill at any height), each with outline rings of 1..4dp.
 */
internal object PetalWidgetDrawables {

    private val RADII = intArrayOf(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 100)

    private val FILLS = intArrayOf(
        R.drawable.bg_petal_bar_r0,
        R.drawable.bg_petal_bar_r4,
        R.drawable.bg_petal_bar_r8,
        R.drawable.bg_petal_bar_r12,
        R.drawable.bg_petal_bar_r16,
        R.drawable.bg_petal_bar_r20,
        R.drawable.bg_petal_bar_r24,
        R.drawable.bg_petal_bar_r28,
        R.drawable.bg_petal_bar_r32,
        R.drawable.bg_petal_bar_r36,
        R.drawable.bg_petal_bar_r40,
        R.drawable.bg_petal_bar_rfull
    )

    private val RINGS = arrayOf(
        intArrayOf(R.drawable.bg_petal_ring_r0_s1, R.drawable.bg_petal_ring_r0_s2, R.drawable.bg_petal_ring_r0_s3, R.drawable.bg_petal_ring_r0_s4),
        intArrayOf(R.drawable.bg_petal_ring_r4_s1, R.drawable.bg_petal_ring_r4_s2, R.drawable.bg_petal_ring_r4_s3, R.drawable.bg_petal_ring_r4_s4),
        intArrayOf(R.drawable.bg_petal_ring_r8_s1, R.drawable.bg_petal_ring_r8_s2, R.drawable.bg_petal_ring_r8_s3, R.drawable.bg_petal_ring_r8_s4),
        intArrayOf(R.drawable.bg_petal_ring_r12_s1, R.drawable.bg_petal_ring_r12_s2, R.drawable.bg_petal_ring_r12_s3, R.drawable.bg_petal_ring_r12_s4),
        intArrayOf(R.drawable.bg_petal_ring_r16_s1, R.drawable.bg_petal_ring_r16_s2, R.drawable.bg_petal_ring_r16_s3, R.drawable.bg_petal_ring_r16_s4),
        intArrayOf(R.drawable.bg_petal_ring_r20_s1, R.drawable.bg_petal_ring_r20_s2, R.drawable.bg_petal_ring_r20_s3, R.drawable.bg_petal_ring_r20_s4),
        intArrayOf(R.drawable.bg_petal_ring_r24_s1, R.drawable.bg_petal_ring_r24_s2, R.drawable.bg_petal_ring_r24_s3, R.drawable.bg_petal_ring_r24_s4),
        intArrayOf(R.drawable.bg_petal_ring_r28_s1, R.drawable.bg_petal_ring_r28_s2, R.drawable.bg_petal_ring_r28_s3, R.drawable.bg_petal_ring_r28_s4),
        intArrayOf(R.drawable.bg_petal_ring_r32_s1, R.drawable.bg_petal_ring_r32_s2, R.drawable.bg_petal_ring_r32_s3, R.drawable.bg_petal_ring_r32_s4),
        intArrayOf(R.drawable.bg_petal_ring_r36_s1, R.drawable.bg_petal_ring_r36_s2, R.drawable.bg_petal_ring_r36_s3, R.drawable.bg_petal_ring_r36_s4),
        intArrayOf(R.drawable.bg_petal_ring_r40_s1, R.drawable.bg_petal_ring_r40_s2, R.drawable.bg_petal_ring_r40_s3, R.drawable.bg_petal_ring_r40_s4),
        intArrayOf(R.drawable.bg_petal_ring_rfull_s1, R.drawable.bg_petal_ring_rfull_s2, R.drawable.bg_petal_ring_rfull_s3, R.drawable.bg_petal_ring_rfull_s4)
    )

    private fun indexOf(radiusDp: Int): Int {
        var best = 0
        for (i in RADII.indices) if (kotlin.math.abs(RADII[i] - radiusDp) < kotlin.math.abs(RADII[best] - radiusDp)) best = i
        return best
    }

    @DrawableRes
    fun fill(radiusDp: Int): Int = FILLS[indexOf(radiusDp)]

    /** An outline ring; [strokeDp] is clamped to 1..4. */
    @DrawableRes
    fun ring(radiusDp: Int, strokeDp: Int): Int = RINGS[indexOf(radiusDp)][strokeDp.coerceIn(1, 4) - 1]
}
