package com.petal.browser.media.sniffer.capture

import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object PetalFileMetadataProbe {

    data class Info(
        val width: Int?,
        val height: Int?,
        val durationSeconds: Double?,
        val bitrateBitsPerSec: Long?
    )

    private const val BLOCK_BYTES = 256 * 1024
    private const val MAX_CACHED_BLOCKS = 32
    private const val MAX_TOTAL_FETCH_BYTES = 24L * 1024 * 1024
    private const val PROBE_DEADLINE_NANOS = 10_000_000_000L
    private const val READ_CHUNK_BYTES = 16 * 1024

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun probe(media: CapturedMediaItem, pageUrl: String): Info? {
        val source = HttpRangeDataSource(
            media.url,
            pageUrl,
            media.requestHeaders,
            media.sizeBytes ?: -1L,
            System.nanoTime() + PROBE_DEADLINE_NANOS
        )
        val wantVideo = media.kind == MediaKind.VIDEO
        val extractor = MediaExtractor()
        var result: Info? = null
        try {
            extractor.setDataSource(source)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val durationSeconds = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val d = format.getLong(MediaFormat.KEY_DURATION)
                    if (d > 0L) d / 1_000_000.0 else null
                } else null

                if (wantVideo) {
                    if (!mime.startsWith("video/")) continue
                    val width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else continue
                    val height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else continue
                    if (width <= 0 || height <= 0) continue
                    val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
                    val swapped = rotation == 90 || rotation == 270
                    result = Info(
                        if (swapped) height else width,
                        if (swapped) width else height,
                        durationSeconds,
                        null
                    )
                    break
                } else if (media.kind == MediaKind.AUDIO) {
                    if (!mime.startsWith("audio/")) continue
                    val bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) format.getInteger(MediaFormat.KEY_BIT_RATE).toLong() else null
                    result = Info(null, null, durationSeconds, bitrate)
                    break
                }
            }
        } catch (_: Exception) {
            // Ignored, fallback to manifest info
        } finally {
            runCatching { extractor.release() }
            runCatching { source.close() }
        }
        return result
    }

    private class HttpRangeDataSource(
        private val url: String,
        private val referer: String,
        private val headers: Map<String, String>,
        sizeHint: Long,
        private val deadlineNanos: Long
    ) : MediaDataSource() {

        private var knownSize = sizeHint
        private var totalFetched = 0L
        private val blocks = LinkedHashMap<Long, ByteArray>(MAX_CACHED_BLOCKS, 0.75f, true)

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (System.nanoTime() > deadlineNanos) return -1
            if (knownSize in 0..position) return -1
            if (size <= 0) return 0

            var bytesCopied = 0
            var currentPos = position

            while (bytesCopied < size) {
                if (System.nanoTime() > deadlineNanos) break
                val blockIndex = currentPos / BLOCK_BYTES
                val blockOffset = (currentPos % BLOCK_BYTES).toInt()
                val blockData = getBlock(blockIndex) ?: break

                val toCopy = minOf(blockData.size - blockOffset, size - bytesCopied)
                if (toCopy <= 0) break
                System.arraycopy(blockData, blockOffset, buffer, offset + bytesCopied, toCopy)
                bytesCopied += toCopy
                currentPos += toCopy
                if (blockData.size < BLOCK_BYTES) break
            }
            return if (bytesCopied > 0) bytesCopied else -1
        }

        private fun getBlock(index: Long): ByteArray? {
            blocks[index]?.let { return it }
            if (totalFetched >= MAX_TOTAL_FETCH_BYTES) return null

            val start = index * BLOCK_BYTES
            val end = start + BLOCK_BYTES - 1
            val builder = Request.Builder().url(url).header("Range", "bytes=$start-$end")
            if (referer.isNotBlank()) builder.header("Referer", referer)
            headers.forEach { (k, v) -> builder.header(k, v) }

            return try {
                httpClient.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) return null
                    if (knownSize < 0L) {
                        resp.header("Content-Range")?.let { cr ->
                            val slash = cr.lastIndexOf('/')
                            if (slash >= 0) knownSize = cr.substring(slash + 1).toLongOrNull() ?: -1L
                        }
                        if (knownSize < 0L) {
                            resp.body?.contentLength()?.takeIf { it > 0L }?.let { knownSize = it }
                        }
                    }
                    val body = resp.body ?: return null
                    val bytes = readStreamLimit(body.byteStream(), BLOCK_BYTES)
                    totalFetched += bytes.size
                    if (blocks.size >= MAX_CACHED_BLOCKS) {
                        val firstKey = blocks.keys.firstOrNull()
                        if (firstKey != null) blocks.remove(firstKey)
                    }
                    blocks[index] = bytes
                    bytes
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun readStreamLimit(input: InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(READ_CHUNK_BYTES)
            var total = 0
            while (total < limit) {
                val read = input.read(buf, 0, minOf(buf.size, limit - total))
                if (read < 0) break
                out.write(buf, 0, read)
                total += read
            }
            return out.toByteArray()
        }

        override fun getSize(): Long = knownSize

        override fun close() {
            blocks.clear()
        }
    }
}
