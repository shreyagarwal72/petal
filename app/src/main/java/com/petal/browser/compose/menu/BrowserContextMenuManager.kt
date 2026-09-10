package com.petal.browser.compose.menu

import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.lifecycleScope
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.compose.mlkit.PetalImageScannerBridge
import com.petal.browser.download.DownloadFileNameResolver
import com.petal.browser.unit.BrowserUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.unit.ImageActionHelper
import com.petal.browser.view.NinjaToast

/**
 * Kotlin Manager encapsulating long-press context menus for BrowserActivity.
 * Fulfills Material 3 Expressive menu handlers for image, link, and video targets.
 */
object BrowserContextMenuManager {

    /**
     * Launches [targetUrl] in a second, independent Android window using freeform/
     * adjacent multi-window mode (FLAG_ACTIVITY_NEW_TASK + MULTIPLE_TASK + LAUNCH_ADJACENT).
     * BrowserActivity is declared singleTask in the manifest for its VIEW/BROWSABLE
     * entry point, which on its own blocks a second instance; MULTIPLE_TASK is what
     * overrides that and allows a genuinely separate task/window to be created here.
     * Whether this actually renders as a floating/freeform window, a split-screen
     * pane, or (on phones with no multi-window support) just focuses/reuses the
     * existing window depends on the device and launcher - this requests freeform,
     * it does not force it, since no such guarantee exists in the public API.
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
            // Freeform/adjacent launch not supported on this device/launcher -
            // fall back to a normal foreground tab rather than doing nothing.
            activity.addAlbum(HelperUnit.domain(targetUrl), targetUrl, true)
        }
    }

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
                    launchInNewWindow(activity, imageURL)
                }

                override fun onPreviewPage() {
                    PetalPagePreviewBridge.show(activity, imageURL)
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
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, imageURL) { fileName, mimeType ->
                            BrowserUnit.download(activity, imageURL, fileName, mimeType)
                            NinjaToast.show(activity, "Download started")
                        }
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
                        val record = Record(HelperUnit.domain(imageURL), imageURL, System.currentTimeMillis(), 0)
                        record.isReadingList = true
                        action.addBookmark(record)
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

                override fun onViewInPetalViewer() {
                    if (imageURL.isNotBlank()) {
                        activity.runOnUiThread {
                            val view = com.petal.browser.compose.downloads.PetalImageViewerBridge.createWebViewerView(
                                activity,
                                imageURL,
                                HelperUnit.domain(imageURL)
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
                        // addAlbumInGroup creates the new tab, then registers its real tab
                        // ID (from setWebView's return value, not currentAlbumController)
                        // into the existing group's persisted membership list.
                        activity.addAlbumInGroup(HelperUnit.domain(urlResult), urlResult, false, existingGroup.id, existingGroup.title)
                    } else {
                        // No group yet: create one seeded with the current tab, then open
                        // the new tab and register its real ID into that same group. The
                        // group is created with only the current tab as a member here -
                        // addAlbumInGroup below adds the new tab once it actually exists,
                        // rather than seeding the group with a placeholder ID for a tab
                        // that doesn't exist yet.
                        if (currentTabId != null) {
                            val currentTab = com.petal.browser.compose.tabs.PetalTabItem(
                                id = currentTabId,
                                title = currentAlbum?.title ?: "Tab",
                                url = currentAlbum?.url ?: "about:blank"
                            )
                            val newGroup = com.petal.browser.compose.tabs.PetalTabGroupManager.createGroupWithTabs(activity, currentTab, currentTab)
                            currentGeckoView?.setTabGroupId(newGroup.id)
                            currentGeckoView?.setTabGroupTitle(newGroup.title)
                            activity.addAlbumInGroup(HelperUnit.domain(urlResult), urlResult, false, newGroup.id, newGroup.title)
                        } else {
                            // No identifiable current tab to group with - fall back to a
                            // plain new tab rather than creating a group with no real members.
                            activity.addAlbum(HelperUnit.domain(urlResult), urlResult, false)
                        }
                    }
                }

                override fun onOpenInIncognitoTab() {
                    activity.addAlbum(HelperUnit.domain(urlResult), urlResult, false, true)
                }

                override fun onOpenInNewWindow() {
                    launchInNewWindow(activity, urlResult)
                }

                override fun onPreviewPage() {
                    PetalPagePreviewBridge.show(activity, urlResult)
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
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, urlResult) { fileName, mimeType ->
                            BrowserUnit.download(activity, urlResult, fileName, mimeType)
                            NinjaToast.show(activity, "Download started")
                        }
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
                        val record = Record(HelperUnit.domain(urlResult), urlResult, System.currentTimeMillis(), 0)
                        record.isReadingList = true
                        action.addBookmark(record)
                        action.close()
                        NinjaToast.show(activity, "Added to reading list")
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
                    activity.shareLink(HelperUnit.domain(urlResult), urlResult)
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
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, cleanVideoUrl, fallbackMimeType = "video/mp4") { fileName, mimeType ->
                            BrowserUnit.download(activity, cleanVideoUrl, fileName, mimeType)
                            NinjaToast.show(activity, "Video download started")
                        }
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
                        DownloadFileNameResolver.resolve(activity.lifecycleScope, audioUrl) { fileName, mimeType ->
                            BrowserUnit.download(activity, audioUrl, fileName, mimeType)
                            NinjaToast.show(activity, "Audio download started")
                        }
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
