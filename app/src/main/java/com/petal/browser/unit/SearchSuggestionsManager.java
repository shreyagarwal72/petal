package com.petal.browser.unit;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SearchSuggestionsManager handles fetching live search autocomplete/recommendations
 * from Google Suggest API (Chromium / Google Chrome omnibox suggestion endpoint).
 */
public class SearchSuggestionsManager {

    private static final String TAG = "SearchSuggestions";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface SuggestionCallback {
        void onSuggestionsFetched(List<String> suggestions);
    }

    /**
     * Fetches privacy-first search recommendations using DuckDuckGo Autocomplete API.
     * Endpoint: https://duckduckgo.com/ac/?q=
     */
    public static void fetchDuckDuckGoSuggestions(final String query, final SuggestionCallback callback) {
        if (query == null || query.trim().length() == 0) {
            if (callback != null) callback.onSuggestionsFetched(new ArrayList<>());
            return;
        }

        executor.execute(() -> {
            List<String> results = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                String encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
                String urlString = "https://duckduckgo.com/ac/?q=" + encodedQuery + "&type=list";
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    JSONArray jsonArray = new JSONArray(builder.toString());
                    if (jsonArray.length() >= 2) {
                        JSONArray suggestionsArray = jsonArray.getJSONArray(1);
                        for (int i = 0; i < suggestionsArray.length(); i++) {
                            results.add(suggestionsArray.getString(i));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "DuckDuckGo suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onSuggestionsFetched(results)
                );
            }
        });
    }

    /**
     * Fetches search recommendations for a query asynchronously using Google Suggest API.
     * Endpoint: https://suggestqueries.google.com/complete/search?client=chrome&q=
     */
    public static void fetchSuggestions(final String query, final SuggestionCallback callback) {
        if (query == null || query.trim().length() == 0) {
            if (callback != null) callback.onSuggestionsFetched(new ArrayList<>());
            return;
        }

        executor.execute(() -> {
            List<String> results = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                String encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
                String urlString = "https://suggestqueries.google.com/complete/search?client=chrome&q=" + encodedQuery;
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    // Google Suggest Chrome API JSON format:
                    // ["query", ["suggestion1", "suggestion2", ...], ...]
                    JSONArray jsonArray = new JSONArray(builder.toString());
                    if (jsonArray.length() >= 2) {
                        JSONArray suggestionsArray = jsonArray.getJSONArray(1);
                        for (int i = 0; i < suggestionsArray.length(); i++) {
                            results.add(suggestionsArray.getString(i));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to fetch suggestions for query: " + query, e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onSuggestionsFetched(results)
                );
            }
        });
    }
}
