package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

@Composable
fun PetalAiActionDialog(
    pageTitle: String,
    onSelectAction: (action: String, setAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var rememberAsDefault by remember { mutableStateOf(false) }

    PetalExpressiveDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isGlassHeader = isLiquidGlassEnabled("sp_liquid_glass_containments")
                    Surface(
                        shape = ExpressiveButtonShape,
                        color = if (isGlassHeader) petalGroupSurfaceColor() else MaterialTheme.colorScheme.primaryContainer,
                        border = null,
                        modifier = Modifier
                            .size(42.dp)
                            .then(if (isGlassHeader) Modifier.liquidGlassChrome(ExpressiveButtonShape, enabled = true, sheenIntensity = 0.5f, pressFx = true) else Modifier)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = if (isGlassHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = ExpressiveButtonShape
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.ui_ai_web_research),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            stringResource(R.string.ui_choose_ai_action_for_webpage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AiActionTile(
                        title = stringResource(R.string.ui_summarise_this_page),
                        subtitle = stringResource(R.string.ui_generate_a_concise_executive_summary),
                        icon = Icons.Rounded.Subject,
                        index = 0,
                        count = 3,
                        onClick = {
                            onSelectAction("SUMMARIZE", rememberAsDefault)
                        }
                    )

                    AiActionTile(
                        title = stringResource(R.string.ui_ask_a_question),
                        subtitle = stringResource(R.string.ui_ask_custom_questions_or_query),
                        icon = Icons.Rounded.HelpOutline,
                        index = 1,
                        count = 3,
                        onClick = {
                            onSelectAction("ASK_QUESTION", rememberAsDefault)
                        }
                    )

                    AiActionTile(
                        title = stringResource(R.string.ui_petal_ai_search),
                        subtitle = stringResource(R.string.ui_real_time_ai_web_search),
                        icon = Icons.Rounded.Search,
                        index = 2,
                        count = 3,
                        onClick = {
                            onSelectAction("AI_SEARCH", rememberAsDefault)
                        }
                    )
                }

                // Remember Choice Checkbox Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { rememberAsDefault = !rememberAsDefault }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.ui_remember_choice_as_default),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Checkbox(
                        checked = rememberAsDefault,
                        onCheckedChange = { rememberAsDefault = it }
                    )
                }
            }
    }
}

@Composable
private fun AiActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    onClick: () -> Unit
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val tileShape = com.petal.browser.ui.containment.petalStackedBlockShape(index, count)
    Surface(
        onClick = onClick,
        shape = tileShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGlass) Modifier.liquidGlassChrome(tileShape, enabled = true, sheenIntensity = 0.50f, pressFx = true) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = ExpressiveButtonShape,
                color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isGlass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                border = null,
                modifier = Modifier
                    .size(40.dp)
                    .then(if (isGlass) Modifier.liquidGlassChrome(ExpressiveButtonShape, enabled = true, sheenIntensity = 0.45f) else Modifier)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
