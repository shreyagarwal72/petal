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
 * The old Fetch2 "PARALLEL" mode pre-allocated the destination to Content-Length and let
 * chunks land wherever they landed, so a server that ignored a Range quietly left holes.
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

    data class Settings(val enabled: Boolean, val connections: Int) {
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
                return Settings(sp.getBoolean(KEY_ENABLED, true), n.coerceIn(1, MAX_CONNECTIONS))
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
        if (!cfg.enabled || cfg.connections < 2) return delegate.fetch(request)
        val startOffset = resumeOffset(request) ?: return delegate.fetch(request)

        return try {
            fetchParallel(request, startOffset, cfg.connections) ?: delegate.fetch(request)
        } catch (e: Exception) {
            // The probe itself failed. Fall back to the untouched request so the service
            // sees (and reports) the real error, or simply succeeds on a single stream.
            Log.w(TAG, "Parallel probe failed, using single stream: ${e.message}")
            delegate.fetch(request)
        }
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
                handedOff = true
                return probe
            }
            if (probe.status != 206) return null

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

        internal fun rangeHeadersFor(request: Request, from: Long, to: Long): MutableHeaders {
            val h = MutableHeaders()
            request.headers?.forEach { header ->
                if (!header.name.equals("Range", ignoreCase = true)) h.append(header.name, header.value)
            }
            h.append("Range", "bytes=$from-$to")
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
 * Sequential [InputStream] backed by a small pool of workers that fetch fixed-size segments
 * ahead of the reader. Memory is bounded: at most `workers + 3` segments are held at once.
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

    private val chunk = PetalParallelFetchClient.CHUNK_BYTES
    private val chunkCount: Int = (((total - start) + chunk - 1) / chunk).toInt()
    private val workerCount = minOf(connections, chunkCount).coerceAtLeast(1)
    private val window = workerCount + 3
    private val statKey = request.url

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val ready = HashMap<Int, ByteArray>()
    private val retryQueue = java.util.ArrayDeque<Int>()
    private val throttleCounts = ConcurrentHashMap<Int, Int>()
    private val allowedWorkers = AtomicInteger(workerCount)
    private val firstResponse = AtomicReference<Response?>(firstResponse)

    private var nextToAssign = 0
    private var consumeIndex = 0
    private var delivered = 0
    private var current: ByteArray? = null
    private var currentPos = 0

    @Volatile
    private var failure: IOException? = null

    @Volatile
    private var closed = false

    private val pool: ExecutorService = Executors.newFixedThreadPool(workerCount) { r ->
        Thread(r, "PetalParallel-${THREAD_COUNTER.incrementAndGet()}").apply { isDaemon = true }
    }

    init {
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
            if (consumeIndex >= chunkCount) {
                current = null
                return null
            }
            while (!ready.containsKey(consumeIndex)) {
                if (closed) throw IOException("Stream closed")
                failure?.let { throw it }
                changed.await(1, TimeUnit.SECONDS)
            }
            val data = ready.remove(consumeIndex)!!
            consumeIndex++
            current = data
            currentPos = 0
            changed.signalAll() // a window slot just opened up for the workers
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
        current = null
    }

    // ── Worker side ─────────────────────────────────────────────────────────────────────────

    private fun workerLoop(id: Int) {
        try {
            while (true) {
                val index = claim(id) ?: return
                val data: ByteArray = try {
                    fetchChunk(index)
                } catch (t: ThrottledException) {
                    val attempts = throttleCounts.merge(index, 1) { a, b -> a + b } ?: 1
                    if (attempts >= MAX_THROTTLE_ATTEMPTS) {
                        fail(IOException("Server refused ranged download (HTTP ${t.status})"))
                        return
                    }
                    requeue(index)
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
                    if (!closed) fail(t)
                    return
                }
                deliver(index, data)
            }
        } catch (t: Throwable) {
            if (!closed) fail(IOException("Parallel worker failed: ${t.message}", t))
        }
    }

    /** Blocks until there is a segment this worker may fetch; null when it should exit. */
    private fun claim(id: Int): Int? {
        lock.lock()
        try {
            while (true) {
                if (closed || failure != null) return null
                if (id > 0 && id >= allowedWorkers.get()) return null
                val retry = retryQueue.pollFirst()
                if (retry != null) return retry
                if (delivered >= chunkCount) return null
                if (nextToAssign < chunkCount && nextToAssign < consumeIndex + window) {
                    return nextToAssign++
                }
                changed.await()
            }
        } finally {
            lock.unlock()
        }
    }

    private fun deliver(index: Int, data: ByteArray) {
        lock.lock()
        try {
            if (closed) return
            ready[index] = data
            delivered++
            changed.signalAll()
        } finally {
            lock.unlock()
        }
    }

    private fun requeue(index: Int) {
        lock.lock()
        try {
            if (closed) return
            retryQueue.addFirst(index)
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
     * Downloads one segment. Handles servers that cap the range they return (206 for fewer
     * bytes than asked) by continuing from where the response ended, and retries transient
     * errors with backoff, switching from the resolved URL back to the original URL after two
     * failures in case the resolved one was single-use.
     */
    private fun fetchChunk(index: Int): ByteArray {
        val from = start + index.toLong() * chunk
        val to = minOf(from + chunk - 1, total - 1)
        val buf = ByteArray((to - from + 1).toInt())
        var pos = from
        var failures = 0
        var pending: Response? = if (index == 0) firstResponse.getAndSet(null) else null

        while (pos <= to) {
            if (closed) throw InterruptedException("closed")
            var response: Response? = pending
            pending = null
            try {
                if (response == null) {
                    val url = if (failures >= 2) request.url else finalUrl
                    response = client.fetch(
                        request.copy(url = url, headers = PetalParallelFetchClient.rangeHeadersFor(request, pos, to))
                    )
                }
                val status = response.status
                if (status == 429 || status == 503 || status == 403) throw ThrottledException(status)
                if (status != 206) throw IOException("Unexpected HTTP $status for range $pos-$to")

                val servedTo = verifyContentRange(response, pos, to)
                val length = (servedTo - pos + 1).toInt()
                val offset = (pos - from).toInt()
                response.body.useStream { stream -> readFully(stream, buf, offset, length) }
                pos += length
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
        return buf
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

    private fun readFully(stream: InputStream, buf: ByteArray, offset: Int, length: Int) {
        var done = 0
        while (done < length) {
            val n = stream.read(buf, offset + done, length - done)
            if (n < 0) throw IOException("Connection ended $done of $length bytes into a segment")
            done += n
        }
    }

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
        val THREAD_COUNTER = AtomicInteger(0)
    }
}
