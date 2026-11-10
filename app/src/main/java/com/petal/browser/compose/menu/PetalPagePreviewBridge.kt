package com.petal.browser.compose.menu

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.petal.browser.ui.kit.IconButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import com.petal.browser.ui.glass.LocalPetalGlassSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.activity.MainActivity
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.PetalAndroidUtils
import com.petal.browser.browser.PetalTabViewController
import com.petal.browser.engine.gecko.PetalEngineStore
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Shows a page preview in a constrained card without navigating the user's
 * actual current tab. Creates an isolated PetalTabViewController session, loads
 * the URL, and calls its destroy() to tear the session down completely when the preview is
 * dismissed - the preview is never added to BrowserContainer and never becomes a real tab.
 */
object PetalPagePreviewBridge {

    @JvmStatic
    fun show(activity: MainActivity, url: String) {
        if (url.isBlank()) return

        activity.runOnUiThread {
            try {
                val decorView = activity.window?.decorView as? ViewGroup ?: return@runOnUiThread
                lateinit var composeView: ComposeView

                val dismissAction = {
                    if (composeView.parent != null) {
                        decorView.removeView(composeView)
                    }
                }

                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setContent {
                        val hostSource = activity.findViewById<android.view.View>(com.petal.browser.R.id.main)
                            ?: activity.findViewById(android.R.id.content)
                        CompositionLocalProvider(LocalPetalGlassSource provides hostSource) {
                            PetalExpressiveTheme {
                                androidx.compose.material3.ModalBottomSheet(
                                    onDismissRequest = dismissAction,
                                    sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                    shape = androidx.compose.material3.BottomSheetDefaults.ExpandedShape,
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() }
                                ) {
                                    PetalPagePreviewSheet(
                                        url = url,
                                        onOpenFullTab = {
                                            dismissAction()
                                            activity.addAlbum(PetalAndroidUtils.domain(url), url, true)
                                        },
                                        onDismiss = dismissAction
                                    )
                                }
                            }
                        }
                    }
                }
                decorView.addView(
                    composeView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PetalPagePreviewSheet(
    url: String,
    onOpenFullTab: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheetShape = androidx.compose.material3.BottomSheetDefaults.ExpandedShape

    // Standalone PetalTabViewController with isolated EngineSession — never added
    // to BrowserContainer or tracked as a main tab. destroy() tears it down on dismiss.
    val previewTabId = remember { "preview_${System.currentTimeMillis()}" }
    val previewView = remember {
        val sessionPair = PetalEngineStore.createTabSession(
            context = context,
            tabId = previewTabId,
            url = url,
            title = "Preview",
            isIncognito = false,
            select = false
        )
        PetalTabViewController(context).apply {
            bindTab(sessionPair.first)
        }
    }

    DisposableEffect(url) {
        onDispose {
            previewView.destroy()
        }
    }

    com.petal.browser.ui.glass.PetalGlassContainer(
        modifier = Modifier.fillMaxWidth(),
        enabled = isGlass,
        live = true
    ) {
        Surface(
            shape = sheetShape,
            color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (isGlass) 0.dp else 2.dp,
            shadowElevation = if (isGlass) 0.dp else 12.dp,
            border = null,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isGlass) Modifier.liquidGlassChrome(sheetShape, enabled = true) else Modifier)
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = PetalAndroidUtils.domain(url) ?: url,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenFullTab) {
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.ui_open_in_new_tab),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close_preview),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Constrained preview card, not full screen - the current tab underneath
            // is never touched or navigated by this preview.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 320.dp, max = 480.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AndroidView(
                    factory = {
                        previewView.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            post { loadUrl(url) }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { isLoading = false }
                )
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        com.petal.browser.compose.composable.PetalContainedLoadingIndicator(
                            size = 36.dp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
    }
}
