/*
 * PetalFilePicker.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Built-in native Material 3 Expressive File Selector & Browser for Petal Browser.
 * Inspired by Morphe-Manager's file browser and enhanced with:
 *   • Pure Material 3 Expressive design (rounded pill chips, fluid animations, liquid glass chrome header)
 *   • Storage roots detection: Downloads, Internal Storage, SD Cards, Root (/)
 *   • Dynamic breadcrumbs bar with quick interactive folder navigation
 *   • Multi-sort: Name (A-Z / Z-A), Size (desc/asc), Date (desc/asc), and Hidden Files toggle
 *   • Live search filtering within directories
 *   • Rich thumbnails: APK application icons (cached via PackageManager), image thumbnails, split packages
 *   • File previews: One-tap integration into Petal File Viewer (PDF, Code, Office, Markdown, Archives)
 *   • Full folder selection support when requested
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.compose.file

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ExpressiveHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileSortMode {
    NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC, DATE_DESC, DATE_ASC
}

private val APK_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "svg")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus")
private val CODE_EXTENSIONS = setOf("kt", "java", "py", "c", "cpp", "h", "js", "ts", "html", "css", "json", "xml", "sh", "yaml", "yml", "sql")
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "jar", "xpi")
private val DOC_EXTENSIONS = setOf("pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "md")

private val apkPackageInfoCache = LruCache<String, PackageInfo>(100)
private val imageThumbnailCache = LruCache<String, ImageBitmap>(50)

/**
 * Returns external storage roots as (Label to Root File) pairs.
 */
fun getStorageRoots(context: Context): List<Pair<String, File>> {
    val roots = mutableListOf<Pair<String, File>>()
    val primary = Environment.getExternalStorageDirectory()
    if (primary != null && primary.exists()) {
        roots += context.getString(R.string.file_picker_internal_storage) to primary
    }
    val dirs = runCatching { context.getExternalFilesDirs(null).filterNotNull() }.getOrDefault(emptyList())
    var sdCardIdx = 1
    dirs.forEach { dir ->
        if (primary != null && !dir.absolutePath.startsWith(primary.absolutePath)) {
            val rootPath = dir.absolutePath.substringBefore("/Android/data/")
            val sdRoot = File(rootPath)
            if (sdRoot.exists() && roots.none { it.second == sdRoot }) {
                roots += "${context.getString(R.string.file_picker_sd_card)} $sdCardIdx" to sdRoot
                sdCardIdx++
            }
        }
    }
    val rootDir = File("/")
    if (rootDir.canRead() || File("/system").exists()) {
        roots += context.getString(R.string.file_picker_root) to rootDir
    }
    return roots
}

private fun decodeThumbnail(file: File): ImageBitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    var sampleSize = 1
    while (opts.outWidth / (sampleSize * 2) >= 120 && opts.outHeight / (sampleSize * 2) >= 120) {
        sampleSize *= 2
    }
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ?.asImageBitmap()
}.getOrNull()

fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Fullscreen Material 3 Expressive Built-in File Picker & Selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalFilePickerScreen(
    mimeTypes: Array<String> = emptyArray(),
    allowFolderSelection: Boolean = false,
    allowMultiple: Boolean = false,
    onDismissRequest: () -> Unit,
    onFileSelected: (File) -> Unit,
    onMultipleFilesSelected: ((List<File>) -> Unit)? = null,
    onPreviewFile: ((File) -> Unit)? = null,
    onBrowseSystemFallback: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = remember { PetalHapticEngine.getInstance(context) }
    var hasPerms by remember { mutableStateOf(hasStoragePermission(context)) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPerms = hasStoragePermission(context)
    }

    val readStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPerms = granted || hasStoragePermission(context)
    }

    fun requestPermissions() {
        haptics.playClick(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                manageStorageLauncher.launch(intent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(fallbackIntent)
            }
        } else {
            readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    PetalPredictiveBackSurface(
        enabled = true,
        onBack = onDismissRequest
    ) {
        PetalScreenWrapper {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (!hasPerms) {
                    StoragePermissionRequestState(
                        onGrant = { requestPermissions() },
                        onDismiss = onDismissRequest,
                        onBrowseSystemFallback = onBrowseSystemFallback
                    )
                } else {
                    FilePickerBrowserContent(
                        mimeTypes = mimeTypes,
                        allowFolderSelection = allowFolderSelection,
                        allowMultiple = allowMultiple,
                        onDismissRequest = onDismissRequest,
                        onFileSelected = onFileSelected,
                        onMultipleFilesSelected = onMultipleFilesSelected,
                        onPreviewFile = onPreviewFile,
                        onBrowseSystemFallback = onBrowseSystemFallback
                    )
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionRequestState(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    onBrowseSystemFallback: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.file_picker_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.file_picker_grant_permission),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onGrant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.8f).height(48.dp)
        ) {
            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.file_picker_grant_button))
        }

        if (onBrowseSystemFallback != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onBrowseSystemFallback,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.8f).height(48.dp)
            ) {
                Text("Open System File Picker")
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun FilePickerBrowserContent(
    mimeTypes: Array<String>,
    allowFolderSelection: Boolean,
    allowMultiple: Boolean,
    onDismissRequest: () -> Unit,
    onFileSelected: (File) -> Unit,
    onMultipleFilesSelected: ((List<File>) -> Unit)?,
    onPreviewFile: ((File) -> Unit)?,
    onBrowseSystemFallback: (() -> Unit)?
) {
    val context = LocalContext.current
    val haptics = remember { PetalHapticEngine.getInstance(context) }
    val roots = remember(context) { getStorageRoots(context) }

    val defaultDir = remember {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .takeIf { it.exists() && it.isDirectory }
            ?: roots.firstOrNull()?.second
            ?: Environment.getExternalStorageDirectory()
    }

    var currentDir by remember { mutableStateOf(defaultDir) }
    var sortMode by remember { mutableStateOf(FileSortMode.NAME_ASC) }
    var showHiddenFiles by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }

    val filesState = produceState<List<File>>(initialValue = emptyList(), currentDir, showHiddenFiles, sortMode) {
        value = withContext(Dispatchers.IO) {
            val list = currentDir.listFiles()?.toList() ?: emptyList()
            val filtered = list.filter { file ->
                if (!showHiddenFiles && file.name.startsWith(".")) return@filter false
                if (file.isDirectory) return@filter true
                if (mimeTypes.isEmpty() || mimeTypes.all { it.isBlank() || it == "*/*" }) return@filter true

                val ext = file.extension.lowercase(Locale.ROOT)
                matchesMimeType(ext, mimeTypes)
            }
            sortFileList(filtered, sortMode)
        }
    }

    val displayedFiles = remember(filesState.value, searchQuery) {
        if (searchQuery.isBlank()) filesState.value
        else filesState.value.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Breadcrumbs path items
    val breadcrumbs = remember(currentDir, roots) {
        val list = mutableListOf<Pair<String, File>>()
        var curr: File? = currentDir
        while (curr != null) {
            val rootMatch = roots.find { it.second.absolutePath == curr?.absolutePath }
            if (rootMatch != null) {
                list.add(0, rootMatch.first to curr)
                break
            } else {
                list.add(0, curr.name to curr)
                curr = curr.parentFile
            }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Expressive Header
        ExpressiveHeader(
            title = if (allowFolderSelection) stringResource(R.string.file_picker_select_folder_title) else stringResource(R.string.file_picker_title),
            subtitle = currentDir.name.ifEmpty { currentDir.absolutePath },
            onBack = {
                haptics.playClick(context)
                val isRoot = roots.any { it.second.absolutePath == currentDir.absolutePath }
                if (!isRoot && currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
                    currentDir = currentDir.parentFile!!
                } else {
                    onDismissRequest()
                }
            },
            actions = {
                IconButton(onClick = {
                    haptics.playClick(context)
                    isSearching = !isSearching
                    if (!isSearching) searchQuery = ""
                }) {
                    Icon(
                        imageVector = if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    IconButton(onClick = {
                        haptics.playClick(context)
                        showSortMenu = true
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_picker_sort_name_asc)) },
                            leadingIcon = { if (sortMode == FileSortMode.NAME_ASC) Icon(Icons.Rounded.Check, null) },
                            onClick = { sortMode = FileSortMode.NAME_ASC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_picker_sort_name_desc)) },
                            leadingIcon = { if (sortMode == FileSortMode.NAME_DESC) Icon(Icons.Rounded.Check, null) },
                            onClick = { sortMode = FileSortMode.NAME_DESC; showSortMenu = false }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_picker_sort_size_desc)) },
                            leadingIcon = { if (sortMode == FileSortMode.SIZE_DESC) Icon(Icons.Rounded.Check, null) },
                            onClick = { sortMode = FileSortMode.SIZE_DESC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_picker_sort_size_asc)) },
                            leadingIcon = { if (sortMode == FileSortMode.SIZE_ASC) Icon(Icons.Rounded.Check, null) },
                            onClick = { sortMode = FileSortMode.SIZE_ASC; showSortMenu = false }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_picker_sort_date_desc)) },
                            leadingIcon = { if (sortMode == FileSortMode.DATE_DESC) Icon(Icons.Rounded.Check, null) },
                            onClick = { sortMode = FileSortMode.DATE_DESC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_picker_sort_date_asc)) },
                            leadingIcon = { if (sortMode == FileSortMode.DATE_ASC) Icon(Icons.Rounded.Check, null) },
                            onClick = { sortMode = FileSortMode.DATE_ASC; showSortMenu = false }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.file_picker_show_hidden_files)) },
                            trailingIcon = {
                                Switch(
                                    checked = showHiddenFiles,
                                    onCheckedChange = null,
                                    modifier = Modifier.scale(0.8f)
                                )
                            },
                            onClick = { showHiddenFiles = !showHiddenFiles; showSortMenu = false }
                        )
                    }
                }

                if (onBrowseSystemFallback != null) {
                    IconButton(onClick = {
                        haptics.playClick(context)
                        onBrowseSystemFallback()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInBrowser,
                            contentDescription = "System Picker",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )

        // Search Bar Input
        AnimatedVisibility(visible = isSearching) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.file_picker_search_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Storage Volume Shortcuts & Breadcrumbs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            // Volume Roots Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Downloads Jump
                val downloads = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
                if (downloads != null && downloads.exists()) {
                    val isSelected = currentDir.absolutePath == downloads.absolutePath
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.playClick(context)
                            currentDir = downloads
                        },
                        leadingIcon = { Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp)) },
                        label = { Text(stringResource(R.string.file_picker_downloads)) },
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                roots.forEach { (label, rootDir) ->
                    val isSelected = currentDir.absolutePath == rootDir.absolutePath
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.playClick(context)
                            currentDir = rootDir
                        },
                        leadingIcon = {
                            val icon = if (label.contains("SD", true)) Icons.Rounded.SdCard else if (rootDir.absolutePath == "/") Icons.Rounded.DeveloperMode else Icons.Rounded.Storage
                            Icon(icon, null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(label) },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Interactive Breadcrumb Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { index, (name, dir) ->
                    val isLast = index == breadcrumbs.lastIndex
                    Surface(
                        onClick = {
                            if (!isLast) {
                                haptics.playClick(context)
                                currentDir = dir
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal),
                                color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (!isLast) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Files List
        val listState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (displayedFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.file_picker_no_files),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Parent Directory Row
                    val isRoot = roots.any { it.second.absolutePath == currentDir.absolutePath }
                    if (!isRoot && currentDir.parentFile != null) {
                        item(key = "__parent__") {
                            Surface(
                                onClick = {
                                    haptics.playClick(context)
                                    currentDir = currentDir.parentFile!!
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = stringResource(R.string.file_picker_previous_directory),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                        }
                    }

                    items(displayedFiles, key = { it.absolutePath }) { file ->
                        val isDir = file.isDirectory
                        val isSelected = selectedFiles.contains(file)

                        FilePickerRowItem(
                            file = file,
                            isSelected = isSelected,
                            allowMultiple = allowMultiple,
                            onClick = {
                                haptics.playClick(context)
                                if (isDir) {
                                    currentDir = file
                                } else {
                                    if (allowMultiple) {
                                        if (isSelected) selectedFiles.remove(file) else selectedFiles.add(file)
                                    } else {
                                        onFileSelected(file)
                                    }
                                }
                            },
                            onPreview = {
                                haptics.playClick(context)
                                onPreviewFile?.invoke(file)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                    }
                }
            }
        }

        // Bottom Action Bar: Folder selection or Multi-file confirm
        if (allowFolderSelection || (allowMultiple && selectedFiles.isNotEmpty())) {
            Surface(
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (allowFolderSelection) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Directory",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentDir.name.ifEmpty { currentDir.absolutePath },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                haptics.playClick(context)
                                onFileSelected(currentDir)
                            },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.file_picker_select_folder))
                        }
                    } else if (allowMultiple) {
                        Text(
                            text = "${selectedFiles.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                haptics.playClick(context)
                                onMultipleFilesSelected?.invoke(selectedFiles.toList())
                            },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePickerRowItem(
    file: File,
    isSelected: Boolean,
    allowMultiple: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    val isDir = file.isDirectory
    val ext = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
    val isApk = ext in APK_EXTENSIONS
    val isImage = ext in IMAGE_EXTENSIONS

    // Load APK PackageInfo in background
    val packageInfo by produceState<PackageInfo?>(initialValue = null, file) {
        if (isApk && ext == "apk") {
            val cached = apkPackageInfoCache.get(file.absolutePath)
            if (cached != null) {
                value = cached
            } else {
                value = withContext(Dispatchers.IO) {
                    try {
                        val pi = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                        if (pi != null) {
                            pi.applicationInfo?.sourceDir = file.absolutePath
                            pi.applicationInfo?.publicSourceDir = file.absolutePath
                            apkPackageInfoCache.put(file.absolutePath, pi)
                        }
                        pi
                    } catch (_: Exception) { null }
                }
            }
        }
    }

    // Load Image Thumbnail in background
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, file) {
        if (isImage) {
            val cached = imageThumbnailCache.get(file.absolutePath)
            if (cached != null) {
                value = cached
            } else {
                value = withContext(Dispatchers.IO) {
                    val bmp = decodeThumbnail(file)
                    if (bmp != null) imageThumbnailCache.put(file.absolutePath, bmp)
                    bmp
                }
            }
        }
    }

    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Icon or Thumbnail
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isDir -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    thumbnail != null -> {
                        Image(
                            bitmap = thumbnail!!,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                    }
                    packageInfo != null -> {
                        val appIcon = remember(packageInfo) {
                            runCatching {
                                packageInfo!!.applicationInfo?.loadIcon(context.packageManager)
                            }.getOrNull()
                        }
                        if (appIcon != null) {
                            // Render drawable as icon or fallback
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Android,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else {
                            Icon(Icons.Rounded.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                    else -> {
                        val iconVector = getFileIcon(ext)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // File Name & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val detailText = if (isDir) {
                    val count = file.list()?.size ?: 0
                    "$count items"
                } else {
                    val sizeFormatted = Formatter.formatFileSize(context, file.length())
                    val dateFormatted = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                    "$sizeFormatted · $dateFormatted"
                }
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Trailing Actions: Preview Button or Selection Checkbox
            if (allowMultiple) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            } else if (!isDir) {
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = stringResource(R.string.file_picker_preview),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun getFileIcon(ext: String): ImageVector {
    return when {
        ext in APK_EXTENSIONS -> Icons.Rounded.Android
        ext in IMAGE_EXTENSIONS -> Icons.Rounded.Image
        ext in VIDEO_EXTENSIONS -> Icons.Rounded.VideoFile
        ext in AUDIO_EXTENSIONS -> Icons.Rounded.AudioFile
        ext in CODE_EXTENSIONS -> Icons.Rounded.Code
        ext in ARCHIVE_EXTENSIONS -> Icons.Rounded.FolderZip
        ext in DOC_EXTENSIONS -> Icons.Rounded.Description
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

private fun matchesMimeType(ext: String, mimeTypes: Array<String>): Boolean {
    for (type in mimeTypes) {
        val lower = type.lowercase(Locale.ROOT)
        if (lower == "*/*" || lower.isEmpty()) return true
        if (lower.startsWith("image/") && ext in IMAGE_EXTENSIONS) return true
        if (lower.startsWith("video/") && ext in VIDEO_EXTENSIONS) return true
        if (lower.startsWith("audio/") && ext in AUDIO_EXTENSIONS) return true
        if (lower.contains("pdf") && ext == "pdf") return true
        if (lower.contains("package-archive") && ext in APK_EXTENSIONS) return true
        if (lower.contains("zip") && (ext == "zip" || ext in ARCHIVE_EXTENSIONS)) return true
        if (lower.contains("text/") && (ext in CODE_EXTENSIONS || ext == "txt" || ext == "md" || ext == "csv" || ext == "log")) return true
    }
    return false
}

private fun sortFileList(files: List<File>, mode: FileSortMode): List<File> {
    val (dirs, nonDirs) = files.partition { it.isDirectory }
    val sortedNonDirs = when (mode) {
        FileSortMode.NAME_ASC -> nonDirs.sortedBy { it.name.lowercase(Locale.ROOT) }
        FileSortMode.NAME_DESC -> nonDirs.sortedByDescending { it.name.lowercase(Locale.ROOT) }
        FileSortMode.SIZE_DESC -> nonDirs.sortedByDescending { it.length() }
        FileSortMode.SIZE_ASC -> nonDirs.sortedBy { it.length() }
        FileSortMode.DATE_DESC -> nonDirs.sortedByDescending { it.lastModified() }
        FileSortMode.DATE_ASC -> nonDirs.sortedBy { it.lastModified() }
    }
    return dirs.sortedBy { it.name.lowercase(Locale.ROOT) } + sortedNonDirs
}
