package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * ArchiveBookEmbedManager constructs Internet Archive Open Library document & book embed URLs.
 */
public class ArchiveBookEmbedManager {

    /**
     * Constructs Internet Archive Book Embed URL.
     * Endpoint: https://archive.org/embed/
     */
    public static String getBookEmbedUrl(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return "";
        try {
            return "https://archive.org/embed/" + URLEncoder.encode(identifier.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://archive.org/embed/" + identifier.trim();
        }
    }
}
