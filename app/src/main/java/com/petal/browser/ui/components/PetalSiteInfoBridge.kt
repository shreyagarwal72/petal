package com.petal.browser.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.view.NinjaWebView

object PetalSiteInfoBridge {

    @JvmStatic
    fun showSiteInfoBottomSheet(
        activity: ComponentActivity,
        webView: NinjaWebView?,
        onResetSiteData: Runnable
    ) {
        activity.runOnUiThread {
            try {
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                        val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                        val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                        val isAmoled = sp.getBoolean("sp_amoled", false)

                        val appFont = AppFont.fromName(fontName)
                        val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }

                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId
                        ) {
                            var showSheet by remember { mutableStateOf(true) }
                            if (showSheet) {
                                PetalSiteInfoBottomSheet(
                                    webView = webView,
                                    onDismissRequest = {
                                        showSheet = false
                                        val parentView = composeView?.parent as? android.view.ViewGroup
                                        parentView?.removeView(composeView)
                                    },
                                    onResetSiteData = {
                                        onResetSiteData.run()
                                        showSheet = false
                                        val parentView = composeView?.parent as? android.view.ViewGroup
                                        parentView?.removeView(composeView)
                                    }
                                )
                            }
                        }
                    }
                }

                val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                if (rootView != null) {
                    rootView.addView(
                        composeView,
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
