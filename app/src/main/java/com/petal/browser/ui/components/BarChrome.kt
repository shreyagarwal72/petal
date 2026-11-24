package com.petal.browser.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.glass.rememberGlassBar
import com.petal.browser.ui.theme.IncognitoSurfaceContainer

/**
 * Everything [PetalAddressBar] and [PetalMergedBar] used to compute twice: resolved position,
 * container shape, collapse visuals, liquid-glass setup and window insets.
 */
class BarChrome(
    val height: AddressBarHeight,
    val isBottom: Boolean,
    val isFloating: Boolean,
    val isPillCollapse: Boolean,
    val collapsedFraction: Float,
    val visuals: BarCollapseMath.Visuals,
    val containerShape: RoundedCornerShape,
    val isLiquidGlass: Boolean,
    val glassBar: com.petal.browser.ui.glass.GlassBar?,
    val pillGlassBar: com.petal.browser.ui.glass.GlassBar?,
    val baseGlassColor: Color,
    val containerColor: Color,
    val statusBarTopInset: Dp,
    val navBarBottomInset: Dp
) {
    val animatedContainerColor: Color
        get() = if (isLiquidGlass) Color.Transparent else containerColor.copy(alpha = containerColor.alpha * visuals.outerBgAlpha)
    val animatedElevation: Dp
        get() = (if (isLiquidGlass) 0.dp else if (isBottom) 2.dp else 4.dp) * visuals.outerBgAlpha
}

/** Container shape shared by both bars. */
fun barContainerShape(height: AddressBarHeight, isBottom: Boolean, isFloating: Boolean): RoundedCornerShape =
    if (isBottom && !isFloating) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(height.cornerDp.dp)
    }

/**
 * @param isBottomParam   caller's `isBottom`; the stored position preference can force bottom too.
 * @param floatingForShape whether the bottom bar floats (decides shape and wallpaper sampling for merged).
 * @param floatingForWallpaper value handed to [BarInsets.glassSamplesWallpaper].
 * @param glassPref       decides if liquid glass is on, given the resolved position.
 */
@Composable
fun rememberBarChrome(
    isBottomParam: Boolean,
    isPreview: Boolean,
    isIncognito: Boolean,
    suppressStatusBarTopInset: Boolean,
    floatingForShape: (isBottom: Boolean) -> Boolean,
    floatingForWallpaper: (isBottom: Boolean) -> Boolean,
    glassPref: (isBottom: Boolean) -> Boolean
): BarChrome {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val settings = rememberBarSettings()
    val height = settings.height

    val isBottom = if (isPreview) isBottomParam else (isBottomParam || settings.position == AddressBarPosition.BOTTOM)
    val isFloating = floatingForShape(isBottom)
    val effectiveStyle = if (isPreview) BarCollapseStyle.HIDE else settings.collapseStyle
    val fraction = if (isPreview) 0f else BarCollapseState.collapsedFraction
    val visuals = BarCollapseMath.compute(effectiveStyle, fraction, isBottom)

    LocalPetalGlassRevision.current
    val isLiquidGlass = !isPreview && glassPref(isBottom)
    val samplesWallpaper = BarInsets.glassSamplesWallpaper(sp, isBottom, floatingForWallpaper(isBottom), false)
    val ambient = LocalPetalGlassSource.current
    val activity = context as? Activity
    val glassSource = if (isPreview || samplesWallpaper) null
    else (ambient ?: activity?.findViewById<android.view.View>(com.petal.browser.R.id.main_content))
    val glassBar = rememberGlassBar(if (isLiquidGlass) glassSource else null)
    val pillGlassBar = rememberGlassBar(if (isLiquidGlass) glassSource else null)

    val surface = if (isIncognito) IncognitoSurfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = if (isLiquidGlass) Color.Transparent else surface

    // status bar UNION display cutout (see Phase 2: no immersive special case, cutouts stay cleared).
    val statusTop = if (isPreview || suppressStatusBarTopInset) 0.dp
    else WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
    val navBottom = if (isPreview) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    return BarChrome(
        height = height,
        isBottom = isBottom,
        isFloating = isFloating,
        isPillCollapse = !isPreview && settings.collapseStyle == BarCollapseStyle.PILL,
        collapsedFraction = fraction,
        visuals = visuals,
        containerShape = barContainerShape(height, isBottom, isFloating),
        isLiquidGlass = isLiquidGlass,
        glassBar = glassBar,
        pillGlassBar = pillGlassBar,
        baseGlassColor = surface,
        containerColor = containerColor,
        statusBarTopInset = statusTop,
        navBarBottomInset = navBottom
    )
}
