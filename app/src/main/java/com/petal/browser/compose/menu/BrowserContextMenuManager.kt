package com.petal.browser.compose.menu

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.URLUtil
import androidx.lifecycle.lifecycleScope
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.ai.ResearchMode
import com.petal.browser.compose.mlkit.PetalImageScannerBridge
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.download.DownloadFileNameResolver
import com.petal.browser.ui.components.PetalAiResearchBridge
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.ImageActionHelper
import com.petal.browser.view.PetalToast

/**
 * Kotlin Manager encapsulating long-press context menus for BrowserActivity.
 * Fulfills Material 3 Expressive menu handlers for image, link, video, audio, text, and page targets.
 */
object BrowserContextMenuManager {

    /**
     * Launches [targetUrl] in a second, independent Android window using freeform/
     * adjacent multi-window mode (FLAG_ACTIVITY_NEW_TASK + MULTIPLE_TASK + LAUNCH_ADJACENT).
     */
    private fun launchInNewWindow(activity: BrowserActivity, targetUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                setClass(activity, BrowserActivity::class.java)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                )
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            activity.addAlbum(HelperUnit.domain(targetUrl), targetUrl, true)
        }
    }

    /**
     * Resolves external app handler for [targetUrl] or opens the system intent chooser.
     */
    private fun openInExternalApp(activity: BrowserActivity, targetUrl: String) {
        try {
            val uri = Uri.parse(targetUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val resolveInfos = activity.packageManager.queryIntentActivities(intent, 0)
            val external = resolveInfos.filter { it.activityInfo.packageName != activity.packageName }
            if (external.isNotEmpty()) {
                val chooser = Intent.createChooser(intent, "Open with")
                activity.startActivity(chooser)
            } else {
                PetalToast.show(activity, "No external app found for this link")
            }
        } catch (e: Exception) {
            PetalToast.show(activity, "Cannot open link in external app")
        }
    }

    /**
     * Forwards audio/video to an external player application via ACTION_VIEW.
     */
    private fun openInExternalPlayer(activity: BrowserActivity, mediaUrl: String, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(mediaUrl), mimeType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(Intent.createChooser(intent, "Play with"))
        } catch (e: Exception) {
            PetalToast.show(activity, "No external player found")
        }
    }

    @JvmStatic
    @JvmOverloads
    fun showImageContextMenu(activity: BrowserActivity, imageURL: String, title: String? = null) {
        val displayTitle = title?.trim()?.takeIf { it.isNotEmpty() }?.take(80) ?: HelperUnit.domain(imageURL)
        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = displayTitle,
            linkUrl = imageURL,
            faviconUrl = imageURL,
            imageUrl = imageURL,
            isImage = true,
            isVideo = false,
            handler = object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(displayTitle, imageURL, true)
                }

                override fun onOpenInBackgroundTab() {
                    activity.addAlbum(displayTitle, imageURL, false)
                    PetalToast.show(activity, "Opened in background tab")
                }

                override fun onOpenImageInNewTab() {
                    activity.addAlbum(displayTitle, imageURL, true)
                }

                override fun onOpenImageInBackgroundTab() {
                    activity.addAlbum(displayTitle, imageURL, false)
                    PetalToast.show(activity, "Opened in background tab")
                }

                override fun onOpenInNewTabInGroup() {
                    activity.addAlbum(displayTitle, imageURL, true)
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(displayTitle, imageURL, true, true)
                }

                override fun onPreviewPage() {
                    PetalPagePreviewBridge.show(activity, imageURL)
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, imageURL)
                    PetalToast.show(activity, "Image address copied")
                }

                override fun onCopyImage() {
                    if (imageURL.isNotBlank()) {
                        ImageActionHelper.copyImage(activity, imageURL)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onCopyImageAddress() {
                    HelperUnit.copy(activity, imageURL)
                    PetalToast.show(activity, "Image address copied")
                }

                override fun onCopyLinkText() {
                    HelperUnit.copy(activity, displayTitle)
                    PetalToast.show(activity, "Title copied")
                }

                override fun onBookmarkLink() {
                    activity.saveBookmark(displayTitle, imageURL)
                }

                override fun onDownloadLink() {
                    try {
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, imageURL) { fileName, mimeType ->
                            BrowserUnit.download(activity, imageURL, fileName, mimeType)
                            PetalToast.show(activity, "Download started")
                        }
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Failed to start download")
                    }
                }

                override fun onDownloadImage() {
                    if (imageURL.isNotBlank()) {
                        ImageActionHelper.downloadImage(activity, imageURL)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onAddToReadingList() {
                    try {
                        val action = RecordAction(activity)
                        action.open(true)
                        val record = Record(displayTitle, imageURL, System.currentTimeMillis(), 0)
                        record.isReadingList = true
                        action.addBookmark(record)
                        action.close()
                        PetalToast.show(activity, "Added to reading list")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onAnalyzeWithPetalAi() {
                    if (imageURL.isNotBlank()) {
                        PetalAiResearchBridge.showAiResearchSheet(
                            activity = activity,
                            pageTitle = "Image Analysis",
                            pageUrl = imageURL,
                            pageContent = "Please analyze this image and explain what it depicts, key details, and context: $imageURL",
                            initialMode = ResearchMode.DEEP_RESEARCH,
                            autoStart = true
                        )
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onScanImage() {
                    if (imageURL.isNotBlank()) {
                        PetalImageScannerBridge.show(activity, imageURL)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onSearchWithGoogleLens() {
                    if (imageURL.isNotBlank()) {
                        com.petal.browser.lens.PetalLensManager.searchImageWithGoogleLens(activity, imageURL)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onShareImage() {
                    if (imageURL.isNotBlank()) {
                        ImageActionHelper.shareImage(activity, imageURL)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onShareLink() {
                    activity.shareLink(displayTitle, imageURL)
                }

                override fun onViewInPetalViewer() {
                    if (imageURL.isNotBlank()) {
                        activity.runOnUiThread {
                            val view = com.petal.browser.compose.downloads.PetalImageViewerBridge.createWebViewerView(
                                activity,
                                imageURL,
                                displayTitle
                            ) {
                                activity.runOnUiThread { activity.performBackNavigation() }
                            }
                            activity.presentComposeScreen(view)
                        }
                    }
                }
            }
        )
    }

    @JvmStatic
    @JvmOverloads
    fun showLinkContextMenu(activity: BrowserActivity, urlResult: String, linkText: String? = null) {
        val parsed = Uri.parse(urlResult)
        val faviconUrl = if (parsed.scheme == "http" || parsed.scheme == "https") {
            "${parsed.scheme}://${parsed.authority}/favicon.ico"
        } else null
        val displayTitle = linkText?.trim()?.takeIf { it.isNotEmpty() }?.take(80) ?: HelperUnit.domain(urlResult)

        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = displayTitle,
            linkUrl = urlResult,
            faviconUrl = faviconUrl,
            imageUrl = null,
            isImage = false,
            isVideo = false,
            handler = object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(displayTitle, urlResult, true)
                }

                override fun onOpenInBackgroundTab() {
                    activity.addAlbum(displayTitle, urlResult, false)
                    PetalToast.show(activity, "Opened in background tab")
                }

                override fun onOpenInNewTabInGroup() {
                    val currentAlbum = activity.currentAlbumController
                    val currentGeckoView = currentAlbum as? com.petal.browser.view.PetalGeckoView
                    val currentTabSurface = currentAlbum as? com.petal.browser.browser.PetalTabViewController
                    val currentTabId = currentTabSurface?.getTabId()
                        ?: currentGeckoView?.getTabId()
                        ?: currentAlbum?.hashCode()?.toString()
                    val existingGroup = if (currentTabId != null) {
                        com.petal.browser.compose.tabs.PetalTabGroupManager.findGroupByTabId(activity, currentTabId)
                    } else null

                    if (existingGroup != null) {
                        activity.addAlbumInGroup(displayTitle, urlResult, true, existingGroup.id, existingGroup.title)
                    } else {
                        if (currentTabId != null) {
                            val currentTab = com.petal.browser.compose.tabs.PetalTabItem(
                                id = currentTabId,
                                title = currentAlbum?.title ?: "Tab",
                                url = currentAlbum?.url ?: "about:blank"
                            )
                            val newGroup = com.petal.browser.compose.tabs.PetalTabGroupManager.createGroupWithTabs(activity, currentTab, currentTab)
                            currentTabSurface?.setTabGroupId(newGroup.id)
                            currentTabSurface?.setTabGroupTitle(newGroup.title)
                            currentGeckoView?.setTabGroupId(newGroup.id)
                            currentGeckoView?.setTabGroupTitle(newGroup.title)
                            activity.addAlbumInGroup(displayTitle, urlResult, true, newGroup.id, newGroup.title)
                        } else {
                            activity.addAlbum(displayTitle, urlResult, true)
                        }
                    }
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(displayTitle, urlResult, true, true)
                }

                override fun onOpenInExternalApp() {
                    openInExternalApp(activity, urlResult)
                }

                override fun onPreviewPage() {
                    PetalPagePreviewBridge.show(activity, urlResult)
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, urlResult)
                    PetalToast.show(activity, "Link copied")
                }

                override fun onCopyLinkText() {
                    val text = linkText?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        HelperUnit.copy(activity, text)
                        PetalToast.show(activity, "Link text copied")
                    } else {
                        PetalToast.show(activity, "This link has no text")
                    }
                }

                override fun onBookmarkLink() {
                    activity.saveBookmark(displayTitle, urlResult)
                }

                override fun onDownloadLink() {
                    try {
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, urlResult) { fileName, mimeType ->
                            BrowserUnit.download(activity, urlResult, fileName, mimeType)
                            PetalToast.show(activity, "Download started")
                        }
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Failed to start download")
                    }
                }

                override fun onDownloadImage() {
                    if (urlResult.isNotBlank()) {
                        ImageActionHelper.downloadImage(activity, urlResult)
                    }
                }

                override fun onAddToReadingList() {
                    try {
                        val action = RecordAction(activity)
                        action.open(true)
                        val record = Record(displayTitle, urlResult, System.currentTimeMillis(), 0)
                        record.isReadingList = true
                        action.addBookmark(record)
                        action.close()
                        PetalToast.show(activity, "Added to reading list")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onShareImage() {
                    if (urlResult.isNotBlank()) {
                        ImageActionHelper.shareImage(activity, urlResult)
                    }
                }

                override fun onShareLink() {
                    activity.shareLink(displayTitle, urlResult)
                }

                override fun onScanImage() {
                    if (urlResult.isNotBlank()) {
                        PetalImageScannerBridge.show(activity, urlResult)
                    }
                }

                override fun onSearchWithGoogleLens() {
                    if (urlResult.isNotBlank()) {
                        com.petal.browser.lens.PetalLensManager.searchImageWithGoogleLens(activity, urlResult)
                    }
                }

                override fun onSendEmail(mailto: String) {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailto))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        PetalToast.show(activity, "No email client found")
                    }
                }

                override fun onDialPhoneNumber(tel: String) {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(tel))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        PetalToast.show(activity, "No phone app found")
                    }
                }

                override fun onSendSms(smsUri: String) {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(smsUri))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        PetalToast.show(activity, "No SMS app found")
                    }
                }

                override fun onOpenMapLocation(geo: String) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geo))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        PetalToast.show(activity, "No maps app found")
                    }
                }
            }
        )
    }

    @JvmStatic
    fun showVideoContextMenu(activity: BrowserActivity, cleanVideoUrl: String) {
        val title = HelperUnit.domain(cleanVideoUrl)
        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = title,
            linkUrl = cleanVideoUrl,
            faviconUrl = null,
            imageUrl = null,
            isImage = false,
            isVideo = true,
            handler = object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(title, cleanVideoUrl, true)
                }

                override fun onOpenVideoInNewTab() {
                    activity.addAlbum(title, cleanVideoUrl, true)
                }

                override fun onOpenVideoInBackgroundTab() {
                    activity.addAlbum(title, cleanVideoUrl, false)
                    PetalToast.show(activity, "Video opened in background tab")
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(title, cleanVideoUrl, true, true)
                }

                override fun onTogglePictureInPicture() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                        } catch (e: Exception) {
                            PetalToast.show(activity, "PiP not supported on this device")
                        }
                    } else {
                        PetalToast.show(activity, "PiP requires Android 8.0+")
                    }
                }

                override fun onOpenInExternalPlayer(mimeType: String) {
                    openInExternalPlayer(activity, cleanVideoUrl, mimeType)
                }

                override fun onDownloadVideo() {
                    try {
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, cleanVideoUrl, fallbackMimeType = "video/mp4") { fileName, mimeType ->
                            BrowserUnit.download(activity, cleanVideoUrl, fileName, mimeType)
                            PetalToast.show(activity, "Video download started")
                        }
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Failed to start video download")
                    }
                }

                override fun onCopyVideoLink() {
                    HelperUnit.copy(activity, cleanVideoUrl)
                    PetalToast.show(activity, "Video link copied")
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, cleanVideoUrl)
                    PetalToast.show(activity, "Video link copied")
                }

                override fun onShareVideo() {
                    activity.shareLink(title, cleanVideoUrl)
                }

                override fun onShareLink() {
                    activity.shareLink(title, cleanVideoUrl)
                }
            }
        )
    }

    @JvmStatic
    fun showAudioContextMenu(activity: BrowserActivity, audioUrl: String) {
        val title = HelperUnit.domain(audioUrl)
        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = title,
            linkUrl = audioUrl,
            faviconUrl = null,
            isImage = false,
            isVideo = false,
            isAudio = true,
            selectedText = null,
            handler = object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(title, audioUrl, true)
                }

                override fun onOpenAudioInNewTab() {
                    activity.addAlbum(title, audioUrl, true)
                }

                override fun onOpenAudioInBackgroundTab() {
                    activity.addAlbum(title, audioUrl, false)
                    PetalToast.show(activity, "Audio opened in background tab")
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(title, audioUrl, true, true)
                }

                override fun onOpenInExternalAudioPlayer(mimeType: String) {
                    openInExternalPlayer(activity, audioUrl, mimeType)
                }

                override fun onDownloadAudio() {
                    try {
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, audioUrl, fallbackMimeType = "audio/mpeg") { fileName, mimeType ->
                            BrowserUnit.download(activity, audioUrl, fileName, mimeType)
                            PetalToast.show(activity, "Audio download started")
                        }
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Failed to start audio download")
                    }
                }

                override fun onCopyAudioLink() {
                    HelperUnit.copy(activity, audioUrl)
                    PetalToast.show(activity, "Audio link copied")
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, audioUrl)
                    PetalToast.show(activity, "Audio link copied")
                }

                override fun onShareAudio() {
                    activity.shareLink(title, audioUrl)
                }

                override fun onShareLink() {
                    activity.shareLink(title, audioUrl)
                }
            }
        )
    }

    @JvmStatic
    fun showSelectionContextMenu(activity: BrowserActivity, selectedText: String) {
        if (selectedText.isBlank()) return
        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = "Text Selection",
            linkUrl = selectedText,
            faviconUrl = null,
            isImage = false,
            isVideo = false,
            isAudio = false,
            selectedText = selectedText,
            handler = object : PetalLinkContextMenuHandler {
                override fun onSearchWebText(text: String) {
                    val query = BrowserUnit.queryWrapper(activity, text)
                    activity.addAlbum(text.take(24), query, true)
                }

                override fun onSearchWebTextInBackground(text: String) {
                    val query = BrowserUnit.queryWrapper(activity, text)
                    activity.addAlbum(text.take(24), query, false)
                    PetalToast.show(activity, "Search opened in background tab")
                }

                override fun onResearchWithPetalAi(text: String) {
                    PetalAiResearchBridge.showAiResearchSheet(
                        activity = activity,
                        pageTitle = "Selected Text Research",
                        pageUrl = "",
                        pageContent = text,
                        initialMode = ResearchMode.DEEP_RESEARCH,
                        autoStart = true
                    )
                }

                override fun onTranslateText(text: String) {
                    PetalAiResearchBridge.showAiResearchSheet(
                        activity = activity,
                        pageTitle = "Translate Text",
                        pageUrl = "",
                        pageContent = "Please translate the following text to English:\n\n$text",
                        initialMode = ResearchMode.CUSTOM,
                        autoStart = true
                    )
                }

                override fun onCopySelectedText(text: String) {
                    HelperUnit.copy(activity, text)
                    PetalToast.show(activity, "Text copied")
                }

                override fun onShareSelectedText(text: String) {
                    activity.shareLink("Shared Text", text)
                }
            }
        )
    }

    @JvmStatic
    fun showSmsContextMenu(activity: BrowserActivity, smsUri: String) {
        val number = smsUri.removePrefix("sms:").removePrefix("smsto:").substringBefore('?')
        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = number.ifBlank { "Text message" },
            linkUrl = smsUri,
            faviconUrl = null,
            imageUrl = null,
            isImage = false,
            isVideo = false,
            isAudio = false,
            selectedText = null,
            handler = object : PetalLinkContextMenuHandler {
                override fun onSendSms(smsUri: String) {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(smsUri))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        PetalToast.show(activity, "No SMS app found")
                    }
                }

                override fun onCopyLinkAddress() {
                    val cleanNumber = smsUri.removePrefix("sms:").removePrefix("smsto:").substringBefore('?')
                    HelperUnit.copy(activity, cleanNumber)
                    PetalToast.show(activity, "Phone number copied")
                }
            }
        )
    }

    @JvmStatic
    @JvmOverloads
    fun showPageContextMenu(
        activity: BrowserActivity,
        pageUrl: String,
        pageTitle: String? = null,
    ) {
        val parsed = Uri.parse(pageUrl)
        val faviconUrl = if (parsed.scheme == "http" || parsed.scheme == "https") {
            "${parsed.scheme}://${parsed.authority}/favicon.ico"
        } else null

        val title = pageTitle?.takeIf { it.isNotBlank() } ?: HelperUnit.domain(pageUrl) ?: "Current Page"

        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = title,
            linkUrl = pageUrl,
            faviconUrl = faviconUrl,
            imageUrl = null,
            isImage = false,
            isVideo = false,
            isAudio = false,
            selectedText = null,
            isPage = true,
            handler = object : PetalLinkContextMenuHandler {
                override fun onBookmarkPage() {
                    activity.saveBookmark(title, pageUrl)
                }

                override fun onSharePage() {
                    activity.shareLink(title, pageUrl)
                }

                override fun onPrintPagePdf() {
                    try {
                        activity.createWebPrintJob(null)
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Print not supported for this page")
                    }
                }

                override fun onToggleReaderMode() {
                    try {
                        activity.showReaderMode()
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Reader mode unavailable")
                    }
                }

                override fun onFindInPage() {
                    activity.searchOnSite()
                }

                override fun onViewPageSource() {
                    val sourceUrl = if (pageUrl.startsWith("view-source:")) pageUrl else "view-source:$pageUrl"
                    activity.addAlbum("Source: " + HelperUnit.domain(pageUrl), sourceUrl, true)
                }

                override fun onAiSummarizePage() {
                    try {
                        PetalAiResearchBridge.showAiResearchSheet(
                            activity = activity,
                            pageTitle = title,
                            pageUrl = pageUrl,
                            pageContent = "Summarize this web page and extract key takeaways:\n\nTitle: $title\nURL: $pageUrl",
                            initialMode = ResearchMode.SUMMARY,
                            autoStart = true
                        )
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Petal AI summary unavailable")
                    }
                }
            }
        )
    }

    @JvmStatic
    @JvmOverloads
    fun showImageLinkContextMenu(activity: BrowserActivity, linkUrl: String, imageUrl: String, linkText: String? = null) {
        val parsed = Uri.parse(linkUrl)
        val faviconUrl = if (parsed.scheme == "http" || parsed.scheme == "https") {
            "${parsed.scheme}://${parsed.authority}/favicon.ico"
        } else null
        val displayTitle = linkText?.trim()?.takeIf { it.isNotEmpty() }?.take(80) ?: HelperUnit.domain(linkUrl)

        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = displayTitle,
            linkUrl = linkUrl,
            faviconUrl = faviconUrl,
            imageUrl = imageUrl,
            isImage = true,
            isVideo = false,
            isAudio = false,
            selectedText = null,
            handler = object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(displayTitle, linkUrl, true)
                }

                override fun onOpenInBackgroundTab() {
                    activity.addAlbum(displayTitle, linkUrl, false)
                    PetalToast.show(activity, "Opened in background tab")
                }

                override fun onOpenInNewTabInGroup() {
                    val currentAlbum = activity.currentAlbumController
                    val currentGeckoView = currentAlbum as? com.petal.browser.view.PetalGeckoView
                    val currentTabSurface = currentAlbum as? com.petal.browser.browser.PetalTabViewController
                    val currentTabId = currentTabSurface?.getTabId()
                        ?: currentGeckoView?.getTabId()
                        ?: currentAlbum?.hashCode()?.toString()
                    val existingGroup = if (currentTabId != null) {
                        com.petal.browser.compose.tabs.PetalTabGroupManager.findGroupByTabId(activity, currentTabId)
                    } else null

                    if (existingGroup != null) {
                        activity.addAlbumInGroup(displayTitle, linkUrl, true, existingGroup.id, existingGroup.title)
                    } else {
                        if (currentTabId != null) {
                            val currentTab = com.petal.browser.compose.tabs.PetalTabItem(
                                id = currentTabId,
                                title = currentAlbum?.title ?: "Tab",
                                url = currentAlbum?.url ?: "about:blank"
                            )
                            val newGroup = com.petal.browser.compose.tabs.PetalTabGroupManager.createGroupWithTabs(activity, currentTab, currentTab)
                            currentTabSurface?.setTabGroupId(newGroup.id)
                            currentTabSurface?.setTabGroupTitle(newGroup.title)
                            currentGeckoView?.setTabGroupId(newGroup.id)
                            currentGeckoView?.setTabGroupTitle(newGroup.title)
                            activity.addAlbumInGroup(displayTitle, linkUrl, true, newGroup.id, newGroup.title)
                        } else {
                            activity.addAlbum(displayTitle, linkUrl, true)
                        }
                    }
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(displayTitle, linkUrl, true, true)
                }

                override fun onOpenInExternalApp() {
                    openInExternalApp(activity, linkUrl)
                }

                override fun onPreviewPage() {
                    PetalPagePreviewBridge.show(activity, linkUrl)
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, linkUrl)
                    PetalToast.show(activity, "Link address copied")
                }

                override fun onCopyLinkText() {
                    val text = linkText?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        HelperUnit.copy(activity, text)
                        PetalToast.show(activity, "Link text copied")
                    } else {
                        PetalToast.show(activity, "This link has no text")
                    }
                }

                override fun onBookmarkLink() {
                    activity.saveBookmark(displayTitle, linkUrl)
                }

                override fun onDownloadLink() {
                    try {
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, linkUrl) { fileName, mimeType ->
                            BrowserUnit.download(activity, linkUrl, fileName, mimeType)
                            PetalToast.show(activity, "Download started")
                        }
                    } catch (e: Exception) {
                        PetalToast.show(activity, "Failed to start download")
                    }
                }

                override fun onShareLink() {
                    activity.shareLink(displayTitle, linkUrl)
                }

                override fun onViewInPetalViewer() {
                    if (imageUrl.isNotBlank()) {
                        activity.runOnUiThread {
                            val view = com.petal.browser.compose.downloads.PetalImageViewerBridge.createWebViewerView(
                                activity,
                                imageUrl,
                                displayTitle
                            ) {
                                activity.runOnUiThread { activity.performBackNavigation() }
                            }
                            activity.presentComposeScreen(view)
                        }
                    }
                }

                override fun onOpenImageInNewTab() {
                    activity.addAlbum(HelperUnit.domain(imageUrl), imageUrl, true)
                }

                override fun onOpenImageInBackgroundTab() {
                    activity.addAlbum(HelperUnit.domain(imageUrl), imageUrl, false)
                    PetalToast.show(activity, "Image opened in background tab")
                }

                override fun onCopyImage() {
                    if (imageUrl.isNotBlank()) {
                        ImageActionHelper.copyImage(activity, imageUrl)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onCopyImageAddress() {
                    HelperUnit.copy(activity, imageUrl)
                    PetalToast.show(activity, "Image address copied")
                }

                override fun onDownloadImage() {
                    if (imageUrl.isNotBlank()) {
                        ImageActionHelper.downloadImage(activity, imageUrl)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onShareImage() {
                    if (imageUrl.isNotBlank()) {
                        ImageActionHelper.shareImage(activity, imageUrl)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onAnalyzeWithPetalAi() {
                    if (imageUrl.isNotBlank()) {
                        PetalAiResearchBridge.showAiResearchSheet(
                            activity = activity,
                            pageTitle = "Image Analysis",
                            pageUrl = imageUrl,
                            pageContent = "Please analyze this image and explain what it depicts, key details, and context: $imageUrl",
                            initialMode = ResearchMode.DEEP_RESEARCH,
                            autoStart = true
                        )
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onSearchWithGoogleLens() {
                    if (imageUrl.isNotBlank()) {
                        com.petal.browser.lens.PetalLensManager.searchImageWithGoogleLens(activity, imageUrl)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onScanImage() {
                    if (imageUrl.isNotBlank()) {
                        PetalImageScannerBridge.show(activity, imageUrl)
                    } else {
                        PetalToast.show(activity, "No valid image URL found")
                    }
                }
            }
        )
    }

    /**
     * Unified entry point for GeckoSession.ContentDelegate.onContextMenu.
     * Dynamically determines whether the targeted element is an image, link, image-in-link,
     * video, audio, phone, email, sms, or page context menu.
     */
    @JvmStatic
    @JvmOverloads
    fun handleContextMenu(
        activity: BrowserActivity,
        elemType: Int,
        linkUri: String?,
        srcUri: String?,
        linkText: String? = null,
        title: String? = null,
        alt: String? = null,
        pageUrl: String? = null,
        pageTitle: String? = null,
    ) {
        val hasLink = !linkUri.isNullOrBlank()
        val hasSrc = !srcUri.isNullOrBlank()
        val effectiveText = linkText?.trim()?.takeIf { it.isNotEmpty() }
            ?: title?.trim()?.takeIf { it.isNotEmpty() }
            ?: alt?.trim()?.takeIf { it.isNotEmpty() }

        when {
            // Combined Image inside a Link (<a href="..."><img src="..."></a>)
            hasLink && (hasSrc || elemType == org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) -> {
                showImageLinkContextMenu(activity, linkUri!!, srcUri ?: linkUri, effectiveText)
            }
            // Explicit Image or non-empty image source
            elemType == org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE || (hasSrc && elemType != org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO && elemType != org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO) -> {
                showImageContextMenu(activity, srcUri ?: linkUri.orEmpty(), effectiveText)
            }
            // Video element
            elemType == org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO -> {
                showVideoContextMenu(activity, srcUri ?: linkUri.orEmpty())
            }
            // Audio element
            elemType == org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> {
                showAudioContextMenu(activity, srcUri ?: linkUri.orEmpty())
            }
            // SMS scheme (sms: or smsto:)
            hasLink && (linkUri!!.startsWith("sms:") || linkUri.startsWith("smsto:")) -> {
                showSmsContextMenu(activity, linkUri)
            }
            // Plain Link
            hasLink -> {
                showLinkContextMenu(activity, linkUri!!, effectiveText)
            }
            // Fallback if srcUri is present
            hasSrc -> {
                showImageContextMenu(activity, srcUri!!, effectiveText)
            }
            // Blank / Page Context Menu (Firefox parity: long press on empty web area)
            else -> {
                val effectivePageUrl = pageUrl?.takeIf { it.isNotBlank() }
                    ?: activity.currentAlbumController?.url.orEmpty()
                val effectivePageTitle = pageTitle?.takeIf { it.isNotBlank() }
                    ?: activity.currentAlbumController?.title.orEmpty()
                showPageContextMenu(activity, effectivePageUrl, effectivePageTitle)
            }
        }
    }
}
