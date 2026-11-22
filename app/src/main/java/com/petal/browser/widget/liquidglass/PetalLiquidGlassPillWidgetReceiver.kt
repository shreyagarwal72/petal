package com.petal.browser.widget.liquidglass

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.widget.PetalSearchWidgetProvider

/**
 * Dedicated AppWidgetProvider for the compact Petal Search Pill (Liquid Glass) widget.
 */
class PetalLiquidGlassPillWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            PetalWallpaperBlurHelper.removeWidgetRect(context, id)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.petal_liquid_glass_pill_widget)

            // 1. Background Liquid Glass Layer
            if (PetalWallpaperBlurHelper.isWallpaperOwnsGlass(context)) {
                views.setImageViewBitmap(R.id.widget_glass_bg, null)
            } else {
                val glassBitmap = PetalWallpaperBlurHelper.loadBlurBitmap(context, appWidgetId)
                if (glassBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_glass_bg, glassBitmap)
                } else {
                    PetalWallpaperBlurHelper.rebakeAllFromWallpaper(context.applicationContext)
                }
            }

            // 2. Action Pending Intents
            val searchIntent = actionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH)
            views.setOnClickPendingIntent(R.id.btn_search_bar, searchIntent)
            views.setOnClickPendingIntent(R.id.btn_petal_logo, searchIntent)

            val voiceIntent = actionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE)
            views.setOnClickPendingIntent(R.id.btn_voice_search, voiceIntent)

            val lensIntent = actionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_LENS)
            views.setOnClickPendingIntent(R.id.btn_lens_camera, lensIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun actionIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
