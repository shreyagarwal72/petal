package com.petal.browser.tools

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalGroupListRow
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.petalGroupPositionFor

/**
 * StorageItem
 * ─────────────────────────────────────────────────────────────────────────
 * Represents an inspected Cookie or WebStorage key-value item.
 */
data class StorageItem(
    val key: String,
    val value: String,
    val domain: String = "",
    val path: String = "/",
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false
)

enum class StorageType(val label: String) {
    COOKIES("Cookies"),
    LOCAL_STORAGE("localStorage"),
    SESSION_STORAGE("sessionStorage")
}

/**
 * PetalStorageInspectorSheet
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Site Data & Cookie Inspector.
 * Lets users inspect, copy, filter, and purge cookies and local storage
 * per site directly from mobile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalStorageInspectorSheet(
    domain: String,
    onExecuteJavascript: (String, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedType by remember { mutableStateOf(StorageType.COOKIES) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    val cookieItems = remember { mutableStateListOf<StorageItem>() }
    val localStorageItems = remember { mutableStateListOf<StorageItem>() }
    val sessionStorageItems = remember { mutableStateListOf<StorageItem>() }

    fun refreshData() {
        isLoading = true
        cookieItems.clear()
        localStorageItems.clear()
        sessionStorageItems.clear()

        // 1. Fetch document.cookie
        onExecuteJavascript("document.cookie") { result ->
            val unquoted = result?.trim('"', '\'') ?: ""
            if (unquoted.isNotBlank() && unquoted != "null") {
                val pairs = unquoted.split(';')
                pairs.forEach { pair ->
                    val eq = pair.indexOf('=')
                    if (eq > 0) {
                        val k = pair.substring(0, eq).trim()
                        val v = pair.substring(eq + 1).trim()
                        cookieItems.add(StorageItem(key = k, value = v, domain = domain))
                    }
                }
            }
        }

        // 2. Fetch localStorage
        onExecuteJavascript("JSON.stringify(Object.entries(localStorage));") { result ->
            try {
                val unquoted = result?.trim('"', '\'')?.replace("\\\"", "\"") ?: "[]"
                val jsonArr = org.json.JSONArray(unquoted)
                for (i in 0 until jsonArr.length()) {
                    val entry = jsonArr.getJSONArray(i)
                    localStorageItems.add(StorageItem(key = entry.getString(0), value = entry.getString(1)))
                }
            } catch (_: Exception) {}
        }

        // 3. Fetch sessionStorage
        onExecuteJavascript("JSON.stringify(Object.entries(sessionStorage));") { result ->
            try {
                val unquoted = result?.trim('"', '\'')?.replace("\\\"", "\"") ?: "[]"
                val jsonArr = org.json.JSONArray(unquoted)
                for (i in 0 until jsonArr.length()) {
                    val entry = jsonArr.getJSONArray(i)
                    sessionStorageItems.add(StorageItem(key = entry.getString(0), value = entry.getString(1)))
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val activeList = when (selectedType) {
        StorageType.COOKIES -> cookieItems
        StorageType.LOCAL_STORAGE -> localStorageItems
        StorageType.SESSION_STORAGE -> sessionStorageItems
    }

    val filteredList = remember(activeList.size, searchQuery, selectedType) {
        if (searchQuery.isBlank()) activeList else activeList.filter {
            it.key.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true)
        }
    }

    PetalSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Site Storage & Cookies",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                        refreshData()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }

            Spacer(Modifier.height(14.dp))

            // Storage Tabs (Cookies, localStorage, sessionStorage)
            PrimaryTabRow(
                selectedTabIndex = selectedType.ordinal,
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            ) {
                StorageType.values().forEach { type ->
                    Tab(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        text = {
                            val count = when (type) {
                                StorageType.COOKIES -> cookieItems.size
                                StorageType.LOCAL_STORAGE -> localStorageItems.size
                                StorageType.SESSION_STORAGE -> sessionStorageItems.size
                            }
                            Text("${type.label} ($count)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Filter Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter ${selectedType.label}…") },
                leadingIcon = { Icon(Icons.Rounded.FilterList, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Clear, null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // Storage items list
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoading) "Reading storage data…" else "No entries found in ${selectedType.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredList, key = { _, item -> "${selectedType.name}_${item.key}" }) { index, item ->
                        PetalGroupListRow(
                            position = petalGroupPositionFor(index, filteredList.size),
                            onClick = {
                                clipboardManager.setText(AnnotatedString("${item.key}=${item.value}"))
                                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.4f)
                            },
                            leading = {
                                Box(
                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (selectedType == StorageType.COOKIES) Icons.Rounded.Cookie else Icons.Rounded.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            content = {
                                Text(item.key, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text(item.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                            },
                            trailing = {
                                IconButton(
                                    onClick = {
                                        // Purge item
                                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                                        when (selectedType) {
                                            StorageType.COOKIES -> {
                                                onExecuteJavascript("document.cookie = '${item.key}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';") {
                                                    refreshData()
                                                }
                                            }
                                            StorageType.LOCAL_STORAGE -> {
                                                onExecuteJavascript("localStorage.removeItem('${item.key}');") {
                                                    refreshData()
                                                }
                                            }
                                            StorageType.SESSION_STORAGE -> {
                                                onExecuteJavascript("sessionStorage.removeItem('${item.key}');") {
                                                    refreshData()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Purge All For Site Button
            OutlinedButton(
                onClick = {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 0.8f)
                    onExecuteJavascript("""
                        (function() {
                            localStorage.clear();
                            sessionStorage.clear();
                            var cookies = document.cookie.split(";");
                            for (var i = 0; i < cookies.length; i++) {
                                var cookie = cookies[i];
                                var eqPos = cookie.indexOf("=");
                                var name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;
                                document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/";
                            }
                        })();
                    """.trimIndent()) {
                        refreshData()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear All Site Data (${domain})")
            }
        }
    }
}
