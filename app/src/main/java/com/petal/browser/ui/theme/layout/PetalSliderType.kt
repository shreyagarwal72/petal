package com.petal.browser.ui.theme.layout

/**
 * Slider rendering variants ported from ImageToolbox.
 * All variants dynamically support Liquid Glass styling when active.
 */
enum class PetalSliderType(val key: String, val title: String, val subtitle: String) {
    FANCY("FANCY", "Fancy", "Scalloped expressive cookie thumb rolling inside pill track"),
    MATERIAL_YOU("MATERIAL_YOU", "Material You", "Expressive pill thumb with variable track thickness"),
    MATERIAL("MATERIAL", "Material", "Standard Material 3 round thumb floating on horizontal rail"),
    HYPEROS("HYPEROS", "HyperOS", "Sleek full-width capsule slider with embedded track line");

    companion object {
        fun fromKey(key: String?): PetalSliderType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FANCY
    }
}
