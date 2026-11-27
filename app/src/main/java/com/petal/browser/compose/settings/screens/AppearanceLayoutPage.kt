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
fun AppearanceLayoutPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val floatingTabBar by viewModel.floatingTabBar.collectAsStateWithLifecycle()
    val onFloatingTabBarChange: (Boolean) -> Unit = viewModel::setFloatingTabBar

    AppearanceSubPageScaffold(
        title = "Layout & Shapes",
        subtitle = "Tab bar, controls, shapes & text selection",
        pageSeed = "appearance_layout",
        onBack = onBack,
        modifier = modifier
    ) {
        PetalSettingsSection(
            title = "Tab bar",
            iconRes = com.petal.browser.R.drawable.layers_filled,
            cardId = "appearance_layout",
            highlightIds = listOf("appearance_floating_tab_bar"),
            targetHighlightId = targetHighlightItemId
        ) {
            // Floating Tab Bar Toggle
            com.petal.browser.ui.containment.PetalSettingsToggleRow(
                title = stringResource(R.string.ui_floating_tab_bar),
                subtitle = stringResource(R.string.ui_show_the_bottom_bar_as),
                icon = Icons.Rounded.SpaceBar,
                checked = floatingTabBar,
                position = com.petal.browser.ui.containment.PetalGroupPosition.SINGLE,
                onCheckedChange = onFloatingTabBarChange
            )
            PetalDesktopTabBarModeRow()
        }

        // Section 3b: Expressive Layout & Controls (Ported from ImageToolbox)
        PetalLayoutSettingsSection(
            targetHighlightItemId = targetHighlightItemId
        )

        // Button Shapes & Expressive Keyboard Geometry
        PetalSettingsSection(
            title = "Button Shapes",
            iconRes = com.petal.browser.R.drawable.icon_cookie,
            cardId = "appearance_button_shapes",
            targetHighlightId = targetHighlightItemId
        ) {
            PetalButtonShapePicker()
        }

        // Text selection toolbar (Material 3 Expressive / liquid glass), app-wide.
        com.petal.browser.ui.selection.PetalSelectionToolbarSettingsSection(targetHighlightId = targetHighlightItemId)
    }
}

/**
 * Auto / Always / Never choice for the desktop-style tab strip. Auto shows it on tablets and wide
 * landscape windows (>= 600dp), Always forces it on phones too, Never hides it.
 */
@androidx.compose.runtime.Composable
private fun PetalDesktopTabBarModeRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var mode by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.petal.browser.compose.tabs.PetalDesktopTabBarBridge.getMode(context)
        )
    }
    val modeItems = listOf(
        com.petal.browser.compose.tabs.PetalDesktopTabBarBridge.MODE_AUTO to R.string.ui_desktop_tab_bar_auto,
        com.petal.browser.compose.tabs.PetalDesktopTabBarBridge.MODE_ALWAYS to R.string.ui_desktop_tab_bar_always,
        com.petal.browser.compose.tabs.PetalDesktopTabBarBridge.MODE_NEVER to R.string.ui_desktop_tab_bar_never
    ).map { (value, label) -> com.petal.browser.ui.components.ExpressiveSegmentItem(id = value, label = stringResource(label)) }
    com.petal.browser.ui.containment.PetalHeroCard {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            // The title previously had no colour, so outside a Surface it inherited black and was
            // invisible on dark themes, leaving the subtitle orphaned.
            androidx.compose.material3.Text(
                text = stringResource(R.string.ui_desktop_tab_bar),
                style = PetalText.rowTitle,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.material3.Text(
                text = stringResource(R.string.ui_desktop_tab_bar_subtitle),
                style = PetalText.rowSubtitle,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            com.petal.browser.ui.components.ExpressiveButtonGroup(
                items = modeItems,
                selectedId = mode,
                onItemSelected = { value ->
                    mode = value
                    com.petal.browser.compose.tabs.PetalDesktopTabBarBridge.setMode(context, value)
                },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
