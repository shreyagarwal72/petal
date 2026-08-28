package com.petal.browser.unit

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.petal.browser.R
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.components.PetalUpdateInfo
import com.petal.browser.ui.components.PetalUpdateSheetBridge
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
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
object UpdateUnit {

    private const val TAG = "UpdateUnit"
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
                    val releaseNotes = json.optString("body", "Performance polish, security enhancements, and stability improvements.")

                    // Locate direct APK asset download URL if available, fallback to html_url release page
                    var apkDownloadUrl = json.optString("html_url", GITHUB_RELEASES_PAGE)
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk")) {
                                apkDownloadUrl = asset.optString("browser_download_url", apkDownloadUrl)
                                break
                            }
                        }
                    }
                    val finalDownloadUrl = apkDownloadUrl

                    // Strictly check if latest release is newer than current version
                    val isNextUpdateAvailable = isNewerVersion(latestTag, currentVersion)

                    // Check if user previously chose to skip this specific version (only applies to launch checks)
                    val skippedVersion = sp.getString(PREF_KEY_SKIP_VERSION, "") ?: ""
                    val isSkipped = isLaunchCheck && latestTag.equals(skippedVersion, ignoreCase = true)

                    if (isNextUpdateAvailable && !isSkipped) {
                        // Automatically push system update notification with custom taglines
                        sendUpdateNotification(context, latestTag, finalDownloadUrl)
                    }

                    activity.runOnUiThread {
                        onComplete?.run()
                        if (activity.isFinishing) return@runOnUiThread

                        if (isNextUpdateAvailable && !isSkipped) {
                            PetalUpdateSheetBridge.showUpdateSheet(
                                activity as androidx.activity.ComponentActivity,
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
                                activity as androidx.activity.ComponentActivity,
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
                } else if (!isLaunchCheck) {
                    activity.runOnUiThread {
                        onComplete?.run()
                        PetalUpdateSheetBridge.showUpdateSheet(
                            activity as androidx.activity.ComponentActivity,
                            PetalUpdateInfo(
                                getAppVersion(activity),
                                "You are currently running the latest build of Petal Browser.",
                                "",
                                GITHUB_RELEASES_PAGE,
                                false
                            )
                        )
                    }
                } else {
                    if (onComplete != null) activity.runOnUiThread(onComplete)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                activity.runOnUiThread {
                    onComplete?.run()
                    if (!isLaunchCheck) {
                        PetalUpdateSheetBridge.showUpdateSheet(
                            activity as androidx.activity.ComponentActivity,
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

            val intent = Intent(context, BrowserActivity::class.java).apply {
                action = "com.petal.browser.action.SHOW_UPDATE"
                putExtra("update_version", latestTag)
                putExtra("update_url", downloadUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                9901,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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

    private fun showMaterial3ExpressiveUpdateDialog(
        activity: Activity,
        currentVersion: String,
        latestVersion: String,
        releaseNotes: String?,
        downloadUrl: String,
        isLaunchCheck: Boolean
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_petal_update_expressive, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.update_title)
        val tvSubhead = dialogView.findViewById<TextView>(R.id.update_subhead)
        val tvCurrentVersion = dialogView.findViewById<TextView>(R.id.update_current_version)
        val tvLatestVersion = dialogView.findViewById<TextView>(R.id.update_latest_version)
        val tvReleaseNotes = dialogView.findViewById<TextView>(R.id.update_release_notes)
        val btnUpdateNow = dialogView.findViewById<MaterialButton>(R.id.btn_update_now)
        val btnLater = dialogView.findViewById<MaterialButton>(R.id.btn_update_later)
        val btnSkipVersion = dialogView.findViewById<MaterialButton>(R.id.btn_skip_version)

        tvTitle?.text = "New Update Available"
        tvSubhead?.text = "Squashed bugs, added magic. You know what to do"
        tvCurrentVersion?.text = currentVersion
        tvLatestVersion?.text = latestVersion
        if (tvReleaseNotes != null) {
            val formattedNotes = if (!releaseNotes.isNullOrBlank()) {
                releaseNotes.trim()
            } else {
                "• Security & performance improvements\n• Bug fixes & UI polish"
            }
            tvReleaseNotes.text = formattedNotes
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setCancelable(!isLaunchCheck)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnUpdateNow?.setOnClickListener {
            dialog.dismiss()
            downloadAndInstallApk(activity, downloadUrl, latestVersion)
        }

        btnLater?.setOnClickListener { dialog.dismiss() }

        btnSkipVersion?.setOnClickListener {
            val sp = PreferenceManager.getDefaultSharedPreferences(activity.applicationContext)
            sp.edit().putString(PREF_KEY_SKIP_VERSION, latestVersion).apply()
            Toast.makeText(activity, "Skipped version $latestVersion", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun downloadAndInstallApk(activity: Activity, apkUrl: String, version: String) {
        Toast.makeText(activity, "Downloading update $version...", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val apkFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "petal_update_$version.apk")
                if (apkFile.exists()) apkFile.delete()

                var currentUrl = apkUrl
                var conn: HttpURLConnection? = null
                var redirects = 0
                while (redirects < 5) {
                    val url = URL(currentUrl)
                    conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "PetalBrowserApp/$version")
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    val status = conn.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                        val redirectUrl = conn.getHeaderField("Location")
                        if (!redirectUrl.isNullOrEmpty()) {
                            currentUrl = redirectUrl
                            redirects++
                            continue
                        }
                    }
                    break
                }

                if (conn == null || conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP status " + (conn?.responseCode ?: -1))
                }

                BufferedInputStream(conn.inputStream, 65536).use { input ->
                    BufferedOutputStream(FileOutputStream(apkFile), 65536).use { output ->
                        val buffer = ByteArray(65536)
                        var len: Int
                        while (input.read(buffer).also { len = it } != -1) {
                            output.write(buffer, 0, len)
                        }
                        output.flush()
                    }
                }

                activity.runOnUiThread { installApk(activity, apkFile) }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update APK", e)
                activity.runOnUiThread {
                    Toast.makeText(activity, "Download failed, opening browser...", Toast.LENGTH_SHORT).show()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(intent)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }

    private fun installApk(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:" + activity.packageName)
                    }
                    activity.startActivity(intent)
                    Toast.makeText(activity, "Please grant permission to install updates", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                activity.packageName + ".fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            Toast.makeText(activity, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showUpToDateToast(activity: Activity, currentVersion: String) {
        Toast.makeText(activity, "Petal is up to date ($currentVersion)", Toast.LENGTH_SHORT).show()
    }

    private fun getAppVersion(activity: Activity): String {
        return try {
            "v" + activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        } catch (e: Exception) {
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
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) sb.append(line)
                reader.close()
                return JSONObject(sb.toString())
            }
        } catch (ignored: Exception) {
        }

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
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) sb.append(line)
                reader.close()

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
        } catch (ignored: Exception) {
        }

        return null
    }

    /**
     * SemVer comparator ensuring we ONLY flag an update when latest is strictly greater than current.
     */
    @JvmStatic
    fun isNewerVersion(latest: String?, current: String?): Boolean {
        if (latest == null || current == null) return false
        val cleanLatest = latest.trim().replace(Regex("(?i)^[vV_\\s-]+"), "").replace(Regex("(?i)[^0-9.].*$"), "")
        val cleanCurrent = current.trim().replace(Regex("(?i)^[vV_\\s-]+"), "").replace(Regex("(?i)[^0-9.].*$"), "")

        if (cleanLatest.isEmpty() || cleanCurrent.isEmpty()) return false

        val latestParts = cleanLatest.split(".")
        val currentParts = cleanCurrent.split(".")

        val length = Math.max(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            var latestNum = 0
            var currentNum = 0
            if (i < latestParts.size) {
                try {
                    latestNum = latestParts[i].replace(Regex("[^0-9]"), "").toInt()
                } catch (ignored: Exception) {
                }
            }
            if (i < currentParts.size) {
                try {
                    currentNum = currentParts[i].replace(Regex("[^0-9]"), "").toInt()
                } catch (ignored: Exception) {
                }
            }
            if (latestNum > currentNum) return true
            if (latestNum < currentNum) return false
        }
        return false
    }
}
