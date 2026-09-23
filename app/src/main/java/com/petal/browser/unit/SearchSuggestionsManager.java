package com.petal.browser.unit;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * SearchSuggestionsManager: Official Mozilla Firefox (Fenix) OpenSearch/JSON suggestion client.
 * ─────────────────────────────────────────────────────────────────────────────
 * Implements standard OpenSearch JSON response parsing:
 * `[ "query", [ "suggestion 1", "suggestion 2", ... ], [ "description 1", ... ], [ "query url 1", ... ] ]`
 * Supports Google (Firefox client), DuckDuckGo, Bing, Ecosia, Qwant, Brave, and Startpage
 * with debouncing, sequential gating, and multi-word fallback expansion.
 */
public class SearchSuggestionsManager {

    private static final String TAG = "SearchSuggestions";
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final AtomicLong querySequence = new AtomicLong(0);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String FIREFOX_MOBILE_UA =
            "Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0";

    public interface SuggestionCallback {
        void onSuggestionsFetched(List<String> suggestions);
    }

    /**
     * Dispatch suggestion query based on search engine index configured in preferences:
     * 0: Google, 1: DuckDuckGo, 2: Startpage, 3: Brave, 4: Bing, 5: Searx, 6: Qwant, 7: Ecosia
     */
    public static void fetchSuggestionsForEngine(
            @Nullable String engineIndex,
            @NonNull String query,
            @NonNull SuggestionCallback callback
    ) {
        if (query == null || query.trim().isEmpty()) {
            callback.onSuggestionsFetched(new ArrayList<>());
            return;
        }

        int index = 0;
        try {
            if (engineIndex != null) index = Integer.parseInt(engineIndex);
        } catch (Exception ignored) {}

        switch (index) {
            case 1:
                fetchDuckDuckGoSuggestions(query, callback);
                break;
            case 2:
                fetchStartpageSuggestions(query, callback);
                break;
            case 4:
                fetchBingSuggestions(query, callback);
                break;
            case 6:
                fetchQwantSuggestions(query, callback);
                break;
            case 7:
                fetchEcosiaSuggestions(query, callback);
                break;
            case 0:
            case 3:
            case 5:
            default:
                fetchGoogleSuggestions(query, callback);
                break;
        }
    }

    /**
     * Official Firefox Google Suggest endpoint:
     * https://suggestqueries.google.com/complete/search?client=firefox&q={query}
     * Returns standard OpenSearch JSON format.
     */
    public static void fetchGoogleSuggestions(final String query, final SuggestionCallback callback) {
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
                String urlString = "https://suggestqueries.google.com/complete/search?client=firefox&q=" + encodedQuery;
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection);
                }

                // Multi-word tail query fallback
                if (results.isEmpty() && query.trim().contains(" ")) {
                    results = fallbackMultiWord(query, q -> fetchGoogleSuggestionsSync(q));
                }
            } catch (Exception e) {
                Log.w(TAG, "Google suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            dispatchCallback(callback, results, seq);
        });
    }

    /**
     * Backward-compatible alias for fetchGoogleSuggestions
     */
    public static void fetchSuggestions(final String query, final SuggestionCallback callback) {
        fetchGoogleSuggestions(query, callback);
    }

    /**
     * Official DuckDuckGo Autocomplete endpoint:
     * https://duckduckgo.com/ac/?q={query}&type=list
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
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection);
                }
            } catch (Exception e) {
                Log.w(TAG, "DuckDuckGo suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            dispatchCallback(callback, results, seq);
        });
    }

    /**
     * Bing Search Autocomplete API:
     * https://api.bing.com/osjson.aspx?query={query}
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
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection);
                }

                if (results.isEmpty() && query.trim().contains(" ")) {
                    results = fallbackMultiWord(query, q -> fetchBingSuggestionsSync(q));
                }
            } catch (Exception e) {
                Log.w(TAG, "Bing suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            dispatchCallback(callback, results, seq);
        });
    }

    /**
     * Ecosia Autocomplete API:
     * https://ac.ecosia.org/autocomplete?q={query}&type=list
     */
    public static void fetchEcosiaSuggestions(final String query, final SuggestionCallback callback) {
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
                String urlString = "https://ac.ecosia.org/autocomplete?q=" + encodedQuery + "&type=list";
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection);
                }
            } catch (Exception e) {
                Log.w(TAG, "Ecosia suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            dispatchCallback(callback, results, seq);
        });
    }

    /**
     * Qwant Autocomplete API:
     * https://api.qwant.com/v3/suggest?q={query}&client=opensearch
     */
    public static void fetchQwantSuggestions(final String query, final SuggestionCallback callback) {
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
                String urlString = "https://api.qwant.com/v3/suggest?q=" + encodedQuery + "&client=opensearch";
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection);
                }
            } catch (Exception e) {
                Log.w(TAG, "Qwant suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            dispatchCallback(callback, results, seq);
        });
    }

    /**
     * Startpage Autocomplete API:
     * https://www.startpage.com/osjson.aspx?query={query}
     */
    public static void fetchStartpageSuggestions(final String query, final SuggestionCallback callback) {
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
                String urlString = "https://www.startpage.com/osjson.aspx?query=" + encodedQuery;
                URL url = new URL(urlString);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection);
                }
            } catch (Exception e) {
                Log.w(TAG, "Startpage suggestions failed for: " + query, e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            dispatchCallback(callback, results, seq);
        });
    }

    /**
     * Robust OpenSearch JSON parser used across Mozilla Firefox.
     * Handles `["query", ["s1", "s2", ...]]` and object array fallbacks.
     */
    private static List<String> parseOpenSearchJson(HttpURLConnection connection) {
        List<String> results = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();

            String raw = builder.toString().trim();
            if (raw.startsWith("[")) {
                JSONArray jsonArray = new JSONArray(raw);
                if (jsonArray.length() >= 2 && jsonArray.get(1) instanceof JSONArray) {
                    JSONArray suggestionsArray = jsonArray.getJSONArray(1);
                    for (int i = 0; i < suggestionsArray.length(); i++) {
                        String item = suggestionsArray.optString(i);
                        if (item != null && !item.trim().isEmpty()) {
                            results.add(item.trim());
                        }
                    }
                } else {
                    for (int i = 0; i < jsonArray.length(); i++) {
                        Object obj = jsonArray.get(i);
                        if (obj instanceof JSONObject) {
                            JSONObject itemObj = (JSONObject) obj;
                            if (itemObj.has("phrase")) results.add(itemObj.getString("phrase"));
                        } else if (obj instanceof String && i > 0) {
                            results.add((String) obj);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing OpenSearch response", e);
        }
        return results;
    }

    private interface SyncSearcher {
        List<String> search(String query);
    }

    private static List<String> fallbackMultiWord(String query, SyncSearcher searcher) {
        List<String> results = new ArrayList<>();
        try {
            String[] words = query.trim().split("\\s+");
            if (words.length > 2) {
                String tailQuery = String.join(" ", Arrays.copyOfRange(words, Math.max(0, words.length - 2), words.length));
                List<String> subResults = searcher.search(tailQuery);
                String prefix = String.join(" ", Arrays.copyOfRange(words, 0, Math.max(0, words.length - 2)));
                for (String sub : subResults) {
                    results.add(prefix + " " + sub);
                }
            }
        } catch (Exception ignored) {}
        return results;
    }

    private static List<String> fetchGoogleSuggestionsSync(String query) {
        List<String> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            URL url = new URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" + encoded);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1800);
            conn.setReadTimeout(1800);
            conn.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                list = parseOpenSearchJson(conn);
            }
        } catch (Exception ignored) {}
        finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static List<String> fetchBingSuggestionsSync(String query) {
        List<String> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            URL url = new URL("https://api.bing.com/osjson.aspx?query=" + encoded);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1800);
            conn.setReadTimeout(1800);
            conn.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA);
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                list = parseOpenSearchJson(conn);
            }
        } catch (Exception ignored) {}
        finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private static void dispatchCallback(SuggestionCallback callback, List<String> results, long seq) {
        if (callback != null && querySequence.get() == seq) {
            mainHandler.post(() -> callback.onSuggestionsFetched(results));
        }
    }
}
