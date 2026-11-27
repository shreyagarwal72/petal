package com.petal.browser.download

import com.petal.browser.download.queue.QueueScheduler
import com.petal.browser.download.queue.QueuedDownload
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Headers
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Tests for the download engine v2 work: header lifetime, cookie capture, 403 handling, splitting, queue, import. */
class DownloadEngineV2Test {

    @Before
    fun setUp() {
        PetalGeckoCookieBridge.clearAllCookies()
        PetalDownloadHeaderRegistry.clearPrivateHeaders()
        PetalBandwidthLimiter.resetForTest()
        PetalConnectionBudget.resetForTest()
        CapabilityCache.clear()
    }

    @After
    fun tearDown() {
        PetalGeckoCookieBridge.clearAllCookies()
        PetalBandwidthLimiter.resetForTest()
    }

    // ── Header registry lifetime ────────────────────────────────────────────

    @Test
    fun headerEntryBoundToDownloadSurvivesUntilReleased() {
        val url = "https://cdn.example.com/big.iso"
        PetalDownloadHeaderRegistry.put(url, mapOf("Referer" to "https://example.com/"), downloadId = "dl-1")
        assertEquals("https://example.com/", PetalDownloadHeaderRegistry.headersFor(url, "dl-1")["Referer"])
        // Looked up by id alone (no URL) still works.
        assertEquals("https://example.com/", PetalDownloadHeaderRegistry.headersFor("", "dl-1")["Referer"])

        PetalDownloadHeaderRegistry.release("dl-1", url)
        assertTrue(PetalDownloadHeaderRegistry.headersFor(url, "dl-1").isEmpty())
    }

    @Test
    fun releasingOneDownloadDoesNotRemoveAnotherDownloadsAlias() {
        val url = "https://cdn.example.com/same.bin"
        PetalDownloadHeaderRegistry.put(url, mapOf("Referer" to "A"), downloadId = "dl-a")
        PetalDownloadHeaderRegistry.put(url, mapOf("Referer" to "B"), downloadId = "dl-b") // last writer owns the URL alias
        PetalDownloadHeaderRegistry.release("dl-a", url)
        assertEquals("B", PetalDownloadHeaderRegistry.headersFor(url)["Referer"])
        assertEquals("B", PetalDownloadHeaderRegistry.headersFor("", "dl-b")["Referer"])
        PetalDownloadHeaderRegistry.release("dl-b", url)
    }

    @Test
    fun secretHeadersAreNeverPersistable() {
        val safe = PetalDownloadHeaderRegistry.persistable(
            mapOf("Cookie" to "a=b", "authorization" to "Bearer x", "Proxy-Authorization" to "y", "Referer" to "r", "Accept-Language" to "en")
        )
        assertEquals(setOf("Referer", "Accept-Language"), safe.keys)
    }

    @Test
    fun privateHeadersStayInMemoryAndAreIsolated() {
        val url = "https://private.example.com/f.zip"
        PetalDownloadHeaderRegistry.put(url, mapOf("Authorization" to "Bearer secret"), downloadId = "p-1", isPrivate = true)
        assertTrue(PetalDownloadHeaderRegistry.headersFor(url, "p-1", isPrivate = false).isEmpty())
        assertEquals("Bearer secret", PetalDownloadHeaderRegistry.headersFor(url, "p-1", isPrivate = true)["Authorization"])
        PetalDownloadHeaderRegistry.clearPrivateHeaders()
        assertTrue(PetalDownloadHeaderRegistry.headersFor(url, "p-1", isPrivate = true).isEmpty())
    }

    // ── Cookie bridge: capture, expiry, private isolation ────────────────────

    @Test
    fun capturedCookiesExpire() {
        var now = 1_000_000L
        PetalGeckoCookieBridge.clock = { now }
        try {
            PetalGeckoCookieBridge.recordCookies("https://a.example.com/x", "s=1", isPrivate = false)
            assertEquals("s=1", PetalGeckoCookieBridge.getCookies("https://a.example.com/y", isPrivate = false))
            now += 31 * 60 * 1000L
            assertNull(PetalGeckoCookieBridge.getCookies("https://a.example.com/y", isPrivate = false))
        } finally {
            PetalGeckoCookieBridge.clock = System::currentTimeMillis
        }
    }

    @Test
    fun privateCookiesNeverReachStandardLookups() {
        PetalGeckoCookieBridge.recordCookies("https://bank.example.com/", "sid=private", isPrivate = true)
        assertNull(PetalGeckoCookieBridge.getCookies("https://bank.example.com/", isPrivate = false))
        assertEquals("sid=private", PetalGeckoCookieBridge.getCookies("https://bank.example.com/", isPrivate = true))
    }

    // ── Parallel client: 403 handling ─────────────────────────────────────────

    private fun status(code: Int) = Response(url = "https://x.test/f", status = code, headers = Headers.EMPTY, body = Response.Body.empty())

    @Test
    fun forbiddenIsRetriedOnceThenFailsWithClearReason() {
        val calls = AtomicInteger(0)
        val client = object : Client() {
            override fun fetch(request: Request): Response {
                calls.incrementAndGet()
                return status(403)
            }
        }
        val request = Request(url = "https://x.test/f", private = false)
        val stream = PetalParallelRangeStream(client, request, "https://resolved.test/f", 0L, 8L * 1024 * 1024, 4, null, status(403))
        try {
            stream.read(ByteArray(16), 0, 16)
            fail("expected an IOException")
        } catch (e: IOException) {
            assertTrue(e.message ?: "", e.message!!.contains("403"))
            assertFalse("must not be reported as throttling", e.message!!.contains("throttl", ignoreCase = true))
        } finally {
            stream.close()
        }
        // The first (probe) 403 is retried once on the original URL per segment worker; never 6x.
        assertTrue("fetch calls: ${calls.get()}", calls.get() in 1..8)
    }

    @Test
    fun rangeIgnoredResultIsCachedPerUrl() {
        assertFalse(CapabilityCache.isSingleStreamOnly("https://x.test/a"))
        CapabilityCache.markSingleStream("https://x.test/a", "\"etag\"")
        assertTrue(CapabilityCache.isSingleStreamOnly("https://x.test/a"))
        assertFalse(CapabilityCache.isSingleStreamOnly("https://x.test/other"))
        assertFalse("expires", CapabilityCache.isSingleStreamOnly("https://x.test/a", now = System.currentTimeMillis() + 11 * 60 * 1000L))
    }

    // ── Segment splitter ──────────────────────────────────────────────────────

    @Test
    fun segmentsCoverTheFileExactlyOnce() {
        val total = 10L * 1024 * 1024 + 123
        val t = SegmentTable(0, total, 2L * 1024 * 1024)
        var covered = 0L
        var expectedFrom = 0L
        while (true) {
            val s = t.allocate(SegmentTable.adaptiveSize(0.0)) ?: break
            assertEquals(expectedFrom, s.from)
            covered += s.length
            expectedFrom = s.to + 1
        }
        assertEquals(total, covered)
        assertTrue(t.allAssigned)
    }

    @Test
    fun slowestSegmentIsSplitOnceEverythingIsAssigned() {
        val t = SegmentTable(0, 6L * 1024 * 1024, 2L * 1024 * 1024)
        val a = t.allocate(2L * 1024 * 1024)!!
        val b = t.allocate(2L * 1024 * 1024)!!
        val c = t.allocate(2L * 1024 * 1024)!!
        assertTrue(t.allAssigned)
        a.pos = a.to + 1 // a finished reading
        b.pos = b.to - 100 // b nearly done
        // c is the slowest: untouched
        val tail = t.splitSlowest(SegmentTable.MIN_SEGMENT)!!
        assertEquals(c.to + 1, tail.from) // c was shortened so that tail starts right after it
        assertTrue(c.length + tail.length == 2L * 1024 * 1024)
        assertTrue(tail.inFlight)
    }

    @Test
    fun smallRemainderIsNotSplit() {
        val t = SegmentTable(0, 1024L * 1024, 1024L * 1024)
        t.allocate(1024L * 1024)!!
        assertNull(t.splitSlowest(SegmentTable.MIN_SEGMENT)) // remaining < 2 * minTail
    }

    @Test
    fun adaptiveSizeIsClamped() {
        assertEquals(SegmentTable.DEFAULT_SEGMENT, SegmentTable.adaptiveSize(0.0))
        assertEquals(SegmentTable.MIN_SEGMENT, SegmentTable.adaptiveSize(1_000.0))
        assertEquals(SegmentTable.MAX_SEGMENT, SegmentTable.adaptiveSize(500_000_000.0))
        assertEquals(3_000_000L, SegmentTable.adaptiveSize(1_500_000.0))
    }

    // ── Global / per-host connection caps ────────────────────────────────────

    @Test
    fun perHostAndGlobalCapsAreEnforcedButNeverBelowOne() {
        PetalConnectionBudget.hostCap = 6
        PetalConnectionBudget.globalCap = 10
        assertEquals(4, PetalConnectionBudget.acquire("a.com", 4))
        assertEquals(2, PetalConnectionBudget.acquire("a.com", 4)) // host cap 6 reached
        assertEquals(4, PetalConnectionBudget.acquire("b.com", 4)) // global 10 reached
        assertEquals(1, PetalConnectionBudget.acquire("c.com", 4)) // always at least one
        PetalConnectionBudget.release("a.com", 6)
        assertEquals(4, PetalConnectionBudget.acquire("a.com", 4))
    }

    // ── Bandwidth limiter ─────────────────────────────────────────────────────

    @Test
    fun tokenBucketThrottlesToTheConfiguredRate() {
        var nanos = 0L
        var slept = 0L
        PetalBandwidthLimiter.nanoClock = { nanos }
        PetalBandwidthLimiter.sleeper = { ms -> slept += ms; nanos += ms * 1_000_000L }
        PetalBandwidthLimiter.setLimitKBps(100) // 102_400 B/s
        PetalBandwidthLimiter.acquire(102_400) // the initial one-second burst is free
        assertEquals(0L, slept)
        PetalBandwidthLimiter.acquire(102_400) // must now wait about one second
        assertTrue("slept $slept ms", slept in 900..1100)
    }

    @Test
    fun unlimitedNeverBlocks() {
        PetalBandwidthLimiter.setLimitKBps(0)
        PetalBandwidthLimiter.sleeper = { fail("must not sleep") }
        PetalBandwidthLimiter.acquire(10_000_000)
    }

    // ── Queue scheduling ──────────────────────────────────────────────────────

    private fun q(id: Long, url: String = "https://x.test/$id", priority: Int = 0, created: Long = id, state: Int = QueuedDownload.STATE_QUEUED, wifiOnly: Boolean = false) =
        QueuedDownload(id = id, url = url, fileName = "f$id", priority = priority, createdAt = created, state = state, wifiOnly = wifiOnly)

    private val online = QueueScheduler.Network(online = true, metered = false)

    @Test
    fun concurrencyLimitAndPriorityOrder() {
        val entries = listOf(q(1), q(2, priority = QueuedDownload.PRIORITY_HIGH), q(3), q(4))
        val picked = QueueScheduler.pickToStart(entries, activeCount = 1, QueueScheduler.Limits(3, false), online, now = 0)
        assertEquals(listOf(2L, 1L), picked.map { it.id }) // 3 max - 1 active = 2 slots; high priority first, then FIFO
    }

    @Test
    fun noSlotsOrOfflineMeansNothingStarts() {
        val entries = listOf(q(1))
        assertTrue(QueueScheduler.pickToStart(entries, 3, QueueScheduler.Limits(3, false), online, 0).isEmpty())
        assertTrue(QueueScheduler.pickToStart(entries, 0, QueueScheduler.Limits(3, false), QueueScheduler.Network(false, false), 0).isEmpty())
    }

    @Test
    fun wifiOnlyBlocksMeteredNetworks() {
        val metered = QueueScheduler.Network(online = true, metered = true)
        assertTrue(QueueScheduler.pickToStart(listOf(q(1)), 0, QueueScheduler.Limits(3, wifiOnlyGlobal = true), metered, 0).isEmpty())
        assertTrue(QueueScheduler.pickToStart(listOf(q(1, wifiOnly = true)), 0, QueueScheduler.Limits(3, false), metered, 0).isEmpty())
        assertEquals(1, QueueScheduler.pickToStart(listOf(q(1)), 0, QueueScheduler.Limits(3, false), metered, 0).size)
    }

    @Test
    fun scheduledPausedAndBackoffEntriesWait() {
        val now = 10_000L
        val entries = listOf(
            q(1).copy(scheduledAt = now + 1),
            q(2, state = QueuedDownload.STATE_PAUSED),
            q(3).copy(nextAttemptAt = now + 5),
            q(4)
        )
        assertEquals(listOf(4L), QueueScheduler.pickToStart(entries, 0, QueueScheduler.Limits(5, false), online, now).map { it.id })
    }

    @Test
    fun sameUrlNeverRunsTwice() {
        val entries = listOf(q(1, url = "https://x.test/same"), q(2, url = "https://x.test/same"))
        assertEquals(1, QueueScheduler.pickToStart(entries, 0, QueueScheduler.Limits(5, false), online, 0).size)
        assertTrue(QueueScheduler.pickToStart(entries, 0, QueueScheduler.Limits(5, false), online, 0, activeUrls = setOf("https://x.test/same")).isEmpty())
    }

    @Test
    fun backoffGrowsAndIsCapped() {
        assertEquals(2_000L, QueueScheduler.backoffMs(1))
        assertEquals(4_000L, QueueScheduler.backoffMs(2))
        assertEquals(60_000L, QueueScheduler.backoffMs(10))
        assertTrue(QueueScheduler.canRetry(5))
        assertFalse(QueueScheduler.canRetry(6))
    }

    // ── Checksums ─────────────────────────────────────────────────────────────

    @Test
    fun digestHeaderPrefersStrongestAlgorithm() {
        // md5("") and sha-256("") in base64
        val e = PetalChecksum.fromHeaders("md5=1B2M2Y8AsgTpgAmY7PhCfg==, sha-256=47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=", null)
        assertNotNull(e)
        assertEquals(PetalChecksum.Algo.SHA256, e!!.algo)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", e.hex)
    }

    @Test
    fun contentMd5AndUserHashParse() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", PetalChecksum.fromHeaders(null, "1B2M2Y8AsgTpgAmY7PhCfg==")!!.hex)
        val u = PetalChecksum.parseUserHash("SHA-256: E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855")
        assertEquals(PetalChecksum.Algo.SHA256, u!!.algo)
        assertNull(PetalChecksum.parseUserHash("not a hash"))
        assertNull(PetalChecksum.parseUserHash("abcd"))
    }

    @Test
    fun verifyDetectsMatchAndMismatch() {
        val ok = PetalChecksum.Expected(PetalChecksum.Algo.MD5, "d41d8cd98f00b204e9800998ecf8427e")
        assertTrue(PetalChecksum.verify("".byteInputStream(), ok) is PetalChecksum.Result.Verified)
        assertTrue(PetalChecksum.verify("x".byteInputStream(), ok) is PetalChecksum.Result.Mismatch)
    }

    // ── Smart routing paths ───────────────────────────────────────────────────

    @Test
    fun relativePathUsesCategoryFolderOnlyWhenSmartRoutingIsOn() {
        assertEquals("Download/", PetalDownloadStorage.relativePathFor(false, "a.mp4", "video/mp4"))
        val smart = PetalDownloadStorage.relativePathFor(true, "a.mp4", "video/mp4")
        assertTrue(smart, smart.startsWith("Download/Petal/") && smart.endsWith("/"))
        assertEquals(smart, PetalDownloadStorage.relativePathFor(true, "b.mkv", "video/x-matroska"))
    }

    // ── Post-download pipeline ────────────────────────────────────────────────

    @Test
    fun zipEntryNamesCannotEscapeTheTargetFolder() {
        assertNull(PetalPostDownload.safeEntryPath("../../etc/passwd"))
        assertNull(PetalPostDownload.safeEntryPath("a/../../b"))
        assertNull(PetalPostDownload.safeEntryPath("C:/windows/x"))
        assertEquals("dir/file.txt", PetalPostDownload.safeEntryPath("/dir//file.txt"))
        assertEquals("a/b.txt", PetalPostDownload.safeEntryPath("a\\b.txt"))
    }

    @Test
    fun rulesRoundTripAndCombine() {
        val rules = listOf(
            PetalPostDownload.Rule("ANY", listOf(PetalPostDownload.Action.MEDIA_SCAN)),
            PetalPostDownload.Rule(PetalSmartDownloadRouter.Category.values().first().name, listOf(PetalPostDownload.Action.UNZIP, PetalPostDownload.Action.MEDIA_SCAN))
        )
        val parsed = PetalPostDownload.parseRules(PetalPostDownload.rulesToJson(rules))
        assertEquals(rules, parsed)
        val acts = PetalPostDownload.actionsFor(parsed, PetalSmartDownloadRouter.Category.values().first())
        assertEquals(listOf(PetalPostDownload.Action.MEDIA_SCAN, PetalPostDownload.Action.UNZIP), acts)
        assertEquals(PetalPostDownload.defaultRules(), PetalPostDownload.parseRules("{broken"))
    }

    // ── Clipboard URL extraction / dedupe ─────────────────────────────────────

    @Test
    fun extractsHttpAndMagnetLinksAndDedupes() {
        val text = """
            Grab these: https://example.com/a.zip, and (https://example.com/b.mp4).
            again https://EXAMPLE.com/a.zip/ and https://example.com/a.zip#frag
            magnet:?xt=urn:btih:ABCDEF1234567890ABCDEF1234567890ABCDEF12&dn=My+File
            same magnet magnet:?xt=urn:btih:abcdef1234567890abcdef1234567890abcdef12
            ftp://nope.example.com/x javascript:alert(1)
        """.trimIndent()
        val links = PetalLinkImport.extract(text)
        assertEquals(3, links.size)
        assertEquals("https://example.com/a.zip", links[0].url)
        assertEquals(PetalLinkImport.Kind.FILE, links[0].kind)
        assertEquals(PetalLinkImport.Kind.MEDIA, links[1].kind)
        assertEquals(PetalLinkImport.Kind.MAGNET, links[2].kind)
        assertEquals("My File", links[2].suggestedName)
    }

    @Test
    fun filterAndEmptyInput() {
        val links = PetalLinkImport.extract("https://a.test/x.zip https://a.test/y.mp3 magnet:?xt=urn:btih:ABCDEF1234567890ABCDEF1234567890ABCDEF12")
        assertEquals(1, PetalLinkImport.filter(links, PetalLinkImport.Filter.FILES).size)
        assertEquals(1, PetalLinkImport.filter(links, PetalLinkImport.Filter.MEDIA).size)
        assertEquals(1, PetalLinkImport.filter(links, PetalLinkImport.Filter.MAGNET).size)
        assertEquals(3, PetalLinkImport.filter(links, PetalLinkImport.Filter.ALL).size)
        assertTrue(PetalLinkImport.extract(null).isEmpty())
        assertTrue(PetalLinkImport.extract("no links here").isEmpty())
    }

    // ── Single-stream bandwidth throttling ────────────────────────────────────

    @Test
    fun singleStreamFetchAppliesBandwidthLimitWhenEnabled() {
        var fakeNow = 1_000_000_000L
        val sleptMs = java.util.concurrent.atomic.AtomicLong(0L)
        PetalBandwidthLimiter.nanoClock = { fakeNow }
        PetalBandwidthLimiter.sleeper = { ms ->
            sleptMs.addAndGet(ms)
            fakeNow += ms * 1_000_000L
        }
        // 192 KB payload at 64 KB/s: initial 1s burst = 64 KB, remaining 128 KB requires ~2000ms simulated wait.
        val payload = ByteArray(192 * 1024) { 0x5A }
        val delegate = object : Client() {
            override fun fetch(request: Request): Response {
                return Response(
                    url = request.url,
                    status = 200,
                    headers = Headers.EMPTY,
                    body = Response.Body(payload.inputStream(), "application/octet-stream")
                )
            }
        }
        val client = PetalParallelFetchClient(delegate) {
            PetalParallelFetchClient.Settings(enabled = false, connections = 1, speedLimitKBps = 64)
        }
        val response = client.fetch(Request(url = "https://example.com/single.bin"))
        val readBytes = response.body.useStream { it.readBytes() }
        assertEquals(payload.size, readBytes.size)
        assertEquals(64 * 1024L, PetalBandwidthLimiter.limitBytesPerSecond())
        assertTrue("Expected single-stream transfer to throttle, sleptMs=${sleptMs.get()}", sleptMs.get() >= 1000L)
    }

    @Test
    fun parallelProbeReturning200AppliesBandwidthLimit() {
        var fakeNow = 1_000_000_000L
        val sleptMs = java.util.concurrent.atomic.AtomicLong(0L)
        PetalBandwidthLimiter.nanoClock = { fakeNow }
        PetalBandwidthLimiter.sleeper = { ms ->
            sleptMs.addAndGet(ms)
            fakeNow += ms * 1_000_000L
        }
        val payload = ByteArray(192 * 1024) { 0x2A }
        val delegate = object : Client() {
            override fun fetch(request: Request): Response {
                return Response(
                    url = request.url,
                    status = 200,
                    headers = Headers.EMPTY,
                    body = Response.Body(payload.inputStream(), "application/octet-stream")
                )
            }
        }
        val client = PetalParallelFetchClient(delegate) {
            PetalParallelFetchClient.Settings(enabled = true, connections = 4, speedLimitKBps = 64)
        }
        val response = client.fetch(Request(url = "https://example.com/norange.bin"))
        val readBytes = response.body.useStream { it.readBytes() }
        assertEquals(payload.size, readBytes.size)
        assertEquals(64 * 1024L, PetalBandwidthLimiter.limitBytesPerSecond())
        assertTrue("Expected 200 probe fallback to throttle, sleptMs=${sleptMs.get()}", sleptMs.get() >= 1000L)
    }
}
