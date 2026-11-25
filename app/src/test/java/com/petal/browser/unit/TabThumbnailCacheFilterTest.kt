package com.petal.browser.unit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabThumbnailCacheFilterTest {

    @Test
    fun rejectsTransparentFrameEvenWhenAllowUniformIsTrue() {
        val pixels = IntArray(384) { 0x00000000 }
        assertTrue(TabThumbnailCache.isNearUniformOrDarkSamples(pixels, allowUniform = true))
        assertTrue(TabThumbnailCache.isNearUniformOrDarkSamples(pixels, allowUniform = false))
    }

    @Test
    fun rejectsDarkDeadFrameEvenWhenAllowUniformIsTrue() {
        val pixels = IntArray(384) { 0xFF040404.toInt() }
        assertTrue(TabThumbnailCache.isNearUniformOrDarkSamples(pixels, allowUniform = true))
        assertTrue(TabThumbnailCache.isNearUniformOrDarkSamples(pixels, allowUniform = false))
    }

    @Test
    fun rejectsNearUniformFrameForWebTabsButAllowsDiverseContent() {
        val nearUniform = IntArray(384) { idx -> if (idx < 3) 0xFF111111.toInt() else 0xFFFFFFFF.toInt() }
        assertTrue(TabThumbnailCache.isNearUniformOrDarkSamples(nearUniform, allowUniform = false))

        val richPage = IntArray(384) { idx ->
            when {
                idx % 4 == 0 -> 0xFF202124.toInt()
                idx % 3 == 0 -> 0xFFE8EAED.toInt()
                else -> 0xFF4285F4.toInt()
            }
        }
        assertFalse(TabThumbnailCache.isNearUniformOrDarkSamples(richPage, allowUniform = false))
        assertFalse(TabThumbnailCache.isNearUniformOrDarkSamples(richPage, allowUniform = true))
    }
}
