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
import androidx.compose.runtime.remember
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
 * Everything [PetalAddressBar] and [PetalMergedBar] share: resolved position, container shape,
 * collapse style, liquid-glass setup, and window insets.
 *
 * Does NOT read [BarCollapseState.collapsedFraction] during composition; bars read it only
 * inside `graphicsLayer { ... }` and `derivedStateOf` thresholds so scroll animations cause zero
 * per-frame recompositions.
 */
class BarChrome(
    val height: AddressBarHeight,
    val isBottom: Boolean,
    val isFloating: Boolean,
    val isPillCollapse: Boolean,
    val collapseStyle: BarCollapseStyle,
    val containerShape: RoundedCornerShape,
    val isLiquidGlass: Boolean,
    val glassBar: com.petal.browser.ui.glass.GlassBar?,
    val pillGlassBar: com.petal.browser.ui.glass.GlassBar?,
    val baseGlassColor: Color,
    val containerColor: Color,
    val statusBarTopInset: Dp,
    val navBarBottomInset: Dp,
    val glassSource: android.view.View? = null
) {
    val staticElevation: Dp
        get() = if (isLiquidGlass) 0.dp else if (isBottom) 2.dp else 4.dp
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

    LocalPetalGlassRevision.current
    val isLiquidGlass = !isPreview && glassPref(isBottom)
    val samplesWallpaper = BarInsets.glassSamplesWallpaper(sp, isBottom, floatingForWallpaper(isBottom), false)
    val ambient = LocalPetalGlassSource.current
    val activity = context as? Activity
    val glassSource = if (isPreview || samplesWallpaper) null
    else (ambient ?: activity?.findViewById<android.view.View>(com.petal.browser.R.id.main_content))
    // Pause live GlassBackdrop re-recording while the page is actively scrolling so scroll frames
    // avoid extra RenderNode source.draw() passes; static frosted glass styling remains intact.
    val activeGlassSource = if (isLiquidGlass && !BarCollapseState.isPageScrolling) glassSource else null
    val glassBar = rememberGlassBar(activeGlassSource)
    val pillGlassBar = rememberGlassBar(activeGlassSource)

    val surface = if (isIncognito) IncognitoSurfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = if (isLiquidGlass) Color.Transparent else surface

    val statusTop = if (isPreview || suppressStatusBarTopInset) 0.dp
    else WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
    val navBottom = if (isPreview) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    return BarChrome(
        height = height,
        isBottom = isBottom,
        isFloating = isFloating,
        isPillCollapse = !isPreview && settings.collapseStyle == BarCollapseStyle.PILL,
        collapseStyle = effectiveStyle,
        containerShape = barContainerShape(height, isBottom, isFloating),
        isLiquidGlass = isLiquidGlass,
        glassBar = glassBar,
        pillGlassBar = pillGlassBar,
        baseGlassColor = surface,
        containerColor = containerColor,
        statusBarTopInset = statusTop,
        navBarBottomInset = navBottom,
        glassSource = activeGlassSource
    )
}
