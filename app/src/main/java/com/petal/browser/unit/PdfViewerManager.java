package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * PdfViewerManager constructs Mozilla PDF.js web viewer URLs for native PDF rendering.
 */
public class PdfViewerManager {

    /**
     * Constructs Mozilla PDF.js web viewer URL.
     * Endpoint: https://mozilla.github.io/pdf.js/web/viewer.html?file=
     */
    public static String getPdfViewerUrl(String pdfUrl) {
        if (pdfUrl == null || pdfUrl.trim().isEmpty()) return "";
        try {
            return "https://mozilla.github.io/pdf.js/web/viewer.html?file=" + URLEncoder.encode(pdfUrl.trim(), "UTF-8");
        } catch (Exception e) {
            return "https://mozilla.github.io/pdf.js/web/viewer.html?file=" + pdfUrl.trim();
        }
    }
}
