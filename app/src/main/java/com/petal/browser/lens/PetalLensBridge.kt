package com.petal.browser.lens

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.preference.PreferenceManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.glass.PetalGlassContainer
import com.petal.browser.ui.glass.isLiquidGlassEnabled
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.PetalBrowserUtils

object PetalLensBridge {

    private var activeDismissAction: (() -> Unit)? = null

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

            val composeView = ComposeView(activity).apply {
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
                        try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.PETAL }
                    }
                    val colorStyle = remember(styleName) {
                        try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                    }

                    val hostSource = activity.findViewById<android.view.View>(com.petal.browser.R.id.main)
                        ?: activity.findViewById(android.R.id.content)
                    CompositionLocalProvider(LocalPetalGlassSource provides hostSource) {
                        PetalExpressiveTheme(
                            darkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            fontWidth = fontWidthVal,
                            fontWeight = fontWeightVal,
                            fontRoundness = fontRoundnessVal,
                            colorStyle = colorStyle,
                            paletteId = paletteId
                        ) {
                            ModalBottomSheet(
                                onDismissRequest = dismissAction,
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                shape = BottomSheetDefaults.ExpandedShape,
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                dragHandle = { BottomSheetDefaults.DragHandle() }
                            ) {
                                PetalGlassContainer(
                                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                    enabled = isLiquidGlassEnabled("sp_liquid_glass_containments"),
                                    live = true
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
     * Opens the Petal Material QR scanner directly (without the Lens sheet),
     * using the same full-screen Dialog and theming as the address bar scanner.
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

                    var showScanner by remember { mutableStateOf(true) }

                    LaunchedEffect(showScanner) {
                        if (!showScanner) {
                            dismissAction()
                        }
                    }

                    PetalExpressiveTheme(
                        darkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        fontWidth = fontWidthVal,
                        fontWeight = fontWeightVal,
                        fontRoundness = fontRoundnessVal,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        if (showScanner) {
                            Dialog(
                                onDismissRequest = { showScanner = false },
                                properties = DialogProperties(
                                    usePlatformDefaultWidth = false,
                                    decorFitsSystemWindows = false
                                )
                            ) {
                                PetalQrScannerScreen(
                                    onResult = { value ->
                                        showScanner = false
                                        if (value.startsWith("http://") || value.startsWith("https://")) {
                                            PetalBrowserUtils.intentURL(activity, Uri.parse(value))
                                        } else {
                                            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                                    as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(
                                                android.content.ClipData.newPlainText("Scanned barcode", value)
                                            )
                                            com.petal.browser.view.PetalToast.show(activity, "Barcode copied")
                                        }
                                    },
                                    onDismiss = { showScanner = false }
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
}

