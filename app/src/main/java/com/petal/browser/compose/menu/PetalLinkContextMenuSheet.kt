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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(14.dp))

            // Header with Favicon / Shape Avatar, Title, and Truncated URL
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
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
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!linkTitle.isNullOrBlank()) linkTitle else HelperUnit.domain(linkUrl) ?: linkUrl,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = linkUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Group 1: Primary Navigation Actions
            Text(
                text = if (isImage) "Image Options" else if (isVideo) "Video Options" else "Link Options",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                primaryActions.forEachIndexed { index, spec ->
                    SettingsItem(
                        title = spec.title,
                        subtitle = "",
                        shape = getGroupItemShape(index, primaryActions.size),
                        leadingIcon = {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        onClick = spec.onClick
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Group 2: Utilities & Sharing
            Text(
                text = "Clipboard & Sharing",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                shareActions.forEachIndexed { index, spec ->
                    SettingsItem(
                        title = spec.title,
                        subtitle = "",
                        shape = getGroupItemShape(index, shareActions.size),
                        leadingIcon = {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        onClick = spec.onClick
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
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
