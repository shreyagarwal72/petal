package com.petal.browser.predictive

import androidx.activity.BackEventCompat
import com.petal.browser.browser.rules.PetalBackTarget
import com.petal.browser.browser.rules.PetalRootTabBackRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetalPredictiveBackTest {

    private fun baseInput(
        progress: Float = 0f,
        swipeEdge: Int = BackEventCompat.EDGE_LEFT,
        blurEnabled: Boolean = true,
        committing: Boolean = false,
        releaseProgress: Float = 0f,
        settleTime: Float = 0f,
        exitDirection: PetalBackExitDirection = PetalBackExitDirection.AlwaysRight,
    ) = PetalBackInput(
        progress = progress,
        swipeEdge = swipeEdge,
        touchY = 1000f,
        initialTouchY = 1000f,
        widthPx = 1080f,
        heightPx = 2400f,
        density = 3f,
        blurEnabled = blurEnabled,
        committing = committing,
        releaseProgress = releaseProgress,
        settleTime = settleTime,
        exitDirection = exitDirection,
    )

    @Test
    fun allStyles_zeroProgressBlurState_matchesDepthBlurToggle() {
        for (style in PetalBackStyle.entries) {
            val withBlur = PetalBackMotion.frame(style, baseInput(progress = 0f, blurEnabled = true))
            val withoutBlur = PetalBackMotion.frame(style, baseInput(progress = 0f, blurEnabled = false))
            assertEquals("Expected 24dp depth blur at progress=0 for $style", 24f, withBlur.behindBlurDp, 0.01f)
            assertEquals("Expected 0dp blur when disabled for $style", 0f, withoutBlur.behindBlurDp, 0.001f)
            assertTrue("Expected softer behind dim with blur for $style", withBlur.behindDim < withoutBlur.behindDim)
        }
    }

    @Test
    fun allStyles_settledCommit_clearsBehindBlurAndDim() {
        for (style in PetalBackStyle.entries) {
            val settled = if (style.usesSpringCommit) {
                PetalBackMotion.frame(style, baseInput(progress = 1f, blurEnabled = true))
            } else {
                PetalBackMotion.frame(
                    style,
                    baseInput(
                        progress = 1f,
                        blurEnabled = true,
                        committing = true,
                        releaseProgress = 0.45f,
                        settleTime = 1f,
                    ),
                )
            }
            assertEquals("Settled blur should reach 0 for $style", 0f, settled.behindBlurDp, 0.01f)
            assertEquals("Settled dim should reach 0 for $style", 0f, settled.behindDim, 0.01f)
        }
    }

    @Test
    fun scaleStyle_commitExitDirection_respectsSwipeEdgeOrOverride() {
        val followFromLeft = PetalBackMotion.frame(
            PetalBackStyle.Scale,
            baseInput(
                progress = 1f,
                swipeEdge = BackEventCompat.EDGE_LEFT,
                committing = true,
                releaseProgress = 0.4f,
                settleTime = 1f,
                exitDirection = PetalBackExitDirection.FollowGesture,
            ),
        )
        val followFromRight = PetalBackMotion.frame(
            PetalBackStyle.Scale,
            baseInput(
                progress = 1f,
                swipeEdge = BackEventCompat.EDGE_RIGHT,
                committing = true,
                releaseProgress = 0.4f,
                settleTime = 1f,
                exitDirection = PetalBackExitDirection.FollowGesture,
            ),
        )
        val forceLeft = PetalBackMotion.frame(
            PetalBackStyle.Scale,
            baseInput(
                progress = 1f,
                swipeEdge = BackEventCompat.EDGE_LEFT,
                committing = true,
                releaseProgress = 0.4f,
                settleTime = 1f,
                exitDirection = PetalBackExitDirection.AlwaysLeft,
            ),
        )
        assertTrue(followFromLeft.top.translationX > 0f)
        assertTrue(followFromRight.top.translationX < 0f)
        assertTrue(forceLeft.top.translationX < 0f)
    }

    @Test
    fun rootTabBackRules_resolvesCrossContainerTabCountAndRootOverlayStates() {
        assertEquals(
            PetalBackTarget.CLOSE_TAB_AND_PREVIOUS,
            PetalRootTabBackRules.decide(
                isOverlayShowing = false,
                isFullscreen = false,
                hasDialog = false,
                canGoBack = false,
                isHomePage = true,
                totalTabs = 2,
            ),
        )
        assertEquals(
            PetalBackTarget.DELEGATE_TO_SYSTEM,
            PetalRootTabBackRules.decide(
                isOverlayShowing = false,
                isFullscreen = false,
                hasDialog = false,
                canGoBack = false,
                isHomePage = true,
                totalTabs = 1,
                confirmExitEnabled = false,
            ),
        )
        assertEquals(
            PetalBackTarget.CONFIRM_EXIT_DIALOG,
            PetalRootTabBackRules.decide(
                isOverlayShowing = false,
                isFullscreen = false,
                hasDialog = false,
                canGoBack = false,
                isHomePage = true,
                totalTabs = 1,
                confirmExitEnabled = true,
            ),
        )
        assertEquals(
            PetalBackTarget.MODAL_OVERLAY,
            PetalRootTabBackRules.decide(
                isOverlayShowing = true,
                isFullscreen = false,
                hasDialog = false,
                canGoBack = false,
                isHomePage = true,
                totalTabs = 1,
            ),
        )
        assertEquals(
            PetalBackTarget.RETURN_TO_HOME,
            PetalRootTabBackRules.decide(
                isOverlayShowing = false,
                isFullscreen = false,
                hasDialog = false,
                canGoBack = false,
                isHomePage = false,
                totalTabs = 1,
            ),
        )
        assertEquals(
            PetalBackTarget.CLOSE_AND_RETURN_TO_OPENER,
            PetalRootTabBackRules.decide(
                isOverlayShowing = false,
                isFullscreen = false,
                hasDialog = false,
                canGoBack = false,
                isHomePage = false,
                totalTabs = 2,
                hasOpener = true,
                isPinned = false,
            ),
        )
        assertEquals(
            PetalBackTarget.RETURN_TO_OPENER,
            PetalRootTabBackRules.decide(
                isOverlayShowing = false,
                isFullscreen = false,
                hasDialog = false,
                canGoBack = false,
                isHomePage = false,
                totalTabs = 2,
                hasOpener = true,
                isPinned = true,
            ),
        )
        assertFalse(PetalBackStyle.Aosp.usesSpringCommit)
        assertTrue(PetalBackStyle.Petal.usesSpringCommit)
    }
}
