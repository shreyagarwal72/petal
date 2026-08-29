package com.petal.browser.unit;

import java.net.URLEncoder;

/**
 * GithubGistExporterManager constructs GitHub Gist creation URLs for code snippets.
 */
public class GithubGistExporterManager {

    /**
     * Constructs GitHub Gist new snippet URL.
     * Endpoint: https://gist.github.com/
     */
    public static String getNewGistUrl() {
        return "https://gist.github.com/";
    }
}
