package com.petal.browser.unit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.PetalLiveAlertManager
import com.petal.browser.download.PetalMozillaDownloadService
import com.petal.browser.download.PetalSpeedMeter
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.support.base.android.NotificationsDelegate
import java.io.File

/**
 * Decoupled, persistent singleton manager for Petal Browser updates.
 * Directs update downloads exclusively through Mozilla's official Firefox/Fenix
 * download pipeline (FetchDownloadManager / AbstractFetchDownloadService via
 * Gecko's network stack).
 *
 * Preserves active download progress across screen navigation, backgrounding,
 * and updater screen reopening.
 */
object PetalUpdateManager {

    private const val TAG = "PetalUpdateManager"

    private const val PREF_KEY_UPDATE_DOWNLOAD_UUID = "sp_active_update_download_uuid"
    private const val PREF_KEY_UPDATE_DOWNLOAD_ID = "sp_active_update_download_id"
    private const val PREF_KEY_UPDATE_FILE_PATH = "sp_active_update_file_path"
    private const val PREF_KEY_UPDATE_VERSION = "sp_active_update_version"
    private const val PREF_KEY_UPDATE_URL = "sp_active_update_download_url"

    sealed class UpdateDownloadState {
        object Idle : UpdateDownloadState()
        data class Downloading(
            val progress: Int,
            val version: String,
            val downloadId: Long,
            val downloadedBytes: Long = 0L,
            val totalBytes: Long = 0L,
            val bytesPerSecond: Long = 0L
        ) : UpdateDownloadState()
        data class Paused(
            val progress: Int,
            val version: String,
            val downloadId: Long,
            val downloadedBytes: Long = 0L,
            val totalBytes: Long = 0L
        ) : UpdateDownloadState()
        data class Completed(val version: String, val apkFile: File) : UpdateDownloadState()
        data class Failed(val error: String, val version: String) : UpdateDownloadState()
    }

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val speedMeter = PetalSpeedMeter()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null
    @Volatile private var isInitialized = false
    @Volatile private var isObserving = false

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val downloadUuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "") ?: ""
        val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
        val filePath = sp.getString(PREF_KEY_UPDATE_FILE_PATH, "") ?: ""

        // Check if previously completed APK file still exists on disk
        if (filePath.isNotBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) {
                _downloadState.value = UpdateDownloadState.Completed(version, file)
            }
        }

        startObserver(appContext)
        isInitialized = true
    }

    private fun startObserver(appContext: Context) {
        if (isObserving) return
        isObserving = true
        try {
            val store = PetalEngineStore.getStore(appContext)
            store.observeManually { state ->
                val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
                val targetUuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "") ?: return@observeManually
                val targetUrl = sp.getString(PREF_KEY_UPDATE_URL, "") ?: ""
                val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""

                val download = state.downloads[targetUuid]
                    ?: state.downloads.values.firstOrNull { it.url == targetUrl && targetUrl.isNotBlank() }

                if (download != null) {
                    val idLong = download.id.hashCode().toLong()
                    val total = download.contentLength ?: 0L
                    val copied = download.currentBytesCopied
                    val rawProgress = download.progress?.toFloat()
                    val progress = when {
                        rawProgress != null && rawProgress > 1f -> rawProgress.toInt()
                        rawProgress != null -> (rawProgress * 100f).toInt()
                        total > 0L -> ((copied.toFloat() * 100f) / total.toFloat()).toInt()
                        else -> 0
                    }.coerceIn(0, 100)

                    when (download.status) {
                        DownloadState.Status.DOWNLOADING, DownloadState.Status.INITIATED -> {
                            val speed = speedMeter.update(System.currentTimeMillis(), copied)
                            _downloadState.value = UpdateDownloadState.Downloading(
                                progress = progress,
                                version = version,
                                downloadId = idLong,
                                downloadedBytes = copied,
                                totalBytes = total,
                                bytesPerSecond = speed
                            )
                        }
                        DownloadState.Status.PAUSED -> {
                            _downloadState.value = UpdateDownloadState.Paused(
                                progress = progress,
                                version = version,
                                downloadId = idLong,
                                downloadedBytes = copied,
                                totalBytes = total
                            )
                        }
                        DownloadState.Status.COMPLETED -> {
                            val destinationDir = File(download.directoryPath)
                            val apkFile = File(destinationDir, download.fileName ?: "Petal_update.apk")
                            _downloadState.value = UpdateDownloadState.Completed(version, apkFile)
                            sp.edit().remove(PREF_KEY_UPDATE_DOWNLOAD_UUID).apply()
                            PetalUpdateInstallerReceiver.installDownloadedApk(appContext, apkFile)
                        }
                        DownloadState.Status.FAILED, DownloadState.Status.CANCELLED -> {
                            _downloadState.value = UpdateDownloadState.Failed("Download stopped or failed", version)
                            clearState(sp)
                        }
                    }
                }
            }.resume()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to observe Mozilla download store: ${t.message}", t)
        }
    }

    private fun clearState(sp: SharedPreferences) {
        sp.edit()
            .remove(PREF_KEY_UPDATE_DOWNLOAD_UUID)
            .remove(PREF_KEY_UPDATE_DOWNLOAD_ID)
            .remove(PREF_KEY_UPDATE_FILE_PATH)
            .remove(PREF_KEY_UPDATE_VERSION)
            .remove(PREF_KEY_UPDATE_URL)
            .apply()
    }

    fun startUpdateDownload(context: Context, downloadUrl: String, version: String): Long {
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)

        val cleanVersion = version.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "Petal_v${cleanVersion}.apk"
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        val destinationFile = File(downloadsDir, fileName)

        val store = PetalEngineStore.getStore(appContext)
        val manager = FetchDownloadManager(
            applicationContext = appContext,
            store = store,
            service = PetalMozillaDownloadService::class,
            notificationsDelegate = NotificationsDelegate(NotificationManagerCompat.from(appContext))
        )

        val downloadState = DownloadState(
            url = downloadUrl,
            fileName = fileName,
            contentType = "application/vnd.android.package-archive",
            directoryPath = downloadsDir.absolutePath,
            referrerUrl = "https://github.com/shreyagarwal72/petal",
            private = false
        )

        val downloadUuid = manager.download(downloadState) ?: ""
        val downloadIdLong = downloadUuid.hashCode().toLong()

        sp.edit()
            .putString(PREF_KEY_UPDATE_DOWNLOAD_UUID, downloadUuid)
            .putLong(PREF_KEY_UPDATE_DOWNLOAD_ID, downloadIdLong)
            .putString(PREF_KEY_UPDATE_FILE_PATH, destinationFile.absolutePath)
            .putString(PREF_KEY_UPDATE_VERSION, version)
            .putString(PREF_KEY_UPDATE_URL, downloadUrl)
            .apply()

        speedMeter.reset()
        _downloadState.value = UpdateDownloadState.Downloading(0, version, downloadIdLong)
        startObserver(appContext)

        PetalLiveAlertManager.trackDownload(
            appContext,
            downloadIdLong,
            destinationFile.name
        )

        return downloadIdLong
    }

    fun pauseUpdateDownload(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val downloadUuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "") ?: ""
        if (downloadUuid.isNotBlank()) {
            sendDownloadServiceAction(context, downloadUuid, AbstractFetchDownloadService.ACTION_PAUSE)
        }
    }

    fun resumeUpdateDownload(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val downloadUuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "") ?: ""
        if (downloadUuid.isNotBlank()) {
            sendDownloadServiceAction(context, downloadUuid, AbstractFetchDownloadService.ACTION_RESUME)
        }
    }

    fun cancelUpdateDownload(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val downloadUuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "") ?: ""
        if (downloadUuid.isNotBlank()) {
            sendDownloadServiceAction(context, downloadUuid, AbstractFetchDownloadService.ACTION_CANCEL)
            try {
                PetalEngineStore.getStore(context.applicationContext).dispatch(
                    DownloadAction.RemoveDownloadAction(downloadUuid)
                )
            } catch (_: Throwable) {}
        }
        val filePath = sp.getString(PREF_KEY_UPDATE_FILE_PATH, "") ?: ""
        if (filePath.isNotBlank()) {
            try { File(filePath).delete() } catch (_: Exception) {}
        }
        clearState(sp)
        _downloadState.value = UpdateDownloadState.Idle
    }

    private fun sendDownloadServiceAction(context: Context, downloadUuid: String, action: String) {
        try {
            val intent = Intent(action).apply {
                setPackage(context.applicationContext.packageName)
                putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadUuid)
            }
            context.applicationContext.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send $action for $downloadUuid: ${t.message}")
        }
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
