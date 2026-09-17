/*
 * PetalDevConsoleSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive In-App Developer Console & JavaScript REPL.
 * Allows web designers and developers to execute snippets, inspect the DOM,
 * and test scripts directly on mobile without desktop USB tethering.
 *
 * MIT License — Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ConsoleMessage(
    val id: Long = System.nanoTime(),
    val type: LogType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class LogType {
        INPUT, RESULT, ERROR, INFO
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalDevConsoleSheet(
    onExecuteScript: (String, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ConsoleMessage>() }
    val focusManager = LocalFocusManager.current

    val presetSnippets = listOf(
        "document.title" to "Title",
        "location.href" to "URL",
        "document.cookie" to "Cookies",
        "navigator.userAgent" to "UserAgent",
        "document.querySelectorAll('a').length" to "Links Count",
        "document.querySelectorAll('img').length" to "Images Count",
        "document.body.style.filter = 'invert(1)'" to "Invert Colors"
    )

    fun runScript(code: String) {
        if (code.isBlank()) return
        messages.add(ConsoleMessage(type = ConsoleMessage.LogType.INPUT, text = "> $code"))
        onExecuteScript(code) { result ->
            if (result != null) {
                if (result.startsWith("ERROR:")) {
                    messages.add(ConsoleMessage(type = ConsoleMessage.LogType.ERROR, text = result))
                } else {
                    messages.add(ConsoleMessage(type = ConsoleMessage.LogType.RESULT, text = result))
                }
            } else {
                messages.add(ConsoleMessage(type = ConsoleMessage.LogType.INFO, text = "Evaluated on page"))
            }
        }
        inputText = ""
        focusManager.clearFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            "Developer Console",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "JavaScript REPL & Page Inspector",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (messages.isNotEmpty()) {
                    IconButton(onClick = { messages.clear() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quick Preset Snippet Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetSnippets.forEach { (snippet, label) ->
                    SuggestionChip(
                        onClick = { inputText = snippet },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Log Console Output Box
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Type JavaScript expression or tap preset above",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val color = when (msg.type) {
                                ConsoleMessage.LogType.INPUT -> MaterialTheme.colorScheme.primary
                                ConsoleMessage.LogType.RESULT -> MaterialTheme.colorScheme.secondary
                                ConsoleMessage.LogType.ERROR -> MaterialTheme.colorScheme.error
                                ConsoleMessage.LogType.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = msg.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = color,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Input bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("console.log(...) or JS snippet", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { runScript(inputText) })
                )

                IconButton(
                    onClick = { runScript(inputText) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Run", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
