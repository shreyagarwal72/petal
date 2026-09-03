package com.petal.browser.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.preference.PreferenceManager
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalProgressBarBridge {
    @JvmStatic
    fun createProgressView(activity: ComponentActivity): ComposeView {
        val progressState = mutableStateOf(0f)
        val visibleState = mutableStateOf(false)

        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setTag(com.petal.browser.R.id.main_progress_bar_compose, Pair(progressState, visibleState))
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
                    PetalFancyWebLoadingBar(
                        progress = progressState.value,
                        visible = visibleState.value
                    )
                }
            }
        }
        return composeView
    }

    @JvmStatic
    fun updateProgress(composeView: ComposeView, progress: Int) {
        val tag = composeView.getTag(com.petal.browser.R.id.main_progress_bar_compose) as? Pair<MutableState<Float>, MutableState<Boolean>>
        if (tag != null) {
            val (progressState, visibleState) = tag
            if (progress < 100) {
                progressState.value = (progress.coerceAtLeast(5) / 100f)
                visibleState.value = true
            } else {
                progressState.value = 1f
                visibleState.value = false
            }
        }
    }

    /**
     * Hides the bar without touching the ComposeView's own Android visibility -
     * only the internal Compose state that drives its AnimatedVisibility. Used
     * whenever a pull-to-refresh reload is in flight or an internal (non-web)
     * screen - settings, downloads, history, account sync, home - is showing.
     *
     * Do NOT call composeView.setVisibility(GONE) for this: a GONE ComposeView
     * never composes again, so calling updateProgress() later - once a real
     * page starts loading - has nothing to make visible and the bar stays gone
     * for the rest of the session.
     */
    @JvmStatic
    fun hide(composeView: ComposeView) {
        val tag = composeView.getTag(com.petal.browser.R.id.main_progress_bar_compose) as? Pair<MutableState<Float>, MutableState<Boolean>>
        tag?.second?.value = false
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetalFancyWebLoadingBar(
    progress: Float,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val coercedProgress = progress.coerceIn(0f, 1f)
        LinearWavyProgressIndicator(
            progress = { coercedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )
    }
}
