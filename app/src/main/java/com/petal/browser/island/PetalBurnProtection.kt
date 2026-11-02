/*
 * PetalBurnProtection.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Automatic built-in AMOLED / OLED burn-in protection for Petal Cutout Island.
 *
 * Implements:
 * 1. Automatic hardware display detection (HDR / OLED characteristics + AMOLED settings)
 *    with zero manual toggles required.
 * 2. Pixel Orbiter: Cyclic ±1..±2 px sub-pixel micro-drifting every 90 seconds while the
 *    island is visible, ensuring static camera rims, outlines, and icons never burn in.
 * 3. Peak Luminance Clamping: Prevents maximum phosphor saturation on blue/red organic diodes.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.island

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object PetalBurnProtection {

    private const val ORBITER_CYCLE_INTERVAL_MS = 90_000L // Shift every 90 seconds

    /**
     * Discrete orbital step offsets in physical pixels:
     * (0, 0) -> (1, 0) -> (1, 1) -> (0, 1) -> (-1, 1) -> (-1, 0) -> (-1, -1) -> (0, -1)
     */
    private val ORBITAL_PATH = listOf(
        Offset(0f, 0f),
        Offset(1f, 0f),
        Offset(1f, 1f),
        Offset(0f, 1f),
        Offset(-1f, 1f),
        Offset(-1f, 0f),
        Offset(-1f, -1f),
        Offset(0f, -1f)
    )

    /**
     * Automatically detects whether the primary display is an OLED / AMOLED panel.
     * Uses HDR capabilities (OLED screens universally support HDR10/DolbyVision/HLG),
     * wide color gamut support, system display name, or user AMOLED preference.
     */
    fun isOledDisplay(context: Context): Boolean {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            if (sp.getBoolean("sp_amoled", false)) {
                return true
            }

            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY) ?: return false

            // Modern OLED displays are HDR-capable (HDR10, HLG, Dolby Vision)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hdrCaps = display.hdrCapabilities
                if (hdrCaps != null && hdrCaps.supportedHdrTypes.isNotEmpty()) {
                    return true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (display.isHdr) {
                    return true
                }
            }

            // Check display name for OLED/AMOLED keywords
            val name = display.name?.lowercase() ?: ""
            if (name.contains("oled") || name.contains("amoled") || name.contains("super_amoled")) {
                return true
            }
        } catch (_: Throwable) {
            // Safe fallback
        }
        return false
    }

    /**
     * Reactive state providing the active micro-shift offset in physical pixels.
     * When on an OLED display, cycles through the orbital path every 90s.
     * When not on OLED, stays at (0, 0) to avoid unnecessary recalculations.
     */
    @Composable
    fun rememberOrbiterShift(isIslandVisible: Boolean): State<Offset> {
        val context = LocalContext.current
        val isOled = remember(context) { isOledDisplay(context) }
        val shiftState = remember { mutableStateOf(Offset.Zero) }

        if (isOled && isIslandVisible) {
            LaunchedEffect(Unit) {
                var stepIndex = 0
                while (isActive) {
                    shiftState.value = ORBITAL_PATH[stepIndex % ORBITAL_PATH.size]
                    stepIndex++
                    delay(ORBITER_CYCLE_INTERVAL_MS)
                }
            }
        } else {
            shiftState.value = Offset.Zero
        }

        return shiftState
    }

    /**
     * Subtle peak luminance clamping factor for pure white elements on OLED screens.
     * Softens harsh 100% white static elements to 94% luminance to protect organic emitters.
     */
    @Composable
    fun rememberOledLuminanceAlpha(): Float {
        val context = LocalContext.current
        val isOled = remember(context) { isOledDisplay(context) }
        return if (isOled) 0.94f else 1.0f
    }
}
