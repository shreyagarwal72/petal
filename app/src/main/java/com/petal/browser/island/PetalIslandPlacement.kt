package com.petal.browser.island

/** Where the NowBar window actually lives right now. */
enum class IslandPlacement {
    /** TYPE_ACCESSIBILITY_OVERLAY from [PetalIslandAccessibilityService]: inside the status bar, over every app. */
    ACCESSIBILITY,

    /** TYPE_APPLICATION_OVERLAY ("Display over other apps"): over other apps, hidden while Petal is open. */
    OVERLAY,

    /** Drawn by Petal's own activity. The only mode the Google Play build can use. */
    IN_APP
}

/**
 * Picks the [IslandPlacement] from the user's choice and what the device/build can really do.
 * Pure function, so it is unit tested without Android.
 */
object PetalIslandPlacementResolver {

    fun resolve(
        mode: String,
        overlayEnabledLegacy: Boolean,
        accessibilityConnected: Boolean,
        overlayGranted: Boolean,
        isPlayBuild: Boolean
    ): IslandPlacement {
        val canA11y = accessibilityConnected && !isPlayBuild
        val canOverlay = overlayGranted && !isPlayBuild
        return when (mode) {
            IslandSettings.PLACE_IN_APP -> IslandPlacement.IN_APP
            IslandSettings.PLACE_OVERLAY ->
                if (canOverlay) IslandPlacement.OVERLAY else IslandPlacement.IN_APP
            IslandSettings.PLACE_ACCESSIBILITY, IslandSettings.PLACE_AUTO -> when {
                canA11y -> IslandPlacement.ACCESSIBILITY
                // AUTO also honours the older "Show over other apps" switch.
                mode == IslandSettings.PLACE_AUTO && overlayEnabledLegacy && canOverlay -> IslandPlacement.OVERLAY
                else -> IslandPlacement.IN_APP
            }
            else -> IslandPlacement.IN_APP
        }
    }
}

/** Horizontal placement maths shared by the in-app bar and the overlay window. */
object PetalIslandPlacementMath {
    /** Distance of the bar's centre from the screen centre in dp. Negative = left. */
    fun effectiveOffsetXDp(alignmentMode: String, hardwareDp: Float, userOffsetDp: Int): Float =
        when (alignmentMode) {
            IslandSettings.ALIGN_LEFT -> -80f + userOffsetDp
            IslandSettings.ALIGN_RIGHT -> 80f + userOffsetDp
            IslandSettings.ALIGN_CENTER -> 0f + userOffsetDp
            else -> hardwareDp + userOffsetDp // AUTO: follow the physical camera
        }
}
