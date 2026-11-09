/*
 * PetalSendToDeviceBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Bridge utility allowing MainActivity to present PetalSendToDeviceSheet.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.account.mozilla

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalSendToDeviceBridge {

    @JvmStatic
    fun showSendToDevice(
        activity: ComponentActivity,
        url: String,
        title: String,
        onOpenSyncSettings: () -> Unit = {}
    ) {
        val dialog = Dialog(activity)
        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val context = activity
                val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val isAmoled = sp.getBoolean("sp_amoled", false)
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)

                val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalSendToDeviceSheet(
                        url = url,
                        title = title,
                        onDismissRequest = { dialog.dismiss() },
                        onOpenSyncSettings = {
                            dialog.dismiss()
                            onOpenSyncSettings()
                        }
                    )
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}
