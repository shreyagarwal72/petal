package com.petal.browser.widget

import android.content.Context
import com.petal.browser.widget.glance.PetalSearchGlanceWidgetUpdater
import com.petal.browser.widget.glance.PetalWidgetShapes
import com.petal.browser.widget.liquidglass.PetalLiquidGlassWidgetBinder

/**
 * Action constants for Petal's home screen search widget.
 */
object PetalSearchWidgetProvider {

    const val ACTION_OPEN_SEARCH = "com.petal.browser.action.OPEN_SEARCH"
    const val ACTION_OPEN_VOICE = "com.petal.browser.action.OPEN_VOICE"
    const val ACTION_OPEN_AI_SEARCH = "com.petal.browser.action.OPEN_AI_SEARCH"
    const val ACTION_OPEN_INCOGNITO = "com.petal.browser.action.OPEN_INCOGNITO"
    const val ACTION_OPEN_BOOKMARKS = "com.petal.browser.action.OPEN_BOOKMARKS"
    const val ACTION_OPEN_DOWNLOADS = "com.petal.browser.action.OPEN_DOWNLOADS"
    const val ACTION_OPEN_NEW_TAB = "com.petal.browser.action.OPEN_NEW_TAB"
    const val ACTION_OPEN_LENS = "com.petal.browser.action.OPEN_LENS"
    const val ACTION_OPEN_SNAP_CAMERA = "com.petal.browser.action.OPEN_SNAP_CAMERA"
    const val ACTION_OPEN_MENU = "com.petal.browser.action.OPEN_MENU"
    const val ACTION_OPEN_TABS = "com.petal.browser.action.OPEN_TABS"
    const val ACTION_OPEN_HISTORY = "com.petal.browser.action.OPEN_HISTORY"

    /** Refreshes every placed Glance and Liquid Glass widget, e.g. after theme, font, or night-mode changes. */
    @JvmStatic
    fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        PetalWidgetShapes.clear()
        PetalSearchGlanceWidgetUpdater.refresh(appContext)
        try {
            PetalLiquidGlassWidgetBinder.updateAll(appContext)
        } catch (_: Throwable) {}
    }
}
