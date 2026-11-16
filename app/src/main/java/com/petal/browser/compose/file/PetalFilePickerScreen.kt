/*
 * PetalFilePickerScreen.kt
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
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.PetalButtonGroupItem
import com.petal.browser.ui.components.PetalChoiceButtonGroup
import com.petal.browser.ui.glass.LocalPetalGlassWallpaper
import com.petal.browser.ui.glass.rememberPetalGlassWallpaper

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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.PetalPopupMenu
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
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

    // Same wiring as the other full-screen pages: reuse the ambient glass wallpaper if the theme
    // provides one, otherwise build one so header, segmented groups and rows get real refraction.
    val wallpaper = LocalPetalGlassWallpaper.current ?: rememberPetalGlassWallpaper()

    PetalPredictiveBackSurface(
        enabled = true,
        onBack = onDismissRequest
    ) {
        PetalScreenWrapper {
          CompositionLocalProvider(LocalPetalGlassWallpaper provides wallpaper) {
            Box(modifier = Modifier.fillMaxSize()) {
                M3ExpressiveVariableBackground(
                    modifier = Modifier.fillMaxSize(),
                    pageSeed = "petal_file_picker"
                )
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
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
            shape = ExpressiveButtonShape,
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
        // M3E: full-width pill actions that morph on press (kit buttons own the shape + glass).
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.file_picker_grant_button), style = MaterialTheme.typography.titleMedium)
        }

        if (onBrowseSystemFallback != null) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onBrowseSystemFallback,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.ui_open_system_file_picker), style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.ui_cancel))
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

    val isRoot = roots.any { it.second.absolutePath == currentDir.absolutePath }
    val canNavigateUp = !isRoot && currentDir.parentFile != null && currentDir.parentFile?.canRead() == true
    val hasSubOverlay = showSortMenu || isSearching || searchQuery.isNotEmpty() || selectedFiles.isNotEmpty() || canNavigateUp

    androidx.activity.compose.BackHandler(enabled = hasSubOverlay) {
        haptics.playClick(context)
        when {
            showSortMenu -> showSortMenu = false
            selectedFiles.isNotEmpty() -> selectedFiles.clear()
            isSearching || searchQuery.isNotEmpty() -> {
                isSearching = false
                searchQuery = ""
            }
            canNavigateUp -> currentDir = currentDir.parentFile!!
            else -> onDismissRequest()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Expressive Header
        ExpressiveHeader(
            title = if (allowFolderSelection) stringResource(R.string.file_picker_select_folder_title) else stringResource(R.string.file_picker_title),
            subtitle = currentDir.name.ifEmpty { currentDir.absolutePath },
            onBack = {
                haptics.playClick(context)
                if (showSortMenu) {
                    showSortMenu = false
                } else if (selectedFiles.isNotEmpty()) {
                    selectedFiles.clear()
                } else if (isSearching || searchQuery.isNotEmpty()) {
                    isSearching = false
                    searchQuery = ""
                } else if (canNavigateUp) {
                    currentDir = currentDir.parentFile!!
                } else {
                    onDismissRequest()
                }
            },
            actions = {
                // Only two header actions so the title keeps its width ("Select File" no longer
                // truncates). The system picker moved into the sort/options menu.
                HeaderActionIcon(
                    icon = if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.ui_search),
                    onClick = {
                        haptics.playClick(context)
                        isSearching = !isSearching
                        if (!isSearching) searchQuery = ""
                    },
                    emphasized = isSearching
                )

                Box {
                    HeaderActionIcon(
                        icon = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.ui_sort),
                        onClick = {
                            haptics.playClick(context)
                            showSortMenu = true
                        },
                        emphasized = showSortMenu
                    )

                    PetalPopupMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        @Composable
                        fun SortItem(mode: FileSortMode, labelRes: Int) {
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = stringResource(labelRes),
                                leadingIcon = { if (sortMode == mode) Icon(Icons.Rounded.Check, null) },
                                onClick = { sortMode = mode; showSortMenu = false }
                            )
                        }
                        SortItem(FileSortMode.NAME_ASC, R.string.file_picker_sort_name_asc)
                        SortItem(FileSortMode.NAME_DESC, R.string.file_picker_sort_name_desc)
                        com.petal.browser.ui.containment.PetalMenuDivider()
                        SortItem(FileSortMode.SIZE_DESC, R.string.file_picker_sort_size_desc)
                        SortItem(FileSortMode.SIZE_ASC, R.string.file_picker_sort_size_asc)
                        com.petal.browser.ui.containment.PetalMenuDivider()
                        SortItem(FileSortMode.DATE_DESC, R.string.file_picker_sort_date_desc)
                        SortItem(FileSortMode.DATE_ASC, R.string.file_picker_sort_date_asc)
                        com.petal.browser.ui.containment.PetalMenuDivider()
                        com.petal.browser.ui.containment.PetalPopupMenuItem(
                            text = { Text(stringResource(R.string.file_picker_show_hidden_files), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) },
                            trailingIcon = {
                                IconSwitch(
                                    checked = showHiddenFiles,
                                    icon = Icons.Rounded.Check,
                                    onCheckedChange = null,
                                    modifier = Modifier.scale(0.8f)
                                )
                            },
                            onClick = { showHiddenFiles = !showHiddenFiles; showSortMenu = false }
                        )
                        if (onBrowseSystemFallback != null) {
                            com.petal.browser.ui.containment.PetalMenuDivider()
                            com.petal.browser.ui.containment.PetalPopupMenuItem(
                                text = stringResource(R.string.ui_system_picker),
                                leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                                onClick = { showSortMenu = false; onBrowseSystemFallback() }
                            )
                        }
                    }
                }
            }
        )

        // Search Bar Input
        AnimatedVisibility(visible = isSearching) {
            val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar") || isLiquidGlassEnabled("sp_liquid_glass_containments")
            val pickerSearchShape = RoundedCornerShape(16.dp)
            Surface(
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .liquidGlassChrome(shape = pickerSearchShape, enabled = isGlass, sheenIntensity = 0.45f),
                shape = pickerSearchShape
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.file_picker_search_hint)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Storage volumes as one M3E connected (segmented) button group + a single breadcrumb pill.
        val downloadsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
        val downloadsLabel = stringResource(R.string.file_picker_downloads)
        val volumeOptions = remember(roots, downloadsDir, downloadsLabel) {
            buildList {
                if (downloadsDir != null && downloadsDir.exists()) {
                    add(PetalButtonGroupItem(downloadsDir.absolutePath, downloadsLabel, icon = Icons.Rounded.Download))
                }
                roots.forEach { (label, rootDir) ->
                    val icon = when {
                        label.contains("SD", true) -> Icons.Rounded.SdCard
                        rootDir.absolutePath == "/" -> Icons.Rounded.DeveloperMode
                        else -> Icons.Rounded.Storage
                    }
                    add(PetalButtonGroupItem(rootDir.absolutePath, label, icon = icon))
                }
            }
        }
        val volumePaths = remember(volumeOptions) { volumeOptions.map { it.value }.toSet() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (volumeOptions.isNotEmpty()) {
                PetalChoiceButtonGroup(
                    options = volumeOptions,
                    // Not a volume root (we're inside a subfolder) -> nothing selected, all segments neutral.
                    selected = currentDir.absolutePath.takeIf { it in volumePaths } ?: "",
                    onSelect = { path ->
                        haptics.playClick(context)
                        currentDir = File(path)
                    }
                )
            }
            PickerBreadcrumbs(
                crumbs = breadcrumbs,
                onNavigate = { dir ->
                    haptics.playClick(context)
                    currentDir = dir
                }
            )
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
                    // Clears the gesture/nav bar so the last row is never cut off; no scrollbar overlay.
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Parent Directory Row
                    val isRoot = roots.any { it.second.absolutePath == currentDir.absolutePath }
                    if (!isRoot && currentDir.parentFile != null) {
                        item(key = "__parent__") {
                            PetalGroupListRow(
                                position = PetalGroupPosition.SINGLE,
                                onClick = {
                                    haptics.playClick(context)
                                    currentDir = currentDir.parentFile!!
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp),
                                leading = {
                                    PetalGroupIconBadge(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        container = MaterialTheme.colorScheme.secondaryContainer,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                },
                                content = {
                                    Text(
                                        text = stringResource(R.string.file_picker_previous_directory),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            )
                        }
                    }

                    itemsIndexed(displayedFiles, key = { _, file -> file.absolutePath }) { index, file ->
                        val isDir = file.isDirectory
                        val isSelected = selectedFiles.contains(file)
                        val position = petalGroupPositionFor(index, displayedFiles.size)

                        FilePickerRowItem(
                            file = file,
                            isSelected = isSelected,
                            allowMultiple = allowMultiple,
                            position = position,
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
                    }
                }
            }
        }

        // Bottom Action Bar: Folder selection or Multi-file confirm
        if (allowFolderSelection || (allowMultiple && selectedFiles.isNotEmpty())) {
            val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
            Surface(
                tonalElevation = 6.dp,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassChrome(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), enabled = isGlass)
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
                                text = stringResource(R.string.ui_current_directory),
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
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.file_picker_select_folder))
                        }
                    } else if (allowMultiple) {
                        Text(
                            text = stringResource(R.string.ui_selected_2, selectedFiles.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                haptics.playClick(context)
                                onMultipleFilesSelected?.invoke(selectedFiles.toList())
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.ui_confirm))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Breadcrumb trail as ONE rounded container with chevron separators (not a row of mini chips).
 * Auto-scrolls to the current folder; the last crumb is emphasised, the rest are tappable.
 */
@Composable
private fun PickerBreadcrumbs(
    crumbs: List<Pair<String, File>>,
    onNavigate: (File) -> Unit,
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val shape = RoundedCornerShape(50)
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs) { scroll.animateScrollTo(scroll.maxValue) }

    Surface(
        shape = shape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            crumbs.forEachIndexed { index, (name, dir) ->
                val isLast = index == crumbs.lastIndex
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !isLast) { onNavigate(dir) }
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                if (!isLast) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
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
    position: PetalGroupPosition,
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

    val detailText = if (isDir) {
        "${file.list()?.size ?: 0} items"
    } else {
        val sizeFormatted = Formatter.formatFileSize(context, file.length())
        val dateFormatted = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        "$sizeFormatted · $dateFormatted"
    }

    PetalGroupListRow(
        position = position,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp),
        selected = isSelected,
        leading = {
            when {
                isDir -> PetalGroupIconBadge(
                    Icons.Rounded.Folder,
                    variant = PetalBadgeVariant.SECONDARY
                )
                thumbnail != null -> Image(
                    bitmap = thumbnail!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)),
                )
                packageInfo != null -> {
                    PetalGroupIconBadge(
                        Icons.Rounded.Android,
                        variant = PetalBadgeVariant.PRIMARY
                    )
                }
                ext in AUDIO_EXTENSIONS || ext in VIDEO_EXTENSIONS || ext in IMAGE_EXTENSIONS -> PetalGroupIconBadge(
                    getFileIcon(ext),
                    variant = PetalBadgeVariant.TERTIARY
                )
                else -> PetalGroupIconBadge(
                    getFileIcon(ext),
                    variant = PetalBadgeVariant.SURFACE_TONAL
                )
            }
        },
        content = {
            Text(file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detailText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailing = {
            if (allowMultiple) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            } else if (!isDir) {
                IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Visibility, contentDescription = stringResource(R.string.file_picker_preview), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        },
    )
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
