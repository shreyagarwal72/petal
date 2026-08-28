package com.petal.browser.widget

import android.content.Context
import com.petal.browser.widget.glance.PetalSearchGlanceWidgetUpdater

/**
 * Action constants for Petal's home screen search widget.
 *
 * The widget itself is now implemented with Jetpack Glance (Material 3 Expressive
 * components) — see [com.petal.browser.widget.glance.PetalSearchGlanceWidget] and
 * [com.petal.browser.widget.glance.PetalSearchGlanceWidgetReceiver].
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

    /** Refreshes every placed instance of the widget, e.g. after a theme/palette change. */
    @JvmStatic
    fun updateAllWidgets(context: Context) {
        PetalSearchGlanceWidgetUpdater.refresh(context)
    }
}
