package com.petal.browser.unit

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.petal.browser.compose.downloads.DownloadItem
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Decoupled, persistent singleton manager for Petal Browser updates (GitHub flavor).
 *
 * The APK is downloaded through the shared [PetalFetchDownloadBridge] (Mozilla's
 * AbstractFetchDownloadService over Gecko's network stack, the same engine as every other
 * download), tagged [com.petal.browser.download.DownloadSource.UPDATE]. There is exactly one id
 * scheme ([PetalFetchDownloadBridge.stableId]) and one observer (the bridge's item flow); this
 * class only translates the update row into [UpdateDownloadState] for the updater UI and hands the
 * finished file to [PetalUpdateInstallerReceiver].
 *
 * Preserves active download progress across screen navigation, backgrounding, and updater screen
 * reopening.
 */
object PetalUpdateManager {

    private const val TAG = "PetalUpdateManager"

    private const val PREF_KEY_UPDATE_DOWNLOAD_UUID = "sp_active_update_download_uuid"
    private const val PREF_KEY_UPDATE_DOWNLOAD_ID = "sp_active_update_download_id"
    private const val PREF_KEY_UPDATE_FILE_PATH = "sp_active_update_file_path"
    private const val PREF_KEY_UPDATE_VERSION = "sp_active_update_version"
    private const val PREF_KEY_UPDATE_URL = "sp_active_update_download_url"
    /** Every download id ever started by the updater, so rows keep their UPDATE tag after a restart. */
    private const val PREF_KEY_UPDATE_IDS = "sp_update_download_ids"

    private const val UPDATE_REFERRER = "https://github.com/shreyagarwal72/petal"

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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    @Volatile private var isInitialized = false
    @Volatile private var isObserving = false

    /** True when [downloadId] (Mozilla's UUID) was started by the updater. Used by the bridge for tagging. */
    @JvmStatic
    fun isUpdateDownload(context: Context, downloadId: String): Boolean {
        if (downloadId.isBlank()) return false
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getStringSet(PREF_KEY_UPDATE_IDS, emptySet())?.contains(downloadId) == true
    }

    private fun rememberUpdateId(sp: SharedPreferences, downloadId: String) {
        val ids = LinkedHashSet(sp.getStringSet(PREF_KEY_UPDATE_IDS, emptySet()) ?: emptySet())
        ids.add(downloadId)
        while (ids.size > 10) ids.remove(ids.first())
        sp.edit().putStringSet(PREF_KEY_UPDATE_IDS, ids).apply()
    }

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
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
            PetalFetchDownloadBridge.ensureInitialized(appContext)
            scope.launch {
                PetalFetchDownloadBridge.downloadItems.collect { items -> applyItems(appContext, items) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to observe the download bridge: ${t.message}", t)
        }
    }

    private fun applyItems(appContext: Context, items: List<DownloadItem>) {
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val targetUuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "").orEmpty()
        if (targetUuid.isBlank()) return
        val version = sp.getString(PREF_KEY_UPDATE_VERSION, "") ?: ""
        val item = items.firstOrNull { it.downloadId == targetUuid } ?: return

        val progress = when {
            item.progress != null -> (item.progress * 100f).toInt()
            item.totalSize > 0L -> ((item.bytesDownloaded * 100L) / item.totalSize).toInt()
            else -> 0
        }.coerceIn(0, 100)

        when (item.status) {
            DownloadStatus.STATUS_RUNNING, DownloadStatus.STATUS_PENDING -> {
                _downloadState.value = UpdateDownloadState.Downloading(
                    progress = progress,
                    version = version,
                    downloadId = item.id,
                    downloadedBytes = item.bytesDownloaded,
                    totalBytes = item.totalSize,
                    bytesPerSecond = item.speedBytesPerSec
                )
            }
            DownloadStatus.STATUS_PAUSED -> {
                _downloadState.value = UpdateDownloadState.Paused(
                    progress = progress,
                    version = version,
                    downloadId = item.id,
                    downloadedBytes = item.bytesDownloaded,
                    totalBytes = item.totalSize
                )
            }
            DownloadStatus.STATUS_SUCCESSFUL -> {
                // Mozilla reports the final path (it may differ from the requested name if a file
                // with that name already existed), so use it instead of rebuilding the path.
                val path = item.localUri?.removePrefix("file://").orEmpty()
                val apkFile = File(path)
                // One-shot: consume the uuid so the install prompt is not shown again on every emission.
                sp.edit()
                    .remove(PREF_KEY_UPDATE_DOWNLOAD_UUID)
                    .putString(PREF_KEY_UPDATE_FILE_PATH, apkFile.absolutePath)
                    .apply()
                if (path.isNotBlank() && apkFile.exists() && apkFile.length() > 0L) {
                    _downloadState.value = UpdateDownloadState.Completed(version, apkFile)
                    PetalUpdateInstallerReceiver.installDownloadedApk(appContext, apkFile)
                } else {
                    _downloadState.value = UpdateDownloadState.Failed("The downloaded update file could not be found", version)
                    clearState(sp)
                }
            }
            DownloadStatus.STATUS_FAILED -> {
                _downloadState.value = UpdateDownloadState.Failed(item.failureReason ?: "Download failed", version)
                clearState(sp)
            }
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

    /**
     * Starts the APK download. Returns the download id (same id scheme as the Downloads screen) or 0
     * if it could not be started, in which case [downloadState] is [UpdateDownloadState.Failed]
     * with the reason.
     */
    fun startUpdateDownload(context: Context, downloadUrl: String, version: String): Long {
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        startObserver(appContext)

        val cleanVersion = version.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "Petal_v${cleanVersion}.apk"

        // The Mozilla file writer saves into the public Downloads collection. Before Android 10
        // that needs the legacy storage permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !PetalBackupManager.checkPermissionStorage(appContext)) {
            _downloadState.value = UpdateDownloadState.Failed("Storage permission is required to download the update", version)
            return 0L
        }

        // A previous failed/cancelled attempt leaves a FAILED row behind; drop it so retrying the
        // update does not pile up duplicates in the Downloads screen.
        PetalFetchDownloadBridge.downloadItems.value
            .filter { it.source == com.petal.browser.download.DownloadSource.UPDATE && it.status == DownloadStatus.STATUS_FAILED }
            .forEach { PetalFetchDownloadBridge.deleteDownload(appContext, it, deleteFile = true) }

        var startedId = 0L
        var failure: String? = null
        PetalFetchDownloadBridge.enqueueUpdateDownload(
            context = appContext,
            url = downloadUrl,
            fileName = fileName,
            referrerUrl = UPDATE_REFERRER,
            onEnqueued = { uuid ->
                startedId = PetalFetchDownloadBridge.stableId(uuid)
                rememberUpdateId(sp, uuid)
                sp.edit()
                    .putString(PREF_KEY_UPDATE_DOWNLOAD_UUID, uuid)
                    .putLong(PREF_KEY_UPDATE_DOWNLOAD_ID, startedId)
                    .remove(PREF_KEY_UPDATE_FILE_PATH)
                    .putString(PREF_KEY_UPDATE_VERSION, version)
                    .putString(PREF_KEY_UPDATE_URL, downloadUrl)
                    .apply()
            },
            onFailed = { reason -> failure = reason }
        )

        if (startedId == 0L) {
            _downloadState.value = UpdateDownloadState.Failed(failure ?: "The update download could not be started", version)
            return 0L
        }

        _downloadState.value = UpdateDownloadState.Downloading(0, version, startedId)
        // The store may already have emitted progress before the uuid was saved; sync once now.
        applyItems(appContext, PetalFetchDownloadBridge.downloadItems.value)
        return startedId
    }

    private fun activeId(context: Context): Long {
        val sp = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val uuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "").orEmpty()
        return PetalFetchDownloadBridge.stableId(uuid)
    }

    fun pauseUpdateDownload(context: Context) {
        val id = activeId(context)
        if (id != 0L) PetalFetchDownloadBridge.pause(context.applicationContext, id)
    }

    fun resumeUpdateDownload(context: Context) {
        val id = activeId(context)
        if (id != 0L) PetalFetchDownloadBridge.resume(context.applicationContext, id)
    }

    fun cancelUpdateDownload(context: Context) {
        val appContext = context.applicationContext
        val sp = PreferenceManager.getDefaultSharedPreferences(appContext)
        val uuid = sp.getString(PREF_KEY_UPDATE_DOWNLOAD_UUID, "").orEmpty()
        val item = PetalFetchDownloadBridge.downloadItems.value.firstOrNull { it.downloadId == uuid && uuid.isNotBlank() }
        if (item != null) {
            // Cancels the service job, dismisses Mozilla's notification, removes the store entry and the partial file.
            PetalFetchDownloadBridge.deleteDownload(appContext, item, deleteFile = true)
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
