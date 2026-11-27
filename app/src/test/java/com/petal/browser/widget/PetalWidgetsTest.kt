package com.petal.browser.widget

import android.graphics.Color
import com.petal.browser.widget.glance.PETAL_RADIUS_FULL
import com.petal.browser.widget.glance.PetalWidgetAction
import com.petal.browser.widget.glance.PetalWidgetConfig
import com.petal.browser.widget.glance.PetalWidgetKind
import com.petal.browser.widget.glance.PetalWidgetShape
import com.petal.browser.widget.glance.planLayout
import com.petal.browser.widget.liquidglass.PetalBlurConfig
import com.petal.browser.widget.liquidglass.PetalWallpaperBlurHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for Petal home-screen widgets:
 * - Glance layout sizing, priority slot dropping, and shape/action consistency
 * - Custom color sync & fallback action mappings
 * - Liquid Glass config normalization, tint/edge calculations, ink contrast, and fallback bounds
 */
class PetalWidgetsTest {

    @Test
    fun lensActionUsesCanonicalActionConstant() {
        assertEquals(
            PetalSearchWidgetProvider.ACTION_OPEN_LENS,
            PetalWidgetAction.LENS.intentAction
        )
        assertFalse(PetalWidgetAction.pickable.contains(PetalWidgetAction.SEARCH))
        assertTrue(PetalWidgetAction.pickable.contains(PetalWidgetAction.NONE))
    }

    @Test
    fun widgetConfigDefaultsAndSyncColors() {
        val petal1 = PetalWidgetConfig.defaults(PetalWidgetKind.PETAL1)
        assertEquals(3, petal1.barActions.size)
        assertEquals(4, petal1.tiles.size)
        assertTrue(petal1.showBadge)

        val petal2 = PetalWidgetConfig.defaults(PetalWidgetKind.PETAL2)
        assertFalse(petal2.showBadge)
        assertEquals(0, petal2.tiles.size)

        val customColor = Color.MAGENTA
        val synced = petal1.copy(syncColors = true, syncColor = customColor)
        assertEquals(customColor, synced.barColor(0))
        assertEquals(customColor, synced.tileColor(2))

        val perSlot = petal1.copy(
            syncColors = false,
            barColors = listOf(Color.RED, 0, Color.BLUE, 0)
        )
        assertEquals(Color.RED, perSlot.barColor(0))
        assertEquals(0, perSlot.barColor(1))
    }

    @Test
    fun layoutPlannerHandlesCompactExpandedAndNarrowSizes() {
        val cfg = PetalWidgetConfig.defaults(PetalWidgetKind.PETAL1).copy(cornerRadius = 24)

        val compact = planLayout(cfg, widthDp = 320f, heightDp = 56f)
        assertFalse(compact.expanded)
        assertTrue(compact.tiles.isEmpty())
        assertTrue(compact.showBadge)
        assertTrue(compact.hintWidthDp >= 48)
        assertEquals(24, compact.radius)

        val expanded = planLayout(cfg, widthDp = 340f, heightDp = 140f)
        assertTrue(expanded.expanded)
        assertEquals(4, expanded.tiles.size)
        assertTrue(expanded.showTileLabels)
        assertEquals(16, expanded.fieldRadius)

        val narrow = planLayout(cfg, widthDp = 110f, heightDp = 52f)
        assertTrue(narrow.hintWidthDp >= 48)
        assertTrue(narrow.buttons.size < cfg.barActions.size)
    }

    @Test
    fun shapeLookupFallsBackCleanly() {
        assertEquals(PetalWidgetShape.COOKIE_12, PetalWidgetShape.fromId("cookie12", PetalWidgetShape.CIRCLE))
        assertEquals(PetalWidgetShape.CIRCLE, PetalWidgetShape.fromId("unknown_shape", PetalWidgetShape.CIRCLE))
    }

    @Test
    fun liquidGlassConfigNormalizeAndColors() {
        assertTrue(PetalBlurConfig.DEFAULT_EDGE_ALPHA in 0..100)

        val normalized = PetalBlurConfig.normalize(
            PetalBlurConfig(
                blurRadius = 99f,
                refractionHeight = -5f,
                refractionOffset = 400f,
                cornerRadius = 200f,
                edgeAlpha = 180,
                darkLightMode = 9,
                darkLightIntensity = -10,
                tintAlpha = 999
            )
        )
        assertEquals(50f, normalized.blurRadius, 0.001f)
        assertEquals(1f, normalized.refractionHeight, 0.001f)
        assertEquals(150f, normalized.refractionOffset, 0.001f)
        assertEquals(60f, normalized.cornerRadius, 0.001f)
        assertEquals(100, normalized.edgeAlpha)
        assertEquals(PetalBlurConfig.DARK_LIGHT_LIGHT, normalized.darkLightMode)
        assertEquals(0, normalized.darkLightIntensity)
        assertEquals(255, normalized.tintAlpha)

        val dark = PetalBlurConfig(darkLightMode = PetalBlurConfig.DARK_LIGHT_DARK, darkLightIntensity = 50)
        val darkColor = dark.getDarkLightColor()
        assertEquals(0, darkColor and 0x00FFFFFF)
        assertTrue((darkColor ushr 24) in 126..128)

        val off = PetalBlurConfig(darkLightMode = PetalBlurConfig.DARK_LIGHT_OFF, darkLightIntensity = 50)
        assertEquals(0, off.getDarkLightColor())
    }

    @Test
    fun liquidGlassInkContrastAndFallbackRect() {
        val auto = PetalBlurConfig(darkLightMode = PetalBlurConfig.DARK_LIGHT_OFF)
        assertEquals(0xFFFFFFFF.toInt(), PetalWallpaperBlurHelper.inkFor(auto, 0.20f))
        assertEquals(0xE6101418.toInt(), PetalWallpaperBlurHelper.inkFor(auto, 0.85f))

        val forcedLight = PetalBlurConfig(darkLightMode = PetalBlurConfig.DARK_LIGHT_LIGHT)
        assertEquals(0xE6101418.toInt(), PetalWallpaperBlurHelper.inkFor(forcedLight, 0.1f))

        val forcedDark = PetalBlurConfig(darkLightMode = PetalBlurConfig.DARK_LIGHT_DARK)
        assertEquals(0xFFFFFFFF.toInt(), PetalWallpaperBlurHelper.inkFor(forcedDark, 0.9f))

        val rect = PetalWallpaperBlurHelper.computeFallbackRect(
            screenW = 1080,
            screenH = 2400,
            density = 3f,
            minWidthDp = 280,
            minHeightDp = 56,
            isPill = false
        )
        assertNotNull(rect)
        assertTrue(rect!!.left >= 0 && rect.top >= 0)
        assertTrue(rect.right <= 1080 && rect.bottom <= 2400)
        assertEquals(840, rect.width())
        assertEquals(168, rect.height())

        assertNull(PetalWallpaperBlurHelper.computeFallbackRect(0, 2400, 3f, 200, 48, true))
    }
}
