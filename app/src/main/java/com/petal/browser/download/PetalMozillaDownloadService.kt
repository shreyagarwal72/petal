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
            throw java.io.IOException("GeckoViewFetchClient unavailable; private download aborted to prevent network stack leakage.")
        }

        android.util.Log.w("PetalMozillaDownload", "Primary GeckoView fetch client unavailable, using OkHttp for non-private request")
        return fallback.fetch(request)
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
}
