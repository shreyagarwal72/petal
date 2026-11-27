package com.petal.browser.download

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * Multi-connection ("segmented") downloads for Mozilla's AbstractFetchDownloadService.
 *
 * ── How it plugs in ──────────────────────────────────────────────────────────────────────
 * AbstractFetchDownloadService asks a concept-fetch [Client] for ONE response whose body it
 * reads sequentially and writes to disk. It owns notifications, pause/resume, progress and
 * completion. This class wraps the real (Gecko) client and, when the server allows it, answers
 * that single request with a synthetic response whose body is fed by several concurrent HTTP
 * Range requests. The service never knows the difference, so nothing about its state handling
 * changes, and Gecko's cookies / private mode / proxy still apply to every segment.
 *
 * ── Why this cannot produce wrong-sized files ────────────────────────────────────────────
 * A naive parallel mode that pre-allocates the destination to Content-Length lets
 * chunks land wherever they land, so a server that ignored a Range would quietly leave holes.
 * Here the body is reassembled strictly in order and every segment is verified before it is
 * released: it must be HTTP 206, its Content-Range must start exactly where we asked, the
 * total must match the probe, the ETag must be unchanged, and exactly the expected number of
 * bytes must arrive. Anything else is retried, and if it still fails the stream errors out
 * (the service marks the download failed and resume restarts from the bytes already written)
 * instead of silently producing a bad file.
 *
 * ── Probe ────────────────────────────────────────────────────────────────────────────────
 * The first segment request doubles as the capability probe, so nothing is wasted:
 *  - 200 (server ignores Range)  -> returned untouched: a plain single-stream download.
 *  - anything unexpected         -> original request is re-issued untouched.
 *  - 206 with a known total      -> parallel mode; this response becomes segment 0.
 * Compressed bodies, unknown sizes and non-GET requests always take the single-stream path.
 *
 * ── Adaptive ─────────────────────────────────────────────────────────────────────────────
 * If a host answers 429/503/403 to extra connections, the allowed connection count is halved
 * (down to 1) and the segment retried with backoff, so per-IP connection limits degrade
 * gracefully to a normal download instead of failing.
 */
class PetalParallelFetchClient(
    private val delegate: Client,
    private val settings: () -> Settings
) : Client() {

    data class Settings(val enabled: Boolean, val connections: Int, val speedLimitKBps: Int = 0) {
        companion object {
            const val KEY_ENABLED = "sp_parallel_downloads"
            const val KEY_CONNECTIONS = "sp_parallel_connections"
            const val DEFAULT_CONNECTIONS = 4
            const val MAX_CONNECTIONS = 8

            @JvmStatic
            fun load(context: Context): Settings {
                val sp = PreferenceManager.getDefaultSharedPreferences(context)
                val n = try {
                    sp.getInt(KEY_CONNECTIONS, DEFAULT_CONNECTIONS)
                } catch (_: ClassCastException) {
                    DEFAULT_CONNECTIONS
                }
                val limit = try { sp.getInt(DownloadPrefs.KEY_SPEED_LIMIT_KBPS, 0) } catch (_: ClassCastException) { 0 }
                return Settings(sp.getBoolean(KEY_ENABLED, true), n.coerceIn(1, MAX_CONNECTIONS), limit)
            }
        }
    }

    @Throws(IOException::class)
    override fun fetch(request: Request): Response {
        val cfg = try {
            settings()
        } catch (_: Throwable) {
            Settings(false, 1)
        }
        PetalBandwidthLimiter.setLimitKBps(cfg.speedLimitKBps)
        if (!cfg.enabled || cfg.connections < 2) return throttleResponse(delegate.fetch(request))
        val startOffset = resumeOffset(request) ?: return throttleResponse(delegate.fetch(request))
        if (CapabilityCache.isSingleStreamOnly(request.url)) return throttleResponse(delegate.fetch(request))

        return try {
            val parallel = fetchParallel(request, startOffset, cfg.connections)
            when {
                parallel == null -> throttleResponse(delegate.fetch(request))
                parallel.body.stream is PetalParallelRangeStream -> parallel
                else -> throttleResponse(parallel)
            }
        } catch (e: Exception) {
            // The probe itself failed. Fall back to the untouched request so the service
            // sees (and reports) the real error, or simply succeeds on a single stream.
            Log.w(TAG, "Parallel probe failed, using single stream: ${e.message}")
            throttleResponse(delegate.fetch(request))
        }
    }

    /** Wraps a single-stream response body with [PetalBandwidthLimiter] so speed caps apply uniformly. */
    private fun throttleResponse(response: Response): Response {
        if (response.status !in 200..299) return response
        val originalBody = response.body
        val throttledStream = object : java.io.FilterInputStream(originalBody.stream) {
            override fun read(): Int {
                PetalBandwidthLimiter.acquire(1)
                return super.read()
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len <= 0) return 0
                val chunk = minOf(len, 64 * 1024)
                PetalBandwidthLimiter.acquire(chunk)
                return super.read(b, off, chunk)
            }
        }
        return Response(
            url = response.url,
            status = response.status,
            headers = response.headers,
            body = Response.Body(throttledStream, response.headers.get("Content-Type"))
        )
    }

    /** Byte offset to start from, or null when this request is not eligible for segmenting. */
    private fun resumeOffset(request: Request): Long? {
        if (!request.method.name.equals("GET", ignoreCase = true)) return null
        if (request.body != null) return null
        val url = request.url
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        val range = request.headers?.get("Range") ?: return 0L
        val match = OPEN_RANGE.matchEntire(range.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun fetchParallel(request: Request, start: Long, connections: Int): Response? {
        val probe = delegate.fetch(request.copy(headers = rangeHeaders(request, start, start + CHUNK_BYTES - 1)))
        var handedOff = false
        try {
            // Server ignored Range: this IS the complete single-stream answer. Hand it back as-is.
            if (probe.status == 200) {
                CapabilityCache.markSingleStream(request.url, probe.headers.get("ETag"))
                handedOff = true
                return probe
            }
            if (probe.status != 206) {
                if (probe.status in 200..299) {
                    CapabilityCache.markSingleStream(request.url, probe.headers.get("ETag"))
                    return null
                }
                // Return terminal HTTP error responses (401/403/404/410/429/etc.) directly so single-use
                // signed URLs are not fetched twice; only fall back to plain GET on range-rejection codes.
                if (probe.status !in listOf(400, 405, 416, 501)) {
                    handedOff = true
                    return probe
                }
                return null
            }

            val contentRange = probe.headers.get("Content-Range") ?: return null
            val match = CONTENT_RANGE.matchEntire(contentRange.trim()) ?: return null
            val first = match.groupValues[1].toLongOrNull() ?: return null
            val last = match.groupValues[2].toLongOrNull() ?: return null
            val total = match.groupValues[3].toLongOrNull() ?: return null // "*" = unknown size
            if (first != start || last < first || total <= start) return null

            val encoding = probe.headers.get("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) return null

            val stream = PetalParallelRangeStream(
                client = delegate,
                request = request,
                finalUrl = probe.url,
                start = start,
                total = total,
                connections = connections,
                etag = probe.headers.get("ETag"),
                firstResponse = probe
            )
            handedOff = true

            val hadRange = request.headers?.get("Range") != null
            val headers = MutableHeaders()
            probe.headers.forEach { header ->
                val name = header.name
                if (name.equals("Content-Range", true) ||
                    name.equals("Content-Length", true) ||
                    name.equals("Content-MD5", true) || // describes the 206 slice, not the whole file
                    name.equals("Transfer-Encoding", true)
                ) return@forEach
                headers.append(name, header.value)
            }
            if (hadRange) headers.append("Content-Range", "bytes $start-${total - 1}/$total")
            headers.append("Content-Length", (total - start).toString())

            return Response(
                url = probe.url,
                status = if (hadRange) 206 else 200,
                headers = headers,
                body = Response.Body(stream, probe.headers.get("Content-Type"))
            )
        } finally {
            if (!handedOff) closeQuietly(probe)
        }
    }

    private fun rangeHeaders(request: Request, from: Long, to: Long): MutableHeaders =
        rangeHeadersFor(request, from, to)

    companion object {
        private const val TAG = "PetalParallelFetch"
        internal const val CHUNK_BYTES = 2L * 1024 * 1024
        private val OPEN_RANGE = Regex("""bytes=(\d+)-""", RegexOption.IGNORE_CASE)
        internal val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)

        internal fun rangeHeadersFor(request: Request, from: Long, to: Long, etag: String? = null): MutableHeaders {
            val h = MutableHeaders()
            request.headers?.forEach { header ->
                if (!header.name.equals("Range", ignoreCase = true) && !header.name.equals("If-Range", ignoreCase = true)) {
                    h.append(header.name, header.value)
                }
            }
            h.append("Range", "bytes=$from-$to")
            if (!etag.isNullOrBlank()) {
                h.append("If-Range", etag.trim())
            }
            return h
        }

        internal fun closeQuietly(response: Response?) {
            try {
                response?.body?.close()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Remembers URLs whose server ignored Range, so resume/retry skips the segmented probe.
 * Keyed by URL (+ETag when known), short TTL so a server change is picked up again.
 */
internal object CapabilityCache {
    private const val TTL_MS = 10 * 60 * 1000L
    private const val MAX = 128
    private class Mark(val etag: String?, val at: Long)
    private val marks = ConcurrentHashMap<String, Mark>()

    fun markSingleStream(url: String, etag: String?, now: Long = System.currentTimeMillis()) {
        if (marks.size >= MAX) marks.entries.removeAll { now - it.value.at > TTL_MS }
        if (marks.size >= MAX) marks.keys.firstOrNull()?.let { marks.remove(it) }
        marks[url] = Mark(etag, now)
    }

    fun isSingleStreamOnly(url: String, now: Long = System.currentTimeMillis()): Boolean {
        val m = marks[url] ?: return false
        if (now - m.at > TTL_MS) { marks.remove(url); return false }
        return true
    }

    fun clear() = marks.clear()
}

/** Live per-download connection counts, read by the UI to show a "x4" parallel badge. */
object PetalParallelStats {
    private val connections = ConcurrentHashMap<String, Int>()

    internal fun set(url: String, count: Int) {
        connections[url] = count
    }

    internal fun clear(url: String) {
        connections.remove(url)
    }

    @JvmStatic
    fun connectionsFor(url: String): Int = connections[url] ?: 1
}

private class ThrottledException(val status: Int) : IOException("Server throttled ranged request (HTTP $status)")
private class FatalChunkException(message: String) : IOException(message)

/**
 * Sequential [InputStream] backed by a small pool of workers that fetch byte ranges ahead of the
 * reader (see [SegmentTable] for how ranges are sized and split).
 *
 * Memory is bounded: no more than [MEMORY_CAP_BYTES] beyond the read position is ever assigned.
 * Connections come from the shared [PetalConnectionBudget] and every read goes through the
 * global [PetalBandwidthLimiter].
 */
internal class PetalParallelRangeStream(
    private val client: Client,
    private val request: Request,
    private val finalUrl: String,
    private val start: Long,
    private val total: Long,
    connections: Int,
    private val etag: String?,
    firstResponse: Response
) : InputStream() {

    private val statKey = request.url
    private val host: String = try { java.net.URI(request.url).host ?: "" } catch (_: Exception) { "" }
    private val table = SegmentTable(start, total, minOf(PetalParallelFetchClient.CHUNK_BYTES, total - start))
    private val workerCount: Int
    private val startedAtNanos = System.nanoTime()
    private val bytesRead = AtomicLong(0)

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val ready = HashMap<Long, ByteArray>()
    private val retryQueue = java.util.ArrayDeque<SegmentTable.Seg>()
    private val throttleCounts = ConcurrentHashMap<Long, Int>()
    private val allowedWorkers: AtomicInteger
    private val firstResponse = AtomicReference<Response?>(firstResponse)

    private var consumePos = start
    private var current: ByteArray? = null
    private var currentPos = 0

    @Volatile
    private var failure: IOException? = null

    @Volatile
    private var closed = false

    private val pool: ExecutorService

    init {
        val maxUseful = (((total - start) + SegmentTable.MIN_SEGMENT - 1) / SegmentTable.MIN_SEGMENT).toInt().coerceAtLeast(1)
        workerCount = PetalConnectionBudget.acquire(host, minOf(connections, maxUseful))
        allowedWorkers = AtomicInteger(workerCount)
        pool = Executors.newFixedThreadPool(workerCount) { r ->
            Thread(r, "PetalParallel-${THREAD_COUNTER.incrementAndGet()}").apply { isDaemon = true }
        }
        PetalParallelStats.set(statKey, workerCount)
        for (id in 0 until workerCount) pool.execute { workerLoop(id) }
    }

    // ── Consumer side (the download service reads here, in order) ───────────────────────────

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var cur = current
        if (cur == null || currentPos >= cur.size) {
            cur = nextChunk() ?: return -1
        }
        val n = minOf(len, cur.size - currentPos)
        System.arraycopy(cur, currentPos, b, off, n)
        currentPos += n
        return n
    }

    override fun available(): Int = current?.let { it.size - currentPos } ?: 0

    private fun nextChunk(): ByteArray? {
        lock.lock()
        try {
            if (consumePos >= total) {
                current = null
                return null
            }
            while (!ready.containsKey(consumePos)) {
                if (closed) throw IOException("Stream closed")
                failure?.let { throw it }
                changed.await(1, TimeUnit.SECONDS)
            }
            val data = ready.remove(consumePos)!!
            consumePos += data.size
            current = data
            currentPos = 0
            changed.signalAll() // memory window opened up for the workers
            return data
        } finally {
            lock.unlock()
        }
    }

    override fun close() {
        lock.lock()
        try {
            if (closed) return
            closed = true
            ready.clear()
            retryQueue.clear()
            changed.signalAll()
        } finally {
            lock.unlock()
        }
        pool.shutdownNow()
        PetalParallelFetchClient.closeQuietly(firstResponse.getAndSet(null))
        PetalParallelStats.clear(statKey)
        PetalConnectionBudget.release(host, workerCount)
        current = null
    }

    // ── Worker side ─────────────────────────────────────────────────────────────────────────

    private fun workerLoop(id: Int) {
        try {
            while (true) {
                val seg = claim(id) ?: return
                val data: ByteArray = try {
                    fetchSegment(seg)
                } catch (t: ThrottledException) {
                    val attempts = throttleCounts.merge(seg.from, 1) { a, b -> a + b } ?: 1
                    if (attempts >= MAX_THROTTLE_ATTEMPTS) {
                        fail(IOException("Server refused ranged download (HTTP ${t.status})"))
                        return
                    }
                    requeue(seg)
                    shrinkConnections()
                    if (id > 0 && id >= allowedWorkers.get()) return // this worker retires
                    sleepBackoff(attempts)
                    continue
                } catch (t: FatalChunkException) {
                    fail(t)
                    return
                } catch (t: InterruptedException) {
                    return
                } catch (t: IOException) {
                    val attempts = throttleCounts.merge(seg.from, 1) { a, b -> a + b } ?: 1
                    if (attempts < MAX_THROTTLE_ATTEMPTS && !closed) {
                        requeue(seg)
                        shrinkConnections()
                        if (id > 0 && id >= allowedWorkers.get()) return
                        sleepBackoff(attempts)
                        continue
                    }
                    if (!closed) fail(t)
                    return
                }
                deliver(seg, data)
            }
        } catch (t: Throwable) {
            if (!closed) fail(IOException("Parallel worker failed: ${t.message}", t))
        }
    }

    /** Aggregate speed divided by live workers; 0 until enough time has passed to measure. */
    private fun perConnectionSpeed(): Double {
        val elapsed = (System.nanoTime() - startedAtNanos) / 1e9
        if (elapsed < 1.5) return 0.0
        return bytesRead.get() / elapsed / allowedWorkers.get().coerceAtLeast(1)
    }

    /** Blocks until there is a segment this worker may fetch; null when it should exit. */
    private fun claim(id: Int): SegmentTable.Seg? {
        lock.lock()
        try {
            while (true) {
                if (closed || failure != null) return null
                if (id > 0 && id >= allowedWorkers.get()) return null
                val retry = retryQueue.pollFirst()
                if (retry != null) {
                    table.reacquire(retry)
                    return retry
                }
                if (consumePos >= total) return null
                if (!table.allAssigned) {
                    if (table.assignedEndOffset - consumePos < MEMORY_CAP_BYTES) {
                        table.allocate(SegmentTable.adaptiveSize(perConnectionSpeed()))?.let { return it }
                    }
                } else {
                    // Everything is handed out and this worker is idle: take half of the slowest segment.
                    table.splitSlowest(SegmentTable.MIN_SEGMENT)?.let { return it }
                }
                changed.await(500, TimeUnit.MILLISECONDS)
            }
        } finally {
            lock.unlock()
        }
    }

    private fun deliver(seg: SegmentTable.Seg, data: ByteArray) {
        lock.lock()
        try {
            if (closed) return
            ready[seg.from] = data
            changed.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun requeue(seg: SegmentTable.Seg) {
        lock.lock()
        try {
            if (closed) return
            table.release(seg)
            retryQueue.addFirst(seg)
            changed.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun fail(error: IOException) {
        lock.lock()
        try {
            if (failure == null) failure = error
            changed.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun shrinkConnections() {
        val now = allowedWorkers.updateAndGet { maxOf(1, it / 2) }
        PetalParallelStats.set(statKey, now)
        Log.i(TAG, "Server throttled extra connections; now using $now for ${statKey.take(80)}")
    }

    /**
     * Downloads one segment. The segment's end can shrink while it is being read (another worker
     * took its tail), so the loop re-reads `seg.to` on every iteration and the result is trimmed
     * to the final length. Handles servers that cap the range they return (206 for fewer bytes
     * than asked) by continuing from where the response ended, and retries transient errors with
     * backoff, switching from the resolved URL back to the original URL after two failures.
     * A 403 is retried once on the original URL, then fails clearly (it is not throttling).
     */
    private fun fetchSegment(seg: SegmentTable.Seg): ByteArray {
        val buf = ByteArray(seg.length.toInt())
        var failures = 0
        var forbiddenRetried = false
        var forceOriginalUrl = false
        var pending: Response? = if (seg.from == start) firstResponse.getAndSet(null) else null

        while (seg.pos <= seg.to) {
            if (closed) throw InterruptedException("closed")
            var response: Response? = pending
            pending = null
            try {
                if (response == null) {
                    val url = if (failures >= 2 || forceOriginalUrl) request.url else finalUrl
                    response = client.fetch(
                        request.copy(url = url, headers = PetalParallelFetchClient.rangeHeadersFor(request, seg.pos, seg.to, etag))
                    )
                }
                val status = response.status
                if (status == 403) {
                    if (!forbiddenRetried) {
                        forbiddenRetried = true
                        forceOriginalUrl = true
                        PetalParallelFetchClient.closeQuietly(response)
                        response = null
                        continue
                    }
                    throw FatalChunkException("Access denied (HTTP 403). The link may have expired or needs a fresh sign-in.")
                }
                if (status == 429 || status == 503) throw ThrottledException(status)
                if (status != 206) throw IOException("Unexpected HTTP $status for range ${seg.pos}-${seg.to}")

                val servedTo = verifyContentRange(response, seg.pos, seg.to)
                response.body.useStream { stream ->
                    while (seg.pos <= minOf(servedTo, seg.to)) {
                        if (closed) throw InterruptedException("closed")
                        val limit = minOf(servedTo, seg.to)
                        val want = minOf(READ_BUFFER, limit - seg.pos + 1).toInt()
                        PetalBandwidthLimiter.acquire(want)
                        val n = stream.read(buf, (seg.pos - seg.from).toInt(), want)
                        if (n < 0) throw IOException("Connection ended at ${seg.pos - seg.from} of ${seg.length} bytes into a segment")
                        seg.pos += n
                        bytesRead.addAndGet(n.toLong())
                    }
                }
                failures = 0
            } catch (e: ThrottledException) {
                throw e
            } catch (e: FatalChunkException) {
                throw e
            } catch (e: InterruptedException) {
                throw e
            } catch (e: IOException) {
                if (closed) throw InterruptedException("closed")
                failures++
                if (failures >= MAX_ATTEMPTS) throw e
                sleepBackoff(failures)
            } finally {
                PetalParallelFetchClient.closeQuietly(response)
            }
        }
        val finalLength = table.finish(seg).toInt()
        return if (finalLength == buf.size) buf else buf.copyOf(finalLength)
    }

    /** Validates a 206 response and returns the last byte index it actually serves (<= [to]). */
    private fun verifyContentRange(response: Response, pos: Long, to: Long): Long {
        val header = response.headers.get("Content-Range")
            ?: throw IOException("Missing Content-Range on 206 response")
        val m = PetalParallelFetchClient.CONTENT_RANGE.matchEntire(header.trim())
            ?: throw IOException("Unparseable Content-Range: $header")
        val a = m.groupValues[1].toLong()
        val b = m.groupValues[2].toLong()
        val t = m.groupValues[3].toLongOrNull()
        if (a != pos || b < pos) throw IOException("Server returned range $a-$b, wanted $pos-$to")
        if (t != null && t != total) throw FatalChunkException("File size changed during download")
        val tag = response.headers.get("ETag")
        if (etag != null && tag != null && normalize(etag) != normalize(tag)) {
            throw FatalChunkException("File changed on the server during download")
        }
        return minOf(b, to)
    }

    private fun normalize(tag: String) = tag.removePrefix("W/").trim()

    private fun sleepBackoff(attempt: Int) {
        val ms = minOf(300L shl (attempt - 1).coerceIn(0, 5), 8_000L)
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    private companion object {
        const val TAG = "PetalParallelStream"
        const val MAX_ATTEMPTS = 4
        const val MAX_THROTTLE_ATTEMPTS = 6
        const val READ_BUFFER = 64L * 1024
        const val MEMORY_CAP_BYTES = 48L * 1024 * 1024
        val THREAD_COUNTER = AtomicInteger(0)
    }
}
