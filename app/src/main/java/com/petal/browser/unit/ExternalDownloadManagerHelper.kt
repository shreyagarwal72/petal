package com.petal.browser.unit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.petal.browser.view.NinjaToast
import java.util.Locale

/**
 * ExternalDownloadManagerHelper
 * Supports detecting installed third-party download managers (1DM, ADM, AB Download Manager, Navi)
 * and dispatching download links with full headers, cookies, user-agent, referer, and filename metadata.
 */
object ExternalDownloadManagerHelper {

    // Package identifiers for popular download managers
    const val PKG_1DM_PLUS = "idm.internet.download.manager.plus"
    const val PKG_1DM_NORMAL = "idm.internet.download.manager"
    const val PKG_1DM_LITE = "idm.internet.download.manager.adm.lite"

    const val PKG_ADM_NORMAL = "com.dv.adm"
    const val PKG_ADM_PRO = "com.dv.adm.pay"

    const val PKG_AB_DM_1 = "com.abdownloadmanager"
    const val PKG_AB_DM_2 = "com.ab.abdownloadmanager"

    const val PKG_NAVI_NORMAL = "com.arun.navi"
    const val PKG_NAVI_PRO = "com.arun.navi.pro"

    enum class ExternalDownloader(
        val key: String,
        val displayName: String,
        val packageNames: List<String>
    ) {
        ENGINE_1DM("1DM", "1DM / 1DM+", listOf(PKG_1DM_PLUS, PKG_1DM_NORMAL, PKG_1DM_LITE)),
        ENGINE_ADM("ADM", "Advanced Download Manager (ADM)", listOf(PKG_ADM_NORMAL, PKG_ADM_PRO)),
        ENGINE_AB_DM("AB_DM", "AB Download Manager", listOf(PKG_AB_DM_1, PKG_AB_DM_2)),
        ENGINE_NAVI("NAVI", "Navi Download Manager", listOf(PKG_NAVI_NORMAL, PKG_NAVI_PRO))
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun hasAnyExternalDownloaderInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (downloader in ExternalDownloader.values()) {
            for (pkg in downloader.packageNames) {
                if (isPackageInstalled(pm, pkg)) return true
            }
        }
        return false
    }

    @JvmStatic
    fun getInstalledDownloaders(context: Context): List<ExternalDownloader> {
        val pm = context.packageManager
        val installed = mutableListOf<ExternalDownloader>()
        for (downloader in ExternalDownloader.values()) {
            val hasPkg = downloader.packageNames.any { isPackageInstalled(pm, it) }
            if (hasPkg) {
                installed.add(downloader)
            }
        }
        return installed
    }

    @JvmStatic
    fun launchDownloadInExternalManager(
        activity: Activity,
        url: String,
        fileName: String?,
        mimeType: String?,
        preferredDownloader: ExternalDownloader? = null
    ): Boolean {
        val pm = activity.packageManager
        val targetDownloader = preferredDownloader ?: getInstalledDownloaders(activity).firstOrNull()

        val verifiedUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            "https://$url"
        } else {
            url
        }

        val userAgent = try { WebSettings.getDefaultUserAgent(activity) } catch (e: Exception) { null }
        val cookie = try { CookieManager.getInstance().getCookie(verifiedUrl) } catch (e: Exception) { null }

        // 1. If preferred or found is 1DM, use Util1DM for direct intent component support
        if (targetDownloader == ExternalDownloader.ENGINE_1DM || (targetDownloader == null && Util1DM.is1DMInstalled(activity))) {
            try {
                val headers = HashMap<String, String>()
                headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                headers["Accept-Language"] = Locale.getDefault().toLanguageTag()
                headers["Referer"] = verifiedUrl

                Util1DM.downloadFile(
                    activity,
                    verifiedUrl,
                    verifiedUrl,
                    fileName,
                    userAgent,
                    cookie,
                    headers,
                    false,
                    false
                )
                NinjaToast.show(activity, "Opening in 1DM...")
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Specific intent targets for ADM, AB DM, or Navi
        if (targetDownloader != null) {
            for (pkg in targetDownloader.packageNames) {
                if (isPackageInstalled(pm, pkg)) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(verifiedUrl), mimeType ?: "*/*")
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (!fileName.isNullOrBlank()) {
                            putExtra("android.intent.extra.TITLE", fileName)
                            putExtra("extra_filename", fileName)
                            putExtra("file_name", fileName)
                            putExtra("name", fileName)
                        }
                        if (!userAgent.isNullOrBlank()) {
                            putExtra("extra_useragent", userAgent)
                            putExtra("user_agent", userAgent)
                        }
                        if (!cookie.isNullOrBlank()) {
                            putExtra("extra_cookies", cookie)
                            putExtra("cookie", cookie)
                        }
                        putExtra("extra_referer", verifiedUrl)
                        putExtra("referer", verifiedUrl)
                    }
                    try {
                        activity.startActivity(intent)
                        NinjaToast.show(activity, "Opening in ${targetDownloader.displayName}...")
                        return true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 3. Fallback: Generic ACTION_VIEW system chooser with download extras
        return try {
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(verifiedUrl), mimeType ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!fileName.isNullOrBlank()) {
                    putExtra("android.intent.extra.TITLE", fileName)
                    putExtra("extra_filename", fileName)
                    putExtra("file_name", fileName)
                }
                if (!userAgent.isNullOrBlank()) {
                    putExtra("extra_useragent", userAgent)
                }
                if (!cookie.isNullOrBlank()) {
                    putExtra("extra_cookies", cookie)
                }
                putExtra("extra_referer", verifiedUrl)
            }
            activity.startActivity(Intent.createChooser(genericIntent, "Download with..."))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
