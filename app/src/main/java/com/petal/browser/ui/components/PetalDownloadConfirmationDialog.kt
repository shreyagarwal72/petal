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

@Composable
fun PetalDownloadConfirmationDialog(
    fileName: String,
    fileSizeFormatted: String,
    isDuplicate: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isDuplicate) "Download file again?" else "Download file?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
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
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
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
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    )
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
        val guessedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
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
                var dialogView: ComposeView? = null
                dialogView = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        PetalExpressiveTheme {
                            var showDialog by remember { mutableStateOf(true) }
                            if (showDialog) {
                                PetalDownloadConfirmationDialog(
                                    fileName = guessedFileName,
                                    fileSizeFormatted = formattedSize,
                                    isDuplicate = isDuplicate,
                                    onConfirm = {
                                        showDialog = false
                                        val parentView = dialogView?.parent as? android.view.ViewGroup
                                        parentView?.removeView(dialogView)
                                        onConfirmDownload(guessedFileName)
                                    },
                                    onDismiss = {
                                        showDialog = false
                                        val parentView = dialogView?.parent as? android.view.ViewGroup
                                        parentView?.removeView(dialogView)
                                    }
                                )
                            }
                        }
                    }
                }

                activity.addContentView(
                    dialogView,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to direct download if dialog creation fails
                onConfirmDownload(guessedFileName)
            }
        }
    }
}
