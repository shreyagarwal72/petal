/*
 * SafeLockerScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Full-page Material 3 Expressive Biometric Safe Locker Screen for Petal Browser.
 * Built with Predictive Back support, hardware-backed Biometric Authentication
 * (gated like official Firefox for Android logins/vault workflow), categorization,
 * search, multi-selection, file viewing, and encrypted storage in private filesDir.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.privacy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalThemedSnackbarHost
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SafeLockerBridge {
    @JvmStatic
    fun createSafeLockerView(activity: ComponentActivity, onBackPress: () -> Unit): android.view.View {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember {
                    com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap()
                }
                DisposableEffect(Unit) {
                    onDispose { com.petal.browser.predictive.PetalContentSnapshot.clear() }
                }
                val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    SafeLockerScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onBackPress = onBackPress
                    )
                }
            }
        }
    }
}

enum class SafeLockerCategory(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Rounded.AllInclusive),
    IMAGES("Images", Icons.Rounded.Image),
    VIDEOS("Videos", Icons.Rounded.Movie),
    AUDIO("Audio", Icons.Rounded.GraphicEq),
    DOCS("Docs", Icons.Rounded.Description),
    ARCHIVES("Archives", Icons.Rounded.FolderZip),
    OTHER("Other", Icons.Rounded.InsertDriveFile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeLockerScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val lockerManager = remember { SafeLockerManager(context) }
    val lockedFiles by lockerManager.lockedFiles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isUnlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf(SafeLockerCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedFileIds.isNotEmpty()

    var showSafeLockerPicker by remember { mutableStateOf(false) }
    val systemSafeLockerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            var name = "file_${System.currentTimeMillis()}"
            context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) name = cursor.getString(idx)
                }
            }
            scope.launch {
                val success = lockerManager.importFile(it, name)
                if (success) {
                    snackbarHostState.showSnackbar("Imported $name into Safe Locker")
                } else {
                    snackbarHostState.showSnackbar("Failed to import file")
                }
            }
        }
    }

    // Trigger biometric authentication upon page entry (matches official Firefox pattern)
    LaunchedEffect(Unit) {
        if (activity != null) {
            lockerManager.authenticate(
                activity,
                onSuccess = {
                    isUnlocked = true
                    authError = null
                },
                onError = { err ->
                    authError = err
                }
            )
        } else {
            isUnlocked = true
        }
    }

    // Filter files by category and search query
    val filteredFiles = remember(lockedFiles, selectedCategory, searchQuery) {
        lockedFiles.filter { item ->
            val matchesCategory = when (selectedCategory) {
                SafeLockerCategory.ALL -> true
                SafeLockerCategory.IMAGES -> item.mimeType.startsWith("image/")
                SafeLockerCategory.VIDEOS -> item.mimeType.startsWith("video/")
                SafeLockerCategory.AUDIO -> item.mimeType.startsWith("audio/")
                SafeLockerCategory.DOCS -> item.mimeType.contains("pdf") || item.mimeType.contains("document") || item.mimeType.contains("text")
                SafeLockerCategory.ARCHIVES -> item.mimeType.contains("zip") || item.mimeType.contains("tar") || item.mimeType.contains("rar") || item.mimeType.contains("compressed")
                SafeLockerCategory.OTHER -> !item.mimeType.startsWith("image/") &&
                        !item.mimeType.startsWith("video/") &&
                        !item.mimeType.startsWith("audio/") &&
                        !item.mimeType.contains("pdf") &&
                        !item.mimeType.contains("document") &&
                        !item.mimeType.contains("zip")
            }
            val matchesQuery = searchQuery.isBlank() || item.name.contains(searchQuery.trim(), ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    fun deleteSelected() {
        val targets = lockedFiles.filter { selectedFileIds.contains(it.id) }
        selectedFileIds = emptySet()
        scope.launch {
            var count = 0
            for (target in targets) {
                if (lockerManager.deleteFile(target)) count++
            }
            snackbarHostState.showSnackbar("Deleted $count file(s)")
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = {
            if (isSelectionMode) {
                selectedFileIds = emptySet()
            } else if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
            } else {
                onBackPress()
            }
        }
    ) {
        com.petal.browser.predictive.PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    PetalThemedSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    M3ExpressiveVariableBackground(pageSeed = "safe_locker_page")

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        if (isSelectionMode) {
                            ExpressiveHeader(
                                title = "${selectedFileIds.size} Selected",
                                subtitle = "Safe Locker selection",
                                onBack = { selectedFileIds = emptySet() },
                                actions = {
                                    HeaderActionIcon(
                                        icon = if (selectedFileIds.size == filteredFiles.size) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                        contentDescription = "Select All",
                                        onClick = {
                                            selectedFileIds = if (selectedFileIds.size == filteredFiles.size) {
                                                emptySet()
                                            } else {
                                                filteredFiles.map { it.id }.toSet()
                                            }
                                        }
                                    )
                                    HeaderActionIcon(
                                        icon = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        onClick = { deleteSelected() }
                                    )
                                }
                            )
                        } else {
                            ExpressiveHeader(
                                title = "Safe Locker",
                                subtitle = if (isUnlocked) "${lockedFiles.size} encrypted files" else "Biometric Protected",
                                onBack = onBackPress,
                                actions = {
                                    if (isUnlocked) {
                                        HeaderActionIcon(
                                            icon = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                            contentDescription = "Search",
                                            onClick = {
                                                isSearchActive = !isSearchActive
                                                if (!isSearchActive) searchQuery = ""
                                            }
                                        )
                                        HeaderActionIcon(
                                            icon = Icons.Rounded.Add,
                                            contentDescription = "Import File",
                                            onClick = { showSafeLockerPicker = true }
                                        )
                                        HeaderActionIcon(
                                            icon = Icons.Rounded.Lock,
                                            contentDescription = "Lock Vault",
                                            onClick = {
                                                isUnlocked = false
                                                authError = null
                                            }
                                        )
                                    }
                                }
                            )
                        }

                        // Search Bar (Animated visibility)
                        AnimatedVisibility(visible = isSearchActive && isUnlocked) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search encrypted files...") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (!isUnlocked) {
                            // Biometric Locked / Challenge View
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(96.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Fingerprint,
                                                contentDescription = null,
                                                modifier = Modifier.size(54.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        "Safe Locker is Locked",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        authError ?: "Authenticate using your fingerprint, face, or device PIN/pattern to access your private encrypted vault.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (authError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (activity != null) {
                                                lockerManager.authenticate(
                                                    activity,
                                                    onSuccess = {
                                                        isUnlocked = true
                                                        authError = null
                                                    },
                                                    onError = { authError = it }
                                                )
                                            } else {
                                                isUnlocked = true
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                    ) {
                                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Unlock Vault")
                                    }
                                }
                            }
                        } else {
                            // Unlocked Content: Category Filter Chips
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(SafeLockerCategory.values()) { category ->
                                    val isSelected = selectedCategory == category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCategory = category },
                                        label = { Text(category.label) },
                                        leadingIcon = {
                                            Icon(
                                                category.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }

                            if (filteredFiles.isEmpty()) {
                                // Empty state
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (searchQuery.isNotEmpty()) Icons.Rounded.SearchOff else Icons.Rounded.FolderOff,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(36.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            if (searchQuery.isNotEmpty()) "No matching files" else "Safe Locker is empty",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            if (searchQuery.isNotEmpty()) "Try a different search term" else "Import private downloads, images, or documents into your hardware-encrypted vault",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        if (searchQuery.isEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            FilledTonalButton(
                                                onClick = { showSafeLockerPicker = true },
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Import File")
                                            }
                                        }
                                    }
                                }
                            } else {
                                // File List
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredFiles, key = { it.id }) { item ->
                                        val isSelected = selectedFileIds.contains(item.id)
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected)
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                else
                                                    MaterialTheme.colorScheme.surfaceContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (isSelectionMode) {
                                                            selectedFileIds = if (isSelected) {
                                                                selectedFileIds - item.id
                                                            } else {
                                                                selectedFileIds + item.id
                                                            }
                                                        } else {
                                                            openSafeLockerFile(context, item)
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                // Icon or selection indicator
                                                if (isSelectionMode) {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = { checked ->
                                                            selectedFileIds = if (checked) {
                                                                selectedFileIds + item.id
                                                            } else {
                                                                selectedFileIds - item.id
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    Surface(
                                                        shape = RoundedCornerShape(14.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        modifier = Modifier.size(46.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            val icon = when {
                                                                item.mimeType.startsWith("image/") -> Icons.Rounded.Image
                                                                item.mimeType.startsWith("video/") -> Icons.Rounded.Movie
                                                                item.mimeType.startsWith("audio/") -> Icons.Rounded.GraphicEq
                                                                item.mimeType.contains("pdf") -> Icons.Rounded.PictureAsPdf
                                                                item.mimeType.contains("zip") || item.mimeType.contains("tar") -> Icons.Rounded.FolderZip
                                                                else -> Icons.Rounded.Description
                                                            }
                                                            Icon(
                                                                icon,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                // Name, Size & Date
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        item.name,
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        "${formatSafeFileSize(item.sizeBytes)} • ${formatSafeFileDate(item.modifiedTime)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                // Item trailing action
                                                if (!isSelectionMode) {
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            val success = lockerManager.deleteFile(item)
                                                            if (success) {
                                                                snackbarHostState.showSnackbar("Deleted ${item.name}")
                                                            }
                                                        }
                                                    }) {
                                                        Icon(
                                                            Icons.Rounded.DeleteOutline,
                                                            contentDescription = "Delete",
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
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
                }
            }
        }
    }

    if (showSafeLockerPicker) {
        com.petal.browser.compose.file.PetalFilePickerScreen(
            mimeTypes = arrayOf("*/*"),
            onDismissRequest = { showSafeLockerPicker = false },
            onFileSelected = { file ->
                showSafeLockerPicker = false
                scope.launch {
                    val success = lockerManager.importFile(Uri.fromFile(file), file.name)
                    if (success) {
                        snackbarHostState.showSnackbar("Imported ${file.name}")
                    }
                }
            },
            onBrowseSystemFallback = {
                showSafeLockerPicker = false
                systemSafeLockerPicker.launch("*/*")
            }
        )
    }
}

private fun openSafeLockerFile(context: Context, item: LockedFileItem) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, item.file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open file"))
    } catch (e: Exception) {
        PetalToast.show(context, "Cannot open file: ${e.message}", android.widget.Toast.LENGTH_SHORT)
    }
}

private fun formatSafeFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatSafeFileDate(timestampMs: Long): String {
    if (timestampMs <= 0) return ""
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}
