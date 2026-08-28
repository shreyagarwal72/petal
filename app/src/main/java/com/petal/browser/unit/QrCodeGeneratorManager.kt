package com.petal.browser.unit

import java.net.URLEncoder

/**
 * QrCodeGeneratorManager generates instant QR code image URLs for sharing tab URLs & text.
 */
object QrCodeGeneratorManager {

    @JvmStatic
    fun getQrCodeUrl(content: String?): String {

        if (content.isNullOrBlank()) return ""
        return try {
            "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + URLEncoder.encode(content.trim(), "UTF-8");
        
    }
}
