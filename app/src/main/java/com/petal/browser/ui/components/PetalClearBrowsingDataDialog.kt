package com.petal.browser.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.unit.BrowsingDataManager
import android.webkit.WebView

@Composable
fun PetalClearBrowsingDataDialog(
    onDismiss: () -> Unit,
    onPerformClear: (cache: Boolean, cookies: Boolean, storage: Boolean, autofill: Boolean, permissions: Boolean) -> Unit
) {
    var clearCache by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearStorage by remember { mutableStateOf(true) }
    var clearAutofill by remember { mutableStateOf(false) }
    var clearPermissions by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Clear Browsing Data",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Select data to remove from this device:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ClearOptionRow(
                    label = "Cached images and files",
                    checked = clearCache,
                    onCheckedChange = { clearCache = it }
                )
                ClearOptionRow(
                    label = "Cookies and site data",
                    checked = clearCookies,
                    onCheckedChange = { clearCookies = it }
                )
                ClearOptionRow(
                    label = "Site databases & WebStorage",
                    checked = clearStorage,
                    onCheckedChange = { clearStorage = it }
                )
                ClearOptionRow(
                    label = "Autofill form data & passwords",
                    checked = clearAutofill,
                    onCheckedChange = { clearAutofill = it }
                )
                ClearOptionRow(
                    label = "Site permissions (Location, etc.)",
                    checked = clearPermissions,
                    onCheckedChange = { clearPermissions = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPerformClear(clearCache, clearCookies, clearStorage, clearAutofill, clearPermissions)
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(
                    text = "Clear data",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    )
}

@Composable
private fun ClearOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
