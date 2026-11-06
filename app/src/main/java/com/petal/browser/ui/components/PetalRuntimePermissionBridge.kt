package com.petal.browser.ui.components

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.mozilla.geckoview.GeckoSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * PetalRuntimePermissionBridge
 * ─────────────────────────────────────────────────────────────────────────────
 * Bridges GeckoSession.PermissionDelegate.onAndroidPermissionsRequest to the host
 * Android Activity (BrowserActivity, PetalCustomTabActivity, PetalPwaActivity).
 * Tracks pending requests by request code and invokes Gecko callback with real grant/reject results.
 */
object PetalRuntimePermissionBridge {
    private const val BASE_REQUEST_CODE = 8500
    private val counter = AtomicInteger(BASE_REQUEST_CODE)
    private val pendingCallbacks = ConcurrentHashMap<Int, GeckoSession.PermissionDelegate.Callback>()

    @JvmStatic
    fun requestPermissions(
        activity: Activity,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        if (permissions.isNullOrEmpty()) {
            callback.grant()
            return
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missing.isEmpty()) {
            callback.grant()
            return
        }

        val requestCode = counter.getAndIncrement()
        pendingCallbacks[requestCode] = callback
        activity.runOnUiThread {
            ActivityCompat.requestPermissions(activity, missing, requestCode)
        }
    }

    @JvmStatic
    fun handlePermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        val callback = pendingCallbacks.remove(requestCode) ?: return false
        val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
            callback.grant()
        } else {
            callback.reject()
        }
        return true
    }
}
