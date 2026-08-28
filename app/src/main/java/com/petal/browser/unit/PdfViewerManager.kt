package com.petal.browser.unit

import java.net.URLEncoder

/**
 * PdfViewerManager constructs Mozilla PDF.js web viewer URLs for native PDF rendering.
 */
object PdfViewerManager {

    @JvmStatic
    fun getPdfViewerUrl(pdfUrl: String?): String {

        if (pdfUrl == null || pdfUrl.trim().isEmpty()) return "";
        return try {
            "https://mozilla.github.io/pdf.js/web/viewer.html?file=" + URLEncoder.encode(pdfUrl.trim(), "UTF-8");
        
    }
}
