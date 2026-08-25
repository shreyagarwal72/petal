package com.petal.browser.ui.components

import android.content.Context
import android.os.Environment
import android.webkit.URLUtil
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = if (isDuplicate) "Download file again?" else "Download file?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Single-line styled file card container with dynamic font sizing
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    val displayFontSize = remember(fileName) {
                        when {
                            fileName.length > 40 -> 11.5.sp
                            fileName.length > 28 -> 12.5.sp
                            fileName.length > 20 -> 13.5.sp
                            else -> 14.5.sp
                        }
                    }

                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = displayFontSize,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (fileSizeFormatted.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = fileSizeFormatted,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
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
                        text = if (isDuplicate) "Download again" else "Download",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false
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
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val autoDismissRunnable = Runnable {
                    try {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                        }
                    } catch (_: Exception) {}
                }

                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        PetalExpressiveTheme {
                            PetalDownloadConfirmationDialog(
                                fileName = guessedFileName,
                                fileSizeFormatted = formattedSize,
                                isDuplicate = isDuplicate,
                                onConfirm = {
                                    handler.removeCallbacks(autoDismissRunnable)
                                    if (dialog.isShowing) dialog.dismiss()
                                    onConfirmDownload(guessedFileName)
                                },
                                onDismiss = {
                                    handler.removeCallbacks(autoDismissRunnable)
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

                dialog.setOnDismissListener {
                    handler.removeCallbacks(autoDismissRunnable)
                }

                dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                dialog.show()
                dialog.window?.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                handler.postDelayed(autoDismissRunnable, 3500L)
                com.petal.browser.unit.HelperUnit.setupDialog(activity, dialog)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to direct download if dialog creation fails
                onConfirmDownload(guessedFileName)
            }
        }
    }
}
