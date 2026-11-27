/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.petal.browser.compose.settings.SettingsCategory
import com.petal.browser.compose.settings.hubIconAccent
import com.petal.browser.compose.settings.iconAccent
import com.petal.browser.ui.containment.resolveBadgeShape
import com.petal.browser.ui.theme.layout.PetalBadgeShape
import com.petal.browser.ui.theme.layout.PetalShapeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PetalIconAccentTest {

    private fun Color.toRelativeLuminance(): Float {
        fun channel(c: Float): Float {
            return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
    }

    private fun contrastRatio(c1: Color, c2: Color): Float {
        val l1 = c1.toRelativeLuminance()
        val l2 = c2.toRelativeLuminance()
        val high = max(l1, l2)
        val low = min(l1, l2)
        return (high + 0.05f) / (low + 0.05f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Color Resolution & Standalone Tint Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testResolveColorsLightColorScheme() {
        val scheme = lightColorScheme()

        val primaryColors = PetalIconAccent.PRIMARY.resolveColors(scheme)
        assertEquals(scheme.primaryContainer, primaryColors.first)
        assertEquals(scheme.onPrimaryContainer, primaryColors.second)
        assertEquals(scheme.primary, PetalIconAccent.PRIMARY.resolveStandaloneTint(scheme))

        val secondaryColors = PetalIconAccent.SECONDARY.resolveColors(scheme)
        assertEquals(scheme.secondaryContainer, secondaryColors.first)
        assertEquals(scheme.onSecondaryContainer, secondaryColors.second)
        assertEquals(scheme.secondary, PetalIconAccent.SECONDARY.resolveStandaloneTint(scheme))

        val tertiaryColors = PetalIconAccent.TERTIARY.resolveColors(scheme)
        assertEquals(scheme.tertiaryContainer, tertiaryColors.first)
        assertEquals(scheme.onTertiaryContainer, tertiaryColors.second)
        assertEquals(scheme.tertiary, PetalIconAccent.TERTIARY.resolveStandaloneTint(scheme))

        val errorColors = PetalIconAccent.ERROR.resolveColors(scheme)
        assertEquals(scheme.errorContainer, errorColors.first)
        assertEquals(scheme.onErrorContainer, errorColors.second)
        assertEquals(scheme.error, PetalIconAccent.ERROR.resolveStandaloneTint(scheme))

        val neutralColors = PetalIconAccent.NEUTRAL.resolveColors(scheme)
        assertEquals(scheme.surfaceContainerHighest, neutralColors.first)
        assertEquals(scheme.onSurfaceVariant, neutralColors.second)
        assertEquals(scheme.onSurfaceVariant, PetalIconAccent.NEUTRAL.resolveStandaloneTint(scheme))
    }

    @Test
    fun testResolveColorsDarkColorScheme() {
        val scheme = darkColorScheme()

        val primaryColors = PetalIconAccent.PRIMARY.resolveColors(scheme)
        assertEquals(scheme.primaryContainer, primaryColors.first)
        assertEquals(scheme.onPrimaryContainer, primaryColors.second)
        assertEquals(scheme.primary, PetalIconAccent.PRIMARY.resolveStandaloneTint(scheme))

        val secondaryColors = PetalIconAccent.SECONDARY.resolveColors(scheme)
        assertEquals(scheme.secondaryContainer, secondaryColors.first)
        assertEquals(scheme.onSecondaryContainer, secondaryColors.second)
        assertEquals(scheme.secondary, PetalIconAccent.SECONDARY.resolveStandaloneTint(scheme))

        val tertiaryColors = PetalIconAccent.TERTIARY.resolveColors(scheme)
        assertEquals(scheme.tertiaryContainer, tertiaryColors.first)
        assertEquals(scheme.onTertiaryContainer, tertiaryColors.second)
        assertEquals(scheme.tertiary, PetalIconAccent.TERTIARY.resolveStandaloneTint(scheme))

        val errorColors = PetalIconAccent.ERROR.resolveColors(scheme)
        assertEquals(scheme.errorContainer, errorColors.first)
        assertEquals(scheme.onErrorContainer, errorColors.second)
        assertEquals(scheme.error, PetalIconAccent.ERROR.resolveStandaloneTint(scheme))

        val neutralColors = PetalIconAccent.NEUTRAL.resolveColors(scheme)
        assertEquals(scheme.surfaceContainerHighest, neutralColors.first)
        assertEquals(scheme.onSurfaceVariant, neutralColors.second)
        assertEquals(scheme.onSurfaceVariant, PetalIconAccent.NEUTRAL.resolveStandaloneTint(scheme))
    }

    @Test
    fun testResolveColorsAmoledScheme() {
        val amoledScheme = darkColorScheme().copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF282828),
            surfaceVariant = Color(0xFF1C1C1C)
        )

        for (accent in PetalIconAccent.entries) {
            val (container, tint) = accent.resolveColors(amoledScheme)
            assertNotNull(container)
            assertNotNull(tint)
            val standalone = accent.resolveStandaloneTint(amoledScheme)
            assertNotNull(standalone)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Contrast Ratio Verification (>= 3:1)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testContrastRatioAboveThreeToOneLight() {
        val scheme = lightColorScheme()
        for (accent in PetalIconAccent.entries) {
            val (container, tint) = accent.resolveColors(scheme)
            val ratio = contrastRatio(tint, container)
            assertTrue(
                "Accent $accent in Light scheme failed contrast check (expected >= 3.0, got $ratio)",
                ratio >= 3.0f
            )
        }
    }

    @Test
    fun testContrastRatioAboveThreeToOneDark() {
        val scheme = darkColorScheme()
        for (accent in PetalIconAccent.entries) {
            val (container, tint) = accent.resolveColors(scheme)
            val ratio = contrastRatio(tint, container)
            assertTrue(
                "Accent $accent in Dark scheme failed contrast check (expected >= 3.0, got $ratio)",
                ratio >= 3.0f
            )
        }
    }

    @Test
    fun testContrastRatioAboveThreeToOneAmoled() {
        val amoledScheme = darkColorScheme().copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerHighest = Color(0xFF282828),
            onSurfaceVariant = Color(0xFFCAC4D0)
        )
        for (accent in PetalIconAccent.entries) {
            val (container, tint) = accent.resolveColors(amoledScheme)
            val ratio = contrastRatio(tint, container)
            assertTrue(
                "Accent $accent in AMOLED scheme failed contrast check (expected >= 3.0, got $ratio)",
                ratio >= 3.0f
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Deterministic Badge Shapes Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testBadgeShapeDeterministicForSameKey() {
        val shape1 = resolveBadgeShape(PetalBadgeShape.MIXED, PetalShapeType.ROUNDED, 1.0f, "bookmarks")
        val shape2 = resolveBadgeShape(PetalBadgeShape.MIXED, PetalShapeType.ROUNDED, 1.0f, "bookmarks")
        assertEquals("Same key must produce identical shape instance/type", shape1, shape2)

        val shape3 = resolveBadgeShape(PetalBadgeShape.MIXED, PetalShapeType.ROUNDED, 1.0f, "settings_hub")
        val shape4 = resolveBadgeShape(PetalBadgeShape.MIXED, PetalShapeType.ROUNDED, 1.0f, "settings_hub")
        assertEquals("Same key must produce identical shape instance/type", shape3, shape4)
    }

    @Test
    fun testBadgeShapeDiffersAcrossDifferentKeysInMixed() {
        // Collect shapes for multiple distinct keys in MIXED mode
        val keys = listOf("key_a", "key_b", "key_c", "key_d", "key_e", "key_f", "key_g", "key_h")
        val shapes = keys.map { key ->
            resolveBadgeShape(PetalBadgeShape.MIXED, PetalShapeType.ROUNDED, 1.0f, key)
        }.toSet()

        // With 8 different keys and an 8-item pool, we should get multiple distinct shapes
        assertTrue("Mixed mode must provide variety across different keys", shapes.size > 1)
    }

    @Test
    fun testBadgeShapeExplicitShapesStable() {
        val cookie1 = resolveBadgeShape(PetalBadgeShape.COOKIE_12, PetalShapeType.ROUNDED, 1.0f, "any_key")
        val cookie2 = resolveBadgeShape(PetalBadgeShape.COOKIE_12, PetalShapeType.ROUNDED, 1.0f, "another_key")
        assertEquals("Explicit shape type must be independent of key", cookie1, cookie2)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Settings Category Icon Accent Semantic Mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun testSettingsCategoryIconAccentSemanticMapping() {
        // Primary: Core navigation and browsing infrastructure
        assertEquals(PetalIconAccent.PRIMARY, SettingsCategory.OVERVIEW.iconAccent())
        assertEquals(PetalIconAccent.PRIMARY, SettingsCategory.SEARCH_HOMEPAGE.iconAccent())
        assertEquals(PetalIconAccent.PRIMARY, SettingsCategory.ADDRESS_BAR.iconAccent())
        assertEquals(PetalIconAccent.PRIMARY, SettingsCategory.TABS.iconAccent())
        assertEquals(PetalIconAccent.PRIMARY, SettingsCategory.ACCOUNT_SYNC.iconAccent())

        // Secondary: Supporting tools, media, extensions and downloads
        assertEquals(PetalIconAccent.SECONDARY, SettingsCategory.DOWNLOADS.iconAccent())
        assertEquals(PetalIconAccent.SECONDARY, SettingsCategory.MEDIA.iconAccent())
        assertEquals(PetalIconAccent.SECONDARY, SettingsCategory.EXTENSIONS.iconAccent())
        assertEquals(PetalIconAccent.SECONDARY, SettingsCategory.DATA_STORAGE.iconAccent())

        // Tertiary: Expressive customization, privacy shields, AI and security
        assertEquals(PetalIconAccent.TERTIARY, SettingsCategory.API_INTEGRATIONS.iconAccent())
        assertEquals(PetalIconAccent.TERTIARY, SettingsCategory.APPEARANCE.iconAccent())
        assertEquals(PetalIconAccent.TERTIARY, SettingsCategory.APP_ICON.iconAccent())
        assertEquals(PetalIconAccent.TERTIARY, SettingsCategory.PRIVACY.iconAccent())
        assertEquals(PetalIconAccent.TERTIARY, SettingsCategory.APP_LOCK.iconAccent())
        assertEquals(PetalIconAccent.TERTIARY, SettingsCategory.DISPLAY_ZOOM.iconAccent())

        // Neutral: General info, updater and diagnostics
        assertEquals(PetalIconAccent.NEUTRAL, SettingsCategory.UPDATER.iconAccent())
        assertEquals(PetalIconAccent.NEUTRAL, SettingsCategory.ABOUT.iconAccent())
        assertEquals(PetalIconAccent.NEUTRAL, SettingsCategory.MISCELLANEOUS.iconAccent())
    }

    @Test
    fun testSettingsCategoryHubIconAccentCyclesByFixedPosition() {
        val hubCategories = SettingsCategory.entries.filter { it != SettingsCategory.OVERVIEW }
        assertTrue(hubCategories.size >= 5)

        assertEquals(PetalIconAccent.PRIMARY, hubCategories[0].hubIconAccent())
        assertEquals(PetalIconAccent.SECONDARY, hubCategories[1].hubIconAccent())
        assertEquals(PetalIconAccent.TERTIARY, hubCategories[2].hubIconAccent())
        assertEquals(PetalIconAccent.NEUTRAL, hubCategories[3].hubIconAccent())
        assertEquals(PetalIconAccent.PRIMARY, hubCategories[4].hubIconAccent())

        val expectedCycle = listOf(
            PetalIconAccent.PRIMARY,
            PetalIconAccent.SECONDARY,
            PetalIconAccent.TERTIARY,
            PetalIconAccent.NEUTRAL
        )
        hubCategories.forEachIndexed { idx, category ->
            assertEquals(expectedCycle[idx % expectedCycle.size], category.hubIconAccent())
            if (idx > 0) {
                assertNotEquals(
                    "Adjacent hub categories must not share the same accent",
                    hubCategories[idx - 1].hubIconAccent(),
                    category.hubIconAccent()
                )
            }
        }
    }
}
