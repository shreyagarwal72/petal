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
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setTag(com.petal.browser.R.id.main_progress_bar_compose, Pair(progressState, visibleState))
            setContent {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "SYSTEM") ?: "SYSTEM"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", "tide") ?: "tide"
                val dynamicColor = sp.getBoolean("useDynamicColor", false)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = try { AppFont.valueOf(fontName) } catch (e: Exception) { AppFont.SYSTEM }
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
}

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
        LinearRipplingWavyProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            height = 10.dp,
            strokeWidth = 3.dp,
            waveAmplitude = 3.dp
        )
    }
}
