package com.petal.browser.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.PetalText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.petal.browser.activity.MainActivity
import com.petal.browser.compose.extensions.PetalExtensionsMenuSheet
import com.petal.browser.engine.gecko.PetalEngineBridge
import com.petal.browser.engine.gecko.PetalEngineStore
import com.petal.browser.engine.gecko.PetalTranslationsManager
import com.petal.browser.extensions.PetalExtensionManager
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.lib.state.ext.flow
import org.mozilla.geckoview.GeckoSession

/** Id of the currently selected tab in the engine store (updates on tab switch). */
@Composable
private fun rememberSelectedTabId(context: Context): String? {
    val id by produceState<String?>(
        initialValue = PetalEngineStore.getStore(context).state.selectedTabId,
        context
    ) {
        PetalEngineStore.getStore(context).flow()
            .map { it.selectedTabId }
            .distinctUntilChanged()
            .collect { value = it }
    }
    return id
}

private fun geckoSessionOf(context: Context, tabId: String?): GeckoSession? {
    if (tabId == null) return null
    val tab = PetalEngineStore.getStore(context).state.tabs.firstOrNull { it.id == tabId } ?: return null
    return PetalEngineBridge.getEngineGeckoSession(tab.engineState.engineSession)
}

// ───────────────────────── Extension action chips ─────────────────────────

@Stable
data class ExtensionChipAction(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val badgeText: String?,
    val badgeBackground: Int?,
    val badgeTextColor: Int?,
    val icon: Bitmap?,
    val onClick: () -> Unit
)

@Composable
fun rememberExtensionChipActions(pageUrl: String): List<ExtensionChipAction> {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var enabled by remember { mutableStateOf(sp.getBoolean("sp_address_bar_extension_chips", true)) }
    DisposableEffect(sp) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sp_address_bar_extension_chips") {
                enabled = sp.getBoolean("sp_address_bar_extension_chips", true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val states by PetalExtensionManager.activeTabActionStates.collectAsStateWithLifecycle()
    val installed by PetalExtensionManager.extensions.collectAsStateWithLifecycle()

    if (!enabled || BarCollapseState.isCollapsed) return emptyList()
    val withActions = installed.filter { it.enabled && states.containsKey(it.id) }
    return withActions.mapNotNull { ext ->
        val state = states[ext.id] ?: return@mapNotNull null
        ExtensionChipAction(
            id = ext.id,
            label = state.title ?: ext.name,
            enabled = state.enabled,
            badgeText = state.badgeText,
            badgeBackground = state.badgeBackgroundColor,
            badgeTextColor = state.badgeTextColor,
            icon = state.icon ?: ext.icon,
            onClick = {
                PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                PetalExtensionManager.triggerBrowserAction(ext.id, context)
            }
        )
    }
}

@Composable
fun ExtensionChipItem(
    action: ExtensionChipAction,
    slotWidth: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    ExtensionChip(
        label = action.label,
        enabled = action.enabled,
        badgeText = action.badgeText,
        badgeBackground = action.badgeBackground,
        badgeTextColor = action.badgeTextColor,
        slotWidth = slotWidth,
        icon = action.icon?.let { bmp ->
            {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        onClick = action.onClick,
        modifier = modifier
    )
}

/**
 * Up to two small icon chips for the active tab's extension actions (with badge text/color), plus
 * one "extensions" overflow chip. Hidden when the tab has no actions, when the bar is collapsed or
 * when `sp_address_bar_extension_chips` is off.
 */
@Composable
fun PetalExtensionActionChips(pageUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val actions = rememberExtensionChipActions(pageUrl)
    if (actions.isEmpty()) return

    val shown = if (actions.size > 2) actions.take(1) else actions
    val hasOverflow = actions.size > 2

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { action ->
            ExtensionChipItem(action = action)
        }
        if (hasOverflow) {
            ExtensionChip(
                label = "Extensions",
                enabled = true,
                badgeText = "+${actions.size - shown.size}",
                badgeBackground = null,
                badgeTextColor = null,
                icon = null,
                onClick = {
                    PetalHapticEngine.getInstance(context).thresholdCrossed(context)
                    val activity = context as? MainActivity ?: return@ExtensionChip
                    PetalExtensionsMenuSheet.show(activity, pageUrl) { activity.showExtensionsScreen() }
                }
            )
        }
    }
}

@Composable
private fun ExtensionChip(
    label: String,
    enabled: Boolean,
    badgeText: String?,
    badgeBackground: Int?,
    badgeTextColor: Int?,
    icon: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    slotWidth: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    // 48dp touch height around a 32dp visual chip.
    Box(
        modifier = modifier
            .width(slotWidth)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) icon() else Icon(
                    imageVector = Icons.Rounded.Extension,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (!badgeText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeBackground?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 0.dp)
            ) {
                Text(
                    text = badgeText,
                    style = PetalText.badge,
                    color = badgeTextColor?.let { Color(it) } ?: MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

// ───────────────────────── Translate chip + sheet ─────────────────────────

@Stable
class TranslateChipController(
    val isTranslatable: Boolean,
    val isTranslated: Boolean,
    val openSheet: () -> Unit
)

@Composable
fun rememberTranslateChipController(): TranslateChipController {
    val context = LocalContext.current
    val tabId = rememberSelectedTabId(context)
    val states by PetalTranslationsManager.states.collectAsStateWithLifecycle()
    val state = tabId?.let { states[it] }
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet && tabId != null) {
        PetalTranslateSheet(tabId = tabId, onDismiss = { showSheet = false })
    }

    val openSheet = remember(context) {
        {
            PetalHapticEngine.getInstance(context).thresholdCrossed(context)
            showSheet = true
        }
    }
    return TranslateChipController(
        isTranslatable = state?.isTranslatable == true && tabId != null,
        isTranslated = state?.isTranslated == true,
        openSheet = openSheet
    )
}

@Composable
fun TranslateChipButton(
    isTranslated: Boolean,
    onClick: () -> Unit,
    slotWidth: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(slotWidth)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (isTranslated) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = "Translate page",
                    tint = if (isTranslated) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Translate chip for the address pill; only composes when the active page is translatable. */
@Composable
fun PetalTranslateChip(modifier: Modifier = Modifier) {
    val controller = rememberTranslateChipController()
    if (!controller.isTranslatable) return
    TranslateChipButton(
        isTranslated = controller.isTranslated,
        onClick = controller.openSheet,
        modifier = modifier
    )
}

@Composable
fun PetalTranslateSheet(tabId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val states by PetalTranslationsManager.states.collectAsStateWithLifecycle()
    val supported by PetalTranslationsManager.supported.collectAsStateWithLifecycle()
    val state = states[tabId] ?: PetalTranslationsManager.TabState()
    val from = state.pageLanguage.orEmpty()
    val to = state.targetLanguage
    var pickingTarget by remember { mutableStateOf(false) }
    var always by remember(from) { mutableStateOf(false) }
    var downloadBytes by remember(from, to) { mutableStateOf(0L) }

    LaunchedEffect(Unit) { PetalTranslationsManager.loadSupportedLanguages() }
    LaunchedEffect(from) {
        if (from.isNotEmpty()) PetalTranslationsManager.getAlwaysTranslate(from) { always = it }
    }
    LaunchedEffect(from, to) {
        if (from.isNotEmpty()) PetalTranslationsManager.checkDownloadSize(from, to) { downloadBytes = it }
    }

    fun nameOf(tag: String): String {
        val all = (supported?.fromLanguages.orEmpty()) + (supported?.toLanguages.orEmpty())
        return all.firstOrNull { it.code.equals(tag, ignoreCase = true) }?.localizedDisplayName?.takeIf { it.isNotBlank() }
            ?: java.util.Locale.forLanguageTag(tag).displayLanguage.ifBlank { tag }
    }

    PetalSheet(onDismissRequest = onDismiss, title = "Translate page") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pickingTarget) {
                Text("Translate to", style = PetalText.sheetTitle)
                val targets = supported?.toLanguages.orEmpty().filter { !it.code.equals(from, ignoreCase = true) }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(targets) { lang ->
                        val selected = lang.code.equals(to, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                PetalTranslationsManager.setTarget(tabId, lang.code)
                                pickingTarget = false
                            }
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lang.localizedDisplayName?.takeIf { it.isNotBlank() } ?: lang.code,
                                    style = PetalText.rowTitle,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                return@Column
            }

            // Detected language -> target language
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Page language", style = PetalText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (from.isEmpty()) "Unknown" else nameOf(from), style = PetalText.rowTitle)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = !state.processing) {
                        pickingTarget = true
                    }
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Translate to", style = PetalText.badge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(nameOf(to), style = PetalText.rowTitle, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            if (state.processing) {
                Text(
                    text = if (downloadBytes > 0) "Downloading language pack and translating…" else "Translating…",
                    style = PetalText.rowSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (!state.isTranslated && downloadBytes > 0) {
                Text(
                    text = "One-time download of about ${"%.1f".format(downloadBytes / (1024.0 * 1024.0))} MB. Translation runs on your device.",
                    style = PetalText.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.error != null) {
                Text("Translation failed. Try again.", style = PetalText.cardBody, color = MaterialTheme.colorScheme.error)
            }

            // Primary action: translate / show original
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).clickable(enabled = !state.processing && from.isNotEmpty()) {
                    val session = geckoSessionOf(context, tabId)
                    if (state.isTranslated) PetalTranslationsManager.restoreOriginal(tabId, session)
                    else PetalTranslationsManager.translate(tabId, session, from, to)
                }
            ) {
                Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isTranslated) "Show original" else "Translate",
                        style = PetalText.button,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Always translate this language
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Always translate ${if (from.isEmpty()) "this language" else nameOf(from)}",
                    style = PetalText.rowTitle,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = always,
                    enabled = from.isNotEmpty(),
                    onCheckedChange = {
                        always = it
                        PetalTranslationsManager.setAlwaysTranslate(from, it)
                    }
                )
            }
        }
    }
}
