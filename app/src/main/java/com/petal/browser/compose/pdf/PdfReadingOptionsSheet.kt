package com.petal.browser.compose.pdf

import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.theme.PetalText

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.R
import com.petal.browser.ui.blur.PetalBlurSurface
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.PetalSheetCard
import com.petal.browser.ui.containment.PetalSheetCheckBadge
import com.petal.browser.ui.containment.PetalSheetChoicePill
import com.petal.browser.ui.containment.PetalSheetHeader
import com.petal.browser.ui.containment.PetalSheetListRow
import com.petal.browser.ui.containment.PetalSheetSectionLabel
import com.petal.browser.ui.containment.PetalSheetSegment
import com.petal.browser.ui.containment.PetalSheetSegmentedBar
import com.petal.browser.ui.containment.PetalSheetToggleRow
import com.petal.browser.ui.containment.PetalSheetTone
import com.petal.browser.ui.containment.petalSheetSurface
import com.petal.browser.ui.theme.SmoothRoundedShape
import java.util.Locale

/**
 * PDF "Reading options" sheet in the Petal sheet language (same building blocks as the tab glance sheet):
 * 80dp rounded header visual, light title, tonal pills, cookie check badges and a bottom segmented bar.
 *
 *  - Material 3 Expressive: filled tonal containers.
 *  - Liquid Glass ("sp_liquid_glass_containments"): every part becomes a live glass chip through
 *    `petalSheetSurface`, the sheet paints the shared glass backdrop, and the switches become the
 *    liquid toggle. No extra wiring is needed here, [PetalSheet] and the parts read the same pref.
 *  - Background blur: [PetalSheet] runs the shared window blur for [PetalBlurSurface.SHEETS], so this
 *    sheet follows Settings → Appearance → Background blur (master switch, method, strength, dim and the
 *    "Bottom sheets" row) exactly like every other sheet.
 *
 * State is hoisted; the caller owns view mode, tone, auto-scroll and crop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfReadingOptionsSheet(
    viewMode: PdfViewMode,
    onViewModeChange: (PdfViewMode) -> Unit,
    readingTone: PdfReadingTone,
    onReadingToneChange: (PdfReadingTone) -> Unit,
    isAutoScrolling: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    autoScrollSpeed: Float,
    onAutoScrollSpeedChange: (Float) -> Unit,
    isCropMarginsEnabled: Boolean,
    onCropMarginsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    val continuous = viewMode == PdfViewMode.CONTINUOUS_VERTICAL

    PetalSheet(
        onDismissRequest = onDismiss,
        blurSurface = PetalBlurSurface.SHEETS,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            PetalSheetHeader(
                title = stringResource(R.string.ui_pdf_reading_settings),
                subtitle = stringResource(if (continuous) R.string.ui_pdf_continuous else R.string.ui_pdf_page_by_page),
                modifier = Modifier.padding(horizontal = 16.dp),
                leading = {
                    Box(
                        Modifier
                            .size(80.dp)
                            .petalSheetSurface(SmoothRoundedShape(24.dp), MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(animationSpec = PetalSpring.calm()) togetherWith fadeOut(animationSpec = PetalSpring.calm()) },
                label = "pdf_reading_page",
                modifier = Modifier.animateContentSize(),
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxBody)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (current == 0) {
                        DisplayPage(viewMode, onViewModeChange, readingTone, onReadingToneChange)
                    } else {
                        ScrollPage(
                            continuous = continuous,
                            isAutoScrolling = isAutoScrolling,
                            onAutoScrollChange = onAutoScrollChange,
                            autoScrollSpeed = autoScrollSpeed,
                            onAutoScrollSpeedChange = onAutoScrollSpeedChange,
                            isCropMarginsEnabled = isCropMarginsEnabled,
                            onCropMarginsChange = onCropMarginsChange,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            PetalSheetSegmentedBar(
                segments = listOf(
                    PetalSheetSegment(stringResource(R.string.ui_pdf_options_display), Icons.Rounded.Palette),
                    PetalSheetSegment(stringResource(R.string.ui_pdf_options_scroll), Icons.Rounded.Speed),
                ),
                selectedIndex = page,
                onSelect = { page = it },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun DisplayPage(
    viewMode: PdfViewMode,
    onViewModeChange: (PdfViewMode) -> Unit,
    readingTone: PdfReadingTone,
    onReadingToneChange: (PdfReadingTone) -> Unit,
) {
    PetalSheetSectionLabel(stringResource(R.string.ui_pdf_view_mode), Modifier.padding(horizontal = 4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PetalSheetChoicePill(
            text = stringResource(R.string.ui_pdf_continuous),
            icon = Icons.Rounded.ViewDay,
            selected = viewMode == PdfViewMode.CONTINUOUS_VERTICAL,
            onClick = { onViewModeChange(PdfViewMode.CONTINUOUS_VERTICAL) },
            modifier = Modifier.weight(1f),
        )
        PetalSheetChoicePill(
            text = stringResource(R.string.ui_pdf_page_by_page),
            icon = Icons.Rounded.ViewCarousel,
            selected = viewMode == PdfViewMode.PAGE_PAGER_HORIZONTAL,
            onClick = { onViewModeChange(PdfViewMode.PAGE_PAGER_HORIZONTAL) },
            modifier = Modifier.weight(1f),
        )
    }

    PetalSheetSectionLabel(stringResource(R.string.ui_pdf_reading_tone), Modifier.padding(horizontal = 4.dp))
    ToneRow(stringResource(R.string.ui_pdf_tone_default), Icons.Rounded.LightMode, PetalSheetTone.Primary,
        readingTone == PdfReadingTone.DEFAULT) { onReadingToneChange(PdfReadingTone.DEFAULT) }
    ToneRow(stringResource(R.string.ui_pdf_tone_sepia), Icons.Rounded.Coffee, PetalSheetTone.Tertiary,
        readingTone == PdfReadingTone.SEPIA) { onReadingToneChange(PdfReadingTone.SEPIA) }
    ToneRow(stringResource(R.string.ui_pdf_tone_night), Icons.Rounded.DarkMode, PetalSheetTone.Neutral,
        readingTone == PdfReadingTone.DARK_INVERTED) { onReadingToneChange(PdfReadingTone.DARK_INVERTED) }
}

@Composable
private fun ToneRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: PetalSheetTone, selected: Boolean, onClick: () -> Unit) {
    PetalSheetListRow(
        title = title,
        icon = icon,
        tone = tone,
        selected = selected,
        onClick = onClick,
        trailing = if (selected) ({ PetalSheetCheckBadge() }) else null,
    )
}

@Composable
private fun ScrollPage(
    continuous: Boolean,
    isAutoScrolling: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    autoScrollSpeed: Float,
    onAutoScrollSpeedChange: (Float) -> Unit,
    isCropMarginsEnabled: Boolean,
    onCropMarginsChange: (Boolean) -> Unit,
) {
    if (continuous) {
        PetalSheetCard(shape = RoundedCornerShape(36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            PetalSheetToggleRow(
                title = stringResource(R.string.ui_pdf_auto_scroll),
                subtitle = stringResource(R.string.ui_pdf_scroll_speed) + ": " +
                    String.format(Locale.getDefault(), "%.1fx", autoScrollSpeed),
                icon = Icons.Rounded.PlayArrow,
                tone = PetalSheetTone.Primary,
                checked = isAutoScrolling,
                onCheckedChange = onAutoScrollChange,
                switchIcon = Icons.Rounded.PlayArrow,
            )
            PetalSlider(
                value = autoScrollSpeed,
                onValueChange = { onAutoScrollSpeedChange(Math.round(it * 10f) / 10f) },
                valueRange = 0.5f..5.0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .petalSheetSurface(RoundedCornerShape(32.dp), MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.ui_pdf_auto_scroll_unavailable),
                style = PetalText.cardBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    PetalSheetToggleRow(
        title = stringResource(R.string.ui_pdf_crop_margins),
        icon = Icons.Rounded.CropFree,
        tone = PetalSheetTone.Secondary,
        checked = isCropMarginsEnabled,
        onCheckedChange = onCropMarginsChange,
        switchIcon = Icons.Rounded.CropFree,
    )
}
