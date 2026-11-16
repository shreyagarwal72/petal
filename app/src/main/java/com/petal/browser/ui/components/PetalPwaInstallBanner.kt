package com.petal.browser.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor

@Composable
fun PetalPwaInstallBanner(
    appName: String,
    onInstallClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val sheen = com.petal.browser.ui.glass.rememberGlassSheen(isGlass)
    val bannerShape = RoundedCornerShape(24.dp)

    Surface(
        shape = bannerShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = if (isGlass) 0.dp else 8.dp,
        shadowElevation = if (isGlass) 0.dp else 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .then(if (isGlass) Modifier.liquidGlassChrome(bannerShape, enabled = true, sheenIntensity = sheen) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GetApp,
                    contentDescription = stringResource(R.string.ui_install_pwa_app),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.ui_install, appName),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.ui_add_to_home_screen_as),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismissClick) {
                    Text(stringResource(R.string.ui_dismiss))
                }
                Button(
                    onClick = onInstallClick,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.ui_install_2))
                }
            }
        }
    }
}
