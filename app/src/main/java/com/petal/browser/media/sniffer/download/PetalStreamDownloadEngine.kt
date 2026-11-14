package com.petal.browser.media.sniffer.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object PetalStreamDownloadEngine {
    private const val TAG = "PetalStreamDownloadEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun startStreamDownload(
        context: Context,
        request: StreamDownloadRequest,
        onProgress: (Int) -> Unit = {},
        onComplete: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        scope.launch {
            val workDir = File(context.cacheDir, "stream_dl_${UUID.randomUUID()}").apply { mkdirs() }
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
                        PetalDashSegmentResolver.resolve(
                            manifestUrl = videoUrl,
                            representationId = "",
                            kind = if (request.videoUrl != null) TrackKind.VIDEO else TrackKind.AUDIO,
                            referer = request.referer,
                            cookies = request.cookies,
                            userAgent = request.userAgent,
                            extraHeaders = request.headers
                        ) ?: throw IllegalStateException("Could not resolve DASH representation")
                    }
                }

                val segments = targetTrack.segments
                if (segments.isEmpty()) throw IllegalStateException("Stream contains 0 segments")

                val totalSegments = segments.size
                val downloadedFiles = mutableListOf<File>()
                val completedCount = AtomicInteger(0)

                // Download segments in chunks of concurrent batches
                val batchSize = request.concurrentSegments.coerceIn(1, 4)
                for (chunk in segments.chunked(batchSize)) {
                    val tasks = chunk.mapIndexed { idx, seg ->
                        async(Dispatchers.IO) {
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
                            }
                            val done = completedCount.incrementAndGet()
                            val progress = (done * 80 / totalSegments) // 0-80% for download
                            withContext(Dispatchers.Main) { onProgress(progress) }
                            segFile
                        }
                    }
                    downloadedFiles.addAll(tasks.awaitAll())
                }

                // Stage 2: Remuxing to MP4 / AAC (80-100%)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val cleanFilename = sanitizeFilename(request.filename)
                val isAudioOnly = request.videoUrl == null && request.audioUrl != null
                val finalExtension = if (isAudioOnly) "m4a" else "mp4"
                val finalFile = File(downloadsDir, "$cleanFilename.$finalExtension")

                withContext(Dispatchers.Main) { onProgress(85) }

                val converted = if (isAudioOnly) {
                    PetalTsToAacConverter.convert(
                        inputs = downloadedFiles,
                        output = finalFile,
                        isActive = { true },
                        onProgress = { p ->
                            val remuxProgress = 85 + (p * 15 / 100)
                            scope.launch(Dispatchers.Main) { onProgress(remuxProgress) }
                        }
                    )
                } else {
                    PetalTsToMp4Converter.convert(
                        inputs = downloadedFiles,
                        output = finalFile,
                        isActive = { true },
                        onProgress = { p ->
                            val remuxProgress = 85 + (p * 15 / 100)
                            scope.launch(Dispatchers.Main) { onProgress(remuxProgress) }
                        }
                    )
                }

                if (converted && finalFile.exists() && finalFile.length() > 0L) {
                    withContext(Dispatchers.Main) {
                        onProgress(100)
                        onComplete(finalFile)
                    }
                } else {
                    throw IllegalStateException("Stream remuxing failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream download error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown download error")
                }
            } finally {
                runCatching { workDir.deleteRecursively() }
            }
        }
    }

    private fun sanitizeFilename(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "stream_${System.currentTimeMillis()}" }
    }
}
