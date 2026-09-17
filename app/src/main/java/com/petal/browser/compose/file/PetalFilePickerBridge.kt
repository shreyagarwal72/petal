/*
 * PetalFilePickerBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Java-friendly and Kotlin bridge for launching Petal's built-in Material 3
 * Expressive File Picker dialog & bottom sheet.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.file

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.*
import java.io.File

object PetalFilePickerBridge {

    private var activeDialog: BottomSheetDialog? = null

    /**
     * Presents the full-featured Petal File Picker as a modal sheet or screen.
     */
    @JvmStatic
    @JvmOverloads
    fun showFilePicker(
        activity: ComponentActivity,
        mimeTypes: Array<String> = emptyArray(),
        allowFolderSelection: Boolean = false,
        allowMultiple: Boolean = false,
        onFileSelected: (File) -> Unit,
        onMultipleFilesSelected: ((List<File>) -> Unit)? = null,
        onDismiss: () -> Unit = {},
        onBrowseSystemFallback: (() -> Unit)? = null
    ) {
        activity.runOnUiThread {
            try {
                activeDialog?.dismiss()
            } catch (_: Exception) {}
            activeDialog = null

            var isHandled = false
            val dialog = BottomSheetDialog(activity)
            activeDialog = dialog

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
                        PetalFilePickerScreen(
                            mimeTypes = mimeTypes,
                            allowFolderSelection = allowFolderSelection,
                            allowMultiple = allowMultiple,
                            onDismissRequest = {
                                try {
                                    if (dialog.isShowing) dialog.dismiss()
                                } catch (_: Exception) {}
                                onDismiss()
                            },
                            onFileSelected = { file ->
                                isHandled = true
                                onFileSelected(file)
                                try {
                                    dialog.dismiss()
                                } catch (_: Exception) {}
                            },
                            onMultipleFilesSelected = { files ->
                                isHandled = true
                                onMultipleFilesSelected?.invoke(files)
                                try {
                                    dialog.dismiss()
                                } catch (_: Exception) {}
                            },
                            onPreviewFile = { file ->
                                val uri = Uri.fromFile(file)
                                val browserAct = activity as? BrowserActivity
                                if (browserAct != null) {
                                    val viewer = PetalFileViewerBridge.createFileViewerView(
                                        browserAct,
                                        uri,
                                        file.name
                                    ) {
                                        browserAct.runOnUiThread { browserAct.performBackNavigation() }
                                    }
                                    browserAct.presentComposeScreen(viewer)
                                }
                            },
                            onBrowseSystemFallback = {
                                isHandled = true
                                try {
                                    dialog.dismiss()
                                } catch (_: Exception) {}
                                onBrowseSystemFallback?.invoke()
                            }
                        )
                    }
                }
            }

            dialog.setContentView(composeView)
            dialog.setOnDismissListener {
                activeDialog = null
                if (!isHandled) {
                    onDismiss()
                }
            }
            dialog.show()
        }
    }

    /**
     * WebChromeClient FileChooser integration bridge.
     */
    @JvmStatic
    fun handleFileChooser(
        activity: ComponentActivity,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        onSystemFallback: () -> Unit
    ) {
        val allowMultiple = fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()

        showFilePicker(
            activity = activity,
            mimeTypes = acceptTypes,
            allowFolderSelection = false,
            allowMultiple = allowMultiple,
            onFileSelected = { file ->
                val uri = Uri.fromFile(file)
                filePathCallback?.onReceiveValue(arrayOf(uri))
            },
            onMultipleFilesSelected = { files ->
                val uris = files.map { Uri.fromFile(it) }.toTypedArray()
                filePathCallback?.onReceiveValue(uris)
            },
            onDismiss = {
                filePathCallback?.onReceiveValue(null)
            },
            onBrowseSystemFallback = {
                onSystemFallback()
            }
        )
    }
}
