package com.petal.browser.compose.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.predictive.PetalBackExitDirection
import com.petal.browser.predictive.PetalBackStyle
import com.petal.browser.predictive.PetalBackStylePreview
import com.petal.browser.ui.components.PetalSlider
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import kotlin.math.roundToInt

@Composable
private fun PetalBackStyle.label(): String = stringResource(
    when (this) {
        PetalBackStyle.Petal -> R.string.ui_back_style_petal
        PetalBackStyle.Aosp -> R.string.ui_back_style_aosp
        PetalBackStyle.Miuix -> R.string.ui_back_style_miuix
        PetalBackStyle.Scale -> R.string.ui_back_style_scale
        PetalBackStyle.Classic -> R.string.ui_back_style_classic
    }
)

/**
 * Shown under the "Predictive Back Animations" switch while it is on. Moving the slider (or
 * tapping a label) switches the style immediately; the preview loops that style's gesture with
 * the depth-blur setting applied, so the combination is visible before you leave the screen.
 */
@Composable
fun PetalBackStyleSettingsRow(
    position: PetalGroupPosition,
    style: PetalBackStyle,
    exitDirection: PetalBackExitDirection,
    depthBlur: Boolean,
    onStyleChange: (PetalBackStyle) -> Unit,
    onExitDirectionChange: (PetalBackExitDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val shape = petalGroupShape(position)

    // Local copy so the thumb and preview react on the very frame the finger moves.
    var sliderValue by remember { mutableFloatStateOf(style.index.toFloat()) }
    LaunchedEffect(style) { sliderValue = style.index.toFloat() }
    val shownStyle = PetalBackStyle.fromIndex(sliderValue.roundToInt())

    fun choose(target: PetalBackStyle) {
        if (target == shownStyle) return
        sliderValue = target.index.toFloat()
        PetalHapticEngine.getInstance(context).playClick(context)
        onStyleChange(target)
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = petalGroupSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(shape = shape, enabled = isGlass, sheenIntensity = sheen),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetalGroupIconBadge(
                    Icons.Rounded.Animation,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.ui_back_style_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.ui_back_style_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PetalBackStylePreview(
                    style = shownStyle,
                    blurEnabled = depthBlur,
                    exitDirection = exitDirection,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        shownStyle.label(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(
                            if (depthBlur) R.string.ui_back_style_blur_on else R.string.ui_back_style_blur_off
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PetalSlider(
                value = sliderValue,
                onValueChange = { raw ->
                    sliderValue = raw
                    choose(PetalBackStyle.fromIndex(raw.roundToInt()))
                },
                valueRange = 0f..(PetalBackStyle.entries.size - 1).toFloat(),
                steps = PetalBackStyle.entries.size - 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth()) {
                PetalBackStyle.entries.forEach { entry ->
                    val selected = entry == shownStyle
                    Text(
                        text = entry.label(),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { choose(entry) }
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = shownStyle == PetalBackStyle.Scale,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.ui_back_exit_direction),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetalBackExitDirection.entries.forEach { direction ->
                            FilterChip(
                                selected = direction == exitDirection,
                                onClick = { onExitDirectionChange(direction) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (direction) {
                                                PetalBackExitDirection.FollowGesture -> R.string.ui_back_exit_follow
                                                PetalBackExitDirection.AlwaysRight -> R.string.ui_back_exit_right
                                                PetalBackExitDirection.AlwaysLeft -> R.string.ui_back_exit_left
                                            }
                                        )
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
