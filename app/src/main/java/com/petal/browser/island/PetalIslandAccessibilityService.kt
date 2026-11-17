package com.petal.browser.island

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hosts the Petal NowBar in a TYPE_ACCESSIBILITY_OVERLAY window so it can sit inside the status
 * bar and camera-cutout area, above every app, with no "Display over other apps" permission.
 *
 * Privacy: this service declares canRetrieveWindowContent = false. It never reads, stores or
 * sends anything from the screen. [onAccessibilityEvent] is intentionally empty.
 *
 * It is declared only in the non-Play manifest (src/github), so the Google Play build can never
 * ship it. Everything else in the app only talks to it through [connected] / [instance].
 */
class PetalIslandAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty. We only need the service to exist so its overlay window is allowed.
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clear()
        super.onDestroy()
    }

    private fun clear() {
        if (instance === this) instance = null
        _connected.value = false
    }

    companion object {
        @Volatile
        var instance: PetalIslandAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)

        /** True while the system has the service bound and running. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        /** True when the user has switched the service on in system Accessibility settings. */
        fun isEnabledInSystem(context: Context): Boolean {
            val enabled = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (_: Throwable) {
                null
            } ?: return false
            val component = ComponentName(context, PetalIslandAccessibilityService::class.java)
            val long = component.flattenToString()
            val short = component.flattenToShortString()
            return enabled.split(':').any { it.equals(long, true) || it.equals(short, true) }
        }

        /** Opens the system Accessibility settings screen. */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
