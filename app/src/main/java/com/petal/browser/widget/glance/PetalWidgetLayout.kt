package com.petal.browser.widget.glance

import kotlin.math.floor

internal data class PetalSlot(val index: Int, val action: PetalWidgetAction)

/**
 * What a widget shows at a given size. Both the Glance widgets and the live preview in the settings screen call
 * [planLayout], so they can't drift apart, and no element is ever squeezed out of sight: instead of letting a
 * narrow or short widget crush the hint text to nothing (what the old fixed layout did when a widget was resized
 * to its minimum), buttons and the badge drop out in a fixed priority order.
 */
internal data class PetalLayoutPlan(
    val expanded: Boolean,
    val barHeight: Int,
    val padding: Int,
    val showBadge: Boolean,
    val badgeSize: Int,
    val buttons: List<PetalSlot>,
    val buttonSize: Int,
    val buttonTouch: Int,
    val tiles: List<PetalSlot>,
    val tileCookie: Int,
    val showTileLabels: Boolean,
    /** Corner radius, in dp, of the bar's outer edge. */
    val radius: Int,
    /** Corner radius of the nested search field. */
    val fieldRadius: Int
)

private const val MIN_HINT_DP = 64
private const val MIN_FIELD_DP = 118
private const val EXPANDED_MIN_HEIGHT_DP = 104
private const val EXPANDED_BAR_DP = 52
private const val TILE_MIN_WIDTH_DP = 58

internal fun planLayout(cfg: PetalWidgetConfig, widthDp: Float, heightDp: Float): PetalLayoutPlan {
    val kind = cfg.kind
    val w = widthDp.coerceAtLeast(60f)
    val h = heightDp.coerceAtLeast(40f)

    val barSlots = (0 until kind.barSlots).mapNotNull { i ->
        cfg.barAction(i).takeIf { !it.isEmpty }?.let { PetalSlot(i, it) }
    }
    val tileSlots = (0 until kind.tileSlots).mapNotNull { i ->
        cfg.tile(i).takeIf { !it.isEmpty }?.let { PetalSlot(i, it) }
    }

    val expanded = kind.tileSlots > 0 && tileSlots.isNotEmpty() && h >= EXPANDED_MIN_HEIGHT_DP
    val barH = if (expanded) EXPANDED_BAR_DP.toFloat() else h
    val pad = if (barH < 52f) 4 else 6
    val inner = (barH - 2 * pad).coerceAtLeast(28f)

    val touch = inner.coerceIn(36f, 48f).toInt()
    val buttonVisual = (touch - 6).coerceAtLeast(30)
    val badge = inner.coerceIn(32f, 48f).toInt()

    // Badge: only where it leaves room for the hint, and never on the widget without one.
    var avail = w - 2 * pad
    var showBadge = cfg.showBadge && kind != PetalWidgetKind.PETAL2
    if (showBadge) {
        avail -= badge + 8
        if (avail < MIN_HINT_DP) {
            showBadge = false
            avail += badge + 8
        }
    }

    // Buttons: as many, left to right, as leave the hint (or, in the nested field, the field) readable.
    val reserve = if (kind == PetalWidgetKind.PETAL2) MIN_FIELD_DP else MIN_HINT_DP
    val fit = floor((avail - reserve) / (touch + 2f)).toInt().coerceIn(0, barSlots.size)
    val buttons = barSlots.take(fit)

    val tileRegion = h - barH - 2 * pad - 6
    val showLabels = tileRegion >= 62
    val tileCookie = (tileRegion - if (showLabels) 18 else 0).coerceIn(30f, 56f).toInt()
    val tileFit = floor((w - 2 * pad) / TILE_MIN_WIDTH_DP).toInt().coerceIn(1, 4)
    val tiles = if (expanded) tileSlots.take(tileFit) else emptyList()

    val radius = cfg.cornerRadius
    val fieldRadius = when {
        radius >= PETAL_RADIUS_FULL -> PETAL_RADIUS_FULL
        else -> (radius - pad).coerceAtLeast(0).let { it - it % 4 }
    }

    return PetalLayoutPlan(
        expanded = expanded,
        barHeight = barH.toInt(),
        padding = pad,
        showBadge = showBadge,
        badgeSize = badge,
        buttons = buttons,
        buttonSize = buttonVisual,
        buttonTouch = touch,
        tiles = tiles,
        tileCookie = tileCookie,
        showTileLabels = showLabels,
        radius = radius,
        fieldRadius = fieldRadius
    )
}
