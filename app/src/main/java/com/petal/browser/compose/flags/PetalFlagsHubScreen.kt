package com.petal.browser.compose.flags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.flags.FlagCategory
import com.petal.browser.flags.FlagState
import com.petal.browser.flags.PetalFlagDefinition
import com.petal.browser.flags.PetalFlagsRegistry
import com.petal.browser.flags.PetalFlagsStore
import com.petal.browser.flags.PetalRestartManager
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSectionLabel
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalBackdropSource
import com.petal.browser.ui.containment.rememberPetalBackdrop
import com.petal.browser.ui.theme.PetalMaterialShapes

/**
 * PetalFlagsHubScreen
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome-flags-inspired experimental hub & native about:config manager for Petal Browser.
 * Built with proper containment, Material 3 Expressive shapes, and Live Liquid Glass.
 */
@Composable
fun PetalFlagsHubScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val backdrop = rememberPetalBackdrop()

    LaunchedEffect(Unit) {
        PetalFlagsStore.initialize(context)
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FlagCategory.ALL) }
    val hasPendingRelaunch by PetalFlagsStore.hasPendingRelaunch.collectAsState()
    val modifiedCount by PetalFlagsStore.modifiedFlagsCount.collectAsState()

    val filteredFlags = remember(searchQuery, selectedCategory) {
        PetalFlagsRegistry.flags.filter { flag ->
            val matchesCategory = selectedCategory == FlagCategory.ALL || flag.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                flag.title.contains(searchQuery, ignoreCase = true) ||
                flag.description.contains(searchQuery, ignoreCase = true) ||
                flag.prefKey.contains(searchQuery, ignoreCase = true) ||
                flag.id.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Top Glass Header ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassChrome(
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        backdrop = backdrop,
                        isDark = MaterialTheme.colorScheme.surface.let { it.red * 0.299 + it.green * 0.587 + it.blue * 0.114 < 0.5 }
                    ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Experiments & Flags",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "about:config • petal://flags",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (modifiedCount > 0) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    PetalFlagsStore.resetAll(context)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Reset All", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Warning Banner
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "WARNING: Experimental features ahead! Modifying these internal GeckoView preferences may affect stability, security, or performance.",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search flags, descriptions, #tags...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // Mode Tabs
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Experiments (${PetalFlagsRegistry.flags.size})", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("All Preferences (Raw)", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            // ── Scrollable Body with Backdrop Sampling ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .petalBackdropSource(backdrop)
            ) {
                if (selectedTabIndex == 0) {
                    // Experiments View
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Category Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FlagCategory.values().forEach { cat ->
                                val selected = selectedCategory == cat
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedCategory = cat
                                    },
                                    label = { Text(cat.displayName) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        // Grouped Flags List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val groupedByCategory = filteredFlags.groupBy { it.category }

                            groupedByCategory.forEach { (category, flagsInCategory) ->
                                item {
                                    PetalSectionLabel(
                                        text = category.displayName,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }

                                item {
                                    PetalGroup(
                                        rowCount = flagsInCategory.size,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { index, position ->
                                        val flag = flagsInCategory[index]
                                        FlagRowItem(
                                            flag = flag,
                                            position = position,
                                            onStateChanged = { newState ->
                                                PetalFlagsStore.setFlagState(context, flag.id, newState)
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                Spacer(Modifier.height(96.dp))
                            }
                        }
                    }
                } else {
                    // Raw Preferences Explorer Tab
                    RawPreferencesTab(
                        searchQuery = searchQuery,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ── Floating Live Liquid Glass Relaunch Bar ──
        PetalRelaunchBar(
            visible = hasPendingRelaunch,
            onRelaunch = {
                PetalRestartManager.restartApp(context)
            },
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Individual Flag Item with Expressive Shape Badge & Proper Containment
 */
@Composable
private fun FlagRowItem(
    flag: PetalFlagDefinition,
    position: PetalGroupPosition,
    onStateChanged: (FlagState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentState = remember(flag.id) { PetalFlagsStore.getFlagState(context, flag.id) }

    val shapeBadge = remember(flag.category) {
        when (flag.category) {
            FlagCategory.PERFORMANCE -> PetalMaterialShapes.Boom.toShape()
            FlagCategory.PRIVACY -> PetalMaterialShapes.Gem.toShape()
            FlagCategory.RENDERING -> PetalMaterialShapes.Flower.toShape()
            FlagCategory.NETWORK -> PetalMaterialShapes.Cookie4Sided.toShape()
            FlagCategory.EXTENSIONS, FlagCategory.ALL -> PetalMaterialShapes.PuffyDiamond.toShape()
        }
    }

    val iconVector = remember(flag.category) {
        when (flag.category) {
            FlagCategory.PERFORMANCE -> Icons.Rounded.Speed
            FlagCategory.PRIVACY -> Icons.Rounded.Security
            FlagCategory.RENDERING -> Icons.Rounded.FlashOn
            FlagCategory.NETWORK -> Icons.Rounded.Language
            FlagCategory.EXTENSIONS, FlagCategory.ALL -> Icons.Rounded.Extension
        }
    }

    val containerShape = when (position) {
        PetalGroupPosition.First -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        PetalGroupPosition.Middle -> RoundedCornerShape(4.dp)
        PetalGroupPosition.Last -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        PetalGroupPosition.Single -> RoundedCornerShape(20.dp)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Expressive Shape Badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(shapeBadge)
                        .background(
                            when (flag.category) {
                                FlagCategory.PERFORMANCE -> MaterialTheme.colorScheme.primaryContainer
                                FlagCategory.PRIVACY -> MaterialTheme.colorScheme.tertiaryContainer
                                FlagCategory.RENDERING -> MaterialTheme.colorScheme.secondaryContainer
                                FlagCategory.NETWORK -> MaterialTheme.colorScheme.primaryContainer
                                FlagCategory.EXTENSIONS, FlagCategory.ALL -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when (flag.category) {
                            FlagCategory.PERFORMANCE -> MaterialTheme.colorScheme.primary
                            FlagCategory.PRIVACY -> MaterialTheme.colorScheme.tertiary
                            FlagCategory.RENDERING -> MaterialTheme.colorScheme.secondary
                            FlagCategory.NETWORK -> MaterialTheme.colorScheme.primary
                            FlagCategory.EXTENSIONS, FlagCategory.ALL -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = flag.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "#${flag.prefKey}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = flag.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // State Selector Chips: Default / Enabled / Disabled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlagState.values().forEach { state ->
                            val isSelected = currentState == state
                            val chipColor by animateColorAsState(
                                if (isSelected) {
                                    when (state) {
                                        FlagState.ENABLED -> MaterialTheme.colorScheme.primary
                                        FlagState.DISABLED -> MaterialTheme.colorScheme.error
                                        FlagState.DEFAULT -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )

                            val textColor by animateColorAsState(
                                if (isSelected) {
                                    when (state) {
                                        FlagState.ENABLED -> MaterialTheme.colorScheme.onPrimary
                                        FlagState.DISABLED -> MaterialTheme.colorScheme.onError
                                        FlagState.DEFAULT -> MaterialTheme.colorScheme.onSurface
                                    }
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(chipColor)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onStateChanged(state)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Raw Preferences Explorer Tab (about:config power inspector)
 */
@Composable
private fun RawPreferencesTab(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val sampleGeckoPrefs = remember {
        listOf(
            Triple("accessibility.force_disabled", "0", "int"),
            Triple("browser.cache.disk.enable", "true", "bool"),
            Triple("browser.cache.memory.enable", "true", "bool"),
            Triple("dom.enable_performance", "false", "bool"),
            Triple("dom.gamepad.enabled", "false", "bool"),
            Triple("dom.security.https_only_mode", "true", "bool"),
            Triple("fission.autostart", "true", "bool"),
            Triple("general.smoothScroll", "true", "bool"),
            Triple("general.useragent.override", "", "string"),
            Triple("gfx.webrender.all", "true", "bool"),
            Triple("javascript.options.wasm_simd", "true", "bool"),
            Triple("media.peerconnection.ice.no_host", "true", "bool"),
            Triple("network.http.http3.enable", "true", "bool"),
            Triple("privacy.resistFingerprinting", "false", "bool")
        )
    }

    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) sampleGeckoPrefs
        else sampleGeckoPrefs.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PetalSectionLabel(
                text = "Gecko Engine Preferences (${filtered.size})",
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        item {
            PetalGroup(
                rowCount = filtered.size,
                modifier = Modifier.fillMaxWidth()
            ) { index, position ->
                val (key, value, type) = filtered[index]
                val containerShape = when (position) {
                    PetalGroupPosition.First -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    PetalGroupPosition.Middle -> RoundedCornerShape(4.dp)
                    PetalGroupPosition.Last -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                    PetalGroupPosition.Single -> RoundedCornerShape(16.dp)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(containerShape),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = type.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "val: $value",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (type == "bool") {
                            Icon(
                                imageVector = if (value == "true") Icons.Rounded.Check else Icons.Rounded.Clear,
                                contentDescription = null,
                                tint = if (value == "true") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(96.dp))
        }
    }
}
