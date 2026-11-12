package com.petal.browser.widget.glance

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for system wallpaper changes and automatically refreshes all Petal liquid glass widgets.
 *
 * Registered in AndroidManifest.xml for [WallpaperManager.ACTION_WALLPAPER_CHANGED].
 * When fired, clears the cached wallpaper bitmap and PNG tiles so the next widget update
 * re-slices the new wallpaper image.
 */
class PetalWallpaperChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_WALLPAPER_CHANGED) return
        PetalGlassWidgets.onSystemWallpaperChanged(context.applicationContext)
    }
}
