package com.petal.browser.widget.liquidglass

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.petal.browser.R
import com.petal.browser.widget.PetalSearchWidgetProvider
import com.petal.browser.widget.PetalWidgetIntentRouter
import java.util.concurrent.ConcurrentHashMap

/**
 * Single place that builds the RemoteViews for both Petal liquid glass widgets
 * (the two receivers used to duplicate this code).
 */
object PetalLiquidGlassWidgetBinder {

    /** The accessibility service finds each widget on screen through this marker. */
    const val MARKER_PREFIX = "petal-lg:"

    private val lastBakeRequest = ConcurrentHashMap<Int, Long>()

    fun clearBakeThrottle() {
        lastBakeRequest.clear()
    }

    fun pillIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PetalLiquidGlassPillWidgetReceiver::class.java)) ?: IntArray(0)

    fun searchIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PetalLiquidGlassSearchWidgetReceiver::class.java)) ?: IntArray(0)

    fun isPill(context: Context, id: Int): Boolean = pillIds(context).contains(id)

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        for (id in searchIds(context)) update(context, mgr, id, false)
        for (id in pillIds(context)) update(context, mgr, id, true)
    }

    /**
     * @param clearForCapture true = show nothing (no glass, hidden foreground) so the launcher
     * can be screenshotted with the real wallpaper visible behind the widget area.
     */
    fun update(
        context: Context,
        mgr: AppWidgetManager,
        id: Int,
        isPill: Boolean,
        clearForCapture: Boolean = false
    ) {
        val layout = if (isPill) R.layout.petal_liquid_glass_pill_widget else R.layout.petal_liquid_glass_search_widget
        val views = RemoteViews(context.packageName, layout)
        val config = PetalBlurConfig.load(context)

        views.setContentDescription(R.id.widget_glass_bg, MARKER_PREFIX + id)
        // The launcher re-applies (not re-inflates) updates, so state set by an earlier update
        // sticks. Always set visibility + image explicitly, otherwise the foreground stayed
        // INVISIBLE after a capture.
        views.setViewVisibility(R.id.widget_glass_content, View.VISIBLE)

        if (clearForCapture) {
            views.setImageViewResource(R.id.widget_glass_bg, android.R.color.transparent)
            views.setViewVisibility(R.id.widget_glass_content, View.INVISIBLE)
            mgr.updateAppWidget(id, views)
            return
        }

        if (PetalWallpaperBlurHelper.isWallpaperOwnsGlass(context)) {
            views.setImageViewResource(R.id.widget_glass_bg, android.R.color.transparent) // live wallpaper draws the glass
        } else {
            val bmp = PetalWallpaperBlurHelper.loadBlurBitmap(context, id)
            if (bmp != null) {
                views.setImageViewBitmap(R.id.widget_glass_bg, bmp)
            } else if (PetalWallpaperBlurHelper.getWidgetRect(context, id) != null) {
                // Known position but nothing baked yet: try once per 30 s (prevents update loops).
                val now = System.currentTimeMillis()
                val last = lastBakeRequest[id] ?: 0L
                if (now - last > 30_000L) {
                    lastBakeRequest[id] = now
                    PetalWallpaperBlurHelper.rebakeAllFromWallpaper(context)
                }
                views.setImageViewResource(R.id.widget_glass_bg, R.drawable.petal_liquid_glass_placeholder_bg)
            } else {
                views.setImageViewResource(R.id.widget_glass_bg, R.drawable.petal_liquid_glass_placeholder_bg)
            }
        }

        val ink = PetalWallpaperBlurHelper.getContentColor(context, id, config)
        val hintText = context.getString(R.string.widget_search_hint)
        val minW = mgr.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val iconsOverheadDp = if (isPill) 130 else 174
        val computedMaxDp = if (minW > 0) (minW - iconsOverheadDp).coerceIn(72, 280) else if (isPill) 140 else 160
        val hintBmp = com.petal.browser.ui.theme.PetalFontHelper.renderTextBitmap(
            context = context,
            text = hintText,
            textSizeSp = if (isPill) 13f else 14f,
            colorInt = ink,
            weight = 500,
            maxWidthDp = computedMaxDp
        )
        if (hintBmp != null) {
            views.setImageViewBitmap(R.id.btn_search_bar_img, hintBmp)
            views.setViewVisibility(R.id.btn_search_bar_img, View.VISIBLE)
            views.setViewVisibility(R.id.btn_search_bar, View.GONE)
        } else {
            views.setTextColor(R.id.btn_search_bar, ink)
            views.setViewVisibility(R.id.btn_search_bar, View.VISIBLE)
            views.setViewVisibility(R.id.btn_search_bar_img, View.GONE)
        }
        views.setInt(R.id.btn_petal_logo, "setColorFilter", ink)
        views.setInt(R.id.btn_voice_search, "setColorFilter", ink)
        views.setInt(R.id.btn_lens_camera, "setColorFilter", ink)

        val searchPending = actionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_SEARCH)
        views.setOnClickPendingIntent(R.id.btn_search_bar, searchPending)
        views.setOnClickPendingIntent(R.id.btn_search_bar_img, searchPending)
        views.setOnClickPendingIntent(R.id.btn_voice_search, actionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_VOICE))
        views.setOnClickPendingIntent(R.id.btn_lens_camera, actionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_LENS))
        if (!isPill) {
            views.setInt(R.id.btn_incognito, "setColorFilter", ink)
            views.setOnClickPendingIntent(R.id.btn_incognito, actionIntent(context, PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO))
        }

        // Logo opens Liquid Glass tuning / setup
        val settings = Intent(context, PetalLiquidGlassSettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        views.setOnClickPendingIntent(
            R.id.btn_petal_logo,
            PendingIntent.getActivity(context, id + 9000, settings, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        mgr.updateAppWidget(id, views)
    }

    private fun actionIntent(context: Context, action: String): PendingIntent =
        PetalWidgetIntentRouter.pendingIntentForAction(context, action)
}
