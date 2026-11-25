package com.petal.browser.ui.components

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.motion.MotionGate
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.LocalPetalGlassSource
import com.petal.browser.ui.glass.rememberGlassBar
import com.petal.browser.ui.theme.IncognitoSurfaceContainer

/**
 * Everything [PetalAddressBar] and [PetalMergedBar] share: resolved position,
 * container shape, collapse style, liquid-glass setup, and window insets.
 *
 * Frame-by-frame collapse values are NOT baked into [BarChrome] during composition so
 * scroll animation frames do not recompose the bars; call [currentVisuals] inside
 * `graphicsLayer` or `derivedStateOf`.
 */
@Stable
class BarChrome(
    val height: AddressBarHeight,
    val isBottom: Boolean,
    val isFloating: Boolean,
    val isPreview: Boolean,
    val collapseStyle: BarCollapseStyle,
    val isPillCollapse: Boolean,
    val containerShape: RoundedCornerShape,
    val isLiquidGlass: Boolean,
    val glassBar: com.petal.browser.ui.glass.GlassBar?,
    val pillGlassBar: com.petal.browser.ui.glass.GlassBar?,
    val baseGlassColor: Color,
    val containerColor: Color,
    val statusBarTopInset: Dp,
    val navBarBottomInset: Dp,
    val glassSource: android.view.View? = null,
    val collapsedFractionProvider: () -> Float = { if (isPreview) 0f else BarCollapseState.collapsedFraction }
) {
    fun currentVisuals(): BarCollapseMath.Visuals =
        BarCollapseMath.compute(
            collapseStyle,
            collapsedFractionProvider(),
            isBottom
        )

    val staticElevation: Dp
        get() = if (isLiquidGlass) 0.dp else if (isBottom) 1.dp else 2.dp
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
    val glassSupported = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !MotionGate.isLowEndDevice(context)
    }
    val wantsLiquidGlass = !isPreview && glassSupported && glassPref(isBottom)
    val samplesWallpaper = BarInsets.glassSamplesWallpaper(sp, isBottom, floatingForWallpaper(isBottom), false)
    val ambient = LocalPetalGlassSource.current
    val activity = context as? Activity
    val glassSource = if (isPreview || !wantsLiquidGlass || samplesWallpaper) null
    else (ambient ?: activity?.findViewById<android.view.View>(com.petal.browser.R.id.main_content))
    val activeGlassSource = if (wantsLiquidGlass && !BarCollapseState.isPageScrolling) glassSource else null
    val glassBar = rememberGlassBar(activeGlassSource)
    val pillGlassBar = rememberGlassBar(activeGlassSource)
    val isLiquidGlass = wantsLiquidGlass && (glassBar != null || glassSource == null)

    val surface = if (isIncognito) IncognitoSurfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = if (isLiquidGlass) Color.Transparent else surface

    val statusTop = if (isPreview || suppressStatusBarTopInset) 0.dp
    else WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
    val navBottom = if (isPreview) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val collapsedFractionProvider = remember(isPreview) {
        if (isPreview) ({ 0f }) else ({ BarCollapseState.collapsedFraction })
    }

    return BarChrome(
        height = height,
        isBottom = isBottom,
        isFloating = isFloating,
        isPreview = isPreview,
        collapseStyle = effectiveStyle,
        isPillCollapse = !isPreview && settings.collapseStyle == BarCollapseStyle.PILL,
        containerShape = barContainerShape(height, isBottom, isFloating),
        isLiquidGlass = isLiquidGlass,
        glassBar = glassBar,
        pillGlassBar = pillGlassBar,
        baseGlassColor = surface,
        containerColor = containerColor,
        statusBarTopInset = statusTop,
        navBarBottomInset = navBottom,
        glassSource = activeGlassSource,
        collapsedFractionProvider = collapsedFractionProvider
    )
}
