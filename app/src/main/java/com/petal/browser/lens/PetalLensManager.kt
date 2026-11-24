package com.petal.browser.lens

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Petal Lens Manager
 * Provides file handling for camera capture and sharable image caching.
 */
object PetalLensManager {

    /**
     * Prepares an image URI so that external apps or sub-components
     * have guaranteed read access via our FileProvider.
     */
    @JvmStatic
    fun prepareSharableImageUri(context: Context, sourceUri: Uri): Uri {
        return try {
            val storageDir = File(context.cacheDir, "lens_photos").apply { mkdirs() }
            val destFile = File(storageDir, "lens_search_${System.currentTimeMillis()}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        } catch (_: Exception) {
            sourceUri
        }
    }

    /**
     * Creates a temporary photo file for camera capture via FileProvider.
     */
    @JvmStatic
    fun createTempCameraUri(context: Context): Uri? {
        return try {
            val timeStamp = System.currentTimeMillis()
            val storageDir = File(context.cacheDir, "lens_photos").apply { mkdirs() }
            val imageFile = File(storageDir, "petal_lens_${timeStamp}.jpg")
            if (imageFile.exists()) {
                imageFile.delete()
            }
            imageFile.createNewFile()
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (_: Exception) {
            null
        }
    }
}
