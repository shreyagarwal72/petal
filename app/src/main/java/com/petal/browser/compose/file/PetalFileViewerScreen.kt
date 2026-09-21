/*
 * PetalFileViewerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Built-in native Material 3 Expressive Petal File Viewer for Petal Browser.
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.compose.pdf.PetalPdfViewerScreen
import com.petal.browser.haptics.PetalHapticEngine
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

object PetalStandaloneFileViewerBridge {

    @JvmStatic
    @JvmOverloads
    fun createFileViewerView(
        activity: ComponentActivity,
        fileUri: Uri,
        displayName: String? = null,
        onBackPress: () -> Unit,
    ): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
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
                    PetalFileViewerScreen(
                        fileUri = fileUri,
                        displayName = displayName ?: fileUri.lastPathSegment ?: "Document",
                        onBackPress = onBackPress,
                    )
                }
            }
        }
    }
}

enum class PetalFileViewerCategory {
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
fun PetalFileViewerScreen(
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
            extension == "pdf" -> PetalFileViewerCategory.PDF
            extension in listOf("zip", "rar", "7z", "tar", "gz", "apk", "jar", "xpi") -> PetalFileViewerCategory.ARCHIVE
            extension == "docx" || extension == "doc" -> PetalFileViewerCategory.DOCX
            extension == "pptx" || extension == "ppt" -> PetalFileViewerCategory.PPTX
            extension == "xlsx" || extension == "xls" -> PetalFileViewerCategory.XLSX
            isTextOrCode -> PetalFileViewerCategory.TEXT_CODE
            else -> PetalFileViewerCategory.GENERIC_BINARY
        }
    }

    // Combine existing PDF viewer directly if it's a PDF
    if (category == PetalFileViewerCategory.PDF) {
        PetalPdfViewerScreen(
            pdfUri = fileUri,
            displayName = displayName,
            onBackPress = onBackPress,
        )
        return
    }

    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var isWordWrap by remember { mutableStateOf(false) }
    var editableText by remember { mutableStateOf("") }
    var onSaveRequested by remember { mutableStateOf<(() -> Unit)?>(null) }

    androidx.activity.compose.BackHandler(onBack = onBackPress)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            UniversalFileViewerTopBar(
                title = displayName,
                extension = extension,
                category = category,
                isEditing = isEditing,
                isWordWrap = isWordWrap,
                onToggleEdit = { isEditing = !isEditing },
                onToggleWrap = { isWordWrap = !isWordWrap },
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
                        .navigationBarsPadding()
                ) {
                    when (category) {
                        PetalFileViewerCategory.ARCHIVE -> ArchiveViewerContent(
                            fileUri = fileUri,
                            extension = extension,
                            displayName = displayName
                        )
                        PetalFileViewerCategory.DOCX -> DocxViewerContent(fileUri = fileUri)
                        PetalFileViewerCategory.PPTX -> PptxViewerContent(fileUri = fileUri)
                        PetalFileViewerCategory.XLSX -> XlsxViewerContent(fileUri = fileUri)
                        PetalFileViewerCategory.TEXT_CODE -> TextCodeViewerContent(
                            fileUri = fileUri,
                            isEditing = isEditing,
                            isWordWrap = isWordWrap,
                            onTextLoaded = { loaded -> editableText = loaded },
                            onRegisterSaveHandler = { handler -> onSaveRequested = handler },
                            onEditFinish = { isEditing = false }
                        )
                        else -> GenericBinaryContent(fileUri = fileUri, displayName = displayName, extension = extension)
                    }
                }
            }
}

@Composable
private fun UniversalFileViewerTopBar(
    title: String,
    extension: String,
    category: PetalFileViewerCategory,
    isEditing: Boolean,
    isWordWrap: Boolean,
    onToggleEdit: () -> Unit,
    onToggleWrap: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    val topClearance = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding().coerceAtLeast(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topClearance),
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

            if (category == PetalFileViewerCategory.TEXT_CODE) {
                if (isEditing) {
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = "Save file",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(onClick = onToggleWrap) {
                        Icon(
                            imageVector = Icons.Rounded.WrapText,
                            contentDescription = if (isWordWrap) "Disable word wrap" else "Enable word wrap",
                            tint = if (isWordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
    isWordWrap: Boolean,
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
            val horizontalScrollState = rememberScrollState()

            // Pre-calculate line number column width based on digit count
            val lineNumberWidth = remember(contentLines.size) {
                val digits = contentLines.size.toString().length.coerceAtLeast(2)
                (digits * 9 + 18).dp
            }

            // Hoist text styles and colors to avoid allocations per row per frame
            val lineNumberStyle = remember {
                TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.End)
            }
            val lineNumberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            val lineTextStyle = remember {
                TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            val lineTextColor = MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .then(if (!isWordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = if (isWordWrap) Modifier.fillMaxSize() else Modifier.wrapContentWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(
                        items = contentLines,
                        key = { index, _ -> index },
                        contentType = { _, _ -> 0 }
                    ) { index, lineText ->
                        Row(
                            modifier = (if (isWordWrap) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                                .padding(horizontal = 8.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = lineNumberStyle,
                                color = lineNumberColor,
                                modifier = Modifier
                                    .width(lineNumberWidth)
                                    .padding(end = 10.dp),
                            )
                            Text(
                                text = lineText.ifEmpty { " " },
                                style = lineTextStyle,
                                color = lineTextColor,
                                modifier = if (isWordWrap) Modifier.weight(1f, fill = false) else Modifier,
                            )
                        }
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
            val tableScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(tableScrollState)
            ) {
                LazyColumn(
                    modifier = Modifier.wrapContentWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { index, _ -> index },
                        contentType = { index, _ -> if (index == 0) 1 else 0 }
                    ) { rowIndex, cellValues ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (rowIndex == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
            val paragraphs = remember(extractedText) {
                extractedText?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = paragraphs,
                    key = { index, _ -> index },
                    contentType = { _, _ -> 0 }
                ) { _, para ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = para,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp),
                            lineHeight = 24.sp,
                        )
                    }
                }
            }
        }
    }
}

private data class ArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
)

private data class ArchiveBrowserState(
    val entries: List<ArchiveEntry>,
    val rootName: String,
)

@Composable
private fun ArchiveViewerContent(
    fileUri: Uri,
    extension: String,
    displayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archiveState by remember(fileUri) { mutableStateOf<ArchiveBrowserState?>(null) }
    var currentPath by remember(fileUri) { mutableStateOf("") }
    var isLoading by remember(fileUri) { mutableStateOf(true) }
    var errorMessage by remember(fileUri) { mutableStateOf<String?>(null) }
    var selectedEntry by remember(fileUri) { mutableStateOf<ArchiveEntry?>(null) }
    var isOpening by remember(fileUri) { mutableStateOf(false) }
    var isInstallingApk by remember(fileUri) { mutableStateOf(false) }

    val isZipFamily = remember(extension) {
        extension.lowercase(Locale.US) in setOf("zip", "apk", "jar", "xpi", "crx")
    }

    LaunchedEffect(fileUri, isZipFamily) {
        isLoading = true
        errorMessage = null
        if (!isZipFamily) {
            errorMessage = "This compressed format is not supported for folder browsing yet. ZIP-based archives are supported."
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                    ?: throw IllegalStateException("Cannot open archive stream.")
                val entries = mutableListOf<ArchiveEntry>()
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    var count = 0
                    while (entry != null && count < 10000) {
                        val normalized = entry.name.replace('\\', '/').trimStart('/')
                        if (normalized.isNotEmpty()) {
                            entries += ArchiveEntry(
                                path = normalized,
                                isDirectory = entry.isDirectory || normalized.endsWith('/'),
                                size = entry.size.takeIf { it >= 0 } ?: 0L,
                            )
                        }
                        count++
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val result = ArchiveBrowserState(entries.distinctBy { it.path }, displayName)
                withContext(Dispatchers.Main) { archiveState = result }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Failed to read archive contents."
                }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    val state = archiveState
    val pathSegments = currentPath.split('/').filter { it.isNotBlank() }

    fun navigateUp() {
        currentPath = currentPath.substringBeforeLast('/', "")
        selectedEntry = null
    }

    fun openEntry(entry: ArchiveEntry) {
        if (entry.isDirectory) {
            currentPath = entry.path.trimEnd('/')
            selectedEntry = null
            return
        }
        selectedEntry = entry
    }

    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Opening archive…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        errorMessage != null -> {
            ErrorDisplayBox(error = errorMessage ?: "")
        }
        state != null -> {
            if (selectedEntry != null) {
                ArchiveEntryPreview(
                    archiveUri = fileUri,
                    entry = selectedEntry!!,
                    onBack = { selectedEntry = null },
                )
            } else {
                val visibleEntries = remember(state, currentPath) {
                    buildArchiveChildren(state.entries, currentPath)
                }
                Column(Modifier.fillMaxSize()) {
                    if (pathSegments.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { navigateUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up")
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = pathSegments.last(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${visibleEntries.size} items",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.FolderZip,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Archive contents",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "Browse folders and files separately",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (visibleEntries.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(top = 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "This folder is empty",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(visibleEntries, key = { _, item -> item.path }) { _, entry ->
                                ArchiveEntryRow(
                                    entry = entry,
                                    onClick = { openEntry(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildArchiveChildren(entries: List<ArchiveEntry>, currentPath: String): List<ArchiveEntry> {
    val prefix = currentPath.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    val children = linkedMapOf<String, ArchiveEntry>()

    entries.forEach { entry ->
        if (!entry.path.startsWith(prefix) || entry.path == currentPath.trim('/')) return@forEach
        val remainder = entry.path.removePrefix(prefix)
        if (remainder.isEmpty()) return@forEach

        val slash = remainder.indexOf('/')
        if (slash < 0) {
            children[remainder] = entry.copy(path = prefix + remainder)
        } else {
            val folderPath = prefix + remainder.substring(0, slash)
            children.putIfAbsent(folderPath, ArchiveEntry(folderPath, true))
        }
    }

    return children.values.sortedWith(compareByDescending<ArchiveEntry> { it.isDirectory }.thenBy { it.path.lowercase(Locale.US) })
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveEntry,
    onClick: () -> Unit,
) {
    val isFolder = entry.isDirectory
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isFolder) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFolder) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = if (isFolder) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.path.substringAfterLast('/').ifEmpty { entry.path },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isFolder) "Folder" else formatArchiveSize(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (isFolder) Icons.Rounded.KeyboardArrowRight else Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArchiveEntryPreview(
    archiveUri: Uri,
    entry: ArchiveEntry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember(entry.path) { mutableStateOf(true) }
    var tempUri by remember(entry.path) { mutableStateOf<Uri?>(null) }
    var error by remember(entry.path) { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.path) {
        withContext(Dispatchers.IO) {
            try {
                val safeName = entry.path.substringAfterLast('/').ifEmpty { "file" }
                val output = File(context.cacheDir, "archive_preview_${System.currentTimeMillis()}_$safeName")
                context.contentResolver.openInputStream(archiveUri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        var current = zip.nextEntry
                        while (current != null) {
                            if (current.name.replace('\\', '/').trimStart('/') == entry.path.trimStart('/')) {
                                FileOutputStream(output).use { out -> zip.copyTo(out) }
                                break
                            }
                            zip.closeEntry()
                            current = zip.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("Cannot open archive stream.")

                if (!output.exists() || output.length() == 0L) {
                    throw IllegalStateException("Could not extract this file from the archive.")
                }
                withContext(Dispatchers.Main) {
                    tempUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.localizedMessage ?: "Could not open archive entry."
                    isLoading = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to archive")
                }
                Text(
                    entry.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> ErrorDisplayBox(error ?: "")
            tempUri != null -> {
                val uri = tempUri!!
                val name = entry.path.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                val isText = ext in setOf("txt", "md", "json", "xml", "csv", "html", "htm", "kt", "java", "py", "js", "ts", "css", "yaml", "yml", "log", "ini", "toml", "gradle", "sql")
                if (ext == "pdf") {
                    PetalPdfViewerScreen(pdfUri = uri, displayName = name, onBackPress = onBack)
                } else if (isText) {
                    TextCodeViewerContent(
                        fileUri = uri,
                        isEditing = false,
                        isWordWrap = true,
                        onTextLoaded = {},
                        onRegisterSaveHandler = {},
                        onEditFinish = {}
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.InsertDriveFile, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Text("This entry was extracted successfully. Open it with another app to view this format.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mimeTypeForExtension(ext))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }) { Text("Open with…") }
                        }
                    }
                }
            }
        }
    }
}

private fun mimeTypeForExtension(extension: String): String = when (extension.lowercase(Locale.US)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp3" -> "audio/mpeg"
    "mp4" -> "video/mp4"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "json" -> "application/json"
    "html", "htm" -> "text/html"
    else -> "application/octet-stream"
}

private fun formatArchiveSize(bytes: Long): String {
    if (bytes <= 0L) return "File"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[unit])
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
        com.petal.browser.ui.components.PetalShapeIconBadge(
            shape = com.petal.browser.ui.theme.PetalMaterialShapes.Cookie9Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 80.dp,
            iconSize = 40.dp,
        ) {
            Icon(
                imageVector = Icons.Rounded.InsertDriveFile,
                contentDescription = null,
            )
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
                com.petal.browser.ui.components.PetalShapeIconBadge(
                    shape = com.petal.browser.ui.theme.PetalMaterialShapes.SoftBoom.toShape(),
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.error,
                    size = 72.dp,
                    iconSize = 36.dp,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                    )
                }
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
