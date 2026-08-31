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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported

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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val headerShape = remember {
                    com.petal.browser.ui.theme.PetalMaterialShapes.SoftScallop.toShape()
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(headerShape)
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!linkTitle.isNullOrBlank()) linkTitle else HelperUnit.domain(linkUrl) ?: linkUrl,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = linkUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Group 1: Primary Navigation Actions
            Text(
                text = if (isImage) "Image Options" else if (isVideo) "Video Options" else "Link Options",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    if (isImage) {
                        ContextMenuItem("Open image in new tab", Icons.Rounded.OpenInNew, shape = com.petal.browser.ui.theme.PetalMaterialShapes.RoundedSquare.toShape()) {
                            onDismiss()
                            handler.onOpenImageInNewTab()
                        }
                        ContextMenuItem("Download image", Icons.Rounded.Download, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Cookie6Sided.toShape()) {
                            onDismiss()
                            handler.onDownloadImage()
                        }
                        ContextMenuItem("Search image with Google Lens", Icons.Rounded.TravelExplore, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Arch.toShape()) {
                            onDismiss()
                            handler.onSearchWithGoogleLens()
                        }
                        ContextMenuItem("Scan image with Petal Scanner", Icons.Rounded.CenterFocusWeak, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Boom.toShape()) {
                            onDismiss()
                            handler.onScanImage()
                        }
                    } else if (isVideo) {
                        ContextMenuItem("Open video in new tab", Icons.Rounded.OpenInNew, shape = com.petal.browser.ui.theme.PetalMaterialShapes.RoundedSquare.toShape()) {
                            onDismiss()
                            handler.onOpenInNewTab()
                        }
                        ContextMenuItem("Download video", Icons.Rounded.Download, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Cookie6Sided.toShape()) {
                            onDismiss()
                            handler.onDownloadVideo()
                        }
                    } else {
                        ContextMenuItem("Open in new tab", Icons.Rounded.OpenInNew, shape = com.petal.browser.ui.theme.PetalMaterialShapes.RoundedSquare.toShape()) {
                            onDismiss()
                            handler.onOpenInNewTab()
                        }
                        ContextMenuItem("Open in new tab in group", Icons.Rounded.TabUnselected, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Cookie6Sided.toShape()) {
                            onDismiss()
                            handler.onOpenInNewTabInGroup()
                        }
                        ContextMenuItem("Open in Incognito tab", Icons.Rounded.VisibilityOff, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Ghostish.toShape()) {
                            onDismiss()
                            handler.onOpenInIncognitoTab()
                        }
                        ContextMenuItem("Open in new window", Icons.Rounded.OpenInBrowser, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Arch.toShape()) {
                            onDismiss()
                            handler.onOpenInNewWindow()
                        }
                        ContextMenuItem("Preview page", Icons.Rounded.FindInPage, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Gem.toShape()) {
                            onDismiss()
                            handler.onPreviewPage()
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Group 2: Utilities & Sharing
            Text(
                text = "Clipboard & Sharing",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    if (isImage) {
                        ContextMenuItem("Copy image URL", Icons.Rounded.ContentCopy, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Pill.toShape()) {
                            onDismiss()
                            handler.onCopyImage()
                        }
                        ContextMenuItem("Share image", Icons.Rounded.Share, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Clover4Leaf.toShape()) {
                            onDismiss()
                            handler.onShareImage()
                        }
                    } else if (isVideo) {
                        ContextMenuItem("Copy video link", Icons.Rounded.ContentCopy, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Pill.toShape()) {
                            onDismiss()
                            handler.onCopyLinkAddress()
                        }
                        ContextMenuItem("Share video", Icons.Rounded.Share, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Clover4Leaf.toShape()) {
                            onDismiss()
                            handler.onShareLink()
                        }
                    } else {
                        ContextMenuItem("Copy link address", Icons.Rounded.ContentCopy, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Pill.toShape()) {
                            onDismiss()
                            handler.onCopyLinkAddress()
                        }
                        ContextMenuItem("Copy link text", Icons.Rounded.Title, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Hexagon.toShape()) {
                            onDismiss()
                            handler.onCopyLinkText()
                        }
                        ContextMenuItem("Download link", Icons.Rounded.Download, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Cookie7Sided.toShape()) {
                            onDismiss()
                            handler.onDownloadLink()
                        }
                        ContextMenuItem("Add to reading list", Icons.Rounded.BookmarkAdd, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Flower.toShape()) {
                            onDismiss()
                            handler.onAddToReadingList()
                        }
                        ContextMenuItem("Share link", Icons.Rounded.Share, shape = com.petal.browser.ui.theme.PetalMaterialShapes.Clover4Leaf.toShape()) {
                            onDismiss()
                            handler.onShareLink()
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ContextMenuItem(
    label: String,
    leadingIcon: ImageVector,
    trailingIcon: ImageVector? = null,
    shape: androidx.compose.ui.graphics.Shape? = null,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val itemShape = remember(label, shape) {
        shape ?: run {
            val hash = kotlin.math.abs(label.hashCode())
            val shapes = listOf(
                com.petal.browser.ui.theme.PetalMaterialShapes.RoundedSquare.toShape(),
                com.petal.browser.ui.theme.PetalMaterialShapes.Cookie6Sided.toShape(),
                com.petal.browser.ui.theme.PetalMaterialShapes.Arch.toShape(),
                com.petal.browser.ui.theme.PetalMaterialShapes.Hexagon.toShape(),
                com.petal.browser.ui.theme.PetalMaterialShapes.Octagon.toShape(),
                com.petal.browser.ui.theme.PetalMaterialShapes.Pill.toShape(),
                com.petal.browser.ui.theme.PetalMaterialShapes.SoftScallop.toShape(),
                com.petal.browser.ui.theme.PetalMaterialShapes.Clover4Leaf.toShape()
            )
            shapes[hash % shapes.size]
        }
    }

    Surface(
        onClick = {
            com.petal.browser.haptics.PetalHapticEngine.getInstance(context)
                .playIfEnabled(context, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f)
            onClick()
        },
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(itemShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
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
