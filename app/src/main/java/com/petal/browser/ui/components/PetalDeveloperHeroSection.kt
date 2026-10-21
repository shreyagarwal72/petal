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
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalGroupSurfaceColor
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

    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val heroShape = RoundedCornerShape(32.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassChrome(shape = heroShape, enabled = isGlass),
        shape = heroShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isGlass) 0.dp else 2.dp
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
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // In glass mode the pill is see-through (the glass panel supplies the tint);
                    // before the backdrop is ready it falls back to a translucent tonal fill.
                    val pillContainerColor = if (isGlass) {
                        petalGroupSurfaceColor().let { c ->
                            if (c == Color.Transparent) Color.Transparent
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        }
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                    val pillTint = MaterialTheme.colorScheme.primaryContainer

                    // Petal Official Website Pill
                    val webShape = RoundedCornerShape(
                        topStart = 20.dp,
                        bottomStart = 20.dp,
                        topEnd = 6.dp,
                        bottomEnd = 6.dp
                    )
                    Button(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://my-petal-browser.vercel.app/")
                        },
                        shape = webShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = if (isGlass) Modifier.liquidGlassChrome(shape = webShape, enabled = true, sheenIntensity = 0.4f, tintColor = pillTint) else Modifier,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = pillContainerColor,
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
                    val emailShape = RoundedCornerShape(6.dp)
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
                        shape = emailShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = if (isGlass) Modifier.liquidGlassChrome(shape = emailShape, enabled = true, sheenIntensity = 0.4f, tintColor = pillTint) else Modifier,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = pillContainerColor,
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
                    val tgShape = RoundedCornerShape(
                        topStart = 6.dp,
                        bottomStart = 6.dp,
                        topEnd = 20.dp,
                        bottomEnd = 20.dp
                    )
                    Button(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://t.me/hereyourchampion")
                        },
                        shape = tgShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = if (isGlass) Modifier.liquidGlassChrome(shape = tgShape, enabled = true, sheenIntensity = 0.4f, tintColor = pillTint) else Modifier,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = pillContainerColor,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroGlassButton(
                    onClick = {
                        haptics.playClick(context)
                        onOpenUrl(com.petal.browser.util.PetalWebLinks.SUPPORT)
                    },
                    isGlass = isGlass,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Support", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                HeroGlassButton(
                    onClick = {
                        haptics.playClick(context)
                        isOtherAppsExpanded = !isOtherAppsExpanded
                    },
                    isGlass = isGlass,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Projects", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = if (isOtherAppsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Other Apps / Projects Drawer (full-width, same size rows)
            AnimatedVisibility(
                visible = isOtherAppsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroGlassButton(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl(com.petal.browser.util.PetalWebLinks.WEBSITE)
                        },
                        isGlass = isGlass,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Public, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Petal Browser", style = MaterialTheme.typography.labelLarge)
                    }

                    HeroGlassButton(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://github.com/shreyagarwal72/switcher5G")
                        },
                        isGlass = isGlass,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Switcher5G", style = MaterialTheme.typography.labelLarge)
                    }

                    HeroGlassButton(
                        onClick = {
                            haptics.playClick(context)
                            onOpenUrl("https://nextup-resource.vercel.app/")
                        },
                        isGlass = isGlass,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nextup Resources", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Website badge (live link to the Petal site) + GitHub repository badge
            HeroLinkBadge(
                label = "my-petal-browser.vercel.app",
                chip = "Website",
                leadingIcon = Icons.Rounded.Language,
                chipIcon = Icons.Rounded.Public,
                isGlass = isGlass,
                onClick = {
                    haptics.playClick(context)
                    onOpenUrl(com.petal.browser.util.PetalWebLinks.WEBSITE)
                }
            )

            HeroLinkBadge(
                label = "shreyagarwal72/petal",
                chip = "GitHub",
                leadingIcon = Icons.Rounded.Code,
                chipIcon = Icons.Rounded.Star,
                isGlass = isGlass,
                onClick = {
                    haptics.playClick(context)
                    onOpenUrl("https://github.com/shreyagarwal72/petal")
                }
            )
        }
    }
}

/** Outlined action button that becomes a single-rim glass pill when Liquid Glass is on. */
@Composable
private fun HeroGlassButton(
    onClick: () -> Unit,
    isGlass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    OutlinedButton(
        onClick = onClick,
        shape = shape,
        // The glass panel already draws its own rim, so the outline would double up.
        border = if (isGlass) null
            else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isGlass) petalGroupSurfaceColor() else Color.Transparent
        ),
        modifier = modifier.then(
            if (isGlass) Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = 0.5f) else Modifier
        ),
        content = content
    )
}

/** Tappable link row (website / GitHub) rendered as a glass capsule. */
@Composable
private fun HeroLinkBadge(
    label: String,
    chip: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    chipIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isGlass: Boolean,
    onClick: () -> Unit
) {
    val badgeShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGlass) Modifier.liquidGlassChrome(shape = badgeShape, enabled = true, sheenIntensity = 0.5f) else Modifier)
            .clip(badgeShape)
            .clickable(onClick = onClick),
        shape = badgeShape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerLowest
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
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isGlass) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = chipIcon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = chip,
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
