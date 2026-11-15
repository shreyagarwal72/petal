package com.petal.browser.ui.miuix.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.petal.browser.ui.miuix.theme.LocalIsDark
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShadows

/** One entry of [MiuiXGlassNavigationBar]. */
data class MiuiXGlassNavItem(
    val icon: ImageVector,
    val label: String,
)

/**
 * Floating glass bottom bar built on the real Miuix [GlassNavigationBar] (glass material, sliding
 * indicator, GlassMotion springs). [backdrop] is the Miuix blur backdrop of the content behind
 * the bar; when null the library draws its own fallback surface. The bar does not add window
 * insets itself, so the host places it with its own insets handling.
 */
@Composable
fun MiuiXGlassNavigationBar(
    items: List<MiuiXGlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    GlassNavigationBar(
        items = items.map { GlassNavigationItem(icon = it.icon, label = it.label, contentDescription = it.label) },
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        backdrop = backdrop,
        modifier = modifier,
        defaultWindowInsetsPadding = false,
        style = GlassDefaults.style(LocalIsDark.current),
        trailingAction = trailingAction,
    )
}

/** Round glass action that sits beside the bar (same height as the bar). */
@Composable
fun MiuiXGlassAction(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    GlassIconButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        size = GlassNavigationBarDefaults.Height,
        style = GlassDefaults.style(LocalIsDark.current),
        shadow = GlassShadows.Float,
        content = content,
    )
}
