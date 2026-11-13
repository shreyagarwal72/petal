package com.petal.browser.ui.components

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import com.petal.browser.ui.glass.StrideGlassPrefs
import com.petal.browser.ui.glass.bouncyClickable
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar
import kotlinx.coroutines.delay

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
    size: Dp = 64.dp
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    LocalPetalGlassRevision.current
    val prefs = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val themeGlass = isLiquidGlassEnabled("sp_liquid_glass_bottom_nav")
    // Same rule as PetalStrideNavBar, so the pill and the button never disagree.
    val glassOn = themeGlass || (StrideGlassPrefs.isEnabled(context) && !prefs.contains("sp_liquid_glass_enabled"))
    val glassBar = rememberGlassBar(if (glassOn) glassSource else null)
    val glassActive = glassBar != null || themeGlass

    // Pops in just after the pill, so the bar assembles left to right.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(140L)
        enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = enter.value.coerceAtLeast(0f)
                scaleX = s
                scaleY = s
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .size(size)
            .then(if (!glassActive) Modifier.shadow(12.dp, CircleShape, clip = false) else Modifier)
            .liquidGlass(CircleShape, scheme.surfaceContainerHigh, glassBar)
            .clip(CircleShape)
            .bouncyClickable(scaleDown = 0.86f, onClick = onClick)
            .semantics { contentDescription = "Search" },
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
