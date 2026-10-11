package com.petal.browser.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.toShape

/**
 * Material 3 Expressive Shaped Hero Art Component for Petal Setup.
 * Displays a playful composition with an expressive scalloped center badge,
 * satellite shaped icon badges (controller, music note), and four interior tiles.
 */
@Composable
fun PetalExpressiveHeroArt(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroArtAnimation")

    // Subtle gentle breathing / floating animations
    val mainPulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mainPulse"
    )

    val satelliteFloatY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "satelliteFloatY"
    )

    val diamondRotate by infiniteTransition.animateFloat(
        initialValue = 42f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "diamondRotate"
    )

    val mainShape = PetalMaterialShapes.Cookie12Sided.toShape()
    val squareShape = PetalMaterialShapes.RoundedSquare.toShape()
    val pillShape = PetalMaterialShapes.Pill.toShape()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer decorative ambient glow
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(mainPulse * 1.08f)
                .clip(mainShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )

        // Central expressive scalloped shape container (Purple / Primary)
        Box(
            modifier = Modifier
                .size(210.dp)
                .scale(mainPulse)
                .clip(mainShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // 4 App tiles grid inside the badge
            Column(
                modifier = Modifier.size(108.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Top-Left: Rounded square tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.95f))
                    )
                    // Top-Right: Diamond expressive rotated tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .rotate(diamondRotate)
                            .scale(0.85f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.95f))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bottom-Left: Rounded square tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.95f))
                    )
                    // Bottom-Right: Rounded square tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.95f))
                    )
                }
            }
        }

        // Top-Right Satellite Shape: Amber/Brownish Rounded Square with Musical Note
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 26.dp)
                .offset(y = satelliteFloatY.dp)
                .size(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }

        // Bottom-Left Satellite Shape: Teal / Deep Cyan Circle with Game Controller
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 20.dp, start = 26.dp)
                .offset(y = (-satelliteFloatY).dp)
                .size(78.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
