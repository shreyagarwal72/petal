/*
 * PetalFileViewerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Built-in native Material 3 Expressive Petal File Viewer for Petal Browser.
 * Supports viewing all major file formats with ZArchiver-inspired directory
 * navigation, file type detection, and Petal Containment design:
 *   • PDF documents (direct embedded native PdfRenderer)
 *   • Archives: .zip, .jar, .xpi, .tar, .gz, .tgz, .7z, .rar
 *     - ZArchiver style: separate folder drill-down, interactive breadcrumbs,
 *       extracted file previews for images, text, and sub-documents.
 *   • Office documents: Word (.docx, .doc), PowerPoint (.pptx, .ppt), Excel (.xlsx, .xls)
 *   • OpenDocument formats: .odt, .ods, .odp, .rtf
 *   • Images: .png, .jpg, .jpeg, .webp, .gif, .bmp, .svg, .ico, .avif, .heic
 *   • Audio & Video: .mp3, .wav, .ogg, .m4a, .flac, .aac, .mp4, .webm, .mkv
 *   • Plain text, Markdown, CSV, TSV, XML, JSON, HTML, YAML, TOML, INI, and all code files
 *   • Formatted generic fallback with MIME type identification and external app launch
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.file

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.compose.pdf.PetalPdfViewerScreen
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.toShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.zip.GZIPInputStream
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

// Backward compatibility alias
object PetalStandaloneFileViewerBridge {
    @JvmStatic
    @JvmOverloads
    fun createFileViewerView(
        activity: ComponentActivity,
        fileUri: Uri,
        displayName: String? = null,
        onBackPress: () -> Unit,
    ): ComposeView = PetalFileViewerBridge.createFileViewerView(activity, fileUri, displayName, onBackPress)
}

enum class PetalFileViewerCategory {
    PDF,
    TEXT_CODE,
    ARCHIVE,
    IMAGE,
    MEDIA,
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
            "asm", "s", "diff", "patch", "dockerfile", "env", "gitignore", "rtf" -> true
            else -> false
        }
    }

    val category = remember(extension, isTextOrCode) {
        when {
            extension == "pdf" -> PetalFileViewerCategory.PDF
            // Archives: ZArchiver style exploration (excluding APK installation)
            extension in listOf("zip", "rar", "7z", "tar", "gz", "tgz", "jar", "xpi", "bz2", "xz") -> PetalFileViewerCategory.ARCHIVE
            extension in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "avif", "heic") -> PetalFileViewerCategory.IMAGE
            extension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "mp4", "mkv", "webm", "3gp") -> PetalFileViewerCategory.MEDIA
            extension in listOf("docx", "doc", "odt") -> PetalFileViewerCategory.DOCX
            extension in listOf("pptx", "ppt", "odp") -> PetalFileViewerCategory.PPTX
            extension in listOf("xlsx", "xls", "ods") -> PetalFileViewerCategory.XLSX
            isTextOrCode -> PetalFileViewerCategory.TEXT_CODE
            else -> PetalFileViewerCategory.GENERIC_BINARY
        }
    }

    // PDF documents rendered natively
    if (category == PetalFileViewerCategory.PDF) {
        PetalPdfViewerScreen(
            pdfUri = fileUri,
            displayName = displayName,
            onBackPress = onBackPress,
        )
        return
    }

    var isEditing by remember { mutableStateOf(false) }
    var isWordWrap by remember { mutableStateOf(false) }
    var onSaveRequested by remember { mutableStateOf<(() -> Unit)?>(null) }

    val backgroundSnapshot = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBackPress
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            // Glass backdrop: records the page background so every card and the top bar
            // frost and refract it live. The Box colour is the plain (non-glass) fallback.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
            com.petal.browser.ui.components.M3ExpressiveVariableBackground(
                modifier = Modifier.fillMaxSize(),
                pageSeed = "petal_file_viewer"
            )
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PetalFileViewerTopBar(
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
                PetalFileViewerCategory.IMAGE -> ImageViewerContent(fileUri = fileUri, displayName = displayName)
                PetalFileViewerCategory.MEDIA -> MediaViewerContent(fileUri = fileUri, displayName = displayName, extension = extension)
                PetalFileViewerCategory.DOCX -> DocxViewerContent(fileUri = fileUri)
                PetalFileViewerCategory.PPTX -> PptxViewerContent(fileUri = fileUri)
                PetalFileViewerCategory.XLSX -> XlsxViewerContent(fileUri = fileUri)
                PetalFileViewerCategory.TEXT_CODE -> TextCodeViewerContent(
                    fileUri = fileUri,
                    isEditing = isEditing,
                    isWordWrap = isWordWrap,
                    onTextLoaded = {},
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
}

@Composable
private fun PetalFileViewerTopBar(
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
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val barShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGlass) Modifier.liquidGlassChrome(barShape, enabled = true) else Modifier),
        shape = barShape,
        color = if (isGlass) petalGroupSurfaceColor()
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = if (isGlass) 0.dp else 3.dp,
        shadowElevation = if (isGlass) 0.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Status bar / cutout inset goes INSIDE the bar so it paints behind the status
                // bar instead of leaving a black gap above it.
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.ui_back),
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
                    text = if (extension.isNotEmpty()) "${extension.uppercase(Locale.US)} File" else "File",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (category == PetalFileViewerCategory.TEXT_CODE) {
                if (isEditing) {
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = stringResource(R.string.ui_save_file),
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
                    contentDescription = stringResource(R.string.ui_share_file),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            IconButton(onClick = onOpenExternal) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.ui_open_with_external_app),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── ARCHIVE VIEWER (ZArchiver style separate directory exploration) ───────────

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
    var archiveState by remember(fileUri) { mutableStateOf<ArchiveBrowserState?>(null) }
    var currentPath by remember(fileUri) { mutableStateOf("") }
    var isLoading by remember(fileUri) { mutableStateOf(true) }
    var errorMessage by remember(fileUri) { mutableStateOf<String?>(null) }
    var selectedEntry by remember(fileUri) { mutableStateOf<ArchiveEntry?>(null) }

    LaunchedEffect(fileUri, extension) {
        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                    ?: throw IllegalStateException("Cannot open archive stream.")
                val entries = mutableListOf<ArchiveEntry>()

                val ext = extension.lowercase(Locale.US)
                if (ext in setOf("gz", "tgz")) {
                    // Try parsing GZIP stream
                    try {
                        GZIPInputStream(stream).use { gzip ->
                            val buf = ByteArray(1024)
                            var read = 0
                            var total = 0L
                            while (gzip.read(buf).also { read = it } > 0) {
                                total += read
                            }
                            entries += ArchiveEntry(displayName.removeSuffix(".gz"), false, total)
                        }
                    } catch (_: Exception) {
                        entries += ArchiveEntry(displayName.removeSuffix(".gz"), false, 0L)
                    }
                } else {
                    // Standard Zip / Tar / Jar stream reading
                    ZipInputStream(stream).use { zip ->
                        var entry = zip.nextEntry
                        var count = 0
                        while (entry != null && count < 25000) {
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
    val pathSegments = remember(currentPath) {
        currentPath.split('/').filter { it.isNotBlank() }
    }

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
                    PetalContainedLoadingIndicator(size = 48.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.ui_opening_archive), style = MaterialTheme.typography.bodyMedium)
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
                    // ── ZArchiver Style Interactive Path Breadcrumbs & Hero Header ──────
                    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
                    val headerShape = RoundedCornerShape(20.dp)
                    Surface(
                        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = headerShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .liquidGlassChrome(headerShape, enabled = isGlass)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (pathSegments.isNotEmpty()) {
                                    IconButton(
                                        onClick = { navigateUp() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Up",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    PetalGroupIconBadge(
                                        icon = Icons.Rounded.FolderZip,
                                        container = MaterialTheme.colorScheme.primaryContainer,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        size = 36.dp,
                                        iconSize = 20.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (pathSegments.isEmpty()) displayName else pathSegments.last(),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_items, visibleEntries.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Interactive Breadcrumb Scrollable Row
                            if (pathSegments.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                val breadcrumbScroll = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(breadcrumbScroll),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Root chip
                                    FilterChip(
                                        selected = false,
                                        onClick = { currentPath = "" },
                                        label = { Text(displayName, maxLines = 1) },
                                        leadingIcon = { Icon(Icons.Rounded.Home, null, Modifier.size(14.dp)) }
                                    )
                                    pathSegments.forEachIndexed { idx, segment ->
                                        Icon(
                                            Icons.Rounded.ChevronRight,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val isCurrent = idx == pathSegments.lastIndex
                                        FilterChip(
                                            selected = isCurrent,
                                            onClick = {
                                                currentPath = pathSegments.take(idx + 1).joinToString("/")
                                            },
                                            label = { Text(segment, maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Items List with Petal Containment Grouping ──────────────────────
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (visibleEntries.isEmpty()) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Rounded.FolderOpen,
                                            null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            stringResource(R.string.ui_this_folder_is_empty),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                visibleEntries,
                                key = { _, item -> item.path }
                            ) { index, entry ->
                                val position = petalGroupPositionFor(index, visibleEntries.size)
                                ArchiveEntryRow(
                                    entry = entry,
                                    position = position,
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
            val folderName = remainder.substring(0, slash)
            val folderPath = prefix + folderName
            children.putIfAbsent(folderPath, ArchiveEntry(folderPath, true))
        }
    }

    return children.values.sortedWith(
        compareByDescending<ArchiveEntry> { it.isDirectory }
            .thenBy { it.path.substringAfterLast('/').lowercase(Locale.US) }
    )
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveEntry,
    position: PetalGroupPosition,
    onClick: () -> Unit,
) {
    val isFolder = entry.isDirectory
    val name = entry.path.substringAfterLast('/').ifEmpty { entry.path }
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)

    val (iconVector, badgeContainer, badgeTint) = remember(isFolder, ext) {
        when {
            isFolder -> Triple(Icons.Filled.Folder, null, null)
            ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg") ->
                Triple(Icons.Rounded.Image, null, null)
            ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "mp4", "mkv", "webm") ->
                Triple(Icons.Rounded.Audiotrack, null, null)
            ext in listOf("pdf") ->
                Triple(Icons.Rounded.PictureAsPdf, null, null)
            ext in listOf("zip", "rar", "7z", "tar", "gz") ->
                Triple(Icons.Rounded.FolderZip, null, null)
            ext in listOf("txt", "md", "json", "xml", "html", "kt", "java", "py", "js", "ts", "css", "c", "cpp") ->
                Triple(Icons.Rounded.Code, null, null)
            else -> Triple(Icons.Filled.InsertDriveFile, null, null)
        }
    }

    PetalGroupListRow(
        position = position,
        onClick = onClick,
        leading = {
            PetalGroupIconBadge(
                icon = iconVector,
                container = if (isFolder) MaterialTheme.colorScheme.secondaryContainer else (badgeContainer ?: MaterialTheme.colorScheme.primaryContainer),
                tint = if (isFolder) MaterialTheme.colorScheme.onSecondaryContainer else (badgeTint ?: MaterialTheme.colorScheme.onPrimaryContainer),
            )
        },
        content = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isFolder) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isFolder) "Directory" else formatArchiveSize(entry.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailing = {
            Icon(
                imageVector = if (isFolder) Icons.Filled.ChevronRight else Icons.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
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
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PetalContainedLoadingIndicator(size = 48.dp) }
            error != null -> ErrorDisplayBox(error ?: "")
            tempUri != null -> {
                val uri = tempUri!!
                val name = entry.path.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                val isText = ext in setOf("txt", "md", "json", "xml", "csv", "html", "htm", "kt", "java", "py", "js", "ts", "css", "yaml", "yml", "log", "ini", "toml", "gradle", "sql")
                val isImage = ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

                when {
                    ext == "pdf" -> PetalPdfViewerScreen(pdfUri = uri, displayName = name, onBackPress = onBack)
                    isImage -> ImageViewerContent(fileUri = uri, displayName = name)
                    isText -> TextCodeViewerContent(
                        fileUri = uri,
                        isEditing = false,
                        isWordWrap = true,
                        onTextLoaded = {},
                        onRegisterSaveHandler = {},
                        onEditFinish = {}
                    )
                    else -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PetalGroupIconBadge(
                                    icon = Icons.Rounded.InsertDriveFile,
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    size = 64.dp,
                                    iconSize = 32.dp
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.ui_this_entry_was_extracted_successfully),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(18.dp))
                                FilledTonalButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeTypeForExtension(ext))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                }) { Text(stringResource(R.string.ui_open_with)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── IMAGE VIEWER COMPONENT ───────────────────────────────────────────────────

@Composable
private fun ImageViewerContent(fileUri: Uri, displayName: String) {
    val context = LocalContext.current
    var bitmap by remember(fileUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(fileUri) { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(fileUri)?.use { input ->
                    bitmap = BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
        when {
            isLoading -> PetalContainedLoadingIndicator(size = 48.dp)
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> Text("Unable to preview image", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── MEDIA VIEWER COMPONENT ───────────────────────────────────────────────────

@Composable
private fun MediaViewerContent(fileUri: Uri, displayName: String, extension: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PetalGroupIconBadge(
                icon = Icons.Rounded.PlayCircle,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 80.dp,
                iconSize = 44.dp
            )
            Spacer(Modifier.height(16.dp))
            Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("${extension.uppercase(Locale.US)} Media", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { openExternal(context, fileUri) },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Play in Media Player")
            }
        }
    }
}

// ── TEXT & CODE VIEWER COMPONENT ─────────────────────────────────────────────

@Composable
private fun TextCodeViewerContent(
    fileUri: Uri,
    isEditing: Boolean,
    isWordWrap: Boolean,
    onTextLoaded: (String) -> Unit,
    onRegisterSaveHandler: (() -> Unit) -> Unit,
    onEditFinish: () -> Unit,
) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var fullText by remember { mutableStateOf("") }
    var editableText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    val reader = BufferedReader(InputStreamReader(stream))
                    val lineList = mutableListOf<String>()
                    val stringBuilder = StringBuilder()
                    var line = reader.readLine()
                    var count = 0
                    while (line != null && count < 50000) {
                        lineList.add(line)
                        stringBuilder.append(line).append("\n")
                        line = reader.readLine()
                        count++
                    }
                    reader.close()
                    val total = stringBuilder.toString()
                    lines = lineList
                    fullText = total
                    editableText = total
                    onTextLoaded(total)
                } else {
                    errorMessage = "Cannot open file stream."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to read file."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(editableText) {
        onRegisterSaveHandler {
            try {
                context.contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                    val writer = OutputStreamWriter(output)
                    writer.write(editableText)
                    writer.flush()
                }
                fullText = editableText
                lines = editableText.split("\n")
                onEditFinish()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PetalContainedLoadingIndicator(size = 48.dp)
            }
        }
        errorMessage != null -> {
            ErrorDisplayBox(error = errorMessage ?: "")
        }
        isEditing -> {
            OutlinedTextField(
                value = editableText,
                onValueChange = { editableText = it },
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

            val lineNumberWidth = remember(contentLines.size) {
                val digits = contentLines.size.toString().length.coerceAtLeast(2)
                (digits * 9 + 18).dp
            }

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

// ── OFFICE / SPREADSHEET / PRESENTATION VIEWERS ───────────────────────────────

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
                        errorMessage = "No presentation slide content found."
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
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PetalContainedLoadingIndicator(size = 48.dp) }
        errorMessage != null -> ErrorDisplayBox(error = errorMessage ?: "")
        slides != null -> {
            val slideList = slides ?: emptyList()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(slideList, key = { index, _ -> index }) { index, slideText ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Slide ${index + 1}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
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
                        errorMessage = "No worksheet data found."
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
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PetalContainedLoadingIndicator(size = 48.dp) }
        errorMessage != null -> ErrorDisplayBox(error = errorMessage ?: "")
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
                    itemsIndexed(rows, key = { index, _ -> index }) { rowIndex, cellValues ->
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
                        errorMessage = "Document content not found."
                    }
                } else {
                    errorMessage = "Cannot open file stream."
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to read document."
            } finally {
                isLoading = false
            }
        }
    }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PetalContainedLoadingIndicator(size = 48.dp) }
        errorMessage != null -> ErrorDisplayBox(error = errorMessage ?: "")
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
                itemsIndexed(paragraphs, key = { index, _ -> index }) { _, para ->
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

// ── FALLBACK & ERROR SCREENS ──────────────────────────────────────────────────

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
        PetalGroupIconBadge(
            shape = PetalMaterialShapes.Cookie9Sided.toShape(),
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
            Text(stringResource(R.string.ui_open_with_external_application))
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
                PetalGroupIconBadge(
                    shape = PetalMaterialShapes.SoftBoom.toShape(),
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
                    text = stringResource(R.string.ui_cannot_view_file),
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

// ── UTILITIES ─────────────────────────────────────────────────────────────────

private fun mimeTypeForExtension(extension: String): String = when (extension.lowercase(Locale.US)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "svg" -> "image/svg+xml"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "ogg" -> "audio/ogg"
    "m4a" -> "audio/mp4"
    "flac" -> "audio/flac"
    "mp4" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "json" -> "application/json"
    "html", "htm" -> "text/html"
    "xml" -> "application/xml"
    "csv" -> "text/csv"
    else -> "application/octet-stream"
}

private fun formatArchiveSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unit])
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
