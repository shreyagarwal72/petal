package com.petal.browser.ui.components

import android.content.Context
import android.os.Environment
import android.webkit.URLUtil
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
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
    onDismiss: () -> Unit
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

                Spacer(modifier = Modifier.width(12.dp))

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
}
