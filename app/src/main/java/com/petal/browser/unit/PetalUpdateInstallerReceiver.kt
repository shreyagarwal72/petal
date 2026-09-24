package com.petal.browser.unit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.view.PetalToast
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.EnqueueAction
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Petal in-app update installer and downloader.
 * Uses Petal's high-speed internal download engine (Fetch2 + OkHttp) exclusively.
 * Fully eliminates legacy android.app.DownloadManager dependencies.
 */
class PetalUpdateInstallerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Maintained as a standard receiver stub for any broadcast callbacks if needed.
    }

    companion object {
        private const val TAG = "PetalUpdateInstaller"
        const val KEY_UPDATE_DOWNLOAD_ID = "sp_active_update_download_id"
        const val KEY_UPDATE_FILE_PATH = "sp_active_update_file_path"
        const val KEY_UPDATE_VERSION = "sp_active_update_version"

        /**
         * Downloads update APK using Petal Download Manager engine with live progress callbacks.
         */
        @JvmStatic
        suspend fun downloadAndInstallApk(
            context: Context,
            apkUrl: String,
            version: String,
            onProgressUpdate: (Int) -> Unit
        ): Boolean = withContext(Dispatchers.Main) {
            val appContext = context.applicationContext
            val cleanVersion = version.replace(Regex("[^a-zA-Z0-9]"), "_")
            val fileName = "Petal_v${cleanVersion}.apk"
            val downloadsDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: appContext.filesDir
            val destinationFile = File(downloadsDir, fileName)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
            val downloadEngine = com.petal.browser.download.PetalDownloadEngine.getInstance(appContext)
            val fetch = downloadEngine.fetch

            val request = Request(apkUrl, destinationFile.absolutePath).apply {
                priority = Priority.HIGH
                networkType = NetworkType.ALL
                enqueueAction = EnqueueAction.REPLACE_EXISTING
                autoRetryMaxAttempts = 10
                addHeader("User-Agent", "PetalBrowserApp")
            }

            var completed = false
            val listener = object : AbstractFetchListener() {
                override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                    if (download.id == request.id) {
                        val progress = download.progress.coerceIn(0, 100)
                        onProgressUpdate(progress)
                    }
                }

                override fun onCompleted(download: Download) {
                    if (download.id == request.id) {
                        completed = true
                        fetch.removeListener(this)
                        sp.edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply()
                        onProgressUpdate(100)
                        installDownloadedApk(appContext, destinationFile)
                    }
                }

                override fun onError(download: Download, error: Error, throwable: Throwable?) {
                    if (download.id == request.id) {
                        fetch.removeListener(this)
                        sp.edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply()
                        Log.e(TAG, "Update download failed via Petal Download Manager: $error", throwable)
                        PetalToast.show(appContext, "Update download failed: $error")
                    }
                }
            }

            fetch.addListener(listener)
            fetch.enqueue(request, { req ->
                sp.edit()
                    .putLong(KEY_UPDATE_DOWNLOAD_ID, req.id.toLong())
                    .putString(KEY_UPDATE_FILE_PATH, destinationFile.absolutePath)
                    .putString(KEY_UPDATE_VERSION, version)
                    .apply()
                com.petal.browser.compose.downloads.PetalLiveAlertManager.trackDownload(
                    appContext,
                    req.id.toLong(),
                    destinationFile.name
                )
            }, { err ->
                fetch.removeListener(listener)
                Log.e(TAG, "Failed to enqueue update download: $err")
                PetalToast.show(appContext, "Failed to start update download: $err")
            })

            true
        }

        /**
         * Enqueues update download directly in Petal Download Manager (Fetch2 engine).
         * Runs in background with notification and automatically prompts to install on completion.
         */
        @JvmStatic
        fun enqueuePetalUpdateDownload(context: Context, downloadUrl: String, version: String): Long {
            return try {
                val appContext = context.applicationContext
                val cleanVersion = version.replace(Regex("[^a-zA-Z0-9]"), "_")
                val fileName = "Petal_v${cleanVersion}.apk"
                val downloadsDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: appContext.filesDir
                val destinationFile = File(downloadsDir, fileName)
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }

                val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
                val downloadEngine = com.petal.browser.download.PetalDownloadEngine.getInstance(appContext)
                val fetch = downloadEngine.fetch

                val request = Request(downloadUrl, destinationFile.absolutePath).apply {
                    priority = Priority.HIGH
                    networkType = NetworkType.ALL
                    enqueueAction = EnqueueAction.REPLACE_EXISTING
                    autoRetryMaxAttempts = 10
                    addHeader("User-Agent", "PetalBrowserApp")
                }

                fetch.addListener(object : AbstractFetchListener() {
                    override fun onCompleted(download: Download) {
                        if (download.id == request.id) {
                            fetch.removeListener(this)
                            sp.edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply()
                            installDownloadedApk(appContext, destinationFile)
                        }
                    }

                    override fun onError(download: Download, error: Error, throwable: Throwable?) {
                        if (download.id == request.id) {
                            fetch.removeListener(this)
                            sp.edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply()
                            Log.e(TAG, "Background update download failed: $error", throwable)
                            PetalToast.show(appContext, "Update download failed: $error")
                        }
                    }
                })

                fetch.enqueue(request, { req ->
                    sp.edit()
                        .putLong(KEY_UPDATE_DOWNLOAD_ID, req.id.toLong())
                        .putString(KEY_UPDATE_FILE_PATH, destinationFile.absolutePath)
                        .putString(KEY_UPDATE_VERSION, version)
                        .apply()
                    com.petal.browser.compose.downloads.PetalLiveAlertManager.trackDownload(
                        appContext,
                        req.id.toLong(),
                        destinationFile.name
                    )
                    PetalToast.show(appContext, "Petal update downloading in background...")
                }, { err ->
                    Log.e(TAG, "Failed to enqueue update download: $err")
                })

                request.id.toLong()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue update download in Petal Download Manager", e)
                -1L
            }
        }

        /**
         * Backward compatibility stub delegating to Petal Download Manager.
         */
        @JvmStatic
        fun enqueueSystemUpdateDownload(context: Context, downloadUrl: String, version: String): Long {
            return enqueuePetalUpdateDownload(context, downloadUrl, version)
        }

        @JvmStatic
        fun installDownloadedApk(context: Context, apkFile: File) {
            try {
                if (!apkFile.exists() || apkFile.length() == 0L) {
                    Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
                    PetalToast.show(context, "Update installer file not found")
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                        PetalToast.show(context, "Please grant permission to install updates")
                        return
                    }
                }

                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(installIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error installing APK", e)
                PetalToast.show(context, "Failed to launch installer: ${e.message}")
            }
        }
    }
}
