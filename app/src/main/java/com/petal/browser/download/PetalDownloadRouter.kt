package com.petal.browser.download

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebSettings
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.media.sniffer.PetalMediaSniffer
import com.petal.browser.unit.ExternalDownloadManagerHelper
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalBackupManager
import com.petal.browser.view.PetalToast

/**
 * PetalDownloadRouter
 * ─────────────────────────────────────────────────────────────────────────
 * The single authoritative entry point for all downloads in Petal Browser.
 *
 * Directs all downloads through Mozilla's official Firefox/Fenix download pipeline
 * (Android Components feature-downloads / AbstractFetchDownloadService with
 * GeckoViewFetchClient and parallel Range segmenting), completely replacing the
 * legacy Android system DownloadManager.
 *
 * If the user configured an external download manager (1DM, ADM, AB Download Manager,
 * Navi, or Auto), requests are routed with full headers, cookies, referer, and user-agent.
 */
object PetalDownloadRouter {

    /** Real Firefox for Android format: "Android <release>" is part of the platform token. */
    @JvmField
    val FIREFOX_MOBILE_UA: String =
        "Mozilla/5.0 (Android ${Build.VERSION.RELEASE}; Mobile; rv:159.0) Gecko/159.0 Firefox/159.0"

    /**
     * Pages are loaded by Gecko, so cookies (e.g. Cloudflare clearance) and CDN tokens are bound to
     * the Gecko UA. Callers that pass the Chromium WebView default UA would get 403s; swap it.
     */
    private fun effectiveUserAgent(context: Context, userAgent: String?): String {
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: return FIREFOX_MOBILE_UA
        val webViewDefault = try { WebSettings.getDefaultUserAgent(context) } catch (_: Throwable) { null }
        return if (ua == webViewDefault) FIREFOX_MOBILE_UA else ua
    }

    private val SCHEME_PREFIX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val HOST_PORT = Regex("^[^/:?#]+:\\d+([/?#].*)?$")

    /**
     * Returns an absolute http(s) URL, adding https:// to a bare host, or null for blank input and
     * for any other scheme (javascript:, file:, intent:, content:, ...).
     */
    @JvmStatic
    fun normalizeDownloadUrl(raw: String?): String? {
        val u = raw?.trim().orEmpty()
        if (u.isEmpty()) return null
        val scheme = SCHEME_PREFIX.find(u)?.groupValues?.get(1)?.lowercase()
        if (scheme != null && !HOST_PORT.matches(u)) {
            return if (scheme == "http" || scheme == "https") u else null
        }
        return "https://$u"
    }

    @JvmStatic
    @JvmOverloads
    fun download(
        context: Context,
        url: String,
        fileName: String? = null,
        mimeType: String? = null,
        referrerUrl: String? = null,
        isPrivate: Boolean = false,
        userAgent: String? = null,
        cookie: String? = null,
        headers: Map<String, String> = emptyMap(),
        onEnqueued: ((Long) -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        // 0. Validate before anything else (including an external manager): only http(s) is
        //    downloadable here. javascript:, file:, intent:, content: ... are refused with a message.
        val verifiedUrl = normalizeDownloadUrl(url)
        if (verifiedUrl == null || !SafeDownloadValues.isHttpUrl(verifiedUrl)) {
            PetalFetchDownloadBridge.notifyFailure(
                context,
                com.petal.browser.compose.downloads.DownloadFailure.INVALID_URL
            )
            onFailed?.invoke()
            return
        }

        // 1. External Download Manager preference check (1DM, ADM, AB Download Manager, Navi, Auto).
        //    Private downloads never leave the browser: an external manager would receive the URL,
        //    cookies and referrer of a private session.
        if (!isPrivate && ExternalDownloadManagerHelper.isExternalPreferred(context)) {
            val launched = ExternalDownloadManagerHelper.launchDownloadInExternalManager(
                context, verifiedUrl, fileName, mimeType, null, userAgent, cookie
            )
            if (launched) {
                return
            }
        }

        // 2. Storage permission check for Android 9 and older
        val hasPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || PetalBackupManager.checkPermissionStorage(context)
        if (!hasPermission) {
            if (context is Activity) {
                PetalBackupManager.requestPermission(context)
            }
            onFailed?.invoke()
            return
        }

        // 3. Resolve context parameters (cookies, user-agent, referer)
        val effectiveUa = effectiveUserAgent(context, userAgent)
        val effectiveCookie = cookie?.takeIf { it.isNotBlank() }
            ?: PetalGeckoCookieBridge.getCookies(verifiedUrl, isPrivate)

        val extraHeaders = LinkedHashMap<String, String>()
        if (!referrerUrl.isNullOrBlank()) {
            extraHeaders["Referer"] = referrerUrl
        }
        headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank() && !k.equals("Cookie", ignoreCase = true)) {
                extraHeaders[k] = v
            }
        }

        val resolvedFileName = SafeDownloadValues.fileName(
            url = verifiedUrl,
            contentDisposition = null,
            mimeType = mimeType,
            preferredFileName = fileName
        ).ifBlank { "download" }

        // 4. Dispatch exclusively via Mozilla's official Firefox/Fenix download pipeline
        PetalFetchDownloadBridge.enqueueUnifiedDownload(
            context = context,
            url = verifiedUrl,
            fileName = resolvedFileName,
            mimeType = mimeType,
            referrerUrl = referrerUrl,
            isPrivate = isPrivate,
            userAgent = effectiveUa,
            cookie = effectiveCookie,
            headers = extraHeaders,
            onEnqueued = onEnqueued,
            onFailed = onFailed
        )
    }
}
