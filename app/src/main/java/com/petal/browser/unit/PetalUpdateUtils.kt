package com.petal.browser.unit

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.petal.browser.BuildConfig
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.ui.components.PetalUpdateInfo
import com.petal.browser.ui.components.PetalUpdateSheetBridge
import com.petal.browser.update.PetalPlayUpdateManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Petal Browser Update Engine with Material 3 Expressive UI.
 * Strictly tracks and notifies the user only when a newer update version is available.
 */
object PetalUpdateUtils {

    private const val TAG = "PetalUpdateUtils"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/shreyagarwal72/petal/releases/latest"
    private const val GITHUB_RELEASES_PAGE = "https://github.com/shreyagarwal72/petal/releases"
    private const val PREF_KEY_LAST_CHECK_TIME = "sp_update_last_check_timestamp"
    private const val PREF_KEY_SKIP_VERSION = "sp_update_skipped_version"

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun checkForUpdates(activity: Activity?, isLaunchCheck: Boolean) {
        checkForUpdates(activity, isLaunchCheck, null)
    }

    @JvmStatic
    fun checkForUpdates(activity: Activity?, isLaunchCheck: Boolean, onComplete: Runnable?) {
        if (activity == null || activity.isFinishing) {
            onComplete?.run()
            return
        }

        if (BuildConfig.IS_PLAY_STORE_BUILD) {
            if (activity is ComponentActivity) {
                PetalPlayUpdateManager.getInstance(activity)
                    .checkForUpdates(activity, isLaunchCheck)
            }
            onComplete?.run()
            return
        }

        val context = activity.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        // Track last check timestamp
        sp.edit().putLong(PREF_KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

        executor.execute {
            try {
                val currentVersion = getAppVersion(activity)
                val json = fetchLatestReleaseJson(currentVersion)
                if (json != null) {
                    val latestTag = json.optString("tag_name", currentVersion)
                    val releaseNotes = json.optString(
                        "body",
                        "Performance polish, security enhancements, and stability improvements."
                    )

                    var apkDownloadUrl = json.optString("html_url", GITHUB_RELEASES_PAGE)
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        var abiUrl: String? = null
                        var abiRank = Int.MAX_VALUE
                        var universalUrl: String? = null
                        var fallbackApkUrl: String? = null
                        val abis = Build.SUPPORTED_ABIS
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val assetName = asset.optString("name", "")
                            if (!assetName.endsWith(".apk")) continue
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (downloadUrl.isEmpty()) continue
                            val lower = assetName.lowercase()
                            if (lower.contains("universal")) {
                                if (universalUrl == null) universalUrl = downloadUrl
                                continue
                            }
                            if (abis != null) {
                                for (a in 0 until Math.min(abis.size, abiRank)) {
                                    if (lower.contains("-" + abis[a].lowercase() + "-")) {
                                        abiUrl = downloadUrl
                                        abiRank = a
                                        break
                                    }
                                }
                            }
                            if (fallbackApkUrl == null) fallbackApkUrl = downloadUrl
                        }
                        if (abiUrl != null) {
                            apkDownloadUrl = abiUrl
                        } else if (universalUrl != null) {
                            apkDownloadUrl = universalUrl
                        } else if (fallbackApkUrl != null) {
                            apkDownloadUrl = fallbackApkUrl
                        }
                    }
                    val finalDownloadUrl = apkDownloadUrl

                    val isNextUpdateAvailable = isNewerVersion(latestTag, currentVersion)
                    val skippedVersion = sp.getString(PREF_KEY_SKIP_VERSION, "") ?: ""
                    val isSkipped = isLaunchCheck && latestTag.equals(skippedVersion, ignoreCase = true)

                    if (isNextUpdateAvailable && !isSkipped) {
                        sendUpdateNotification(context, latestTag, finalDownloadUrl)
                    }

                    activity.runOnUiThread {
                        onComplete?.run()
                        if (activity.isFinishing) return@runOnUiThread

                        if (activity is ComponentActivity) {
                            if (isNextUpdateAvailable && !isSkipped) {
                                PetalUpdateSheetBridge.showUpdateSheet(
                                    activity,
                                    PetalUpdateInfo(
                                        latestTag,
                                        releaseNotes,
                                        finalDownloadUrl,
                                        GITHUB_RELEASES_PAGE,
                                        true
                                    )
                                )
                            } else if (!isLaunchCheck) {
                                PetalUpdateSheetBridge.showUpdateSheet(
                                    activity,
                                    PetalUpdateInfo(
                                        currentVersion,
                                        releaseNotes,
                                        finalDownloadUrl,
                                        GITHUB_RELEASES_PAGE,
                                        false
                                    )
                                )
                            }
                        }
                    }
                } else if (!isLaunchCheck) {
                    activity.runOnUiThread {
                        onComplete?.run()
                        if (activity is ComponentActivity) {
                            PetalUpdateSheetBridge.showUpdateSheet(
                                activity,
                                PetalUpdateInfo(
                                    getAppVersion(activity),
                                    "You are currently running the latest build of Petal Browser.",
                                    "",
                                    GITHUB_RELEASES_PAGE,
                                    false
                                )
                            )
                        }
                    }
                } else {
                    if (onComplete != null) activity.runOnUiThread(onComplete)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                activity.runOnUiThread {
                    onComplete?.run()
                    if (!isLaunchCheck && activity is ComponentActivity) {
                        PetalUpdateSheetBridge.showUpdateSheet(
                            activity,
                            PetalUpdateInfo(
                                getAppVersion(activity),
                                "You are currently running the latest build of Petal Browser.",
                                "",
                                GITHUB_RELEASES_PAGE,
                                false
                            )
                        )
                    }
                }
            }
        }
    }

    @JvmStatic
    fun sendUpdateNotification(context: Context?, latestTag: String, downloadUrl: String) {
        if (context == null) return
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = "petal_updates_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new Petal Browser updates and releases"
                }
                nm.createNotificationChannel(channel)
            }

            val customLines = arrayOf(
                "Step into the next era. Update now.",
                "Something massive just landed. Update to unlock it.",
                "Warning: Updating may cause extreme satisfaction"
            )
            val body = customLines[Random().nextInt(customLines.size)]

            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.petal.browser.action.SHOW_UPDATE"
                putExtra("update_version", latestTag)
                putExtra("update_url", downloadUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                9901,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Petal Browser $latestTag Available!")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            nm.notify(9901, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error posting update notification", e)
        }
    }

    private fun getAppVersion(activity: Activity): String {
        return try {
            val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            "v" + pInfo.versionName
        } catch (_: Exception) {
            "v1.0.2"
        }
    }

    private fun fetchLatestReleaseJson(currentVersion: String): JSONObject? {
        try {
            val url = URL(GITHUB_RELEASES_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "PetalBrowserApp/$currentVersion")

            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) sb.append(line)
                    return JSONObject(sb.toString())
                }
            }
        } catch (_: Exception) {}

        // Fallback: fetch list of releases in case /releases/latest returns 404 or draft
        try {
            val url = URL("https://api.github.com/repos/shreyagarwal72/petal/releases")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "PetalBrowserApp/$currentVersion")

            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) sb.append(line)

                    val array = JSONArray(sb.toString())
                    var bestRelease: JSONObject? = null
                    var bestTag = ""
                    for (i in 0 until array.length()) {
                        val rel = array.getJSONObject(i)
                        val tag = rel.optString("tag_name", "")
                        if (bestRelease == null || isNewerVersion(tag, bestTag)) {
                            bestRelease = rel
                            bestTag = tag
                        }
                    }
                    return bestRelease
                }
            }
        } catch (_: Exception) {}

        return null
    }

    @JvmStatic
    fun isNewerVersion(latest: String?, current: String?): Boolean {
        if (latest == null || current == null) return false
        val cleanLatest = latest.trim().replace("(?i)^[vV_\\s-]+".toRegex(), "").replace("(?i)[^0-9.].*$".toRegex(), "")
        val cleanCurrent = current.trim().replace("(?i)^[vV_\\s-]+".toRegex(), "").replace("(?i)[^0-9.].*$".toRegex(), "")

        if (cleanLatest.isEmpty() || cleanCurrent.isEmpty()) return false

        val latestParts = cleanLatest.split(".")
        val currentParts = cleanCurrent.split(".")

        val length = Math.max(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            var latestNum = 0
            var currentNum = 0
            if (i < latestParts.size) {
                try {
                    latestNum = latestParts[i].replace("[^0-9]".toRegex(), "").toInt()
                } catch (_: Exception) {}
            }
            if (i < currentParts.size) {
                try {
                    currentNum = currentParts[i].replace("[^0-9]".toRegex(), "").toInt()
                } catch (_: Exception) {}
            }
            if (latestNum > currentNum) return true
            if (latestNum < currentNum) return false
        }
        return false
    }
}
