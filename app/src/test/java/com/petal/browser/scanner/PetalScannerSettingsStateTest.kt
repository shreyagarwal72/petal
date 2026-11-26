package com.petal.browser.scanner

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetalScannerSettingsStateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun `state holder reads default scanner settings`() {
        val holder = PetalScannerSettingsStateHolder(context)
        try {
            holder.start()
            val state = holder.state.value
            assertFalse(state.autoOpen)
            assertTrue(state.vibrate)
            assertFalse(state.beep)
            assertTrue(state.historyEnabled)
            assertTrue(state.safetyWarnings)
        } finally {
            holder.dispose()
        }
    }

    @Test
    fun `state holder updates both SharedPreferences and UI state`() {
        val holder = PetalScannerSettingsStateHolder(context)
        try {
            holder.start()
            holder.setAutoOpenEnabled(true)
            holder.setVibrateEnabled(false)
            holder.setBeepEnabled(true)
            holder.setSafetyWarningsEnabled(false)
            holder.setHistoryEnabled(false)

            val state = holder.state.value
            assertTrue(state.autoOpen)
            assertFalse(state.vibrate)
            assertTrue(state.beep)
            assertFalse(state.safetyWarnings)
            assertFalse(state.historyEnabled)

            assertTrue(PetalScannerSettings.isAutoOpenEnabled(context))
            assertFalse(PetalScannerSettings.isVibrateEnabled(context))
            assertTrue(PetalScannerSettings.isBeepEnabled(context))
            assertFalse(PetalScannerSettings.isSafetyWarningsEnabled(context))
            assertFalse(PetalScannerSettings.isHistoryEnabled(context))
        } finally {
            holder.dispose()
        }
    }

    @Test
    fun `state holder reacts to external PetalScannerSettings mutations`() {
        val holder = PetalScannerSettingsStateHolder(context)
        try {
            holder.start()
            PetalScannerSettings.setAutoOpenEnabled(context, true)
            PetalScannerSettings.setSafetyWarningsEnabled(context, false)

            assertEquals(true, holder.state.value.autoOpen)
            assertEquals(false, holder.state.value.safetyWarnings)
        } finally {
            holder.dispose()
        }
    }
}
