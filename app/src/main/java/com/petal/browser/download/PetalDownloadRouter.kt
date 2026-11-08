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
        if (url.isBlank()) {
            onFailed?.invoke()
            return
        }

        val verifiedUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            "https://$url"
        } else {
            url
        }

        // 1. External Download Manager preference check (1DM, ADM, AB Download Manager, Navi, Auto)
        if (ExternalDownloadManagerHelper.isExternalPreferred(context)) {
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
