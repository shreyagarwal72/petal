package com.petal.browser.unit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import com.petal.browser.ui.components.PetalAlertDialogBuilder
import com.petal.browser.view.PetalToast
import java.io.File
import java.util.ArrayList

object Util1DM {
    const val PACKAGE_NAME_1DM_PLUS = "idm.internet.download.manager.plus"
    const val PACKAGE_NAME_1DM_NORMAL = "idm.internet.download.manager"
    const val PACKAGE_NAME_1DM_LITE = "idm.internet.download.manager.adm.lite"
    const val DOWNLOADER_ACTIVITY_NAME_1DM = "idm.internet.download.manager.Downloader"
    const val SECURE_URI_1DM_SUPPORT_MIN_VERSION_CODE = 169
    const val HEADERS_AND_MULTIPLE_LINKS_1DM_SUPPORT_MIN_VERSION_CODE = 157
    const val GOOGLE_PLAY_STORE_SCHEMA = "market://details?id="
    const val HUAWEI_APP_GALLERY_SCHEMA = "appmarket://details?id="
    const val GOOGLE_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id="
    const val EXTRA_SECURE_URI = "secure_uri"
    const val EXTRA_COOKIES = "extra_cookies"
    const val EXTRA_USERAGENT = "extra_useragent"
    const val EXTRA_REFERER = "extra_referer"
    const val EXTRA_HEADERS = "extra_headers"
    const val EXTRA_FILENAME = "extra_filename"
    const val EXTRA_URL_LIST = "url_list"
    const val EXTRA_URL_FILENAME_LIST = "url_list.filename"
    const val MESSAGE_INSTALL_1DM = "To download content install 1DM"
    const val MESSAGE_UPDATE_1DM = "To download content update 1DM"

    enum class AppState { OK, UPDATE_REQUIRED, NOT_INSTALLED }

    @JvmStatic
    fun is1DMInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return get1DMAppState(pm, PACKAGE_NAME_1DM_PLUS, 0) != AppState.NOT_INSTALLED ||
                get1DMAppState(pm, PACKAGE_NAME_1DM_NORMAL, 0) != AppState.NOT_INSTALLED ||
                get1DMAppState(pm, PACKAGE_NAME_1DM_LITE, 0) != AppState.NOT_INSTALLED
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadTorrent(activity: Activity, torrentUrl: String, askUserToInstall1DMIfNotInstalled: Boolean) {
        downloadFilesInternal(activity, null, torrentUrl, null, null, null, null, null, false, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadTorrent(activity: Activity, torrentUrl: String, headers: Map<String, String>?, askUserToInstall1DMIfNotInstalled: Boolean) {
        downloadFilesInternal(activity, null, torrentUrl, null, null, null, null, headers, false, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadMagnet(activity: Activity, magnetUrl: String, askUserToInstall1DMIfNotInstalled: Boolean) {
        downloadFilesInternal(activity, null, magnetUrl, null, null, null, null, null, false, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadFile(activity: Activity, url: String, secureUri: Boolean, askUserToInstall1DMIfNotInstalled: Boolean) {
        downloadFilesInternal(activity, null, url, null, null, null, null, null, secureUri, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadFile(activity: Activity, url: String, headers: Map<String, String>?, secureUri: Boolean, askUserToInstall1DMIfNotInstalled: Boolean) {
        downloadFilesInternal(activity, null, url, null, null, null, null, headers, secureUri, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadFile(
        activity: Activity,
        url: String,
        referer: String?,
        fileName: String?,
        userAgent: String?,
        cookies: String?,
        secureUri: Boolean,
        askUserToInstall1DMIfNotInstalled: Boolean
    ) {
        downloadFilesInternal(activity, null, url, referer, fileName, userAgent, cookies, null, secureUri, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadFile(
        activity: Activity,
        url: String,
        referer: String?,
        fileName: String?,
        userAgent: String?,
        cookies: String?,
        headers: Map<String, String>?,
        secureUri: Boolean,
        askUserToInstall1DMIfNotInstalled: Boolean
    ) {
        downloadFilesInternal(activity, null, url, referer, fileName, userAgent, cookies, headers, secureUri, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadFiles(
        activity: Activity,
        urlAndFileNames: Map<String, String>,
        secureUri: Boolean,
        askUserToInstall1DMIfNotInstalled: Boolean
    ) {
        downloadFilesInternal(activity, urlAndFileNames, null, null, null, null, null, null, secureUri, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadFiles(
        activity: Activity,
        urlAndFileNames: Map<String, String>,
        headers: Map<String, String>?,
        secureUri: Boolean,
        askUserToInstall1DMIfNotInstalled: Boolean
    ) {
        downloadFilesInternal(activity, urlAndFileNames, null, null, null, null, null, headers, secureUri, askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadTorrentFile(activity: Activity, torrent: File, askUserToInstall1DMIfNotInstalled: Boolean) {
        downloadTorrentFile(activity, torrent, activity.packageName + ".fileprovider", askUserToInstall1DMIfNotInstalled)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun downloadTorrentFile(
        activity: Activity,
        torrentFile: File,
        authority: String,
        askUserToInstall1DMIfNotInstalled: Boolean
    ) {
        val packageName = get1DMInstalledPackageName(activity, 0, askUserToInstall1DMIfNotInstalled) ?: return
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(activity, authority, torrentFile)
        } else {
            Uri.fromFile(torrentFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(packageName, DOWNLOADER_ACTIVITY_NAME_1DM)
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun downloadFilesInternal(
        activity: Activity,
        urlAndFileNames: Map<String, String>?,
        url: String?,
        referer: String?,
        fileName: String?,
        userAgent: String?,
        cookies: String?,
        headers: Map<String, String>?,
        secureUri: Boolean,
        askUserToInstall1DMIfNotInstalled: Boolean
    ) {
        val requiredVersionCode = if (secureUri) {
            SECURE_URI_1DM_SUPPORT_MIN_VERSION_CODE
        } else if (!urlAndFileNames.isNullOrEmpty() || !headers.isNullOrEmpty()) {
            HEADERS_AND_MULTIPLE_LINKS_1DM_SUPPORT_MIN_VERSION_CODE
        } else {
            0
        }
        val packageName = get1DMInstalledPackageName(activity, requiredVersionCode, askUserToInstall1DMIfNotInstalled) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(packageName, DOWNLOADER_ACTIVITY_NAME_1DM)
            putExtra(EXTRA_SECURE_URI, secureUri)
        }
        if (urlAndFileNames.isNullOrEmpty()) {
            if (!url.isNullOrEmpty()) {
                intent.data = Uri.parse(url)
            }
            if (!referer.isNullOrEmpty()) intent.putExtra(EXTRA_REFERER, referer)
            if (!userAgent.isNullOrEmpty()) intent.putExtra(EXTRA_USERAGENT, userAgent)
            if (!cookies.isNullOrEmpty()) intent.putExtra(EXTRA_COOKIES, cookies)
            if (!fileName.isNullOrEmpty()) intent.putExtra(EXTRA_FILENAME, fileName)
        } else {
            val urls = ArrayList<String>(urlAndFileNames.size)
            val names = ArrayList<String>(urlAndFileNames.size)
            for ((key, value) in urlAndFileNames) {
                if (key.isNotEmpty()) {
                    urls.add(key)
                    names.add(value)
                }
            }
            if (urls.isNotEmpty()) {
                intent.putExtra(EXTRA_URL_LIST, urls)
                intent.putExtra(EXTRA_URL_FILENAME_LIST, names)
                intent.data = Uri.parse(urls[0])
            }
        }
        if (!headers.isNullOrEmpty()) {
            val extra = Bundle()
            for ((key, value) in headers) {
                extra.putString(key, value)
            }
            intent.putExtra(EXTRA_HEADERS, extra)
        }
        activity.startActivity(intent)
    }

    @JvmStatic
    fun install1DM(activity: Activity, packageName: String, update: Boolean) {
        val builder = PetalAlertDialogBuilder(activity)
        builder.setMessage(if (update) MESSAGE_UPDATE_1DM else MESSAGE_INSTALL_1DM)
            .setPositiveButton(if (update) "Update" else "Install") { _, _ ->
                try {
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PLAY_STORE_SCHEMA + packageName + getStoreTracking(activity))))
                    } catch (_: ActivityNotFoundException) {
                        try {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HUAWEI_APP_GALLERY_SCHEMA + packageName + getStoreTracking(activity))))
                        } catch (_: ActivityNotFoundException) {
                            try {
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PLAY_STORE_URL + packageName + getStoreTracking(activity))))
                            } catch (e2: ActivityNotFoundException) {
                                PetalToast.show(activity, e2.message, PetalToast.LENGTH_SHORT)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    PetalToast.show(activity, t.message, PetalToast.LENGTH_SHORT)
                }
            }
        val dialog = builder.create()
        PetalAndroidUtils.setupDialog(activity, dialog)
        dialog.show()
    }

    private fun getStoreTracking(context: Context): String {
        return "&referrer=utm_source%3D" + context.packageName + "%26utm_medium%3DApp%26utm_campaign%3DDownload"
    }

    private fun get1DMInstalledPackageName(activity: Activity, requiredVersionCode: Int, askUserToInstall1DMIfNotInstalled: Boolean): String? {
        val packageManager = activity.packageManager
        var packageName = PACKAGE_NAME_1DM_PLUS
        var state = get1DMAppState(packageManager, packageName, requiredVersionCode)
        if (state == AppState.NOT_INSTALLED) {
            packageName = PACKAGE_NAME_1DM_NORMAL
            state = get1DMAppState(packageManager, packageName, requiredVersionCode)
            if (state == AppState.NOT_INSTALLED) {
                packageName = PACKAGE_NAME_1DM_LITE
                state = get1DMAppState(packageManager, packageName, requiredVersionCode)
                if (state == AppState.NOT_INSTALLED) {
                    if (askUserToInstall1DMIfNotInstalled) {
                        install1DM(activity, PACKAGE_NAME_1DM_NORMAL, false)
                        return null
                    } else {
                        throw Exception(MESSAGE_INSTALL_1DM)
                    }
                }
            }
        }
        if (state == AppState.UPDATE_REQUIRED) {
            if (askUserToInstall1DMIfNotInstalled) {
                install1DM(activity, packageName, true)
                return null
            } else {
                throw Exception(MESSAGE_UPDATE_1DM)
            }
        }
        return packageName
    }

    private fun get1DMAppState(packageManager: PackageManager, packageName: String, requiredVersion: Int): AppState {
        return try {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val versionCode = packageInfo.versionCode
            if (requiredVersion <= 0 || versionCode >= requiredVersion) {
                AppState.OK
            } else {
                AppState.UPDATE_REQUIRED
            }
        } catch (_: PackageManager.NameNotFoundException) {
            AppState.NOT_INSTALLED
        }
    }
}
