package com.petal.browser.download

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Keeps the CPU and Wi-Fi radio awake while ANY download is transferring, whichever engine owns it.
 *
 * Why this exists: downloads stalled once the screen locked. The only code that took a
 * WakeLock/WifiLock was PetalDownloadService, which is a Fetch2-era service and is deliberately
 * never started for downloads owned by Mozilla's AbstractFetchDownloadService (the main browser
 * path), and that Mozilla service takes no wake lock of its own. With the screen off the CPU was
 * free to sleep between packets, so those transfers crawled or froze until the screen came back.
 *
 * The locks live in the app process, which both foreground services keep alive, so this does
 * not need its own service or a second notification. It is driven by PetalFetchDownloadBridge,
 * which sees every download regardless of engine.
 *
 * Safety: the wake lock always carries a timeout and is renewed while work is active, so a bug
 * elsewhere can never leave the CPU pinned awake indefinitely.
 */
object PetalDownloadKeepAlive {
    private const val TAG = "PetalDownloadKeepAlive"
    private const val LOCK_TIMEOUT_MS = 10 * 60 * 1000L
    private const val RENEW_AFTER_MS = 4 * 60 * 1000L
    private const val RELEASE_GRACE_MS = 15_000L

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var acquiredAtElapsed = 0L
    private var releaseScheduled = false

    @Volatile
    private var lastActive = 0

    private val releaseRunnable = Runnable { releaseNow() }

    /**
     * Reports how many transfers are currently moving bytes (running or waiting to start).
     * Cheap to call on every progress tick.
     */
    @JvmStatic
    @Synchronized
    fun update(context: Context, activeTransfers: Int) {
        lastActive = activeTransfers
        if (activeTransfers > 0) {
            if (releaseScheduled) {
                handler.removeCallbacks(releaseRunnable)
                releaseScheduled = false
            }
            val now = SystemClock.elapsedRealtime()
            if (wakeLock?.isHeld != true || now - acquiredAtElapsed > RENEW_AFTER_MS) {
                acquire(context.applicationContext, now)
            }
        } else if (!releaseScheduled && (wakeLock?.isHeld == true || wifiLock?.isHeld == true)) {
            // Grace period so a brief gap between two files does not thrash the locks.
            releaseScheduled = true
            handler.postDelayed(releaseRunnable, RELEASE_GRACE_MS)
        }
    }

    private fun acquire(context: Context, now: Long) {
        try {
            if (wakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PetalBrowser:Downloads")
                    ?.apply { setReferenceCounted(false) }
            }
            // Re-acquiring a non-reference-counted lock resets its timeout.
            wakeLock?.acquire(LOCK_TIMEOUT_MS)
            acquiredAtElapsed = now
        } catch (t: Throwable) {
            Log.w(TAG, "Could not acquire wake lock", t)
        }

        try {
            if (wifiLock == null) {
                val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "PetalBrowser:DownloadsWifi")
                    ?.apply { setReferenceCounted(false) }
            }
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
        } catch (t: Throwable) {
            // Best effort: the Wi-Fi lock is a bonus, the wake lock is what matters.
            Log.w(TAG, "Could not acquire wifi lock", t)
        }
    }

    @Synchronized
    private fun releaseNow() {
        releaseScheduled = false
        if (lastActive > 0) return // work resumed while the release was pending
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Throwable) {
        }
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Throwable) {
        }
    }
}
