package com.petal.browser.download

import androidx.core.app.NotificationManagerCompat
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.DefaultFileSizeFormatter
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.DefaultPackageNameProvider
import mozilla.components.feature.downloads.filewriter.DefaultDownloadFileWriter
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.utils.DefaultDateTimeProvider
import mozilla.components.support.utils.DefaultDownloadFileUtils

/**
 * Android Components download service used by the Mozilla download pipeline.
 * Routing is enabled separately after CI validates this service against the
 * pinned Android Components version.
 */
class PetalMozillaDownloadService : AbstractFetchDownloadService() {
    override val store: BrowserStore
        get() = com.petal.browser.engine.gecko.PetalEngineStore.getStore(this)

    override val packageNameProvider = DefaultPackageNameProvider(this)

    override val notificationsDelegate by lazy {
        NotificationsDelegate(NotificationManagerCompat.from(this))
    }

    override val httpClient = PetalMozillaFetchClient()

    override val fileSizeFormatter = DefaultFileSizeFormatter(this)

    override val downloadEstimator = DownloadEstimator(DefaultDateTimeProvider())

    override val downloadFileUtils = DefaultDownloadFileUtils(this)

    override val downloadFileWriter = DefaultDownloadFileWriter(this, downloadFileUtils)
}
