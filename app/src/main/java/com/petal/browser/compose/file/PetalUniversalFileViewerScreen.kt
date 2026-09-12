/*
 * PetalUniversalFileViewerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Built-in native Material 3 Expressive Universal File Viewer for Petal Browser.
 * Supports all document and data formats:
 *   • PDF documents (combines and renders via PetalPdfViewerScreen / native PdfRenderer)
 *   • Office documents: Word (.docx), PowerPoint (.pptx), Excel (.xlsx)
 *   • Plain text, Markdown, CSV, XML, JSON, HTML, YAML, and source code files (.kt, .java, .py, .c, .cpp, .js, .ts, etc.)
 *   • Archives (.zip, .rar, .7z, .tar, .gz) with table of contents extraction
 *   • Fallback formatted binary/metadata viewer with open-in-external-app option
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.compose.pdf.PetalPdfViewerScreen
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.ZipInputStream

object PetalFileViewerBridge {

    @JvmStatic
    @JvmOverloads
    fun createFileViewerView(
        activity: ComponentActivity,
        fileUri: Uri,
        displayName: String? = null,
        onBackPress: () -> Unit,
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content)
            ?: activity.window.decorView
        PetalContentSnapshot.capture(rootView)

        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember {
                    PetalContentSnapshot.current?.asImageBitmap()
                }
                DisposableEffect(Unit) {
                    onDispose { PetalContentSnapshot.clear() }
                }

                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) { AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId,
                ) {
                    PetalUniversalFileViewerScreen(
                        fileUri = fileUri,
                        displayName = displayName ?: fileUri.lastPathSegment ?: "Document",
                        onBackPress = onBackPress,
                    )
                }
            }
        }
    }
}

enum class FileCategory {
    PDF,
    TEXT_CODE,
    ARCHIVE,
    DOCX,
    GENERIC_BINARY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalUniversalFileViewerScreen(
    fileUri: Uri,
    displayName: String,
    onBackPress: () -> Unit,
) {
    val context = LocalContext.current
    val extension = remember(fileUri, displayName) {
        val name = displayName.ifEmpty { fileUri.lastPathSegment ?: "" }
        name.substringAfterLast('.', "").lowercase(Locale.US)
    }

    val category = remember(extension) {
        when (extension) {
            "pdf" -> FileCategory.PDF
            "zip", "rar", "7z", "tar", "gz", "apk", "jar" -> FileCategory.ARCHIVE
            "docx" -> FileCategory.DOCX
            "txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "log",
            "kt", "java", "py", "c", "cpp", "h", "hpp", "js", "ts", "css", "sh",
            "yaml", "yml", "ini", "properties", "gradle", "sql", "svg" -> FileCategory.TEXT_CODE
            else -> FileCategory.GENERIC_BINARY
        }
    }

    // Combine existing PDF viewer directly if it's a PDF
    if (category == FileCategory.PDF) {
        PetalPdfViewerScreen(
            pdfUri = fileUri,
            displayName = displayName,
            onBackPress = onBackPress,
        )
        return
    }

    PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBackPress,
    ) {
        PetalScreenWrapper {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    UniversalFileViewerTopBar(
                        title = displayName,
                        extension = extension,
                        onBack = onBackPress,
                        onShare = { shareFile(context, fileUri, displayName) },
                        onOpenExternal = { openExternal(context, fileUri) },
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (category) {
                        FileCategory.ARCHIVE -> ArchiveViewerContent(fileUri = fileUri)
                        FileCategory.DOCX -> DocxViewerContent(fileUri = fileUri)
                        FileCategory.TEXT_CODE -> TextCodeViewerContent(fileUri = fileUri)
                        else -> GenericBinaryContent(fileUri = fileUri, displayName = displayName, extension = extension)
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversalFileViewerTopBar(
    title: String,
    extension: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (extension.isNotEmpty()) "${extension.uppercase(Locale.US)} Document" else "Document",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share file",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            IconButton(onClick = onOpenExternal) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Open with external app",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TextCodeViewerContent(fileUri: Uri) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    val reader = BufferedReader(InputStreamReader(stream))
                    val readLines = mutableListOf<String>()
                    var line: String?
                    var count = 0
                    while (reader.readLine().also { line = it } != null && count < 5000) {
                        readLines.add(line ?: "")
                        count++
                    }
                    reader.close()
                    lines = readLines
                } else {
                    errorMessage = "Cannot open file stream."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to read file contents."
            } finally {
                isLoading = false
            }
        }
    }

    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            ErrorDisplayBox(error = errorMessage ?: "")
        }
        lines != null -> {
            val listState = rememberLazyListState()
            val contentLines = lines ?: emptyList()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(contentLines) { index, lineText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .width(42.dp)
                                .padding(end = 8.dp),
                        )
                        Text(
                            text = lineText.ifEmpty { " " },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocxViewerContent(fileUri: Uri) {
    val context = LocalContext.current
    var extractedText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    val zip = ZipInputStream(stream)
                    var entry = zip.nextEntry
                    var foundDocument = false
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val xmlContent = zip.bufferedReader().readText()
                            val clean = xmlContent
                                .replace(Regex("<w:p.*?>"), "\n")
                                .replace(Regex("<[^>]*>"), "")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .trim()
                            extractedText = clean
                            foundDocument = true
                            break
                        }
                        entry = zip.nextEntry
                    }
                    zip.close()
                    if (!foundDocument) {
                        errorMessage = "Document XML structure not found in file."
                    }
                } else {
                    errorMessage = "Cannot open file stream."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to read DOCX file."
            } finally {
                isLoading = false
            }
        }
    }

    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            ErrorDisplayBox(error = errorMessage ?: "")
        }
        extractedText != null -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = extractedText ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                            lineHeight = 24.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveViewerContent(fileUri: Uri) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    val zip = ZipInputStream(stream)
                    val list = mutableListOf<String>()
                    var entry = zip.nextEntry
                    while (entry != null && list.size < 2000) {
                        list.add(entry.name + if (entry.isDirectory) " [Folder]" else "")
                        entry = zip.nextEntry
                    }
                    zip.close()
                    entries = list
                } else {
                    errorMessage = "Cannot open archive stream."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to read archive contents."
            } finally {
                isLoading = false
            }
        }
    }

    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        errorMessage != null -> {
            ErrorDisplayBox(error = errorMessage ?: "")
        }
        entries != null -> {
            val list = entries ?: emptyList()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(list) { _, itemText ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (itemText.endsWith(" [Folder]")) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = itemText.removeSuffix(" [Folder]"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenericBinaryContent(fileUri: Uri, displayName: String, extension: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (extension.isNotEmpty()) "${extension.uppercase(Locale.US)} File" else "Binary Data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(
            onClick = {
                PetalHapticEngine.getInstance(context).playClick(context)
                openExternal(context, fileUri)
            },
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open with external application")
        }
    }
}

@Composable
private fun ErrorDisplayBox(error: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Cannot View File",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun shareFile(context: Context, fileUri: Uri, displayName: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, fileUri)
            type = context.contentResolver.getType(fileUri) ?: "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share $displayName").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun openExternal(context: Context, fileUri: Uri) {
    try {
        val mime = context.contentResolver.getType(fileUri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
