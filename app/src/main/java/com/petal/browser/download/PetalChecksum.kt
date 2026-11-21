package com.petal.browser.download

import android.util.Base64
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Checksum support: read the expected hash from `Digest` / `Content-MD5` response headers or a
 * hash the user pastes, hash the finished file and report the result.
 */
object PetalChecksum {

    enum class Algo(val jca: String, val hexLength: Int) {
        MD5("MD5", 32), SHA1("SHA-1", 40), SHA256("SHA-256", 64), SHA512("SHA-512", 128)
    }

    data class Expected(val algo: Algo, val hex: String)

    sealed class Result {
        object Verified : Result()
        data class Mismatch(val expected: String, val actual: String, val algo: Algo) : Result()
        object Unavailable : Result()
    }

    /**
     * @param digest value of the `Digest` header (RFC 3230), e.g. `sha-256=base64, md5=base64`
     * @param contentMd5 value of `Content-MD5` (RFC 1864). Only meaningful for a complete (200)
     *        response, so callers must pass null for 206 responses.
     */
    @JvmStatic
    fun fromHeaders(digest: String?, contentMd5: String?): Expected? {
        if (!digest.isNullOrBlank()) {
            var best: Expected? = null
            for (part in digest.split(',')) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                val algo = when (part.substring(0, eq).trim().lowercase()) {
                    "sha-256", "sha256" -> Algo.SHA256
                    "sha-512", "sha512" -> Algo.SHA512
                    "sha", "sha-1", "sha1" -> Algo.SHA1
                    "md5" -> Algo.MD5
                    else -> continue
                }
                val hex = base64ToHex(part.substring(eq + 1).trim()) ?: continue
                if (hex.length != algo.hexLength) continue
                // Prefer the strongest algorithm on offer.
                if (best == null || algo.ordinal > best.algo.ordinal) best = Expected(algo, hex)
            }
            if (best != null) return best
        }
        if (!contentMd5.isNullOrBlank()) {
            val hex = base64ToHex(contentMd5.trim())
            if (hex != null && hex.length == Algo.MD5.hexLength) return Expected(Algo.MD5, hex)
        }
        return null
    }

    /** Parses a pasted hash: bare hex (algorithm by length) or `sha256:<hex>` / `SHA-256 <hex>`. */
    @JvmStatic
    fun parseUserHash(text: String?): Expected? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        val m = Regex("""^(?:(md5|sha-?1|sha-?256|sha-?512)\s*[:=\s]\s*)?([0-9a-fA-F]{32,128})(?:\s+\S+)?$""", RegexOption.IGNORE_CASE).find(t)
            ?: return null
        val hex = m.groupValues[2].lowercase()
        val byLength = Algo.values().firstOrNull { it.hexLength == hex.length } ?: return null
        val named = m.groupValues[1].lowercase().replace("-", "")
        val algo = when (named) {
            "md5" -> Algo.MD5
            "sha1" -> Algo.SHA1
            "sha256" -> Algo.SHA256
            "sha512" -> Algo.SHA512
            else -> byLength
        }
        return if (algo.hexLength == hex.length) Expected(algo, hex) else null
    }

    @JvmStatic
    fun compute(input: InputStream, algo: Algo, isCancelled: () -> Boolean = { false }): String? {
        val md = MessageDigest.getInstance(algo.jca)
        val buf = ByteArray(256 * 1024)
        while (true) {
            if (isCancelled()) return null
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    fun verify(input: InputStream, expected: Expected): Result {
        val actual = compute(input, expected.algo) ?: return Result.Unavailable
        return if (actual.equals(expected.hex, ignoreCase = true)) Result.Verified
        else Result.Mismatch(expected.hex, actual, expected.algo)
    }

    private fun base64ToHex(b64: String): String? = try {
        Base64.decode(b64, Base64.DEFAULT).joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) {
        // Plain JVM unit tests have no android.util.Base64; fall back to java.util.
        try {
            java.util.Base64.getDecoder().decode(b64).joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }
}

/** Expected hashes seen in response headers (by URL) and results (by download id). */
object PetalChecksumStore {
    private val expectedByUrl = ConcurrentHashMap<String, PetalChecksum.Expected>()
    private val userExpected = ConcurrentHashMap<String, PetalChecksum.Expected>()
    private val results = ConcurrentHashMap<String, PetalChecksum.Result>()

    fun recordExpected(url: String, expected: PetalChecksum.Expected) {
        if (expectedByUrl.size > 256) expectedByUrl.keys.firstOrNull()?.let { expectedByUrl.remove(it) }
        expectedByUrl[url] = expected
    }

    fun expectedFor(url: String, downloadId: String): PetalChecksum.Expected? =
        userExpected[downloadId] ?: expectedByUrl[url]

    fun setUserExpected(downloadId: String, expected: PetalChecksum.Expected) {
        userExpected[downloadId] = expected
        results.remove(downloadId)
    }

    fun setResult(downloadId: String, result: PetalChecksum.Result) { results[downloadId] = result }
    fun resultFor(downloadId: String): PetalChecksum.Result? = results[downloadId]
    fun forget(downloadId: String) { results.remove(downloadId); userExpected.remove(downloadId) }

    /** Short label for the download row, or null when there is nothing to say. */
    fun label(downloadId: String): String? = when (val r = results[downloadId]) {
        is PetalChecksum.Result.Verified -> "Checksum verified"
        is PetalChecksum.Result.Mismatch -> "Checksum mismatch (${r.algo.jca})"
        else -> null
    }
}
