package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.theme.PetalText

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
fun AppearanceWidgetsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null
) {
    val context = LocalContext.current

    AppearanceSubPageScaffold(
        title = "Home Screen Widgets",
        subtitle = "Customize each Petal widget",
        pageSeed = "appearance_widgets",
        onBack = onBack,
        modifier = modifier
    ) {
        PetalSettingsSection(
            title = "Home Screen Widgets",
            icon = Icons.Rounded.Widgets,
            cardId = "appearance_search_widget",
            targetHighlightId = targetHighlightItemId
        ) {
            Text(
                "Customize colors, cookie shapes, shortcuts, corners and outline for each Petal widget.",
                style = PetalText.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val widgetRows = listOf(
                Triple(com.petal.browser.widget.glance.PetalWidgetKind.PETAL1, "Petal #1", "Search pill with cookie badge, buttons and shortcut tiles"),
                Triple(com.petal.browser.widget.glance.PetalWidgetKind.PETAL2, "Petal #2", "Search field with a cookie action island"),
                Triple(com.petal.browser.widget.glance.PetalWidgetKind.PETAL3, "Petal #3", "Large cookie badge with an open search prompt")
            )
            widgetRows.forEachIndexed { index, (kind, title, subtitle) ->
                com.petal.browser.ui.containment.PetalGroupNavigationRow(
                    title = title,
                    subtitle = subtitle,
                    position = when (index) {
                        0 -> com.petal.browser.ui.containment.PetalGroupPosition.TOP
                        widgetRows.lastIndex -> com.petal.browser.ui.containment.PetalGroupPosition.BOTTOM
                        else -> com.petal.browser.ui.containment.PetalGroupPosition.MIDDLE
                    },
                    leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                    onClick = {
                        context.startActivity(
                            com.petal.browser.widget.glance.PetalWidgetSettingsActivity.intent(context, kind)
                        )
                    }
                )
            }
        }
    }
}
