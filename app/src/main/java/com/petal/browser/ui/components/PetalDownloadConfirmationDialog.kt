package com.petal.browser.ui.components

import android.content.Context
import android.os.Environment
import android.webkit.URLUtil
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.theme.PetalExpressiveTheme
import java.io.File
import java.util.Locale

/**
 * NOTE: This is intentionally a *plain* composable (Surface/Column), not a
 * Compose `AlertDialog`/`Dialog`. This content is hosted inside a real
 * platform `AlertDialog` (see [PetalDownloadDialogBridge.showDownloadConfirmation]).
 * Compose's `AlertDialog` internally opens its own separate Android window,
 * so nesting it inside another platform dialog created two stacked windows:
 * tapping "Download"/"Cancel" only dismissed the outer (invisible) wrapper
 * while the actual visible inner window stayed on screen, appearing frozen.
 * Keeping this as flat content that shares the single outer dialog's window
 * ensures dismiss() from the bridge actually closes what the user sees.
 */
@Composable
fun PetalDownloadConfirmationDialog(
    fileName: String,
    fileSizeFormatted: String,
    isDuplicate: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onExternalDownload: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isDuplicate) "Download File Again?" else "Download File?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            val annotatedText = buildAnnotatedString {
                append("Do you want to download ")
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(fileName)
                }
                if (fileSizeFormatted.isNotEmpty()) {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" ($fileSizeFormatted)")
                    }
                }
                append("?")
            }
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                if (onExternalDownload != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onExternalDownload,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "External App",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isDuplicate) "Download Again" else "Download",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun PetalFirstTimeDownloadEngineDialog(
    initialEngineKey: String,
    onConfirm: (com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKey by remember { mutableStateOf(initialEngineKey) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Choose Default Download Engine",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select your preferred high-speed engine for downloads, torrents, and magnet links. You can also customize this anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val engineModes = com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.values()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                engineModes.forEach { mode ->
                    val isSelected = mode.key.equals(selectedKey, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedKey = mode.key
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (mode) {
                                            com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM -> Icons.Rounded.Speed
                                            com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_EMBEDDED -> Icons.Rounded.Download
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedKey = mode.key }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        val chosenMode = engineModes.firstOrNull { it.key.equals(selectedKey, ignoreCase = true) }
                            ?: com.petal.browser.torrent.PetalTorrentEngineManager.TorrentEngineMode.ENGINE_1DM
                        onConfirm(chosenMode)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Set as Default",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

object PetalDownloadDialogBridge {

    @JvmStatic
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    @JvmStatic
    fun isFileExistsInDownloads(fileName: String): Boolean {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            file.exists()
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun showFirstTimeEngineSelection(
        activity: androidx.activity.ComponentActivity,
        onComplete: () -> Unit
    ) {
        activity.runOnUiThread {
            try {
                lateinit var engineDialog: androidx.appcompat.app.AlertDialog

                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = com.petal.browser.ui.theme.AppFont.fromName(fontName)
                val colorStyle = try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                val initialEngineKey = com.petal.browser.torrent.PetalTorrentEngineManager.getSelectedEngineMode(activity).key

                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId
                        ) {
                            PetalFirstTimeDownloadEngineDialog(
                                initialEngineKey = initialEngineKey,
                                onConfirm = { chosenMode ->
                                    com.petal.browser.torrent.PetalTorrentEngineManager.setEngineMode(activity, chosenMode)
                                    if (engineDialog.isShowing) engineDialog.dismiss()
                                    onComplete()
                                },
                                onDismiss = {
                                    com.petal.browser.torrent.PetalTorrentEngineManager.setEnginePromptCompleted(activity)
                                    if (engineDialog.isShowing) engineDialog.dismiss()
                                    onComplete()
                                }
                            )
                        }
                    }
                }

                engineDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setView(composeView)
                    .setCancelable(true)
                    .setOnCancelListener {
                        com.petal.browser.torrent.PetalTorrentEngineManager.setEnginePromptCompleted(activity)
                        onComplete()
                    }
                    .create()

                engineDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                engineDialog.show()
                com.petal.browser.unit.HelperUnit.setupDialog(activity, engineDialog)
            } catch (e: Exception) {
                e.printStackTrace()
                com.petal.browser.torrent.PetalTorrentEngineManager.setEnginePromptCompleted(activity)
                onComplete()
            }
        }
    }

    @JvmStatic
    fun showDownloadConfirmation(
        context: Context,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        onConfirmDownload: (String) -> Unit
    ) {
        val guessedFileName = com.petal.browser.unit.HelperUnit.resolveFileName(url, contentDisposition, mimeType)
        val formattedSize = formatFileSize(contentLength)
        val isDuplicate = isFileExistsInDownloads(guessedFileName)

        var currContext = context
        while (currContext is android.content.ContextWrapper) {
            if (currContext is androidx.activity.ComponentActivity) break
            currContext = currContext.baseContext
        }
        val activity = currContext as? androidx.activity.ComponentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onConfirmDownload(guessedFileName)
            return
        }

        val showConfirmationDialogAction = {
            activity.runOnUiThread {
                try {
                    lateinit var dialog: androidx.appcompat.app.AlertDialog

                    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                    val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    val appFont = com.petal.browser.ui.theme.AppFont.fromName(fontName)
                    val colorStyle = try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }

                    val composeView = ComposeView(activity).apply {
                        setViewTreeLifecycleOwner(activity)
                        setViewTreeViewModelStoreOwner(activity)
                        setViewTreeSavedStateRegistryOwner(activity)
                        setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            PetalExpressiveTheme(
                                dynamicColor = dynamicColor,
                                useAmoled = isAmoled,
                                appFont = appFont,
                                colorStyle = colorStyle,
                                paletteId = paletteId
                            ) {
                                PetalDownloadConfirmationDialog(
                                    fileName = guessedFileName,
                                    fileSizeFormatted = formattedSize,
                                    isDuplicate = isDuplicate,
                                    onExternalDownload = {
                                        if (dialog.isShowing) dialog.dismiss()
                                        com.petal.browser.unit.ExternalDownloadManagerHelper.launchDownloadInExternalManager(
                                            activity = activity,
                                            url = url,
                                            fileName = guessedFileName,
                                            mimeType = mimeType
                                        )
                                    },
                                    onConfirm = {
                                        if (dialog.isShowing) dialog.dismiss()
                                        if (!com.petal.browser.torrent.PetalTorrentEngineManager.handleTorrentOrMagnet(activity, url, guessedFileName, mimeType)) {
                                            onConfirmDownload(guessedFileName)
                                        }
                                    },
                                    onDismiss = {
                                        if (dialog.isShowing) dialog.dismiss()
                                    }
                                )
                            }
                        }
                    }

                    dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                        .setView(composeView)
                        .setCancelable(true)
                        .create()

                    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    dialog.show()
                    com.petal.browser.unit.HelperUnit.setupDialog(activity, dialog)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to direct download if dialog creation fails
                    onConfirmDownload(guessedFileName)
                }
            }
        }

        showConfirmationDialogAction()
    }

    /**
     * Same confirmation UI as [showDownloadConfirmation], for downloads where the bytes have
     * already been fetched (blob: URLs) and the filename/size are already known - so no
     * URL/mimeType guessing is done here, and [onConfirmDownload] takes no filename argument.
     */
    @JvmStatic
    fun showBlobDownloadConfirmation(
        context: Context,
        fileName: String,
        byteSize: Long,
        onConfirmDownload: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val formattedSize = formatFileSize(byteSize)
        val isDuplicate = isFileExistsInDownloads(fileName)

        var currContext = context
        while (currContext is android.content.ContextWrapper) {
            if (currContext is androidx.activity.ComponentActivity) break
            currContext = currContext.baseContext
        }
        val activity = currContext as? androidx.activity.ComponentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onConfirmDownload()
            return
        }

        activity.runOnUiThread {
            try {
                lateinit var dialog: androidx.appcompat.app.AlertDialog

                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = com.petal.browser.ui.theme.AppFont.fromName(fontName)
                val colorStyle = try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }

                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId
                        ) {
                            PetalDownloadConfirmationDialog(
                                fileName = fileName,
                                fileSizeFormatted = formattedSize,
                                isDuplicate = isDuplicate,
                                onConfirm = {
                                    if (dialog.isShowing) dialog.dismiss()
                                    onConfirmDownload()
                                },
                                onDismiss = {
                                    if (dialog.isShowing) dialog.dismiss()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                    .setView(composeView)
                    .setCancelable(true)
                    .create()

                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                dialog.show()
                com.petal.browser.unit.HelperUnit.setupDialog(activity, dialog)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to direct save if dialog creation fails
                onConfirmDownload()
            }
        }
    }
}
