/*
 * PetalVideoThumbs.kt
 * Real preview frames for live-video wallpapers.
 *
 * The preset list used to borrow a still photo for every video, so videos showed pictures
 * that were not in them (and the same photo twice). This pulls an actual frame out of the
 * video itself, caches it as a small JPEG, and shares one extraction between callers.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** True when [uri] points at a video (or HLS stream) rather than an image or GIF. */
fun petalIsVideoWallpaper(uri: String): Boolean {
    val lower = uri.lowercase()
    return lower.endsWith(".mp4") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mkv") ||
        lower.endsWith(".mov") ||
        lower.endsWith(".3gp") ||
        lower.endsWith(".ts") ||
        lower.endsWith(".avi") ||
        lower.endsWith(".flv") ||
        lower.endsWith(".m4v") ||
        lower.endsWith(".m3u8") ||
        lower.contains(".m3u8?") ||
        lower.contains(".mp4?") ||
        lower.contains(".webm?") ||
        lower.contains("video-files") ||
        lower.contains("videos.pexels.com") ||
        lower.contains("/video/") ||
        lower.contains("live_wallpaper_") ||
        lower.contains("/raw/wallpaper_") ||
        runCatching {
            val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri)
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            mime?.startsWith("video/") == true
        }.getOrDefault(false)
}

object PetalVideoThumbs {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Deferred<File?>>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    // A few at a time: the sheet can ask for a dozen thumbnails at once on a phone connection.
    private val gate = Semaphore(2)

    private fun cacheFile(context: Context, uri: String): File {
        val dir = File(context.applicationContext.cacheDir, "wallpaper_thumbs").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$name.jpg")
    }

    /** The cached frame if it already exists, without doing any work. */
    fun cached(context: Context, uri: String): File? =
        cacheFile(context, uri).takeIf { it.exists() && it.length() > 0 }

    /** A preview frame for the video at [uri], or null if none could be read. */
    suspend fun get(context: Context, uri: String): File? {
        cached(context, uri)?.let { return it }
        if (uri in failed) return null
        val app = context.applicationContext
        val job = jobs.getOrPut(uri) {
            scope.async { gate.withPermit { extract(app, uri) } }
        }
        return try {
            job.await().also { if (it == null) failed.add(uri) }
        } finally {
            if (job.isCompleted) jobs.remove(uri)
        }
    }

    private fun extract(context: Context, uri: String): File? {
        val out = cacheFile(context, uri)
        val retriever = MediaMetadataRetriever()
        return try {
            when {
                uri.startsWith("http", ignoreCase = true) ->
                    retriever.setDataSource(uri, hashMapOf("User-Agent" to USER_AGENT))
                uri.startsWith("android.resource://") ||
                    uri.startsWith("content://") ||
                    uri.startsWith("file://") -> retriever.setDataSource(context, Uri.parse(uri))
                else -> retriever.setDataSource(uri)
            }
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) * 1000L
            // A little way in, so it is not a black fade-in frame.
            val atUs = if (durationUs > 0) minOf(durationUs / 4, 2_000_000L) else 0L
            val frame = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L) ?: return null
            val scaled = if (frame.width > 540) {
                val h = (frame.height * (540f / frame.width)).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(frame, 540, h, true).also { if (it !== frame) frame.recycle() }
            } else frame
            val tmp = File(out.parentFile, out.name + ".tmp")
            FileOutputStream(tmp).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            scaled.recycle()
            if (tmp.renameTo(out)) out else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
