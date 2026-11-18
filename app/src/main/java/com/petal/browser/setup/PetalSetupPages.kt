package com.petal.browser.setup

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.petal.browser.R
import com.petal.browser.browser.PetalAdBlockEngine
import com.petal.browser.extensions.PetalBuiltInExtensionManager
import com.petal.browser.ui.components.PetalBrowserPermissionDialog
import com.petal.browser.ui.components.SearchEngineItem
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalSelectableOptionCard
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.petalConnectedShape
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.Card
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.FilterChip
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.PetalPalettes
import com.petal.browser.ui.theme.ThemeConfig
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.theme.toShape
import com.petal.browser.unit.PetalLanguages

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Page 0 · Welcome
// ─────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun WelcomePage(language: String, onLanguage: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PetalExpressiveHeroArt(modifier = Modifier.fillMaxWidth())

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
            Text(
                stringResource(R.string.petal_setup_welcome_eyebrow),
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
        }
        Text(
            stringResource(R.string.petal_setup_hero_headline),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.petal_setup_hero_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FeatureTile(Modifier.weight(1f), Icons.Rounded.Shield, PetalMaterialShapes.Sunny.toShape(), stringResource(R.string.petal_setup_feat_private))
            FeatureTile(Modifier.weight(1f), Icons.Rounded.Palette, PetalMaterialShapes.Flower.toShape(), stringResource(R.string.petal_setup_feat_yours))
            FeatureTile(Modifier.weight(1f), Icons.Rounded.Bolt, PetalMaterialShapes.Cookie6Sided.toShape(), stringResource(R.string.petal_setup_feat_tools))
        }

        SetupSectionLabel(stringResource(R.string.ui_app_language))
        LanguagePicker(language, onLanguage)
    }
}

@Composable
private fun FeatureTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, shape: androidx.compose.ui.graphics.Shape, label: String) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun LanguagePicker(language: String, onLanguage: (String) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val quick = remember {
        listOf("system", "en", "hi-Latn", "hi", "es").map { PetalLanguages.findLanguage(it) }
    }
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        labelColor = MaterialTheme.colorScheme.onSurface,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        quick.forEach { lang ->
            val selected = language.equals(lang.tag, ignoreCase = true)
            FilterChip(
                selected = selected,
                onClick = { onLanguage(lang.tag) },
                label = { Text(lang.nativeName, maxLines = 1) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null,
                shape = RoundedCornerShape(14.dp),
                colors = chipColors,
                border = null
            )
        }
        val customSelected = quick.none { it.tag.equals(language, ignoreCase = true) }
        FilledTonalButton(
            onClick = { showSheet = true },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Rounded.Translate, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (customSelected) PetalLanguages.findLanguage(language).nativeName
                else stringResource(R.string.petal_setup_all_languages),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    if (showSheet) {
        LanguageSheet(language, onLanguage = { onLanguage(it); showSheet = false }, onDismiss = { showSheet = false })
    }
}

@Composable
private fun LanguageSheet(language: String, onLanguage: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) PetalLanguages.ALL_LANGUAGES
        else PetalLanguages.ALL_LANGUAGES.filter {
            it.nativeName.lowercase().contains(q) || it.englishName.lowercase().contains(q) || it.tag.lowercase().contains(q)
        }
    }
    PetalSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.ui_app_language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.ui_choose_your_preferred_display_language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(12.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.petal_setup_language_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, null) }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Spacer(Modifier.size(12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(count = filtered.size, key = { filtered[it].tag }) { i ->
                    val lang = filtered[i]
                    PetalSelectableOptionCard(
                        title = lang.displayLabel,
                        subtitle = if (lang.tag != "system") lang.tag else null,
                        selected = language.equals(lang.tag, ignoreCase = true),
                        onClick = { onLanguage(lang.tag) }
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Page 1 · Look
// ─────────────────────────────────────────────────────────────────────────────────────────────────

internal data class SetupTheme(
    val paletteId: String,
    val dynamicColor: Boolean,
    val amoled: Boolean,
    val mode: ThemeConfig
)

@Composable
internal fun LookPage(
    sp: SharedPreferences,
    theme: SetupTheme,
    onTheme: (SetupTheme) -> Unit,
    adBlock: Boolean
) {
    var floating by remember { mutableStateOf(sp.getBoolean("sp_floating_tab_bar", true)) }
    var addressBottom by remember { mutableStateOf(sp.getString("sp_address_bar_position", "TOP") == "BOTTOM") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SetupPageHeader(
            icon = Icons.Rounded.Palette,
            badgeShape = PetalMaterialShapes.Flower.toShape(),
            title = stringResource(R.string.petal_setup_look_title),
            body = stringResource(R.string.petal_setup_look_body)
        )

        MiniBrowserPreview(
            addressAtBottom = addressBottom,
            floatingTabs = floating,
            adBlock = adBlock,
            homeLabel = stringResource(R.string.petal_setup_home),
            bookmarksLabel = stringResource(R.string.petal_setup_bookmarks),
            tabsLabel = stringResource(R.string.petal_setup_tabs),
            searchHint = stringResource(R.string.petal_setup_search_hint),
            pageTitle = stringResource(R.string.petal_setup_mini_page_title),
            shieldLabel = stringResource(R.string.petal_setup_ad_shield)
        )

        // Appearance (system / light / dark)
        SetupSectionLabel(stringResource(R.string.petal_setup_mode_label))
        val modes = listOf(
            ThemeConfig.FOLLOW_SYSTEM to (stringResource(R.string.petal_setup_mode_system) to Icons.Rounded.BrightnessAuto),
            ThemeConfig.LIGHT to (stringResource(R.string.petal_setup_mode_light) to Icons.Rounded.LightMode),
            ThemeConfig.DARK to (stringResource(R.string.petal_setup_mode_dark) to Icons.Rounded.DarkMode)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            modes.forEachIndexed { index, (mode, labelIcon) ->
                val selected = theme.mode == mode
                val onClick = {
                    sp.edit().putString("sp_theme_config", mode.name).apply()
                    onTheme(theme.copy(mode = mode))
                }
                val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    Icon(labelIcon.second, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(labelIcon.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (selected) {
                    Button(onClick, Modifier.weight(1f), shape = petalConnectedShape(index, modes.size), content = content)
                } else {
                    FilledTonalButton(
                        onClick, Modifier.weight(1f), shape = petalConnectedShape(index, modes.size),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        content = content
                    )
                }
            }
        }

        // Colour
        SetupSectionLabel(stringResource(R.string.petal_setup_palette))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PetalPalettes.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    label = paletteLabel(palette.id, palette.label),
                    selected = !theme.dynamicColor && theme.paletteId == palette.id,
                    dimmed = theme.dynamicColor,
                    onClick = {
                        // Picking a palette implies leaving device colours, otherwise nothing would change.
                        sp.edit().putString("sp_palette_id", palette.id).putBoolean("useDynamicColor", false).apply()
                        onTheme(theme.copy(paletteId = palette.id, dynamicColor = false))
                    }
                )
            }
        }

        PetalGroup(rowCount = if (isDynamicColorSupported) 2 else 1) { index, position ->
            val dynamicRow = isDynamicColorSupported && index == 0
            if (dynamicRow) {
                PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_dynamic_color),
                    subtitle = stringResource(R.string.pref_summary_dynamic_colors),
                    icon = Icons.Rounded.AutoAwesome,
                    checked = theme.dynamicColor,
                    onCheckedChange = {
                        sp.edit().putBoolean("useDynamicColor", it).apply()
                        onTheme(theme.copy(dynamicColor = it))
                    },
                    position = position
                )
            } else {
                PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_amoled),
                    subtitle = stringResource(R.string.pref_summary_amoled),
                    icon = Icons.Rounded.Contrast,
                    checked = theme.amoled,
                    onCheckedChange = {
                        sp.edit().putBoolean("sp_amoled", it).apply()
                        onTheme(theme.copy(amoled = it))
                    },
                    position = position
                )
            }
        }

        // Layout
        SetupSectionLabel(stringResource(R.string.petal_setup_address_bar_label))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(false to R.string.petal_setup_pos_top, true to R.string.petal_setup_pos_bottom).forEach { (bottom, label) ->
                val selected = addressBottom == bottom
                Card(
                    onClick = {
                        sp.edit().putString("sp_address_bar_position", if (bottom) "BOTTOM" else "TOP").apply()
                        addressBottom = bottom
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AddressBarSchematic(atBottom = bottom, selected = selected)
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        PetalGroup(rowCount = 1) { _, position ->
            PetalSettingsToggleRow(
                title = stringResource(R.string.petal_setup_floating_tabs),
                subtitle = stringResource(R.string.pref_summary_floating_tab_bar),
                icon = Icons.Rounded.Tab,
                checked = floating,
                onCheckedChange = {
                    sp.edit().putBoolean("sp_floating_tab_bar", it).apply()
                    floating = it
                },
                position = position
            )
        }
    }
}

private fun paletteLabelRes(id: String): Int? = when (id) {
    "petal" -> R.string.petal_setup_palette_petal
    "default" -> R.string.petal_setup_palette_indigo
    "tide" -> R.string.petal_setup_palette_tide
    "zen" -> R.string.petal_setup_palette_zen
    "ember" -> R.string.petal_setup_palette_ember
    "forest" -> R.string.petal_setup_palette_forest
    else -> null
}

@Composable
private fun paletteLabel(id: String, fallback: String): String =
    paletteLabelRes(id)?.let { stringResource(it) } ?: fallback

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Page 2 · Privacy
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Curated, correctly-labelled built-ins. (Previously every unmapped extension was labelled "Password Manager".) */
private data class SetupExtension(val prefKey: String, val title: Int?, val reason: Int?, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val setupExtensions = listOf(
    SetupExtension("petal_builtin_webcompat", R.string.petal_setup_ext_webcompat, R.string.petal_setup_ext_webcompat_reason, Icons.Rounded.Build),
    SetupExtension("petal_builtin_consent_manager", null, null, Icons.Rounded.Cookie),
    SetupExtension("petal_builtin_scriptlet_defuser", null, null, Icons.Rounded.Security),
    SetupExtension("petal_builtin_google_search_fixer", R.string.petal_setup_ext_search_fixer, R.string.petal_setup_ext_search_fixer_reason, Icons.Rounded.Search),
    SetupExtension("petal_builtin_media_grabber", R.string.petal_setup_ext_media, R.string.petal_setup_ext_media_reason, Icons.Rounded.Download)
)

@Composable
internal fun PrivacyPage(sp: SharedPreferences, adBlock: Boolean, onAdBlock: (Boolean) -> Unit) {
    val context = LocalContext.current
    val specs = remember {
        val byKey = PetalBuiltInExtensionManager.builtIns.associateBy { it.prefKey }
        setupExtensions.mapNotNull { ext -> byKey[ext.prefKey]?.let { ext to it } }
    }
    var darkWebpages by remember { mutableStateOf(sp.getBoolean("petal_builtin_dark_webpages", false)) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SetupPageHeader(
            icon = Icons.Rounded.Shield,
            badgeShape = PetalMaterialShapes.Sunny.toShape(),
            title = stringResource(R.string.petal_setup_power_title),
            body = stringResource(R.string.petal_setup_power_body)
        )

        // Hero toggle for the one protection most people care about.
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = if (adBlock) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(if (adBlock) PetalMaterialShapes.VerySunny.toShape() else CircleShape)
                        .background(if (adBlock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (adBlock) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe,
                        null,
                        Modifier.size(30.dp),
                        tint = if (adBlock) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.petal_setup_ad_block), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.petal_setup_ad_block_reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (adBlock) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(checked = adBlock, onCheckedChange = onAdBlock)
            }
        }

        SetupSectionLabel(stringResource(R.string.petal_setup_tools_label))
        PetalGroup(rowCount = specs.size + 1) { index, position ->
            if (index == 0) {
                PetalSettingsToggleRow(
                    title = stringResource(R.string.petal_setup_ext_dark),
                    subtitle = stringResource(R.string.petal_setup_ext_dark_reason),
                    icon = Icons.Rounded.DarkMode,
                    checked = darkWebpages,
                    onCheckedChange = {
                        darkWebpages = it
                        PetalBuiltInExtensionManager.setEnabled(context, "petal_builtin_dark_webpages", it)
                    },
                    position = position
                )
            } else {
                val (ext, spec) = specs[index - 1]
                var enabled by remember(spec.prefKey) { mutableStateOf(sp.getBoolean(spec.prefKey, spec.defaultOn)) }
                PetalSettingsToggleRow(
                    title = ext.title?.let { stringResource(it) } ?: spec.label,
                    subtitle = ext.reason?.let { stringResource(it) } ?: spec.description,
                    icon = ext.icon,
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        PetalBuiltInExtensionManager.setEnabled(context, spec.prefKey, it)
                    },
                    position = position
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Page 3 · Access (permissions)
// ─────────────────────────────────────────────────────────────────────────────────────────────────

internal data class SetupPermission(
    val id: String,
    val title: Int,
    val reason: Int,
    val permissions: List<String>,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val optional: Boolean = false
)

internal fun setupPermissions(): List<SetupPermission> {
    val media = if (Build.VERSION.SDK_INT >= 33) listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    return buildList {
        if (Build.VERSION.SDK_INT >= 33) add(SetupPermission("notifications", R.string.petal_setup_notifications, R.string.petal_setup_notifications_reason, listOf(Manifest.permission.POST_NOTIFICATIONS), Icons.Rounded.Notifications))
        add(SetupPermission("camera", R.string.petal_setup_camera, R.string.petal_setup_camera_reason, listOf(Manifest.permission.CAMERA), Icons.Rounded.CameraAlt))
        add(SetupPermission("media", R.string.petal_setup_media, R.string.petal_setup_media_reason, media, Icons.Rounded.PhotoLibrary))
        add(SetupPermission("microphone", R.string.petal_setup_microphone, R.string.petal_setup_microphone_reason, listOf(Manifest.permission.RECORD_AUDIO), Icons.Rounded.Mic))
        add(SetupPermission("location", R.string.petal_setup_location, R.string.petal_setup_location_reason, listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), Icons.Rounded.LocationOn, optional = true))
    }
}

internal fun SetupPermission.isGranted(context: android.content.Context): Boolean =
    permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@Composable
internal fun AccessPage(activity: ComponentActivity, sp: SharedPreferences, items: List<SetupPermission>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }

    fun permanentlyDenied(item: SetupPermission): Boolean =
        sp.getBoolean("setup_permission_asked_${item.id}", false) &&
            item.permissions.all {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED &&
                    !activity.shouldShowRequestPermissionRationale(it)
            }

    fun request(vararg wanted: SetupPermission) {
        val perms = wanted.flatMap { it.permissions }.distinct().toTypedArray()
        wanted.forEach { sp.edit().putBoolean("setup_permission_asked_${it.id}", true).apply() }
        runCatching { launcher.launch(perms) }.onFailure {
            androidx.core.app.ActivityCompat.requestPermissions(activity, perms, 17322)
        }
    }

    val granted = remember(refresh, items) { items.count { it.isGranted(context) } }
    val recommended = remember(refresh, items) { items.filter { !it.optional && !it.isGranted(context) } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SetupPageHeader(
            icon = Icons.Rounded.Security,
            badgeShape = PetalMaterialShapes.Clover4Leaf.toShape(),
            title = stringResource(R.string.petal_setup_access_title),
            body = stringResource(R.string.petal_setup_access_body)
        )

        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.petal_setup_access_progress, granted, items.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                LinearProgressIndicator(
                    progress = { if (items.isEmpty()) 0f else granted / items.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                AnimatedVisibility(visible = recommended.isNotEmpty()) {
                    Button(onClick = { request(*recommended.toTypedArray()) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.DoneAll, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.petal_setup_grant_recommended), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        PetalGroup(rowCount = items.size) { index, position ->
            val item = items[index]
            val isGranted = remember(refresh, item.id) { item.isGranted(context) }
            val blocked = !isGranted && permanentlyDenied(item)
            Card(
                onClick = { if (blocked) openAppSettings() else if (!isGranted) request(item) },
                shape = petalGroupShape(position),
                colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PetalGroupIconBadge(
                        icon = item.icon,
                        container = if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(item.title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (item.optional) {
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                                    Text(
                                        stringResource(R.string.petal_setup_optional),
                                        Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Text(stringResource(item.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    when {
                        isGranted -> Icon(Icons.Rounded.CheckCircle, stringResource(R.string.petal_setup_granted), tint = MaterialTheme.colorScheme.primary)
                        blocked -> FilledTonalButton(onClick = { openAppSettings() }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.petal_setup_open_settings), style = MaterialTheme.typography.labelSmall)
                        }
                        else -> Button(onClick = { request(item) }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.ui_grant), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Page 4 · Search & AI
// ─────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SearchPage(
    sp: SharedPreferences,
    engines: List<SearchEngineItem>,
    selectedEngine: Int,
    onSelectEngine: (Int) -> Unit
) {
    var aiOn by remember { mutableStateOf(!sp.getString("sp_gemini_api_key", "").isNullOrBlank()) }
    var aiKey by remember { mutableStateOf(sp.getString("sp_gemini_api_key", "") ?: "") }
    var reveal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SetupPageHeader(
            icon = Icons.Rounded.Search,
            badgeShape = PetalMaterialShapes.SoftBurst.toShape(),
            title = stringResource(R.string.petal_setup_you_title),
            body = stringResource(R.string.petal_setup_you_body)
        )

        SetupSectionLabel(stringResource(R.string.petal_setup_search))
        PetalGroup(rowCount = engines.size) { index, position ->
            val engine = engines[index]
            val selected = selectedEngine == engine.index
            Card(
                onClick = { onSelectEngine(engine.index) },
                shape = petalGroupShape(position),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else petalGroupSurfaceColor()
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(if (selected) PetalMaterialShapes.Cookie6Sided.toShape() else CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            engine.name.firstOrNull()?.uppercase() ?: "?",
                            fontWeight = FontWeight.Black,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(engine.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Text(engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RadioButton(selected = selected, onClick = { onSelectEngine(engine.index) })
                }
            }
        }

        SetupSectionLabel(stringResource(R.string.ui_ai_assistant))
        PetalGroup(rowCount = 1) { _, position ->
            PetalSettingsToggleRow(
                title = stringResource(R.string.petal_setup_ai_optional),
                subtitle = stringResource(R.string.petal_setup_ai_reason),
                icon = Icons.Rounded.AutoAwesome,
                checked = aiOn,
                onCheckedChange = {
                    aiOn = it
                    if (!it) {
                        aiKey = ""
                        sp.edit().remove("sp_gemini_api_key").apply()
                    }
                },
                position = position
            )
        }
        AnimatedVisibility(visible = aiOn) {
            TextField(
                value = aiKey,
                onValueChange = {
                    aiKey = it.trim()
                    sp.edit().putString("sp_gemini_api_key", aiKey).apply()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.petal_setup_ai_key)) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Page 5 · Ready
// ─────────────────────────────────────────────────────────────────────────────────────────────────

internal data class SetupSummary(
    val language: String,
    val colors: String,
    val engine: String,
    val adBlock: Boolean,
    val permissionsGranted: Int,
    val xpAvailable: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReadyPage(activity: ComponentActivity, summary: SetupSummary) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isDefault = remember(refresh) { PetalBrowserPermissionDialog.isDefaultBrowser(activity) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SetupPageHeader(
            icon = Icons.Rounded.Favorite,
            badgeShape = PetalMaterialShapes.Heart.toShape(),
            title = stringResource(R.string.petal_setup_ready_title),
            body = stringResource(R.string.petal_setup_ready_body)
        )

        SetupSectionLabel(stringResource(R.string.petal_setup_ready_summary_label))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryChip(Icons.Rounded.Translate, summary.language)
            SummaryChip(Icons.Rounded.Palette, summary.colors)
            SummaryChip(Icons.Rounded.Search, summary.engine)
            SummaryChip(
                if (summary.adBlock) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe,
                stringResource(if (summary.adBlock) R.string.petal_setup_ready_chip_shield_on else R.string.petal_setup_ready_chip_shield_off)
            )
            SummaryChip(Icons.Rounded.Security, stringResource(R.string.petal_setup_ready_chip_perms, summary.permissionsGranted))
        }

        PetalGroup(rowCount = 1) { _, position ->
            PetalGroupRow(
                icon = if (isDefault) Icons.Rounded.CheckCircle else Icons.Rounded.Public,
                title = stringResource(if (isDefault) R.string.petal_setup_ready_default_done else R.string.petal_setup_ready_default_title),
                subtitle = stringResource(R.string.petal_setup_ready_default_reason),
                position = position,
                onClick = { if (!isDefault) PetalBrowserPermissionDialog.requestSetDefaultBrowser(activity) },
                iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        if (summary.xpAvailable) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        stringResource(R.string.petal_setup_ready_xp, PetalSetupGate.WELCOME_XP.toInt()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
