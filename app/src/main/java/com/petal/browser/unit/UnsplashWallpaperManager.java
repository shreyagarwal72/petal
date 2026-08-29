package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * UnsplashWallpaperManager constructs high-res daily aesthetic wallpaper URLs.
 */
public class UnsplashWallpaperManager {

    /**
     * Generates Unsplash source wallpaper image URL for mobile resolution.
     * Endpoint: https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1080
     */
    public static String getWallpaperUrl(String categoryKeyword) {
        String keyword = (categoryKeyword != null && !categoryKeyword.trim().isEmpty()) ? categoryKeyword.trim() : "nature";
        try {
            return "https://source.unsplash.com/1080x1920/?" + URLEncoder.encode(keyword, "UTF-8");
        } catch (Exception e) {
            return "https://source.unsplash.com/1080x1920/?" + keyword;
        }
    }
}
