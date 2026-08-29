package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * GoogleFontsManager constructs Google Fonts CSS link URLs for web font customization.
 */
public class GoogleFontsManager {

    /**
     * Constructs Google Fonts CSS link URL for dynamic web font loading.
     * Endpoint: https://fonts.googleapis.com/css2?family=&display=swap
     */
    public static String getFontCssUrl(String fontName) {
        if (fontName == null || fontName.trim().isEmpty()) return "";
        try {
            String cleanName = fontName.trim().replace(" ", "+");
            return "https://fonts.googleapis.com/css2?family=" + URLEncoder.encode(cleanName, "UTF-8") + ":wght@400;500;700&display=swap";
        } catch (Exception e) {
            return "https://fonts.googleapis.com/css2?family=" + fontName.trim().replace(" ", "+") + ":wght@400;500;700&display=swap";
        }
    }
}
