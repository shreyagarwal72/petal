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
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.PetalGeckoView
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

/**
 * Shows a page preview in a constrained card without navigating the user's
 * actual current tab. Deliberately does NOT use BrowserWebViewController's
 * createAndConfigureGeckoView() - that wires a PetalGeckoView into media/PiP/
 * tab-lifecycle plumbing meant for a real, tracked browser tab, which a
 * disposable preview should never become. This bridge creates a bare
 * PetalGeckoView, loads the URL, and calls its destroy() to tear the session
 * down completely when the preview is dismissed - the preview is never added
 * to BrowserContainer and never becomes a real tab.
 */
object PetalPagePreviewBridge {

    @JvmStatic
    fun show(activity: BrowserActivity, url: String) {
        if (url.isBlank()) return

        activity.runOnUiThread {
            try {
                val dialog = BottomSheetDialog(activity)
                dialog.setOnShowListener {
                    try {
                        val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                        bottomSheet?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    } catch (_: Throwable) {}
                }
                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        PetalExpressiveTheme {
                            PetalPagePreviewSheet(
                                url = url,
                                onOpenFullTab = {
                                    dialog.dismiss()
                                    activity.addAlbum(HelperUnit.domain(url), url, true)
                                },
                                onDismiss = { dialog.dismiss() }
                            )
                        }
                    }
                }
                dialog.setContentView(composeView)
                dialog.show()
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
    val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    // Bare PetalGeckoView, not routed through BrowserWebViewController - never
    // added to BrowserContainer, never made the activity's currentAlbumController,
    // so it never becomes a real tab. destroy() in the DisposableEffect below
    // tears the GeckoSession down completely when this composable leaves.
    val previewGeckoView = remember { PetalGeckoView(context) }

    DisposableEffect(url) {
        onDispose {
            previewGeckoView.destroy()
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
            border = if (isGlass) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isGlass) Modifier.liquidGlassChrome(sheetShape, enabled = true) else Modifier)
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))

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
                    text = HelperUnit.domain(url) ?: url,
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
                        previewGeckoView.apply {
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
