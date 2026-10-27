package com.petal.browser.download

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Battery-optimization ("Doze whitelist") exemption for background downloads.
 *
 * A foreground service plus a wake lock keeps a download alive on stock Android, but many
 * vendors (Xiaomi, Oppo, Vivo, Samsung "Sleeping apps", etc.) and Doze itself still throttle
 * or kill apps that are not exempt once the screen is locked. Asking for the exemption is the
 * single most effective fix for "downloads stop when the screen turns off".
 *
 * Play Store note: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is restricted by Google Play policy.
 * This repository is the FOSS/GitHub variant, where it is allowed. When porting to the Play
 * variant, drop the permission from the manifest: [requestExemption] then falls through to the
 * permission-free settings-list intent automatically.
 */
object PetalBatteryOptimization {
    private const val TAG = "PetalBatteryOpt"

    /** Prompt at most once per app process so tab switches don't re-trigger the dialog. */
    @Volatile
    private var promptedThisProcess = false

    /** True when Petal is already exempt (or when the state cannot be determined: never nag blindly). */
    @JvmStatic
    fun isExempt(context: Context): Boolean = try {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.applicationContext.packageName)
    } catch (_: Throwable) {
        true
    }

    /** Whether the Downloads page should show the permission prompt right now. */
    @JvmStatic
    fun shouldPrompt(context: Context): Boolean = !promptedThisProcess && !isExempt(context)

    @JvmStatic
    fun markPrompted() {
        promptedThisProcess = true
    }

    /**
     * Launches the system UI for the exemption. Tries the one-tap system dialog first, then the
     * battery-optimization list, then the app's details page. Returns false only if none opened.
     */
    @JvmStatic
    @SuppressLint("BatteryLife")
    fun requestExemption(context: Context): Boolean {
        val pkg = context.applicationContext.packageName
        val attempts = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        )
        for (intent in attempts) {
            try {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "Could not open ${intent.action}", t)
            }
        }
        return false
    }
}
