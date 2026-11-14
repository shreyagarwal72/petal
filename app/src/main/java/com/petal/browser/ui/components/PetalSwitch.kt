package com.petal.browser.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.components.expressive.support.LocalSettingsState
import com.petal.browser.ui.components.expressive.support.ShapeDefaults
import com.petal.browser.ui.components.expressive.support.compositeOverSafe
import com.petal.browser.ui.components.expressive.support.container
import com.petal.browser.ui.components.expressive.switches.ComposeSwitch
import com.petal.browser.ui.components.expressive.switches.CupertinoSwitch
import com.petal.browser.ui.components.expressive.switches.CupertinoSwitchDefaults
import com.petal.browser.ui.components.expressive.switches.FluentSwitch
import com.petal.browser.ui.components.expressive.switches.HyperOSSwitch
import com.petal.browser.ui.components.expressive.switches.LiquidGlassSwitch
import com.petal.browser.ui.components.expressive.switches.M3Switch
import com.petal.browser.ui.components.expressive.switches.OneUISwitch
import com.petal.browser.ui.components.expressive.switches.PixelSwitch
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.theme.layout.PetalSwitchType

/**
 * Petal's switch. Renders whichever [PetalSwitchType] the user picked (live - changing the choice
 * restyles every switch on screen immediately) using the real ImageToolbox implementations.
 *
 * Liquid Glass is NOT a switch type: whenever Liquid Glass is active every switch automatically
 * becomes the liquid glass toggle (draggable, lens-refracting thumb), and returns to the chosen
 * type the moment Liquid Glass is turned off.
 *
 * [typeOverride] / [glassOverride] exist for previews (e.g. the picker sheet), not for app screens.
 */
@Composable
fun PetalSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbIcon: ImageVector? = null,
    enabled: Boolean = true,
    colors: SwitchColors? = null,
    interactionSource: MutableInteractionSource? = null,
    typeOverride: PetalSwitchType? = null,
    glassOverride: Boolean? = null,
    colorUnderSwitch: Color = Color.Unspecified,
) {
    val settings = LocalSettingsState.current
    val isGlass = glassOverride ?: isLiquidGlassEnabled("sp_liquid_glass_containments")
    val isMiuix = com.petal.browser.ui.containment.isMiuixEnabled()
    val type = typeOverride ?: if (isMiuix) PetalSwitchType.HYPEROS else settings.switchType
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current

    val switchColors = colors ?: SwitchDefaults.colors(
        disabledUncheckedThumbColor = scheme.onSurface
            .copy(alpha = 0.12f)
            .compositeOverSafe(scheme.surface)
    )

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        val shadow by animateDpAsState(if (settings.drawSwitchShadows) 1.dp else 0.dp, label = "switchShadow")
        val switchModifier = modifier
            .minimumInteractiveComponentSize()
            .container(
                shape = ShapeDefaults.circle,
                resultPadding = 0.dp,
                autoShadowElevation = shadow,
                borderColor = Color.Transparent,
                isShadowClip = true,
                isStandaloneContainer = false,
                color = Color.Transparent
            )

        val onChange: ((Boolean) -> Unit)? = onCheckedChange?.let {
            { value ->
                it(value)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                focus.clearFocus()
            }
        }

        val thumbContent: (@Composable () -> Unit)? = thumbIcon?.let { icon ->
            {
                AnimatedContent(icon, label = "switchThumbIcon") { current ->
                    Icon(
                        imageVector = current,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            }
        }

        // One key drives the cross-fade: glass wins over the chosen type while Liquid Glass is on.
        AnimatedContent(
            targetState = if (isGlass) null else type,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(false) },
            label = "switchType"
        ) { target ->
            when (target) {
                null -> LiquidGlassSwitch(
                    checked = checked,
                    onCheckedChange = onChange,
                    internalModifier = switchModifier,
                    modifier = modifier,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    colors = CupertinoSwitchDefaults.colors(),
                    backgroundColor = colorUnderSwitch.takeOrElse {
                        scheme.surfaceContainerHigh.copy(alpha = 0.55f)
                    }
                )

                PetalSwitchType.MATERIAL_YOU -> M3Switch(
                    modifier = modifier,
                    internalModifier = switchModifier,
                    colors = switchColors,
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onChange,
                    interactionSource = interactionSource
                )

                PetalSwitchType.COMPOSE -> ComposeSwitch(
                    modifier = switchModifier,
                    colors = switchColors,
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onChange,
                    interactionSource = interactionSource,
                    thumbContent = thumbContent
                )

                PetalSwitchType.PIXEL -> PixelSwitch(
                    modifier = switchModifier,
                    colors = switchColors,
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onChange,
                    interactionSource = interactionSource
                )

                PetalSwitchType.FLUENT -> FluentSwitch(
                    modifier = switchModifier,
                    colors = switchColors,
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onChange,
                    interactionSource = interactionSource
                )

                PetalSwitchType.CUPERTINO -> CupertinoSwitch(
                    checked = checked,
                    onCheckedChange = onChange,
                    modifier = switchModifier,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    colors = CupertinoSwitchDefaults.colors()
                )

                PetalSwitchType.HYPEROS -> HyperOSSwitch(
                    modifier = switchModifier,
                    colors = switchColors.copy(uncheckedTrackColor = scheme.surfaceContainerHigh),
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onChange,
                    interactionSource = interactionSource
                )

                PetalSwitchType.ONE_UI -> OneUISwitch(
                    modifier = modifier.minimumInteractiveComponentSize(),
                    colors = switchColors.copy(uncheckedTrackColor = scheme.surfaceContainerLow),
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onChange,
                    interactionSource = interactionSource
                )
            }
        }
    }
}
