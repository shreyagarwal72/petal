package com.petal.browser.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

object PetalMediaPickerBridge {

    private var activeDismissAction: (() -> Unit)? = null

    @JvmStatic
    fun showMediaPicker(
        activity: ComponentActivity,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        onBrowseSystemFallback: () -> Unit
    ) {
        activity.runOnUiThread {
            try {
                activeDismissAction?.invoke()
            } catch (_: Exception) {}
            activeDismissAction = null

            var isHandled = false

            val allowMultiple = fileChooserParams != null && fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
            val acceptTypes = fileChooserParams?.acceptTypes

            val isExplicitNonMedia = acceptTypes != null && acceptTypes.isNotEmpty() && acceptTypes.none {
                val lower = it.lowercase()
                lower.contains("image") || lower.contains("video") || lower.contains("audio") || lower.contains("*/*") || lower.isEmpty()
            }
            if (isExplicitNonMedia) {
                onBrowseSystemFallback()
                return@runOnUiThread
            }

            val decorView = activity.window?.decorView as? android.view.ViewGroup ?: return@runOnUiThread
            lateinit var composeView: ComposeView

            val dismissAction: () -> Unit = {
                if (activeDismissAction != null) {
                    activeDismissAction = null
                    if (composeView.parent != null) {
                        decorView.removeView(composeView)
                    }
                    composeView.clearFocus()
                    if (!isHandled) {
                        filePathCallback?.onReceiveValue(null)
                    }
                }
            }
            activeDismissAction = dismissAction

            val composeViewInstance = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)

                setContent {
                    val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                    var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                    var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                    var paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                    var dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                    var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                    var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                    var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

                    DisposableEffect(sp) {
                        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            when (key) {
                                "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                                "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                                "sp_palette_id" -> paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                                "useDynamicColor" -> dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                                "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                                "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                                "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                                "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                            }
                        }
                        sp.registerOnSharedPreferenceChangeListener(listener)
                        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                    }

                    val appFont = remember(fontName) {
                        try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.PETAL }
                    }
                    val colorStyle = remember(styleName) {
                        try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                    }

                    PetalExpressiveTheme(
                        darkTheme = isSystemInDarkTheme(),
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        fontWidth = fontWidthVal,
                        fontWeight = fontWeightVal,
                        fontRoundness = fontRoundnessVal,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
                        val hostSource = activity.findViewById<android.view.View>(com.petal.browser.R.id.main)
                            ?: activity.findViewById(android.R.id.content)

                        androidx.compose.runtime.CompositionLocalProvider(
                            com.petal.browser.ui.glass.LocalPetalGlassSource provides hostSource
                        ) {
                            ModalBottomSheet(
                                onDismissRequest = dismissAction,
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                shape = BottomSheetDefaults.ExpandedShape,
                                containerColor = if (isGlass) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                                dragHandle = { BottomSheetDefaults.DragHandle() }
                            ) {
                                com.petal.browser.ui.glass.PetalGlassContainer(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = isGlass,
                                    live = true
                                ) {
                                    PetalMediaPickerBottomSheet(
                                        allowMultiple = allowMultiple,
                                        acceptTypes = acceptTypes,
                                        onMediaSelected = { uris ->
                                            isHandled = true
                                            filePathCallback?.onReceiveValue(uris.toTypedArray())
                                            dismissAction()
                                        },
                                        onDismissRequest = dismissAction,
                                        onBrowseSystemFiles = {
                                            isHandled = true
                                            dismissAction()
                                            onBrowseSystemFallback()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            composeView = composeViewInstance

            decorView.addView(
                composeView,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }
}
