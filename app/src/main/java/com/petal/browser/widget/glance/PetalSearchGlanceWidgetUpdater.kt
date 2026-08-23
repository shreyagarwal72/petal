package com.petal.browser.widget.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * `GlanceAppWidget.updateAll()` is a suspend function, so this gives the existing Java
 * call sites (theme/palette pickers in [com.petal.browser.compose.settings.PetalSettingsScreen])
 * a plain static entry point to refresh every placed instance of [PetalSearchGlanceWidget]
 * — e.g. right after the user changes the active color palette or light/dark override.
 */
object PetalSearchGlanceWidgetUpdater {

    @JvmStatic
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        PetalSearchGlanceWidget.clearShapeCache()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PetalSearchGlanceWidget().updateAll(appContext)
            } catch (e: Exception) {
                android.util.Log.e("PetalSearchWidget", "Error updating Glance search widgets", e)
            }
        }
    }
}
