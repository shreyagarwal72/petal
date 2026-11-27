package com.petal.browser.ui.blur

import com.petal.browser.ui.glass.RefractionTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for live blur & Liquid Glass configuration/math:
 * - Behind-window blur method resolution (Auto / System / In-app)
 * - Radius & dim bounds clamping
 * - Refraction tuning enablement thresholds
 */
class PetalLiveBlurTest {

    @Test
    fun resolveBlurMethodHandlesOsAvailability() {
        assertEquals(PetalBlurMode.SYSTEM, PetalBlurPrefs.resolveMode(PetalBlurMethod.AUTO, osAvailable = true))
        assertEquals(PetalBlurMode.IN_APP, PetalBlurPrefs.resolveMode(PetalBlurMethod.AUTO, osAvailable = false))

        assertEquals(PetalBlurMode.SYSTEM, PetalBlurPrefs.resolveMode(PetalBlurMethod.SYSTEM, osAvailable = true))
        assertEquals(PetalBlurMode.NONE, PetalBlurPrefs.resolveMode(PetalBlurMethod.SYSTEM, osAvailable = false))

        assertEquals(PetalBlurMode.IN_APP, PetalBlurPrefs.resolveMode(PetalBlurMethod.IN_APP, osAvailable = true))
        assertEquals(PetalBlurMode.IN_APP, PetalBlurPrefs.resolveMode(PetalBlurMethod.IN_APP, osAvailable = false))
    }

    @Test
    fun radiusPxClampsSafelyForHighDensity() {
        assertEquals(0, PetalBlurPrefs.radiusPx(-4f, 3f))
        assertEquals(72, PetalBlurPrefs.radiusPx(24f, 3f))
        // Even max strength (60dp) at 4x density must respect MAX_RADIUS_PX (200px)
        assertEquals(200, PetalBlurPrefs.radiusPx(PetalBlurPrefs.MAX_STRENGTH_DP, 4f))
    }

    @Test
    fun blurMethodParsingFallsBackToAuto() {
        assertEquals(PetalBlurMethod.AUTO, PetalBlurMethod.fromId(null))
        assertEquals(PetalBlurMethod.AUTO, PetalBlurMethod.fromId("unknown"))
        assertEquals(PetalBlurMethod.SYSTEM, PetalBlurMethod.fromId("system"))
        assertEquals(PetalBlurMethod.IN_APP, PetalBlurMethod.fromId("inapp"))
    }

    @Test
    fun refractionTuningEnabledThresholds() {
        assertTrue(RefractionTuning(1f, 1f).enabled)
        assertFalse(RefractionTuning(0f, 1f).enabled)
        assertFalse(RefractionTuning(1f, 0f).enabled)
        assertFalse(RefractionTuning(0.0005f, 1f).enabled)
    }

    @Test
    fun blurStateActiveDependsOnBothEnabledAndMode() {
        assertTrue(PetalBlurState(enabled = true, mode = PetalBlurMode.IN_APP, strengthDp = 24f, dim = 0.12f).active)
        assertFalse(PetalBlurState(enabled = false, mode = PetalBlurMode.IN_APP, strengthDp = 24f, dim = 0.12f).active)
        assertFalse(PetalBlurState(enabled = true, mode = PetalBlurMode.NONE, strengthDp = 24f, dim = 0.12f).active)
    }
}
