package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * FaviconGrabberManager extracts favicon and touch icon arrays for site accent tinting.
 */
public class FaviconGrabberManager {

    /**
     * Constructs FaviconGrabber API URL.
     * Endpoint: https://favicongrabber.com/api/site/{domain}
     */
    public static String getFaviconGrabberUrl(String domain) {
        if (domain == null || domain.trim().isEmpty()) return "";
        String cleanDomain = domain.replace("https://", "").replace("http://", "").split("/")[0];
        try {
            return "https://favicongrabber.com/api/site/" + URLEncoder.encode(cleanDomain, "UTF-8");
        } catch (Exception e) {
            return "https://favicongrabber.com/api/site/" + cleanDomain;
        }
    }
}
