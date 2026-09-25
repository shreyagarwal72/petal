/*
 * PetalAutoScrollOverlay.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Floating Material 3 Auto-Scroll Reader Pill with speed controls.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PetalAutoScrollOverlay(
    onScrollStep: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(true) }
    var speedLevel by remember { mutableIntStateOf(2) } // 1: Slow, 2: Normal, 3: Fast, 4: Turbo

    val pixelStep = when (speedLevel) {
        1 -> 2
        2 -> 4
        3 -> 8
        else -> 16
    }

    LaunchedEffect(isPlaying, speedLevel) {
        while (isPlaying) {
            delay(16L) // ~60fps smooth scrolling
            onScrollStep(pixelStep)
        }
    }

    Surface(
        modifier = modifier
            .padding(16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { isPlaying = !isPlaying },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            TextButton(
                onClick = {
                    speedLevel = if (speedLevel >= 4) 1 else speedLevel + 1
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = CircleShape
            ) {
                Text(
                    text = "${speedLevel}x",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    isPlaying = false
                    onClose()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Auto-Scroll",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
