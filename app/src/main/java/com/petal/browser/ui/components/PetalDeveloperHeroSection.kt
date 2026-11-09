package com.petal.browser.ui.components

import com.petal.browser.ui.theme.ExpressiveButtonShape
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
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
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
import com.petal.browser.ui.containment.PetalBadgeVariant
import com.petal.browser.ui.containment.PetalGroupIconBadge
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
    val animActive = rememberAnimationActive()
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_cookie_rotation")
    val rawRotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cookie_rotation"
    )
    val rotationDegrees = if (animActive) rawRotationDegrees else 0f

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

            // Connected contact group (M3 Expressive): outer corners pill, inner corners 6dp,
            // one colour role per action (primary / secondary / tertiary).
            val scheme = MaterialTheme.colorScheme
            val contactBg = if (isGlass) Color.Transparent else null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val contacts = listOf(
                    Triple(Icons.Rounded.Language, "Website", Pair(scheme.primaryContainer, scheme.onPrimaryContainer)),
                    Triple(Icons.Rounded.Mail, "Email", Pair(scheme.secondaryContainer, scheme.onSecondaryContainer)),
                    Triple(Icons.Rounded.Send, "Telegram", Pair(scheme.tertiaryContainer, scheme.onTertiaryContainer)),
                )
                contacts.forEachIndexed { i, (icon, label, colors) ->
                    val shape = RoundedCornerShape(
                        topStart = if (i == 0) 50.dp else 6.dp,
                        bottomStart = if (i == 0) 50.dp else 6.dp,
                        topEnd = if (i == contacts.lastIndex) 50.dp else 6.dp,
                        bottomEnd = if (i == contacts.lastIndex) 50.dp else 6.dp,
                    )
                    Button(
                        onClick = {
                            haptics.playClick(context)
                            when (i) {
                                0 -> onOpenUrl("https://my-petal-browser.vercel.app/")
                                1 -> {
                                    val mailUri = Uri.parse("mailto:hereyourchampion@gmail.com")
                                    val emailIntent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
                                        putExtra(Intent.EXTRA_SUBJECT, "Hello from Petal Browser")
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(emailIntent, "Send Email"))
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "No email client installed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                else -> onOpenUrl("https://t.me/hereyourchampion")
                            }
                        },
                        shape = shape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isGlass) Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = 0.4f, tintColor = colors.first) else Modifier),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contactBg ?: colors.first,
                            contentColor = colors.second
                        ), glass = false
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }

            // Secondary actions: Support (tertiary role) + Projects (secondary role, toggles drawer)
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
                    container = scheme.tertiaryContainer,
                    content = scheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Support", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                HeroGlassButton(
                    onClick = {
                        haptics.playClick(context)
                        isOtherAppsExpanded = !isOtherAppsExpanded
                    },
                    isGlass = isGlass,
                    container = scheme.secondaryContainer,
                    content = scheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
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

            // Projects drawer: one connected list group (outer 22dp, inner 6dp corners)
            AnimatedVisibility(
                visible = isOtherAppsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val projects = listOf(
                    HeroLink(Icons.Rounded.Public, "Petal Browser", "Official website", PetalBadgeVariant.PRIMARY, com.petal.browser.util.PetalWebLinks.WEBSITE),
                    HeroLink(Icons.Rounded.NetworkCheck, "Switcher5G", "Quick 5G / LTE switching", PetalBadgeVariant.SECONDARY, "https://github.com/shreyagarwal72/switcher5G"),
                    HeroLink(Icons.Rounded.AutoStories, "Nextup Resources", "Study resources", PetalBadgeVariant.TERTIARY, "https://nextup-resource.vercel.app/"),
                )
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    projects.forEachIndexed { i, link ->
                        HeroListRow(
                            link = link,
                            shape = heroGroupShape(i, projects.size),
                            isGlass = isGlass,
                            onClick = { haptics.playClick(context); onOpenUrl(link.url) }
                        )
                    }
                }
            }

            // Links (website + GitHub) as a connected list group
            val links = listOf(
                HeroLink(Icons.Rounded.Language, "my-petal-browser.vercel.app", "Website", PetalBadgeVariant.PRIMARY, com.petal.browser.util.PetalWebLinks.WEBSITE),
                HeroLink(Icons.Rounded.Code, "shreyagarwal72/petal", "GitHub", PetalBadgeVariant.SECONDARY, "https://github.com/shreyagarwal72/petal"),
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                links.forEachIndexed { i, link ->
                    HeroListRow(
                        link = link,
                        shape = heroGroupShape(i, links.size),
                        isGlass = isGlass,
                        onClick = { haptics.playClick(context); onOpenUrl(link.url) }
                    )
                }
            }
        }
    }
}

private data class HeroLink(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val variant: PetalBadgeVariant,
    val url: String,
)

private fun heroGroupShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 22.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** Tonal action button (role colours) that becomes a single-rim glass pill when Liquid Glass is on. */
@Composable
private fun HeroGlassButton(
    onClick: () -> Unit,
    isGlass: Boolean,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    body: @Composable RowScope.() -> Unit
) {
    val shape = ExpressiveButtonShape
    FilledTonalButton(
        onClick = onClick,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isGlass) petalGroupSurfaceColor() else container,
            contentColor = if (isGlass) MaterialTheme.colorScheme.onSurface else content
        ),
        modifier = modifier.then(
            if (isGlass) Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = 0.5f) else Modifier
        ),
        content = body, glass = false
    )
}

/** M3 Expressive list row: colour-role icon badge, title + subtitle, trailing open-in-new. */
@Composable
private fun HeroListRow(
    link: HeroLink,
    shape: RoundedCornerShape,
    isGlass: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGlass) Modifier.liquidGlassChrome(shape = shape, enabled = true, sheenIntensity = 0.5f) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isGlass) petalGroupSurfaceColor() else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PetalGroupIconBadge(icon = link.icon, variant = link.variant, size = 44.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = link.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = link.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
