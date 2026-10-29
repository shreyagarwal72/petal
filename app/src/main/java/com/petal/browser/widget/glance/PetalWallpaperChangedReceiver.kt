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
        // Clear cached bitmap and tiles so the next render picks up the new wallpaper
        PetalGlassWidgetRenderer.forget()
        PetalGlassWidgetRenderer.deleteTiles(context)
        // Re-extract the new wallpaper from the system
        try {
            PetalGlassWidgets.tryExtractSystemWallpaper(context)
        } catch (_: Throwable) {}
        // Trigger widget redraw
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }
}
