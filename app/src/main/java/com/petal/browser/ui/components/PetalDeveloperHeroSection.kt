package com.petal.browser.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.toShape

/**
 * Top Developer Containment Section for Petal About & Developer screens.
 * Recreated with Material 3 Expressive shapes, rotating Cookie12Sided avatar,
 * segmented pill contact options, support & expandable other apps drawer,
 * and live repository badge.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PetalDeveloperHeroSection(
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = remember { PetalHapticEngine.getInstance(context) }

    val versionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "4.2"
        } catch (_: Exception) {
            "4.2"
        }
    }

    var isOtherAppsExpanded by remember { mutableStateOf(false) }

    // 360° Infinite Cookie Rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_cookie_rotation")
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cookie_rotation"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Title & Tagline
            Text(
                text = "Petal v$versionName",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Fast, private, and customizable browser for Android",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(4.dp))

            // Animated Rotating Cookie 12-Sided Avatar
            Box(
                modifier = Modifier
                    .size(184.dp)
                    .graphicsLayer { rotationZ = rotationDegrees }
                    .clip(PetalMaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .combinedClickable(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://github.com/shreyagarwal72")
                        },
                        onLongClick = {
                            haptics.playHeavyClick(context)
                            Toast.makeText(context, "Vanshu Agarwal • @shreyagarwal72", Toast.LENGTH_SHORT).show()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.avatar_developer),
                    contentDescription = "Developer Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(184.dp)
                        .graphicsLayer { rotationZ = -rotationDegrees }
                )
            }

            Spacer(Modifier.height(4.dp))

            // Crafted by Vanshu Agarwal with heart from India
            Text(
                text = "Crafted by Vanshu Agarwal\nwith ❤️ from 🇮🇳",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // Segmented 3-Pill Contact Row (Website, Email, Telegram)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Petal Official Website Pill
                    Button(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://my-petal-browser.vercel.app/")
                        },
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            bottomStart = 20.dp,
                            topEnd = 6.dp,
                            bottomEnd = 6.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Website",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Contact Email Pill (hereyourchampion@gmail.com)
                    Button(
                        onClick = {
                            haptics.playClick(context)
                            val mailUri = Uri.parse("mailto:hereyourchampion@gmail.com")
                            val emailIntent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "Hello from Petal Browser")
                            }
                            try {
                                context.startActivity(Intent.createChooser(emailIntent, "Send Email"))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, "No email client installed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mail,
                            contentDescription = "Contact",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Telegram Pill (t.me/hereyourchampion)
                    Button(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://t.me/hereyourchampion")
                        },
                        shape = RoundedCornerShape(
                            topStart = 6.dp,
                            bottomStart = 6.dp,
                            topEnd = 20.dp,
                            bottomEnd = 20.dp
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Telegram",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Secondary Buttons: Support & Other Apps
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.playClick(context)
                        onOpenUrl("https://github.com/sponsors/shreyagarwal72")
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Support", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        haptics.playClick(context)
                        isOtherAppsExpanded = !isOtherAppsExpanded
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Other Projects", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isOtherAppsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Other Apps / Projects Drawer
            AnimatedVisibility(
                visible = isOtherAppsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 3
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://github.com/shreyagarwal72/petal")
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Public, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Petal Browser", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://github.com/shreyagarwal72/switcher5G")
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Switcher5G", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://nextup-resource.vercel.app/")
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nextup Resources", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // GitHub Repository Live Badge
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable {
                        haptics.playClick(context)
                        onOpenUrl("https://github.com/shreyagarwal72/petal")
                    },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "shreyagarwal72/petal",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
