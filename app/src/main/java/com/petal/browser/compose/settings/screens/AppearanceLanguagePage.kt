package com.petal.browser.compose.settings.screens

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
import com.petal.browser.ui.theme.PetalText
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
fun AppearanceLanguagePage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val matchWebsiteLanguage by viewModel.matchWebsiteLanguage.collectAsStateWithLifecycle()
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsStateWithLifecycle()
    val liquidGlassContainments by viewModel.liquidGlassContainments.collectAsStateWithLifecycle()
    val liquidGlassSheen by viewModel.liquidGlassSheen.collectAsStateWithLifecycle()
    val onAppLanguageChange: (String) -> Unit = viewModel::setAppLanguage
    val onMatchWebsiteLanguageChange: (Boolean) -> Unit = viewModel::setMatchWebsiteLanguage

    var showLanguageSheet by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }

    AppearanceSubPageScaffold(
        title = "App Language",
        subtitle = "Display language and website language",
        pageSeed = "appearance_language",
        onBack = onBack,
        modifier = modifier,
        overlay = {
            if (showLanguageSheet) {
                com.petal.browser.ui.containment.PetalSheet(
                    onDismissRequest = { showLanguageSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.ui_app_language),
                            style = PetalText.sheetTitle
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.ui_choose_your_preferred_display_language),
                            style = PetalText.pageSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        val langSearchShape = RoundedCornerShape(16.dp)
                        val isSheetGlass = liquidGlassEnabled && liquidGlassContainments
                        TextField(
                            value = languageSearchQuery,
                            onValueChange = { languageSearchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassChrome(shape = langSearchShape, enabled = isSheetGlass, sheenIntensity = 0.45f),
                            placeholder = { Text("Search language...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (languageSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { languageSearchQuery = "" }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = langSearchShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = if (isSheetGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = if (isSheetGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ), glass = false
                        )

                        Spacer(Modifier.height(12.dp))

                        val filteredLanguages = remember(languageSearchQuery) {
                            if (languageSearchQuery.isBlank()) {
                                PetalLanguages.ALL_LANGUAGES
                            } else {
                                val q = languageSearchQuery.trim().lowercase()
                                PetalLanguages.ALL_LANGUAGES.filter {
                                    it.nativeName.lowercase().contains(q) ||
                                    it.englishName.lowercase().contains(q) ||
                                    it.tag.lowercase().contains(q)
                                }
                            }
                        }

                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(count = filteredLanguages.size, key = { filteredLanguages[it].tag }) { idx ->
                                val lang = filteredLanguages[idx]
                                val isSelected = appLanguage == lang.tag
                                val position = com.petal.browser.ui.containment.petalGroupPositionFor(idx, filteredLanguages.size)
                                com.petal.browser.ui.containment.PetalGroupListRow(
                                    position = position,
                                    selected = isSelected,
                                    onClick = {
                                        if (appLanguage != lang.tag) {
                                            onAppLanguageChange(lang.tag)
                                            PetalAndroidUtils.setAppLanguage(context, lang.tag)
                                        }
                                        showLanguageSheet = false
                                    },
                                    leading = {
                                        val badgeContainer = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                                        val badgeTint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        com.petal.browser.ui.containment.PetalGroupIconBadge(
                                            icon = Icons.Rounded.Translate,
                                            container = badgeContainer,
                                            tint = badgeTint
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = lang.displayLabel,
                                            style = PetalText.rowTitle.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (lang.tag != "system") "BCP-47: ${lang.tag} • ${lang.nativeName}" else stringResource(R.string.ui_follow_device_language),
                                            style = PetalText.rowSubtitle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailing = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    ) {
        val currentLanguage = remember(appLanguage) { PetalLanguages.findLanguage(appLanguage) }
        PetalSettingsSection(
            title = stringResource(R.string.ui_app_language),
            iconRes = com.petal.browser.R.drawable.translate,
            cardId = "appearance_language",
            targetHighlightId = targetHighlightItemId
        ) {
            Text(
                stringResource(R.string.ui_choose_your_preferred_display_language),
                style = PetalText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Containment Group: Quick Languages & Website Language Setting
            val isGlass = liquidGlassEnabled && liquidGlassContainments
            val glassSheen = if (isGlass) liquidGlassSheen.coerceIn(0f, 1f) else 0.60f
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Top item: Quick languages horizontal row
                val quickLangShape = com.petal.browser.ui.containment.petalGroupShape(com.petal.browser.ui.containment.PetalGroupPosition.TOP)
                Card(
                    shape = quickLangShape,
                    colors = CardDefaults.cardColors(containerColor = com.petal.browser.ui.containment.petalGroupSurfaceColor()),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassChrome(
                            shape = quickLangShape,
                            enabled = isGlass,
                            sheenIntensity = glassSheen
                        ), glass = false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            com.petal.browser.ui.containment.PetalGroupIconBadge(
                                icon = Icons.Rounded.Language,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 38.dp,
                                iconSize = 20.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.ui_quick_languages),
                                    style = PetalText.rowTitle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.ui_switch_common_languages_instantly),
                                    style = PetalText.rowSubtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val quickLanguages = remember {
                            listOf(
                                PetalLanguages.findLanguage("system"),
                                PetalLanguages.findLanguage("en"),
                                PetalLanguages.findLanguage("hi-Latn"),
                                PetalLanguages.findLanguage("hi"),
                                PetalLanguages.findLanguage("es")
                            )
                        }

                        val quickLangScrollState = rememberScrollState()
                        ScrollFadeRow(
                            scrollState = quickLangScrollState,
                            edgeColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(quickLangScrollState),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quickLanguages.forEach { lang ->
                                    val isSelected = appLanguage == lang.tag
                                    com.petal.browser.ui.containment.PetalFilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (appLanguage != lang.tag) {
                                                onAppLanguageChange(lang.tag)
                                                PetalAndroidUtils.setAppLanguage(context, lang.tag)
                                            }
                                        },
                                        label = { Text(lang.nativeName) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                // Middle item: Action row to open All Languages modal sheet
                com.petal.browser.ui.containment.PetalGroupRow(
                    icon = Icons.Rounded.TravelExplore,
                    title = stringResource(R.string.ui_all_languages),
                    subtitle = "${PetalLanguages.ALL_LANGUAGES.size} languages supported",
                    position = com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE,
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        languageSearchQuery = ""
                        showLanguageSheet = true
                    }
                )

                // Bottom item: Match Website Language toggle
                com.petal.browser.ui.containment.PetalSettingsToggleRow(
                    title = stringResource(R.string.ui_website_language_title),
                    subtitle = stringResource(R.string.ui_website_language_subtitle),
                    icon = Icons.Rounded.Http,
                    checked = matchWebsiteLanguage,
                    onCheckedChange = { checked ->
                        onMatchWebsiteLanguageChange(checked)
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit()
                            .putBoolean("sp_match_website_language", checked)
                            .apply()
                    },
                    position = com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM
                )
            }
        }
    }
}
