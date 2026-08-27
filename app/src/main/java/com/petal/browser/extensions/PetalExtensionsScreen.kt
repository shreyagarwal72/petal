package com.petal.browser.extensions

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalExtensionsScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var extensionsList by remember { mutableStateOf(PetalExtensionManager.getInstalledExtensions(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val crxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val success = PetalExtensionManager.installExtensionFromUri(context, uri)
            if (success) {
                extensionsList = PetalExtensionManager.getInstalledExtensions(context)
                statusMessage = "Extension installed successfully!"
            } else {
                statusMessage = "Failed to install extension."
            }
        }
    }

    val filteredExtensions = remember(searchQuery, extensionsList) {
        extensionsList.filter { ext ->
            searchQuery.isBlank() ||
                ext.name.contains(searchQuery, ignoreCase = true) ||
                ext.description.contains(searchQuery, ignoreCase = true)
        }
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onDismiss,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper(isBehind = true, backgroundSnapshot = backgroundSnapshot) {
    Scaffold(
        topBar = {
            com.petal.browser.ui.components.ExpressiveHeader(
                title = "Chrome Extensions",
                subtitle = "petal://extensions • chrome://extensions",
                onBack = onDismiss,
                actions = {
                    FilledTonalButton(
                        onClick = { crxLauncher.launch("*/*") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Install .CRX", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search extensions...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )

            statusMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredExtensions, key = { it.id }) { ext ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Extension,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            ext.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            "v${ext.version}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                IconSwitch(
                                    checked = ext.enabled,
                                    icon = Icons.Rounded.Extension,
                                    onCheckedChange = { isEnabled ->
                                        PetalExtensionManager.setExtensionEnabled(context, ext.id, isEnabled)
                                        extensionsList = PetalExtensionManager.getInstalledExtensions(context)
                                    }
                                )
                            }

                            Text(
                                ext.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = {
                                        PetalExtensionManager.removeExtension(context, ext.id)
                                        extensionsList = PetalExtensionManager.getInstalledExtensions(context)
                                    }
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
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

/** Java Interop Bridge to open Chrome Extensions sheet */
object PetalExtensionsBridge {
    @JvmStatic
    fun showExtensions(activity: ComponentActivity) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.setOnShowListener {
                val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let { sheet ->
                    val behavior = BottomSheetBehavior.from(sheet)
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    behavior.skipCollapsed = true
                }
            }

            val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
            com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                    DisposableEffect(Unit) {
                        onDispose {
                            com.petal.browser.predictive.PetalContentSnapshot.clear()
                        }
                    }
                    val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                    val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        paletteId = paletteId
                    ) {
                        PetalExtensionsScreen(
                            backgroundSnapshot = snapshotBitmap,
                            onDismiss = { dialog.dismiss() }
                        )
                    }
                }
            }

            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
