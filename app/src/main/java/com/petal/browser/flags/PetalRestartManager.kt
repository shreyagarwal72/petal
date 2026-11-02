package com.petal.browser.flags

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * PetalRestartManager
 * ─────────────────────────────────────────────────────────────────────────
 * Performs a seamless application restart to apply low-level GeckoView
 * engine preferences and user.js flags while preserving active tab sessions.
 */
object PetalRestartManager {

    private const val TAG = "PetalRestartManager"

    @JvmStatic
    fun restartApp(context: Context) {
        val appContext = context.applicationContext
        PetalFlagsStore.acknowledgeRelaunch()

        try {
            Log.i(TAG, "Initiating Petal process restart...")

            // Find launch intent for application
            val pm = appContext.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(appContext.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("extra_flags_restarted", true)
            }

            if (launchIntent != null) {
                appContext.startActivity(launchIntent)
            }

            if (context is Activity && !context.isFinishing) {
                context.finishAffinity()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
                System.exit(0)
            }, 250L)

        } catch (t: Throwable) {
            Log.e(TAG, "Failed graceful restart: ${t.message}", t)
        }
    }
}
