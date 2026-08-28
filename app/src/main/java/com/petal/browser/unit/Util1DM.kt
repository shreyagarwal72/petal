package com.petal.browser.unit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Map

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
        return get1DMAppState(pm, PACKAGE_NAME_1DM_PLUS, 0) != AppState.NOT_INSTALLED
                || get1DMAppState(pm, PACKAGE_NAME_1DM_NORMAL, 0) != AppState.NOT_INSTALLED
                || get1DMAppState(pm, PACKAGE_NAME_1DM_LITE, 0) != AppState.NOT_INSTALLED
    }

    @JvmStatic
    fun get1DMAppState(pm: PackageManager, packageName: String, minVersionCode: Int): AppState {
        return try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, 0)
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
            if (versionCode >= minVersionCode) AppState.OK else AppState.UPDATE_REQUIRED
        } catch (e: PackageManager.NameNotFoundException) {
            AppState.NOT_INSTALLED
        }
    }

    @JvmStatic
    fun download(
        activity: Activity,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookie: String?
    ) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(PACKAGE_NAME_1DM_NORMAL, DOWNLOADER_ACTIVITY_NAME_1DM)
            data = Uri.parse(url)
            if (cookie != null) putExtra(EXTRA_COOKIES, cookie)
            if (userAgent != null) putExtra(EXTRA_USERAGENT, userAgent)
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, MESSAGE_INSTALL_1DM, Toast.LENGTH_SHORT).show()
        }
    }
}
