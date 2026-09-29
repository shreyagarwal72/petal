package com.petal.browser.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupIconBadge
import com.petal.browser.ui.containment.PetalGroupListRow
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

data class SearchEngineItem(
    val index: Int,
    val name: String,
    val description: String,
    val url: String,
    val custom: Boolean = false
)

val availableSearchEngines = listOf(
    SearchEngineItem(0, "Google", "Fast and comprehensive global search", "https://www.google.com/search?q=%s"),
    SearchEngineItem(1, "DuckDuckGo", "Privacy search without tracking", "https://duckduckgo.com/?q=%s"),
    SearchEngineItem(2, "Startpage", "Privacy-focused Google results", "https://www.startpage.com/sp/search?query=%s"),
    SearchEngineItem(3, "Brave Search", "Independent privacy-focused search index", "https://search.brave.com/search?q=%s"),
    SearchEngineItem(4, "Bing", "Microsoft search and discovery", "https://www.bing.com/search?q=%s"),
    SearchEngineItem(5, "SearXNG", "Open-source metasearch engine", "https://searx.space/search?q=%s"),
    SearchEngineItem(6, "Qwant", "Privacy-oriented European search", "https://www.qwant.com/?q=%s"),
    SearchEngineItem(7, "Ecosia", "Search engine that supports climate action", "https://www.ecosia.org/search?q=%s")
)

private const val CUSTOM_ENGINES_KEY = "sp_custom_search_engines"
private const val CUSTOM_ID_START = 1000

fun getCustomSearchEngines(context: Context): List<SearchEngineItem> {
    val raw = PreferenceManager.getDefaultSharedPreferences(context).getString(CUSTOM_ENGINES_KEY, "[]") ?: "[]"
    return try {
        val json = JSONArray(raw)
        buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val id = item.optInt("id", CUSTOM_ID_START + i)
                val name = item.optString("name").trim()
                val url = item.optString("url").trim()
                if (name.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    add(SearchEngineItem(id, name, "Custom search engine", url, true))
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveCustomSearchEngines(context: Context, engines: List<SearchEngineItem>) {
    val json = JSONArray()
    engines.forEach {
        json.put(JSONObject().apply {
            put("id", it.index)
            put("name", it.name)
            put("url", it.url)
        })
    }
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(CUSTOM_ENGINES_KEY, json.toString()).apply()
}

fun allSearchEngines(context: Context): List<SearchEngineItem> =
    availableSearchEngines + getCustomSearchEngines(context)

object PetalSearchEngineBridge {
    @JvmStatic
    fun showSearchEngineDialog(activity: ComponentActivity, onDismiss: Runnable? = null) {
        try {
            val dialog = BottomSheetDialog(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    PetalExpressiveTheme {
                        PetalSearchEngineSheetContent(
                            onConfirm = { index ->
                                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                                    .putString("sp_search_engine", index.toString())
                                    .putBoolean("sp_search_engine_chosen", true)
                                    .putBoolean("searchEngineSwitch", false)
                                    .apply()
                                dialog.dismiss()
                                onDismiss?.run()
                            },
                            onCancel = {
                                dialog.dismiss()
                                onDismiss?.run()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSearchEngineSheetContent(
    initialIndex: Int? = null,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var selectedIndex by remember(initialIndex) {
        mutableIntStateOf(initialIndex ?: sp.getString("sp_search_engine", "0")?.toIntOrNull() ?: 0)
    }
    var customEngines by remember { mutableStateOf(getCustomSearchEngines(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEngine by remember { mutableStateOf<SearchEngineItem?>(null) }

    if (showAddDialog || editingEngine != null) {
        val editing = editingEngine
        var name by remember(editing?.index) { mutableStateOf(editing?.name ?: "") }
        var url by remember(editing?.index) { mutableStateOf(editing?.url ?: "") }
        var error by remember(editing?.index) { mutableStateOf<String?>(null) }

        com.petal.browser.ui.containment.PetalMaterialAlertDialog(
            onDismissRequest = { showAddDialog = false; editingEngine = null },
            title = { Text(if (editing == null) "Add Search Engine" else "Edit Search Engine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_name)) }, singleLine = true)
                    OutlinedTextField(
                        url, { url = it },
                        label = { Text(stringResource(R.string.ui_search_url)) },
                        supportingText = { Text(stringResource(R.string.ui_use_s_or_searchterms_where)) },
                        singleLine = true
                    )
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = url.trim()
                    val validScheme = normalized.startsWith("https://") || normalized.startsWith("http://")
                    val hasToken = normalized.contains("%s") || normalized.contains("{searchTerms}")
                    if (name.trim().isEmpty()) error = "Enter a name."
                    else if (!validScheme) error = "Search URL must start with http:// or https://."
                    else if (!hasToken) error = "Add %s or {searchTerms} to the URL."
                    else {
                        val id = editing?.index ?: ((customEngines.maxOfOrNull { it.index } ?: (CUSTOM_ID_START - 1)) + 1)
                        val item = SearchEngineItem(id, name.trim(), "Custom search engine", normalized, true)
                        customEngines = if (editing == null) customEngines + item else customEngines.map { if (it.index == id) item else it }
                        saveCustomSearchEngines(context, customEngines)
                        selectedIndex = id
                        showAddDialog = false
                        editingEngine = null
                    }
                }) { Text(if (editing == null) "Add" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; editingEngine = null }) { Text(stringResource(R.string.ui_cancel)) }
            }
        )
    }

    val engines = availableSearchEngines + customEngines
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PetalGroupIconBadge(Icons.Rounded.Search, size = 48.dp)
            Column {
                Text(stringResource(R.string.ui_choose_default_search_engine), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(stringResource(R.string.ui_built_in_and_unlimited_custom), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        PetalGroup(rowCount = engines.size, modifier = Modifier.fillMaxWidth()) { index, position ->
            val engine = engines[index]
            val isSelected = engine.index == selectedIndex
            PetalGroupListRow(
                position = position,
                selected = isSelected,
                onClick = { selectedIndex = engine.index },
                leading = { PetalGroupIconBadge(if (engine.custom) Icons.Rounded.Tune else Icons.Rounded.Search, size = 40.dp, iconSize = 20.dp) },
                content = {
                    Text(engine.name, fontWeight = FontWeight.SemiBold)
                    Text(engine.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (engine.custom) {
                    IconButton(onClick = { editingEngine = engine }) { Icon(Icons.Rounded.Edit, "Edit") }
                    IconButton(onClick = {
                        customEngines = customEngines.filterNot { it.index == engine.index }
                        saveCustomSearchEngines(context, customEngines)
                        if (selectedIndex == engine.index) selectedIndex = 0
                    }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                        }
                        RadioButton(selected = isSelected, onClick = { selectedIndex = engine.index })
                    }
                },
            )
        }

        OutlinedButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ui_add_custom_search_engine))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.ui_cancel)) }
            Button(onClick = { onConfirm(selectedIndex) }) { Text(stringResource(R.string.ui_use_selected_engine)) }
        }
    }
}
