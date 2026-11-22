package com.petal.browser.widget.liquidglass

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle

/** Compact Petal Search Pill (Liquid Glass) widget provider. Logic lives in [PetalLiquidGlassWidgetBinder]. */
class PetalLiquidGlassPillWidgetReceiver : android.appwidget.AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        PetalWallpaperBlurHelper.rebakeAllFromWallpaper(context.applicationContext)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) PetalLiquidGlassWidgetBinder.update(context, appWidgetManager, id, true)
    }

    /** Resized: the old baked bitmap no longer fits, so the service retakes the capture. */
    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        PetalLiquidGlassWidgetBinder.update(context, appWidgetManager, appWidgetId, true)
        PetalGlassAccessibilityService.onCapturesInvalidated()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) PetalWallpaperBlurHelper.removeWidgetRect(context, id)
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) =
            PetalLiquidGlassWidgetBinder.update(context, appWidgetManager, appWidgetId, true)
    }
}
