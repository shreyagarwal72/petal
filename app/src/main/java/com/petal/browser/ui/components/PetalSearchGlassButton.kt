package com.petal.browser.ui.components

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.R
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.bouncyClickable
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar
import kotlinx.coroutines.delay
import androidx.compose.material3.Icon

/**
 * The separate search button that sits at the right of the floating nav bar.
 *
 * It is its own glass surface (its own [com.petal.browser.ui.glass.GlassBar] on the same
 * backdrop as the nav pill), so it blurs and bends whatever is under it independently.
 * It follows the same Liquid Glass settings as the nav bar: the "bottom nav" switch, the
 * opacity slider and the sheen slider. The icon is Petal's own drawable, not a Material icon.
 */
@Composable
internal fun PetalSearchGlassButton(
    glassSource: View?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    LocalPetalGlassRevision.current
    val glassSupported = remember(context) {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !com.petal.browser.motion.MotionGate.isLowEndDevice(context)
    }
    val themeGlass = isLiquidGlassEnabled("sp_liquid_glass_bottom_nav") && glassSupported
    // Same rule as PetalStrideNavBar, so the pill and the button never disagree.
    val glassOn = themeGlass
    val glassBar = rememberGlassBar(if (glassOn) glassSource else null)
    val glassActive = glassSupported && (glassBar != null || themeGlass)

    val searchLabel = androidx.compose.ui.res.stringResource(R.string.bar_search)

    Box(
        modifier = modifier
            .size(size)
            .then(if (!glassActive) Modifier.shadow(1.dp, CircleShape, clip = false) else Modifier)
            .clip(CircleShape)
            .liquidGlass(CircleShape, scheme.surfaceContainerHigh, glassBar)
            .bouncyClickable(
                scaleDown = 0.86f,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = searchLabel },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_petal_search),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(26.dp)
        )
    }
}
