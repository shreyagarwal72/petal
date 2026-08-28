package com.petal.browser.unit

import java.net.URLEncoder

/**
 * W3cValidatorManager constructs W3C Nu HTML5 Validation endpoint URLs for developer debugging.
 */
object W3cValidatorManager {

    @JvmStatic
    fun getValidationUrl(targetUrl: String?): String {

        if (targetUrl.isNullOrBlank()) return ""
        return try {
            "https://validator.w3.org/nu/?doc=" + URLEncoder.encode(targetUrl.trim(), "UTF-8");
        
    }
}
