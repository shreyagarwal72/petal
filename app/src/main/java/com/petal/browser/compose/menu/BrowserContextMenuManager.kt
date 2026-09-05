package com.petal.browser.compose.menu

import android.net.Uri
import android.webkit.URLUtil
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.compose.mlkit.PetalImageScannerBridge
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.ImageActionHelper
import com.petal.browser.view.NinjaToast

/**
 * Kotlin Manager encapsulating long-press context menus for BrowserActivity.
 * Fulfills Material 3 Expressive menu handlers for image, link, and video targets.
 */
object BrowserContextMenuManager {

    @JvmStatic
    fun showImageContextMenu(activity: BrowserActivity, imageURL: String) {
        PetalLinkContextMenuBridge.show(
            activity,
            HelperUnit.domain(imageURL),
            imageURL,
            imageURL,
            true,  // isImage
            false, // isVideo
            object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(HelperUnit.domain(imageURL), imageURL, false)
                }

                override fun onOpenImageInNewTab() {
                    activity.addAlbum(HelperUnit.domain(imageURL), imageURL, false)
                }

                override fun onOpenInNewTabInGroup() {
                    activity.addAlbum(HelperUnit.domain(imageURL), imageURL, false)
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(HelperUnit.domain(imageURL), imageURL, false, true)
                }

                override fun onOpenInNewWindow() {
                    activity.addAlbum(HelperUnit.domain(imageURL), imageURL, true)
                }

                override fun onPreviewPage() {
                    activity.addAlbum(HelperUnit.domain(imageURL), imageURL, true)
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, imageURL)
                    NinjaToast.show(activity, "Image URL copied")
                }

                override fun onCopyImage() {
                    HelperUnit.copy(activity, imageURL)
                    NinjaToast.show(activity, "Image copied to clipboard")
                }

                override fun onCopyLinkText() {
                    HelperUnit.copy(activity, HelperUnit.domain(imageURL))
                    NinjaToast.show(activity, "Domain copied")
                }

                override fun onDownloadLink() {
                    try {
                        val fileName = URLUtil.guessFileName(imageURL, null, null)
                        BrowserUnit.download(activity, imageURL, fileName, null)
                        NinjaToast.show(activity, "Download started")
                    } catch (e: Exception) {
                        NinjaToast.show(activity, "Failed to start download")
                    }
                }

                override fun onDownloadImage() {
                    if (imageURL.isNotBlank()) {
                        ImageActionHelper.downloadImage(activity, imageURL)
                    } else {
                        NinjaToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onAddToReadingList() {
                    try {
                        val action = RecordAction(activity)
                        action.open(true)
                        action.addBookmark(Record(HelperUnit.domain(imageURL), imageURL, System.currentTimeMillis(), 0))
                        action.close()
                        NinjaToast.show(activity, "Added to reading list")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onScanImage() {
                    if (imageURL.isNotBlank()) {
                        PetalImageScannerBridge.show(activity, imageURL)
                    } else {
                        NinjaToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onSearchWithGoogleLens() {
                    if (imageURL.isNotBlank()) {
                        com.petal.browser.lens.PetalLensManager.searchImageWithGoogleLens(activity, imageURL)
                    } else {
                        NinjaToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onShareImage() {
                    if (imageURL.isNotBlank()) {
                        ImageActionHelper.shareImage(activity, imageURL)
                    } else {
                        NinjaToast.show(activity, "No valid image URL found")
                    }
                }

                override fun onShareLink() {
                    activity.shareLink(HelperUnit.domain(imageURL), imageURL)
                }

                override fun onDownloadVideo() {}
            }
        )
    }

    @JvmStatic
    fun showLinkContextMenu(activity: BrowserActivity, urlResult: String) {
        PetalLinkContextMenuBridge.show(
            activity,
            HelperUnit.domain(urlResult),
            urlResult,
            "$urlResult/favicon.ico",
            false, // isImage
            false, // isVideo
            object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(HelperUnit.domain(urlResult), urlResult, false)
                }

                override fun onOpenInNewTabInGroup() {
                    val currentAlbum = activity.currentAlbumController
                    val currentGeckoView = currentAlbum as? com.petal.browser.view.PetalGeckoView
                    val currentTabId = currentGeckoView?.getTabId() ?: currentAlbum?.hashCode()?.toString()
                    val existingGroup = if (currentTabId != null) {
                        com.petal.browser.compose.tabs.PetalTabGroupManager.findGroupByTabId(activity, currentTabId)
                    } else null

                    if (existingGroup != null) {
                        activity.addAlbumInGroup(HelperUnit.domain(urlResult), urlResult, false, existingGroup.id, existingGroup.title)
                    } else {
                        // Create a new group for current tab + new tab
                        val currentTab = com.petal.browser.compose.tabs.PetalTabItem(
                            id = currentTabId ?: "current_${System.currentTimeMillis()}",
                            title = currentAlbum?.title ?: "Tab",
                            url = currentAlbum?.url ?: "about:blank"
                        )
                        val newTabDummyId = "temp_${System.currentTimeMillis()}"
                        val newTabDummy = com.petal.browser.compose.tabs.PetalTabItem(
                            id = newTabDummyId,
                            title = HelperUnit.domain(urlResult),
                            url = urlResult
                        )
                        val newGroup = com.petal.browser.compose.tabs.PetalTabGroupManager.createGroupWithTabs(activity, currentTab, newTabDummy)
                        currentGeckoView?.setTabGroupId(newGroup.id)
                        currentGeckoView?.setTabGroupTitle(newGroup.title)
                        activity.addAlbumInGroup(HelperUnit.domain(urlResult), urlResult, false, newGroup.id, newGroup.title)
                    }
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(HelperUnit.domain(urlResult), urlResult, false, true)
                }

                override fun onOpenInNewWindow() {
                    activity.addAlbum(HelperUnit.domain(urlResult), urlResult, true)
                }

                override fun onPreviewPage() {
                    activity.addAlbum(activity.getString(R.string.app_name), urlResult, true)
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, urlResult)
                    NinjaToast.show(activity, "Link copied")
                }

                override fun onCopyLinkText() {
                    HelperUnit.copy(activity, HelperUnit.domain(urlResult))
                    NinjaToast.show(activity, "Link text copied")
                }

                override fun onDownloadLink() {
                    try {
                        val fileName = URLUtil.guessFileName(urlResult, null, null)
                        BrowserUnit.download(activity, urlResult, fileName, null)
                        NinjaToast.show(activity, "Download started")
                    } catch (e: Exception) {
                        NinjaToast.show(activity, "Failed to start download")
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
                        action.addBookmark(Record(HelperUnit.domain(urlResult), urlResult, System.currentTimeMillis(), 0))
                        action.close()
                        NinjaToast.show(activity, "Added to reading list")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onScanImage() {
                    if (urlResult.isNotBlank()) {
                        PetalImageScannerBridge.show(activity, urlResult)
                    }
                }

                override fun onShareImage() {
                    if (urlResult.isNotBlank()) {
                        ImageActionHelper.shareImage(activity, urlResult)
                    }
                }

                override fun onShareLink() {
                    activity.shareLink(HelperUnit.domain(urlResult), urlResult)
                }

                override fun onSearchWithGoogleLens() {
                    if (urlResult.isNotBlank()) {
                        com.petal.browser.lens.PetalLensManager.searchImageWithGoogleLens(activity, urlResult)
                    }
                }

                override fun onSendEmail(mailto: String) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, Uri.parse(mailto))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        NinjaToast.show(activity, "No email client found")
                    }
                }

                override fun onDialPhoneNumber(tel: String) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse(tel))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        NinjaToast.show(activity, "No phone app found")
                    }
                }

                override fun onOpenMapLocation(geo: String) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(geo))
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        NinjaToast.show(activity, "No maps app found")
                    }
                }

                override fun onOpenImageInNewTab() {}
                override fun onCopyImage() {}
                override fun onDownloadVideo() {}
            }
        )
    }

    @JvmStatic
    fun showVideoContextMenu(activity: BrowserActivity, cleanVideoUrl: String) {
        PetalLinkContextMenuBridge.show(
            activity,
            HelperUnit.domain(cleanVideoUrl),
            cleanVideoUrl,
            null,
            false, // isImage
            true,  // isVideo
            object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(HelperUnit.domain(cleanVideoUrl), cleanVideoUrl, false)
                }

                override fun onDownloadVideo() {
                    try {
                        val fileName = URLUtil.guessFileName(cleanVideoUrl, null, "video/mp4")
                        BrowserUnit.download(activity, cleanVideoUrl, fileName, null)
                        NinjaToast.show(activity, "Video download started")
                    } catch (e: Exception) {
                        NinjaToast.show(activity, "Failed to start video download")
                    }
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, cleanVideoUrl)
                    NinjaToast.show(activity, "Video link copied")
                }

                override fun onShareLink() {
                    activity.shareLink(HelperUnit.domain(cleanVideoUrl), cleanVideoUrl)
                }

                override fun onOpenInNewTabInGroup() {}
                override fun onOpenInIncognitoTab() {}
                override fun onOpenInNewWindow() {}
                override fun onPreviewPage() {}
                override fun onCopyLinkText() {}
                override fun onDownloadLink() {}
                override fun onOpenImageInNewTab() {}
                override fun onCopyImage() {}
                override fun onDownloadImage() {}
                override fun onAddToReadingList() {}
                override fun onShareImage() {}
                override fun onScanImage() {}
                override fun onSearchWithGoogleLens() {}
            }
        )
    }

    @JvmStatic
    fun showAudioContextMenu(activity: BrowserActivity, audioUrl: String) {
        PetalLinkContextMenuBridge.show(
            activity = activity,
            linkTitle = HelperUnit.domain(audioUrl),
            linkUrl = audioUrl,
            faviconUrl = null,
            isImage = false,
            isVideo = false,
            isAudio = true,
            selectedText = null,
            handler = object : PetalLinkContextMenuHandler {
                override fun onOpenInNewTab() {
                    activity.addAlbum(HelperUnit.domain(audioUrl), audioUrl, false)
                }

                override fun onDownloadAudio() {
                    try {
                        val fileName = URLUtil.guessFileName(audioUrl, null, "audio/*")
                        BrowserUnit.download(activity, audioUrl, fileName, null)
                        NinjaToast.show(activity, "Audio download started")
                    } catch (e: Exception) {
                        NinjaToast.show(activity, "Failed to start audio download")
                    }
                }

                override fun onCopyLinkAddress() {
                    HelperUnit.copy(activity, audioUrl)
                    NinjaToast.show(activity, "Audio link copied")
                }

                override fun onShareLink() {
                    activity.shareLink(HelperUnit.domain(audioUrl), audioUrl)
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
                    activity.addAlbum(text.take(24), query, false)
                }

                override fun onCopySelectedText(text: String) {
                    HelperUnit.copy(activity, text)
                    NinjaToast.show(activity, "Text copied")
                }

                override fun onShareSelectedText(text: String) {
                    activity.shareLink("Shared Text", text)
                }
            }
        )
    }
}
