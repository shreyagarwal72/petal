package com.petal.browser.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * Clean no-op Play Update Manager for FOSS and GitHub distribution variants.
 * Zero Play dependencies are included in non-Play builds.
 */
class PetalPlayUpdateManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PetalPlayUpdateNoOp"
        const val UPDATE_REQUEST_CODE = 53012

        @Volatile
        private var instance: PetalPlayUpdateManager? = null

        @JvmStatic
        fun getInstance(context: Context): PetalPlayUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: PetalPlayUpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun registerListener(activity: ComponentActivity) {
        // No-op for non-Play builds
    }

    fun unregisterListener() {
        // No-op for non-Play builds
    }

    fun checkForUpdates(activity: ComponentActivity, isLaunchCheck: Boolean = false) {
        Log.d(TAG, "Play Store in-app updates disabled on non-Play distribution variant")
    }

    fun onResume(activity: ComponentActivity) {
        // No-op for non-Play builds
    }

    fun completeUpdate() {
        // No-op for non-Play builds
    }

    fun openPlayStore(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
