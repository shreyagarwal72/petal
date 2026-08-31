package com.petal.browser.compose.menu

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.petal.browser.ui.components.SettingsItem
import com.petal.browser.ui.components.getGroupItemShape
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.HelperUnit

interface PetalLinkContextMenuHandler {
    fun onOpenInNewTab() {}
    fun onOpenInNewTabInGroup() {}
    fun onOpenInIncognitoTab() {}
    fun onOpenInNewWindow() {}
    fun onPreviewPage() {}
    fun onCopyLinkAddress() {}
    fun onCopyLinkText() {}
    fun onDownloadLink() {}
    fun onOpenImageInNewTab() {}
    fun onCopyImage() {}
    fun onDownloadImage() {}
    fun onDownloadVideo() {}
    fun onAddToReadingList() {}
    fun onShareLink() {}
    fun onShareImage() {}
    fun onScanImage() {}
    fun onSearchWithGoogleLens() {}
}

private data class MenuItemSpec(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun PetalLinkContextMenuSheet(
    linkTitle: String?,
    linkUrl: String,
    faviconUrl: String? = null,
    isImage: Boolean = false,
    isVideo: Boolean = false,
    onDismiss: () -> Unit,
    handler: PetalLinkContextMenuHandler
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag Handle Bar
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

            // Header with Favicon / Shape Avatar, Title, and Truncated URL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!faviconUrl.isNullOrEmpty() && !isImage && !isVideo) {
                        AsyncImage(
                            model = faviconUrl,
                            contentDescription = linkTitle ?: "Site Favicon",
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isImage) Icons.Rounded.Image else if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Language,
                            contentDescription = "Content",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!linkTitle.isNullOrBlank()) linkTitle else HelperUnit.domain(linkUrl) ?: linkUrl,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = linkUrl.ifBlank { "about:blank" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Primary Navigation Actions
            val primaryActions = remember(isImage, isVideo, linkUrl) {
                if (isImage) {
                    listOf(
                        MenuItemSpec("Open image in new tab", Icons.Rounded.OpenInNew) {
                            onDismiss()
                            handler.onOpenImageInNewTab()
                        },
                        MenuItemSpec("Download image", Icons.Rounded.Download) {
                            onDismiss()
                            handler.onDownloadImage()
                        },
                        MenuItemSpec("Search image with Google Lens", Icons.Rounded.TravelExplore) {
                            onDismiss()
                            handler.onSearchWithGoogleLens()
                        },
                        MenuItemSpec("Scan image with Petal Scanner", Icons.Rounded.CenterFocusWeak) {
                            onDismiss()
                            handler.onScanImage()
                        }
                    )
                } else if (isVideo) {
                    listOf(
                        MenuItemSpec("Open video in new tab", Icons.Rounded.OpenInNew) {
                            onDismiss()
                            handler.onOpenInNewTab()
                        },
                        MenuItemSpec("Download video", Icons.Rounded.Download) {
                            onDismiss()
                            handler.onDownloadVideo()
                        }
                    )
                } else {
                    listOf(
                        MenuItemSpec("Open in new tab", Icons.Rounded.OpenInNew) {
                            onDismiss()
                            handler.onOpenInNewTab()
                        },
                        MenuItemSpec("Open in new tab in group", Icons.Rounded.TabUnselected) {
                            onDismiss()
                            handler.onOpenInNewTabInGroup()
                        },
                        MenuItemSpec("Open in Incognito tab", Icons.Rounded.VisibilityOff) {
                            onDismiss()
                            handler.onOpenInIncognitoTab()
                        },
                        MenuItemSpec("Open in new window", Icons.Rounded.OpenInBrowser) {
                            onDismiss()
                            handler.onOpenInNewWindow()
                        },
                        MenuItemSpec("Preview page", Icons.Rounded.FindInPage) {
                            onDismiss()
                            handler.onPreviewPage()
                        }
                    )
                }
            }

            primaryActions.forEach { spec ->
                ContextMenuItemRow(
                    icon = spec.icon,
                    title = spec.title,
                    onClick = spec.onClick
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // Clipboard & Sharing Actions
            val shareActions = remember(isImage, isVideo, linkUrl) {
                if (isImage) {
                    listOf(
                        MenuItemSpec("Copy image URL", Icons.Rounded.ContentCopy) {
                            onDismiss()
                            handler.onCopyImage()
                        },
                        MenuItemSpec("Share image", Icons.Rounded.Share) {
                            onDismiss()
                            handler.onShareImage()
                        }
                    )
                } else if (isVideo) {
                    listOf(
                        MenuItemSpec("Copy video link", Icons.Rounded.ContentCopy) {
                            onDismiss()
                            handler.onCopyLinkAddress()
                        },
                        MenuItemSpec("Share video", Icons.Rounded.Share) {
                            onDismiss()
                            handler.onShareLink()
                        }
                    )
                } else {
                    listOf(
                        MenuItemSpec("Copy link address", Icons.Rounded.ContentCopy) {
                            onDismiss()
                            handler.onCopyLinkAddress()
                        },
                        MenuItemSpec("Copy link text", Icons.Rounded.Title) {
                            onDismiss()
                            handler.onCopyLinkText()
                        },
                        MenuItemSpec("Download link", Icons.Rounded.Download) {
                            onDismiss()
                            handler.onDownloadLink()
                        },
                        MenuItemSpec("Add to reading list", Icons.Rounded.BookmarkAdd) {
                            onDismiss()
                            handler.onAddToReadingList()
                        },
                        MenuItemSpec("Share link", Icons.Rounded.Share) {
                            onDismiss()
                            handler.onShareLink()
                        }
                    )
                }
            }

            shareActions.forEach { spec ->
                ContextMenuItemRow(
                    icon = spec.icon,
                    title = spec.title,
                    onClick = spec.onClick
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ContextMenuItemRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                    .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
                onClick()
            })
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

object PetalLinkContextMenuBridge {
    @JvmStatic
    @JvmOverloads
    fun show(
        activity: ComponentActivity,
        linkTitle: String?,
        linkUrl: String,
        faviconUrl: String? = null,
        isImage: Boolean = false,
        isVideo: Boolean = false,
        handler: PetalLinkContextMenuHandler
    ) {
        activity.runOnUiThread {
            try {
                val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                        val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        val paletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                        val isAmoled = sp.getBoolean("sp_amoled", false)
                        val dynamicColor = sp.getBoolean("useDynamicColor", isDynamicColorSupported)

                        val appFont = remember(fontName) {
                            AppFont.fromName(fontName)
                        }
                        val colorStyle = remember(styleName) {
                            try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                        }

                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId
                        ) {
                            PetalLinkContextMenuSheet(
                                linkTitle = linkTitle,
                                linkUrl = linkUrl,
                                faviconUrl = faviconUrl,
                                isImage = isImage,
                                isVideo = isVideo,
                                onDismiss = { dialog.dismiss() },
                                handler = handler
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
