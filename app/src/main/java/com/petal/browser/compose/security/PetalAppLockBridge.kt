/*
 * PetalAppLockBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Java-interop bridge for launching App Lock Overlay and Configuration screens.
 */

package com.petal.browser.compose.security

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.activity.MainActivity
import com.petal.browser.predictive.PetalContentSnapshot
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalAppLockBridge {

    @JvmStatic
    fun showLockOverlay(activity: Activity, onUnlocked: Runnable, onCancel: Runnable) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val browserActivity = activity as? MainActivity
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        PetalContentSnapshot.capture(rootView)
        var composeView: ComposeView? = null
        composeView = ComposeView(activity).apply {
            if (activity is ComponentActivity) {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeOnBackPressedDispatcherOwner(activity)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val snapshotBitmap = remember { PetalContentSnapshot.current?.asImageBitmap() }

                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                var currentPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
                var useDynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)) }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_palette_id" -> currentPaletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                            "useDynamicColor" -> useDynamic = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val fontState = com.petal.browser.ui.theme.rememberFontState()
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    paletteId = currentPaletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic,
                    expressiveColors = isExpressiveColors,
                    fontState = fontState,
                    colorStyle = colorStyle
                ) {
                    PetalAppLockScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onUnlocked = {
                            composeView?.let { cv ->
                                cv.animate()
                                    .alpha(0f)
                                    .setDuration(180)
                                    .withEndAction {
                                        decor.removeView(cv)
                                        browserActivity?.isDecorOverlayShowing = false
                                        onUnlocked.run()
                                    }
                                    .start()
                            } ?: run {
                                decor.removeView(composeView)
                                browserActivity?.isDecorOverlayShowing = false
                                onUnlocked.run()
                            }
                        },
                        onBackPress = {
                            decor.removeView(composeView)
                            browserActivity?.isDecorOverlayShowing = false
                            onCancel.run()
                        }
                    )
                }
            }
            addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {}
                override fun onViewDetachedFromWindow(v: android.view.View) {
                    removeOnAttachStateChangeListener(this)
                    PetalContentSnapshot.clear()
                }
            })
        }
        browserActivity?.isDecorOverlayShowing = true
        decor.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    @JvmStatic
    fun showConfig(activity: Activity, onBack: Runnable) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val browserActivity = activity as? MainActivity
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        // Always snapshot what is on screen NOW (e.g. the Account page). A cached transition snapshot
        // from an earlier screen (the Settings hub) made predictive back preview the wrong page.
        PetalContentSnapshot.capturePreview(rootView)
        PetalContentSnapshot.capture(rootView, forceFresh = true)
        var composeView: ComposeView? = null
        composeView = ComposeView(activity).apply {
            if (activity is ComponentActivity) {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeOnBackPressedDispatcherOwner(activity)
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val snapshotBitmap = remember { PetalContentSnapshot.current?.asImageBitmap() }

                val context = LocalContext.current
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
                var currentPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
                var useDynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)) }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_palette_id" -> currentPaletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                            "useDynamicColor" -> useDynamic = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val fontState = com.petal.browser.ui.theme.rememberFontState()
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    paletteId = currentPaletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic,
                    expressiveColors = isExpressiveColors,
                    fontState = fontState,
                    colorStyle = colorStyle
                ) {
                    PetalAppLockConfigScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onBack = {
                            decor.removeView(composeView)
                            browserActivity?.isDecorOverlayShowing = false
                            onBack.run()
                        }
                    )
                }
            }
            addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {}
                override fun onViewDetachedFromWindow(v: android.view.View) {
                    removeOnAttachStateChangeListener(this)
                    PetalContentSnapshot.clear()
                }
            })
        }
        browserActivity?.isDecorOverlayShowing = true
        decor.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}
