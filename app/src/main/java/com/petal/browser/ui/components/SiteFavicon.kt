package com.petal.browser.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.petal.browser.engine.gecko.PetalEngineStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import mozilla.components.browser.icons.Icon as MozIcon
import mozilla.components.browser.icons.IconRequest
import java.util.Locale

private object SiteFaviconMemoryCache {
    private const val MAX_ENTRIES = 64
    private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {}

    fun get(host: String): Bitmap? {
        if (host.isEmpty()) return null
        val bmp = synchronized(cache) { cache.get(host) } ?: return null
        return if (bmp.isRecycled) {
            synchronized(cache) { cache.remove(host) }
            null
        } else {
            bmp
        }
    }

    fun put(host: String, bitmap: Bitmap) {
        if (host.isEmpty() || bitmap.isRecycled) return
        synchronized(cache) {
            cache.put(host, bitmap)
        }
    }
}

internal fun extractFaviconHost(url: String): String {
    if (url.isBlank()) return ""
    return try {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        if (scheme != "http" && scheme != "https") return ""
        uri.host.orEmpty().lowercase(Locale.US).removePrefix("www.")
    } catch (_: Throwable) {
        ""
    }
}

internal fun isHttpOrHttpsUrl(url: String): Boolean {
    return url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
}

/**
 * Resolves the site favicon for [url] with strict private-mode isolation:
 * 1. Never returns or caches favicons when [isPrivate] is true.
 * 2. Only resolves for http/https URLs.
 * 3. Uses [tabIcon] first (and caches it per host in memory), then in-memory host cache,
 *    then Mozilla's [BrowserIcons.loadIcon] (which also provides Mozilla's generated letter icon
 *    fallback), returning null so callers fall back to the globe icon if loading fails.
 */
@Composable
fun rememberSiteFavicon(
    url: String,
    tabIcon: Bitmap?,
    isPrivate: Boolean
): State<Bitmap?> {
    val context = LocalContext.current
    val host = extractFaviconHost(url)
    val initialBitmap = when {
        isPrivate || !isHttpOrHttpsUrl(url) -> null
        tabIcon != null && !tabIcon.isRecycled -> tabIcon
        else -> SiteFaviconMemoryCache.get(host)
    }

    return produceState(initialValue = initialBitmap, url, tabIcon, isPrivate) {
        if (isPrivate || !isHttpOrHttpsUrl(url)) {
            value = null
            return@produceState
        }

        if (tabIcon != null && !tabIcon.isRecycled) {
            SiteFaviconMemoryCache.put(host, tabIcon)
            value = tabIcon
            return@produceState
        }

        val cached = SiteFaviconMemoryCache.get(host)
        if (cached != null) {
            value = cached
        }

        var deferred: Deferred<MozIcon>? = null
        try {
            val icons = PetalEngineStore.getBrowserIcons(context)
            deferred = icons.loadIcon(IconRequest(url = url, isPrivate = isPrivate))
            val loaded = deferred.await()
            val bmp = loaded.bitmap
            if (!bmp.isRecycled) {
                if (loaded.source != MozIcon.Source.GENERATOR) {
                    SiteFaviconMemoryCache.put(host, bmp)
                }
                value = bmp
            }
        } catch (e: CancellationException) {
            deferred?.cancel()
            throw e
        } catch (_: Throwable) {
            if (value == null) {
                value = null
            }
        }
    }
}
