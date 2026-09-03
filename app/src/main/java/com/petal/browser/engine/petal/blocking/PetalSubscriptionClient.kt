package com.petal.browser.engine.petal.blocking

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

sealed interface PetalSubscriptionResult {
    data class Preview(val sourceUrl: String, val preview: PetalRulePreview) : PetalSubscriptionResult
    data class Error(val reason: String) : PetalSubscriptionResult
}

object PetalSubscriptionClient {
    fun fetch(sourceUrl: String): PetalSubscriptionResult {
        if (!PetalRuleValidator.isSafeHttpsUrl(sourceUrl)) {
            return PetalSubscriptionResult.Error("https-required")
        }
        val connection = runCatching {
            URI(sourceUrl).toURL().openConnection() as HttpURLConnection
        }.getOrElse { return PetalSubscriptionResult.Error("invalid-url") }
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = PetalSubscriptionRules.CONNECT_TIMEOUT_MS
            connection.readTimeout = PetalSubscriptionRules.READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty("User-Agent", "PetalBrowser-FilterStudio/1")
            val code = connection.responseCode
            if (code !in 200..299) return PetalSubscriptionResult.Error("http-$code")
            val contentLength = connection.contentLengthLong
            if (contentLength > PetalSubscriptionRules.MAX_BYTES) {
                return PetalSubscriptionResult.Error("size-limit")
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1_024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > PetalSubscriptionRules.MAX_BYTES) {
                        return PetalSubscriptionResult.Error("size-limit")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            PetalSubscriptionResult.Preview(
                sourceUrl,
                PetalSubscriptionRules.validatePreview(sourceUrl, body),
            )
        } catch (_: Exception) {
            PetalSubscriptionResult.Error("network")
        } finally {
            connection.disconnect()
        }
    }
}
