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
        
        // 1. Standalone Google Lens app
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = android.content.ComponentName("com.google.ar.lens", "com.google.vr.lens.LensCaptureActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}

        // 2. Google App Lens Search Action
        try {
            val gIntent = Intent("com.google.android.gms.actions.SEARCH_ACTION").apply {
                setPackage("com.google.android.googlequicksearchbox")
                putExtra("query", "google lens")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(gIntent)
            return
        } catch (_: Exception) {}

        // 3. Fallback to Google Lens web URL inside the browser / active activity
        try {
            if (context is com.petal.browser.activity.BrowserActivity) {
                context.addAlbum("Google Lens", "https://lens.google.com", true)
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        } catch (_: Exception) {}
    }

    /**
     * Launches Google Lens search for a given image URI via Google Search / Lens app intent.
     * Grants explicit read URI permissions to target packages and resolves the best available viewer.
     */
    @JvmStatic
    fun launchLensForImageUri(context: Context, imageUri: Uri) {
        PetalHapticEngine.getInstance(context).playClick(context)

        val targetPackages = listOf(
            "com.google.ar.lens",
            "com.google.android.googlequicksearchbox"
        )

        for (pkg in targetPackages) {
            try {
                context.grantUriPermission(
                    pkg,
                    imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        var launched = false

        // 1. Try Google Lens direct attachment intent via Google App
        try {
            val intent = Intent("lens.intent.action.LENS_ATTACHMENT").apply {
                setPackage("com.google.android.googlequicksearchbox")
                setDataAndType(imageUri, "image/*")
                clipData = android.content.ClipData.newRawUri("Lens Image", imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolves = context.packageManager.queryIntentActivities(intent, 0)
            if (resolves.isNotEmpty()) {
                context.startActivity(intent)
                launched = true
                return
            }
        } catch (e: Exception) {
            android.util.Log.w("PetalLens", "Direct lens attachment failed", e)
        }

        // 2. Try Google Lens standalone app via ACTION_SEND
        if (!launched) {
            try {
                val standaloneIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    clipData = android.content.ClipData.newRawUri("Lens Image", imageUri)
                    setPackage("com.google.ar.lens")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val resolves = context.packageManager.queryIntentActivities(standaloneIntent, 0)
                if (resolves.isNotEmpty()) {
                    context.startActivity(standaloneIntent)
                    launched = true
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("PetalLens", "Standalone lens send failed", e)
            }
        }

        // 3. Try Google QuickSearchBox ACTION_SEND
        if (!launched) {
            try {
                val gsbIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    clipData = android.content.ClipData.newRawUri("Lens Image", imageUri)
                    setPackage("com.google.android.googlequicksearchbox")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val resolves = context.packageManager.queryIntentActivities(gsbIntent, 0)
                if (resolves.isNotEmpty()) {
                    context.startActivity(gsbIntent)
                    launched = true
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("PetalLens", "QuickSearchBox send failed", e)
            }
        }

        // 4. Try generic image chooser across installed photo/lens search handlers
        if (!launched) {
            try {
                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    clipData = android.content.ClipData.newRawUri("Lens Image", imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(chooserIntent, "Search image with Google Lens").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                }
                context.startActivity(chooser)
                launched = true
                return
            } catch (e: Exception) {
                android.util.Log.w("PetalLens", "Chooser send failed", e)
            }
        }

        // 5. Fallback to Google Lens web URL
        launchGoogleLensApp(context)
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
