/*
 * PetalHistoryScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Chrome Android-inspired Material 3 Expressive History Page for Petal Browser.
 * Features live search/filter, grouped dates, individual entry deletion,
 * clear browsing data action, and 60fps smooth animations.
 */

package com.petal.browser.compose.history

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.SubcomposeAsyncImage
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.database.Record
import com.petal.browser.database.RecordAction
import com.petal.browser.unit.RecordUnit
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.compose.composable.ContainedLoadingIndicator
import com.petal.browser.ui.components.entrance
import com.petal.browser.ui.components.M3ExpressiveVariableBackground
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.PetalExpressiveTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun interface HistoryUrlHandler {
    fun open(url: String)
}

fun interface HistoryActionHandler {
    fun action()
}

object PetalHistoryBridge {
    @JvmStatic
    @JvmOverloads
    fun showHistory(
        activity: ComponentActivity,
        onOpenUrl: HistoryUrlHandler,
        onClearBrowsingData: HistoryActionHandler,
        onDismiss: Runnable? = null
    ) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.setOnShowListener {
                try {
                    val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
                    bottomSheet?.let { sheet ->
                        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                        behavior.skipCollapsed = true
                        behavior.isDraggable = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            dialog.setOnDismissListener {
                onDismiss?.run()
            }

            // BottomSheetDialog is a plain Dialog with its own Window, so it doesn't
            // automatically inherit the Activity's OnBackPressedDispatcher the way views
            // added directly into the Activity's own hierarchy (Settings/Downloads/Account)
            // do. Without this, PredictiveBackHandler inside PetalHistoryScreen would have
            // no dispatcher to attach to. This wires a small dispatcher of its own - backed
            // by the dialog's own window on API 33+ for real predictive-back gesture events,
            // falling back to a plain dismiss() otherwise - and hands it to the ComposeView
            // via the standard ViewTree owner mechanism.
            val historyBackDispatcher = androidx.activity.OnBackPressedDispatcher {
                try { dialog.dismiss() } catch (ignored: Exception) {}
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                try {
                    historyBackDispatcher.setOnBackInvokedDispatcher(dialog.window!!.onBackInvokedDispatcher)
                } catch (ignored: Exception) {
                }
            }
            val historyBackDispatcherOwner = object : androidx.activity.OnBackPressedDispatcherOwner {
                override val lifecycle: androidx.lifecycle.Lifecycle
                    get() = activity.lifecycle
                override val onBackPressedDispatcher: androidx.activity.OnBackPressedDispatcher
                    get() = historyBackDispatcher
            }

            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeOnBackPressedDispatcherOwner(historyBackDispatcherOwner)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val fontName = sp.getString("sp_app_font", "GS_FLEX") ?: "GS_FLEX"
                val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                val isAmoled = sp.getBoolean("sp_amoled", false)

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.GS_FLEX }
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    dynamicColor = dynamicColor,
                    useAmoled = isAmoled,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    paletteId = paletteId
                ) {
                        PetalHistoryScreen(
                            onOpenUrl = { url ->
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                onOpenUrl.open(url)
                            },
                            onClearBrowsingData = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                onClearBrowsingData.action()
                            },
                            onDismiss = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                            }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PetalHistoryScreen(
    onOpenUrl: (String) -> Unit,
    onClearBrowsingData: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Load history records from SQLite database asynchronously
    var rawHistory by remember { mutableStateOf<List<Record>?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            val action = RecordAction(context)
            action.open(false)
            val list = action.listHistory(context)
            action.close()
            rawHistory = list.reversed().filter { record ->
                val url = record.url?.trim() ?: ""
                url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true) && !url.startsWith("about:", ignoreCase = true)
            }
        } catch (e: Exception) {
            rawHistory = emptyList()
        }
    }

    val filteredHistory = remember(searchQuery, rawHistory) {
        val historyList = rawHistory ?: emptyList()
        if (searchQuery.isBlank()) {
            historyList
        } else {
            val query = searchQuery.trim().lowercase()
            historyList.filter { record ->
                (record.title?.lowercase()?.contains(query) == true) ||
                (record.url?.lowercase()?.contains(query) == true)
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All History?") },
            text = { Text("This will permanently remove all visited web pages from your history records.") },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val action = RecordAction(context)
                            action.open(true)
                            action.clearTable(RecordUnit.TABLE_HISTORY)
                            action.close()
                            rawHistory = emptyList()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Same predictive-back wiring as Settings/Downloads/Account - see the dispatcher set up
    // in PetalHistoryBridge.showHistory above, which is what makes this usable at all inside
    // a BottomSheetDialog's own window.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backIsLeftEdge by remember { mutableStateOf(true) }
    val animatedBackProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = backProgress,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "HistoryBackProgress"
    )
    val historySp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    androidx.activity.compose.PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress = backEvent.progress
                backIsLeftEdge = backEvent.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT
            }
            backProgress = 0f
            onDismiss()
        } catch (e: Exception) {
            // gesture cancelled - stay on the history screen
            backProgress = 0f
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) { innerPadding ->
        // Reflects the selected Predictive Back Animation style, with the browser page this
        // was opened from peeking in from behind - same InstallerX-Revived-style choreography
        // as Settings/Downloads/Account.
        val animation = remember(historySp) {
            com.petal.browser.animation.predictiveback.PredictiveBackAnimation.fromValueOrDefault(
                historySp.getString("sp_predictive_back_anim", com.petal.browser.animation.predictiveback.PredictiveBackAnimation.CLASSIC.value)
                    ?: com.petal.browser.animation.predictiveback.PredictiveBackAnimation.CLASSIC.value
            )
        }
        val exitDirection = remember(historySp) {
            com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.fromValueOrDefault(
                historySp.getString("sp_predictive_back_exit_dir", com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.ALWAYS_RIGHT.value)
                    ?: com.petal.browser.animation.predictiveback.PredictiveBackExitDirection.ALWAYS_RIGHT.value
            )
        }
        val backFrame = com.petal.browser.animation.predictiveback.PredictiveBackStyle.frameFor(
            animation = animation,
            exitDirection = exitDirection,
            progress = animatedBackProgress,
            isLeftEdge = backIsLeftEdge,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            M3ExpressiveVariableBackground(pageSeed = "history_page")

            val previewBitmap = remember(animatedBackProgress > 0f) {
                com.petal.browser.animation.predictiveback.PagePreviewCache.get(
                    com.petal.browser.animation.predictiveback.PagePreviewCache.KEY_BROWSER_MAIN
                )
            }
            if (previewBitmap != null && animatedBackProgress > 0.001f) {
                val underlay = com.petal.browser.animation.predictiveback.PredictiveBackStyle.underlayFrameFor(
                    animation = animation,
                    exitDirection = exitDirection,
                    progress = animatedBackProgress,
                    isLeftEdge = backIsLeftEdge,
                )
                androidx.compose.foundation.Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = underlay.scale
                            scaleY = underlay.scale
                            alpha = underlay.alpha
                            translationX = underlay.translationXDp.dp.toPx()
                            clip = underlay.cornerRadiusDp > 0.01f
                            shape = RoundedCornerShape(underlay.cornerRadiusDp.dp)
                        }
                )
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backFrame.scale
                    scaleY = backFrame.scale
                    alpha = backFrame.alpha
                    translationX = backFrame.translationXDp.dp.toPx()
                    clip = animatedBackProgress > 0.01f
                    shape = RoundedCornerShape(backFrame.cornerRadiusDp.dp)
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Text(
                            "History",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        if (rawHistory?.isNotEmpty() == true) {
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = "Clear History",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    placeholder = { Text("Search history...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
            if (rawHistory == null) {
                ContainedLoadingIndicator(
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item(key = "clear_banner") {
                        Surface(
                            onClick = onClearBrowsingData,
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CleaningServices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Clear browsing data...",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (filteredHistory.isEmpty()) {
                        item(key = "empty_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Rounded.HistoryToggleOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No matching history" else "No browsing history yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(filteredHistory, key = { idx, item -> "${item.url}_$idx" }) { index, record ->
                            HistoryCardItem(
                                record = record,
                                index = index,
                                onSelect = { record.url?.let(onOpenUrl) },
                                onDelete = {
                                    try {
                                        val action = RecordAction(context)
                                        action.open(true)
                                        action.deleteURL(record.url, RecordUnit.TABLE_HISTORY)
                                        action.close()
                                        rawHistory = rawHistory?.filter { it.url != record.url } ?: emptyList()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                    }
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
private fun HistoryCardItem(
    record: Record,
    index: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatted = remember(record.time) {
        if (record.time > 0L) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(record.time))
        } else ""
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(scaleDown = 0.95f, onClick = onSelect)
            .entrance(index = index)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val faviconUrl = remember(record.url) {
                val domain = record.domain?.takeIf { it.isNotBlank() }
                    ?: try { java.net.URI(record.url ?: "").host } catch (e: Exception) { null }
                if (!domain.isNullOrEmpty()) "https://www.google.com/s2/favicons?domain=$domain&sz=32" else null
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (faviconUrl != null) {
                        SubcomposeAsyncImage(
                            model = faviconUrl,
                            contentDescription = "Website Icon",
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            loading = {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            error = {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title?.takeIf { it.isNotBlank() } ?: record.domain ?: "Web Page",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.url ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (timeFormatted.isNotEmpty()) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
