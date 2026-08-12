/*
 * PetalSettingsScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Modern Jetpack Compose Settings screen for Petal Browser featuring
 * Stride IconSwitch toggles, StrideSlider expressive controls, persistent SharedPreferences,
 * Material You Dynamic Colors, and AMOLED Black dark mode.
 */

package com.petal.browser.compose.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalSearchEngineSheetContent
import com.petal.browser.ui.components.availableSearchEngines
import com.petal.browser.ui.components.StrideSlider
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalSettingsBridge {
    @JvmStatic
    fun createSettingsView(activity: ComponentActivity, onBackPress: () -> Unit): ComposeView {
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PetalExpressiveTheme {
                    PetalSettingsScreen(onBackPress = onBackPress)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSettingsScreen(onBackPress: () -> Unit = {}) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // Load initial values from SharedPreferences so state is NOT lost on app restart
    var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
    var isDynamicColor by remember { mutableStateOf(sp.getBoolean("useDynamicColor", true)) }
    var isAdBlock by remember { mutableStateOf(sp.getBoolean("sp_ad_block", true)) }
    var isHttpsOnly by remember { mutableStateOf(sp.getBoolean("sp_https_only", true)) }
    var isJavaScript by remember { mutableStateOf(sp.getBoolean("sp_javascript", true)) }
    var isBlockPopups by remember { mutableStateOf(sp.getBoolean("sp_block_popups", true)) }
    var isAutoOpenApps by remember { mutableStateOf(sp.getBoolean("sp_auto_open_apps", true)) }
    var fontSize by remember { mutableFloatStateOf(sp.getFloat("sp_font_size_scale", 1.0f)) }
    var zoomLevel by remember { mutableFloatStateOf(sp.getFloat("sp_zoom_level_scale", 1.0f)) }
    var searchEngineIndex by remember { mutableStateOf(sp.getString("sp_search_engine", "0") ?: "0") }
    var showEngineSheet by remember { mutableStateOf(false) }

    if (showEngineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEngineSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            PetalSearchEngineSheetContent(
                onConfirm = { idx ->
                    sp.edit().putString("sp_search_engine", idx.toString()).apply()
                    searchEngineIndex = idx.toString()
                    showEngineSheet = false
                },
                onCancel = { showEngineSheet = false }
            )
        }
    }

    PetalExpressiveTheme(
        dynamicColor = isDynamicColor,
        useAmoled = isAmoled
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Browser Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPress) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Search Engine Section
                SettingsCategoryCard(title = "Default Search Engine", icon = Icons.Rounded.Search) {
                    val currentEngineName = remember(searchEngineIndex) {
                        val idx = searchEngineIndex.toIntOrNull() ?: 0
                        availableSearchEngines.find { it.index == idx }?.name ?: "Google"
                    }
                    Surface(
                        onClick = { showEngineSheet = true },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Default Search Provider",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentEngineName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 1. Appearance & Theme Section
                SettingsCategoryCard(title = "Appearance & Theme", icon = Icons.Rounded.Palette) {
                    // AMOLED Black Toggle
                    ToggleRow(
                        title = "AMOLED Black Dark Mode",
                        subtitle = "Pure black backgrounds for OLED panels",
                        icon = Icons.Rounded.DarkMode,
                        checked = isAmoled,
                        onCheckedChange = { newValue ->
                            isAmoled = newValue
                            sp.edit().putBoolean("sp_amoled", newValue).apply()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Material You Dynamic Color Toggle
                    ToggleRow(
                        title = "Material You Dynamic Colors",
                        subtitle = "Extract system colors on Android 12+",
                        icon = Icons.Rounded.ColorLens,
                        checked = isDynamicColor,
                        onCheckedChange = { newValue ->
                            isDynamicColor = newValue
                            sp.edit().putBoolean("useDynamicColor", newValue).apply()
                        }
                    )
                }

                // 2. Privacy & Shield Section
                SettingsCategoryCard(title = "Privacy & Shield Protection", icon = Icons.Rounded.Shield) {
                    // Ad Block Toggle
                    ToggleRow(
                        title = "Ad & Tracker Shield",
                        subtitle = "Block invasive ads, popunders, and web trackers",
                        icon = Icons.Rounded.Shield,
                        checked = isAdBlock,
                        onCheckedChange = { newValue ->
                            isAdBlock = newValue
                            sp.edit().putBoolean("sp_ad_block", newValue).putBoolean("profileStandard_adBlock", newValue).apply()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Block Popups Toggle
                    ToggleRow(
                        title = "Block Popup Windows",
                        subtitle = "Prevent unwanted popups and redirect windows",
                        icon = Icons.Rounded.OpenInNew,
                        checked = isBlockPopups,
                        onCheckedChange = { newValue ->
                            isBlockPopups = newValue
                            sp.edit().putBoolean("sp_block_popups", newValue).putBoolean("profileStandard_javascriptPopUp", !newValue).apply()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Auto Open External Apps Toggle
                    ToggleRow(
                        title = "Auto Open External Apps",
                        subtitle = "Open YouTube, Maps & Play Store links in native apps",
                        icon = Icons.Rounded.Launch,
                        checked = isAutoOpenApps,
                        onCheckedChange = { newValue ->
                            isAutoOpenApps = newValue
                            sp.edit().putBoolean("sp_auto_open_apps", newValue).apply()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // HTTPS Only Toggle
                    ToggleRow(
                        title = "HTTPS Security Enforcer",
                        subtitle = "Automatically upgrade connections to HTTPS",
                        icon = Icons.Rounded.Lock,
                        checked = isHttpsOnly,
                        onCheckedChange = { newValue ->
                            isHttpsOnly = newValue
                            sp.edit().putBoolean("sp_https_only", newValue).apply()
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // JavaScript Toggle
                    ToggleRow(
                        title = "Enable JavaScript",
                        subtitle = "Required for modern web features",
                        icon = Icons.Rounded.Code,
                        checked = isJavaScript,
                        onCheckedChange = { newValue ->
                            isJavaScript = newValue
                            sp.edit().putBoolean("sp_javascript", newValue).putBoolean("profileStandard_javascript", newValue).apply()
                        }
                    )
                }

                // 3. Display & Scaling Sliders (using StrideSlider)
                SettingsCategoryCard(title = "Display & Font Scaling", icon = Icons.Rounded.FormatSize) {
                    // Font Size StrideSlider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Text Font Scale",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${(fontSize * 100f).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        StrideSlider(
                            value = fontSize,
                            onValueChange = { newValue ->
                                fontSize = newValue
                                sp.edit().putFloat("sp_font_size_scale", newValue).apply()
                            },
                            valueRange = 0.7f..1.5f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Page Zoom StrideSlider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Default Page Zoom",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${(zoomLevel * 100f).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        StrideSlider(
                            value = zoomLevel,
                            onValueChange = { newValue ->
                                zoomLevel = newValue
                                sp.edit().putFloat("sp_zoom_level_scale", newValue).apply()
                            },
                            valueRange = 0.8f..2.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconSwitch(
            checked = checked,
            icon = icon,
            onCheckedChange = onCheckedChange
        )
    }
}
