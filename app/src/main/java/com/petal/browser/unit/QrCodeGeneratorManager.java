package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * QrCodeGeneratorManager generates instant QR code image URLs for sharing tab URLs & text.
 */
public class QrCodeGeneratorManager {

    /**
     * Returns a 300x300 QR code image URL for a tab URL using QRServer API.
     * Endpoint: https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=
     */
    public static String getQrCodeUrl(String content) {
        if (content == null || content.trim().isEmpty()) return "";
        try {
            return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + URLEncoder.encode(content.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + content.trim();
        }
    }
}
