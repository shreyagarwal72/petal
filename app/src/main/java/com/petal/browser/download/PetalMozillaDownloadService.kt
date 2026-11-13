package com.petal.browser.download

import androidx.core.app.NotificationManagerCompat
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.DefaultFileSizeFormatter
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.DefaultPackageNameProvider
import mozilla.components.feature.downloads.filewriter.DefaultDownloadFileWriter
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.utils.DefaultDateTimeProvider
import mozilla.components.support.utils.DefaultDownloadFileUtils

class PetalFallbackFetchClient(
    private val primary: Client?,
    private val fallback: Client
) : Client() {
    @Throws(java.io.IOException::class)
    override fun fetch(request: mozilla.components.concept.fetch.Request): mozilla.components.concept.fetch.Response {
        if (primary != null) {
            // Primary Gecko client is available: execute exclusively through Gecko's network stack.
            // Never silently fall back mid-stream or swallow network disconnects.
            return primary.fetch(request)
        }

        // Primary client failed initial construction:
        if (request.private) {
            // Strict private-mode isolation: private requests must NEVER leave Gecko's network stack.
            PetalDownloadFailureLog.record(request.url, "Private download blocked: the browser network engine is unavailable")
            throw java.io.IOException("GeckoViewFetchClient unavailable; private download aborted to prevent network stack leakage.")
        }

        // The OkHttp fallback has no access to Gecko's cookie jar. That is fine for a public URL, but
        // an authenticated download (Cookie/Authorization header, URL credentials, or Gecko cookies
        // known for the host) would silently come back as a login page or a 401/403. Fail loudly instead.
        if (isAuthenticatedRequest(request)) {
            PetalDownloadFailureLog.record(
                request.url,
                "This download needs your signed-in session and the browser engine is unavailable. Restart Petal and try again."
            )
            throw java.io.IOException("GeckoViewFetchClient unavailable; refusing cookie-less fallback for an authenticated download.")
        }

        android.util.Log.w("PetalMozillaDownload", "Primary GeckoView fetch client unavailable, using OkHttp for public non-private request")
        return fallback.fetch(request)
    }

    private fun isAuthenticatedRequest(request: mozilla.components.concept.fetch.Request): Boolean {
        val hasAuthHeader = request.headers?.any { header ->
            header.name.equals("Cookie", ignoreCase = true) ||
                header.name.equals("Authorization", ignoreCase = true) ||
                header.name.equals("Proxy-Authorization", ignoreCase = true)
        } == true
        if (hasAuthHeader) return true
        val hasUserInfo = runCatching { java.net.URI(request.url).rawUserInfo != null }.getOrDefault(false)
        if (hasUserInfo) return true
        return !PetalGeckoCookieBridge.getCookies(request.url, request.private).isNullOrBlank()
    }
}

/**
 * Whether Petal's Mozilla download service process component is alive. Retry/pause/resume use this to
 * choose between a broadcast to the running service and (re)starting the service for a download.
 */
object PetalDownloadServiceState {
    @Volatile private var cachedAt = 0L
    @Volatile private var cachedValue = false

    @JvmStatic
    fun isRunning(context: android.content.Context): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - cachedAt < 400L) return cachedValue
        val value = try {
            val am = context.applicationContext.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val name = PetalMozillaDownloadService::class.java.name
            @Suppress("DEPRECATION")
            am.getRunningServices(Int.MAX_VALUE).any { it.service.className == name }
        } catch (_: Throwable) {
            false
        }
        cachedValue = value
        cachedAt = now
        return value
    }

    /** Forget the cached answer, e.g. right after starting the service. */
    @JvmStatic
    fun invalidate() {
        cachedAt = 0L
    }
}

/**
 * Android Components download service (the same AbstractFetchDownloadService Firefox/Fenix
 * uses) and Petal's only engine for browser downloads. Bytes go through Gecko's own network
 * stack via GeckoViewFetchClient, so Gecko cookies, private mode and proxy settings apply.
 *
 * Large downloads from servers that support HTTP Range are fetched over several parallel
 * connections by [PetalParallelFetchClient]; this service is unaware of that and keeps owning
 * notifications, pause/resume and completion exactly as before.
 */
class PetalMozillaDownloadService : AbstractFetchDownloadService() {
    override val store: BrowserStore
        get() = com.petal.browser.engine.gecko.PetalEngineStore.getStore(this)

    override val packageNameProvider = DefaultPackageNameProvider(this)

    override val notificationsDelegate by lazy {
        NotificationsDelegate(NotificationManagerCompat.from(this))
    }

    override val httpClient: Client by lazy {
        val geckoClient: Client? = try {
            GeckoViewFetchClient(
                applicationContext,
                com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(applicationContext)
            )
        } catch (t: Throwable) {
            android.util.Log.w("PetalMozillaDownload", "GeckoViewFetchClient unavailable, using OkHttp", t)
            null
        }
        val okHttpClient = PetalOkHttpFetchClient()
        val base: Client = PetalFallbackFetchClient(primary = geckoClient, fallback = okHttpClient)
        // Order matters: PetalMozillaFetchClient injects the per-URL Referer/Cookie/UA headers on the
        // original request, then PetalParallelFetchClient splits it into Range segments that all
        // inherit those headers. Parallel mode self-disables for servers without Range support.
        PetalMozillaFetchClient(
            PetalParallelFetchClient(base) {
                PetalParallelFetchClient.Settings.load(applicationContext)
            }
        )
    }

    override val fileSizeFormatter = DefaultFileSizeFormatter(this)

    override val downloadEstimator = DownloadEstimator(DefaultDateTimeProvider())

    override val downloadFileUtils = DefaultDownloadFileUtils(this)

    override val downloadFileWriter = DefaultDownloadFileWriter(this, downloadFileUtils)

    override fun onTimeout(startId: Int) {
        android.util.Log.w("PetalMozillaDownload", "Service timed out by system (dataSync limit reached). Pausing active downloads.")
        try {
            val active = store.state.downloads.values.filter {
                it.status == mozilla.components.browser.state.state.content.DownloadState.Status.DOWNLOADING
            }
            active.forEach { dl ->
                val intent = android.content.Intent(ACTION_PAUSE).apply {
                    setPackage(packageName)
                    putExtra(mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID, dl.id)
                }
                sendBroadcast(intent)
            }
        } catch (_: Throwable) {}
        super.onTimeout(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        android.util.Log.w("PetalMozillaDownload", "Service timed out for fgsType $fgsType (startId $startId). Pausing active downloads.")
        onTimeout(startId)
    }
}
