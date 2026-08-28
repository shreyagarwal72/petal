package com.petal.browser.unit

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * SearchSuggestionsManager handles fetching live search autocomplete/recommendations
 * from Google Suggest API (Chromium / Google Chrome omnibox suggestion endpoint).
 */
object SearchSuggestionsManager {

    private const val TAG = "SearchSuggestions"
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun interface SuggestionCallback {
        fun onSuggestionsFetched(suggestions: List<String>)
    }

    @JvmStatic
    fun fetchBingSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(ArrayList())
            return
        }

        executor.execute {
            val results: MutableList<String> = ArrayList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
                val urlString = "https://api.bing.com/osjson.aspx?query=$encodedQuery"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val builder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        builder.append(line)
                    }
                    reader.close()

                    val jsonArray = JSONArray(builder.toString())
                    if (jsonArray.length() > 1) {
                        val suggestionsArray = jsonArray.getJSONArray(1)
                        for (i in 0 until suggestionsArray.length()) {
                            results.add(suggestionsArray.getString(i))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Bing suggestions: ${e.message}")
            } finally {
                connection?.disconnect()
            }
            callback?.onSuggestionsFetched(results)
        }
    }

    @JvmStatic
    fun fetchGoogleSuggestions(query: String?, callback: SuggestionCallback?) {
        if (query.isNullOrBlank()) {
            callback?.onSuggestionsFetched(ArrayList())
            return
        }

        executor.execute {
            val results: MutableList<String> = ArrayList()
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
                val urlString = "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
                val url = URL(urlString)

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val builder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        builder.append(line)
                    }
                    reader.close()

                    val jsonArray = JSONArray(builder.toString())
                    if (jsonArray.length() > 1) {
                        val suggestionsArray = jsonArray.getJSONArray(1)
                        for (i in 0 until suggestionsArray.length()) {
                            results.add(suggestionsArray.getString(i))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Google suggestions: ${e.message}")
            } finally {
                connection?.disconnect()
            }
            callback?.onSuggestionsFetched(results)
        }
    }
}
