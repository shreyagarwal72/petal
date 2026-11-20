package com.petal.browser.ui.components

import com.petal.browser.ui.theme.layout.PetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalPullRefreshStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class PetalPullRefreshStyleTest {

    @Test
    fun testFromKeyValid() {
        assertEquals(PetalPullRefreshStyle.CURRENT, PetalPullRefreshStyle.fromKey("CURRENT"))
        assertEquals(PetalPullRefreshStyle.CURRENT, PetalPullRefreshStyle.fromKey("current"))
        assertEquals(PetalPullRefreshStyle.OVERCAST_MORPH, PetalPullRefreshStyle.fromKey("OVERCAST_MORPH"))
        assertEquals(PetalPullRefreshStyle.FIREFOX, PetalPullRefreshStyle.fromKey("firefox"))
        assertEquals(PetalPullRefreshStyle.WAVY_ARC, PetalPullRefreshStyle.fromKey("WAVY_ARC"))
        assertEquals(PetalPullRefreshStyle.MORPH_LADDER, PetalPullRefreshStyle.fromKey("morph_ladder"))
        assertEquals(PetalPullRefreshStyle.LIQUID_BRIDGE, PetalPullRefreshStyle.fromKey("LIQUID_BRIDGE"))
    }

    @Test
    fun testFromKeyFallback() {
        assertEquals(PetalPullRefreshStyle.CURRENT, PetalPullRefreshStyle.fromKey(null))
        assertEquals(PetalPullRefreshStyle.CURRENT, PetalPullRefreshStyle.fromKey(""))
        assertEquals(PetalPullRefreshStyle.CURRENT, PetalPullRefreshStyle.fromKey("NON_EXISTENT_STYLE"))
    }

    @Test
    fun testAllStylesHaveTitlesAndSubtitles() {
        for (style in PetalPullRefreshStyle.entries) {
            org.junit.Assert.assertTrue(style.title.isNotBlank())
            org.junit.Assert.assertTrue(style.subtitle.isNotBlank())
            org.junit.Assert.assertTrue(style.key.isNotBlank())
        }
    }

    @Test
    fun testPreferenceKeyMatchesSpec() {
        assertEquals("sp_pull_refresh_style", PetalLayoutSettings.KEY_PULL_REFRESH_STYLE)
    }
}
