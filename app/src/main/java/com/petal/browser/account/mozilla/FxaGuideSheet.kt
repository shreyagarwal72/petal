/*
 * FxaGuideSheet.kt
 * ─────────────────────────────────────────────────────────────────────────
 * "How does this work?" guide for people who have never used a Mozilla account.
 * Opened from the (i) icon on the sync card, and shown once automatically on first visit.
 */
package com.petal.browser.account.mozilla

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.vector.ImageVector
import com.petal.browser.R
import com.petal.browser.ui.containment.PetalSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxaGuideSheet(onDismiss: () -> Unit) {
    PetalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.fxa_guide_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                stringResource(R.string.fxa_guide_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel(stringResource(R.string.fxa_guide_steps_label))
            Step(1, stringResource(R.string.fxa_guide_step1_title), stringResource(R.string.fxa_guide_step1_body))
            Step(2, stringResource(R.string.fxa_guide_step2_title), stringResource(R.string.fxa_guide_step2_body))
            Step(3, stringResource(R.string.fxa_guide_step3_title), stringResource(R.string.fxa_guide_step3_body))

            SectionLabel(stringResource(R.string.fxa_guide_what_label))
            Feature(Icons.Rounded.Bookmarks, stringResource(R.string.fxa_guide_what_bookmarks))
            Feature(Icons.Rounded.History, stringResource(R.string.fxa_guide_what_history))
            Feature(Icons.Rounded.Tab, stringResource(R.string.fxa_guide_what_tabs))
            Feature(Icons.Rounded.Lock, stringResource(R.string.fxa_guide_what_private))

            SectionLabel(stringResource(R.string.fxa_guide_qr_label))
            Text(
                stringResource(R.string.fxa_guide_qr_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.fxa_guide_got_it), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
