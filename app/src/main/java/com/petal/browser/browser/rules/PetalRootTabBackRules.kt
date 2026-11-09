package com.petal.browser.browser.rules

/**
 * Target action determined for a back gesture or back press in Petal Browser.
 * Adapted from official Firefox navigation fallbacks and Candy Browser's RootTabBackRules.
 */
enum class PetalBackTarget {
    FULLSCREEN_VIDEO,
    MODAL_OVERLAY,
    WEB_HISTORY,
    RETURN_TO_OPENER,
    CLOSE_AND_RETURN_TO_OPENER,
    RETURN_TO_HOME,
    CLOSE_TAB_AND_PREVIOUS,
    CONFIRM_EXIT_DIALOG,
    DELEGATE_TO_SYSTEM
}

/**
 * Pure decision rules engine for back gesture target resolution.
 */
object PetalRootTabBackRules {

    @JvmStatic
    @JvmOverloads
    fun decide(
        isOverlayShowing: Boolean,
        isFullscreen: Boolean,
        hasDialog: Boolean,
        canGoBack: Boolean,
        isHomePage: Boolean,
        totalTabs: Int,
        hasOpener: Boolean = false,
        isPinned: Boolean = false,
        confirmExitEnabled: Boolean = false
    ): PetalBackTarget {
        if (isFullscreen) return PetalBackTarget.FULLSCREEN_VIDEO
        if (hasDialog || isOverlayShowing) return PetalBackTarget.MODAL_OVERLAY
        if (canGoBack) return PetalBackTarget.WEB_HISTORY

        // Tab opened from another tab / popup
        if (hasOpener) {
            return if (isPinned) PetalBackTarget.RETURN_TO_OPENER
            else PetalBackTarget.CLOSE_AND_RETURN_TO_OPENER
        }

        // Web page at root of history -> return to Home surface
        if (!isHomePage) return PetalBackTarget.RETURN_TO_HOME

        // On Homepage with multiple tabs -> close tab and reveal previous tab
        if (totalTabs > 1) return PetalBackTarget.CLOSE_TAB_AND_PREVIOUS

        // Single tab on Homepage with confirmation dialog requested by user
        if (confirmExitEnabled) return PetalBackTarget.CONFIRM_EXIT_DIALOG

        // Single tab on Homepage -> delegate to Android 14+ system predictive back to home launcher
        return PetalBackTarget.DELEGATE_TO_SYSTEM
    }
}
