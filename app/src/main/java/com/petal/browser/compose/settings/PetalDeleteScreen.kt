/*
 * PetalDeleteScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Clear Browsing Data / Delete History Screen for Petal Browser.
 * Fully follows app theme, color scheme, expressiveness, expressive feature tiles,
 * and card/containment styling.
 */

package com.petal.browser.compose.settings

import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.BrowserUnit

object PetalDeleteBridge {
    @JvmStatic
    fun createDeleteView(activity: ComponentActivity, onBackPress: Runnable): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.let { androidx.compose.ui.graphics.asImageBitmap(it) } }
                DisposableEffect(Unit) {
                    onDispose {
                        com.petal.browser.predictive.PetalContentSnapshot.clear()
                    }
                }
                val context = LocalContext.current
                val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }

                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    AppFont.fromName(fontName)
                }
                val colorStyle = remember(styleName) {
                    try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalDeleteScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onBackPress = { onBackPress.run() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalDeleteScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var isExpressiveFeatureTiles by remember { mutableStateOf(sp.getBoolean("sp_expressive_feature_tiles", true)) }

    var clearHistory by remember { mutableStateOf(sp.getBoolean("sp_clear_history", false)) }
    var clearCache by remember { mutableStateOf(sp.getBoolean("sp_clear_cache", false)) }
    var clearIndexedDB by remember { mutableStateOf(sp.getBoolean("sp_clearIndexedDB", false)) }
    var clearCookie by remember { mutableStateOf(sp.getBoolean("sp_clear_cookie", false)) }
    var clearDatabase by remember { mutableStateOf(sp.getBoolean("sp_deleteDatabase", false)) }
    var clearSettings by remember { mutableStateOf(sp.getBoolean("sp_clear_settings", false)) }
    var clearQuit by remember { mutableStateOf(sp.getBoolean("sp_clear_quit", false)) }

    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Clear Selected Browsing Data?",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This action will permanently delete the selected items. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        BrowserUnit.clearBrowserData(context)
                        com.petal.browser.view.NinjaToast.show(context, R.string.app_ok)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    com.petal.browser.predictive.PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBackPress,
    ) {
    com.petal.browser.predictive.PetalScreenWrapper(isBehind = true, backgroundSnapshot = backgroundSnapshot) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ExpressiveHeader(
                title = context.getString(R.string.menu_delete),
                subtitle = "Clear Browsing Data & History",
                onBack = onBackPress
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            M3ExpressiveVariableBackground(pageSeed = "delete_page")

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header / Summary containment card (Material 3 Expressive UI redesign)
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(24.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clear Browsing Data",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Choose items to erase. Settings apply immediately and during clear operations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Containment Card grouping clear options (Material 3 Expressive UI redesign)
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "DATA CATEGORIES",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                            )

                            DeleteOptionItem(
                                title = context.getString(R.string.album_title_history),
                                subtitle = "Clear visited web pages and address bar history",
                                icon = Icons.Rounded.History,
                                checked = clearHistory,
                                onCheckedChange = {
                                    clearHistory = it
                                    sp.edit().putBoolean("sp_clear_history", it).apply()
                                }
                            )

                            DeleteOptionItem(
                                title = context.getString(R.string.clear_title_cache),
                                subtitle = "Frees up space by clearing cached images and files",
                                icon = Icons.Rounded.Storage,
                                checked = clearCache,
                                onCheckedChange = {
                                    clearCache = it
                                    sp.edit().putBoolean("sp_clear_cache", it).apply()
                                }
                            )

                            DeleteOptionItem(
                                title = context.getString(R.string.setting_title_dom),
                                subtitle = "Local website data and offline storage",
                                icon = Icons.Rounded.FolderZip,
                                checked = clearIndexedDB,
                                onCheckedChange = {
                                    clearIndexedDB = it
                                    sp.edit().putBoolean("sp_clearIndexedDB", it).apply()
                                }
                            )

                            DeleteOptionItem(
                                title = context.getString(R.string.setting_title_cookie),
                                subtitle = context.getString(R.string.setting_summary_cookie_delete),
                                icon = Icons.Rounded.Cookie,
                                checked = clearCookie,
                                onCheckedChange = {
                                    clearCookie = it
                                    sp.edit().putBoolean("sp_clear_cookie", it).apply()
                                }
                            )

                            DeleteOptionItem(
                                title = context.getString(R.string.title_appDatabase),
                                subtitle = context.getString(R.string.setting_backup_sumDatabase),
                                icon = Icons.Rounded.Backup,
                                checked = clearDatabase,
                                onCheckedChange = {
                                    clearDatabase = it
                                    sp.edit().putBoolean("sp_deleteDatabase", it).apply()
                                }
                            )

                            DeleteOptionItem(
                                title = context.getString(R.string.setting_label),
                                subtitle = context.getString(R.string.setting_backup_sumSettings),
                                icon = Icons.Rounded.Settings,
                                checked = clearSettings,
                                onCheckedChange = {
                                    clearSettings = it
                                    sp.edit().putBoolean("sp_clear_settings", it).apply()
                                }
                            )

                            DeleteOptionItem(
                                title = context.getString(R.string.clear_title_quit),
                                subtitle = "Automatically clear selected data when exiting Petal",
                                icon = Icons.Rounded.ExitToApp,
                                checked = clearQuit,
                                onCheckedChange = {
                                    clearQuit = it
                                    sp.edit().putBoolean("sp_clear_quit", it).apply()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { showConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Clear Selected Data",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
private fun DeleteOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    com.petal.browser.ui.components.PetalMonitorToggleTile(
        title = title,
        subtitle = subtitle,
        icon = icon,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}
