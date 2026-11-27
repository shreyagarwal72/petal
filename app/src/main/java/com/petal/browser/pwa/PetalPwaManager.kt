package com.petal.browser.pwa

import android.app.Activity
import com.petal.browser.activity.MainActivity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.webkit.JavascriptInterface
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.button.MaterialButton
import com.petal.browser.R
import com.petal.browser.browser.AlbumController
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.database.FaviconHelper
import com.petal.browser.engine.gecko.PetalEngineBridge
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.logger.PetalAppLogger
import com.petal.browser.ui.components.PetalAlertDialogBuilder
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * PetalPwaManager
 * Dynamic Progressive Web App (PWA) manager supporting PetalTabViewController (GeckoView engine),
 * providing manifest detection & parsing, high-fidelity app icon extraction matching
 * official Firefox for Android, offline webpage web archive saving, and standalone Web App launching.
 */
class PetalPwaManager(
    private val context: Context,
    private val albumController: AlbumController?,
    private var promptListener: PwaInstallPromptListener?
) {

    class PwaManifest {
        var name: String = ""
        var shortName: String = ""
        var startUrl: String = ""
        var display: String = "standalone"
        var themeColor: String = "#FFFFFF"
        var backgroundColor: String = "#FFFFFF"
        var iconUrl: String = ""
        var shortcuts: MutableList<PwaShortcut> = ArrayList()
    }

    class PwaShortcut {
        var name: String = ""
        var url: String = ""
        var iconUrl: String = ""
    }

    fun interface PwaInstallPromptListener {
        fun onPwaDetected(manifest: PwaManifest)
    }

    companion object {
        private const val TAG = "PetalPwaManager"
        private const val JS_INTERFACE_NAME = "PetalPwaInterface"

        private val sPwaExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        private val sManifestCache: MutableMap<String, PwaManifest> = ConcurrentHashMap()
        private val sPrivateManifestCache: MutableMap<String, PwaManifest> = ConcurrentHashMap()

        @Volatile
        @JvmStatic
        var currentManifest: PwaManifest? = null
            private set

        @Volatile
        private var sPromptListener: PwaInstallPromptListener? = null

        @JvmStatic
        fun getLatestManifest(): PwaManifest? = currentManifest

        private fun createGeckoFetchClient(): GeckoViewFetchClient? {
            val ctx = com.petal.browser.PetalApplication.instance?.applicationContext ?: return null
            return runCatching {
                GeckoViewFetchClient(ctx, PetalGeckoRuntime.getOrCreate(ctx))
            }.getOrNull()
        }

        private data class FetchedPayload(val finalUrl: String, val bytes: ByteArray)

        private fun fetchBytes(
            urlStr: String,
            accept: String,
            userAgent: String,
            isPrivate: Boolean,
            timeoutMs: Int = 5000
        ): FetchedPayload? {
            val geckoClient = createGeckoFetchClient()
            if (geckoClient != null) {
                return try {
                    val headers = MutableHeaders().apply {
                        append("Accept", accept)
                        append("User-Agent", userAgent)
                    }
                    val request = Request(
                        url = urlStr,
                        method = Request.Method.GET,
                        headers = headers,
                        connectTimeout = timeoutMs.toLong() to java.util.concurrent.TimeUnit.MILLISECONDS,
                        readTimeout = timeoutMs.toLong() to java.util.concurrent.TimeUnit.MILLISECONDS,
                        redirect = Request.Redirect.FOLLOW,
                        private = isPrivate
                    )
                    val response = geckoClient.fetch(request)
                    try {
                        if (response.status in 200..399) {
                            val bytes = response.body.useStream { it.readBytes() }
                            FetchedPayload(response.url.ifBlank { urlStr }, bytes)
                        } else null
                    } finally {
                        runCatching { response.body.close() }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Gecko PWA fetch note ($urlStr): ${e.message}")
                    null
                }
            }
            if (isPrivate) return null
            var conn: HttpURLConnection? = null
            return try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", accept)
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = true
                }
                conn.connect()
                if (conn.responseCode in 200..399) {
                    val finalUrl = conn.url?.toString() ?: urlStr
                    val bytes = conn.inputStream.use { it.readBytes() }
                    FetchedPayload(finalUrl, bytes)
                } else null
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

        @JvmStatic
        @JvmOverloads
        fun onManifestDiscovered(
            manifestUrl: String?,
            pageUrl: String?,
            pageTitle: String?,
            isPrivate: Boolean = false
        ) {
            if (manifestUrl.isNullOrBlank()) return
            sPwaExecutor.execute {
                try {
                    val payload = fetchBytes(
                        urlStr = manifestUrl,
                        accept = "application/manifest+json, application/json, text/plain, */*",
                        userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:161.0) Gecko/161.0 Firefox/161.0",
                        isPrivate = isPrivate,
                        timeoutMs = 6000
                    ) ?: return@execute
                    val jsonStr = payload.bytes.toString(Charsets.UTF_8).trim()
                    if (jsonStr.isNotEmpty()) {
                        val json = JSONObject(jsonStr)
                        val manifest = parseManifest(json, manifestUrl, pageUrl, pageTitle)
                        if (manifest != null) {
                            val targetCache = if (isPrivate) sPrivateManifestCache else sManifestCache
                            if (!isPrivate) {
                                currentManifest = manifest
                            }
                            if (!pageUrl.isNullOrEmpty()) {
                                targetCache[pageUrl] = manifest
                            }
                            targetCache[manifestUrl] = manifest
                            Log.d(TAG, "PWA manifest discovered & parsed for ${pageUrl ?: manifestUrl}: name=${manifest.name}, display=${manifest.display}")

                            val prompt = sPromptListener
                            if (prompt != null && ("standalone".equals(manifest.display, ignoreCase = true) || "fullscreen".equals(manifest.display, ignoreCase = true))) {
                                Handler(Looper.getMainLooper()).post {
                                    try {
                                        prompt.onPwaDetected(manifest)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error notifying PWA prompt listener", e)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch PWA manifest from $manifestUrl: ${e.message}")
                }
            }
        }

        @JvmStatic
        fun parseManifest(json: JSONObject?, manifestUrl: String?, pageUrl: String?, pageTitle: String?): PwaManifest? {
            if (json == null) return null
            return try {
                val manifest = PwaManifest().apply {
                    name = json.optString("name", "")
                    if (name.isEmpty() && !pageTitle.isNullOrEmpty()) {
                        name = pageTitle
                    }
                    shortName = json.optString("short_name", name)
                    val rawStartUrl = json.optString("start_url", "")
                    if (rawStartUrl.isNotEmpty()) {
                        startUrl = resolveUrl(manifestUrl ?: pageUrl, rawStartUrl)
                    } else if (!pageUrl.isNullOrEmpty()) {
                        startUrl = pageUrl
                    }
                    display = json.optString("display", "standalone")
                    themeColor = json.optString("theme_color", "#FFFFFF")
                    backgroundColor = json.optString("background_color", "#FFFFFF")

                    if (json.has("icons")) {
                        val icons = json.getJSONArray("icons")
                        if (icons.length() > 0) {
                            var bestIcon = ""
                            var bestSize = 0
                            for (i in 0 until icons.length()) {
                                val ic = icons.getJSONObject(i)
                                val src = ic.optString("src", "")
                                val sizes = ic.optString("sizes", "0x0")
                                var sizeVal = 0
                                try {
                                    if (sizes.contains("x")) {
                                        sizeVal = sizes.split("x")[0].trim().toInt()
                                    }
                                } catch (_: Exception) {}
                                if (bestIcon.isEmpty() || sizeVal >= bestSize) {
                                    bestSize = sizeVal
                                    bestIcon = src
                                }
                            }
                            if (bestIcon.isEmpty()) {
                                bestIcon = icons.getJSONObject(icons.length() - 1).optString("src", "")
                            }
                            if (bestIcon.isNotEmpty()) {
                                iconUrl = resolveUrl(manifestUrl ?: pageUrl, bestIcon)
                            }
                        }
                    }

                    if (json.has("shortcuts")) {
                        val rawShortcuts = json.getJSONArray("shortcuts")
                        for (i in 0 until rawShortcuts.length()) {
                            val sc = rawShortcuts.getJSONObject(i)
                            val shortcut = PwaShortcut().apply {
                                name = sc.optString("name", sc.optString("short_name", ""))
                                val scUrl = sc.optString("url", "")
                                if (scUrl.isNotEmpty()) {
                                    url = resolveUrl(manifestUrl ?: pageUrl, scUrl)
                                }
                                if (sc.has("icons")) {
                                    val scIcons = sc.getJSONArray("icons")
                                    if (scIcons.length() > 0) {
                                        iconUrl = resolveUrl(manifestUrl ?: pageUrl, scIcons.getJSONObject(0).optString("src", ""))
                                    }
                                }
                            }
                            shortcuts.add(shortcut)
                        }
                    }
                }
                manifest
            } catch (e: Exception) {
                Log.w(TAG, "Error in parseManifest: ${e.message}")
                null
            }
        }

        @JvmStatic
        fun parseManifest(jsonStr: String?, manifestUrl: String?, pageUrl: String?, pageTitle: String?): PwaManifest? {
            if (jsonStr.isNullOrBlank()) return null
            return try {
                parseManifest(JSONObject(jsonStr), manifestUrl, pageUrl, pageTitle)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse manifest JSON string: ${e.message}")
                null
            }
        }

        @JvmStatic
        fun resolveUrl(baseUrl: String?, relativeOrAbsoluteUrl: String?): String {
            if (relativeOrAbsoluteUrl.isNullOrBlank()) {
                return baseUrl ?: ""
            }
            val trimmed = relativeOrAbsoluteUrl.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed
            }
            if (baseUrl.isNullOrEmpty()) {
                return trimmed
            }
            return try {
                val base = URI(baseUrl)
                base.resolve(trimmed).toString()
            } catch (_: Exception) {
                try {
                    val base = URL(baseUrl)
                    URL(base, trimmed).toString()
                } catch (_: Exception) {
                    trimmed
                }
            }
        }

        private fun discoverManifestAndIcons(
            pageUrl: String?,
            manifest: PwaManifest,
            iconCandidates: MutableList<String>,
            isPrivate: Boolean = false
        ) {
            if (pageUrl == null || !pageUrl.startsWith("http")) return
            try {
                val payload = fetchBytes(
                    urlStr = pageUrl,
                    accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:161.0) Gecko/161.0 Firefox/161.0",
                    isPrivate = isPrivate,
                    timeoutMs = 4000
                ) ?: return

                val actualUrl = payload.finalUrl
                val rawHtml = payload.bytes.toString(Charsets.UTF_8)
                val headEnd = rawHtml.indexOf("</head>", ignoreCase = true)
                val html = if (headEnd >= 0) rawHtml.substring(0, headEnd + 7) else rawHtml.take(32_768)

                // 1. Check title
                val titlePattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
                val titleMatcher = titlePattern.matcher(html)
                if (titleMatcher.find()) {
                    val extractedTitle = titleMatcher.group(1)?.trim() ?: ""
                    if (extractedTitle.isNotEmpty() && manifest.name.isEmpty()) {
                        manifest.name = extractedTitle
                        manifest.shortName = extractedTitle
                    }
                }

                // 2. Check manifest link
                var manifestPattern = Pattern.compile("<link[^>]+rel=[\"']manifest[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                var manifestMatcher = manifestPattern.matcher(html)
                if (!manifestMatcher.find()) {
                    manifestPattern = Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"'][^>]*rel=[\"']manifest[\"']", Pattern.CASE_INSENSITIVE)
                    manifestMatcher = manifestPattern.matcher(html)
                }
                if (manifestMatcher.find()) {
                    val manifestHref = manifestMatcher.group(1)
                    if (manifestHref != null) {
                        val manifestResolvedUrl = resolveUrl(actualUrl, manifestHref)
                        parseManifestFromNetwork(manifestResolvedUrl, manifest, iconCandidates, isPrivate)
                    }
                }

                // 3. Apple Touch Icons
                val applePattern = Pattern.compile("<link[^>]+rel=[\"']apple-touch-icon(?:-precomposed)?[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val appleMatcher = applePattern.matcher(html)
                while (appleMatcher.find()) {
                    appleMatcher.group(1)?.let {
                        val resolved = resolveUrl(actualUrl, it)
                        if (!iconCandidates.contains(resolved)) iconCandidates.add(resolved)
                    }
                }

                // 4. Standard Icons
                val iconPattern = Pattern.compile("<link[^>]+rel=[\"'](?:shortcut )?icon[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val iconMatcher = iconPattern.matcher(html)
                while (iconMatcher.find()) {
                    iconMatcher.group(1)?.let {
                        val resolved = resolveUrl(actualUrl, it)
                        if (!iconCandidates.contains(resolved)) iconCandidates.add(resolved)
                    }
                }

                // 5. Theme Color
                val themePattern = Pattern.compile("<meta[^>]+name=[\"']theme-color[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val themeMatcher = themePattern.matcher(html)
                if (themeMatcher.find()) {
                    val color = themeMatcher.group(1)?.trim() ?: ""
                    if (color.isNotEmpty() && (manifest.themeColor.isEmpty() || "#FFFFFF".equals(manifest.themeColor, ignoreCase = true))) {
                        manifest.themeColor = color
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Network discovery note: ${e.message}")
            }
        }

        private fun parseManifestFromNetwork(
            manifestUrl: String,
            manifest: PwaManifest,
            iconCandidates: MutableList<String>,
            isPrivate: Boolean = false
        ) {
            try {
                val payload = fetchBytes(
                    urlStr = manifestUrl,
                    accept = "application/manifest+json, application/json, text/plain, */*",
                    userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:161.0) Gecko/161.0 Firefox/161.0",
                    isPrivate = isPrivate,
                    timeoutMs = 4000
                ) ?: return

                val json = JSONObject(payload.bytes.toString(Charsets.UTF_8))
                if (json.has("name") && manifest.name.isEmpty()) {
                    manifest.name = json.optString("name", "")
                }
                if (json.has("short_name") && manifest.shortName.isEmpty()) {
                    manifest.shortName = json.optString("short_name", manifest.name)
                }
                if (json.has("start_url") && manifest.startUrl.isEmpty()) {
                    manifest.startUrl = resolveUrl(manifestUrl, json.optString("start_url", ""))
                }
                if (json.has("display")) {
                    manifest.display = json.optString("display", "standalone")
                }
                if (json.has("theme_color")) {
                    manifest.themeColor = json.optString("theme_color", "#FFFFFF")
                }
                if (json.has("background_color")) {
                    manifest.backgroundColor = json.optString("background_color", "#FFFFFF")
                }

                if (json.has("icons")) {
                    val icons = json.getJSONArray("icons")
                    for (i in 0 until icons.length()) {
                        val ic = icons.getJSONObject(i)
                        val src = ic.optString("src", "")
                        if (src.isNotEmpty()) {
                            val resolved = resolveUrl(manifestUrl, src)
                            if (!iconCandidates.contains(resolved)) {
                                iconCandidates.add(0, resolved)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Manifest fetch note: ${e.message}")
            }
        }

        @JvmStatic
        fun createAdaptiveAppIcon(context: Context, rawIcon: Bitmap?, themeColorHex: String?): Bitmap {
            val targetSize = 192
            val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            if (rawIcon == null || rawIcon.isRecycled) {
                val appIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
                if (appIcon != null) {
                    return if (appIcon.isRecycled) output else (appIcon.copy(appIcon.config ?: Bitmap.Config.ARGB_8888, false) ?: output)
                }
                return output
            }

            var safeRawIcon: Bitmap? = null
            try {
                safeRawIcon = rawIcon.copy(rawIcon.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (_: Throwable) {
                safeRawIcon = rawIcon
            }
            val effectiveRawIcon = if (safeRawIcon == null || safeRawIcon.isRecycled) rawIcon else safeRawIcon

            var bgColor = Color.TRANSPARENT
            if (!themeColorHex.isNullOrBlank() && !themeColorHex.equals("#FFFFFF", ignoreCase = true)) {
                try {
                    bgColor = Color.parseColor(themeColorHex.trim())
                } catch (_: Exception) {}
            }

            val isHighRes = effectiveRawIcon.width >= 96 && effectiveRawIcon.height >= 96
            val hasAlpha = effectiveRawIcon.hasAlpha()

            var isFullyOpaqueArtwork = false
            if (!hasAlpha && isHighRes) {
                isFullyOpaqueArtwork = true
            } else if (isHighRes) {
                try {
                    val c1 = effectiveRawIcon.getPixel(2, 2)
                    val c2 = effectiveRawIcon.getPixel(effectiveRawIcon.width - 3, 2)
                    val c3 = effectiveRawIcon.getPixel(2, effectiveRawIcon.height - 3)
                    val c4 = effectiveRawIcon.getPixel(effectiveRawIcon.width - 3, effectiveRawIcon.height - 3)
                    if (Color.alpha(c1) == 255 && Color.alpha(c2) == 255 && Color.alpha(c3) == 255 && Color.alpha(c4) == 255) {
                        isFullyOpaqueArtwork = true
                    }
                } catch (_: Exception) {}
            }

            val cornerRadius = targetSize * 0.22f
            val rect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
            val path = Path()
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            if (isFullyOpaqueArtwork) {
                canvas.clipPath(path)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(effectiveRawIcon, null, Rect(0, 0, targetSize, targetSize), paint)
                return output
            }

            if (bgColor == Color.TRANSPARENT || bgColor == Color.WHITE) {
                bgColor = Color.parseColor("#F5F5F7")
            }

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            val iconPadding = (targetSize * 0.16f).toInt()
            val destRect = Rect(iconPadding, iconPadding, targetSize - iconPadding, targetSize - iconPadding)
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(effectiveRawIcon, null, destRect, iconPaint)

            return output
        }

        private fun fetchBitmap(urlStr: String?, isPrivate: Boolean = false): Bitmap? {
            if (urlStr.isNullOrEmpty()) return null
            val payload = fetchBytes(
                urlStr = urlStr,
                accept = "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:161.0) Gecko/161.0 Firefox/161.0",
                isPrivate = isPrivate,
                timeoutMs = 4000
            ) ?: return null
            return runCatching { BitmapFactory.decodeByteArray(payload.bytes, 0, payload.bytes.size) }.getOrNull()
        }
    }

    private val tabViewController: PetalTabViewController? =
        if (albumController is PetalTabViewController) albumController else null

    init {
        if (promptListener != null) {
            sPromptListener = promptListener
        }
    }

    fun detectPwaManifest() {
        val js = "(function() {" +
                "   try {" +
                "       var manifestLink = document.querySelector('link[rel=\"manifest\"]');" +
                "       var manifestUrl = manifestLink ? manifestLink.href : '';" +
                "       var appleIcon = document.querySelector('link[rel=\"apple-touch-icon\"]') || document.querySelector('link[rel=\"apple-touch-icon-precomposed\"]') || document.querySelector('link[rel=\"icon\"][sizes=\"192x192\"]') || document.querySelector('link[rel=\"icon\"][sizes=\"512x512\"]') || document.querySelector('link[rel=\"icon\"]');" +
                "       var appleIconUrl = appleIcon ? appleIcon.href : '';" +
                "       var themeColorMeta = document.querySelector('meta[name=\"theme-color\"]');" +
                "       var themeColor = themeColorMeta ? themeColorMeta.content : '';" +
                "       if (manifestUrl) {" +
                "           fetch(manifestUrl, { credentials: 'omit' })" +
                "               .then(function(res) { return res.json(); })" +
                "               .then(function(manifest) {" +
                "                   if (appleIconUrl && (!manifest.icons || manifest.icons.length === 0)) {" +
                "                       manifest.icons = [{ src: appleIconUrl, sizes: '192x192' }];" +
                "                   }" +
                "                   if (themeColor && !manifest.theme_color) {" +
                "                       manifest.theme_color = themeColor;" +
                "                   }" +
                "                   if (window." + JS_INTERFACE_NAME + ") {" +
                "                       window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(manifest), manifestUrl);" +
                "                   }" +
                "               })" +
                "               .catch(function(err) {" +
                "                   var fallback = {" +
                "                       name: document.title || ''," +
                "                       short_name: document.title || ''," +
                "                       start_url: window.location.href," +
                "                       display: 'standalone'," +
                "                       theme_color: themeColor," +
                "                       icons: appleIconUrl ? [{ src: appleIconUrl, sizes: '192x192' }] : []" +
                "                   };" +
                "                   if (window." + JS_INTERFACE_NAME + ") {" +
                "                       window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(fallback), window.location.href);" +
                "                   }" +
                "               });" +
                "       } else {" +
                "           var isStandalone = document.querySelector('meta[name=\"mobile-web-app-capable\"]') || document.querySelector('meta[name=\"apple-mobile-web-app-capable\"]');" +
                "           var fallback = {" +
                "               name: document.title || ''," +
                "               short_name: document.title || ''," +
                "               start_url: window.location.href," +
                "               display: isStandalone ? 'standalone' : 'browser'," +
                "               theme_color: themeColor," +
                "               icons: appleIconUrl ? [{ src: appleIconUrl, sizes: '192x192' }] : []" +
                "           };" +
                "           if (window." + JS_INTERFACE_NAME + ") {" +
                "               window." + JS_INTERFACE_NAME + ".onManifestParsed(JSON.stringify(fallback), window.location.href);" +
                "           }" +
                "       }" +
                "       if (!navigator.share && window." + JS_INTERFACE_NAME + ") {" +
                "           navigator.share = function(data) {" +
                "               return new Promise(function(resolve, reject) {" +
                "                   try {" +
                "                       window." + JS_INTERFACE_NAME + ".share(data ? (data.title || '') : '', data ? (data.text || '') : '', data ? (data.url || '') : '');" +
                "                       resolve();" +
                "                   } catch(e) {" +
                "                       reject(e);" +
                "                   }" +
                "               });" +
                "           };" +
                "       }" +
                "   } catch(e) {}" +
                "})();"

        tabViewController?.evaluateJavascript(js, null)
    }

    fun installCurrentPwa(activity: Activity?) {
        if (activity == null) return

        var targetController: AlbumController? = this.albumController
        if (targetController == null && activity is MainActivity) {
            targetController = activity.currentAlbumController
        }

        var pageUrl = targetController?.url
        if (pageUrl.isNullOrBlank() || "about:blank".equals(pageUrl.trim(), ignoreCase = true)) {
            if (activity is MainActivity) {
                pageUrl = activity.resolveCurrentTabUrl()
            }
        }

        if (pageUrl.isNullOrBlank() || "about:blank".equals(pageUrl.trim(), ignoreCase = true) || pageUrl.startsWith("petal://")) {
            activity.runOnUiThread { PetalToast.show(activity, R.string.pwa_install_empty_page) }
            return
        }

        val finalController = targetController
        val finalPageUrl = pageUrl
        val isPrivateSession = (finalController is PetalTabViewController && finalController.isIncognito()) ||
            (activity is MainActivity && activity.viewModel.activeTab.value?.content?.private == true)

        Thread {
            try {
                val workingManifest = PwaManifest()
                val pageHost = runCatching { URI(finalPageUrl).host.orEmpty() }.getOrDefault("")
                var activeManifest = if (isPrivateSession) sPrivateManifestCache[finalPageUrl] else sManifestCache[finalPageUrl]
                if (activeManifest == null && !isPrivateSession) {
                    val candidate = currentManifest
                    val candidateHost = runCatching { candidate?.startUrl?.let { URI(it).host } }.getOrNull().orEmpty()
                    if (candidate != null && (candidateHost.isEmpty() || candidateHost.equals(pageHost, ignoreCase = true))) {
                        activeManifest = candidate
                    }
                }
                if (activeManifest != null) {
                    workingManifest.name = activeManifest.name
                    workingManifest.shortName = activeManifest.shortName
                    workingManifest.startUrl = activeManifest.startUrl
                    workingManifest.display = activeManifest.display
                    workingManifest.themeColor = activeManifest.themeColor
                    workingManifest.backgroundColor = activeManifest.backgroundColor
                    workingManifest.iconUrl = activeManifest.iconUrl
                }

                val iconCandidates = ArrayList<String>()
                if (workingManifest.iconUrl.isNotEmpty()) {
                    iconCandidates.add(resolveUrl(finalPageUrl, workingManifest.iconUrl))
                }

                discoverManifestAndIcons(finalPageUrl, workingManifest, iconCandidates, isPrivateSession)

                var host = ""
                try {
                    host = URL(finalPageUrl).host ?: ""
                } catch (_: Exception) {}

                if (host.isNotEmpty()) {
                    val origin = (if (finalPageUrl.startsWith("https")) "https://" else "http://") + host
                    iconCandidates.add("$origin/apple-touch-icon.png")
                    iconCandidates.add("$origin/apple-touch-icon-precomposed.png")
                    iconCandidates.add("$origin/favicon.ico")
                    if (!isPrivateSession) {
                        iconCandidates.add("https://www.google.com/s2/favicons?domain=$host&sz=256")
                    }
                }

                var rawTitle: String? = null
                if (workingManifest.shortName.isNotEmpty()) {
                    rawTitle = workingManifest.shortName
                } else if (workingManifest.name.isNotEmpty()) {
                    rawTitle = workingManifest.name
                }
                if (rawTitle.isNullOrEmpty()) {
                    rawTitle = if (!finalController?.title.isNullOrEmpty()) finalController?.title else PetalAndroidUtils.domain(finalPageUrl)
                }
                if (rawTitle.isNullOrEmpty() && activity is MainActivity) {
                    rawTitle = activity.viewModel.activeTitle.value.takeIf { it.isNotBlank() }
                }
                if (rawTitle.isNullOrEmpty()) {
                    rawTitle = "Web App"
                }
                val initialTitle = rawTitle

                var candidateUrl = finalPageUrl
                if (workingManifest.startUrl.isNotEmpty()) {
                    candidateUrl = resolveUrl(finalPageUrl, workingManifest.startUrl)
                }
                val targetUrl = candidateUrl

                var rawBitmap: Bitmap? = null
                for (candidate in iconCandidates) {
                    if (candidate.isNotEmpty()) {
                        rawBitmap = fetchBitmap(candidate, isPrivateSession)
                        if (rawBitmap != null) break
                    }
                }

                if (rawBitmap == null && finalController is PetalTabViewController) {
                    rawBitmap = finalController.favicon
                }
                if (rawBitmap == null && activity is MainActivity) {
                    rawBitmap = activity.viewModel.activeTab.value?.content?.icon
                }
                if (rawBitmap == null && !isPrivateSession) {
                    val helper = FaviconHelper(activity)
                    rawBitmap = helper.getFavicon(finalPageUrl)
                }

                val themeColorHex = if (workingManifest.themeColor.isNotEmpty()) workingManifest.themeColor else "#FFFFFF"
                val finalAdaptiveIcon = createAdaptiveAppIcon(activity, rawBitmap, themeColorHex)

                activity.runOnUiThread {
                    if (activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)) {
                        return@runOnUiThread
                    }
                    showInstallConfirmationDialog(activity, initialTitle, targetUrl, finalAdaptiveIcon, themeColorHex, workingManifest)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing PWA install", e)
                activity.runOnUiThread { PetalToast.show(activity, R.string.pwa_install_failed) }
            }
        }.start()
    }

    private fun showInstallConfirmationDialog(
        activity: Activity,
        defaultTitle: String,
        targetUrl: String,
        adaptiveIcon: Bitmap,
        themeColorHex: String,
        manifest: PwaManifest
    ) {
        try {
            val inflater = LayoutInflater.from(activity)
            val dialogView = inflater.inflate(R.layout.dialog_pwa_install, null)

            val iconView = dialogView.findViewById<ImageView>(R.id.dialog_pwa_icon)
            val headerLabel = dialogView.findViewById<TextView>(R.id.dialog_pwa_header_label)
            val titleView = dialogView.findViewById<EditText>(R.id.dialog_pwa_title)
            val urlView = dialogView.findViewById<TextView>(R.id.dialog_pwa_url)
            val descView = dialogView.findViewById<TextView>(R.id.dialog_pwa_description)
            val cancelButton = dialogView.findViewById<MaterialButton>(R.id.dialog_pwa_cancel)
            val installButton = dialogView.findViewById<MaterialButton>(R.id.dialog_pwa_install)

            val isStandalonePwa = !manifest.display.equals("browser", ignoreCase = true)

            headerLabel?.setText(if (isStandalonePwa) R.string.pwa_install_title else R.string.shortcut_add_to_homescreen)
            descView?.setText(if (isStandalonePwa) R.string.pwa_install_message else R.string.shortcut_install_message)
            installButton?.setText(if (isStandalonePwa) R.string.pwa_install_button else R.string.shortcut_add_button)

            if (iconView != null && !adaptiveIcon.isRecycled) {
                iconView.setImageBitmap(adaptiveIcon)
            }
            if (titleView != null) {
                titleView.setText(defaultTitle)
                titleView.setSelection(titleView.text.length)
            }
            if (urlView != null) {
                var domainText = PetalAndroidUtils.domain(targetUrl)
                if (domainText.isEmpty()) domainText = targetUrl
                urlView.text = domainText
            }

            val dialog = PetalAlertDialogBuilder(activity)
                .setView(dialogView)
                .create()

            cancelButton?.setOnClickListener { dialog.dismiss() }

            installButton?.setOnClickListener {
                var chosenTitle = defaultTitle
                if (titleView?.text != null) {
                    val input = titleView.text.toString().trim()
                    if (input.isNotEmpty()) {
                        chosenTitle = input
                    }
                }
                dialog.dismiss()
                performShortcutPinning(activity, chosenTitle, targetUrl, adaptiveIcon, themeColorHex, manifest)
            }

            dialog.show()
            PetalAndroidUtils.setupDialog(activity, dialog)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing install dialog, falling back to direct pinning", e)
            performShortcutPinning(activity, defaultTitle, targetUrl, adaptiveIcon, themeColorHex, manifest)
        }
    }

    private fun performShortcutPinning(
        activity: Activity,
        title: String,
        targetUrl: String,
        adaptiveIcon: Bitmap,
        themeColorHex: String,
        manifest: PwaManifest
    ) {
        Thread {
            try {
                val archiveDir = File(activity.filesDir, "offline_web_archives")
                if (!archiveDir.exists()) archiveDir.mkdirs()
                val filename = "archive_" + Math.abs(targetUrl.hashCode()) + ".pdf"
                val archiveFile = File(archiveDir, filename)
                val composeSession = (activity as? MainActivity)?.viewModel?.activeTab?.value?.engineState?.engineSession
                    ?.let { PetalEngineBridge.extractGeckoSession(it) }
                val canSaveOfflinePdf = tabViewController != null || composeSession != null

                activity.runOnUiThread {
                    if (tabViewController != null) {
                        var fos: FileOutputStream? = null
                        try {
                            fos = FileOutputStream(archiveFile)
                            tabViewController.printToPdf(fos) { success ->
                                if (java.lang.Boolean.TRUE == success) {
                                    PetalEngineStore.addOfflineArchive(activity, targetUrl, archiveFile.absolutePath, title)
                                } else {
                                    try { fos?.close() } catch (_: Throwable) {}
                                    try { archiveFile.delete() } catch (_: Throwable) {}
                                }
                            }
                        } catch (e: Exception) {
                            try { fos?.close() } catch (_: Throwable) {}
                            try { archiveFile.delete() } catch (_: Throwable) {}
                            PetalAppLogger.w(TAG, "Error printing tab to PDF for offline archive", e)
                        }
                    } else if (composeSession != null) {
                        try {
                            val pdfResult = composeSession.saveAsPdf()
                            pdfResult?.accept(
                                { pdfStream ->
                                    if (pdfStream != null) {
                                        Thread {
                                            try {
                                                pdfStream.use { inp ->
                                                    FileOutputStream(archiveFile).use { out -> inp.copyTo(out) }
                                                }
                                                PetalEngineStore.addOfflineArchive(activity, targetUrl, archiveFile.absolutePath, title)
                                            } catch (t: Throwable) {
                                                runCatching { archiveFile.delete() }
                                                PetalAppLogger.w(TAG, "Failed saving Compose offline archive PDF", t)
                                            }
                                        }.start()
                                    }
                                },
                                { err -> PetalAppLogger.w(TAG, "Compose saveAsPdf rejected", err) }
                            )
                        } catch (e: Exception) {
                            PetalAppLogger.w(TAG, "Error capturing Compose offline PDF", e)
                        }
                    }
                }

                val shortcutIntent = Intent("com.petal.browser.action.OPEN_PWA", Uri.parse(targetUrl)).apply {
                    component = ComponentName(activity, PetalPwaActivity::class.java)
                    putExtra(PetalPwaActivity.EXTRA_URL, targetUrl)
                    putExtra(PetalPwaActivity.EXTRA_TITLE, title)
                    putExtra(PetalPwaActivity.EXTRA_THEME_COLOR, themeColorHex)
                    putExtra(PetalPwaActivity.EXTRA_DISPLAY, manifest.display)
                    if (canSaveOfflinePdf) {
                        putExtra(PetalPwaActivity.EXTRA_OFFLINE_ARCHIVE, archiveFile.absolutePath)
                    }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                }

                var safeShortcutIcon: Bitmap? = null
                if (!adaptiveIcon.isRecycled) {
                    try {
                        safeShortcutIcon = adaptiveIcon.copy(adaptiveIcon.config ?: Bitmap.Config.ARGB_8888, false)
                    } catch (_: Throwable) {
                        safeShortcutIcon = adaptiveIcon
                    }
                }
                if (safeShortcutIcon == null || safeShortcutIcon.isRecycled) {
                    val defaultAppIcon = BitmapFactory.decodeResource(activity.resources, R.mipmap.ic_launcher)
                    safeShortcutIcon = if (defaultAppIcon != null && !defaultAppIcon.isRecycled) {
                        defaultAppIcon.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                    }
                }

                val iconCompat = IconCompat.createWithBitmap(safeShortcutIcon!!)
                val shortcutId = "pwa_" + Math.abs(targetUrl.hashCode())

                val pinShortcutInfo = ShortcutInfoCompat.Builder(activity, shortcutId)
                    .setShortLabel(title)
                    .setLongLabel(if (manifest.name.isNotEmpty()) manifest.name else title)
                    .setIcon(iconCompat)
                    .setIntent(shortcutIntent)
                    .build()

                var pinned = false
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(activity)) {
                    pinned = ShortcutManagerCompat.requestPinShortcut(activity, pinShortcutInfo, null)
                }
                if (!pinned) {
                    val addIntent = ShortcutManagerCompat.createShortcutResultIntent(activity, pinShortcutInfo)
                    if (addIntent != null) {
                        addIntent.action = "com.android.launcher.action.INSTALL_SHORTCUT"
                        activity.sendBroadcast(addIntent)
                        pinned = true
                    }
                }

                val successMsg = activity.getString(R.string.pwa_install_success, title)
                activity.runOnUiThread { PetalToast.show(activity, successMsg) }
            } catch (e: Exception) {
                Log.e(TAG, "Error pinning PWA shortcut", e)
                activity.runOnUiThread { PetalToast.show(activity, R.string.pwa_install_failed) }
            }
        }.start()
    }

    private inner class PwaJavascriptInterface {
        @JavascriptInterface
        fun onManifestParsed(jsonStr: String?, manifestUrl: String?) {
            try {
                if (jsonStr != null) {
                    val json = JSONObject(jsonStr)
                    val manifest = parseManifest(json, manifestUrl, manifestUrl, "")
                    if (manifest != null) {
                        currentManifest = manifest
                        if (!manifestUrl.isNullOrEmpty()) {
                            sManifestCache[manifestUrl] = manifest
                        }
                        if (promptListener != null && ("standalone".equals(manifest.display, ignoreCase = true) || "fullscreen".equals(manifest.display, ignoreCase = true))) {
                            (context as? Activity)?.runOnUiThread { promptListener?.onPwaDetected(manifest) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing PWA manifest JSON: ${e.message}")
            }
        }

        @JavascriptInterface
        fun share(title: String?, text: String?, url: String?) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                val shareContent = (if (!title.isNullOrEmpty()) "$title\n" else "") +
                        (if (!text.isNullOrEmpty()) "$text\n" else "") +
                        (if (!url.isNullOrEmpty()) url else "")
                putExtra(Intent.EXTRA_TEXT, shareContent.trim())
            }
            context.startActivity(Intent.createChooser(shareIntent, "Web Share"))
        }
    }
}
