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
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import java.util.zip.ZipInputStream
import java.io.OutputStreamWriter
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import android.widget.Toast

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
    PPTX,
    XLSX,
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

    val isTextOrCode = remember(extension) {
        when (extension) {
            "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "xhtml", "log",
            "kt", "kts", "java", "py", "pyw", "c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "cs",
            "js", "mjs", "cjs", "jsx", "ts", "tsx", "css", "scss", "sass", "less",
            "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "psm1",
            "yaml", "yml", "ini", "toml", "conf", "cfg", "properties", "gradle", "sql", "svg",
            "php", "rb", "rs", "go", "swift", "lua", "dart", "r", "scala", "pl", "pm",
            "asm", "s", "diff", "patch", "dockerfile", "env", "gitignore", "properties" -> true
            else -> false
        }
    }

    val category = remember(extension, isTextOrCode) {
        when {
            extension == "pdf" -> FileCategory.PDF
            extension in listOf("zip", "rar", "7z", "tar", "gz", "apk", "jar", "xpi") -> FileCategory.ARCHIVE
            extension == "docx" || extension == "doc" -> FileCategory.DOCX
            extension == "pptx" || extension == "ppt" -> FileCategory.PPTX
            extension == "xlsx" || extension == "xls" -> FileCategory.XLSX
            isTextOrCode -> FileCategory.TEXT_CODE
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

    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editableText by remember { mutableStateOf("") }
    var onSaveRequested by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                        category = category,
                        isEditing = isEditing,
                        onToggleEdit = { isEditing = !isEditing },
                        onSave = { onSaveRequested?.invoke() },
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
                        FileCategory.ARCHIVE -> ArchiveViewerContent(
                            fileUri = fileUri,
                            extension = extension,
                            displayName = displayName
                        )
                        FileCategory.DOCX -> DocxViewerContent(fileUri = fileUri)
                        FileCategory.PPTX -> PptxViewerContent(fileUri = fileUri)
                        FileCategory.XLSX -> XlsxViewerContent(fileUri = fileUri)
                        FileCategory.TEXT_CODE -> TextCodeViewerContent(
                            fileUri = fileUri,
                            isEditing = isEditing,
                            onTextLoaded = { loaded -> editableText = loaded },
                            onRegisterSaveHandler = { handler -> onSaveRequested = handler },
                            onEditFinish = { isEditing = false }
                        )
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
    category: FileCategory,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onSave: () -> Unit,
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

            if (category == FileCategory.TEXT_CODE) {
                if (isEditing) {
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = "Save file",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onToggleEdit) {
                    Icon(
                        imageVector = if (isEditing) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                        contentDescription = if (isEditing) "View mode" else "Edit mode",
                        tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
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
private fun TextCodeViewerContent(
    fileUri: Uri,
    isEditing: Boolean,
    onTextLoaded: (String) -> Unit,
    onRegisterSaveHandler: (() -> Unit) -> Unit,
    onEditFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullContent by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    val reader = BufferedReader(InputStreamReader(stream))
                    val sb = StringBuilder()
                    val readLines = mutableListOf<String>()
                    var line: String?
                    var count = 0
                    while (reader.readLine().also { line = it } != null) {
                        if (count < 8000) {
                            readLines.add(line ?: "")
                            if (count > 0) sb.append("\n")
                            sb.append(line ?: "")
                            count++
                        }
                    }
                    reader.close()
                    val resultText = sb.toString()
                    withContext(Dispatchers.Main) {
                        fullContent = resultText
                        lines = readLines
                        onTextLoaded(resultText)
                    }
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

    LaunchedEffect(fullContent) {
        onRegisterSaveHandler {
            scope.launch(Dispatchers.IO) {
                isSaving = true
                try {
                    val outStream = context.contentResolver.openOutputStream(fileUri, "wt")
                        ?: context.contentResolver.openOutputStream(fileUri, "w")
                    if (outStream != null) {
                        val writer = OutputStreamWriter(outStream, "UTF-8")
                        writer.write(fullContent)
                        writer.flush()
                        writer.close()
                        withContext(Dispatchers.Main) {
                            PetalHapticEngine.getInstance(context).playClick(context)
                            lines = fullContent.split("\n")
                            Toast.makeText(context, "File saved successfully", Toast.LENGTH_SHORT).show()
                            onEditFinish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not open file for writing", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isSaving = false
                }
            }
        }
    }

    when {
        isLoading || isSaving -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    if (isSaving) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Saving changes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        errorMessage != null -> {
            ErrorDisplayBox(error = errorMessage ?: "")
        }
        isEditing -> {
            OutlinedTextField(
                value = fullContent,
                onValueChange = {
                    fullContent = it
                    onTextLoaded(it)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
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
private fun PptxViewerContent(fileUri: Uri) {
    val context = LocalContext.current
    var slides by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    val zip = ZipInputStream(stream)
                    val slideMap = sortedMapOf<Int, String>()
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                            val numStr = name.removePrefix("ppt/slides/slide").removeSuffix(".xml")
                            val slideNum = numStr.toIntOrNull() ?: 999
                            val xml = zip.bufferedReader().readText()
                            val clean = xml
                                .replace(Regex("<a:p.*?>"), "\n")
                                .replace(Regex("<[^>]*>"), "")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&quot;", "\"")
                                .replace("&apos;", "'")
                                .trim()
                            if (clean.isNotEmpty()) {
                                slideMap[slideNum] = clean
                            }
                        }
                        entry = zip.nextEntry
                    }
                    zip.close()
                    if (slideMap.isNotEmpty()) {
                        slides = slideMap.values.toList()
                    } else {
                        errorMessage = "No presentation slide content found in PPTX."
                    }
                } else {
                    errorMessage = "Cannot open presentation stream."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to read presentation."
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
        slides != null -> {
            val slideList = slides ?: emptyList()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(slideList) { index, slideText ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "Slide ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = slideText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XlsxViewerContent(fileUri: Uri) {
    val context = LocalContext.current
    var extractedRows by remember { mutableStateOf<List<List<String>>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    val zip = ZipInputStream(stream)
                    val sharedStrings = mutableListOf<String>()
                    val sheetXmls = mutableListOf<String>()
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "xl/sharedStrings.xml") {
                            val xml = zip.bufferedReader().readText()
                            val parts = xml.split("<si>")
                            for (p in parts.drop(1)) {
                                val s = p.split("</si>").firstOrNull() ?: ""
                                val clean = s.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
                                sharedStrings.add(clean)
                            }
                        } else if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                            sheetXmls.add(zip.bufferedReader().readText())
                        }
                        entry = zip.nextEntry
                    }
                    zip.close()

                    if (sheetXmls.isNotEmpty()) {
                        val firstSheet = sheetXmls.first()
                        val rowStrings = mutableListOf<List<String>>()
                        val rowTokens = firstSheet.split("<row")
                        for (r in rowTokens.drop(1)) {
                            val rowBody = r.split("</row>").firstOrNull() ?: ""
                            val cells = mutableListOf<String>()
                            val cellTokens = rowBody.split("<c ")
                            for (c in cellTokens.drop(1)) {
                                val isShared = c.contains("t=\"s\"")
                                val valMatch = Regex("<v>(.*?)</v>").find(c)
                                val cellVal = valMatch?.groupValues?.get(1)?.trim() ?: ""
                                val text = if (isShared) {
                                    val idx = cellVal.toIntOrNull() ?: -1
                                    if (idx in 0 until sharedStrings.size) sharedStrings[idx] else cellVal
                                } else {
                                    cellVal
                                }
                                if (text.isNotEmpty()) {
                                    cells.add(text)
                                }
                            }
                            if (cells.isNotEmpty()) {
                                rowStrings.add(cells)
                            }
                        }
                        extractedRows = rowStrings
                    } else {
                        errorMessage = "No worksheet data found in XLSX."
                    }
                } else {
                    errorMessage = "Cannot open spreadsheet stream."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to read spreadsheet."
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
        extractedRows != null -> {
            val rows = extractedRows ?: emptyList()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(rows) { rowIndex, cellValues ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (rowIndex == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rowIndex + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.width(32.dp)
                            )
                            cellValues.forEach { cell ->
                                Text(
                                    text = cell,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (rowIndex == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                            }
                        }
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
private fun ArchiveViewerContent(
    fileUri: Uri,
    extension: String,
    displayName: String
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isInstallingApk by remember { mutableStateOf(false) }

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

    val isApk = remember(extension) { extension.equals("apk", ignoreCase = true) }

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
            Column(modifier = Modifier.fillMaxSize()) {
                if (isApk) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Android Package (APK)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Install this application directly",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Button(
                                onClick = {
                                    PetalHapticEngine.getInstance(context).playClick(context)
                                    installApkPackage(context, fileUri, displayName)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Install")
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
}

private fun installApkPackage(context: Context, fileUri: Uri, displayName: String) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                Toast.makeText(context, "Please enable permission to install packages", Toast.LENGTH_LONG).show()
                return
            }
        }

        val apkUriToInstall: Uri = if (fileUri.scheme == "file") {
            val f = File(fileUri.path ?: "")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        } else {
            // Copy to cache dir to ensure package manager has direct file read permission
            val tempApk = File(context.cacheDir, displayName.ifEmpty { "install.apk" })
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(tempApk).use { output ->
                    input.copyTo(output)
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempApk)
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUriToInstall, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
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
