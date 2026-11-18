package com.petal.browser.compose.settings.screens

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Spa
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.petal.browser.R
import com.petal.browser.appicon.PetalAppIcon
import com.petal.browser.appicon.PetalAppIcons
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.components.scrollMotionBlur
import com.petal.browser.ui.containment.PetalSettingsSection

private const val ICONS_PER_ROW = 3

/**
 * Settings > App Icon & Intro.
 *
 * Lets the user switch the launcher icon (activity-aliases, see [PetalAppIcons]) and preview the launch
 * animation that belongs to it. Previews are the real resources, so they follow Material You exactly like
 * the launcher does.
 */
@Composable
fun AppIconSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetHighlightItemId: String? = null
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(PetalAppIcons.current(context)) }
    var showIntro by remember { mutableStateOf(false) }
    val changedMsg = stringResource(R.string.app_icon_changed)
    val failedMsg = stringResource(R.string.app_icon_failed)

    Box(modifier = modifier.fillMaxSize()) {
        M3ExpressiveVariableBackground(pageSeed = "app_icon_settings")

        com.petal.browser.ui.components.PetalGlassHeaderLayout(
            modifier = Modifier.fillMaxSize(),
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
                        style = MaterialTheme.typography.bodySmall,
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
                                    onClick = {
                                        if (icon != selected) {
                                            if (PetalAppIcons.apply(context, icon)) {
                                                selected = icon
                                                PetalAppIcons.findActivity(context)
                                                    ?.let { PetalAppIcons.syncSplashTheme(it, icon, force = true) }
                                                Toast.makeText(context, changedMsg, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                            repeat(ICONS_PER_ROW - row.size) { Spacer(Modifier.width(IconCellWidth)) }
                        }
                    }
                    Text(
                        text = stringResource(selected.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }

                PetalSettingsSection(
                    title = stringResource(R.string.app_icon_intro_title),
                    icon = Icons.Rounded.AutoAwesome,
                    cardId = "app_icon_intro",
                    targetHighlightId = targetHighlightItemId
                ) {
                    Text(
                        text = stringResource(R.string.app_icon_intro_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    FilledTonalButton(
                        onClick = { showIntro = true },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) { Text(stringResource(R.string.app_icon_intro_preview)) }
                    Text(
                        text = stringResource(R.string.app_icon_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (showIntro) {
        IntroPreviewDialog(icon = selected, onDismiss = { showIntro = false })
    }
}

private val IconCellWidth = 100.dp

@Composable
private fun IconChoice(icon: PetalAppIcon, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "app_icon_choice_scale"
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .width(IconCellWidth)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
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
            style = MaterialTheme.typography.labelMedium.copy(
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
                    style = MaterialTheme.typography.titleMedium,
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
