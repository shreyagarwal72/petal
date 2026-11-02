/*
 * PetalAudioColorEngine.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Adaptive dynamic color engine for Petal Audio Player, inspired by PixelPlayer's
 * ColorSchemeProcessor.
 *
 * Extracts vibrant seed color from track artwork, computes harmonious dark/light
 * Material 3 palettes, and maintains an LRU memory cache for instantaneous transitions.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.media

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min

typealias PetalAudioPalette = PetalAudioColorEngine.ExtractedAudioPalette

object PetalAudioColorEngine {

    private val colorCache = LruCache<Int, ExtractedAudioPalette>(32)

    data class ExtractedAudioPalette(
        val seedColor: Color,
        val primary: Color,
        val onPrimary: Color,
        val primaryContainer: Color,
        val onPrimaryContainer: Color,
        val surface: Color,
        val onSurface: Color,
        val onSurfaceVariant: Color,
        val surfaceContainer: Color,
        val surfaceContainerHigh: Color,
        val surfaceContainerHighest: Color,
        val backgroundGradientColors: List<Color>,
    ) {
        val seed: Color get() = seedColor
    }

    /**
     * Extracts or retrieves cached palette from the artwork bitmap.
     */
    fun extractPalette(artwork: Bitmap?): ExtractedAudioPalette {
        if (artwork == null) {
            return defaultPalette()
        }

        val cacheKey = artwork.hashCode()
        colorCache.get(cacheKey)?.let { return it }

        val seed = extractSeedColor(artwork)
        val palette = buildDarkPaletteFromSeed(seed)
        colorCache.put(cacheKey, palette)
        return palette
    }

    private fun extractSeedColor(bitmap: Bitmap): Color {
        return try {
            // Sample a downscaled grid of pixels for instant performance without memory bloat
            val targetW = min(bitmap.width, 48)
            val targetH = min(bitmap.height, 48)
            val scaled = if (bitmap.width > 48 || bitmap.height > 48) {
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, false)
            } else {
                bitmap
            }

            var bestColorArgb = 0
            var highestScore = -1.0f

            val hsl = FloatArray(3)
            for (y in 0 until scaled.height step 2) {
                for (x in 0 until scaled.width step 2) {
                    val pixel = scaled.getPixel(x, y)
                    val alpha = (pixel ushr 24) and 0xFF
                    if (alpha < 128) continue

                    ColorUtils.colorToHSL(pixel, hsl)
                    val saturation = hsl[1]
                    val lightness = hsl[2]

                    // Discard extreme black, extreme white, or completely desaturated grays
                    if (lightness < 0.12f || lightness > 0.88f || saturation < 0.15f) continue

                    // Score favoring vibrant, rich colors (similar to Material You seed scoring)
                    val score = saturation * 2.0f + (1.0f - kotlin.math.abs(lightness - 0.5f))
                    if (score > highestScore) {
                        highestScore = score
                        bestColorArgb = pixel
                    }
                }
            }

            if (scaled !== bitmap) {
                scaled.recycle()
            }

            if (bestColorArgb != 0) {
                Color(bestColorArgb)
            } else {
                // Fallback to center-third pixel
                Color(bitmap.getPixel(bitmap.width / 2, bitmap.height / 3))
            }
        } catch (_: Exception) {
            Color(0xFF7D5260)
        }
    }

    private fun buildDarkPaletteFromSeed(seed: Color): ExtractedAudioPalette {
        val seedArgb = seed.toArgb()
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seedArgb, hsl)

        val hue = hsl[0]
        val sat = hsl[1].coerceIn(0.35f, 0.85f)

        fun hslColor(h: Float, s: Float, l: Float): Color {
            val out = FloatArray(3) { 0f }
            out[0] = h
            out[1] = s
            out[2] = l.coerceIn(0f, 1f)
            return Color(ColorUtils.HSLToColor(out))
        }

        val primary = hslColor(hue, sat, 0.72f)
        val onPrimary = hslColor(hue, sat, 0.12f)
        val primaryContainer = hslColor(hue, sat * 0.7f, 0.28f)
        val onPrimaryContainer = hslColor(hue, sat * 0.4f, 0.90f)

        val bgDark = hslColor(hue, sat * 0.25f, 0.08f)
        val surface = hslColor(hue, sat * 0.20f, 0.11f)
        val surfaceContainer = hslColor(hue, sat * 0.22f, 0.14f)
        val surfaceContainerHigh = hslColor(hue, sat * 0.22f, 0.18f)
        val surfaceContainerHighest = hslColor(hue, sat * 0.24f, 0.22f)

        val onSurface = Color(0xFFE6E1E5)
        val onSurfaceVariant = Color(0xFFCAC4D0)

        val gradient = listOf(
            hslColor(hue, sat * 0.65f, 0.24f).copy(alpha = 0.75f),
            hslColor(hue, sat * 0.45f, 0.14f).copy(alpha = 0.50f),
            bgDark
        )

        return ExtractedAudioPalette(
            seedColor = seed,
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            surface = surface,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            backgroundGradientColors = gradient
        )
    }

    fun defaultPalette(): ExtractedAudioPalette {
        return ExtractedAudioPalette(
            seedColor = Color(0xFF6750A4),
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            surface = Color(0xFF141218),
            onSurface = Color(0xFFE6E1E5),
            onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceContainer = Color(0xFF1D1B20),
            surfaceContainerHigh = Color(0xFF2B2930),
            surfaceContainerHighest = Color(0xFF36343B),
            backgroundGradientColors = listOf(
                Color(0xFF211F26),
                Color(0xFF17151C),
                Color(0xFF0F0E13)
            )
        )
    }
}
