package com.petal.browser.compose.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalSlider
import kotlinx.coroutines.delay

fun isSettingHighlightMatch(cardId: String?, targetHighlightId: String?): Boolean {
    if (cardId.isNullOrBlank() || targetHighlightId.isNullOrBlank()) return false
    val c = cardId.trim().lowercase()
    val t = targetHighlightId.trim().lowercase()
    return c == t || t.startsWith("${c}_") || c.startsWith("${t}_") || c.contains(t) || t.contains(c)
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.highlightableSetting(
    cardId: String?,
    targetHighlightId: String?,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp)
): Modifier = composed {
    val isMatched = remember(cardId, targetHighlightId) {
        isSettingHighlightMatch(cardId, targetHighlightId)
    }
    var isHighlighted by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(cardId, targetHighlightId) {
        if (isMatched) {
            isHighlighted = true
            try {
                bringIntoViewRequester.bringIntoView()
            } catch (_: Exception) {}
            delay(1000L)
            isHighlighted = false
        }
    }

    val borderWidth by animateDpAsState(
        targetValue = if (isHighlighted) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "highlightBorder"
    )

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .then(
            if (borderWidth > 0.dp) {
                Modifier.border(borderWidth, MaterialTheme.colorScheme.primary, shape)
            } else Modifier
        )
}

/**
 * Unified Contained Settings Category Card matching main hub specification:
 * Outer Card with surfaceVariant 70% alpha, 24dp corners, 0dp elevation.
 * Integrated header featuring 48.dp primary-colored badge with 12.dp rounded corners,
 * onPrimary icon tint, SemiBold titleMedium typography, and horizontal divider.
 * Supports dynamic 1-second visual highlight and auto-scroll when targeted from settings search.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCategoryCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    cardId: String? = null,
    targetHighlightId: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isMatched = remember(cardId, targetHighlightId) {
        isSettingHighlightMatch(cardId, targetHighlightId)
    }
    var isHighlighted by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(cardId, targetHighlightId) {
        if (isMatched) {
            isHighlighted = true
            try {
                bringIntoViewRequester.bringIntoView()
            } catch (_: Exception) {}
            delay(1000L) // Highlight for 1 second
            isHighlighted = false
        }
    }

    val borderWidth by animateDpAsState(
        targetValue = if (isHighlighted) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "highlightBorder"
    )
    val highlightBorder = if (borderWidth > 0.dp) {
        BorderStroke(borderWidth, MaterialTheme.colorScheme.primary)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .animateContentSize(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = highlightBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 4.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card Header matching main settings page icon badge style
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (iconRes != null || icon != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconRes != null) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (checked && enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked && enabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconSwitch(
                checked = checked,
                icon = icon,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
fun PetalVariableSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    String.format(java.util.Locale.getDefault(), "%.0f", value),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            PetalSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
