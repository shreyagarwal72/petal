package com.petal.browser.ui.components;

import androidx.compose.animation.core.Spring;
import androidx.compose.animation.core.animateFloatAsState;
import androidx.compose.animation.core.spring;
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * ExpressiveFabWheel is a radial floating action menu for quick browser shortcuts.
 */
@Composable
fun ExpressiveFabWheel(
    onNewTab: () -> Unit,
    onBookmark: () -> Unit,
    onVoiceSearch: () -> Unit,
    onReaderMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fabRotation"
    )

    val fanScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fanScale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        if (isExpanded || fanScale > 0.01f) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(bottom = 70.dp, end = 6.dp)
                    .graphicsLayer {
                        scaleX = fanScale
                        scaleY = fanScale
                        alpha = fanScale
                    }
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        isExpanded = false
                        onNewTab()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New Tab")
                }

                SmallFloatingActionButton(
                    onClick = {
                        isExpanded = false
                        onBookmark()
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Rounded.BookmarkBorder, contentDescription = "Bookmark")
                }

                SmallFloatingActionButton(
                    onClick = {
                        isExpanded = false
                        onVoiceSearch()
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice Search")
                }

                SmallFloatingActionButton(
                    onClick = {
                        isExpanded = false
                        onReaderMode()
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Icon(Icons.Rounded.Article, contentDescription = "Reader Mode")
                }
            }
        }

        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Expand Menu",
                modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }
            )
        }
    }
}
