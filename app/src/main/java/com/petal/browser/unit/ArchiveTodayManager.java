package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * ArchiveTodayManager constructs Archive.today anti-paywall snapshot URLs.
 */
public class ArchiveTodayManager {

    /**
     * Constructs Archive.today snapshot URL for anti-paywall viewing.
     * Endpoint: https://archive.is/newest/
     */
    public static String getArchiveTodayUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return "";
        try {
            return "https://archive.is/newest/" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://archive.is/newest/" + targetUrl.trim();
        }
    }
}
