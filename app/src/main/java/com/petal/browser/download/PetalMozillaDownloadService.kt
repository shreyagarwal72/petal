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

/**
 * Android Components download service (the same AbstractFetchDownloadService Firefox/Fenix
 * uses) and Petal's only engine for browser downloads. Bytes go through Gecko's own network
 * stack via GeckoViewFetchClient, so Gecko cookies, private mode and proxy settings apply.
 */
class PetalMozillaDownloadService : AbstractFetchDownloadService() {
    override val store: BrowserStore
        get() = com.petal.browser.engine.gecko.PetalEngineStore.getStore(this)

    override val packageNameProvider = DefaultPackageNameProvider(this)

    override val notificationsDelegate by lazy {
        NotificationsDelegate(NotificationManagerCompat.from(this))
    }

    override val httpClient: Client by lazy {
        val base: Client = try {
            GeckoViewFetchClient(
                applicationContext,
                com.petal.browser.engine.gecko.PetalGeckoRuntime.getOrCreate(applicationContext)
            )
        } catch (t: Throwable) {
            android.util.Log.w("PetalMozillaDownload", "GeckoViewFetchClient unavailable, using OkHttp", t)
            PetalOkHttpFetchClient()
        }
        PetalMozillaFetchClient(base)
    }

    override val fileSizeFormatter = DefaultFileSizeFormatter(this)

    override val downloadEstimator = DownloadEstimator(DefaultDateTimeProvider())

    override val downloadFileUtils = DefaultDownloadFileUtils(this)

    override val downloadFileWriter = DefaultDownloadFileWriter(this, downloadFileUtils)
}
