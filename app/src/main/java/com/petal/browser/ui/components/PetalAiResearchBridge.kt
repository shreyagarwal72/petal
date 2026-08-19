package com.petal.browser.ui.components

import android.app.Dialog
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.compose.ai.ResearchMode
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalAiResearchBridge {

    @JvmStatic
    fun showAiFeature(
        activity: ComponentActivity,
        pageTitle: String,
        pageUrl: String,
        pageContent: String
    ) {
        activity.runOnUiThread {
            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val defaultAction = sp.getString("sp_ai_default_action", "") ?: ""

            when (defaultAction) {
                "SUMMARIZE" -> {
                    showAiResearchSheet(
                        activity = activity,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        pageContent = pageContent,
                        initialMode = ResearchMode.SUMMARY,
                        autoStart = true
                    )
                }
                "ASK_QUESTION" -> {
                    showAiResearchSheet(
                        activity = activity,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        pageContent = pageContent,
                        initialMode = ResearchMode.CUSTOM,
                        autoStart = false
                    )
                }
                else -> {
                    showActionSelectionDialog(
                        activity = activity,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        pageContent = pageContent
                    )
                }
            }
        }
    }

    private fun showActionSelectionDialog(
        activity: ComponentActivity,
        pageTitle: String,
        pageUrl: String,
        pageContent: String
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
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
                        PetalAiActionDialog(
                            pageTitle = pageTitle,
                            onSelectAction = { action, setAsDefault ->
                                if (setAsDefault) {
                                    sp.edit().putString("sp_ai_default_action", action).apply()
                                }
                                isVisible = false
                                dialog.dismiss()

                                val mode = if (action == "SUMMARIZE") ResearchMode.SUMMARY else ResearchMode.CUSTOM
                                val autoStart = action == "SUMMARIZE"

                                showAiResearchSheet(
                                    activity = activity,
                                    pageTitle = pageTitle,
                                    pageUrl = pageUrl,
                                    pageContent = pageContent,
                                    initialMode = mode,
                                    autoStart = autoStart
                                )
                            },
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

    @JvmStatic
    @JvmOverloads
    fun showAiResearchSheet(
        activity: ComponentActivity,
        pageTitle: String,
        pageUrl: String,
        pageContent: String,
        initialMode: ResearchMode = ResearchMode.SUMMARY,
        autoStart: Boolean = true
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
                                initialMode = initialMode,
                                autoStart = autoStart,
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
