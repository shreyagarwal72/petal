package com.petal.browser.passwords

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.app.Dialog
import androidx.activity.ComponentActivity
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
import com.petal.browser.ui.kit.IconButton
import com.petal.browser.ui.kit.FilledTonalButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.glass.*

/**
 * PetalAccountPickerSheet
 *
 * Material 3 Expressive bottom sheet for choosing between multiple accounts on a website.
 * Inspired by Google Chrome, Mozilla Firefox, and PixelMusic's tactile M3 design.
 */
object PetalAccountPickerSheet {

    fun show(
        activity: ComponentActivity,
        domain: String,
        accounts: List<PetalCredential>,
        onAccountSelected: (PetalCredential) -> Unit,
        onSuggestPassword: () -> Unit = {},
        onManagePasswords: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        activity.runOnUiThread {
            try {
                val dialog = Dialog(activity, R.style.PetalContextMenuDialogTheme)
                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        val hostSource = activity.findViewById<android.view.View>(com.petal.browser.R.id.main)
                            ?: activity.findViewById(android.R.id.content)
                        CompositionLocalProvider(LocalPetalGlassSource provides hostSource) {
                            com.petal.browser.ui.theme.PetalExpressiveTheme {
                                AccountPickerSheetContent(
                                    domain = domain,
                                    accounts = accounts,
                                    onSelect = { cred ->
                                        onAccountSelected(cred)
                                        dialog.dismiss()
                                    },
                                    onSuggest = {
                                        onSuggestPassword()
                                        dialog.dismiss()
                                    },
                                    onManage = {
                                        onManagePasswords()
                                        dialog.dismiss()
                                    },
                                    onDismiss = {
                                        onDismiss()
                                        dialog.dismiss()
                                    }
                                )
                            }
                        }
                    }
                }
                dialog.setContentView(composeView)
                dialog.window?.let { win ->
                    win.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    win.setGravity(android.view.Gravity.BOTTOM)
                }
                dialog.show()
            } catch (t: Throwable) {
                android.util.Log.w("PetalAccountPickerSheet", "Failed to show account picker sheet", t)
            }
        }
    }
}

@Composable
private fun AccountPickerSheetContent(
    domain: String,
    accounts: List<PetalCredential>,
    onSelect: (PetalCredential) -> Unit,
    onSuggest: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = remember { PetalHapticEngine.getInstance(context) }

    val cleanDomain = remember(domain) {
        domain.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore(':')
    }

    val isGlass = com.petal.browser.ui.containment.isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = rememberGlassSheen(isGlass)
    val sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    PetalGlassContainer(
        modifier = Modifier.fillMaxWidth(),
        enabled = isGlass,
        live = true
    ) {
        Surface(
            shape = sheetShape,
            color = if (isGlass) com.petal.browser.ui.containment.petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = if (isGlass) 0.dp else 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .liquidGlassChrome(shape = sheetShape, enabled = isGlass, sheenIntensity = sheen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(ExpressiveButtonShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Choose account to fill",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = cleanDomain,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Accounts list
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        accounts.forEachIndexed { index, cred ->
                            val itemShape = RoundedCornerShape(14.dp)
                            Surface(
                                shape = itemShape,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(itemShape)
                                    .clickable {
                                        haptic.playClick(context)
                                        onSelect(cred)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Initials badge
                                    Surface(
                                        shape = ExpressiveButtonShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val initial = cred.username.trim().take(1).uppercase().ifBlank { "?" }
                                            Text(
                                                text = initial,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cred.username.ifBlank { "Saved Login" },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "••••••••",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = "Fill",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Tools Row (PixelMusic inspired segmented actions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptic.playClick(context)
                            onSuggest()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Password,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Suggest Password",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            haptic.playClick(context)
                            onManage()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Manage",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
