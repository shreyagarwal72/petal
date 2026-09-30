package com.petal.browser.ui.components

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

class ProgressViewState(
    val progressState: MutableState<Float> = mutableStateOf(0f),
    val visibleState: MutableState<Boolean> = mutableStateOf(false)
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var resetRunnable: Runnable? = null

    fun updateProgress(progress: Int) {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        resetRunnable?.let { mainHandler.removeCallbacks(it) }

        if (progress < 100) {
            // Firefox behavior: start immediately at minimum ~12% progress so user feels instant response
            val targetFrac = (progress.coerceIn(12, 99)) / 100f
            // Never animate progress backwards while loading
            if (targetFrac > progressState.value || !visibleState.value) {
                progressState.value = targetFrac
            }
            visibleState.value = true
        } else {
            // Firefox behavior: smoothly complete to 100%, linger for 180ms, fade out, then reset
            progressState.value = 1f
            visibleState.value = true

            val hideTask = Runnable {
                visibleState.value = false
                val resetTask = Runnable {
                    if (!visibleState.value) {
                        progressState.value = 0f
                    }
                }
                resetRunnable = resetTask
                mainHandler.postDelayed(resetTask, 250L)
            }
            hideRunnable = hideTask
            mainHandler.postDelayed(hideTask, 180L)
        }
    }

    fun hide() {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        resetRunnable?.let { mainHandler.removeCallbacks(it) }
        visibleState.value = false
        progressState.value = 0f
    }
}

object PetalProgressBarBridge {
    @JvmStatic
    fun createProgressView(activity: ComponentActivity): ComposeView {
        val state = ProgressViewState()

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setTag(com.petal.browser.R.id.main_progress_bar_compose, state)
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = AppFont.fromName(fontName)
                val colorStyle = try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                    PetalWebProgressIndicator(
                        progress = state.progressState.value,
                        visible = state.visibleState.value
                    )
                }
            }
        }
        return composeView
    }

    @JvmStatic
    fun updateProgress(composeView: ComposeView, progress: Int) {
        val state = composeView.getTag(com.petal.browser.R.id.main_progress_bar_compose) as? ProgressViewState
        state?.updateProgress(progress)
    }

    @JvmStatic
    fun hide(composeView: ComposeView) {
        val state = composeView.getTag(com.petal.browser.R.id.main_progress_bar_compose) as? ProgressViewState
        state?.hide()
    }
}

@Composable
fun PetalWebProgressIndicator(
    progress: Float,
    visible: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (progress >= 1f) {
            tween(durationMillis = 180, easing = LinearOutSlowInEasing)
        } else {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        },
        label = "petalWebProgress",
    )
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.6f))
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
    }
}
