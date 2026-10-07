package com.petal.browser.split

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.compose.tabs.PetalTabItem
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalContainmentShapes

/**
 * SplitOrientation
 * ─────────────────────────────────────────────────────────────────────────
 * Split Screen layout modes: Horizontal (Side-by-Side) or Vertical (Top-Bottom).
 */
enum class SplitOrientation {
    VERTICAL,   // Top & Bottom (best for portrait phones)
    HORIZONTAL  // Left & Right (best for foldables & landscape tablets)
}

/**
 * PetalSplitScreenContainer
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Dual-Tab Split Screen Controller:
 * Displays two interactive tabs concurrently with a smooth draggable divider,
 * orientation switcher, independent navigation headers, and link handoff.
 */
@Composable
fun PetalSplitScreenContainer(
    primaryTab: PetalTabItem,
    secondaryTab: PetalTabItem,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
    onCloseSplit: () -> Unit,
    onSwapTabs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var orientation by remember(isLandscape) {
        mutableStateOf(if (isLandscape) SplitOrientation.HORIZONTAL else SplitOrientation.VERTICAL)
    }

    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    val animatedSplitRatio by animateFloatAsState(targetValue = splitRatio, label = "splitRatio")

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight
        val totalHeightPx = this.constraints.maxHeight.toFloat()
        val totalWidthPx = this.constraints.maxWidth.toFloat()

        if (orientation == SplitOrientation.VERTICAL) {
            // Top / Bottom layout
            val topHeight = totalHeight * animatedSplitRatio
            val bottomHeight = totalHeight * (1f - animatedSplitRatio)

            Column(modifier = Modifier.fillMaxSize()) {
                // Primary Tab Pane (Top)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topHeight)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        SplitPaneHeader(
                            tab = primaryTab,
                            isPrimary = true,
                            onClose = onCloseSplit,
                            onSwap = onSwapTabs,
                            onToggleOrientation = {
                                orientation = if (orientation == SplitOrientation.VERTICAL) SplitOrientation.HORIZONTAL else SplitOrientation.VERTICAL
                            }
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            primaryContent()
                        }
                    }
                }

                // Split Divider Handle
                SplitDividerHandle(
                    orientation = SplitOrientation.VERTICAL,
                    onDragDelta = { deltaPx ->
                        if (totalHeightPx > 0f) {
                            splitRatio = (splitRatio + deltaPx / totalHeightPx).coerceIn(0.25f, 0.75f)
                        }
                    },
                    onReset = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        splitRatio = 0.5f
                    }
                )

                // Secondary Tab Pane (Bottom)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomHeight)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        SplitPaneHeader(
                            tab = secondaryTab,
                            isPrimary = false,
                            onClose = onCloseSplit,
                            onSwap = onSwapTabs
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            secondaryContent()
                        }
                    }
                }
            }
        } else {
            // Left / Right layout
            val leftWidth = totalWidth * animatedSplitRatio
            val rightWidth = totalWidth * (1f - animatedSplitRatio)

            Row(modifier = Modifier.fillMaxSize()) {
                // Primary Tab Pane (Left)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(leftWidth)
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        SplitPaneHeader(
                            tab = primaryTab,
                            isPrimary = true,
                            onClose = onCloseSplit,
                            onSwap = onSwapTabs,
                            onToggleOrientation = {
                                orientation = if (orientation == SplitOrientation.VERTICAL) SplitOrientation.HORIZONTAL else SplitOrientation.VERTICAL
                            }
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            primaryContent()
                        }
                    }
                }

                // Split Divider Handle
                SplitDividerHandle(
                    orientation = SplitOrientation.HORIZONTAL,
                    onDragDelta = { deltaPx ->
                        if (totalWidthPx > 0f) {
                            splitRatio = (splitRatio + deltaPx / totalWidthPx).coerceIn(0.25f, 0.75f)
                        }
                    },
                    onReset = {
                        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.CLICK, 0.5f)
                        splitRatio = 0.5f
                    }
                )

                // Secondary Tab Pane (Right)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(rightWidth)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        SplitPaneHeader(
                            tab = secondaryTab,
                            isPrimary = false,
                            onClose = onCloseSplit,
                            onSwap = onSwapTabs
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            secondaryContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitPaneHeader(
    tab: PetalTabItem,
    isPrimary: Boolean,
    onClose: () -> Unit,
    onSwap: () -> Unit,
    onToggleOrientation: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (isPrimary) "Pane 1" else "Pane 2",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = tab.title.ifBlank { tab.url },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleOrientation != null) {
                    IconButton(onClick = onToggleOrientation, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.ScreenRotation, contentDescription = "Toggle Orientation", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onSwap, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = "Swap Panes", modifier = Modifier.size(16.dp))
                }
                if (isPrimary) {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Split", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitDividerHandle(
    orientation: SplitOrientation,
    onDragDelta: (Float) -> Unit,
    onReset: () -> Unit
) {
    val isVertical = orientation == SplitOrientation.VERTICAL

    Box(
        modifier = Modifier
            .then(
                if (isVertical) {
                    Modifier.fillMaxWidth().height(16.dp)
                } else {
                    Modifier.fillMaxHeight().width(16.dp)
                }
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(if (isVertical) dragAmount.y else dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier
                .then(
                    if (isVertical) Modifier.size(width = 48.dp, height = 4.dp)
                    else Modifier.size(width = 4.dp, height = 48.dp)
                )
        ) {}
    }
}
