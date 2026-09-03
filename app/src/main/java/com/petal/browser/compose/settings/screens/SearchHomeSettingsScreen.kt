package com.petal.browser.compose.settings.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.settings.viewmodel.SearchHomeSettingsViewModel
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.PetalSearchEngineSheetContent
import com.petal.browser.ui.components.ScrollFadeRow
import com.petal.browser.ui.components.availableSearchEngines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHomeSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchHomeSettingsViewModel = hiltViewModel()
) {
    val searchEngineIndex by viewModel.searchEngineIndex.collectAsStateWithLifecycle()
    val homepageType by viewModel.homepageType.collectAsStateWithLifecycle()
    val customHomepageUrl by viewModel.customHomepageUrl.collectAsStateWithLifecycle()
    val backgroundPlay by viewModel.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by viewModel.autoPip.collectAsStateWithLifecycle()
    val forceDarkMode by viewModel.forceDarkMode.collectAsStateWithLifecycle()
    val enableLiveSuggestions by viewModel.enableLiveSuggestions.collectAsStateWithLifecycle()

    SearchHomeSettingsScreenContent(
        searchEngineIndex = searchEngineIndex,
        homepageType = homepageType,
        customHomepageUrl = customHomepageUrl,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
        forceDarkMode = forceDarkMode,
        enableLiveSuggestions = enableLiveSuggestions,
        onSearchEngineIndexChange = viewModel::setSearchEngineIndex,
        onHomepageTypeChange = viewModel::setHomepageType,
        onCustomHomepageUrlChange = viewModel::setCustomHomepageUrl,
        onBackgroundPlayChange = viewModel::setBackgroundPlay,
        onAutoPipChange = viewModel::setAutoPip,
        onForceDarkModeChange = viewModel::setForceDarkMode,
        onEnableLiveSuggestionsChange = viewModel::setEnableLiveSuggestions,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHomeSettingsScreenContent(
    searchEngineIndex: String,
    homepageType: String,
    customHomepageUrl: String,
    backgroundPlay: Boolean,
    autoPip: Boolean,
    forceDarkMode: Boolean,
    enableLiveSuggestions: Boolean,
    onSearchEngineIndexChange: (String) -> Unit,
    onHomepageTypeChange: (String) -> Unit,
    onCustomHomepageUrlChange: (String) -> Unit,
    onBackgroundPlayChange: (Boolean) -> Unit,
    onAutoPipChange: (Boolean) -> Unit,
    onForceDarkModeChange: (Boolean) -> Unit,
    onEnableLiveSuggestionsChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEngineSheet by remember { mutableStateOf(false) }

    val isPipSupported = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    if (showEngineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEngineSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            PetalSearchEngineSheetContent(
                onConfirm = { idx ->
                    onSearchEngineIndexChange(idx.toString())
                    showEngineSheet = false
                },
                onCancel = { showEngineSheet = false }
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "search_home_settings")

        Column(modifier = Modifier.fillMaxSize()) {
            ExpressiveHeader(
                title = "Search Engine & Home",
                subtitle = "Default search engine and custom homepage",
                onBack = onNavigateBack
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Default Search Engine Card
                SettingsCategoryCard(title = "Default Search Engine", iconRes = com.petal.browser.R.drawable.globe_2_cancel_rounded) {
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ToggleRow(
                        title = "Live Search Recommendations",
                        subtitle = "Fetch live autocomplete suggestions from Google, DuckDuckGo, or Bing while typing",
                        icon = Icons.Rounded.Search,
                        checked = enableLiveSuggestions,
                        onCheckedChange = onEnableLiveSuggestionsChange
                    )
                }

                // Homepage & Media Playback Card
                SettingsCategoryCard(title = "Homepage & Media Playback", iconRes = com.petal.browser.R.drawable.home_filled) {
                    Text(
                        "Custom Homepage:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val homeTypeScrollState = rememberScrollState()
                    ScrollFadeRow(
                        scrollState = homeTypeScrollState,
                        edgeColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(homeTypeScrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = homepageType == "0",
                                onClick = { onHomepageTypeChange("0") },
                                label = { Text("Petal Start Page") },
                                leadingIcon = if (homepageType == "0") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = homepageType == "1",
                                onClick = { onHomepageTypeChange("1") },
                                label = { Text("Custom URL") },
                                leadingIcon = if (homepageType == "1") {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    if (homepageType == "1") {
                        OutlinedTextField(
                            value = customHomepageUrl,
                            onValueChange = onCustomHomepageUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter Homepage URL") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Background Audio & Video Playback
                    ToggleRow(
                        title = "Background Audio & Video Playback",
                        subtitle = "Keep YouTube & web media playing when switching tabs or backgrounding app",
                        icon = Icons.Rounded.PlayCircle,
                        checked = backgroundPlay,
                        onCheckedChange = onBackgroundPlayChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Auto Picture-in-Picture
                    ToggleRow(
                        title = if (isPipSupported) "Auto Picture-in-Picture (PiP)" else "Auto Picture-in-Picture (Not Supported)",
                        subtitle = if (isPipSupported) "Automatically enter floating PiP window when leaving app during video playback" else "Picture-in-Picture mode is not supported on this device",
                        icon = Icons.Rounded.PictureInPicture,
                        checked = autoPip && isPipSupported,
                        enabled = isPipSupported,
                        onCheckedChange = onAutoPipChange
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Force Dark Mode for Web Content
                    ToggleRow(
                        title = "Force Dark Web Content",
                        subtitle = "Automatically apply dark themes to websites that do not natively support dark mode",
                        icon = Icons.Rounded.DarkMode,
                        checked = forceDarkMode,
                        onCheckedChange = onForceDarkModeChange
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
