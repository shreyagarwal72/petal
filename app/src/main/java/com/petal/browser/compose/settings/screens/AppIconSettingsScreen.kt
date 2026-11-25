package com.petal.browser.compose.settings.screens

import com.petal.browser.ui.theme.PetalText

import com.petal.browser.ui.components.PetalSpring

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petal.browser.R
import com.petal.browser.appicon.PetalAppIcon
import com.petal.browser.appicon.PetalAppIcons
import com.petal.browser.compose.settings.viewmodel.AppearanceSettingsViewModel
import com.petal.browser.compose.splash.PetalSplashScreen
import com.petal.browser.compose.splash.PetalSplashState
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalSettingsSection
import com.petal.browser.ui.containment.PetalSettingsToggleRow
import com.petal.browser.ui.layout.LiquidRippleEffect

private const val ICONS_PER_ROW = 3

/**
 * Settings > App Icon & Intro.
 *
 * Queues launcher icon switches cleanly in `sp_pending_app_icon` to apply when Petal closes or
 * backgrounds, configures startup launch style (`intro` / `quantum`), and manages launch ripple.
 */
@Composable
fun AppIconSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val activeIcon = remember(context) { PetalAppIcons.current(context) }
    var selected by remember(context) { mutableStateOf(PetalAppIcons.pending(context) ?: activeIcon) }
    val launchStyle by viewModel.launchStyle.collectAsStateWithLifecycle()
    val launchRippleEnabled by viewModel.launchRippleEnabled.collectAsStateWithLifecycle()
    var showIntro by remember { mutableStateOf(false) }
    var showQuantumPreview by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            pageBackground = { com.petal.browser.ui.components.PetalPageBackground(pageKey = "app_icon_settings", modifier = Modifier.fillMaxSize()) },
            header = { headerModifier, headerBackdrop ->
                ExpressiveHeader(
                    modifier = headerModifier,
                    backdrop = headerBackdrop,
                    title = stringResource(R.string.app_icon_title),
                    subtitle = stringResource(R.string.app_icon_subtitle),
                    onBack = onNavigateBack
                )
            }
        ) { topInset ->

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .scrollMotionBlur(scrollState)
                    .verticalScroll(scrollState, flingBehavior = com.petal.browser.ui.theme.layout.petalFlingBehavior())
                    .padding(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PetalSettingsSection(
                    title = stringResource(R.string.app_icon_choose),
                    icon = Icons.Rounded.Spa,
                    cardId = "app_icon_choose",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = stringResource(R.string.app_icon_choose_hint),
                        style = PetalText.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    PetalAppIcon.values().toList().chunked(ICONS_PER_ROW).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { icon ->
                                IconChoice(
                                    icon = icon,
                                    selected = icon == selected,
                                    onClick = { center ->
                                        if (icon != selected) {
                                            PetalAppIcons.queueIconChange(context, icon)
                                            selected = icon
                                            LiquidRippleEffect.trigger(hostView, center?.x, center?.y)
                                        }
                                    }
                                )
                            }
                            repeat(ICONS_PER_ROW - row.size) { Spacer(Modifier.width(IconCellWidth)) }
                        }
                    }
                    Text(
                        text = stringResource(selected.descriptionRes),
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    val selectedName = stringResource(selected.labelRes)
                    Text(
                        text = if (selected != activeIcon) {
                            "The new $selectedName icon appears after you close Petal."
                        } else {
                            "The new icon appears after you close Petal."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected != activeIcon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                PetalSettingsSection(
                    title = "Launch style",
                    icon = Icons.Rounded.AutoAwesome,
                    cardId = "app_icon_intro",
                    highlightIds = listOf("app_icon_launch_style", "app_icon_quantum", "app_icon_ripple"),
                    targetHighlightId = targetHighlightItemId
                ) {
                    val styleOptions = listOf(
                        Triple(
                            PetalSplashState.STYLE_INTRO,
                            "App Intro",
                            "Animated icon splash matched to your chosen Petal icon"
                        ),
                        Triple(
                            PetalSplashState.STYLE_QUANTUM,
                            "Quantum Web Animation",
                            "Procedural stream of web icons fading in from the system splash"
                        )
                    )
                    PetalGroup(rowCount = styleOptions.size) { index, position ->
                        val (styleKey, title, subtitle) = styleOptions[index]
                        val isSelected = launchStyle == styleKey
                        PetalGroupListRow(
                            position = position,
                            selected = isSelected,
                            onClick = { viewModel.setLaunchStyle(styleKey) },
                            leading = {
                                PetalGroupIconBadge(
                                    icon = if (styleKey == PetalSplashState.STYLE_INTRO) Icons.Rounded.Spa else Icons.Rounded.AutoAwesome,
                                    variant = if (isSelected) PetalBadgeVariant.PRIMARY else PetalBadgeVariant.SURFACE_TONAL
                                )
                            },
                            content = {
                                Text(
                                    text = title,
                                    style = PetalText.rowTitle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitle,
                                    style = PetalText.rowSubtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                FilledTonalButton(
                                    onClick = {
                                        if (styleKey == PetalSplashState.STYLE_INTRO) {
                                            showIntro = true
                                        } else {
                                            showQuantumPreview = true
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Preview")
                                }
                            },
                            trailing = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.app_icon_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    PetalSettingsToggleRow(
                        title = stringResource(R.string.ui_app_launch_ripple_effect),
                        subtitle = stringResource(R.string.ui_display_fluid_liquid_displacement_ripp),
                        icon = Icons.Rounded.WaterDrop,
                        checked = launchRippleEnabled,
                        position = PetalGroupPosition.SINGLE,
                        onCheckedChange = viewModel::setLaunchRippleEnabled
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { LiquidRippleEffect.trigger(hostView, force = true) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Preview Ripple")
                        }
                    }
                }
            }
        }
    }

    if (showIntro) {
        IntroPreviewDialog(icon = selected, onDismiss = { showIntro = false })
    }

    if (showQuantumPreview) {
        Dialog(
            onDismissRequest = { showQuantumPreview = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                PetalSplashScreen(
                    onSplashComplete = { showQuantumPreview = false },
                    durationMs = 2200L,
                    markSessionShown = false
                )
            }
        }
    }
}

private val IconCellWidth = 100.dp

@Composable
private fun IconChoice(icon: PetalAppIcon, selected: Boolean, onClick: (Offset?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = PetalSpring.calm(),
        label = "app_icon_choice_scale"
    )
    var cardCenter by remember { mutableStateOf<Offset?>(null) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .width(IconCellWidth)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                cardCenter = Offset(
                    pos.x + coords.size.width / 2f,
                    pos.y + coords.size.height / 2f
                )
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick(cardCenter) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(if (selected) 3.dp else 0.dp, scheme.primary, shape)
                .clip(shape)
        ) {
            // Adaptive icons are 108dp with the visible area in the middle 72dp (2/3); scale the layers
            // up by 1.5 so our own rounded clip shows exactly the visible part, full-bleed.
            DrawableImage(
                res = icon.launcherRes,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
                flattenAdaptive = true
            )
            if (selected) {
                Surface(
                    color = scheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.app_icon_selected),
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(icon.labelRes),
            style = PetalText.chip.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IntroPreviewDialog(icon: PetalAppIcon, onDismiss: () -> Unit) {
    var replay by remember { mutableStateOf(0) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = colorResource(R.color.petal_splash_bg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // key(replay) re-creates the drawable, so Replay restarts the animation from frame 0.
                key(replay) {
                    DrawableImage(
                        res = icon.introRes,
                        modifier = Modifier.size(220.dp),
                        animate = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(icon.labelRes),
                    style = PetalText.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { replay++ }) { Text(stringResource(R.string.app_icon_intro_replay)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_icon_intro_done)) }
                }
            }
        }
    }
}

/**
 * Shows a framework drawable resource. [flattenAdaptive] draws an adaptive icon's background and
 * foreground layers unmasked (the caller applies its own shape); [animate] starts an AnimatedVectorDrawable.
 */
@Composable
private fun DrawableImage(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    flattenAdaptive: Boolean = false,
    animate: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                var d = ContextCompat.getDrawable(ctx, res)?.mutate()
                if (flattenAdaptive && d is AdaptiveIconDrawable) {
                    d = LayerDrawable(arrayOf(d.background, d.foreground))
                }
                setImageDrawable(d)
                val animatable = d as? Animatable
                if (animate && animatable != null) post { animatable.start() }
            }
        }
    )
}
