package com.petal.browser.ui.components;

import androidx.compose.animation.AnimatedContent;
import androidx.compose.animation.ContentTransform;
import androidx.compose.animation.ExperimentalAnimationApi;
import androidx.compose.animation.fadeIn;
import androidx.compose.animation.fadeOut;
import androidx.compose.animation.slideInVertically;
import androidx.compose.animation.slideOutVertically;
import androidx.compose.animation.with;
import androidx.compose.material3.MaterialTheme;
import androidx.compose.material3.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.unit.sp;

/**
 * AnimatedCounterBadge renders M3 Expressive animated flip numbers for open tabs & download counts.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedCounterBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { height -> height } + fadeIn() with
                slideOutVertically { height -> -height } + fadeOut()
            } else {
                slideInVertically { height -> -height } + fadeIn() with
                slideOutVertically { height -> height } + fadeOut()
            }
        },
        label = "animatedBadgeCounter"
    ) { targetCount ->
        Text(
            text = "$targetCount",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            modifier = modifier
        )
    }
}
