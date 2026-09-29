package com.petal.browser.compose.file

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.PetalMediaPickerScreen
import com.petal.browser.media.PetalUploadRoute
import com.petal.browser.media.PetalUploadSpec
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Petal's upload chooser. Replaces the Android system chooser for `<input type=file>`.
 *
 * Two sources:
 *   1. Photos & videos - Petal's built-in full-screen media picker (full library access)
 *   2. Files           - Petal's built-in file picker
 *
 * The right one is auto-selected from the page's `accept` / `capture` request; only genuinely
 * ambiguous requests (any file, or images mixed with documents) show the 2-option sheet.
 */
object PetalFileChooser {

    private const val PREF_LAST_SOURCE = "petal_upload_last_source"
    private var activeSheet: BottomSheetDialog? = null

    @JvmStatic
    fun show(
        activity: ComponentActivity,
        filePathCallback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
        onSystemFallback: () -> Unit,
    ) {
        val spec = PetalUploadSpec(
            accept = params?.acceptTypes,
            allowMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,
            captureRequested = params?.isCaptureEnabled == true,
        )
        // The web callback must be completed exactly once, whichever path finishes the request.
        val done = AtomicBoolean(false)
        val finish: (Array<Uri>?) -> Unit = { uris ->
            if (done.compareAndSet(false, true)) filePathCallback?.onReceiveValue(uris)
        }

        activity.runOnUiThread {
            when (spec.route()) {
                PetalUploadRoute.MEDIA -> openMedia(activity, spec, finish, onSystemFallback)
                PetalUploadRoute.FILES -> openFiles(activity, spec, finish, onSystemFallback, canSwitchToMedia = false)
                PetalUploadRoute.ASK -> showSheet(activity, spec, finish, onSystemFallback)
            }
        }
    }

    // ---- Routes -----------------------------------------------------------------------------

    private fun openMedia(
        activity: ComponentActivity,
        spec: PetalUploadSpec,
        finish: (Array<Uri>?) -> Unit,
        onSystemFallback: () -> Unit,
    ) {
        rememberSource(activity, "media")
        PetalMediaPickerHost.show(
            activity = activity,
            spec = spec,
            onResult = { uris -> finish(uris.toTypedArray()) },
            onDismiss = { finish(null) },
            // Switch to the Petal file picker (same request, other source).
            onBrowseFiles = {
                Handler(Looper.getMainLooper()).post {
                    openFiles(activity, spec, finish, onSystemFallback, canSwitchToMedia = spec.allowsImages || spec.allowsVideos)
                }
            },
        )
    }

    private fun openFiles(
        activity: ComponentActivity,
        spec: PetalUploadSpec,
        finish: (Array<Uri>?) -> Unit,
        onSystemFallback: () -> Unit,
        canSwitchToMedia: Boolean,
    ) {
        rememberSource(activity, "files")
        PetalFilePickerBridge.showFilePicker(
            activity = activity,
            mimeTypes = spec.patterns.toTypedArray(),
            allowFolderSelection = false,
            allowMultiple = spec.allowMultiple,
            onFileSelected = { file -> finish(arrayOf(Uri.fromFile(file))) },
            onMultipleFilesSelected = { files -> finish(files.map { Uri.fromFile(it) }.toTypedArray()) },
            onDismiss = { finish(null) },
            onBrowseSystemFallback = onSystemFallback,
            onSwitchToMedia = if (canSwitchToMedia) {
                {
                    Handler(Looper.getMainLooper()).post {
                        openMedia(activity, spec, finish, onSystemFallback)
                    }
                }
            } else null,
        )
    }

    private fun rememberSource(activity: ComponentActivity, source: String) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putString(PREF_LAST_SOURCE, source).apply()
    }

    // ---- 2-option chooser sheet -------------------------------------------------------------

    private fun showSheet(
        activity: ComponentActivity,
        spec: PetalUploadSpec,
        finish: (Array<Uri>?) -> Unit,
        onSystemFallback: () -> Unit,
    ) {
        try { activeSheet?.dismiss() } catch (_: Exception) {}

        val lastSource = PreferenceManager.getDefaultSharedPreferences(activity).getString(PREF_LAST_SOURCE, null)
        val dialog = BottomSheetDialog(activity)
        activeSheet = dialog
        var chosen = false
        val canMedia = spec.allowsImages || spec.allowsVideos

        val view = petalComposeView(activity) {
            PetalUploadChooserSheet(
                acceptSummary = spec.describe(),
                lastSource = lastSource,
                showMedia = canMedia,
                onMedia = {
                    chosen = true
                    dialog.dismiss()
                    openMedia(activity, spec, finish, onSystemFallback)
                },
                onFiles = {
                    chosen = true
                    dialog.dismiss()
                    openFiles(activity, spec, finish, onSystemFallback, canSwitchToMedia = canMedia)
                },
                onCancel = { dialog.dismiss() },
            )
        }
        dialog.setContentView(view)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.setOnShowListener {
            dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(AndroidColor.TRANSPARENT)
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.setOnDismissListener {
            activeSheet = null
            if (!chosen) finish(null)
        }
        dialog.show()
    }
}

// ---------------------------------------------------------------------------------------------
// Sheet UI (Material 3 Expressive + Petal containment)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PetalUploadChooserSheet(
    acceptSummary: String,
    lastSource: String?,
    showMedia: Boolean,
    onMedia: () -> Unit,
    onFiles: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = remember { PetalHapticEngine.getInstance(context) }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    .align(Alignment.CenterHorizontally),
            )

            // Header card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PetalGroupIconBadge(
                        Icons.Rounded.FileUpload,
                        container = MaterialTheme.colorScheme.primary,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        size = 52.dp,
                        iconSize = 26.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Upload from",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            acceptSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            val rows = if (showMedia) 2 else 1
            PetalGroup(rowCount = rows, modifier = Modifier.padding(horizontal = 16.dp)) { index, position ->
                val isMediaRow = showMedia && index == 0
                if (isMediaRow) {
                    PetalGroupListRow(
                        position = position,
                        selected = lastSource == "media",
                        onClick = { haptics.playClick(context); onMedia() },
                        leading = {
                            PetalGroupIconBadge(
                                Icons.Rounded.PhotoLibrary,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        content = {
                            Text("Photos & videos", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Gallery, albums and camera", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailing = { ChevronOrLast(lastSource == "media") },
                    )
                } else {
                    PetalGroupListRow(
                        position = position,
                        selected = lastSource == "files",
                        onClick = { haptics.playClick(context); onFiles() },
                        leading = {
                            PetalGroupIconBadge(
                                Icons.Rounded.FolderOpen,
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        },
                        content = {
                            Text("Files", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Downloads, folders and storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailing = { ChevronOrLast(lastSource == "files") },
                    )
                }
            }

            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun ChevronOrLast(isLast: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isLast) {
            Text("Last used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Hosting
// ---------------------------------------------------------------------------------------------

/** Builds a ComposeView with the ViewTree owners and the user's Petal theme applied. */
internal fun petalComposeView(activity: ComponentActivity, content: @Composable () -> Unit): ComposeView =
    ComposeView(activity).apply {
        setViewTreeLifecycleOwner(activity)
        setViewTreeViewModelStoreOwner(activity)
        setViewTreeSavedStateRegistryOwner(activity)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
            val fontName = remember { sp.getString("sp_app_font", "PETAL") ?: "PETAL" }
            val styleName = remember { sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT" }
            val paletteId = remember { sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId }
            val dynamicColor = remember { sp.getBoolean("useDynamicColor", isDynamicColorSupported) }
            val isAmoled = remember { sp.getBoolean("sp_amoled", false) }
            val fontWidth = remember { sp.getFloat("sp_font_width", 92f) }
            val fontWeight = remember { sp.getInt("sp_font_weight", 750) }
            val fontRoundness = remember { sp.getFloat("sp_font_roundness", 100f) }

            val appFont = remember(fontName) { try { AppFont.valueOf(fontName) } catch (_: Exception) { AppFont.PETAL } }
            val colorStyle = remember(styleName) { try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT } }

            PetalExpressiveTheme(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = dynamicColor,
                useAmoled = isAmoled,
                appFont = appFont,
                fontWidth = fontWidth,
                fontWeight = fontWeight,
                fontRoundness = fontRoundness,
                colorStyle = colorStyle,
                paletteId = paletteId,
            ) { content() }
        }
    }

/**
 * Presents the media picker as a real full-screen Petal page - the same host path the file picker
 * uses (not an overlay, not a dialog).
 */
object PetalMediaPickerHost {
    @JvmStatic
    fun show(
        activity: ComponentActivity,
        spec: PetalUploadSpec,
        onResult: (List<Uri>) -> Unit,
        onDismiss: () -> Unit,
        onBrowseFiles: (() -> Unit)?,
    ) {
        val browser = activity as? BrowserActivity
        if (browser == null) {
            onDismiss()
            return
        }
        activity.runOnUiThread {
            var handled = false
            fun close(action: () -> Unit) {
                if (handled) return
                handled = true
                browser.performBackNavigation()
                action()
            }

            val view = petalComposeView(activity) {
                PetalMediaPickerScreen(
                    spec = spec,
                    onDismissRequest = { close(onDismiss) },
                    onSelected = { uris -> close { onResult(uris) } },
                    onBrowseFiles = onBrowseFiles?.let { switch -> { close(switch) } },
                )
            }
            browser.captureBrowserMainPreview()
            browser.clearContentFrameKeepingTabs()
            browser.presentComposeScreen(view)
        }
    }
}
