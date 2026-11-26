package com.petal.browser.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.motion.MotionGate
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.PetalGlassHeaderLayout
import com.petal.browser.ui.components.PetalPageBackground
import com.petal.browser.ui.components.rememberPetalToggleMorphShape
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.theme.PetalMaterialShapes

/**
 * Scanner settings screen allowing user to configure auto-open, haptics, beep,
 * history retention, and security warning flags using Petal M3E containment and Liquid Glass.
 */
@Composable
fun PetalScannerSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val stateHolder = rememberScannerSettingsStateHolder()
    val uiState by stateHolder.state.collectAsState()

    val isLowEndDevice = remember(context) { MotionGate.isLowEndDevice(context) }
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments") && !isLowEndDevice
    val isAmoled = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("sp_amoled", false)
    }

    val stateOnText = stringResource(R.string.scanner_settings_state_on)
    val stateOffText = stringResource(R.string.scanner_settings_state_off)

    val autoOpenTitle = stringResource(R.string.scanner_settings_auto_open_title)
    val autoOpenDesc = stringResource(R.string.scanner_settings_auto_open_desc)
    val vibrateTitle = stringResource(R.string.scanner_settings_vibrate_title)
    val vibrateDesc = stringResource(R.string.scanner_settings_vibrate_desc)
    val beepTitle = stringResource(R.string.scanner_settings_beep_title)
    val beepDesc = stringResource(R.string.scanner_settings_beep_desc)
    val safetyTitle = stringResource(R.string.scanner_settings_safety_title)
    val safetyDesc = stringResource(R.string.scanner_settings_safety_desc)
    val historyTitle = stringResource(R.string.scanner_settings_history_title)
    val historyDesc = stringResource(R.string.scanner_settings_history_desc)

    val autoOpenBadgeShape = rememberPetalToggleMorphShape(
        checked = uiState.autoOpen,
        offPolygon = PetalMaterialShapes.Square.polygon,
        onPolygon = PetalMaterialShapes.Cookie9Sided.polygon,
    )
    val vibrateBadgeShape = rememberPetalToggleMorphShape(
        checked = uiState.vibrate,
        offPolygon = PetalMaterialShapes.Square.polygon,
        onPolygon = PetalMaterialShapes.SoftBurst.polygon,
    )
    val beepBadgeShape = rememberPetalToggleMorphShape(
        checked = uiState.beep,
        offPolygon = PetalMaterialShapes.Square.polygon,
        onPolygon = PetalMaterialShapes.Flower.polygon,
    )
    val safetyBadgeShape = rememberPetalToggleMorphShape(
        checked = uiState.safetyWarnings,
        offPolygon = PetalMaterialShapes.Square.polygon,
        onPolygon = PetalMaterialShapes.Cookie9Sided.polygon,
    )
    val historyBadgeShape = rememberPetalToggleMorphShape(
        checked = uiState.historyEnabled,
        offPolygon = PetalMaterialShapes.Square.polygon,
        onPolygon = PetalMaterialShapes.SoftBurst.polygon,
    )

    PetalGlassHeaderLayout(
        modifier = Modifier.fillMaxSize(),
        enableLiquidGlass = isGlass,
        pageBackground = {
            if (isGlass) {
                PetalPageBackground(
                    pageKey = "scanner_settings",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.background)
                )
            }
        },
        header = { headerModifier, headerBackdrop ->
            ExpressiveHeader(
                title = stringResource(R.string.scanner_settings_title),
                subtitle = stringResource(R.string.scanner_settings_subtitle),
                onBack = onBack,
                backContentDescription = stringResource(R.string.scanner_back),
                enableLiquidGlass = isGlass,
                backdrop = headerBackdrop,
                modifier = headerModifier,
            )
        },
    ) { topInset ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = topInset + 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PetalSettingsSection(
                title = stringResource(R.string.scanner_settings_section_behavior),
                icon = Icons.Rounded.Tune,
            ) {
                PetalGroup(rowCount = 3) { index, position ->
                    when (index) {
                        0 -> PetalSettingsToggleRow(
                            title = autoOpenTitle,
                            subtitle = autoOpenDesc,
                            icon = Icons.Rounded.OpenInBrowser,
                            checked = uiState.autoOpen,
                            onCheckedChange = stateHolder::setAutoOpenEnabled,
                            position = position,
                            variant = PetalBadgeVariant.PRIMARY,
                            badgeShape = autoOpenBadgeShape,
                            enableLiquidGlass = isGlass,
                            modifier = Modifier.semantics {
                                contentDescription = "$autoOpenTitle. $autoOpenDesc"
                                stateDescription = if (uiState.autoOpen) stateOnText else stateOffText
                            },
                        )
                        1 -> PetalSettingsToggleRow(
                            title = vibrateTitle,
                            subtitle = vibrateDesc,
                            icon = Icons.Rounded.Vibration,
                            checked = uiState.vibrate,
                            onCheckedChange = stateHolder::setVibrateEnabled,
                            position = position,
                            variant = PetalBadgeVariant.PRIMARY,
                            badgeShape = vibrateBadgeShape,
                            enableLiquidGlass = isGlass,
                            modifier = Modifier.semantics {
                                contentDescription = "$vibrateTitle. $vibrateDesc"
                                stateDescription = if (uiState.vibrate) stateOnText else stateOffText
                            },
                        )
                        else -> PetalSettingsToggleRow(
                            title = beepTitle,
                            subtitle = beepDesc,
                            icon = Icons.Rounded.VolumeUp,
                            checked = uiState.beep,
                            onCheckedChange = stateHolder::setBeepEnabled,
                            position = position,
                            variant = PetalBadgeVariant.PRIMARY,
                            badgeShape = beepBadgeShape,
                            enableLiquidGlass = isGlass,
                            modifier = Modifier.semantics {
                                contentDescription = "$beepTitle. $beepDesc"
                                stateDescription = if (uiState.beep) stateOnText else stateOffText
                            },
                        )
                    }
                }
            }

            PetalSettingsSection(
                title = stringResource(R.string.scanner_settings_section_safety),
                icon = Icons.Rounded.Shield,
            ) {
                PetalGroup(rowCount = 2) { index, position ->
                    when (index) {
                        0 -> PetalSettingsToggleRow(
                            title = safetyTitle,
                            subtitle = safetyDesc,
                            icon = Icons.Rounded.GppGood,
                            checked = uiState.safetyWarnings,
                            onCheckedChange = stateHolder::setSafetyWarningsEnabled,
                            position = position,
                            variant = PetalBadgeVariant.TERTIARY,
                            badgeShape = safetyBadgeShape,
                            enableLiquidGlass = isGlass,
                            modifier = Modifier.semantics {
                                contentDescription = "$safetyTitle. $safetyDesc"
                                stateDescription = if (uiState.safetyWarnings) stateOnText else stateOffText
                            },
                        )
                        else -> PetalSettingsToggleRow(
                            title = historyTitle,
                            subtitle = historyDesc,
                            icon = Icons.Rounded.History,
                            checked = uiState.historyEnabled,
                            onCheckedChange = stateHolder::setHistoryEnabled,
                            position = position,
                            variant = PetalBadgeVariant.TERTIARY,
                            badgeShape = historyBadgeShape,
                            enableLiquidGlass = isGlass,
                            modifier = Modifier.semantics {
                                contentDescription = "$historyTitle. $historyDesc"
                                stateDescription = if (uiState.historyEnabled) stateOnText else stateOffText
                            },
                        )
                    }
                }
            }
        }
    }
}
