package com.petal.browser.account.mozilla

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class TokenServerResponse(
    val id: String,
    val key: String,
    val apiEndpoint: String,
    val durationSeconds: Long,
    val hashAlgorithm: String = "sha256"
)

data class BsoRecord(
    val id: String,
    val modified: Double = System.currentTimeMillis() / 1000.0,
    val payload: String,
    val sortindex: Int? = null,
    val ttl: Int? = null
)

sealed class SyncClientResult<out T> {
    data class Success<out T>(
        val data: T,
        val serverTimestamp: Double = 0.0,
        val backoffSeconds: Long = 0L
    ) : SyncClientResult<T>()

    data class Failure(
        val statusCode: Int,
        val errorMessage: String,
        val isAuthError: Boolean = (statusCode == 401 || statusCode == 403),
        val backoffSeconds: Long = 0L
    ) : SyncClientResult<Nothing>()
}

class MozillaSyncClient(
    private val tokenServerUrl: String = "https://token.services.mozilla.com/1.0/sync/1.5"
) {

    /**
     * Exchanges FxA OAuth access token for storage node endpoint and authentication keys.
     */
    @Volatile
    private var activeCredentials: TokenServerResponse? = null

    fun fetchStorageCredentials(accessToken: String, syncKey: String? = null): SyncClientResult<TokenServerResponse> {
        return try {
            val url = URL(tokenServerUrl)
            // TokenServer for Firefox Accounts 1.5 accepts OAuth Bearer tokens directly
            val authHeader = "Bearer $accessToken"

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)

            if (statusCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val response = TokenServerResponse(
                    id = json.optString("id", json.optString("uid", "user_storage")),
                    key = json.optString("key", "sync_master_key"),
                    apiEndpoint = json.optString("api_endpoint", "https://sync-1-5.sync.services.mozilla.com/1.5/"),
                    durationSeconds = json.optLong("duration", 3600L),
                    hashAlgorithm = json.optString("hashalg", "sha256")
                )
                activeCredentials = response
                SyncClientResult.Success(response, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "TokenServer returned status $statusCode"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(
                statusCode = -1,
                errorMessage = e.message ?: "Network error connecting to TokenServer"
            )
        }
    }

    /**
     * Queries last modified timestamps for each collection (bookmarks, tabs, history).
     */
    fun fetchCollectionTimestamps(
        apiEndpoint: String,
        authToken: String
    ): SyncClientResult<Map<String, Double>> {
        return try {
            val url = URL(apiEndpoint.trimEnd('/') + "/info/collections")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", resolveAuthHeader("GET", url, authToken))
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8_000
                readTimeout = 8_000
            }

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)

            if (statusCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val timestamps = mutableMapOf<String, Double>()
                json.keys().forEach { key ->
                    timestamps[key] = json.optDouble(key, 0.0)
                }
                SyncClientResult.Success(timestamps, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "HTTP $statusCode"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(-1, e.message ?: "Network error")
        }
    }

    /**
     * Fetches BSO records from a collection modified since [newerThan].
     */
    fun fetchCollectionRecords(
        apiEndpoint: String,
        collection: String,
        authToken: String,
        newerThan: Double? = null,
        limit: Int = 500
    ): SyncClientResult<List<BsoRecord>> {
        return try {
            val queryParams = mutableListOf("full=1", "limit=$limit")
            if (newerThan != null && newerThan > 0) {
                queryParams.add("newer=$newerThan")
            }
            val queryString = queryParams.joinToString("&")
            val fullUrl = "${apiEndpoint.trimEnd('/')}/storage/$collection?$queryString"

            val url = URL(fullUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", resolveAuthHeader("GET", url, authToken))
                setRequestProperty("Accept", "application/json")
                connectTimeout = 12_000
                readTimeout = 15_000
            }

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)
            val serverTimestamp = conn.getHeaderField("X-Last-Modified")?.toDoubleOrNull() ?: 0.0

            if (statusCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.readText()
                reader.close()

                val records = mutableListOf<BsoRecord>()
                val array = JSONArray(responseText)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    records.add(
                        BsoRecord(
                            id = obj.optString("id"),
                            modified = obj.optDouble("modified", 0.0),
                            payload = obj.optString("payload", "{}"),
                            sortindex = if (obj.has("sortindex")) obj.getInt("sortindex") else null,
                            ttl = if (obj.has("ttl")) obj.getInt("ttl") else null
                        )
                    )
                }
                SyncClientResult.Success(records, serverTimestamp = serverTimestamp, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "HTTP $statusCode"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(-1, e.message ?: "Failed to fetch $collection records")
        }
    }

    /**
     * Uploads local BSO records to a Mozilla Sync collection in batches.
     */
    fun postCollectionRecords(
        apiEndpoint: String,
        collection: String,
        authToken: String,
        records: List<BsoRecord>
    ): SyncClientResult<List<String>> {
        if (records.isEmpty()) return SyncClientResult.Success(emptyList())

        return try {
            val fullUrl = "${apiEndpoint.trimEnd('/')}/storage/$collection"
            val url = URL(fullUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", resolveAuthHeader("POST", url, authToken))
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 20_000
            }

            val array = JSONArray()
            for (r in records) {
                array.put(JSONObject().apply {
                    put("id", r.id)
                    put("payload", r.payload)
                    r.sortindex?.let { put("sortindex", it) }
                    r.ttl?.let { put("ttl", it) }
                })
            }

            val writer = OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8)
            writer.write(array.toString())
            writer.flush()
            writer.close()

            val statusCode = conn.responseCode
            val backoff = extractBackoff(conn)
            val serverTimestamp = conn.getHeaderField("X-Last-Modified")?.toDoubleOrNull() ?: 0.0

            if (statusCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
                val responseText = reader.readText()
                reader.close()

                val json = JSONObject(responseText)
                val successIds = mutableListOf<String>()
                val successArr = json.optJSONArray("success")
                if (successArr != null) {
                    for (i in 0 until successArr.length()) {
                        successIds.add(successArr.getString(i))
                    }
                }
                SyncClientResult.Success(successIds, serverTimestamp = serverTimestamp, backoffSeconds = backoff)
            } else {
                val errorMsg = extractErrorStream(conn) ?: "HTTP $statusCode"
                SyncClientResult.Failure(statusCode, errorMsg, backoffSeconds = backoff)
            }
        } catch (e: Exception) {
            SyncClientResult.Failure(-1, e.message ?: "Failed to upload $collection records")
        }
    }

    private fun extractBackoff(conn: HttpURLConnection): Long {
        val backoffHeader = conn.getHeaderField("X-Backoff") ?: conn.getHeaderField("Retry-After")
        return backoffHeader?.toLongOrNull() ?: 0L
    }

    private fun extractErrorStream(conn: HttpURLConnection): String? {
        return try {
            conn.errorStream?.let { stream ->
                val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
                val txt = reader.readText()
                reader.close()
                txt
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateHawkHeader(id: String, key: String, method: String, url: URL): String {
        val ts = System.currentTimeMillis() / 1000L
        val nonce = java.util.UUID.randomUUID().toString().replace("-", "").take(10)
        val host = url.host
        val port = if (url.port > 0) url.port else (if (url.protocol.equals("https", true)) 443 else 80)
        val path = if (!url.query.isNullOrBlank()) "${url.path}?${url.query}" else url.path

        val normalized = "hawk.1.header\n$ts\n$nonce\n${method.uppercase()}\n$path\n$host\n$port\n\n\n"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val rawMac = mac.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val macBase64 = android.util.Base64.encodeToString(rawMac, android.util.Base64.NO_WRAP)

        return "Hawk id=\"$id\", ts=\"$ts\", nonce=\"$nonce\", mac=\"$macBase64\""
    }

    private fun resolveAuthHeader(method: String, url: URL, fallbackAuthToken: String): String {
        val creds = activeCredentials
        return if (creds != null && creds.key.isNotBlank()) {
            generateHawkHeader(creds.id, creds.key, method, url)
        } else {
            fallbackAuthToken
        }
    }
}
