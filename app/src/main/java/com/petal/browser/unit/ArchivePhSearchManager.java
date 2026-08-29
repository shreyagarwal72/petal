package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * ArchivePhSearchManager constructs Archive.ph / Archive.today index search URLs.
 */
public class ArchivePhSearchManager {

    /**
     * Constructs Archive.ph search URL.
     * Endpoint: https://archive.ph/search/?q=
     */
    public static String getSearchUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return "";
        try {
            return "https://archive.ph/search/?q=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://archive.ph/search/?q=" + targetUrl.trim();
        }
    }
}
