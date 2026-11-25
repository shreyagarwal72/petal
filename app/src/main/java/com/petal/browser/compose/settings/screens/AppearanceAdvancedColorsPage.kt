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
fun AppearanceAdvancedColorsPage(
    reveal: ThemeReveal,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val paletteId by viewModel.paletteId.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val themeSp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val customRevision = com.petal.browser.ui.theme.PetalThemePrefs.revision
    var pickerSlot by remember { mutableStateOf(-1) } // 0 primary, 1 secondary, 2 tertiary
    val isCustomPalette = paletteId == com.petal.browser.ui.theme.CUSTOM_PALETTE_ID && !dynamicColor

    AppearanceSubPageScaffold(
        title = "Custom palette colors",
        subtitle = "Customize primary seed, secondary and tertiary colors",
        pageSeed = "appearance_advanced_colors",
        onBack = onBack,
        modifier = modifier
    ) {
        PetalSettingsSection(
            title = "Custom palette colors",
            icon = Icons.Rounded.Tune,
            cardId = "appearance_advanced_colors",
            targetHighlightId = targetHighlightItemId
        ) {
            if (isCustomPalette) {
                val spec = remember(customRevision) { com.petal.browser.ui.theme.PetalCustomPalette.spec(context) }
                com.petal.browser.ui.containment.PetalGroup(rowCount = 3) { index, position ->
                    val (slotTitle, slotColor, slotHint) = when (index) {
                        0 -> Triple("Primary colour", spec.seed, "Seed for the whole palette")
                        1 -> Triple("Secondary colour", spec.secondary, "Auto: derived from the primary")
                        else -> Triple("Tertiary colour", spec.tertiary, "Auto: derived from the primary")
                    }
                    com.petal.browser.ui.containment.PetalGroupListRow(
                        position = position,
                        onClick = { pickerSlot = index },
                        leading = {
                            Box(
                                Modifier.size(40.dp).clip(ExpressiveButtonShape)
                                    .background(if (slotColor != 0) Color(slotColor) else MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                if (slotColor == 0) Text("Auto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        content = {
                            Text(slotTitle, style = PetalText.rowTitle, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (slotColor != 0) "#%06X".format(slotColor and 0xFFFFFF) else slotHint,
                                style = PetalText.rowSubtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = if (index > 0 && slotColor != 0) {
                            {
                                TextButton(onClick = {
                                    reveal.reveal {
                                        themeSp.edit().putInt(
                                            if (index == 1) com.petal.browser.ui.theme.PetalCustomPalette.KEY_SECONDARY
                                            else com.petal.browser.ui.theme.PetalCustomPalette.KEY_TERTIARY, 0
                                        ).apply()
                                        PetalSearchWidgetProvider.updateAllWidgets(context)
                                    }
                                }) { Text("Reset") }
                            }
                        } else null
                    )
                }
            } else {
                Surface(
                    shape = com.petal.browser.ui.containment.petalGroupShape(com.petal.browser.ui.containment.PetalGroupPosition.SINGLE),
                    color = com.petal.browser.ui.containment.petalGroupSurfaceColor(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Custom palette not active",
                            style = PetalText.cardTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Select the 'Custom' palette preset on the Theme & Colors page (with Material You disabled) to customize your primary seed, secondary and tertiary colors.",
                            style = PetalText.cardBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AppearanceCustomColorPickerDialog(
                slot = pickerSlot,
                reveal = reveal,
                onDismiss = { pickerSlot = -1 }
            )
        }
    }
}
