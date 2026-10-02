package com.petal.browser.compose.menu

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupPosition
import com.petal.browser.ui.containment.PetalMotion
import com.petal.browser.ui.containment.PetalSectionLabel
import com.petal.browser.ui.containment.rememberPetalGroupPressScale
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.unit.HelperUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface PetalLinkContextMenuHandler {
    fun onOpenInNewTab() {}
    fun onOpenInNewTabInGroup() {}
    fun onOpenInIncognitoTab() {}
    fun onPreviewPage() {}
    fun onCopyLinkAddress() {}
    fun onCopyLinkText() {}
    fun onDownloadLink() {}
    fun onOpenImageInNewTab() {}
    /** Copies the actual image (pixels) to the clipboard. */
    fun onCopyImage() {}
    /** Copies the image's URL to the clipboard. */
    fun onCopyImageAddress() { onCopyLinkAddress() }
    fun onDownloadImage() {}
    fun onDownloadVideo() {}
    fun onDownloadAudio() {}
    fun onAddToReadingList() {}
    fun onShareLink() {}
    fun onShareImage() {}
    fun onScanImage() {}
    fun onSearchWithGoogleLens() {}
    fun onSearchWebText(text: String) {}
    fun onCopySelectedText(text: String) {}
    fun onShareSelectedText(text: String) {}
    fun onDialPhoneNumber(tel: String) {}
    fun onSendEmail(mailto: String) {}
    fun onOpenMapLocation(geo: String) {}
    /** Opens the image in Petal's built-in full-screen image viewer */
    fun onViewInPetalViewer() {}
}

// ---------------------------------------------------------------------------------------------
// Model
// ---------------------------------------------------------------------------------------------

private enum class MenuKind(val label: String, val icon: ImageVector) {
    LINK("Link", Icons.Rounded.Language),
    IMAGE("Image", Icons.Rounded.Image),
    IMAGE_LINK("Image & Link", Icons.Rounded.Image),
    VIDEO("Video", Icons.Rounded.Videocam),
    AUDIO("Audio", Icons.Rounded.Audiotrack),
    TEXT("Selected text", Icons.Rounded.FormatQuote),
    EMAIL("Email address", Icons.Rounded.Email),
    PHONE("Phone number", Icons.Rounded.Phone),
    LOCATION("Location", Icons.Rounded.Place),
}

/** Colour role of an action's icon badge. Groups related features visually. */
private enum class MenuTone { OPEN, COPY, SAVE, SEARCH }

private data class MenuAction(
    val id: String,
    val title: String,
    /** One dedicated icon per feature - the icon describes the action, it is never decoration. */
    val icon: ImageVector,
    val tone: MenuTone,
    val onClick: () -> Unit,
)

private data class MenuSection(val label: String, val actions: List<MenuAction>)

private fun menuKindOf(
    linkUrl: String,
    isImage: Boolean,
    isVideo: Boolean,
    isAudio: Boolean,
    selectedText: String?,
    hasLinkAndImage: Boolean = false,
): MenuKind = when {
    selectedText != null -> MenuKind.TEXT
    linkUrl.startsWith("mailto:") -> MenuKind.EMAIL
    linkUrl.startsWith("tel:") -> MenuKind.PHONE
    linkUrl.startsWith("geo:") -> MenuKind.LOCATION
    hasLinkAndImage -> MenuKind.IMAGE_LINK
    isAudio -> MenuKind.AUDIO
    isImage -> MenuKind.IMAGE
    isVideo -> MenuKind.VIDEO
    else -> MenuKind.LINK
}

private fun buildSections(
    kind: MenuKind,
    linkUrl: String,
    selectedText: String?,
    handler: PetalLinkContextMenuHandler,
): List<MenuSection> = when (kind) {
    MenuKind.IMAGE_LINK -> listOf(
        MenuSection(
            "Link actions",
            listOf(
                MenuAction("open_tab", "Open link in new tab", Icons.Rounded.Tab, MenuTone.OPEN) { handler.onOpenInNewTab() },
                MenuAction("open_group", "Open in new tab group", Icons.Rounded.Layers, MenuTone.OPEN) { handler.onOpenInNewTabInGroup() },
                MenuAction("open_incognito", "Open link in private tab", Icons.Rounded.VisibilityOff, MenuTone.OPEN) { handler.onOpenInIncognitoTab() },
                MenuAction("preview", "Preview page", Icons.Rounded.FindInPage, MenuTone.OPEN) { handler.onPreviewPage() },
                MenuAction("copy_link", "Copy link address", Icons.Rounded.Link, MenuTone.COPY) { handler.onCopyLinkAddress() },
                MenuAction("download_link", "Download link target", Icons.Rounded.FileDownload, MenuTone.SAVE) { handler.onDownloadLink() },
                MenuAction("share_link", "Share link", Icons.Rounded.Share, MenuTone.SAVE) { handler.onShareLink() },
            ),
        ),
        MenuSection(
            "Image actions",
            listOf(
                MenuAction("view_image", "View in Petal Viewer", Icons.Rounded.PhotoLibrary, MenuTone.OPEN) { handler.onViewInPetalViewer() },
                MenuAction("open_image_tab", "Open image in new tab", Icons.Rounded.Tab, MenuTone.OPEN) { handler.onOpenImageInNewTab() },
                MenuAction("copy_image", "Copy image", Icons.Rounded.Image, MenuTone.COPY) { handler.onCopyImage() },
                MenuAction("copy_image_address", "Copy image address", Icons.Rounded.Link, MenuTone.COPY) { handler.onCopyImageAddress() },
                MenuAction("save_image", "Save image", Icons.Rounded.SaveAlt, MenuTone.SAVE) { handler.onDownloadImage() },
                MenuAction("share_image", "Share image", Icons.Rounded.Share, MenuTone.SAVE) { handler.onShareImage() },
                MenuAction("lens", "Search with Google Lens", Icons.Rounded.TravelExplore, MenuTone.SEARCH) { handler.onSearchWithGoogleLens() },
                MenuAction("scan_image", "Scan image for QR & text", Icons.Rounded.DocumentScanner, MenuTone.SEARCH) { handler.onScanImage() },
            ),
        ),
    )
    MenuKind.TEXT -> {
        val text = selectedText.orEmpty()
        listOf(
            MenuSection(
                "Selected text",
                listOf(
                    MenuAction("search_text", "Search the web", Icons.Rounded.Search, MenuTone.SEARCH) { handler.onSearchWebText(text) },
                    MenuAction("copy_text", "Copy text", Icons.Rounded.ContentCopy, MenuTone.COPY) { handler.onCopySelectedText(text) },
                    MenuAction("share_text", "Share text", Icons.Rounded.Share, MenuTone.SAVE) { handler.onShareSelectedText(text) },
                ),
            ),
        )
    }

    MenuKind.EMAIL -> listOf(
        MenuSection(
            "Email",
            listOf(
                MenuAction("send_email", "Send email", Icons.Rounded.Email, MenuTone.OPEN) { handler.onSendEmail(linkUrl) },
                MenuAction("copy_email", "Copy email address", Icons.Rounded.ContentCopy, MenuTone.COPY) { handler.onCopyLinkAddress() },
            ),
        ),
    )

    MenuKind.PHONE -> listOf(
        MenuSection(
            "Phone",
            listOf(
                MenuAction("call", "Call number", Icons.Rounded.Call, MenuTone.OPEN) { handler.onDialPhoneNumber(linkUrl) },
                MenuAction("copy_phone", "Copy phone number", Icons.Rounded.ContentCopy, MenuTone.COPY) { handler.onCopyLinkAddress() },
            ),
        ),
    )

    MenuKind.LOCATION -> listOf(
        MenuSection(
            "Location",
            listOf(
                MenuAction("open_map", "Open in Maps", Icons.Rounded.Place, MenuTone.OPEN) { handler.onOpenMapLocation(linkUrl) },
                MenuAction("copy_geo", "Copy coordinates", Icons.Rounded.ContentCopy, MenuTone.COPY) { handler.onCopyLinkAddress() },
            ),
        ),
    )

    MenuKind.AUDIO -> listOf(
        MenuSection(
            "Open",
            listOf(MenuAction("open_audio", "Open audio in new tab", Icons.Rounded.Tab, MenuTone.OPEN) { handler.onOpenInNewTab() }),
        ),
        MenuSection(
            "Copy",
            listOf(MenuAction("copy_audio_link", "Copy audio link", Icons.Rounded.Link, MenuTone.COPY) { handler.onCopyLinkAddress() }),
        ),
        MenuSection(
            "Save & share",
            listOf(
                MenuAction("download_audio", "Download audio", Icons.Rounded.MusicNote, MenuTone.SAVE) { handler.onDownloadAudio() },
                MenuAction("share_audio", "Share audio", Icons.Rounded.Share, MenuTone.SAVE) { handler.onShareLink() },
            ),
        ),
    )

    MenuKind.VIDEO -> listOf(
        MenuSection(
            "Open",
            listOf(MenuAction("open_video", "Open video in new tab", Icons.Rounded.Tab, MenuTone.OPEN) { handler.onOpenInNewTab() }),
        ),
        MenuSection(
            "Copy",
            listOf(MenuAction("copy_video_link", "Copy video link", Icons.Rounded.Link, MenuTone.COPY) { handler.onCopyLinkAddress() }),
        ),
        MenuSection(
            "Save & share",
            listOf(
                MenuAction("download_video", "Download video", Icons.Rounded.Movie, MenuTone.SAVE) { handler.onDownloadVideo() },
                MenuAction("share_video", "Share video", Icons.Rounded.Share, MenuTone.SAVE) { handler.onShareLink() },
            ),
        ),
    )

    MenuKind.IMAGE -> listOf(
        MenuSection(
            "Open",
            listOf(
                MenuAction("view_image", "View in Petal Viewer", Icons.Rounded.PhotoLibrary, MenuTone.OPEN) { handler.onViewInPetalViewer() },
                MenuAction("open_image_tab", "Open image in new tab", Icons.Rounded.Tab, MenuTone.OPEN) { handler.onOpenImageInNewTab() },
                MenuAction("open_image_incognito", "Open image in private tab", Icons.Rounded.VisibilityOff, MenuTone.OPEN) { handler.onOpenInIncognitoTab() },
            ),
        ),
        MenuSection(
            "Copy",
            listOf(
                MenuAction("copy_image", "Copy image", Icons.Rounded.Image, MenuTone.COPY) { handler.onCopyImage() },
                MenuAction("copy_image_address", "Copy image address", Icons.Rounded.Link, MenuTone.COPY) { handler.onCopyImageAddress() },
            ),
        ),
        MenuSection(
            "Save & share",
            listOf(
                MenuAction("save_image", "Save image", Icons.Rounded.SaveAlt, MenuTone.SAVE) { handler.onDownloadImage() },
                MenuAction("share_image", "Share image", Icons.Rounded.Share, MenuTone.SAVE) { handler.onShareImage() },
                MenuAction("share_image_link", "Share image link", Icons.Rounded.Send, MenuTone.SAVE) { handler.onShareLink() },
            ),
        ),
        MenuSection(
            "Search & scan",
            listOf(
                MenuAction("lens", "Search with Google Lens", Icons.Rounded.TravelExplore, MenuTone.SEARCH) { handler.onSearchWithGoogleLens() },
                MenuAction("scan_image", "Scan image for QR & text", Icons.Rounded.DocumentScanner, MenuTone.SEARCH) { handler.onScanImage() },
            ),
        ),
    )

    MenuKind.LINK -> listOf(
        MenuSection(
            "Open",
            listOf(
                MenuAction("open_tab", "Open in new tab", Icons.Rounded.Tab, MenuTone.OPEN) { handler.onOpenInNewTab() },
                MenuAction("open_group", "Open in new tab group", Icons.Rounded.Layers, MenuTone.OPEN) { handler.onOpenInNewTabInGroup() },
                MenuAction("open_incognito", "Open in private tab", Icons.Rounded.VisibilityOff, MenuTone.OPEN) { handler.onOpenInIncognitoTab() },
                MenuAction("preview", "Preview page", Icons.Rounded.FindInPage, MenuTone.OPEN) { handler.onPreviewPage() },
            ),
        ),
        MenuSection(
            "Copy",
            listOf(
                MenuAction("copy_link", "Copy link", Icons.Rounded.Link, MenuTone.COPY) { handler.onCopyLinkAddress() },
                MenuAction("copy_link_text", "Copy link text", Icons.Rounded.TextFields, MenuTone.COPY) { handler.onCopyLinkText() },
            ),
        ),
        MenuSection(
            "Save & share",
            listOf(
                MenuAction("download_link", "Download link", Icons.Rounded.FileDownload, MenuTone.SAVE) { handler.onDownloadLink() },
                MenuAction("reading_list", "Add to reading list", Icons.Rounded.MenuBook, MenuTone.SAVE) { handler.onAddToReadingList() },
                MenuAction("share_link", "Share link", Icons.Rounded.Share, MenuTone.SAVE) { handler.onShareLink() },
            ),
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------------------------

/**
 * Staggered spring entrance: each element fades, rises and scales in slightly after the one before it.
 * Capped so long menus never feel slow.
 */
@Composable
private fun Modifier.petalStaggerIn(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 9) * 32L)
        progress.animateTo(1f, PetalMotion.spatialDefault())
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 26.dp.toPx()
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

@Composable
fun PetalLinkContextMenuSheet(
    linkTitle: String?,
    linkUrl: String,
    faviconUrl: String? = null,
    imageUrl: String? = null,
    isImage: Boolean = false,
    isVideo: Boolean = false,
    isAudio: Boolean = false,
    selectedText: String? = null,
    onDismiss: () -> Unit,
    handler: PetalLinkContextMenuHandler,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }

    val hasLinkAndImage = !imageUrl.isNullOrBlank() && linkUrl.isNotBlank() && isImage
    val kind = remember(linkUrl, isImage, isVideo, isAudio, selectedText, hasLinkAndImage) {
        menuKindOf(linkUrl, isImage, isVideo, isAudio, selectedText, hasLinkAndImage)
    }
    val sections = remember(kind, linkUrl, selectedText, handler) {
        buildSections(kind, linkUrl, selectedText, handler)
    }

    // Tap: haptic -> row morphs + highlights -> brief beat so the feedback is seen -> dismiss -> action.
    fun choose(action: MenuAction) {
        if (selectedId != null) return
        selectedId = action.id
        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.75f)
        scope.launch {
            delay(120)
            onDismiss()
            action.onClick()
        }
    }

    val title = when (kind) {
        MenuKind.TEXT -> "Selected text"
        MenuKind.EMAIL -> linkUrl.removePrefix("mailto:").substringBefore('?')
        MenuKind.PHONE -> linkUrl.removePrefix("tel:")
        MenuKind.LOCATION -> "Map location"
        MenuKind.IMAGE_LINK -> linkTitle?.takeIf { it.isNotBlank() } ?: HelperUnit.domain(linkUrl) ?: linkUrl
        else -> linkTitle?.takeIf { it.isNotBlank() } ?: HelperUnit.domain(linkUrl) ?: linkUrl
    }
    val subtitle = when (kind) {
        MenuKind.TEXT -> selectedText
        MenuKind.EMAIL, MenuKind.PHONE -> null
        MenuKind.LOCATION -> linkUrl.removePrefix("geo:")
        else -> linkUrl.ifBlank { "about:blank" }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    .align(Alignment.CenterHorizontally),
            )

            PetalMenuHeader(
                kind = kind,
                title = title,
                subtitle = subtitle,
                imageUrl = if (kind == MenuKind.IMAGE || kind == MenuKind.IMAGE_LINK) (imageUrl ?: linkUrl) else null,
                faviconUrl = if (kind == MenuKind.LINK || kind == MenuKind.IMAGE_LINK) faviconUrl else null,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .petalStaggerIn(0),
            )

            var offset = 1
            val showLabels = sections.size > 1
            sections.forEach { section ->
                Spacer(Modifier.height(if (showLabels) 14.dp else 10.dp))
                if (showLabels) {
                    PetalSectionLabel(
                        section.label,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .petalStaggerIn(offset),
                    )
                }
                val base = offset
                PetalGroup(rowCount = section.actions.size, modifier = Modifier.padding(horizontal = 16.dp)) { index, position ->
                    val action = section.actions[index]
                    PetalMenuRow(
                        action = action,
                        position = position,
                        enterIndex = base + index,
                        selected = selectedId == action.id,
                        dimmed = selectedId != null && selectedId != action.id,
                        onClick = { choose(action) },
                    )
                }
                offset += section.actions.size
            }
        }
    }
}

@Composable
private fun PetalMenuHeader(
    kind: MenuKind,
    title: String,
    subtitle: String?,
    imageUrl: String?,
    faviconUrl: String?,
    modifier: Modifier = Modifier,
) {
    var previewLoaded by remember(imageUrl, faviconUrl) { mutableStateOf(false) }
    val previewModel = imageUrl ?: faviconUrl
    val isImage = imageUrl != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(if (isImage) 18.dp else 20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (!previewLoaded) {
                    Icon(
                        imageVector = kind.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
                if (previewModel != null) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = null,
                        contentScale = if (isImage) ContentScale.Crop else ContentScale.Fit,
                        modifier = if (isImage) Modifier.fillMaxSize() else Modifier.size(30.dp),
                        onSuccess = { previewLoaded = true },
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (kind == MenuKind.TEXT) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PetalMenuRow(
    action: MenuAction,
    position: PetalGroupPosition,
    enterIndex: Int,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale = rememberPetalGroupPressScale(source)
    val active = pressed || selected

    // Containment corners (same 28 / 6 language as Settings) that morph fuller while pressed / chosen.
    val (outerTop, outerBottom) = when (position) {
        PetalGroupPosition.SINGLE -> 28.dp to 28.dp
        PetalGroupPosition.TOP -> 28.dp to 6.dp
        PetalGroupPosition.MIDDLE -> 6.dp to 6.dp
        PetalGroupPosition.BOTTOM -> 6.dp to 28.dp
    }
    val topRadius by animateDpAsState(if (active) 22.dp else outerTop, PetalMotion.spatialFast(), label = "menuRowTop")
    val bottomRadius by animateDpAsState(if (active) 22.dp else outerBottom, PetalMotion.spatialFast(), label = "menuRowBottom")
    val badgeRadius by animateDpAsState(if (active) 20.dp else 14.dp, PetalMotion.spatialFast(), label = "menuRowBadge")
    val shape = RoundedCornerShape(
        topStart = maxOf(topRadius, 0.dp),
        topEnd = maxOf(topRadius, 0.dp),
        bottomEnd = maxOf(bottomRadius, 0.dp),
        bottomStart = maxOf(bottomRadius, 0.dp),
    )

    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        PetalMotion.effectsFast(),
        label = "menuRowContainer",
    )
    val (toneContainer, toneContent) = when (action.tone) {
        MenuTone.OPEN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        MenuTone.COPY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        MenuTone.SAVE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        MenuTone.SEARCH -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    val badgeContainer by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else toneContainer,
        PetalMotion.effectsFast(),
        label = "menuRowBadgeContainer",
    )
    val badgeContent by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else toneContent,
        PetalMotion.effectsFast(),
        label = "menuRowBadgeContent",
    )
    val contentAlpha by animateFloatAsState(if (dimmed) 0.45f else 1f, PetalMotion.effectsFast(), label = "menuRowAlpha")

    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        modifier = Modifier
            .fillMaxWidth()
            .petalStaggerIn(enterIndex)
            .scale(pressScale)
            .alpha(contentAlpha),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(badgeRadius))
                    .background(badgeContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = badgeContent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bridge
// ---------------------------------------------------------------------------------------------

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
        handler: PetalLinkContextMenuHandler,
    ) {
        show(
            activity = activity,
            linkTitle = linkTitle,
            linkUrl = linkUrl,
            faviconUrl = faviconUrl,
            imageUrl = null,
            isImage = isImage,
            isVideo = isVideo,
            isAudio = false,
            selectedText = null,
            handler = handler,
        )
    }

    @JvmStatic
    @JvmOverloads
    fun show(
        activity: ComponentActivity,
        linkTitle: String?,
        linkUrl: String,
        faviconUrl: String? = null,
        imageUrl: String? = null,
        isImage: Boolean = false,
        isVideo: Boolean = false,
        isAudio: Boolean = false,
        selectedText: String? = null,
        handler: PetalLinkContextMenuHandler,
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

                        val appFont = remember(fontName) { AppFont.fromName(fontName) }
                        val colorStyle = remember(styleName) {
                            try { ColorStyle.valueOf(styleName) } catch (e: Exception) { ColorStyle.TONAL_SPOT }
                        }

                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = appFont,
                            colorStyle = colorStyle,
                            paletteId = paletteId,
                        ) {
                            PetalLinkContextMenuSheet(
                                linkTitle = linkTitle,
                                linkUrl = linkUrl,
                                faviconUrl = faviconUrl,
                                imageUrl = imageUrl,
                                isImage = isImage,
                                isVideo = isVideo,
                                isAudio = isAudio,
                                selectedText = selectedText,
                                onDismiss = { dialog.dismiss() },
                                handler = handler,
                            )
                        }
                    }
                }
                dialog.setContentView(composeView)
                // Always open fully expanded (no half-height peek) so the whole menu is visible at once,
                // and let our own rounded Surface be the sheet's visible container.
                dialog.behavior.skipCollapsed = true
                dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                dialog.setOnShowListener {
                    dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                        ?.setBackgroundColor(AndroidColor.TRANSPARENT)
                    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
