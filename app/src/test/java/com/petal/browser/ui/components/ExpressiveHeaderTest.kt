/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.FontAccentMode
import com.petal.browser.ui.theme.PetalFontAccentColors
import com.petal.browser.ui.theme.applyAmoled
import com.petal.browser.ui.theme.applyStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveHeaderTest {

    @Test
    fun darkSchemes_headerTitleColorIsNeverBlackOrUnspecified() {
        val schemes = listOf(
            "dark" to darkColorScheme(),
            "amoled" to darkColorScheme().applyAmoled(),
            "material_you_dark_sim" to darkColorScheme(
                primary = Color(0xFFA8C7FA),
                onSurface = Color(0xFFE3E2E6),
                background = Color(0xFF111318),
                surfaceContainer = Color(0xFF1E2025),
                primaryContainer = Color(0xFF284777),
            ).applyStyle(ColorStyle.TONAL_SPOT),
        )

        for ((name, scheme) in schemes) {
            assertTrue("Expected dark background for $name", scheme.background.luminance() < 0.5f)

            // Glass off
            val offColor = resolveHeaderContentColor(glassStyle = null, onSurface = scheme.onSurface)
            assertNotEquals("Glass-off header title must not be Color.Black in $name", Color.Black, offColor)
            assertNotEquals("Glass-off header title must not be Unspecified in $name", Color.Unspecified, offColor)
            assertTrue(
                "Glass-off header title luminance too low in $name",
                offColor.luminance() > 0.5f
            )

            // Glass on
            val glassColor = resolveHeaderContentColor(
                glassStyle = HeaderGlassStyle(isDark = true),
                onSurface = scheme.onSurface
            )
            assertNotEquals("Glass-on header title must not be Color.Black in $name", Color.Black, glassColor)
            assertEquals(Color.White, glassColor)

            // Header surface in glass-off mode has readable contrast with resolved title color
            val headerBg = lerp(scheme.surfaceContainer, scheme.primaryContainer, 0.30f)
            assertTrue(
                "Header contrast below 4.5:1 for $name",
                PetalFontAccentColors.contrastRatio(offColor, headerBg) >= 4.5f
            )
        }
    }

    @Test
    fun lightSchemes_headerTitleColorMatchesOnSurfaceOrGlassDarkInk() {
        val lightScheme = lightColorScheme()
        val materialYouLightSim = lightColorScheme(
            primary = Color(0xFF0B57D0),
            onSurface = Color(0xFF1A1C1E),
            background = Color(0xFFFAF9FD),
        ).applyStyle(ColorStyle.VIBRANT)

        for (scheme in listOf(lightScheme, materialYouLightSim)) {
            assertTrue(scheme.background.luminance() >= 0.5f)
            assertEquals(
                scheme.onSurface,
                resolveHeaderContentColor(glassStyle = null, onSurface = scheme.onSurface)
            )
            assertEquals(
                Color(0xFF111114),
                resolveHeaderContentColor(glassStyle = HeaderGlassStyle(isDark = false), onSurface = scheme.onSurface)
            )
        }
    }

    @Test
    fun pageTitleFallbackColor_resolvesOnSurfaceNeverBlackOnDark() {
        val darkScheme = darkColorScheme()
        val accentOff = PetalFontAccentColors.Off.headingOr(darkScheme.onSurface)
        assertNotEquals(Color.Black, accentOff)
        assertEquals(darkScheme.onSurface, accentOff)

        val resolvedAccent = PetalFontAccentColors.resolve(FontAccentMode.HEADINGS, darkScheme)
        val accentHeading = resolvedAccent.headingOr(darkScheme.onSurface)
        assertNotEquals(Color.Black, accentHeading)
        assertNotEquals(Color.Unspecified, accentHeading)
    }

    @Test
    fun glassHeaderLayout_drawsBackgroundOnceWhenGlassOff_preventsCornerSeam() {
        // When backdrop is null (glass off), only bgPlaceable draws pageBackground at (0, 0) full-screen.
        assertFalse(shouldDrawInlinePageBackground(backdrop = null))
    }

    @Test
    fun headerShadowElevation_suppressedOnWallpaperAndGlass() {
        // Home with wallpaper and glass off: no drop-shadow grey edge outside 36dp corners.
        assertEquals(0.dp, resolveHeaderShadowElevation(enableLiquidGlass = false, hasWallpaper = true))
        // Non-wallpaper screens with glass off (e.g. Settings without wallpaper): keeps 3.dp elevation.
        assertEquals(3.dp, resolveHeaderShadowElevation(enableLiquidGlass = false, hasWallpaper = false))
        // Glass active: shadow elevation disabled regardless of wallpaper.
        assertEquals(0.dp, resolveHeaderShadowElevation(enableLiquidGlass = true, hasWallpaper = false))
        assertEquals(0.dp, resolveHeaderShadowElevation(enableLiquidGlass = true, hasWallpaper = true))
    }
}
