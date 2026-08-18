package com.petal.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * FaviconPaletteExtractor extracts dominant accent colors from favicons for ambient tab glowing.
 */
object FaviconPaletteExtractor {

    fun interface ColorCallback {
        fun onColorExtracted(color: Color)
    }

    /**
     * Extracts vibrant/dominant color from bitmap asynchronously.
     */
    @JvmStatic
    fun extractDominantColor(bitmap: Bitmap?, callback: ColorCallback?) {
        if (bitmap == null || bitmap.isRecycled) {
            callback?.onColorExtracted(Color(0xFF6750A4))
            return
        }

        try {
            Palette.from(bitmap).generate { palette ->
                val defaultColor = 0xFF6750A4.toInt()
                var colorInt = defaultColor
                if (palette != null) {
                    colorInt = palette.getVibrantColor(palette.getDominantColor(defaultColor))
                }
                val resultColor = Color(colorInt)
                callback?.onColorExtracted(resultColor)
            }
        } catch (e: Exception) {
            callback?.onColorExtracted(Color(0xFF6750A4))
        }
    }
}
