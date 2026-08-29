package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * GoogleWebLightManager constructs Google Web Light ultra-lightweight page loading URLs.
 */
public class GoogleWebLightManager {

    /**
     * Constructs Google Web Light proxy URL for slow 2G/cellular networks.
     * Endpoint: https://googleweblight.com/i?u=
     */
    public static String getWebLightUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return "";
        try {
            return "https://googleweblight.com/i?u=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://googleweblight.com/i?u=" + targetUrl.trim();
        }
    }
}
