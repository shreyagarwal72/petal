package com.petal.browser.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * PetalMotionPhysics
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Motion Physics design system primitives adapted from
 * Philipp Lackner's Material3ExpressiveGuide (https://github.com/philipplackner/Material3ExpressiveGuide).
 *
 * Provides expressive spatial spring physics and motion effect specs across
 * Petal UI components.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object PetalMotionPhysics {

    /** Fast spatial spring physics for snappy touch feedback, scale transitions, and button presses. */
    val fastSpatial: AnimationSpec<Float>
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.motionScheme.fastSpatialSpec()

    /** Slow spatial spring physics for fluid card expansions, sheets, and full-screen transitions. */
    val slowSpatial: AnimationSpec<Float>
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.motionScheme.slowSpatialSpec()

    /** Default spatial spring physics for standard container transformations. */
    val defaultSpatial: AnimationSpec<Float>
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Fast effects spec for rapid opacity crossfades and visual feedback. */
    val fastEffects: AnimationSpec<Float>
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.motionScheme.fastEffectsSpec()

    /** Slow effects spec for gradual background and color transitions. */
    val slowEffects: AnimationSpec<Float>
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.motionScheme.slowEffectsSpec()

    /** Default effects spec for standard effect animations. */
    val defaultEffects: AnimationSpec<Float>
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.motionScheme.defaultEffectsSpec()
}
