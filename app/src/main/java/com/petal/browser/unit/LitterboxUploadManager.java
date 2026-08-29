package com.petal.browser.unit;

/**
 * LitterboxUploadManager constructs Litterbox anonymous temporary file sharing upload endpoint URLs.
 */
public class LitterboxUploadManager {

    /**
     * Constructs Litterbox Upload endpoint URL.
     * Endpoint: https://litterbox.catbox.moe/resources/internals/api.php
     */
    public static String getUploadUrl() {
        return "https://litterbox.catbox.moe/resources/internals/api.php";
    }
}
