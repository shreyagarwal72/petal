package com.petal.browser.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.theme.PetalExpressiveTheme

object PetalPlayUpdatePopupBridge {

    @JvmStatic
    fun showUpdateAvailableDialog(
        activity: ComponentActivity,
        onUpdateNow: () -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        showDialog(activity, isDownloaded = false, onUpdateNow = onUpdateNow, onRestartNow = {}, onDismiss = onDismiss)
    }

    @JvmStatic
    fun showUpdateDownloadedDialog(
        activity: ComponentActivity,
        onRestartNow: () -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        showDialog(activity, isDownloaded = true, onUpdateNow = {}, onRestartNow = onRestartNow, onDismiss = onDismiss)
    }

    private fun showDialog(
        activity: ComponentActivity,
        isDownloaded: Boolean,
        onUpdateNow: () -> Unit,
        onRestartNow: () -> Unit,
        onDismiss: () -> Unit
    ) {
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            try {
                var composeView: ComposeView? = null
                composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                        val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                        val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                        val isAmoled = sp.getBoolean("sp_amoled", false)

                        val appFont = remember(fontName) { com.petal.browser.ui.theme.AppFont.fromName(fontName) }
                        val colorStyle = remember(styleName) {
                            try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (_: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                        }

                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId
                        ) {
                            var showSheet by remember { mutableStateOf(true) }
                            if (showSheet) {
                                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                ModalBottomSheet(
                                    onDismissRequest = {
                                        showSheet = false
                                        val parent = composeView?.parent as? ViewGroup
                                        parent?.removeView(composeView)
                                        onDismiss()
                                    },
                                    sheetState = sheetState,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                    dragHandle = { BottomSheetDefaults.DragHandle() }
                                ) {
                                    PetalPlayUpdatePopupContent(
                                        isDownloaded = isDownloaded,
                                        onUpdateNow = {
                                            showSheet = false
                                            val parent = composeView?.parent as? ViewGroup
                                            parent?.removeView(composeView)
                                            onUpdateNow()
                                        },
                                        onRestartNow = {
                                            showSheet = false
                                            val parent = composeView?.parent as? ViewGroup
                                            parent?.removeView(composeView)
                                            onRestartNow()
                                        },
                                        onDismiss = {
                                            showSheet = false
                                            val parent = composeView?.parent as? ViewGroup
                                            parent?.removeView(composeView)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                rootView?.addView(
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

@Composable
fun PetalPlayUpdatePopupContent(
    isDownloaded: Boolean,
    onUpdateNow: () -> Unit,
    onRestartNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Expressive Animated Halo Icon
            Surface(
                shape = CircleShape,
                color = if (isDownloaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = if (isDownloaded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Play Store Badge
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Google Play Store",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Headline
            Text(
                text = if (isDownloaded) "Update Ready to Install" else "New Version Available",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = if (isDownloaded)
                    "The update was downloaded in the background. Restart Petal to finish applying the latest updates."
                else
                    "A new release of Petal Browser is available on Google Play with enhanced performance and security upgrades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (!isDownloaded) {
                Spacer(modifier = Modifier.height(20.dp))

                // Feature Highlights Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        UpdateFeatureRow(
                            icon = Icons.Rounded.Speed,
                            title = "Performance & Engine",
                            description = "GeckoView engine optimization, smooth scrolling & fast tab loads"
                        )
                        UpdateFeatureRow(
                            icon = Icons.Rounded.Security,
                            title = "Privacy & Blockers",
                            description = "Updated tracking protection rules and secure browsing patches"
                        )
                        UpdateFeatureRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Material 3 Expressive UI",
                            description = "Polished animations, responsive layouts & stability fixes"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isDownloaded) {
                    Button(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                            onRestartNow()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restart Petal",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Later")
                    }
                } else {
                    Button(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                            onUpdateNow()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update on Google Play",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Not Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateFeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
