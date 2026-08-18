package com.petal.browser.ui.components;

import android.graphics.Bitmap;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.graphics.ColorKt;
import androidx.palette.graphics.Palette;

/**
 * FaviconPaletteExtractor extracts dominant accent colors from favicons for ambient tab glowing.
 */
public class FaviconPaletteExtractor {

    public interface ColorCallback {
        void onColorExtracted(Color color);
    }

    /**
     * Extracts vibrant/dominant color from bitmap asynchronously.
     */
    public static void extractDominantColor(Bitmap bitmap, ColorCallback callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            if (callback != null) callback.onColorExtracted(ColorKt.Color(0xFF6750A4L));
            return;
        }

        try {
            Palette.from(bitmap).generate(palette -> {
                int defaultColor = 0xFF6750A4;
                int colorInt = defaultColor;
                if (palette != null) {
                    colorInt = palette.getVibrantColor(palette.getDominantColor(defaultColor));
                }
                Color resultColor = ColorKt.Color((long) colorInt & 0xFFFFFFFFL);
                if (callback != null) callback.onColorExtracted(resultColor);
            });
        } catch (Exception e) {
            if (callback != null) callback.onColorExtracted(ColorKt.Color(0xFF6750A4L));
        }
    }
}
