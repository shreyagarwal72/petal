package com.petal.browser.unit

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.compose.downloads.PetalLiveAlertManager
import com.petal.browser.download.PetalDownloadEngine
import com.petal.browser.view.PetalToast
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.EnqueueAction
import com.tonyodev.fetch2.Error
import com.tonyodev.fetch2.NetworkType
import com.tonyodev.fetch2.Priority
import com.tonyodev.fetch2.Request
import com.tonyodev.fetch2.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Decoupled, persistent singleton manager for Petal Browser updates.
 * Preserves active download progress across screen navigation, backgrounding,
 * and updater screen reopening.
 */
object PetalUpdateManager {

    private const val TAG = "PetalUpdateManager"

    private const val PREF_KEY_UPDATE_DOWNLOAD_ID = "sp_active_update_download_id"
    private const val PREF_KEY_UPDATE_FILE_PATH = "sp_active_update_file_path"
    private const val PREF_KEY_UPDATE_VERSION = "sp_active_update_version"
    private const val PREF_KEY_UPDATE_URL = "sp_active_update_download_url"

    sealed class UpdateDownloadState {
        object Idle : UpdateDownloadState()
        data class Downloading(val progress: Int, val version: String, val downloadId: Long) : UpdateDownloadState()
        data class Paused(val progress: Int, val version: String, val downloadId: Long) : UpdateDownloadState()
        data class Completed(val version: String, val apkFile: File) : UpdateDownloadState()
        data class Failed(val error: String, val version: String) : UpdateDownloadState()
    }

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var activeListener: AbstractFetchListener? = null
    @Volatile private var isInitialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val downloadId = sp.getLong(PREF_KEY_UPDATE_DOWNLOAD_ID, -1L)
        val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
        val filePath = sp.getString(PREF_KEY_UPDATE_FILE_PATH, "") ?: ""

        val fetch = PetalDownloadEngine.getInstance(appContext).fetch

        if (downloadId > 0L) {
            fetch.getDownload(downloadId.toInt()) { download ->
                if (download != null) {
                    when (download.status) {
                        Status.DOWNLOADING, Status.QUEUED -> {
                            val progress = download.progress.coerceIn(0, 100)
                            _downloadState.value = UpdateDownloadState.Downloading(progress, version, downloadId)
                            attachFetchListener(appContext, download.id)
                        }
                        Status.PAUSED -> {
                            val progress = download.progress.coerceIn(0, 100)
                            _downloadState.value = UpdateDownloadState.Paused(progress, version, downloadId)
                            attachFetchListener(appContext, download.id)
                        }
                        Status.COMPLETED -> {
                            val file = File(download.file)
                            if (file.exists() && file.length() > 0L) {
                                _downloadState.value = UpdateDownloadState.Completed(version, file)
                            } else {
                                clearState(sp)
                                _downloadState.value = UpdateDownloadState.Idle
                            }
                        }
                        Status.FAILED, Status.CANCELLED, Status.DELETED -> {
                            clearState(sp)
                            _downloadState.value = UpdateDownloadState.Idle
                        }
                        else -> {
                            attachFetchListener(appContext, download.id)
                        }
                    }
                } else if (filePath.isNotBlank()) {
                    val file = File(filePath)
                    if (file.exists() && file.length() > 0L) {
                        _downloadState.value = UpdateDownloadState.Completed(version, file)
                    } else {
                        clearState(sp)
                        _downloadState.value = UpdateDownloadState.Idle
                    }
                } else {
                    clearState(sp)
                    _downloadState.value = UpdateDownloadState.Idle
                }
            }
        } else if (filePath.isNotBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) {
                _downloadState.value = UpdateDownloadState.Completed(version, file)
            }
        }
        isInitialized = true
    }

    private fun clearState(sp: SharedPreferences) {
        sp.edit()
            .remove(PREF_KEY_UPDATE_DOWNLOAD_ID)
            .remove(PREF_KEY_UPDATE_FILE_PATH)
            .remove(PREF_KEY_UPDATE_VERSION)
            .remove(PREF_KEY_UPDATE_URL)
            .apply()
    }

    private fun attachFetchListener(context: Context, targetId: Int) {
        val appContext = context.applicationContext
        val fetch = PetalDownloadEngine.getInstance(appContext).fetch
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        activeListener?.let { fetch.removeListener(it) }

        val listener = object : AbstractFetchListener() {
            override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                if (download.id == targetId) {
                    val progress = download.progress.coerceIn(0, 100)
                    val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
                    _downloadState.value = UpdateDownloadState.Downloading(progress, version, download.id.toLong())
                }
            }

            override fun onPaused(download: Download) {
                if (download.id == targetId) {
                    val progress = download.progress.coerceIn(0, 100)
                    val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
                    _downloadState.value = UpdateDownloadState.Paused(progress, version, download.id.toLong())
                }
            }

            override fun onResumed(download: Download) {
                if (download.id == targetId) {
                    val progress = download.progress.coerceIn(0, 100)
                    val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
                    _downloadState.value = UpdateDownloadState.Downloading(progress, version, download.id.toLong())
                }
            }

            override fun onCompleted(download: Download) {
                if (download.id == targetId) {
                    val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
                    val file = File(download.file)
                    _downloadState.value = UpdateDownloadState.Completed(version, file)
                    fetch.removeListener(this)
                    activeListener = null
                    sp.edit().remove(PREF_KEY_UPDATE_DOWNLOAD_ID).apply()
                    PetalUpdateInstallerReceiver.installDownloadedApk(appContext, file)
                }
            }

            override fun onError(download: Download, error: Error, throwable: Throwable?) {
                if (download.id == targetId) {
                    val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
                    _downloadState.value = UpdateDownloadState.Failed(error.toString(), version)
                    fetch.removeListener(this)
                    activeListener = null
                    sp.edit().remove(PREF_KEY_UPDATE_DOWNLOAD_ID).apply()
                    Log.e(TAG, "Update download failed: $error", throwable)
                }
            }
        }

        activeListener = listener
        fetch.addListener(listener)
    }

    fun startUpdateDownload(context: Context, downloadUrl: String, version: String): Long {
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        val cleanVersion = version.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "Petal_v${cleanVersion}.apk"
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        val destinationFile = File(downloadsDir, fileName)

        val fetch = PetalDownloadEngine.getInstance(appContext).fetch

        val request = Request(downloadUrl, destinationFile.absolutePath).apply {
            priority = Priority.HIGH
            networkType = NetworkType.ALL
            enqueueAction = EnqueueAction.REPLACE_EXISTING
            autoRetryMaxAttempts = 10
            addHeader("User-Agent", "PetalBrowserApp")
        }

        sp.edit()
            .putLong(PREF_KEY_UPDATE_DOWNLOAD_ID, request.id.toLong())
            .putString(PREF_KEY_UPDATE_FILE_PATH, destinationFile.absolutePath)
            .putString(PREF_KEY_UPDATE_VERSION, version)
            .putString(PREF_KEY_UPDATE_URL, downloadUrl)
            .apply()

        _downloadState.value = UpdateDownloadState.Downloading(0, version, request.id.toLong())
        attachFetchListener(appContext, request.id)

        fetch.enqueue(request, { req ->
            PetalLiveAlertManager.trackDownload(
                appContext,
                req.id.toLong(),
                destinationFile.name
            )
        }, { err ->
            _downloadState.value = UpdateDownloadState.Failed(err.toString(), version)
            clearState(sp)
            Log.e(TAG, "Failed to enqueue update download: $err")
            PetalToast.show(appContext, "Failed to start update download: $err")
        })

        return request.id.toLong()
    }

    fun pauseUpdateDownload(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val downloadId = sp.getLong(PREF_KEY_UPDATE_DOWNLOAD_ID, -1L)
        if (downloadId > 0L) {
            val fetch = PetalDownloadEngine.getInstance(context.applicationContext).fetch
            fetch.pause(downloadId.toInt())
        }
    }

    fun resumeUpdateDownload(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val downloadId = sp.getLong(PREF_KEY_UPDATE_DOWNLOAD_ID, -1L)
        if (downloadId > 0L) {
            val fetch = PetalDownloadEngine.getInstance(context.applicationContext).fetch
            fetch.resume(downloadId.toInt())
        }
    }

    fun cancelUpdateDownload(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val downloadId = sp.getLong(PREF_KEY_UPDATE_DOWNLOAD_ID, -1L)
        if (downloadId > 0L) {
            val fetch = PetalDownloadEngine.getInstance(context.applicationContext).fetch
            fetch.cancel(downloadId.toInt())
        }
        val filePath = sp.getString(PREF_KEY_UPDATE_FILE_PATH, "") ?: ""
        if (filePath.isNotBlank()) {
            try { File(filePath).delete() } catch (_: Exception) {}
        }
        clearState(sp)
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun installActiveUpdate(context: Context): Boolean {
        val currentState = _downloadState.value
        if (currentState is UpdateDownloadState.Completed && currentState.apkFile.exists()) {
            PetalUpdateInstallerReceiver.installDownloadedApk(context, currentState.apkFile)
            return true
        }
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val filePath = sp.getString(PREF_KEY_UPDATE_FILE_PATH, "") ?: ""
        if (filePath.isNotBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) {
                PetalUpdateInstallerReceiver.installDownloadedApk(context, file)
                return true
            }
        }
        return false
    }
}
