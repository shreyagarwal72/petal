package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * W3cValidatorManager constructs W3C Nu HTML5 Validation endpoint URLs for developer debugging.
 */
public class W3cValidatorManager {

    /**
     * Constructs W3C Nu HTML5 Validator URL.
     * Endpoint: https://validator.w3.org/nu/?doc=
     */
    public static String getValidationUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return "";
        try {
            return "https://validator.w3.org/nu/?doc=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://validator.w3.org/nu/?doc=" + targetUrl.trim();
        }
    }
}
