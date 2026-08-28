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
                        val lensUrl = "https://lens.google.com/uploadbyurl?url=" + Uri.encode(imageURL)
                        activity.addAlbum(null, lensUrl, true)
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
                    activity.addAlbum(HelperUnit.domain(urlResult), urlResult, false)
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
                        val lensUrl = "https://lens.google.com/uploadbyurl?url=" + Uri.encode(urlResult)
                        activity.addAlbum(null, lensUrl, true)
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
}
