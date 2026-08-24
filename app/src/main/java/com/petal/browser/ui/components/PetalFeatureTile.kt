package com.petal.browser.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * PetalFeatureTile
 * ─────────────────────────────────────────────────────────────────────────
 * Ported from Gramly's home-screen "FeatureTile" card: a bold editorial button
 * that pairs a text column with a scalloped "cookie" icon badge, while the
 * feature's own glyph bleeds oversized and tilted behind the content as a
 * graphic anchor. Optionally ends in a stadium pill affordance.
 *
 * Drop-in replacement for a plain settings/action row wherever a card-style
 * tappable button is wanted (e.g. Settings overview categories, Account
 * actions).
 */

/** Shared scalloped silhouette for feature-tile icon badges. */
private val PetalCookieBadge = ScallopedShape(lobes = 8, depth = 0.16f)

@Composable
fun PetalFeatureTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    onContainer: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    height: Dp = 128.dp,
    pillLabel: String? = "Open",
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp)
            .expressivePress(interactionSource),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = onContainer,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The feature's own glyph, oversized and tilted, bleeding off the
            // bottom-right corner. The card clips it to its rounded shape.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer.copy(alpha = 0.08f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(140.dp)
                    .offset(x = 30.dp, y = 36.dp)
                    .rotate(-14f),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = onContainer.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when {
                        trailing != null -> trailing()
                        pillLabel != null -> PetalOpenPill(onContainer = onContainer, label = pillLabel)
                    }
                }

                // Scalloped icon badge — the expressive focal point.
                Surface(
                    shape = PetalCookieBadge,
                    color = onContainer.copy(alpha = 0.16f),
                    contentColor = onContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

/** Stadium pill affordance — a clear, label-led alternative to a bare chevron. */
@Composable
fun PetalOpenPill(onContainer: Color, label: String = "Open") {
    Surface(
        shape = CircleShape,
        color = onContainer.copy(alpha = 0.14f),
        contentColor = onContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * A soft scalloped "cookie" outline — a circle whose radius is gently rippled into
 * [lobes] rounded bumps. One of the signature M3 Expressive silhouettes; gives an
 * icon badge a friendlier, more crafted feel than a plain circle.
 */
class ScallopedShape(
    private val lobes: Int = 8,
    private val depth: Float = 0.14f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = min(cx, cy)
        val rMid = base * (1f - depth / 2f)
        val amp = base * depth / 2f
        val steps = 240
        val path = Path()
        for (i in 0..steps) {
            val angle = (i.toFloat() / steps * 2f * PI - PI / 2f).toFloat()
            val r = rMid + amp * cos(lobes * angle)
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Springy press feedback: the element squishes slightly when pressed and bounces
 * back. Matches [PetalFeatureTile]'s tactile feel.
 */
fun Modifier.expressivePress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 650f),
        label = "petalFeatureTilePressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
