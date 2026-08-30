package com.petal.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.theme.PetalMaterialShapes

enum class EmptyStateIllustrationType {
    DEFAULT,
    DOWNLOADS,
    BOOKMARKS,
    HISTORY,
    TABS
}

@Composable
fun DownloadsEmptyIllustration(
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Box(
        modifier = Modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back tray / disk outline (offset to bottom-right)
        Canvas(
            modifier = Modifier
                .size(width = 54.dp, height = 54.dp)
                .offset(x = 6.dp, y = 5.dp)
        ) {
            val cornerRadius = CornerRadius(14.dp.toPx())
            val strokeWidth = 3.dp.toPx()
            drawRoundRect(
                color = contentColor.copy(alpha = 0.5f),
                size = size,
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth)
            )
        }

        // Front container card with download arrow
        Box(
            modifier = Modifier
                .offset(x = (-4).dp, y = (-3).dp)
                .size(width = 52.dp, height = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .border(3.dp, contentColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 2.5.dp)
                        .clip(CircleShape)
                        .background(contentColor)
                )
            }
        }
    }
}

@Composable
fun BookmarksEmptyIllustration(
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Box(
        modifier = Modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back bookmark outline (offset to bottom-right)
        Canvas(
            modifier = Modifier
                .size(width = 46.dp, height = 62.dp)
                .offset(x = 7.dp, y = 5.dp)
        ) {
            val cornerRadius = CornerRadius(12.dp.toPx())
            val strokeWidth = 3.dp.toPx()
            drawRoundRect(
                color = contentColor.copy(alpha = 0.5f),
                size = size,
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth)
            )
        }

        // Front bookmark card
        Box(
            modifier = Modifier
                .offset(x = (-5).dp, y = (-3).dp)
                .size(width = 46.dp, height = 60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .border(3.dp, contentColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 2.5.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.8f))
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 2.5.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun HistoryEmptyIllustration(
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Box(
        modifier = Modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back history clock outline (offset to bottom-right)
        Canvas(
            modifier = Modifier
                .size(width = 54.dp, height = 54.dp)
                .offset(x = 6.dp, y = 5.dp)
        ) {
            val cornerRadius = CornerRadius(16.dp.toPx())
            val strokeWidth = 3.dp.toPx()
            drawRoundRect(
                color = contentColor.copy(alpha = 0.5f),
                size = size,
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth)
            )
        }

        // Front clock card
        Box(
            modifier = Modifier
                .offset(x = (-4).dp, y = (-3).dp)
                .size(width = 52.dp, height = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor)
                .border(3.dp, contentColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.HistoryToggleOff,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Reusable Expressive "empty state" block:
 * Features a Material 3 Expressive Bun-shaped badge with layered custom illustrations,
 * large bold titles, subtitles, and optional action buttons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyStateBlob(
    icon: Painter? = null,
    imageVector: ImageVector? = null,
    illustrationType: EmptyStateIllustrationType = EmptyStateIllustrationType.DEFAULT,
    title: String,
    description: String = "",
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    badgeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    actionText: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    fraction: Float = 0.85f,
    badgeSize: Dp = 112.dp,
    iconSize: Dp = 48.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = fraction)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = PetalMaterialShapes.Bun.toShape(),
            modifier = Modifier.size(badgeSize),
            color = badgeColor,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (illustrationType) {
                    EmptyStateIllustrationType.DOWNLOADS -> {
                        DownloadsEmptyIllustration(
                            containerColor = badgeColor,
                            contentColor = badgeContentColor
                        )
                    }
                    EmptyStateIllustrationType.BOOKMARKS -> {
                        BookmarksEmptyIllustration(
                            containerColor = badgeColor,
                            contentColor = badgeContentColor
                        )
                    }
                    EmptyStateIllustrationType.HISTORY -> {
                        HistoryEmptyIllustration(
                            containerColor = badgeColor,
                            contentColor = badgeContentColor
                        )
                    }
                    else -> {
                        if (imageVector != null) {
                            Icon(
                                imageVector = imageVector,
                                contentDescription = null,
                                tint = badgeContentColor,
                                modifier = Modifier.size(iconSize)
                            )
                        } else if (icon != null) {
                            Icon(
                                painter = icon,
                                contentDescription = null,
                                tint = badgeContentColor,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (description.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = badgeColor,
                    contentColor = badgeContentColor
                )
            ) {
                if (actionIcon != null) {
                    Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(actionText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

