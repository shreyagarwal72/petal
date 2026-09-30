/*
 * PetalUploadChooser.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Single entry point for every website <input type="file"> request.
 * It fully replaces the Android system chooser (Camera / Video / Files / More).
 *
 * Routing:
 *  - Site asks only for images/videos (or camera capture) -> Petal photo/video picker, no question asked.
 *  - Site asks only for documents / other files            -> Petal file picker, no question asked.
 *  - Site asks for anything (empty accept, star/star, or a mix of media and documents)
 *                                                          -> small sheet with two choices:
 *                                                             1) Petal file picker  2) Petal photo/video picker
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.file

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.R
import com.petal.browser.media.PetalMediaPickerBridge
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

object PetalUploadChooser {

    private enum class RequestKind { MEDIA, FILES, ANY }

    private var activeDialog: BottomSheetDialog? = null

    private val mediaExtensions = setOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif", ".bmp", ".avif",
        ".mp4", ".mov", ".mkv", ".webm", ".3gp", ".avi", ".m4v"
    )

    /**
     * Called from BrowserActivity.showFileChooser(). [onFatalFallback] runs only if BOTH
     * Petal pickers throw while opening (should never happen); it lets the caller cancel cleanly.
     */
    @JvmStatic
    fun show(
        activity: ComponentActivity,
        filePathCallback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
        onFatalFallback: () -> Unit
    ) {
        activity.runOnUiThread {
            dismissActive()

            val kind = classify(params?.acceptTypes)
            val capture = params?.isCaptureEnabled == true

            when {
                // Camera capture is only offered by the Petal photo/video picker.
                capture && kind != RequestKind.FILES ->
                    openMedia(activity, filePathCallback, params, onFatalFallback)
                kind == RequestKind.MEDIA ->
                    openMedia(activity, filePathCallback, params, onFatalFallback)
                kind == RequestKind.FILES ->
                    openFiles(activity, filePathCallback, params, onFatalFallback)
                else ->
                    showChoiceSheet(activity, filePathCallback, params, onFatalFallback)
            }
        }
    }

    // ── Routing helpers ────────────────────────────────────────────────────

    private fun classify(acceptTypes: Array<String>?): RequestKind {
        val types = acceptTypes
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        if (types.isEmpty() || types.any { it == "*/*" || it == "*" }) return RequestKind.ANY

        val mediaCount = types.count { it.startsWith("image/") || it.startsWith("video/") || it in mediaExtensions }
        return when (mediaCount) {
            types.size -> RequestKind.MEDIA
            0 -> RequestKind.FILES
            else -> RequestKind.ANY
        }
    }

    private fun openMedia(
        activity: ComponentActivity,
        cb: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
        onFatalFallback: () -> Unit
    ) {
        try {
            // The "Files" button inside the media picker opens the Petal file picker.
            PetalMediaPickerBridge.showMediaPicker(activity, cb, params) {
                openFiles(activity, cb, params, onFatalFallback, allowMediaFallback = false)
            }
        } catch (t: Throwable) {
            openFiles(activity, cb, params, onFatalFallback, allowMediaFallback = false)
        }
    }

    private fun openFiles(
        activity: ComponentActivity,
        cb: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
        onFatalFallback: () -> Unit,
        allowMediaFallback: Boolean = true
    ) {
        try {
            // No system-picker button is passed, so the Android chooser can never appear from here.
            PetalFilePickerBridge.handleFileChooser(activity, cb, params, null)
        } catch (t: Throwable) {
            if (allowMediaFallback) {
                try {
                    PetalMediaPickerBridge.showMediaPicker(activity, cb, params) {}
                    return
                } catch (_: Throwable) {
                }
            }
            cb?.onReceiveValue(null)
            onFatalFallback()
        }
    }

    // ── Choice sheet (only for "any file" requests) ────────────────────────

    private fun showChoiceSheet(
        activity: ComponentActivity,
        cb: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
        onFatalFallback: () -> Unit
    ) {
        var chosen = false
        val dialog = BottomSheetDialog(activity)
        activeDialog = dialog

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                val fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                val styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                val paletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                val dynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                val isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                val fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                val fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                val fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

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
                    ChoiceContent(
                        onPickFiles = {
                            chosen = true
                            try { dialog.dismiss() } catch (_: Exception) {}
                            openFiles(activity, cb, params, onFatalFallback)
                        },
                        onPickMedia = {
                            chosen = true
                            try { dialog.dismiss() } catch (_: Exception) {}
                            openMedia(activity, cb, params, onFatalFallback)
                        }
                    )
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.setOnShowListener {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
        dialog.setOnDismissListener {
            if (activeDialog === dialog) activeDialog = null
            // Closed without choosing -> tell the website nothing was picked.
            if (!chosen) cb?.onReceiveValue(null)
        }
        dialog.show()
    }

    private fun dismissActive() {
        try { activeDialog?.dismiss() } catch (_: Exception) {}
        activeDialog = null
    }
}

@androidx.compose.runtime.Composable
private fun ChoiceContent(onPickFiles: () -> Unit, onPickMedia: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.upload_chooser_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))

        ChoiceRow(
            icon = Icons.Rounded.FolderOpen,
            title = stringResource(R.string.upload_chooser_files_title),
            subtitle = stringResource(R.string.upload_chooser_files_desc),
            onClick = onPickFiles
        )
        ChoiceRow(
            icon = Icons.Rounded.PhotoLibrary,
            title = stringResource(R.string.upload_chooser_media_title),
            subtitle = stringResource(R.string.upload_chooser_media_desc),
            onClick = onPickMedia
        )
    }
}

@androidx.compose.runtime.Composable
private fun ChoiceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp).size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
