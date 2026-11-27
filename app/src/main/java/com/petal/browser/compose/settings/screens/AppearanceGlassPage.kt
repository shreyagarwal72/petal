package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.theme.PetalText

import com.petal.browser.ui.components.PetalSpring

import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.icons.imagetoolbox.AutoAwesome
import com.petal.browser.ui.icons.imagetoolbox.HyperOS
import com.petal.browser.ui.icons.imagetoolbox.ImageToolboxIcons
import com.petal.browser.ui.icons.imagetoolbox.ToggleOn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.theme.layout.UiExperience
import com.petal.browser.ui.theme.layout.PetalLayoutState
import com.petal.browser.ui.theme.layout.PetalLayoutSettings
import com.petal.browser.ui.theme.layout.PetalSwitchType
import com.petal.browser.ui.theme.layout.PetalSliderType
import com.petal.browser.ui.theme.layout.PetalShapeType
import com.petal.browser.ui.theme.layout.PetalContainmentShapeChoice
import com.petal.browser.ui.theme.layout.PetalBadgeShape
import com.petal.browser.ui.theme.layout.rememberPetalLayoutSettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.unit.PetalLanguages
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import com.petal.browser.ui.theme.trackRevealOrigin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.petal.browser.ui.kit.Card
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.compose.settings.viewmodel.AppearanceSettingsViewModel
import com.petal.browser.ui.components.*
import com.petal.browser.ui.theme.*
import com.petal.browser.unit.PetalHighRefreshRateManager
import com.petal.browser.widget.PetalSearchWidgetProvider
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.runtime.saveable.rememberSaveable
import com.petal.browser.ui.theme.ThemeReveal

@Composable
fun AppearanceGlassPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsStateWithLifecycle()
    val liquidGlassAlpha by viewModel.liquidGlassAlpha.collectAsStateWithLifecycle()
    val liquidGlassSheen by viewModel.liquidGlassSheen.collectAsStateWithLifecycle()
    val liquidGlassRefraction by viewModel.liquidGlassRefraction.collectAsStateWithLifecycle()
    val liquidGlassRefractionWidth by viewModel.liquidGlassRefractionWidth.collectAsStateWithLifecycle()
    val liquidGlassContainments by viewModel.liquidGlassContainments.collectAsStateWithLifecycle()
    val liquidGlassBottomNav by viewModel.liquidGlassBottomNav.collectAsStateWithLifecycle()
    val liquidGlassAddressBar by viewModel.liquidGlassAddressBar.collectAsStateWithLifecycle()
    val liquidGlassBgMode by viewModel.liquidGlassBgMode.collectAsStateWithLifecycle()
    val liquidGlassBgImageUri by viewModel.liquidGlassBgImageUri.collectAsStateWithLifecycle()
    val liquidGlassBgDim by viewModel.liquidGlassBgDim.collectAsStateWithLifecycle()
    val liquidGlassBgBlur by viewModel.liquidGlassBgBlur.collectAsStateWithLifecycle()
    val onLiquidGlassEnabledChange: (Boolean) -> Unit = viewModel::setLiquidGlassEnabled
    val onLiquidGlassAlphaChange: (Float) -> Unit = viewModel::setLiquidGlassAlpha
    val onLiquidGlassSheenChange: (Float) -> Unit = viewModel::setLiquidGlassSheen
    val onLiquidGlassRefractionChange: (Float) -> Unit = viewModel::setLiquidGlassRefraction
    val onLiquidGlassRefractionWidthChange: (Float) -> Unit = viewModel::setLiquidGlassRefractionWidth
    val onLiquidGlassContainmentsChange: (Boolean) -> Unit = viewModel::setLiquidGlassContainments
    val onLiquidGlassBottomNavChange: (Boolean) -> Unit = viewModel::setLiquidGlassBottomNav
    val onLiquidGlassAddressBarChange: (Boolean) -> Unit = viewModel::setLiquidGlassAddressBar
    val onLiquidGlassBgModeChange: (String) -> Unit = viewModel::setLiquidGlassBgMode
    val onLiquidGlassBgImageUriChange: (String) -> Unit = viewModel::setLiquidGlassBgImageUri
    val onLiquidGlassBgDimChange: (Float) -> Unit = viewModel::setLiquidGlassBgDim
    val onLiquidGlassBgBlurChange: (Float) -> Unit = viewModel::setLiquidGlassBgBlur

    val onSelectExperience: (UiExperience) -> Unit = { exp ->
        val isGlass = (exp == UiExperience.LIQUID_GLASS)
        onLiquidGlassEnabledChange(isGlass)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().putBoolean("sp_liquid_glass_enabled", isGlass).apply()
        // Only the experience changes: the user's own switch / slider / shape picks are kept.
        PetalLayoutState.update(context) { current -> current.copy(uiExperience = exp) }
    }

    // Liquid Glass Background Image Picker states
    var showBgPickerChoice by remember { mutableStateOf(false) }
    var showBuiltInImagePicker by remember { mutableStateOf(false) }
    var pendingGlassCropUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}
            pendingGlassCropUri = it
        }
    }

    AppearanceSubPageScaffold(
        title = "Glass & Blur",
        subtitle = "Liquid Glass surfaces and background blur",
        pageSeed = "appearance_glass",
        onBack = onBack,
        modifier = modifier,
        overlay = {
            if (showBgPickerChoice) {
                com.petal.browser.ui.containment.PetalSheet(
                    onDismissRequest = { showBgPickerChoice = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Choose Wallpaper Source",
                            style = PetalText.sheetTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select an image using Petal's built-in file picker or your device's media gallery.",
                            style = PetalText.pageSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(4.dp))

                        com.petal.browser.ui.containment.PetalSelectableOptionCard(
                            title = "Petal Built-in File & Image Picker",
                            subtitle = "Browse device folders, downloads, and storage roots directly in Petal",
                            selected = false,
                            onClick = {
                                showBgPickerChoice = false
                                showBuiltInImagePicker = true
                            },
                            leading = {
                                com.petal.browser.ui.containment.PetalGroupIconBadge(
                                    icon = Icons.Rounded.FolderOpen,
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        )

                        com.petal.browser.ui.containment.PetalSelectableOptionCard(
                            title = "System Media Gallery",
                            subtitle = "Select from Google Photos, Gallery or System File Picker",
                            selected = false,
                            onClick = {
                                showBgPickerChoice = false
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            leading = {
                                com.petal.browser.ui.containment.PetalGroupIconBadge(
                                    icon = Icons.Rounded.PhotoLibrary,
                                    container = MaterialTheme.colorScheme.secondaryContainer,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        )
                    }
                }
            }

            if (showBuiltInImagePicker) {
                com.petal.browser.compose.file.PetalFilePickerScreen(
                    mimeTypes = arrayOf("image/*"),
                    onDismissRequest = { showBuiltInImagePicker = false },
                    onFileSelected = { file ->
                        showBuiltInImagePicker = false
                        pendingGlassCropUri = android.net.Uri.fromFile(file)
                    },
                    onBrowseSystemFallback = {
                        showBuiltInImagePicker = false
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
            }

            pendingGlassCropUri?.let { cropUri ->
                PetalWallpaperCropSheet(
                    imageUri = cropUri,
                    title = "Crop Liquid Glass Wallpaper",
                    buttonLabel = "Apply Liquid Glass Wallpaper",
                    onDismiss = { pendingGlassCropUri = null },
                    onWallpaperCropped = { croppedLocalUri ->
                        pendingGlassCropUri = null
                        onLiquidGlassBgImageUriChange(croppedLocalUri.toString())
                        onLiquidGlassBgModeChange("IMAGE")
                    }
                )
            }
        }
    ) {
        // Section 5: Liquid Glass UI & Surfaces.
        run {
            PetalSettingsSection(
                title = "Liquid Glass UI & Surfaces",
                icon = Icons.Rounded.AutoAwesome,
                cardId = "appearance_liquid_glass",
                targetHighlightId = targetHighlightItemId
            ) {
                Text(
                    text = "Stride and Vyxel-style glass with a shared frosted wallpaper, refracted edges and soft highlights.",
                    style = PetalText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Master Toggle
                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                    title = "Liquid Glass UI",
                    subtitle = if (liquidGlassEnabled) "Active system-wide across containments, bottom bar & address bar" else "Enable liquid glass materials & specular effects",
                    icon = Icons.Rounded.BlurOn,
                    checked = liquidGlassEnabled,
                    position = if (liquidGlassEnabled) com.petal.browser.ui.containment.PetalGroupPosition.TOP else com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onSelectExperience(UiExperience.LIQUID_GLASS)
                        } else {
                            onSelectExperience(UiExperience.MATERIAL_EXPRESSIVE)
                        }
                    }
                )

                if (liquidGlassEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Real-time local state so sliding immediately updates on this screen and system-wide without lag
                        val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
                        var liveAlpha by remember { mutableFloatStateOf(liquidGlassAlpha) }
                        var liveSheen by remember { mutableFloatStateOf(liquidGlassSheen) }
                        var liveRefraction by remember { mutableFloatStateOf(liquidGlassRefraction) }
                        var liveRefractionWidth by remember { mutableFloatStateOf(liquidGlassRefractionWidth) }

                        LaunchedEffect(liquidGlassAlpha) { liveAlpha = liquidGlassAlpha }
                        LaunchedEffect(liquidGlassSheen) { liveSheen = liquidGlassSheen }
                        LaunchedEffect(liquidGlassRefraction) { liveRefraction = liquidGlassRefraction }
                        LaunchedEffect(liquidGlassRefractionWidth) { liveRefractionWidth = liquidGlassRefractionWidth }

                        val lensSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

                        // Live preview: authentic Liquid Glass AGSL over moving colour. Every slider below
                        // drives it frame by frame (the values are the unsaved live ones) with zero lag.
                        com.petal.browser.ui.glass.PetalGlassLab(
                            opacity = liveAlpha,
                            sheen = liveSheen,
                            refraction = liveRefraction,
                            refractionWidth = liveRefractionWidth,
                            fx = com.petal.browser.ui.glass.rememberGlassFx()
                        )

                        // Glass Translucency Slider (Stride style PetalSlider)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Surface Translucency / Opacity",
                                    style = PetalText.sectionHeader,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${Math.round(liveAlpha * 100)}%",
                                    style = PetalText.value,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = liveAlpha,
                                onValueChange = {
                                    liveAlpha = it
                                    sp.edit().putFloat("sp_liquid_glass_alpha", it).apply()
                                },
                                onValueChangeFinished = { onLiquidGlassAlphaChange(liveAlpha) },
                                valueRange = 0.10f..0.95f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Blur Strength Slider (Gaussian radius). -1 = Auto, each surface's own tuned blur.
                        var liveBlur by remember(sp) { mutableFloatStateOf(sp.getFloat("sp_liquid_glass_blur", -1f)) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Blur Strength",
                                    style = PetalText.sectionHeader,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (liveBlur < 0f) "Auto" else "${Math.round(liveBlur)} dp",
                                    style = PetalText.value,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = if (liveBlur < 0f) 12f else liveBlur,
                                onValueChange = {
                                    liveBlur = it
                                    sp.edit().putFloat("sp_liquid_glass_blur", it).apply()
                                },
                                valueRange = 0f..32f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Specular Sheen Intensity Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Specular Sheen & Border Reflection",
                                    style = PetalText.sectionHeader,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${Math.round(liveSheen * 100)}%",
                                    style = PetalText.value,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = liveSheen,
                                onValueChange = {
                                    liveSheen = it
                                    sp.edit().putFloat("sp_liquid_glass_sheen", it).apply()
                                },
                                onValueChangeFinished = { onLiquidGlassSheenChange(liveSheen) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Refraction Strength Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Refraction Strength",
                                    style = PetalText.sectionHeader,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (lensSupported) "${Math.round(liveRefraction * 100)}%" else "Android 13+",
                                    style = PetalText.value,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = liveRefraction,
                                onValueChange = {
                                    liveRefraction = it
                                    sp.edit().putFloat("sp_liquid_glass_refraction", it).apply()
                                },
                                onValueChangeFinished = { onLiquidGlassRefractionChange(liveRefraction) },
                                valueRange = 0f..2f,
                                enabled = lensSupported,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = if (lensSupported) "How strongly the rim bends what is behind the glass. 0% turns the lens off."
                                else "Edge refraction needs Android 13 or newer. Your device shows blur only.",
                                style = PetalText.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Refraction Width Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Refraction Width",
                                    style = PetalText.sectionHeader,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (lensSupported) "${Math.round(liveRefractionWidth * 100)}%" else "Android 13+",
                                    style = PetalText.value,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            PetalSlider(
                                value = liveRefractionWidth,
                                onValueChange = {
                                    liveRefractionWidth = it
                                    sp.edit().putFloat("sp_liquid_glass_refraction_width", it).apply()
                                },
                                onValueChangeFinished = { onLiquidGlassRefractionWidthChange(liveRefractionWidth) },
                                valueRange = 0f..2f,
                                enabled = lensSupported,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        com.petal.browser.ui.kit.TextButton(
                            onClick = {
                                liveAlpha = 0.70f
                                liveSheen = 0.60f
                                liveRefraction = 1f
                                liveRefractionWidth = 1f
                                liveBlur = -1f
                                sp.edit()
                                    .remove("sp_liquid_glass_blur")
                                    .putFloat("sp_liquid_glass_alpha", 0.70f)
                                    .putFloat("sp_liquid_glass_sheen", 0.60f)
                                    .putFloat("sp_liquid_glass_refraction", 1f)
                                    .putFloat("sp_liquid_glass_refraction_width", 1f)
                                    .apply()
                                onLiquidGlassAlphaChange(0.70f)
                                onLiquidGlassSheenChange(0.60f)
                                onLiquidGlassRefractionChange(1f)
                                onLiquidGlassRefractionWidthChange(1f)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Reset glass tuning") }

                        com.petal.browser.ui.glass.GlassEffectsSettings()

                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Component Level Tweaks (Stride custom components)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Component Surfaces",
                                style = PetalText.sectionHeader,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Cards & Containments",
                                    subtitle = "Apply liquid glass material to cards, list groups & dialogs",
                                    icon = Icons.Rounded.Layers,
                                    checked = liquidGlassContainments,
                                    position = com.petal.browser.ui.containment.PetalGroupPosition.TOP,
                                    onCheckedChange = onLiquidGlassContainmentsChange
                                )
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Bottom Navigation Bar",
                                    subtitle = "Translucent floating or persistent bottom navigation bar",
                                    icon = Icons.Rounded.SpaceBar,
                                    checked = liquidGlassBottomNav,
                                    position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                                    onCheckedChange = onLiquidGlassBottomNavChange
                                )
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Address & Search Bar",
                                    subtitle = "Translucent address bar pill with specular edge glow",
                                    icon = Icons.Rounded.Search,
                                    checked = liquidGlassAddressBar,
                                    position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                                    onCheckedChange = onLiquidGlassAddressBarChange
                                )
                                // Toolbars/top bars read sp_liquid_glass_toolbars (default on); it had no toggle.
                                var toolbarGlass by remember(sp) {
                                    mutableStateOf(sp.getBoolean("sp_liquid_glass_toolbars", true))
                                }
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Top Bars & Toolbars",
                                    subtitle = "Glass on collapsing top bars and custom tab toolbars",
                                    icon = Icons.Rounded.Layers,
                                    checked = toolbarGlass,
                                    position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                                    onCheckedChange = {
                                        toolbarGlass = it
                                        sp.edit().putBoolean("sp_liquid_glass_toolbars", it).apply()
                                    }
                                )
                                var progressiveBlur by remember(sp) {
                                    mutableStateOf(sp.getBoolean("sp_liquid_glass_progressive_blur", false))
                                }
                                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                                    title = "Progressive Blur",
                                    subtitle = "Docked edge bars feather smoothly from sharp page to frosted glass (Android 13+)",
                                    icon = Icons.Rounded.BlurOn,
                                    checked = progressiveBlur,
                                    position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM,
                                    onCheckedChange = {
                                        progressiveBlur = it
                                        sp.edit().putBoolean("sp_liquid_glass_progressive_blur", it).apply()
                                    }
                                )
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = {
                                        context.startActivity(Intent(context, com.petal.browser.widget.liquidglass.PetalLiquidGlassSettingsActivity::class.java))
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Configure Liquid Glass Widgets")
                                }
                            }
                        }

                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Background Mode Selector
                        Text(
                            text = "Liquid Glass Background Layer",
                            style = PetalText.sectionHeader,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExpressiveButtonGroup(
                            items = listOf(
                                ExpressiveSegmentItem(id = "BLOOM_GLASS", label = "Bloom + glass", icon = Icons.Rounded.BubbleChart),
                                ExpressiveSegmentItem(id = "IMAGE", label = "Wallpaper", icon = Icons.Rounded.Wallpaper)
                            ),
                            selectedId = if (liquidGlassBgMode == "STRIDE" || liquidGlassBgMode == "MORPHING") "BLOOM_GLASS" else liquidGlassBgMode,
                            onItemSelected = onLiquidGlassBgModeChange,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Live Preview of Bloom behind sample glass cards
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                com.petal.browser.ui.components.PetalBloomBackground(
                                    pageSeed = "glass_preview",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .fillMaxWidth(0.9f)
                                        .height(64.dp)
                                        .liquidGlassChrome(RoundedCornerShape(16.dp), enabled = true)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Bloom + Glass Preview",
                                                style = PetalText.cardTitle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Tonal M3E shapes with live refraction",
                                                style = PetalText.cardBody,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (liquidGlassBgMode == "IMAGE") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { showBgPickerChoice = true },
                                        modifier = Modifier.weight(1f),
                                        shape = if (liquidGlassBgImageUri.isNotEmpty()) {
                                            RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                                        } else {
                                            RoundedCornerShape(50.dp)
                                        }, glass = false
                                    ) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (liquidGlassBgImageUri.isNotEmpty()) "Replace Image" else "Choose Image")
                                    }

                                    if (liquidGlassBgImageUri.isNotEmpty()) {
                                        FilledTonalButton(
                                            onClick = {
                                                onLiquidGlassBgImageUriChange("")
                                                onLiquidGlassBgModeChange("BLOOM_GLASS")
                                            },
                                            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ), glass = false
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Clear")
                                        }
                                    }
                                }

                                if (liquidGlassBgImageUri.isNotEmpty()) {
                                    // Image Dim Slider
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Wallpaper Dim Level",
                                                style = PetalText.sectionHeader,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${Math.round(liquidGlassBgDim * 100)}%",
                                                style = PetalText.value,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        PetalSlider(
                                            value = liquidGlassBgDim,
                                            onValueChange = onLiquidGlassBgDimChange,
                                            valueRange = 0f..0.85f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    // Image Blur Slider
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Wallpaper Blur Radius",
                                                style = PetalText.sectionHeader,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${Math.round(liquidGlassBgBlur)} dp",
                                                style = PetalText.value,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        PetalSlider(
                                            value = liquidGlassBgBlur,
                                            onValueChange = onLiquidGlassBgBlurChange,
                                            valueRange = 0f..40f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Background blur behind menus, sheets and dialogs (works in M3E and Liquid Glass).
        com.petal.browser.ui.blur.PetalBlurSettingsSection(targetHighlightId = targetHighlightItemId)
    }
}
