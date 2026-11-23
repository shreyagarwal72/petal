package com.petal.browser.account.progress

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
import com.petal.browser.ui.kit.Button
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.theme.MaterialShapes
import com.petal.browser.ui.theme.toShape

/**
 * Developer Payment Support Sheet
 * ─────────────────────────────────────────────────────────────────────────────
 * Replaces ad-based unlocks and third-party tip links with direct developer payment support.
 * Directs users to Telegram (@hereyourchampion) with a prefilled message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalFuelTheCodeSheet(
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    PetalSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Icon Container
            val iconShape = MaterialShapes.SoftBurst.toShape()
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(iconShape)
                    .background(cs.primaryContainer)
                    .then(
                        if (isGlass) Modifier.liquidGlassChrome(
                            shape = iconShape,
                            enabled = true,
                            sheenIntensity = 0.5f,
                            tintColor = cs.primaryContainer
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = cs.onPrimaryContainer
                )
            }

            // Title & Subtitle
            Text(
                text = "Support Development",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = cs.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Petal Browser is 100% free, independent, and open source with zero advertisements. Support continued development and future updates with direct payment support via Telegram.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // Info Card
            val cardShape = RoundedCornerShape(16.dp)
            Surface(
                shape = cardShape,
                color = petalGroupSurfaceColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .then(
                        if (isGlass) Modifier.liquidGlassChrome(
                            shape = cardShape,
                            enabled = true,
                            sheenIntensity = 0.25f,
                            tintColor = cs.surface
                        ) else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = cs.secondaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Send,
                                    contentDescription = null,
                                    tint = cs.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Direct Telegram Support",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface
                            )
                            Text(
                                text = "@hereyourchampion",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Primary Action: Telegram Redirect
            Button(
                onClick = {
                    try {
                        val telegramUrl = "https://t.me/hereyourchampion?text=I%20want%20to%20support%20Petal%20Browser"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                        onDismiss()
                    } catch (e: Exception) {
                        onMessage("Could not open Telegram: ${e.message}")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Support on Telegram",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Secondary Action: GitHub
            TextButton(
                onClick = {
                    try {
                        val githubUrl = "https://github.com/shreyagarwal72/petal"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("View Source on GitHub")
                }
            }
        }
    }
}
