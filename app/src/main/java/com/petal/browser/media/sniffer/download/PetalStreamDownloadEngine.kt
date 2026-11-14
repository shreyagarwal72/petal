package com.petal.browser.media.sniffer.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.petal.browser.compose.downloads.DownloadStatus
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.compose.downloads.PetalLiveAlertManager
import com.petal.browser.download.DownloadSource
import com.petal.browser.download.PetalSmartDownloadRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object PetalStreamDownloadEngine {
    private const val TAG = "PetalStreamDownloadEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun isDownloading(id: String): Boolean = activeJobs[id]?.isActive == true

    fun cancelDownload(id: String) {
        activeJobs.remove(id)?.cancel()
    }

    fun startStreamDownload(
        context: Context,
        request: StreamDownloadRequest,
        onProgress: (Int) -> Unit = {},
        onComplete: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val taskId = request.id
        val isAudioOnly = request.videoUrl == null && request.audioUrl != null
        val finalExtension = if (isAudioOnly) "m4a" else "mp4"
        val cleanFilename = sanitizeFilename(request.filename)
        val targetFileName = "$cleanFilename.$finalExtension"
        val targetUrl = request.videoUrl ?: request.audioUrl ?: ""
        val mimeType = if (isAudioOnly) "audio/mp4" else "video/mp4"

        // Register cancel and retry handlers with PetalFetchDownloadBridge
        PetalFetchDownloadBridge.setLocalHandlers(
            key = taskId,
            onCancel = {
                cancelDownload(taskId)
                PetalFetchDownloadBridge.updateLocalRow(
                    context = appContext,
                    key = taskId,
                    source = DownloadSource.STREAM,
                    fileName = targetFileName,
                    fileUrl = targetUrl,
                    status = DownloadStatus.STATUS_FAILED,
                    failureReason = "Cancelled"
                )
            },
            onRetry = {
                startStreamDownload(appContext, request, onProgress, onComplete, onError)
            }
        )

        // Publish initial row state
        PetalFetchDownloadBridge.updateLocalRow(
            context = appContext,
            key = taskId,
            source = DownloadSource.STREAM,
            fileName = targetFileName,
            fileUrl = targetUrl,
            status = DownloadStatus.STATUS_RUNNING,
            progress = 0f,
            bytes = 0L,
            total = request.estimatedTotalBytes
        )
        PetalLiveAlertManager.trackDownload(appContext, PetalFetchDownloadBridge.stableId(taskId), targetFileName)

        val job = scope.launch {
            val workDir = File(appContext.cacheDir, "stream_dl_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val targetTrack = when (request.format) {
                    StreamContainerFormat.HLS -> {
                        val videoUrl = request.videoUrl ?: request.audioUrl ?: throw IllegalArgumentException("No stream URL")
                        PetalHlsPlaylistFetcher.fetch(
                            playlistUrl = videoUrl,
                            referer = request.referer,
                            cookies = request.cookies,
                            userAgent = request.userAgent,
                            kind = if (request.videoUrl != null) TrackKind.VIDEO else TrackKind.AUDIO,
                            extraHeaders = request.headers
                        ) ?: throw IllegalStateException("Could not resolve HLS playlist")
                    }
                    StreamContainerFormat.DASH -> {
                        val videoUrl = request.videoUrl ?: request.audioUrl ?: throw IllegalArgumentException("No stream URL")
                        PetalDashSegmentResolver.fetch(
                            mpdUrl = videoUrl,
                            referer = request.referer,
                            cookies = request.cookies,
                            userAgent = request.userAgent,
                            kind = if (request.videoUrl != null) TrackKind.VIDEO else TrackKind.AUDIO,
                            extraHeaders = request.headers
                        ) ?: throw IllegalStateException("Could not resolve DASH representation")
                    }
                }

                val segments = targetTrack.segments
                if (segments.isEmpty()) throw IllegalStateException("Stream contains 0 segments")

                val totalSegments = segments.size
                val downloadedFiles = mutableListOf<File>()
                val completedCount = AtomicInteger(0)
                val totalBytesDownloaded = AtomicLong(0L)

                var lastProgressTime = SystemClock.elapsedRealtime()
                var lastProgressBytes = 0L

                // Download segments in chunks of concurrent batches
                val batchSize = request.concurrentSegments.coerceIn(1, 4)
                for (chunk in segments.chunked(batchSize)) {
                    if (!isActive) throw CancellationException("Download cancelled")

                    val tasks = chunk.mapIndexed { _, seg ->
                        async(Dispatchers.IO) {
                            if (!isActive) throw CancellationException("Segment cancelled")
                            val segIndex = segments.indexOf(seg)
                            val segFile = File(workDir, "seg_%05d.ts".format(segIndex))
                            val builder = Request.Builder().url(seg.url)
                            StreamRequestHeaders.apply(builder, seg.url, request.referer, request.cookies, request.userAgent, request.headers)
                            if (seg.byteRangeLength != null && seg.byteRangeOffset != null) {
                                builder.header("Range", "bytes=${seg.byteRangeOffset}-${seg.byteRangeOffset + seg.byteRangeLength - 1}")
                            }

                            okHttpClient.newCall(builder.build()).execute().use { resp ->
                                if (!resp.isSuccessful) throw IllegalStateException("Segment fetch error: ${resp.code}")
                                var bytes = resp.body?.bytes() ?: throw IllegalStateException("Empty segment body")

                                // Decrypt if encrypted with AES-128
                                if (seg.key != null && PetalHlsAesDecryptor.isSupported(seg.key.method)) {
                                    val decrypted = PetalHlsAesDecryptor.decrypt(
                                        data = bytes,
                                        key = seg.key,
                                        sequenceNumber = seg.sequenceNumber,
                                        referer = request.referer,
                                        cookies = request.cookies,
                                        userAgent = request.userAgent,
                                        extraHeaders = request.headers
                                    )
                                    if (decrypted != null) bytes = decrypted
                                }

                                FileOutputStream(segFile).use { it.write(bytes) }
                                totalBytesDownloaded.addAndGet(bytes.size.toLong())
                            }

                            val done = completedCount.incrementAndGet()
                            val dlFraction = (done.toFloat() / totalSegments.toFloat()) * 0.80f
                            val currentDownloaded = totalBytesDownloaded.get()

                            val now = SystemClock.elapsedRealtime()
                            val deltaMs = now - lastProgressTime
                            var speed = 0L
                            var eta = 0L
                            if (deltaMs >= 500L) {
                                val deltaBytes = currentDownloaded - lastProgressBytes
                                if (deltaBytes > 0 && deltaMs > 0) {
                                    speed = (deltaBytes * 1000L) / deltaMs
                                    val remainingSegments = totalSegments - done
                                    val avgSegSize = currentDownloaded / done.coerceAtLeast(1)
                                    val remainingBytes = remainingSegments * avgSegSize
                                    if (speed > 0) {
                                        eta = remainingBytes / speed
                                    }
                                }
                                lastProgressTime = now
                                lastProgressBytes = currentDownloaded
                            }

                            val estTotal = if (request.estimatedTotalBytes > 0) {
                                request.estimatedTotalBytes
                            } else {
                                currentDownloaded * totalSegments / done.coerceAtLeast(1)
                            }

                            PetalFetchDownloadBridge.updateLocalRow(
                                context = appContext,
                                key = taskId,
                                source = DownloadSource.STREAM,
                                fileName = targetFileName,
                                fileUrl = targetUrl,
                                status = DownloadStatus.STATUS_RUNNING,
                                progress = dlFraction,
                                bytes = currentDownloaded,
                                total = estTotal,
                                speed = speed,
                                eta = eta
                            )

                            withContext(Dispatchers.Main) {
                                onProgress((dlFraction * 100).toInt())
                            }
                            segFile
                        }
                    }
                    downloadedFiles.addAll(tasks.awaitAll())
                }

                // Stage 2: Remuxing to MP4 / AAC (80-100%)
                val destinationDir = PetalSmartDownloadRouter.resolveDestinationDirectory(appContext, targetFileName, mimeType)
                val finalFile = File(destinationDir, targetFileName)

                PetalFetchDownloadBridge.updateLocalRow(
                    context = appContext,
                    key = taskId,
                    source = DownloadSource.STREAM,
                    fileName = targetFileName,
                    fileUrl = targetUrl,
                    status = DownloadStatus.STATUS_RUNNING,
                    progress = 0.82f,
                    bytes = totalBytesDownloaded.get(),
                    total = totalBytesDownloaded.get()
                )
                withContext(Dispatchers.Main) { onProgress(82) }

                val converted = if (isAudioOnly) {
                    PetalTsToAacConverter.convert(
                        inputs = downloadedFiles,
                        output = finalFile,
                        isActive = { isActive },
                        onProgress = { p ->
                            val remuxFraction = 0.82f + (p.toFloat() / 100f) * 0.18f
                            PetalFetchDownloadBridge.updateLocalRow(
                                context = appContext,
                                key = taskId,
                                source = DownloadSource.STREAM,
                                fileName = targetFileName,
                                fileUrl = targetUrl,
                                status = DownloadStatus.STATUS_RUNNING,
                                progress = remuxFraction,
                                bytes = totalBytesDownloaded.get(),
                                total = totalBytesDownloaded.get()
                            )
                            scope.launch(Dispatchers.Main) { onProgress((remuxFraction * 100).toInt()) }
                        }
                    )
                } else {
                    PetalTsToMp4Converter.convert(
                        inputs = downloadedFiles,
                        output = finalFile,
                        isActive = { isActive },
                        onProgress = { p ->
                            val remuxFraction = 0.82f + (p.toFloat() / 100f) * 0.18f
                            PetalFetchDownloadBridge.updateLocalRow(
                                context = appContext,
                                key = taskId,
                                source = DownloadSource.STREAM,
                                fileName = targetFileName,
                                fileUrl = targetUrl,
                                status = DownloadStatus.STATUS_RUNNING,
                                progress = remuxFraction,
                                bytes = totalBytesDownloaded.get(),
                                total = totalBytesDownloaded.get()
                            )
                            scope.launch(Dispatchers.Main) { onProgress((remuxFraction * 100).toInt()) }
                        }
                    )
                }

                if (converted && finalFile.exists() && finalFile.length() > 0L) {
                    try {
                        MediaScannerConnection.scanFile(
                            appContext,
                            arrayOf(finalFile.absolutePath),
                            arrayOf(mimeType),
                            null
                        )
                    } catch (_: Throwable) {
                    }

                    PetalFetchDownloadBridge.updateLocalRow(
                        context = appContext,
                        key = taskId,
                        source = DownloadSource.STREAM,
                        fileName = targetFileName,
                        fileUrl = targetUrl,
                        status = DownloadStatus.STATUS_SUCCESSFUL,
                        progress = 1.0f,
                        bytes = finalFile.length(),
                        total = finalFile.length(),
                        localUri = Uri.fromFile(finalFile).toString()
                    )

                    withContext(Dispatchers.Main) {
                        onProgress(100)
                        onComplete(finalFile)
                    }
                } else {
                    throw IllegalStateException("Stream remuxing failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream download error: ${e.message}", e)
                val isCancelled = !isActive || e is CancellationException || e is StreamCancelledException
                val failureReason = if (isCancelled) "Cancelled" else (e.message ?: "Unknown download error")
                PetalFetchDownloadBridge.updateLocalRow(
                    context = appContext,
                    key = taskId,
                    source = DownloadSource.STREAM,
                    fileName = targetFileName,
                    fileUrl = targetUrl,
                    status = DownloadStatus.STATUS_FAILED,
                    failureReason = failureReason
                )
                withContext(Dispatchers.Main) {
                    onError(failureReason)
                }
            } finally {
                activeJobs.remove(taskId)
                runCatching { workDir.deleteRecursively() }
            }
        }

        activeJobs[taskId] = job
    }

    private fun sanitizeFilename(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "stream_${System.currentTimeMillis()}" }
    }
}
