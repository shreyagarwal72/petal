package com.petal.browser.ui.components

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import com.petal.browser.activity.BrowserActivity
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
                    showSummaryBoxDialog(
                        activity = activity,
                        pageTitle = pageTitle,
                        pageUrl = pageUrl,
                        pageContent = pageContent
                    )
                }
                "AI_SEARCH" -> {
                    PetalAiSearchBridge.showAiSearchResult(activity, "")
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
        try {
            var composeView: ComposeView? = null
            composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

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
                                    (composeView?.parent as? ViewGroup)?.removeView(composeView)

                                    if (action == "SUMMARIZE") {
                                        showSummaryBoxDialog(
                                            activity = activity,
                                            pageTitle = pageTitle,
                                            pageUrl = pageUrl,
                                            pageContent = pageContent
                                        )
                                    } else if (action == "AI_SEARCH") {
                                        PetalAiSearchBridge.showAiSearchResult(activity, "")
                                    } else {
                                        showAiResearchSheet(
                                            activity = activity,
                                            pageTitle = pageTitle,
                                            pageUrl = pageUrl,
                                            pageContent = pageContent,
                                            initialMode = ResearchMode.CUSTOM,
                                            autoStart = false
                                        )
                                    }
                                },
                                onDismiss = {
                                    isVisible = false
                                    (composeView?.parent as? ViewGroup)?.removeView(composeView)
                                }
                            )
                        }
                    }
                }
            }

            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            rootView?.addView(
                composeView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            try {
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

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
                                        (composeView?.parent as? ViewGroup)?.removeView(composeView)
                                    }
                                )
                            }
                        }
                    }
                }

                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                rootView?.addView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun showSummaryBoxDialog(
        activity: ComponentActivity,
        pageTitle: String,
        pageUrl: String,
        pageContent: String
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
                                PetalSummaryBoxDialog(
                                    pageTitle = pageTitle,
                                    pageUrl = pageUrl,
                                    pageContent = pageContent,
                                    onAskQuestion = {
                                        isVisible = false
                                        (composeView?.parent as? ViewGroup)?.removeView(composeView)
                                        showAiResearchSheet(
                                            activity = activity,
                                            pageTitle = pageTitle,
                                            pageUrl = pageUrl,
                                            pageContent = pageContent,
                                            initialMode = ResearchMode.CUSTOM,
                                            autoStart = false
                                        )
                                    },
                                    onOpenSettings = {
                                        isVisible = false
                                        (composeView?.parent as? ViewGroup)?.removeView(composeView)
                                        (activity as? BrowserActivity)?.openApiIntegrationsHub()
                                    },
                                    onDismiss = {
                                        isVisible = false
                                        (composeView?.parent as? ViewGroup)?.removeView(composeView)
                                    }
                                )
                            }
                        }
                    }
                }

                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                rootView?.addView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
