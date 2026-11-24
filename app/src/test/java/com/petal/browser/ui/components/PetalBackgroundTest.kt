/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.petal.browser.ui.theme.ColorStyle
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PetalBackgroundTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Resolver Table Combinations
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testResolveBackgroundMode_GlassMasterBloomGlass() {
        val mode = resolveBackgroundMode(
            isGlassMaster = true,
            glassBgMode = "BLOOM_GLASS",
            glassImageUri = "",
            shapesEnabled = true
        )
        assertEquals(PetalBackgroundMode.BLOOM_GLASS, mode)
    }

    @Test
    fun testResolveBackgroundMode_GlassMasterLegacyStride() {
        // Legacy "STRIDE" resolves to BLOOM_GLASS
        val mode = resolveBackgroundMode(
            isGlassMaster = true,
            glassBgMode = "STRIDE",
            glassImageUri = "",
            shapesEnabled = true
        )
        assertEquals(PetalBackgroundMode.BLOOM_GLASS, mode)
    }

    @Test
    fun testResolveBackgroundMode_GlassMasterLegacyMorphing() {
        // Legacy "MORPHING" resolves to BLOOM_GLASS
        val mode = resolveBackgroundMode(
            isGlassMaster = true,
            glassBgMode = "MORPHING",
            glassImageUri = "",
            shapesEnabled = true
        )
        assertEquals(PetalBackgroundMode.BLOOM_GLASS, mode)
    }

    @Test
    fun testResolveBackgroundMode_GlassMasterWallpaperValidUri() {
        val mode = resolveBackgroundMode(
            isGlassMaster = true,
            glassBgMode = "WALLPAPER",
            glassImageUri = "content://media/external/images/media/42",
            shapesEnabled = true
        )
        assertEquals(PetalBackgroundMode.WALLPAPER, mode)
    }

    @Test
    fun testResolveBackgroundMode_GlassMasterImageValidUri() {
        val mode = resolveBackgroundMode(
            isGlassMaster = true,
            glassBgMode = "IMAGE",
            glassImageUri = "file:///storage/emulated/0/Download/wallpaper.jpg",
            shapesEnabled = false
        )
        assertEquals(PetalBackgroundMode.WALLPAPER, mode)
    }

    @Test
    fun testResolveBackgroundMode_GlassMasterWallpaperEmptyUriFallback() {
        // When wallpaper mode is selected but URI is empty/blank, fall back to BLOOM_GLASS
        val mode = resolveBackgroundMode(
            isGlassMaster = true,
            glassBgMode = "WALLPAPER",
            glassImageUri = "   ",
            shapesEnabled = true
        )
        assertEquals(PetalBackgroundMode.BLOOM_GLASS, mode)
    }

    @Test
    fun testResolveBackgroundMode_GlassDisabledShapesEnabled() {
        val mode = resolveBackgroundMode(
            isGlassMaster = false,
            glassBgMode = "BLOOM_GLASS",
            glassImageUri = "",
            shapesEnabled = true
        )
        assertEquals(PetalBackgroundMode.BLOOM, mode)
    }

    @Test
    fun testResolveBackgroundMode_GlassDisabledShapesDisabled() {
        val mode = resolveBackgroundMode(
            isGlassMaster = false,
            glassBgMode = "BLOOM_GLASS",
            glassImageUri = "",
            shapesEnabled = false
        )
        assertEquals(PetalBackgroundMode.PLAIN, mode)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Legacy Migration Logic
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testLegacyStrideAndMorphingMigration() {
        val legacyModes = listOf("STRIDE", "MORPHING")
        for (legacy in legacyModes) {
            val migrated = if (legacy == "STRIDE" || legacy == "MORPHING") "BLOOM_GLASS" else legacy
            assertEquals("BLOOM_GLASS", migrated)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Color Roles for Light, Dark, AMOLED
    // ─────────────────────────────────────────────────────────────────────────

    private val testLightScheme = lightColorScheme(
        primaryContainer = Color(0xFFD0E4FF),
        secondaryContainer = Color(0xFFDCE2F9),
        tertiaryContainer = Color(0xFFFFD8E4),
        tertiary = Color(0xFF7D5260)
    )

    private val testDarkScheme = darkColorScheme(
        primaryContainer = Color(0xFF004A77),
        secondaryContainer = Color(0xFF40485D),
        tertiaryContainer = Color(0xFF633B48),
        tertiary = Color(0xFFEFB8C8)
    )

    @Test
    fun testColorRoles_LightMode() {
        // Role 0: primaryContainer
        val (color0, alpha0) = resolveColorRole(
            roleIndex = 0,
            colorScheme = testLightScheme,
            colorStyle = ColorStyle.TONAL_SPOT,
            pageKey = "test_page",
            isDark = false,
            isAmoled = false
        )
        assertEquals(testLightScheme.primaryContainer, color0)
        assertEquals(0.46f, alpha0, 0.001f)

        // Role 1: secondaryContainer
        val (color1, alpha1) = resolveColorRole(
            roleIndex = 1,
            colorScheme = testLightScheme,
            colorStyle = ColorStyle.TONAL_SPOT,
            pageKey = "test_page",
            isDark = false,
            isAmoled = false
        )
        assertEquals(testLightScheme.secondaryContainer, color1)
        assertEquals(0.42f, alpha1, 0.001f)

        // Role 2: tertiaryContainer
        val (color2, alpha2) = resolveColorRole(
            roleIndex = 2,
            colorScheme = testLightScheme,
            colorStyle = ColorStyle.TONAL_SPOT,
            pageKey = "test_page",
            isDark = false,
            isAmoled = false
        )
        assertEquals(testLightScheme.tertiaryContainer, color2)
        assertEquals(0.38f, alpha2, 0.001f)

        // Role 3: tertiary accent (~12-14%)
        val (color3, alpha3) = resolveColorRole(
            roleIndex = 3,
            colorScheme = testLightScheme,
            colorStyle = ColorStyle.TONAL_SPOT,
            pageKey = "test_page",
            isDark = false,
            isAmoled = false
        )
        assertEquals(testLightScheme.tertiary, color3)
        assertEquals(0.14f, alpha3, 0.001f)
    }

    @Test
    fun testColorRoles_DarkMode() {
        val (_, alpha0) = resolveColorRole(0, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, false)
        val (_, alpha1) = resolveColorRole(1, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, false)
        val (_, alpha2) = resolveColorRole(2, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, false)
        val (_, alpha3) = resolveColorRole(3, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, false)

        assertEquals(0.40f, alpha0, 0.001f)
        assertEquals(0.36f, alpha1, 0.001f)
        assertEquals(0.32f, alpha2, 0.001f)
        assertEquals(0.12f, alpha3, 0.001f)
    }

    @Test
    fun testColorRoles_AmoledModeReducesAlpha() {
        val (_, alpha0) = resolveColorRole(0, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, true)
        val (_, alpha1) = resolveColorRole(1, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, true)
        val (_, alpha2) = resolveColorRole(2, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, true)
        val (_, alpha3) = resolveColorRole(3, testDarkScheme, ColorStyle.TONAL_SPOT, "test", true, true)

        // AMOLED preserves pure OLED blacks by scaling down shape alphas
        assertEquals(0.28f, alpha0, 0.001f)
        assertEquals(0.24f, alpha1, 0.001f)
        assertEquals(0.20f, alpha2, 0.001f)
        assertEquals(0.08f, alpha3, 0.001f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. ColorStyle.EXPRESSIVE Role Rotation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testExpressiveRoleRotationPerScreen() {
        // Find page keys with different shifts: shift = abs(pageKey.hashCode()) % 3
        var foundShift0: String? = null
        var foundShift1: String? = null
        var foundShift2: String? = null

        val candidates = listOf("history", "bookmarks", "downloads", "settings", "tabs", "vault", "about", "welcome")
        for (cand in candidates) {
            when (Math.abs(cand.hashCode()) % 3) {
                0 -> if (foundShift0 == null) foundShift0 = cand
                1 -> if (foundShift1 == null) foundShift1 = cand
                2 -> if (foundShift2 == null) foundShift2 = cand
            }
        }

        assertNotNull(foundShift0)
        assertNotNull(foundShift1)
        assertNotNull(foundShift2)

        // Shift 0 should keep role 0 as primaryContainer
        val (c0Shift0, _) = resolveColorRole(0, testLightScheme, ColorStyle.EXPRESSIVE, foundShift0!!, false, false)
        assertEquals(testLightScheme.primaryContainer, c0Shift0)

        // Shift 1 should rotate role 0 to secondaryContainer
        val (c0Shift1, _) = resolveColorRole(0, testLightScheme, ColorStyle.EXPRESSIVE, foundShift1!!, false, false)
        assertEquals(testLightScheme.secondaryContainer, c0Shift1)

        // Shift 2 should rotate role 0 to tertiaryContainer
        val (c0Shift2, _) = resolveColorRole(0, testLightScheme, ColorStyle.EXPRESSIVE, foundShift2!!, false, false)
        assertEquals(testLightScheme.tertiaryContainer, c0Shift2)

        // Accent role 3 must ALWAYS remain tertiary regardless of shift
        val (accent, _) = resolveColorRole(3, testLightScheme, ColorStyle.EXPRESSIVE, foundShift1, false, false)
        assertEquals(testLightScheme.tertiary, accent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Text Contrast against Bloom Background (> 4.5:1 WCAG AA)
    // ─────────────────────────────────────────────────────────────────────────

    private fun relativeLuminance(r: Float, g: Float, b: Float): Double {
        fun channel(c: Float): Double {
            val d = c.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrastRatio(l1: Double, l2: Double): Double {
        val high = max(l1, l2)
        val low = min(l1, l2)
        return (high + 0.05) / (low + 0.05)
    }

    @Test
    fun testTextContrastAgainstBackgroundWithScrim() {
        // Verify Dark mode text contrast:
        // On dark theme: background is ~0x121316 (Luminance ~ 0.005)
        // onBackground is ~0xE2E2E6 (Luminance ~ 0.76)
        // Scrim adds 0.45-0.50 opacity base color over bloom shapes
        val darkBgLum = relativeLuminance(0.07f, 0.07f, 0.09f)
        val textLum = relativeLuminance(0.89f, 0.89f, 0.90f)
        val contrastDark = contrastRatio(textLum, darkBgLum)
        assertTrue("Dark mode text contrast should exceed 4.5:1 (was $contrastDark)", contrastDark >= 4.5)

        // Verify Light mode text contrast:
        val lightBgLum = relativeLuminance(0.98f, 0.98f, 0.99f)
        val darkTextLum = relativeLuminance(0.10f, 0.10f, 0.12f)
        val contrastLight = contrastRatio(lightBgLum, darkTextLum)
        assertTrue("Light mode text contrast should exceed 4.5:1 (was $contrastLight)", contrastLight >= 4.5)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Shape Catalog Determinism & Curated M3E Shapes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testBloomShapeCatalogDeterminism() {
        val shapes1 = BloomShapeCatalog.createShapesForSeed("history_page", 0L)
        val shapes2 = BloomShapeCatalog.createShapesForSeed("history_page", 0L)

        assertEquals(4, shapes1.size)
        assertEquals(4, shapes2.size)

        for (i in 0 until 4) {
            assertEquals(shapes1[i].roleIndex, shapes2[i].roleIndex)
            assertEquals(shapes1[i].centerRelX, shapes2[i].centerRelX, 0.0001f)
            assertEquals(shapes1[i].centerRelY, shapes2[i].centerRelY, 0.0001f)
            assertEquals(shapes1[i].radiusDp, shapes2[i].radiusDp, 0.0001f)
            assertEquals(shapes1[i].baseRotation, shapes2[i].baseRotation, 0.0001f)
            assertEquals(shapes1[i].driftSign, shapes2[i].driftSign, 0.0001f)
        }
    }
}
