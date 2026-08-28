package com.petal.browser.unit

import java.net.URLEncoder

/**
 * GoogleFontsManager constructs Google Fonts CSS link URLs for web font customization.
 */
object GoogleFontsManager {

    @JvmStatic
    fun getFontCssUrl(fontName: String?): String {

        if (fontName.isNullOrBlank()) return ""
        return try {
            String cleanName = fontName.trim().replace(" ", "+");
            "https://fonts.googleapis.com/css2?family=" + URLEncoder.encode(cleanName, "UTF-8") + ":wght@400;500;700&display=swap";
        
    }
}
