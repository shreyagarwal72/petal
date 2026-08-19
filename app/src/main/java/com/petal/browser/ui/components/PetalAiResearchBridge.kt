package com.petal.browser.ui.components

import android.app.Dialog
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalAiResearchBridge {

    @JvmStatic
    fun showAiResearchSheet(
        activity: ComponentActivity,
        pageTitle: String,
        pageUrl: String,
        pageContent: String
    ) {
        activity.runOnUiThread {
            val dialog = Dialog(activity, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

                setContent {
                    val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    val appFont = try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.GS_FLEX }
                    val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }

                    val fontWidthVal = sp.getFloat("sp_font_width", 100f)
                    val fontWeightVal = sp.getInt("sp_font_weight", 400)
                    val fontRoundnessVal = sp.getFloat("sp_font_roundness", 0f)

                    var isVisible by remember { mutableStateOf(true) }

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        fontWidth = fontWidthVal,
                        fontWeight = fontWeightVal,
                        fontRoundness = fontRoundnessVal,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        if (isVisible) {
                            PetalAiResearchSheet(
                                pageTitle = pageTitle,
                                pageUrl = pageUrl,
                                pageContent = pageContent,
                                onDismiss = {
                                    isVisible = false
                                    dialog.dismiss()
                                }
                            )
                        }
                    }
                }
            }

            dialog.setContentView(composeView)
            dialog.show()
        }
    }
}
