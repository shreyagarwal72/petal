package com.petal.browser.media.ytdlp

import android.content.ClipData
import com.petal.browser.ui.theme.PetalText
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.petal.browser.compose.composable.PetalContainedLoadingIndicator
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.media.sniffer.PetalMediaCaptureStore
import com.petal.browser.media.sniffer.PetalMediaSniffer
import com.petal.browser.media.sniffer.PetalNativeSnifferEngine
import com.petal.browser.media.sniffer.PetalResourceSnifferSheet
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.PetalSheetIconPill
import com.petal.browser.ui.containment.PetalSheetPill
import com.petal.browser.ui.containment.PetalSheetTone
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.launch

private sealed interface GrabberLoad {
    object Loading : GrabberLoad
    data class Loaded(val info: YtDlpInfo) : GrabberLoad
    data class Failed(val message: String) : GrabberLoad
    object NotApplicable : GrabberLoad
}

/**
 * Petal Media Grabber sheet: native yt-dlp downloads (video/audio, quality picker) plus a
 * shortcut to every resource the sniffer captured on the page.
 *
 * It is built on [PetalSheet], so liquid glass, blur and the sheet shape follow the app's
 * existing bottom-sheet settings with no grabber-specific options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalMediaGrabberSheet(
    pageUrl: String,
    onDismiss: () -> Unit,
    onPlayVideo: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = remember { PetalHapticEngine.getInstance(context) }

    var showResources by remember { mutableStateOf(false) }

    val engineState by PetalYtDlpEngine.state.collectAsStateWithLifecycle()
    val capturedCount by PetalMediaCaptureStore.totalMediaCount.collectAsStateWithLifecycle()
    val resources by PetalNativeSnifferEngine.currentTabResources.collectAsStateWithLifecycle()
    val detectedCount = maxOf(capturedCount, resources.size)

    val isWebPage = remember(pageUrl) {
        (pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) &&
            !PetalMediaSniffer.isSearchEngineOrInternalUrl(pageUrl)
    }

    var attempt by remember { mutableIntStateOf(0) }
    var load by remember(pageUrl) {
        mutableStateOf<GrabberLoad>(if (isWebPage) GrabberLoad.Loading else GrabberLoad.NotApplicable)
    }
    LaunchedEffect(pageUrl, attempt) {
        if (!isWebPage) return@LaunchedEffect
        load = GrabberLoad.Loading
        PetalYtDlpEngine.fetchInfo(context, pageUrl).fold(
            onSuccess = { load = GrabberLoad.Loaded(it) },
            onFailure = { load = GrabberLoad.Failed(friendlyError(it)) }
        )
    }

    var audioMode by remember { mutableStateOf(false) }
    var selectedId by remember(load) { mutableStateOf<String?>(null) }

    // The resources sheet replaces this one (never stacked); state above survives the swap.
    if (showResources) {
        PetalResourceSnifferSheet(onDismiss = { showResources = false }, onPlayVideo = onPlayVideo)
        return
    }

    PetalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GrabberHeader(load = load, pageUrl = pageUrl)

            when (val l = load) {
                GrabberLoad.Loading -> {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        PetalContainedLoadingIndicator(size = 44.dp)
                    }
                    Text(
                        text = if (engineState is YtDlpEngineState.Initializing) "Setting up yt-dlp…" else "Reading page video…",
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }

                is GrabberLoad.Failed -> {
                    Text(
                        text = l.message,
                        style = PetalText.cardBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetalSheetPill(
                            text = "Retry",
                            icon = Icons.Rounded.Refresh,
                            tone = PetalSheetTone.Primary,
                            minHeight = 56.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { attempt++ }
                        )
                        PetalSheetPill(
                            text = "Update yt-dlp",
                            icon = Icons.Rounded.SystemUpdateAlt,
                            tone = PetalSheetTone.Tertiary,
                            minHeight = 56.dp,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    PetalToast.show(context, "Updating yt-dlp…")
                                    val ok = PetalYtDlpEngine.updateBinary(context)
                                    PetalToast.show(context, if (ok) "yt-dlp updated" else "Update failed")
                                    if (ok) attempt++
                                }
                            }
                        )
                    }
                }

                is GrabberLoad.Loaded -> {
                    val options = if (audioMode) l.info.audioOptions else l.info.videoOptions
                    val selected = options.firstOrNull { it.id == selectedId } ?: options.firstOrNull()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetalSheetPill(
                            text = "Video",
                            icon = Icons.Rounded.VideoLibrary,
                            tone = if (!audioMode) PetalSheetTone.Primary else PetalSheetTone.Neutral,
                            minHeight = 52.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { audioMode = false; selectedId = null }
                        )
                        PetalSheetPill(
                            text = "Audio",
                            icon = Icons.Rounded.Audiotrack,
                            tone = if (audioMode) PetalSheetTone.Primary else PetalSheetTone.Neutral,
                            minHeight = 52.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { audioMode = true; selectedId = null }
                        )
                    }

                    options.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { opt ->
                                val isSel = opt.id == selected?.id
                                PetalSheetPill(
                                    text = opt.label,
                                    icon = if (isSel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    tone = if (isSel) PetalSheetTone.Tertiary else PetalSheetTone.Neutral,
                                    minHeight = 52.dp,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        com.petal.browser.haptics.PetalHapticEngine.getInstance(context).selectionChanged(context)
                                        selectedId = opt.id
                                    }
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    selected?.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = PetalText.cardBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    PetalSheetPill(
                        text = if (selected != null) "Download ${selected.label}" else "Download",
                        icon = Icons.Rounded.Download,
                        tone = PetalSheetTone.Primary,
                        minHeight = 64.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val opt = selected ?: return@PetalSheetPill
                            PetalYtDlpEngine.download(
                                context = context,
                                info = l.info,
                                option = opt,
                                referer = pageUrl,
                                onFinished = { file, error ->
                                    PetalToast.show(
                                        context,
                                        if (file != null) "Saved ${file.name}" else (error ?: "Download failed")
                                    )
                                }
                            )
                            PetalToast.show(context, "Download started")
                            onDismiss()
                        }
                    )
                }

                GrabberLoad.NotApplicable -> Text(
                    text = "Open a page with a video to grab it.",
                    style = PetalText.emptyState,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PetalSheetPill(
                    text = if (detectedCount > 0) "Page resources ($detectedCount)" else "Page resources",
                    icon = Icons.Rounded.TravelExplore,
                    tone = PetalSheetTone.Secondary,
                    minHeight = 56.dp,
                    modifier = Modifier.weight(1f),
                    onClick = { showResources = true }
                )
                if (isWebPage) {
                    PetalSheetIconPill(
                        icon = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy page link",
                        minHeight = 56.dp,
                        modifier = Modifier.size(56.dp),
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Page URL", pageUrl))
                            PetalToast.show(context, "Link copied")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GrabberHeader(load: GrabberLoad, pageUrl: String) {
    val info = (load as? GrabberLoad.Loaded)?.info
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbShape = com.petal.browser.ui.theme.SmoothRoundedShape(22.dp)
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (info?.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(84.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.VideoLibrary, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = info?.title ?: "Media Grabber",
                style = PetalText.sheetTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            val sub = listOfNotNull(
                info?.uploader,
                info?.durationSeconds?.let(::formatDuration)
            ).joinToString(" • ").ifBlank { hostOf(pageUrl) }
            Text(
                text = sub,
                style = PetalText.pageSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: ""

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
}

private fun friendlyError(t: Throwable): String {
    val msg = (t.message ?: "").lowercase()
    return when {
        "unsupported url" in msg -> "yt-dlp found no downloadable video on this page. Check Page resources for files the sniffer caught."
        "sign in" in msg || "login" in msg -> "This video needs you to sign in. Sign in on the page, then retry."
        "unable to resolve host" in msg || "network" in msg || "timed out" in msg -> "No connection. Check your network and retry."
        "429" in msg || "too many requests" in msg -> "The site is rate-limiting requests. Try again in a minute."
        else -> "yt-dlp couldn't read this page. Try Update yt-dlp, then Retry."
    }
}
