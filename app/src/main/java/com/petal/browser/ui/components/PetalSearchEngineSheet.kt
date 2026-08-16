/*
 * PetalSearchEngineSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Search Engine Selection Modal Sheet for Petal Browser.
 * Zero-lag single BottomSheetDialog with clear radio selections and explicit Confirm action.
 */

package com.petal.browser.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.ui.theme.PetalExpressiveTheme

data class SearchEngineItem(
    val index: Int,
    val name: String,
    val description: String,
    val url: String
)

val availableSearchEngines = listOf(
    SearchEngineItem(0, "Google", "Fast and comprehensive global search", "https://google.com"),
    SearchEngineItem(1, "DuckDuckGo", "Privacy search without tracking", "https://duckduckgo.com"),
    SearchEngineItem(2, "Brave Search", "Independent, privacy-focused search index", "https://search.brave.com"),
    SearchEngineItem(3, "Bing", "Microsoft intelligent search & discovery", "https://bing.com"),
    SearchEngineItem(4, "Ecosia", "Search engine that plants trees", "https://ecosia.org")
)

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
                                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                                sp.edit()
                                    .putString("sp_search_engine", index.toString())
                                    .putBoolean("sp_search_engine_chosen", true)
                                    .apply()
                                try { dialog.dismiss() } catch (ignored: Exception) {}
                                onDismiss?.run()
                            },
                            onCancel = {
                                try { dialog.dismiss() } catch (ignored: Exception) {}
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

@Composable
fun PetalSearchEngineSheetContent(
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var selectedIndex by remember {
        val current = sp.getString("sp_search_engine", "0") ?: "0"
        mutableIntStateOf(current.toIntOrNull() ?: 0)
    }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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

            // Header Title
            Row(
                modifier = Modifier.entrance(index = 0),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PetalSearchLottie(
                    modifier = Modifier
                        .size(44.dp)
                        .popIn()
                )
                Column {
                    Text(
                        text = "Choose Default Search Engine",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Selected engine will handle omnibox & query searches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Search Engines Cards List
            Column(
                modifier = Modifier.entrance(index = 1),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                availableSearchEngines.forEach { engine ->
                    val isSelected = engine.index == selectedIndex
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = bgColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .bouncyClickable(scaleDown = 0.97f, onClick = { selectedIndex = engine.index })
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = engine.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = engine.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedIndex = engine.index
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Confirm Button
            Button(
                onClick = { onConfirm(selectedIndex) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .entrance(index = 2)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Confirm Search Engine",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
