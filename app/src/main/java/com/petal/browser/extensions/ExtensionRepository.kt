/*
 * ExtensionRepository.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Queries Mozilla's public AMO v5 search API (addons.mozilla.org/api/v5)
 * using Petal's configured OkHttp client and Kotlin Coroutines.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.extensions

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Metadata model for an add-on discovered from Mozilla AMO API.
 */
data class RemoteExtension(
    val id: Long,
    val guid: String,
    val title: String,
    val summary: String,
    val author: String,
    val version: String,
    val iconUrl: String?,
    val rating: Double,
    val ratingsCount: Int,
    val dailyUsers: Long,
    val categories: List<String>,
    val xpiDownloadUrl: String?,
    val permissions: List<String>
)

/**
 * Repository responsible for querying Mozilla AMO v5 Search API.
 */
object ExtensionRepository {
    private const val TAG = "ExtensionRepository"
    private const val BASE_AMO_URL = "https://addons.mozilla.org/api/v5/addons/search/"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Searches extensions from Mozilla Add-ons (AMO) API v5.
     *
     * @param query search keyword (e.g., "ublock", "dark", "privacy").
     * @param category optional category filter slug (e.g. "privacy-security", "appearance").
     * @param pageSize number of results per page (defaults to 25).
     * @return Result containing list of parsed [RemoteExtension] items.
     */
    suspend fun searchExtensions(
        query: String = "",
        category: String? = null,
        pageSize: Int = 25
    ): Result<List<RemoteExtension>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder(BASE_AMO_URL)
                .append("?type=extension")
                .append("&page_size=").append(pageSize)

            if (query.isNotBlank()) {
                urlBuilder.append("&q=").append(URLEncoder.encode(query.trim(), "UTF-8"))
            }

            if (!category.isNullOrBlank() && category != "all") {
                urlBuilder.append("&category=").append(URLEncoder.encode(category.trim(), "UTF-8"))
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; PetalBrowser/2.4)")
                .header("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    RuntimeException("AMO API returned HTTP error code ${response.code}: ${response.message}")
                )
            }

            val bodyString = response.body?.string()
                ?: return@withContext Result.failure(RuntimeException("Empty response body from AMO API"))

            val json = JSONObject(bodyString)
            val resultsArray = json.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<RemoteExtension>()

            val systemLang = Locale.getDefault().language.lowercase()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L)
                val guid = item.optString("guid", "")

                val nameObj = item.optJSONObject("name")
                val title = resolveLocalizedText(nameObj, systemLang)
                    ?: item.optString("name", "Untitled Extension")

                val summaryObj = item.optJSONObject("summary")
                val summary = resolveLocalizedText(summaryObj, systemLang)
                    ?: item.optString("summary", "")

                // Authors
                val authorsArray = item.optJSONArray("authors")
                val author = if (authorsArray != null && authorsArray.length() > 0) {
                    authorsArray.optJSONObject(0)?.optString("name", "Unknown Author") ?: "Unknown Author"
                } else {
                    "Unknown Author"
                }

                val iconUrl = item.optString("icon_url").takeIf { it.isNotBlank() }

                // Ratings
                val ratingsObj = item.optJSONObject("ratings")
                val rating = ratingsObj?.optDouble("average", 0.0) ?: 0.0
                val ratingsCount = ratingsObj?.optInt("count", 0) ?: 0
                val dailyUsers = item.optLong("average_daily_users", 0L)

                // Categories
                val categoriesList = mutableListOf<String>()
                val catArray = item.optJSONArray("categories")
                if (catArray != null) {
                    for (c in 0 until catArray.length()) {
                        catArray.optString(c).takeIf { it.isNotBlank() }?.let { categoriesList.add(it) }
                    }
                }

                // Current Version & .xpi URL
                val currentVersion = item.optJSONObject("current_version")
                val versionStr = currentVersion?.optString("version", "1.0.0") ?: "1.0.0"
                val fileObj = currentVersion?.optJSONObject("file")
                val xpiDownloadUrl = fileObj?.optString("url").takeIf { !it.isNullOrBlank() }

                // Permissions
                val permissionsList = mutableListOf<String>()
                val permArray = fileObj?.optJSONArray("permissions")
                if (permArray != null) {
                    for (p in 0 until permArray.length()) {
                        permArray.optString(p).takeIf { it.isNotBlank() }?.let { permissionsList.add(it) }
                    }
                }

                list.add(
                    RemoteExtension(
                        id = id,
                        guid = guid,
                        title = cleanHtml(title),
                        summary = cleanHtml(summary),
                        author = author,
                        version = versionStr,
                        iconUrl = iconUrl,
                        rating = rating,
                        ratingsCount = ratingsCount,
                        dailyUsers = dailyUsers,
                        categories = categoriesList,
                        xpiDownloadUrl = xpiDownloadUrl,
                        permissions = permissionsList
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search extensions from AMO", e)
            Result.failure(e)
        }
    }

    private fun resolveLocalizedText(obj: JSONObject?, preferredLang: String): String? {
        if (obj == null) return null
        return when {
            obj.has(preferredLang) -> obj.optString(preferredLang)
            obj.has("en-US") -> obj.optString("en-US")
            obj.has("en") -> obj.optString("en")
            obj.keys().hasNext() -> obj.optString(obj.keys().next())
            else -> null
        }
    }

    private fun cleanHtml(raw: String): String {
        return raw.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .trim()
    }
}
