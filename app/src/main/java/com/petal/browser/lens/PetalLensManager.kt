package com.petal.browser.lens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.petal.browser.haptics.PetalHapticEngine
import java.io.File
import java.io.FileOutputStream

/**
 * Petal Lens Manager
 * Full in-app Google Lens flow: Photo capture, gallery selection, and Google Lens app launch.
 */
object PetalLensManager {

    private const val TAG = "PetalLens"

    /**
     * Directly launches the native Google Lens app or search intent.
     */
    @JvmStatic
    fun launchGoogleLensApp(context: Context) {
        PetalHapticEngine.getInstance(context).playClick(context)
        
        // 1. Standalone Google Lens app launcher activities
        val lensComponents = listOf(
            ComponentName("com.google.ar.lens", "com.google.vr.apps.ornament.app.lens.LensLauncherActivity"),
            ComponentName("com.google.ar.lens", "com.google.vr.lens.LensCaptureActivity")
        )

        for (comp in lensComponents) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    component = comp
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        // 2. Google App Lens Search Activity / Action
        val googleAppComponents = listOf(
            ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensExportedActivity"),
            ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensActivity")
        )

        for (comp in googleAppComponents) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    component = comp
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

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
     * Prepares an image URI so that external apps like Google Lens / Google QuickSearchBox
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
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to copy image to sharable cache, falling back to sourceUri", e)
            sourceUri
        }
    }

    /**
     * Launches Google Lens search for a given image URI via Google Search / Lens app intent.
     * Grants explicit read URI permissions to target packages and resolves the best available viewer.
     */
    @JvmStatic
    fun launchLensForImageUri(context: Context, imageUri: Uri) {
        PetalHapticEngine.getInstance(context).playClick(context)

        // Ensure URI is hosted from our FileProvider with proper external access
        val sharableUri = prepareSharableImageUri(context, imageUri)

        val targetPackages = listOf(
            "com.google.ar.lens",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.photos"
        )

        for (pkg in targetPackages) {
            try {
                context.grantUriPermission(
                    pkg,
                    sharableUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            } catch (_: Exception) {
                try {
                    context.grantUriPermission(
                        pkg,
                        sharableUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
        }

        // 1. Try Google Lens direct attachment intent via Google App
        try {
            val intent = Intent("lens.intent.action.LENS_ATTACHMENT").apply {
                setPackage("com.google.android.googlequicksearchbox")
                setDataAndType(sharableUri, "image/*")
                clipData = android.content.ClipData.newRawUri("Lens Image", sharableUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (isIntentResolvable(context, intent)) {
                context.startActivity(intent)
                return
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Direct lens attachment intent failed", e)
        }

        // 2. Try Google Lens standalone app via ACTION_SEND
        try {
            val standaloneIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, sharableUri)
                clipData = android.content.ClipData.newRawUri("Lens Image", sharableUri)
                setPackage("com.google.ar.lens")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (isIntentResolvable(context, standaloneIntent)) {
                context.startActivity(standaloneIntent)
                return
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Standalone lens send failed", e)
        }

        // 3. Try Google QuickSearchBox ACTION_SEND
        try {
            val gsbIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, sharableUri)
                clipData = android.content.ClipData.newRawUri("Lens Image", sharableUri)
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (isIntentResolvable(context, gsbIntent)) {
                context.startActivity(gsbIntent)
                return
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "QuickSearchBox send failed", e)
        }

        // 4. Try Google Photos ACTION_SEND
        try {
            val photosIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, sharableUri)
                clipData = android.content.ClipData.newRawUri("Lens Image", sharableUri)
                setPackage("com.google.android.apps.photos")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (isIntentResolvable(context, photosIntent)) {
                context.startActivity(photosIntent)
                return
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Photos send failed", e)
        }

        // 5. Try system image search / share chooser targeting visual search apps
        try {
            val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, sharableUri)
                clipData = android.content.ClipData.newRawUri("Lens Image", sharableUri)
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
            return
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Chooser send failed", e)
        }

        // 6. Direct app launch fallback
        launchGoogleLensApp(context)
    }

    private fun isIntentResolvable(context: Context, intent: Intent): Boolean {
        return try {
            val activities = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            activities.isNotEmpty()
        } catch (_: Exception) {
            false
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
