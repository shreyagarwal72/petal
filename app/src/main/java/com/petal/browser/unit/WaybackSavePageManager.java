package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * WaybackSavePageManager constructs Internet Archive Save Page URLs to archive active web pages.
 */
public class WaybackSavePageManager {

    /**
     * Constructs Internet Archive Save Page URL.
     * Endpoint: https://web.archive.org/save/
     */
    public static String getSavePageUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return "";
        try {
            return "https://web.archive.org/save/" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://web.archive.org/save/" + targetUrl.trim();
        }
    }
}
