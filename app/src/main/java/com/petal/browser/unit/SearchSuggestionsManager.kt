package com.petal.browser.unit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * SearchSuggestionsManager: Official Mozilla Firefox (Fenix) OpenSearch/JSON suggestion client.
 * ─────────────────────────────────────────────────────────────────────────────
 * Implements standard OpenSearch JSON response parsing:
 * `[ "query", [ "suggestion 1", "suggestion 2", ... ], [ "description 1", ... ], [ "query url 1", ... ] ]`
 * Supports Google (Firefox client), DuckDuckGo, Bing, Ecosia, Qwant, Brave, and Startpage
 * with debouncing, sequential gating, and multi-word fallback expansion.
 */
object SearchSuggestionsManager {

    private const val TAG = "SearchSuggestions"
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val querySequence = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val FIREFOX_MOBILE_UA =
        "Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"

    fun interface SuggestionCallback {
        fun onSuggestionsFetched(suggestions: List<String>)
    }

    @JvmStatic
    fun fetchSuggestionsForEngine(
        engineIndex: String?,
        query: String,
        callback: SuggestionCallback
    ) {
        fetchSuggestionsForEngine(null, engineIndex, query, callback)
    }

    @JvmStatic
    fun fetchSuggestionsForEngine(
        context: Context?,
        engineIndex: String?,
        query: String,
        callback: SuggestionCallback
    ) {
        if (query.trim().isEmpty()) {
            callback.onSuggestionsFetched(emptyList())
            return
        }

        var index = 0
        try {
            if (engineIndex != null) index = engineIndex.toInt()
        } catch (_: Exception) {}

        if (index >= 1000) {
            var suggestTemplate: String? = null
            if (context != null) {
                try {
                    val sp = PreferenceManager.getDefaultSharedPreferences(context)
                    val customJson = sp.getString("sp_custom_search_engines", "[]") ?: "[]"
                    val engines = JSONArray(customJson)
                    for (n in 0 until engines.length()) {
                        val engine = engines.optJSONObject(n)
                        if (engine != null && engine.optInt("id", -1) == index) {
                            suggestTemplate = engine.optString("suggestUrl", "").trim()
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
            if (!suggestTemplate.isNullOrEmpty()) {
                fetchCustomSuggestions(suggestTemplate, query, callback)
            } else {
                callback.onSuggestionsFetched(emptyList())
            }
            return
        }

        when (index) {
            1 -> fetchDuckDuckGoSuggestions(query, callback)
            2 -> fetchStartpageSuggestions(query, callback)
            3 -> fetchBraveSuggestions(query, callback)
            4 -> fetchBingSuggestions(query, callback)
            5 -> fetchSearxSuggestions(query, callback)
            6 -> fetchQwantSuggestions(query, callback)
            7 -> fetchEcosiaSuggestions(query, callback)
            8 -> callback.onSuggestionsFetched(emptyList())
            0 -> fetchGoogleSuggestions(query, callback)
            else -> fetchGoogleSuggestions(query, callback)
        }
    }

    @JvmStatic
    fun fetchGoogleSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }

                if (results.isEmpty() && query.trim().contains(" ")) {
                    results = fallbackMultiWord(query) { q -> fetchGoogleSuggestionsSync(q) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchSuggestions(query: String?, callback: SuggestionCallback?) {
        fetchGoogleSuggestions(query, callback)
    }

    @JvmStatic
    fun fetchDuckDuckGoSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }
            } catch (e: Exception) {
                Log.w(TAG, "DuckDuckGo suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchBingSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://api.bing.com/osjson.aspx?query=$encodedQuery"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }

                if (results.isEmpty() && query.trim().contains(" ")) {
                    results = fallbackMultiWord(query) { q -> fetchBingSuggestionsSync(q) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bing suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchBraveSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://search.brave.com/api/suggest?q=$encodedQuery"
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Brave suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchEcosiaSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://ac.ecosia.org/autocomplete?q=$encodedQuery&type=list"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ecosia suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchQwantSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://api.qwant.com/v3/suggest?q=$encodedQuery&client=opensearch"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Qwant suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchStartpageSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://www.startpage.com/osjson.aspx?query=$encodedQuery"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Startpage suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchSearxSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = "https://searx.space/autocompleter?q=$encodedQuery"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }
            } catch (e: Exception) {
                Log.w(TAG, "SearXNG suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    @JvmStatic
    fun fetchCustomSuggestions(suggestUrlTemplate: String?, query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank() || suggestUrlTemplate.isNullOrBlank()) {
            callback?.onSuggestionsFetched(emptyList())
            return
        }

        val seq = querySequence.incrementAndGet()
        executor.execute {
            var results: List<String> = emptyList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val urlString = suggestUrlTemplate.replace("{searchTerms}", encodedQuery).replace("%s", encodedQuery)
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    results = parseOpenSearchJson(connection)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Custom suggestions failed for: $query", e)
            } finally {
                connection?.disconnect()
            }

            dispatchCallback(callback, results, seq)
        }
    }

    private fun parseOpenSearchJson(connection: HttpURLConnection): List<String> {
        val results = ArrayList<String>()
        try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val builder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
            reader.close()

            val raw = builder.toString().trim()
            if (raw.startsWith("[")) {
                val jsonArray = JSONArray(raw)
                if (jsonArray.length() >= 2 && jsonArray.get(1) is JSONArray) {
                    val suggestionsArray = jsonArray.getJSONArray(1)
                    for (i in 0 until suggestionsArray.length()) {
                        val item = suggestionsArray.optString(i)
                        if (!item.isNullOrBlank()) {
                            results.add(item.trim())
                        }
                    }
                } else {
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.get(i)
                        if (obj is JSONObject) {
                            if (obj.has("phrase")) results.add(obj.getString("phrase"))
                        } else if (obj is String && i > 0) {
                            results.add(obj)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing OpenSearch response", e)
        }
        return results
    }

    private fun interface SyncSearcher {
        fun search(query: String): List<String>
    }

    private fun fallbackMultiWord(query: String, searcher: SyncSearcher): List<String> {
        val results = ArrayList<String>()
        try {
            val words = query.trim().split("\\s+".toRegex()).toTypedArray()
            if (words.size > 2) {
                val tailQuery = words.copyOfRange(Math.max(0, words.size - 2), words.size).joinToString(" ")
                val subResults = searcher.search(tailQuery)
                val prefix = words.copyOfRange(0, Math.max(0, words.size - 2)).joinToString(" ")
                for (sub in subResults) {
                    results.add("$prefix $sub")
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun fetchGoogleSuggestionsSync(query: String): List<String> {
        var list: List<String> = emptyList()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1800
            conn.readTimeout = 1800
            conn.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                list = parseOpenSearchJson(conn)
            }
        } catch (_: Exception) {} finally {
            conn?.disconnect()
        }
        return list
    }

    private fun fetchBingSuggestionsSync(query: String): List<String> {
        var list: List<String> = emptyList()
        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = URL("https://api.bing.com/osjson.aspx?query=$encoded")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1800
            conn.readTimeout = 1800
            conn.setRequestProperty("User-Agent", FIREFOX_MOBILE_UA)
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                list = parseOpenSearchJson(conn)
            }
        } catch (_: Exception) {} finally {
            conn?.disconnect()
        }
        return list
    }

    private fun dispatchCallback(callback: SuggestionCallback?, results: List<String>, seq: Long) {
        if (callback != null && querySequence.get() == seq) {
            mainHandler.post { callback.onSuggestionsFetched(results) }
        }
    }
}
