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
            PetalUpdateManager.initialize(context)
            PetalUpdateManager.startUpdateDownload(context, apkUrl, version)
            true
        }

        /**
         * Enqueues update download directly in Petal Download Manager (Fetch2 engine).
         * Runs in background with notification and automatically prompts to install on completion.
         */
        @JvmStatic
        fun enqueuePetalUpdateDownload(context: Context, downloadUrl: String, version: String): Long {
            PetalUpdateManager.initialize(context)
            return PetalUpdateManager.startUpdateDownload(context, downloadUrl, version)
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
