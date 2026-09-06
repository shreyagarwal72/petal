/*
 * MIT License
 * Copyright (c) 2026 Petal Browser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT/TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.petal.browser.unit

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.petal.browser.view.NinjaToast
import java.io.File

/**
 * BroadcastReceiver triggered when the system DownloadManager completes downloading
 * an in-app update APK. Survives app backgrounding and process termination.
 */
class PetalUpdateInstallerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId <= 0L) return

            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val activeUpdateDownloadId = sp.getLong(KEY_UPDATE_DOWNLOAD_ID, -1L)
            val updateFilePath = sp.getString(KEY_UPDATE_FILE_PATH, null)

            if (downloadId == activeUpdateDownloadId && !updateFilePath.isNullOrBlank()) {
                val apkFile = File(updateFilePath)
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (dm != null) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    dm.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                sp.edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply()
                                installDownloadedApk(context, apkFile)
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                sp.edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply()
                                NinjaToast.show(context, "Update download failed")
                            }
                        }
                    }
                } else if (apkFile.exists() && apkFile.length() > 0) {
                    sp.edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply()
                    installDownloadedApk(context, apkFile)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PetalUpdateInstaller"
        const val KEY_UPDATE_DOWNLOAD_ID = "sp_active_update_download_id"
        const val KEY_UPDATE_FILE_PATH = "sp_active_update_file_path"
        const val KEY_UPDATE_VERSION = "sp_active_update_version"

        @JvmStatic
        fun enqueueSystemUpdateDownload(context: Context, downloadUrl: String, version: String): Long {
            return try {
                val cleanVersion = version.replace(Regex("[^a-zA-Z0-9]"), "_")
                val fileName = "Petal_v${cleanVersion}.apk"
                val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
                val destinationFile = File(downloadsDir, fileName)
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    ?: throw IllegalStateException("DownloadManager not available")

                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    setTitle("Petal Browser $version Update")
                    setDescription("Downloading update package...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationUri(Uri.fromFile(destinationFile))
                    setMimeType("application/vnd.android.package-archive")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }

                val downloadId = dm.enqueue(request)

                val sp = PreferenceManager.getDefaultSharedPreferences(context)
                sp.edit()
                    .putLong(KEY_UPDATE_DOWNLOAD_ID, downloadId)
                    .putString(KEY_UPDATE_FILE_PATH, destinationFile.absolutePath)
                    .putString(KEY_UPDATE_VERSION, version)
                    .apply()

                NinjaToast.show(context, "Update download started in background...")
                downloadId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue update download with DownloadManager", e)
                -1L
            }
        }

        @JvmStatic
        fun installDownloadedApk(context: Context, apkFile: File) {
            try {
                if (!apkFile.exists() || apkFile.length() == 0L) {
                    Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
                    NinjaToast.show(context, "Update installer file not found")
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(settingsIntent)
                        NinjaToast.show(context, "Please grant permission to install updates")
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
                NinjaToast.show(context, "Failed to launch installer: ${e.message}")
            }
        }
    }
}
