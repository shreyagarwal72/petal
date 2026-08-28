package com.petal.browser.unit

import java.net.URLEncoder

/**
 * UnsplashWallpaperManager constructs high-res daily aesthetic wallpaper URLs.
 */
object UnsplashWallpaperManager {

    @JvmStatic
    fun getWallpaperUrl(categoryKeyword: String?): String {

        String keyword = (categoryKeyword != null && !categoryKeyword.trim().isEmpty()) ? categoryKeyword.trim() : "nature";
        return try {
            "https://source.unsplash.com/1080x1920/?" + URLEncoder.encode(keyword, "UTF-8");
        
    }
}
