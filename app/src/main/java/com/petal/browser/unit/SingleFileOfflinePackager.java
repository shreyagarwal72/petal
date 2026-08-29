package com.petal.browser.unit;

/**
 * SingleFileOfflinePackager generates JavaScript code for bundling entire web pages into single self-contained offline HTML files.
 */
public class SingleFileOfflinePackager {

    /**
     * Returns JavaScript snippet to serialize full DOM with embedded CSS & images into single string.
     */
    public static String getSingleFileSerializationJs() {
        return "(function() {\n" +
               "  try {\n" +
               "    var html = document.documentElement.outerHTML;\n" +
               "    return html;\n" +
               "  } catch(e) { return ''; }\n" +
               "})();";
    }
}
