package com.petal.browser.compose.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

data class PetalDownloadTask(
    val id: Long,
    val fileName: String,
    val url: String,
    val destinationPath: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val progressFraction: Float = 0f,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val speedBps: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
}

object PetalDownloadEngine {
    private const val CHANNEL_ID = "petal_downloads_channel"
    private const val NOTIFICATION_ID = 2004

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _downloadTasks = MutableStateFlow<Map<Long, PetalDownloadTask>>(emptyMap())
    val downloadTasks: StateFlow<Map<Long, PetalDownloadTask>> = _downloadTasks.asStateFlow()

    private val activeJobs = mutableMapOf<Long, Job>()

    fun startDownload(context: Context, url: String, fileName: String): Long {
        val id = System.currentTimeMillis()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        val task = PetalDownloadTask(
            id = id,
            fileName = fileName,
            url = url,
            destinationPath = file.absolutePath,
            status = DownloadStatus.PENDING
        )

        _downloadTasks.value = _downloadTasks.value + (id to task)

        val job = engineScope.launch {
            executeDownload(context, id)
        }
        activeJobs[id] = job
        return id
    }

    fun pauseDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        updateTask(id) { it.copy(status = DownloadStatus.PAUSED) }
    }

    fun resumeDownload(context: Context, id: Long) {
        val task = _downloadTasks.value[id] ?: return
        if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
            updateTask(id) { it.copy(status = DownloadStatus.PENDING) }
            val job = engineScope.launch {
                executeDownload(context, id)
            }
            activeJobs[id] = job
        }
    }

    fun cancelDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val task = _downloadTasks.value[id]
        if (task != null) {
            val file = File(task.destinationPath)
            if (file.exists() && task.status != DownloadStatus.COMPLETED) {
                file.delete()
            }
        }
        _downloadTasks.value = _downloadTasks.value - id
    }

    private suspend fun executeDownload(context: Context, id: Long) {
        var retryCount = 0
        val maxRetries = 3
        var delayMs = 1000L

        while (retryCount <= maxRetries) {
            val currentTask = _downloadTasks.value[id] ?: return
            val targetFile = File(currentTask.destinationPath)
            val existingLength = if (targetFile.exists()) targetFile.length() else 0L

            updateTask(id) { it.copy(status = DownloadStatus.RUNNING, bytesDownloaded = existingLength) }

            try {
                val requestBuilder = Request.Builder().url(currentTask.url)
                if (existingLength > 0) {
                    requestBuilder.header("Range", "bytes=$existingLength-")
                }

                val response = httpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    if (retryCount < maxRetries) {
                        retryCount++
                        delay(delayMs)
                        delayMs *= 2
                        continue
                    }
                    updateTask(id) { it.copy(status = DownloadStatus.FAILED) }
                    return
                }

                val body = response.body ?: throw Exception("Empty response body")
                val isPartial = response.code == 206
                val contentLength = body.contentLength()
                val totalBytes = if (isPartial) existingLength + contentLength else contentLength

                val randomAccessFile = RandomAccessFile(targetFile, "rw")
                if (isPartial) {
                    randomAccessFile.seek(existingLength)
                } else {
                    randomAccessFile.setLength(0)
                }

                val inputStream = java.io.BufferedInputStream(body.byteStream(), 65536)
                val buffer = ByteArray(65536) // 64KB optimized I/O buffer
                var bytesRead: Int
                var totalRead = if (isPartial) existingLength else 0L
                var lastEmittedTime = System.currentTimeMillis()
                var lastEmittedBytes = totalRead

                val currentCoroutineContext = kotlin.coroutines.coroutineContext
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!currentCoroutineContext.isActive) break
                    randomAccessFile.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsedMs = now - lastEmittedTime

                    // Throttled & Debounced progress state update (every 250ms)
                    if (elapsedMs >= 250L || totalRead == totalBytes) {
                        val progressFraction = if (totalBytes > 0L) totalRead.toFloat() / totalBytes.toFloat() else 0f
                        val speedBps = if (elapsedMs > 0L) ((totalRead - lastEmittedBytes) * 1000L) / elapsedMs else 0L

                        updateTask(id) {
                            it.copy(
                                bytesDownloaded = totalRead,
                                totalBytes = totalBytes,
                                progressFraction = progressFraction,
                                speedBps = speedBps
                            )
                        }

                        lastEmittedTime = now
                        lastEmittedBytes = totalRead
                    }
                }

                randomAccessFile.close()
                inputStream.close()

                updateTask(id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        bytesDownloaded = totalRead,
                        totalBytes = totalBytes,
                        progressFraction = 1.0f,
                        speedBps = 0L
                    )
                }
                break // Success - exit retry loop
            } catch (e: Exception) {
                if (e is CancellationException) {
                    updateTask(id) { it.copy(status = DownloadStatus.PAUSED, speedBps = 0L) }
                    break
                } else {
                    if (retryCount < maxRetries) {
                        retryCount++
                        delay(delayMs)
                        delayMs *= 2
                    } else {
                        updateTask(id) { it.copy(status = DownloadStatus.FAILED, speedBps = 0L) }
                        break
                    }
                }
            }
        }
    }

    private fun updateTask(id: Long, transform: (PetalDownloadTask) -> PetalDownloadTask) {
        val currentMap = _downloadTasks.value
        val task = currentMap[id] ?: return
        _downloadTasks.value = currentMap + (id to transform(task))
    }
}
