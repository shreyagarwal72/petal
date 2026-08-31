package com.petal.browser.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

enum class MediaFilterType(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos")
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val isVideo: Boolean,
    val durationMs: Long = 0L
)

object PetalMediaPickerManager {

    /**
     * Returns required runtime permissions based on Android SDK level and requested filter.
     */
    fun getRequiredMediaPermissions(filterType: MediaFilterType = MediaFilterType.ALL): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                when (filterType) {
                    MediaFilterType.PHOTOS -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                    MediaFilterType.VIDEOS -> arrayOf(
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                    MediaFilterType.ALL -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                when (filterType) {
                    MediaFilterType.PHOTOS -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                    MediaFilterType.VIDEOS -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                    MediaFilterType.ALL -> arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                }
            }
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Checks if media read permissions are granted for the specified filter type.
     */
    fun hasMediaPermissions(context: Context, filterType: MediaFilterType = MediaFilterType.ALL): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val fullImages = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val fullVideos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val partial = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
                if (partial) return true
                when (filterType) {
                    MediaFilterType.PHOTOS -> fullImages
                    MediaFilterType.VIDEOS -> fullVideos
                    MediaFilterType.ALL -> fullImages || fullVideos
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                val videos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                when (filterType) {
                    MediaFilterType.PHOTOS -> images
                    MediaFilterType.VIDEOS -> videos
                    MediaFilterType.ALL -> images || videos
                }
            }
            else -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Query MediaStore for photos and/or videos.
     */
    fun queryMedia(context: Context, filterType: MediaFilterType = MediaFilterType.ALL): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        // 1. Query Images
        if (filterType == MediaFilterType.ALL || filterType == MediaFilterType.PHOTOS) {
            val imageUris = mutableListOf<Uri>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageUris.add(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
                imageUris.add(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY))
            }
            imageUris.add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imageUris.add(MediaStore.Images.Media.INTERNAL_CONTENT_URI)

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val seenIds = mutableSetOf<Long>()
            for (targetUri in imageUris.distinct()) {
                try {
                    context.contentResolver.query(
                        targetUri,
                        projection,
                        null,
                        null,
                        sortOrder
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                        val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                        val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                        if (idCol != -1) {
                            while (cursor.moveToNext() && mediaList.size < 300) {
                                val id = cursor.getLong(idCol)
                                if (!seenIds.add(id)) continue

                                val name = if (nameCol != -1) cursor.getString(nameCol) ?: "Image_$id" else "Image_$id"
                                val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                                val date = if (dateCol != -1) cursor.getLong(dateCol) else System.currentTimeMillis() / 1000
                                val contentUri = ContentUris.withAppendedId(targetUri, id)

                                mediaList.add(
                                    MediaItem(
                                        id = id,
                                        uri = contentUri,
                                        displayName = name,
                                        mimeType = mime,
                                        size = size,
                                        dateAdded = date,
                                        isVideo = false
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Continue to next URI
                }
            }
        }

        // 2. Query Videos
        if (filterType == MediaFilterType.ALL || filterType == MediaFilterType.VIDEOS) {
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.DURATION
                )
                val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC LIMIT 150"
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Video_$id"
                        val mime = cursor.getString(mimeCol) ?: "video/mp4"
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol)
                        val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        mediaList.add(
                            MediaItem(
                                id = id,
                                uri = contentUri,
                                displayName = name,
                                mimeType = mime,
                                size = size,
                                dateAdded = date,
                                isVideo = true,
                                durationMs = duration
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Sort descending by date added
        return mediaList.sortedByDescending { it.dateAdded }
    }

    /**
     * Formats duration in milliseconds into a readable MM:SS or HH:MM:SS format.
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val totalSecs = durationMs / 1000
        val seconds = totalSecs % 60
        val minutes = (totalSecs / 60) % 60
        val hours = totalSecs / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Creates a temporary photo file for camera capture.
     */
    fun createTempCaptureUri(context: Context, isVideo: Boolean = false): Uri? {
        return try {
            val timeStamp = System.currentTimeMillis()
            val storageDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
            val suffix = if (isVideo) ".mp4" else ".jpg"
            val prefix = if (isVideo) "capture_vid_" else "capture_img_"
            val file = File.createTempFile("${prefix}${timeStamp}", suffix, storageDir)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
