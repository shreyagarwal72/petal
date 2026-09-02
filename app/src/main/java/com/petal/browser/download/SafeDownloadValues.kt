/*
 * SafeDownloadValues.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Ported from candy-browser's robust SafeDownloadValues & BrowserDownloadRequestFactory.
 * Safely resolves download URLs, handles Content-Disposition (RFC 5987 / UTF-8),
 * sanitizes filenames, detects accurate MIME types, and prevents corrupt downloads.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.download

import android.webkit.MimeTypeMap
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object SafeDownloadValues {
    private val invalidFileNameCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]")
    private val mimeTypePattern = Regex("^[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+*-]+$")
    private const val MAX_FILE_NAME_LENGTH = 120

    fun isHttpUrl(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return runCatching {
            val uri = URI(value.trim())
            (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null
        }.getOrDefault(false)
    }

    fun mimeType(value: String?): String {
        val candidate = value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return candidate.takeIf(mimeTypePattern::matches) ?: "application/octet-stream"
    }

    fun header(value: String?, maxLength: Int = 4_096): String? = value
        ?.takeIf { it.isNotBlank() && it.length <= maxLength && '\r' !in it && '\n' !in it }

    fun referrer(value: String?, targetUrl: String): String? = runCatching {
        val source = URI(value?.trim() ?: return null)
        val target = URI(targetUrl.trim())
        if (!isHttpUrl(source.toString()) || !isHttpUrl(target.toString())) return null
        if (source.scheme.equals("https", ignoreCase = true) &&
            target.scheme.equals("http", ignoreCase = true)
        ) {
            return null
        }
        val sameOrigin = source.scheme.equals(target.scheme, ignoreCase = true) &&
            source.host.equals(target.host, ignoreCase = true) &&
            effectivePort(source) == effectivePort(target)
        val safe = URI(
            source.scheme,
            null,
            source.host,
            source.port.takeUnless { it == defaultPort(source.scheme) } ?: -1,
            source.path.takeIf { sameOrigin },
            source.query.takeIf { sameOrigin },
            null,
        )
        safe.toString()
    }.getOrNull()?.let { header(it) }

    private fun effectivePort(uri: URI): Int =
        uri.port.takeUnless { it == -1 } ?: defaultPort(uri.scheme)

    private fun defaultPort(scheme: String?): Int = when {
        scheme.equals("http", ignoreCase = true) -> 80
        scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }

    /**
     * Resolves the exact safe file name from Content-Disposition (RFC 5987 / UTF-8* or standard),
     * URL path, query params, and MIME type fallback (Candy-Browser algorithm).
     */
    @JvmStatic
    fun fileName(url: String, contentDisposition: String?, mimeType: String?): String {
        val safeMime = mimeType(mimeType)
        val candidate = contentDispositionFileName(contentDisposition)
            ?: urlQueryFileName(url)
            ?: urlFileName(url)
            ?: "download"
        val sanitized = candidate
            .replace(invalidFileNameCharacters, "_")
            .trim()
            .trim('.')
            .ifEmpty { "download" }
        val extension = extensionForMimeType(safeMime)
        val withExtension = if (extension != null && !hasExtension(sanitized)) {
            "$sanitized.$extension"
        } else {
            sanitized
        }
        if (withExtension.length <= MAX_FILE_NAME_LENGTH) return withExtension
        val suffix = withExtension.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() && it.length <= 10 }
            ?.let { ".$it" }
            .orEmpty()
        return withExtension.take(MAX_FILE_NAME_LENGTH - suffix.length).trimEnd() + suffix
    }

    private fun contentDispositionFileName(value: String?): String? {
        if (value.isNullOrBlank()) return null
        // RFC 5987 UTF-8 encoded filename*
        val encoded = Regex("filename\\*\\s*=\\s*UTF-8'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
        if (!encoded.isNullOrBlank()) {
            return runCatching {
                URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            }.getOrNull()
        }
        // Standard filename="..."
        return Regex("filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))", RegexOption.IGNORE_CASE)
            .find(value)
            ?.let { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
            ?.trim()
            ?.trim('"')
    }

    private fun urlFileName(value: String): String? = runCatching {
        URI(value).rawPath
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?.let { URLDecoder.decode(it.replace("+", "%2B"), StandardCharsets.UTF_8.name()) }
    }.getOrNull()

    private fun urlQueryFileName(value: String): String? = runCatching {
        val uri = URI(value)
        val query = uri.rawQuery ?: return null
        val params = query.split("&")
        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].lowercase(Locale.ROOT)
                if (key == "filename" || key == "file" || key == "name") {
                    val name = URLDecoder.decode(parts[1].replace("+", "%2B"), StandardCharsets.UTF_8.name())
                    if (name.isNotBlank() && hasExtension(name)) {
                        return name
                    }
                }
            }
        }
        null
    }.getOrNull()

    fun hasExtension(value: String): Boolean {
        val extension = value.substringAfterLast('.', missingDelimiterValue = "")
        return extension.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9+_-]{0,9}$"))
    }

    private fun extensionForMimeType(mimeType: String): String? = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        "image/svg+xml" -> "svg"
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        "text/html" -> "html"
        "application/json" -> "json"
        "application/zip" -> "zip"
        "application/x-zip-compressed" -> "zip"
        "application/vnd.android.package-archive" -> "apk"
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    }
}
