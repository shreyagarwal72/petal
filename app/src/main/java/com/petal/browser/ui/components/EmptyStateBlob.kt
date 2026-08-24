package com.petal.browser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.theme.ExperimentalMaterial3ExpressiveApi
import com.petal.browser.ui.theme.MaterialShapes
import com.petal.browser.ui.theme.toShape

/**
 * Reusable "empty state" block: icon sitting inside a soft, irregular
 * Material 3 Expressive blob shape, with a title and optional description below it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EmptyStateBlob(
    icon: Painter,
    title: String,
    description: String = "",
    fraction: Float = 0.8f,
    blobSize: Dp = 160.dp,
    iconSize: Dp = 76.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = fraction),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialShapes.Pill.toShape(),
            modifier = Modifier
                .height(blobSize)
                .width(blobSize),
            color = MaterialTheme.colorScheme.surfaceBright
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(iconSize).height(iconSize)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            title,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (description.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
