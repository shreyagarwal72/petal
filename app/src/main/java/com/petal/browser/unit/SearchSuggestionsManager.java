package com.petal.browser.unit;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SearchSuggestionsManager handles fetching live search autocomplete/recommendations
 * from Google Suggest, DuckDuckGo, and Bing APIs with robust multi-word support for long queries.
 */
public class SearchSuggestionsManager {

    private static final String TAG = "SearchSuggestions";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final AtomicLong querySequence = new AtomicLong(0);

    public interface SuggestionCallback {
        void onSuggestionsFetched(List<String> suggestions);
    }

    /**
     * Fetches search recommendations using Bing Search Autocomplete API.
     * Endpoint: https://api.bing.com/osjson.aspx?query=
     */
    public static void fetchBingSuggestions(final String query, final SuggestionCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onSuggestionsFetched(new ArrayList<>());
            return;
        }

        final long seq = querySequence.incrementAndGet();
        executor.execute(() -> {
            List<String> results = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
                String urlString = "https://api.bing.com/osjson.aspx?query=" + encodedQuery;
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
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

                // Fallback for long multi-word searches when Bing returns 0 exact full-match results
                if (results.isEmpty() && query.trim().contains(" ")) {
                    String[] words = query.trim().split("\\s+");
                    if (words.length > 2) {
                        String tailQuery = String.join(" ", Arrays.copyOfRange(words, Math.max(0, words.length - 2), words.length));
                        List<String> subResults = fetchBingSuggestionsSync(tailQuery);
                        String prefix = String.join(" ", Arrays.copyOfRange(words, 0, Math.max(0, words.length - 2)));
                        for (String sub : subResults) {
                            results.add(prefix + " " + sub);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Bing suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            if (callback != null && querySequence.get() == seq) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onSuggestionsFetched(results)
                );
            }
        });
    }

    private static List<String> fetchBingSuggestionsSync(String query) {
        List<String> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            URL url = new URL("https://api.bing.com/osjson.aspx?query=" + encoded);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder b = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) b.append(line);
                r.close();
                JSONArray arr = new JSONArray(b.toString());
                if (arr.length() >= 2) {
                    JSONArray sa = arr.getJSONArray(1);
                    for (int i = 0; i < Math.min(sa.length(), 4); i++) {
                        list.add(sa.getString(i));
                    }
                }
            }
        } catch (Exception ignored) {}
        finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    /**
     * Fetches privacy-first search recommendations using DuckDuckGo Autocomplete API.
     * Endpoint: https://duckduckgo.com/ac/?q=
     */
    public static void fetchDuckDuckGoSuggestions(final String query, final SuggestionCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onSuggestionsFetched(new ArrayList<>());
            return;
        }

        final long seq = querySequence.incrementAndGet();
        executor.execute(() -> {
            List<String> results = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
                String urlString = "https://duckduckgo.com/ac/?q=" + encodedQuery + "&type=list";
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    reader.close();

                    String jsonStr = builder.toString().trim();
                    if (jsonStr.startsWith("[")) {
                        JSONArray jsonArray = new JSONArray(jsonStr);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            Object obj = jsonArray.get(i);
                            if (obj instanceof JSONObject) {
                                JSONObject itemObj = (JSONObject) obj;
                                if (itemObj.has("phrase")) {
                                    results.add(itemObj.getString("phrase"));
                                }
                            } else if (obj instanceof String) {
                                results.add((String) obj);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "DuckDuckGo suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            if (callback != null && querySequence.get() == seq) {
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
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onSuggestionsFetched(new ArrayList<>());
            return;
        }

        final long seq = querySequence.incrementAndGet();
        executor.execute(() -> {
            List<String> results = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
                String urlString = "https://suggestqueries.google.com/complete/search?client=chrome&q=" + encodedQuery;
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
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

                // Fallback for long multi-word searches when Google returns 0 exact full-match completions
                if (results.isEmpty() && query.trim().contains(" ")) {
                    String[] words = query.trim().split("\\s+");
                    if (words.length > 2) {
                        String tailQuery = String.join(" ", Arrays.copyOfRange(words, Math.max(0, words.length - 2), words.length));
                        List<String> subResults = fetchGoogleSuggestionsSync(tailQuery);
                        String prefix = String.join(" ", Arrays.copyOfRange(words, 0, Math.max(0, words.length - 2)));
                        for (String sub : subResults) {
                            results.add(prefix + " " + sub);
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

            if (callback != null && querySequence.get() == seq) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onSuggestionsFetched(results)
                );
            }
        });
    }

    private static List<String> fetchGoogleSuggestionsSync(String query) {
        List<String> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            URL url = new URL("https://suggestqueries.google.com/complete/search?client=chrome&q=" + encoded);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder b = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) b.append(line);
                r.close();
                JSONArray arr = new JSONArray(b.toString());
                if (arr.length() >= 2) {
                    JSONArray sa = arr.getJSONArray(1);
                    for (int i = 0; i < Math.min(sa.length(), 4); i++) {
                        list.add(sa.getString(i));
                    }
                }
            }
        } catch (Exception ignored) {}
        finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }
}
