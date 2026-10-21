/*
 * PetalTorManager.kt
 * ──────────────────────────────────────────────────────────────────────────
 * Orbot / Tor integration for Petal Browser.
 * Routes private tab network traffic via local SOCKS5 proxy (127.0.0.1:9050).
 */
package com.petal.browser.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object PetalTorManager {

    private const val TAG = "PetalTorManager"
    const val ORBOT_PACKAGE = "org.torproject.android"
    const val ORBOT_ACTION_START = "org.torproject.android.intent.action.START"
    const val SOCKS_HOST = "127.0.0.1"
    const val SOCKS_PORT = "9050"

    var isTorActive: Boolean = false
        private set

    fun isOrbotInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ORBOT_PACKAGE, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun startTorSession(context: Context): Boolean {
        if (!isOrbotInstalled(context)) {
            Log.w(TAG, "Orbot is not installed on this device.")
            return false
        }
        return try {
            System.setProperty("socksProxyHost", SOCKS_HOST)
            System.setProperty("socksProxyPort", SOCKS_PORT)
            val intent = Intent(ORBOT_ACTION_START).apply {
                setPackage(ORBOT_PACKAGE)
                putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", context.packageName)
            }
            context.startService(intent)
            isTorActive = true
            Log.i(TAG, "Tor SOCKS5 proxy set to $SOCKS_HOST:$SOCKS_PORT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Tor session: ${e.message}")
            false
        }
    }

    fun stopTorSession() {
        System.clearProperty("socksProxyHost")
        System.clearProperty("socksProxyPort")
        isTorActive = false
        Log.i(TAG, "Tor SOCKS5 proxy unset")
    }

    fun isTorWindowEnabled(context: Context): Boolean {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return sp.getBoolean("sp_tor_private_window", false)
    }
}
