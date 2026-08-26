package com.petal.browser.widget.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * `GlanceAppWidget.updateAll()` is a suspend function, so this gives existing Java
 * call sites (theme/palette pickers in Settings) a static entry point to refresh every placed
 * instance of Petal Glance widgets (Quick Search, Shortcuts & Bookmarks, and Mode Switcher).
 */
object PetalSearchGlanceWidgetUpdater {

    @JvmStatic
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        PetalSearchGlanceWidget.clearShapeCache()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PetalSearchGlanceWidget().updateAll(appContext)
                PetalShortcutsGlanceWidget().updateAll(appContext)
                PetalModeSwitchGlanceWidget().updateAll(appContext)
            } catch (e: Exception) {
                android.util.Log.e("PetalWidgetUpdater", "Error updating Glance search widgets", e)
            }
        }
    }
}
