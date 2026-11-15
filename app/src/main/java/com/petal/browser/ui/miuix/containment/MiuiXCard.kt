package com.petal.browser.ui.miuix.containment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.petal.browser.ui.containment.PetalBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Thin adapter over the real Miuix [Card]. Signature is kept so existing call sites do not
 * change; [shape], [backdrop] and [glassEnabled] are accepted for source compatibility but the
 * real Card owns its corner radius, surface (surfaceContainer) and press feedback.
 */
@Composable
fun MiuiXCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    color: Color? = null,
    @Suppress("UNUSED_PARAMETER") backdrop: PetalBackdrop? = null,
    @Suppress("UNUSED_PARAMETER") glassEnabled: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = if (color != null) {
        CardDefaults.defaultColors(color = color, contentColor = MiuixTheme.colorScheme.onSurfaceContainer)
    } else {
        CardDefaults.defaultColors()
    }

    if (onClick != null) {
        Card(
            modifier = modifier,
            colors = colors,
            pressFeedbackType = PressFeedbackType.Sink,
            showIndication = true,
            onClick = if (enabled) onClick else null,
        ) {
            Box { content() }
        }
    } else {
        Card(modifier = modifier, colors = colors) {
            Box { content() }
        }
    }
}
