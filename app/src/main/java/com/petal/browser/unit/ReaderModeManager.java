package com.petal.browser.unit;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ReaderModeManager uses Readability / Mercury Parser API to extract clean, clutter-free
 * article content (title, lead image, text body) from web pages.
 */
public class ReaderModeManager {

    private static final String TAG = "ReaderModeManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static class ReaderArticle {
        public String title;
        public String author;
        public String contentHtml;
        public String leadImageUrl;
        public String domain;

        public ReaderArticle(String title, String author, String contentHtml, String leadImageUrl, String domain) {
            this.title = title != null ? title : "";
            this.author = author != null ? author : "";
            this.contentHtml = contentHtml != null ? contentHtml : "";
            this.leadImageUrl = leadImageUrl != null ? leadImageUrl : "";
            this.domain = domain != null ? domain : "";
        }
    }

    public interface ReaderCallback {
        void onArticleParsed(ReaderArticle article);
    }

    /**
     * Parses web article content into Reader Mode HTML using Mercury Parser API.
     */
    public static void parseArticle(final String targetUrl, final ReaderCallback callback) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            if (callback != null) callback.onArticleParsed(null);
            return;
        }

        executor.execute(() -> {
            ReaderArticle article = null;
            HttpURLConnection connection = null;
            try {
                String apiUrl = "https://mercury.postlight.com/parser?url=" + URLEncoder.encode(targetUrl, "UTF-8");
                URL url = new URL(apiUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(builder.toString());
                    article = new ReaderArticle(
                        json.optString("title", ""),
                        json.optString("author", ""),
                        json.optString("content", ""),
                        json.optString("lead_image_url", ""),
                        json.optString("domain", "")
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "Reader Mode parse failed for: " + targetUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final ReaderArticle finalArticle = article;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onArticleParsed(finalArticle)
                );
            }
        });
    }

    /**
     * Generates styled HTML document string for displaying in Reader Mode inside NinjaWebView.
     */
    public static String buildReaderHtml(ReaderArticle article, boolean isDarkTheme) {
        if (article == null) return "<html><body><p>Unable to parse Reader Mode content.</p></body></html>";

        String bg = isDarkTheme ? "#121212" : "#FAFAFA";
        String fg = isDarkTheme ? "#E1E1E1" : "#1F1F1F";
        String sub = isDarkTheme ? "#9E9E9E" : "#666666";

        return "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<style>" +
                "body { background-color: " + bg + "; color: " + fg + "; font-family: 'Roboto', sans-serif; line-height: 1.6; padding: 20px; max-width: 680px; margin: 0 auto; }" +
                "h1 { font-size: 24px; font-weight: 700; margin-bottom: 8px; }" +
                ".byline { color: " + sub + "; font-size: 14px; margin-bottom: 20px; border-bottom: 1px solid " + sub + "; padding-bottom: 10px; }" +
                "img { max-width: 100%; height: auto; border-radius: 12px; margin: 16px 0; }" +
                "p { font-size: 16px; margin-bottom: 16px; }" +
                "</style></head><body>" +
                "<h1>" + article.title + "</h1>" +
                "<div class=\"byline\">" + (article.domain.isEmpty() ? "" : article.domain) + (article.author.isEmpty() ? "" : " • " + article.author) + "</div>" +
                (article.leadImageUrl.isEmpty() ? "" : "<img src=\"" + article.leadImageUrl + "\"/>") +
                "<div>" + article.contentHtml + "</div>" +
                "</body></html>";
    }
}
