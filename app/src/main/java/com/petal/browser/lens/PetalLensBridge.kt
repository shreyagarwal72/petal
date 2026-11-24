package com.petal.browser.lens

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.scanner.PetalScanActionHandler
import com.petal.browser.scanner.ScannerHost
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.PetalBrowserUtils

object PetalLensBridge {

    private var activeDismissAction: (() -> Unit)? = null

    @OptIn(ExperimentalMaterial3Api::class)
    @JvmStatic
    @JvmOverloads
    fun showLensBottomSheet(activity: ComponentActivity, autoSnapCamera: Boolean = false) {
        activity.runOnUiThread {
            try {
                activeDismissAction?.invoke()
            } catch (_: Exception) {}
            activeDismissAction = null

            val decorView = activity.window?.decorView as? android.view.ViewGroup ?: return@runOnUiThread
            lateinit var composeView: ComposeView

            val dismissAction: () -> Unit = {
                if (activeDismissAction != null) {
                    activeDismissAction = null
                    if (composeView.parent != null) {
                        decorView.removeView(composeView)
                    }
                    composeView.clearFocus()
                    (activity as? com.petal.browser.activity.MainActivity)?.restoreBrowserInputFocus()
                }
            }
            activeDismissAction = dismissAction

            composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

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
                        try { AppFont.valueOf(fontName) } catch (_: Exception) { AppFont.PETAL }
                    }
                    val colorStyle = remember(styleName) {
                        try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                    }

                    val hostSource = activity.findViewById<android.view.View>(com.petal.browser.R.id.main)
                        ?: activity.findViewById(android.R.id.content)
                    CompositionLocalProvider(LocalPetalGlassSource provides hostSource) {
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
                            com.petal.browser.ui.containment.PetalSheet(
                                onDismissRequest = dismissAction,
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                dragHandle = { BottomSheetDefaults.DragHandle() }
                            ) {
                                PetalLensBottomSheet(
                                    autoSnapCamera = autoSnapCamera,
                                    onDismissRequest = dismissAction
                                )
                            }
                        }
                    }
                }
            }

            decorView.addView(
                composeView,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    /**
     * Opens the Petal Camera Scanner directly without the Lens sheet.
     * Used by Quick Tools "QR Scanner" button.
     */
    @JvmStatic
    fun showPetalScannerDirectly(activity: ComponentActivity) {
        activity.runOnUiThread {
            val decorView = activity.window?.decorView as? android.view.ViewGroup ?: return@runOnUiThread
            lateinit var composeView: ComposeView

            val dismissAction: () -> Unit = {
                if (composeView.parent != null) {
                    decorView.removeView(composeView)
                }
                composeView.clearFocus()
                (activity as? com.petal.browser.activity.MainActivity)?.restoreBrowserInputFocus()
            }

            composeView = ComposeView(activity).apply {
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

                    val appFont = remember(fontName) {
                        try { AppFont.valueOf(fontName) } catch (_: Exception) { AppFont.PETAL }
                    }
                    val colorStyle = remember(styleName) {
                        try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
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
                        ScannerHost(
                            onDismiss = dismissAction,
                            onResultDispatched = { value ->
                                if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
                                    PetalBrowserUtils.intentURL(activity, Uri.parse(value))
                                } else {
                                    PetalScanActionHandler.copyToClipboard(activity, value)
                                }
                                dismissAction()
                            }
                        )
                    }
                }
            }

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
