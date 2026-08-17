package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * HtmlToMarkdownManager constructs Markdown export endpoint URLs for web pages.
 */
public class HtmlToMarkdownManager {

    /**
     * Constructs Statickit HTML-to-Markdown API export URL.
     * Endpoint: https://api.statickit.com/v1/markdown?url=
     */
    public static String getMarkdownExportUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return "";
        try {
            return "https://api.statickit.com/v1/markdown?url=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://api.statickit.com/v1/markdown?url=" + targetUrl.trim();
        }
    }
}
