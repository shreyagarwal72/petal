package com.petal.browser.media.sniffer.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PetalHlsAesDecryptor {

    private val keyCache = ConcurrentHashMap<String, ByteArray>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun isSupported(method: String): Boolean = method == "AES-128"

    fun prefetch(key: HlsKeyInfo, referer: String, cookies: String, userAgent: String, extraHeaders: Map<String, String> = emptyMap()): Boolean =
        isSupported(key.method) && fetchKey(key.keyUri, referer, cookies, userAgent, extraHeaders) != null

    private fun fetchKey(keyUri: String, referer: String, cookies: String, userAgent: String, extraHeaders: Map<String, String>): ByteArray? {
        keyCache[keyUri]?.let { return it }
        val builder = Request.Builder().url(keyUri)
        StreamRequestHeaders.apply(builder, keyUri, referer, cookies, userAgent, extraHeaders)

        return try {
            httpClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.size != 16) return null
                keyCache[keyUri] = bytes
                bytes
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.trim().removePrefix("0x").removePrefix("0X")
        if (clean.length != 32) return null
        return try {
            ByteArray(16) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(
        data: ByteArray,
        key: HlsKeyInfo,
        sequenceNumber: Long,
        referer: String,
        cookies: String,
        userAgent: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): ByteArray? {
        if (!isSupported(key.method)) return null
        val keyBytes = fetchKey(key.keyUri, referer, cookies, userAgent, extraHeaders) ?: return null

        val ivBytes = key.ivHex?.let { hexToBytes(it) } ?: run {
            // Standard HLS spec: If no IV attribute is present, the IV is the 16-byte sequence number as big-endian int
            val iv = ByteArray(16)
            for (i in 0..7) {
                iv[15 - i] = ((sequenceNumber ushr (i * 8)) and 0xFF).toByte()
            }
            iv
        }

        return try {
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            cipher.doFinal(data)
        } catch (_: Exception) {
            null
        }
    }
}
