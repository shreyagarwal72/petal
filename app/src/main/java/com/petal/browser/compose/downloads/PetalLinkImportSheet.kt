package com.petal.browser.compose.downloads

import android.app.Activity
import com.petal.browser.ui.theme.PetalText
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.download.DownloadPrefs
import com.petal.browser.download.PetalDownloadRouter
import com.petal.browser.download.PetalLinkImport
import com.petal.browser.torrent.PetalTorrentEngineManager
import com.petal.browser.ui.components.PetalChoiceButtonGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Reads and probes links for the batch-import sheet. */
object PetalLinkImportSource {

    /** Reads the clipboard. Only call from an explicit user tap (Android 12+ shows its own toast). */
    @JvmStatic
    fun readClipboardText(context: Context): String? = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    } catch (_: Throwable) {
        null
    }

    /** Reads a user-picked text file (max 1 MB). */
    @JvmStatic
    fun readTextFile(context: Context, uri: android.net.Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readNBytes(1024 * 1024).toString(Charsets.UTF_8) }
    } catch (_: Throwable) {
        null
    }

    class Probe(val fileName: String?, val sizeBytes: Long)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true).build()
    }
    private val permits = Semaphore(4)

    /** HEAD request off the main thread; any failure just yields an empty probe. */
    suspend fun probe(url: String): Probe = withContext(Dispatchers.IO) {
        permits.withPermit {
            try {
                http.newCall(Request.Builder().url(url).head().build()).execute().use { r ->
                    val size = r.header("Content-Length")?.toLongOrNull() ?: -1L
                    val name = r.header("Content-Disposition")
                        ?.let { Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
                    Probe(name?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }, size)
                }
            } catch (_: Throwable) {
                Probe(null, -1L)
            }
        }
    }
}

/**
 * Batch import sheet: filter, per-link checklist (name + size), Select all, destination info and a
 * primary "Download N" button that enqueues through the download queue.
 *
 * [isPrivate]: private context never probes over the network and never uses external managers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalLinkImportSheet(
    links: List<PetalLinkImport.ImportLink>,
    isPrivate: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val selected = remember(links) { mutableStateListOf<String>().apply { addAll(links.map { it.url }) } }
    var filterIndex by remember { mutableStateOf(0) }
    var smartFolders by remember { mutableStateOf(sp.getBoolean(DownloadPrefs.KEY_SMART_ROUTING, false)) }
    val filter = PetalLinkImport.Filter.values()[filterIndex]
    val visible = remember(links, filter) { PetalLinkImport.filter(links, filter) }
    val selectedVisible = visible.filter { it.url in selected }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("Import links", style = PetalText.sheetTitle)
            Text(
                "${links.size} found" + if (isPrivate) " · private (no previews)" else "",
                style = PetalText.pageSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            PetalChoiceButtonGroup(
                labels = listOf("All", "Files", "Media", "Magnet"),
                selectedIndex = filterIndex,
                onSelectIndex = { filterIndex = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val allSelected = visible.isNotEmpty() && visible.all { it.url in selected }
                Checkbox(checked = allSelected, onCheckedChange = { on ->
                    if (on) visible.forEach { if (it.url !in selected) selected.add(it.url) }
                    else selected.removeAll(visible.map { it.url }.toSet())
                })
                Text("Select all", Modifier.weight(1f), style = PetalText.rowTitle)
                Text("${selectedVisible.size} selected", style = PetalText.badge, color = MaterialTheme.colorScheme.primary)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(visible, key = { it.url }) { link ->
                    val probe by produceState<PetalLinkImportSource.Probe?>(null, link.url, isPrivate) {
                        value = if (isPrivate || link.kind == PetalLinkImport.Kind.MAGNET) null else PetalLinkImportSource.probe(link.url)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = link.url in selected,
                            onCheckedChange = { on -> if (on) selected.add(link.url) else selected.remove(link.url) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = com.petal.browser.ui.components.formatMiddleEllipsisFileName(probe?.fileName ?: link.suggestedName),
                                style = PetalText.listItemTitle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val size = probe?.sizeBytes?.takeIf { it > 0 }?.let { formatBytes(it) }
                            Text(
                                listOfNotNull(link.url.substringAfter("://", link.url).substringBefore('/').take(40), size).joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = PetalText.listItemMeta, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sort into folders by type", style = PetalText.rowTitle)
                    Text(
                        if (smartFolders) "Saves to Downloads/Petal/<type>" else "Saves to Downloads",
                        style = PetalText.rowSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = smartFolders, onCheckedChange = {
                    smartFolders = it
                    sp.edit().putBoolean(DownloadPrefs.KEY_SMART_ROUTING, it).apply() // same switch as Settings
                })
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    modifier = Modifier.weight(1f).height(56.dp),
                    enabled = selectedVisible.isNotEmpty(),
                    onClick = {
                        enqueueImportedLinks(context, selectedVisible, isPrivate)
                        onDismiss()
                    }
                ) { Text("Download ${selectedVisible.size}") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Sends the chosen links through the router (and so the queue). Magnets go to the torrent hand-off. */
fun enqueueImportedLinks(context: Context, links: List<PetalLinkImport.ImportLink>, isPrivate: Boolean) {
    for (link in links) {
        if (link.kind == PetalLinkImport.Kind.MAGNET) {
            val activity = context as? Activity
            // Never hand a private link to another app.
            if (!isPrivate && activity != null) PetalTorrentEngineManager.handleTorrentOrMagnet(activity, link.url, link.suggestedName, null)
            continue
        }
        PetalDownloadRouter.download(
            context = context, url = link.url, fileName = link.suggestedName, isPrivate = isPrivate
        )
    }
}
