package com.petal.browser.tiles

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager

/**
 * Base Quick Settings TileService for all Petal Browser tiles.
 *
 * Implements:
 * 1. Safe intent launching across API levels (PendingIntent on API 34+, deprecated Intent below 34).
 * 2. Keyguard handling via unlockAndRun when isLocked is true or explicitly requested.
 * 3. Unified tile state updater (state, label, subtitle for API 29+, icon).
 * 4. Automatic SharedPreferences observation and TileService.requestListeningState notification while listening.
 * 5. Strict live state reads during onStartListening without stale caching.
 */
abstract class PetalTileBase : TileService() {

    /**
     * Preference keys to monitor for changes while this tile is listening.
     * When any of these change, [refreshTileState] is called and listening state is requested.
     */
    open val observedPrefKeys: Set<String>
        get() = emptySet()

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (observedPrefKeys.isEmpty() || key in observedPrefKeys)) {
            try {
                requestListeningState(this, ComponentName(this, this::class.java))
            } catch (_: Throwable) {}
            refreshTileState()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        if (observedPrefKeys.isNotEmpty()) {
            try {
                PreferenceManager.getDefaultSharedPreferences(this)
                    .registerOnSharedPreferenceChangeListener(prefChangeListener)
            } catch (_: Throwable) {}
        }
        refreshTileState()
    }

    override fun onStopListening() {
        if (observedPrefKeys.isNotEmpty()) {
            try {
                PreferenceManager.getDefaultSharedPreferences(this)
                    .unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            } catch (_: Throwable) {}
        }
        super.onStopListening()
    }

    /**
     * Re-read live state from preferences/system and update [qsTile].
     * Must never rely on cached state.
     */
    abstract fun refreshTileState()

    /**
     * Updates label, subtitle (API 29+ only), icon and state on [qsTile] in one call.
     */
    fun updateTileState(
        state: Int,
        label: CharSequence? = null,
        subtitle: CharSequence? = null,
        icon: Icon? = null
    ) {
        val tile = qsTile ?: return
        tile.state = state
        if (label != null) {
            tile.label = label
        }
        if (icon != null) {
            tile.icon = icon
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && subtitle != null) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    /**
     * Launch an Activity intent and collapse the Quick Settings shade safely.
     * On API 34+ uses [startActivityAndCollapse] with [PendingIntent].
     * Below API 34 uses the deprecated Intent overload.
     * If [requireUnlock] or [isLocked] is true, wraps the launch in [unlockAndRun].
     */
    fun launchIntent(intent: Intent, requireUnlock: Boolean = false) {
        val launchAction = Runnable {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } catch (_: Throwable) {
                // Fallback attempt with normal startActivity if collapse failed
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Throwable) {}
            }
        }

        if (requireUnlock || isLocked) {
            unlockAndRun(launchAction)
        } else {
            launchAction.run()
        }
    }
}
