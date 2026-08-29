package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * ImageUploadManager constructs FreeImageHost image upload endpoint URLs.
 */
public class ImageUploadManager {

    /**
     * Constructs FreeImageHost upload endpoint URL.
     * Endpoint: https://freeimage.host/api/1/upload
     */
    public static String getUploadEndpointUrl() {
        return "https://freeimage.host/api/1/upload";
    }
}
