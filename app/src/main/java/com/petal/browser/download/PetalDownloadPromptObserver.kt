package com.petal.browser.download

import android.content.Context
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.engine.gecko.PetalGeckoRuntime
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.view.PetalToast
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore

/**
 * Turns "a tab reported a download" into the Petal confirmation sheet and then a real download.
 *
 * Tabs are GeckoEngineSession-based. When Gecko reports an external response, the session does
 * not call any Petal code: it dispatches `ContentAction.UpdateDownloadAction`, which leaves the
 * download in `tab.content.download`. Nothing used to read that field (there is no
 * DownloadsFeature), so tapping a download link did nothing. This observer is that missing piece,
 * in the same role as Fenix's DownloadsFeature, but with Petal's own confirmation dialog:
 *
 *  1. read `content.download` of the selected tab,
 *  2. consume it (so it never fires twice),
 *  3. extensions and torrents/magnets go to their handlers,
 *  4. everything else shows [com.petal.browser.ui.components.PetalDownloadDialogBridge] and then
 *     goes to [PetalDownloadRouter] (http/https) or straight to Mozilla's manager (blob:/data:,
 *     whose bytes live in the original [DownloadState]).
 */
object PetalDownloadPromptObserver {

    @Volatile
    private var started = false
    private val handledIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    @JvmStatic
    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            val appContext = context.applicationContext
            val store = PetalEngineStore.getStore(appContext)
            store.observeManually { state ->
                val tab = state.tabs.firstOrNull { it.id == state.selectedTabId }
                val download = tab?.content?.download
                if (tab != null && download != null) {
                    handle(appContext, store, tab.id, download)
                }
            }.resume()
            started = true
        }
    }

    private fun handle(appContext: Context, store: BrowserStore, tabId: String, download: DownloadState) {
        // Nothing can be shown without a foreground activity; leave the download in the tab state
        // and look at it again on the next store emission.
        val activity = PetalGeckoRuntime.getForegroundActivity() ?: return
        if (!handledIds.add(download.id)) return
        if (handledIds.size > 200) {
            synchronized(handledIds) { handledIds.iterator().let { it.next(); it.remove() } }
        }
        // Consume first: the observer fires on every state change and must not re-prompt.
        store.dispatch(ContentAction.ConsumeDownloadAction(tabId, download.id))

        val url = download.url
        val mimeType = download.contentType
        val contentLength = download.contentLength ?: 0L
        val contentDisposition = download.fileName?.takeIf { it.isNotBlank() }
            ?.let { "attachment; filename=\"" + it.replace("\"", "") + "\"" }
        val resolvedName = PetalAndroidUtils.resolveFileName(url, contentDisposition, mimeType)

        activity.runOnUiThread {
            try {
                if (isExtension(url, mimeType)) {
                    com.petal.browser.extensions.PetalExtensionManager.install(url) { success, message ->
                        PetalToast.show(activity, message ?: if (success) "Extension installed" else "Extension installation failed")
                    }
                    return@runOnUiThread
                }
                if (com.petal.browser.torrent.PetalTorrentEngineManager.handleTorrentOrMagnet(activity, url, resolvedName, mimeType)) {
                    return@runOnUiThread
                }

                val isStream = url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)
                val startDownload: (String) -> Unit = { confirmedName ->
                    val finalName = confirmedName.ifBlank { resolvedName }
                    if (isStream) {
                        // The payload is inside the original state; hand it over unchanged.
                        PetalFetchDownloadBridge.enqueueStoreDownload(appContext, download, finalName)
                    } else {
                        PetalDownloadRouter.download(
                            context = activity,
                            url = url,
                            fileName = finalName,
                            mimeType = mimeType,
                            referrerUrl = download.referrerUrl,
                            isPrivate = download.private,
                            userAgent = download.userAgent
                        )
                    }
                }

                if (download.skipConfirmation) {
                    startDownload(resolvedName)
                } else {
                    com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                        activity, url, contentDisposition, mimeType, contentLength, startDownload
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.e("PetalDownloadPrompt", "Failed to handle download for $url", t)
                PetalFetchDownloadBridge.notifyFailure(appContext, com.petal.browser.compose.downloads.DownloadFailure.START_FAILED)
            }
        }
    }

    private fun isExtension(url: String, mimeType: String?): Boolean {
        val path = runCatching { android.net.Uri.parse(url).path }.getOrNull()
        return path?.endsWith(".xpi", ignoreCase = true) == true ||
            mimeType?.contains("application/x-xpinstall", ignoreCase = true) == true
    }
}
