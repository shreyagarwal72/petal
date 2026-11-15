package com.petal.browser.ui.miuix.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TabRowWithContour

/** Adapter over the real Miuix segmented control ([TabRowWithContour], sliding contour). */
@Composable
fun <T> MiuiXSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String,
) {
    if (items.isEmpty()) return
    TabRowWithContour(
        tabs = items.map(labelProvider),
        selectedTabIndex = selectedIndex.coerceIn(0, items.size - 1),
        onTabSelected = onItemSelected,
        modifier = modifier.fillMaxWidth(),
    )
}
