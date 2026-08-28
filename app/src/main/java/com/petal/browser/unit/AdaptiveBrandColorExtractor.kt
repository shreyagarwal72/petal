package com.petal.browser.unit

/**
 * AdaptiveBrandColorExtractor generates JS snippet to extract website theme-color meta tag or brand color.
 */
object AdaptiveBrandColorExtractor {

    /**
     * JS code to extract theme-color meta tag from active DOM.
     */
    @JvmStatic
    fun getThemeColorExtractorJs(): String {
        return "(function() {\n" +
               "  var meta = document.querySelector('meta[name=\"theme-color\"]');\n" +
               "  if (meta && meta.content) return meta.content;\n" +
               "  var tileMeta = document.querySelector('meta[name=\"msapplication-TileColor\"]');\n" +
               "  if (tileMeta && tileMeta.content) return tileMeta.content;\n" +
               "  return '';\n" +
               "})();"
    }
}
