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
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PetalSearchPetal1Widget().updateAll(appContext)
                PetalSearchPetal2Widget().updateAll(appContext)
                PetalSearchPetal3Widget().updateAll(appContext)
            } catch (e: Exception) {
                android.util.Log.e("PetalWidgetUpdater", "Error updating Glance search widgets", e)
            }
        }
    }
}
