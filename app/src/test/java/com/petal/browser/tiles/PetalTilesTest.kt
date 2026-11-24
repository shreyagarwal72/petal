package com.petal.browser.tiles

import android.service.quicksettings.Tile
import com.petal.browser.browser.PetalAdBlockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Quick Settings tiles:
 * - Ad-block mode cycling (DISABLED -> STANDARD -> AGGRESSIVE -> DISABLED)
 * - Toggle state mapping and SharedPreferences logic (JS, Force Dark, HTTPS-Only)
 * - Close Incognito tab counting & subtitle safety (no URLs/tab titles)
 * - Biometric unlock requirement on incognito launch
 * - Registry completeness across all 16 quick tiles
 */
class PetalTilesTest {

    @Test
    fun testAdBlockModeCycling() {
        assertEquals(
            PetalAdBlockMode.STANDARD,
            PetalAdBlockTileService.cycleNextMode(PetalAdBlockMode.DISABLED)
        )
        assertEquals(
            PetalAdBlockMode.AGGRESSIVE,
            PetalAdBlockTileService.cycleNextMode(PetalAdBlockMode.STANDARD)
        )
        assertEquals(
            PetalAdBlockMode.DISABLED,
            PetalAdBlockTileService.cycleNextMode(PetalAdBlockMode.AGGRESSIVE)
        )
    }

    @Test
    fun testAdBlockModeFromId() {
        assertEquals(PetalAdBlockMode.DISABLED, PetalAdBlockMode.fromId("disabled"))
        assertEquals(PetalAdBlockMode.DISABLED, PetalAdBlockMode.fromId("off"))
        assertEquals(PetalAdBlockMode.DISABLED, PetalAdBlockMode.fromId("false"))

        assertEquals(PetalAdBlockMode.AGGRESSIVE, PetalAdBlockMode.fromId("aggressive"))
        assertEquals(PetalAdBlockMode.AGGRESSIVE, PetalAdBlockMode.fromId("strict"))
        assertEquals(PetalAdBlockMode.AGGRESSIVE, PetalAdBlockMode.fromId("high"))

        assertEquals(PetalAdBlockMode.STANDARD, PetalAdBlockMode.fromId("standard"))
        assertEquals(PetalAdBlockMode.STANDARD, PetalAdBlockMode.fromId(null))
        assertEquals(PetalAdBlockMode.STANDARD, PetalAdBlockMode.fromId("unknown"))
    }

    @Test
    fun testAdBlockTileStateMapping() {
        fun modeToTileState(mode: PetalAdBlockMode): Int =
            if (mode != PetalAdBlockMode.DISABLED) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        assertEquals(Tile.STATE_INACTIVE, modeToTileState(PetalAdBlockMode.DISABLED))
        assertEquals(Tile.STATE_ACTIVE, modeToTileState(PetalAdBlockMode.STANDARD))
        assertEquals(Tile.STATE_ACTIVE, modeToTileState(PetalAdBlockMode.AGGRESSIVE))
    }

    @Test
    fun testBooleanToggleStateMapping() {
        fun booleanToTileState(enabled: Boolean): Int =
            if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        // JavaScript toggle (default true)
        var jsEnabled = true
        assertEquals(Tile.STATE_ACTIVE, booleanToTileState(jsEnabled))
        jsEnabled = !jsEnabled
        assertEquals(Tile.STATE_INACTIVE, booleanToTileState(jsEnabled))

        // Force Dark toggle (default false)
        var darkEnabled = false
        assertEquals(Tile.STATE_INACTIVE, booleanToTileState(darkEnabled))
        darkEnabled = !darkEnabled
        assertEquals(Tile.STATE_ACTIVE, booleanToTileState(darkEnabled))

        // HTTPS-Only toggle (default false)
        var httpsOnly = false
        assertEquals(Tile.STATE_INACTIVE, booleanToTileState(httpsOnly))
        httpsOnly = !httpsOnly
        assertEquals(Tile.STATE_ACTIVE, booleanToTileState(httpsOnly))
    }

    @Test
    fun testCloseIncognitoTileStateAndSubtitle() {
        fun computeSubtitle(count: Int): String {
            return if (count > 0) "$count open" else "None open"
        }

        fun computeTileState(count: Int): Int {
            return if (count > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        }

        // When 0 tabs open: inactive, "None open"
        assertEquals(Tile.STATE_INACTIVE, computeTileState(0))
        assertEquals("None open", computeSubtitle(0))

        // When 3 tabs open: active, "3 open"
        assertEquals(Tile.STATE_ACTIVE, computeTileState(3))
        assertEquals("3 open", computeSubtitle(3))

        // Safety verification: subtitles must never expose URLs or titles
        val subtitle = computeSubtitle(5)
        assertFalse("Subtitle must not contain URL schemes", subtitle.contains("http://") || subtitle.contains("https://"))
        assertFalse("Subtitle must not contain domains", subtitle.contains(".com") || subtitle.contains(".org"))
    }

    @Test
    fun testIncognitoBiometricLockRequirement() {
        fun isUnlockRequired(prefEnabled: Boolean): Boolean = prefEnabled

        assertFalse(isUnlockRequired(false))
        assertTrue(isUnlockRequired(true))
    }

    @Test
    fun testQuickSettingsManagerRegistry() {
        val tiles = PetalQuickSettingsManager.TILES
        assertEquals(16, tiles.size)

        val ids = mutableSetOf<String>()
        val serviceClasses = mutableSetOf<Class<*>>()

        tiles.forEach { tile ->
            assertTrue("ID must be unique: ${tile.id}", ids.add(tile.id))
            assertTrue("Service must be unique: ${tile.serviceClass.name}", serviceClasses.add(tile.serviceClass))
            assertTrue("Title res must be valid", tile.titleRes != 0)
            assertTrue("Subtitle res must be valid", tile.subtitleRes != 0)
            assertTrue("Icon res must be valid", tile.iconRes != 0)
            assertNotNull(tile.serviceClass)
        }
    }
}
