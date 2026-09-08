/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.download

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * DownloadFileNameResolver
 * ─────────────────────────────────────────────────────────────────────────
 * Context-menu download actions (save image / save link / save video / save
 * audio) only ever have a raw URL in hand - unlike [org.mozilla.geckoview.WebResponse]
 * from GeckoView's onExternalResponse, there is no live HTTP response object
 * carrying real Content-Disposition/Content-Type headers.
 *
 * [PetalDownloadEngine] hands the destination filename to Fetch2 up front,
 * before the download connection opens, and never renames the file
 * afterward - so guessing the filename from the URL alone (no headers) is
 * what previously produced wrong/missing extensions for any URL that
 * didn't end in a clean "name.ext" pattern (signed CDN URLs, dynamic
 * endpoints, etc).
 *
 * This does a cheap async HEAD request to read the real Content-Disposition
 * and Content-Type before the download is enqueued, then falls back to the
 * existing URL-only guess if the request fails or times out - so a slow or
 * blocked HEAD request never blocks or breaks the download itself.
 */
object DownloadFileNameResolver {

    private const val TAG = "DownloadFileNameResolver"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Resolves the best-known filename for [url] and hands it to [onResolved] on the
     * main thread. Always calls [onResolved] exactly once - with the real
     * server-provided name when the HEAD request succeeds, or the existing
     * URL-only guess (optionally seeded with [fallbackMimeType], e.g. "video/mp4"
     * for a video element whose URL has no extension) if it doesn't.
     */
    @JvmStatic
    fun resolve(
        scope: CoroutineScope,
        url: String,
        fallbackMimeType: String? = null,
        onResolved: (fileName: String, mimeType: String?) -> Unit
    ) {
        val fallbackName = com.petal.browser.unit.HelperUnit.resolveFileName(url, null, fallbackMimeType)
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            // data:/blob:/file: URIs etc. - no network request possible, use the guess as-is.
            onResolved(fallbackName, fallbackMimeType)
            return
        }
        scope.launch(Dispatchers.Main) {
            val resolved = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(url).head().build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        val contentDisposition = response.header("Content-Disposition")
                        val contentType = response.header("Content-Type")
                        val name = SafeDownloadValues.fileName(url, contentDisposition, contentType ?: fallbackMimeType)
                        name to contentType
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "HEAD request failed for $url, falling back to URL-only guess: ${e.message}")
                    null
                }
            }
            if (resolved != null) {
                onResolved(resolved.first, resolved.second)
            } else {
                onResolved(fallbackName, fallbackMimeType)
            }
        }
    }
}
