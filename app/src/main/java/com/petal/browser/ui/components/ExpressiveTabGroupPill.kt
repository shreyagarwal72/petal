package com.petal.browser.ui.components;

import androidx.compose.foundation.background;
import androidx.compose.foundation.clickable;
import androidx.compose.foundation.layout.*;
import androidx.compose.foundation.shape.RoundedCornerShape;
import androidx.compose.material3.MaterialTheme;
import androidx.compose.material3.Surface;
import androidx.compose.material3.Text;
import androidx.compose.runtime.Composable;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.clip;
import androidx.compose.ui.graphics.Color;
import androidx.compose.ui.text.font.FontWeight;
import androidx.compose.ui.unit.dp;
import androidx.compose.ui.unit.sp;

/**
 * ExpressiveTabGroupPill renders Material 3 Expressive color-coded tab group tags.
 */
@Composable
fun ExpressiveTabGroupPill(
    groupName: String,
    containerColor: Color,
    contentColor: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(contentColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = groupName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}
