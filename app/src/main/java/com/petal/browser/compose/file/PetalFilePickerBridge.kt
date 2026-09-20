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
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
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
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.*
import java.io.File

object PetalFilePickerBridge {

    private var activeDialog: Dialog? = null
    private var activeComposeView: ComposeView? = null

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
            // The browser file chooser is a real Petal page, not a BottomSheet.
            // BottomSheetDialog used to intercept vertical drags and left the picker
            // visually anchored over the website. Presenting it through the same
            // full-screen screen host used by Settings/Downloads fixes both issues:
            // scrolling is owned by LazyColumn and the header starts at the normal
            // edge-to-edge/status-bar position used by other Petal pages.
            try {
                activeDialog?.dismiss()
            } catch (_: Exception) {}
            activeDialog = null
            activeComposeView = null

            var isHandled = false

            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )

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
                                if (!isHandled) {
                                    isHandled = true
                                    if (activity is BrowserActivity) {
                                        activity.performBackNavigation()
                                    } else {
                                        try { activeDialog?.dismiss() } catch (_: Exception) {}
                                    }
                                    onDismiss()
                                }
                            },
                            onFileSelected = { file ->
                                isHandled = true
                                onFileSelected(file)
                                if (activity is BrowserActivity) {
                                    activity.performBackNavigation()
                                } else {
                                    try { activeDialog?.dismiss() } catch (_: Exception) {}
                                }
                            },
                            onMultipleFilesSelected = { files ->
                                isHandled = true
                                onMultipleFilesSelected?.invoke(files)
                                if (activity is BrowserActivity) {
                                    activity.performBackNavigation()
                                } else {
                                    try { activeDialog?.dismiss() } catch (_: Exception) {}
                                }
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
                                if (activity is BrowserActivity) {
                                    activity.performBackNavigation()
                                } else {
                                    try { activeDialog?.dismiss() } catch (_: Exception) {}
                                }
                                onBrowseSystemFallback?.invoke()
                            }
                        )
                    }
                }
            }

            activeComposeView = composeView

            val browserActivity = activity as? BrowserActivity
            if (browserActivity != null) {
                // Clear the old browser content before pushing the picker. This is
                // important: the picker is a first-class full-screen Petal page,
                // not a transparent overlay sitting above GeckoView.
                browserActivity.captureBrowserMainPreview()
                browserActivity.clearContentFrameKeepingTabs()
                browserActivity.presentComposeScreen(composeView)
                return@runOnUiThread
            }

            // Non-browser callers still get a full-screen host instead of a
            // draggable BottomSheet. This keeps the component reusable from
            // settings/account screens without the old sheet scroll behaviour.
            val dialog = Dialog(activity)
            activeDialog = dialog
            dialog.setContentView(composeView)
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnDismissListener {
                activeDialog = null
                activeComposeView = null
                if (!isHandled) {
                    isHandled = true
                    onDismiss()
                }
            }
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                    window.setDimAmount(0f)
                    window.setGravity(Gravity.CENTER)
                    window.setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                }
            }
            dialog.show()
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                window.setDimAmount(0f)
                window.setGravity(Gravity.CENTER)
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
            }
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
