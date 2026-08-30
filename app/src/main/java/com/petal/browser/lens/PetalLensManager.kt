package com.petal.browser.lens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.petal.browser.haptics.PetalHapticEngine
import java.io.File

/**
 * Petal Lens Manager
 * Full in-app Google Lens flow (Option 3): Photo capture, gallery selection, and Google Lens search launch.
 */
object PetalLensManager {

    /**
     * Directly launches the native Google Lens app or search intent.
     */
    @JvmStatic
    fun launchGoogleLensApp(context: Context) {
        PetalHapticEngine.getInstance(context).playClick(context)
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = android.content.ComponentName("com.google.ar.lens", "com.google.vr.lens.LensCaptureActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val gIntent = Intent("com.google.android.gms.actions.SEARCH_ACTION").apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    putExtra("query", "google lens")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(gIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    /**
     * Launches Google Lens search for a given image URI via Google Search / Lens app intent.
     */
    @JvmStatic
    fun launchLensForImageUri(context: Context, imageUri: Uri) {
        PetalHapticEngine.getInstance(context).playClick(context)
        try {
            val intent = Intent("lens.intent.action.LENS_ATTACHMENT").apply {
                setPackage("com.google.android.googlequicksearchbox")
                setDataAndType(imageUri, "image/*")
                clipData = android.content.ClipData.newRawUri("Lens Image", imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    clipData = android.content.ClipData.newRawUri("Lens Image", imageUri)
                    setPackage("com.google.android.googlequicksearchbox")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(shareIntent)
            } catch (_: Exception) {
                launchGoogleLensApp(context)
            }
        }
    }

    /**
     * Creates a temporary photo file for camera capture via FileProvider.
     */
    @JvmStatic
    fun createTempCameraUri(context: Context): Uri? {
        return try {
            val timeStamp = System.currentTimeMillis()
            val storageDir = context.cacheDir
            val imageFile = File.createTempFile("petal_lens_${timeStamp}", ".jpg", storageDir)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
