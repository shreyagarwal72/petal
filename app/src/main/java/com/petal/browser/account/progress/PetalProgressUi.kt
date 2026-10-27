package com.petal.browser.account.progress

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.preference.PreferenceManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.account.GoogleUserProfile
import com.petal.browser.account.ProfileAvatarDisplay
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun badgeIcon(id: String): ImageVector = when (id) {
    "first_steps", "tab_juggler", "tab_titan" -> Icons.Rounded.Tab
    "collector", "librarian" -> Icons.Rounded.Bookmark
    "downloader", "hoarder" -> Icons.Rounded.Download
    "shield_up", "fortress" -> Icons.Rounded.Shield
    "regular", "loyal" -> Icons.Rounded.CalendarMonth
    "supporter", "patron" -> Icons.Rounded.Favorite
    else -> Icons.Rounded.EmojiEvents
}

private fun fmt(n: Long): String = NumberFormat.getIntegerInstance().format(n)

@Composable
fun PetalProfileCard(
    profile: GoogleUserProfile,
    onAvatarClick: () -> Unit,
    onEditName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { PetalProgressStore.init(ctx) }
    val xp = PetalProgressStore.xp
    val level = PetalXp.level(xp)
    val progress by animateFloatAsState(PetalXp.progress(xp), MaterialTheme.motionScheme.slowEffectsSpec(), label = "xpBar")
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(36.dp)
    val glass = isLiquidGlassEnabled()
    val showcased = PETAL_BADGES.filter { it.id in PetalProgressStore.showcase && PetalProgressStore.isBadgeUnlocked(it.id) }

    val configuration = LocalConfiguration.current
    val avatarSizeDp = if (configuration.screenWidthDp < 360) 68 else if (configuration.screenWidthDp < 400) 72 else 80
    val cameraBadgeSizeDp = if (avatarSizeDp <= 72) 24 else 28
    val cameraIconSizeDp = if (avatarSizeDp <= 72) 13 else 15

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
            .liquidGlassChrome(shape = shape, enabled = glass, sheenIntensity = 0.6f)
    ) {
        if (glass) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(cs.surface.copy(alpha = 0.22f))
            )
        }
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PETAL EXPLORER",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = cs.surfaceContainerHighest.copy(alpha = 0.95f),
                    contentColor = cs.onSurface,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        "LV $level",
                        Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.bouncyClickable { onAvatarClick() },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    ProfileAvatarDisplay(profile = profile, sizeDp = avatarSizeDp)
                    Surface(
                        shape = CircleShape,
                        color = cs.primary,
                        contentColor = cs.onPrimary,
                        border = BorderStroke(2.dp, cs.surface),
                        modifier = Modifier.size(cameraBadgeSizeDp.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(cameraIconSizeDp.dp))
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimaryContainer
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 16.sp,
                                maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                                stepSize = 1.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .bouncyClickable { onEditName() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.progress_edit_name),
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${PetalXp.title(level)} • ${fmt(xp)} XP",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = cs.onPrimaryContainer.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(cs.surface.copy(alpha = 0.55f))
            ) {
                val barProgress = progress.coerceIn(0f, 1f)
                val fillWidth = when {
                    xp <= 0L -> 0.dp
                    barProgress <= 0f -> 10.dp
                    else -> (maxWidth * barProgress).coerceAtLeast(10.dp)
                }
                if (fillWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(fillWidth)
                            .clip(CircleShape)
                            .background(cs.primary)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${fmt(xp - PetalXp.start(level))} / ${fmt(PetalXp.step(level))} XP to level ${level + 1}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = cs.onPrimaryContainer.copy(alpha = 0.92f)
            )
            Spacer(Modifier.height(14.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 340.dp
                val since = PetalProgressStore.since
                val sinceText = if (since > 0) {
                    stringResource(
                        R.string.progress_since,
                        SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(since))
                    )
                } else null

                if (isNarrow && sinceText != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showcased.isEmpty()) {
                                Text(
                                    stringResource(R.string.progress_pin_hint),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = cs.onPrimaryContainer.copy(alpha = 0.90f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                showcased.forEach {
                                    Surface(
                                        shape = CircleShape,
                                        color = cs.surface,
                                        contentColor = cs.primary,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(badgeIcon(it.id), it.title, Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            sinceText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = cs.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showcased.isEmpty()) {
                                Text(
                                    stringResource(R.string.progress_pin_hint),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = cs.onPrimaryContainer.copy(alpha = 0.90f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                showcased.forEach {
                                    Surface(
                                        shape = CircleShape,
                                        color = cs.surface,
                                        contentColor = cs.primary,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(badgeIcon(it.id), it.title, Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                        if (sinceText != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                sinceText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = cs.onPrimaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PetalStatTiles(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    data class Tile(val icon: ImageVector, val value: Long, val label: String, val bg: androidx.compose.ui.graphics.Color, val fg: androidx.compose.ui.graphics.Color)
    val tiles = listOf(
        Tile(Icons.Rounded.Tab, PetalProgressStore.counter(PetalCounter.TABS_OPENED), "Tabs opened", cs.secondaryContainer, cs.onSecondaryContainer),
        Tile(Icons.Rounded.Download, PetalProgressStore.counter(PetalCounter.DOWNLOADS), "Downloads", cs.tertiaryContainer, cs.onTertiaryContainer),
        Tile(Icons.Rounded.Shield, PetalProgressStore.counter(PetalCounter.SITES_BLOCKED), "Blocked", cs.primaryContainer, cs.onPrimaryContainer),
        Tile(Icons.Rounded.Bolt, PetalProgressStore.xp, "XP earned", cs.errorContainer, cs.onErrorContainer),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Surface(shape = RoundedCornerShape(28.dp), color = t.bg, contentColor = t.fg, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(t.icon, null, Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(fmt(t.value), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                Text(t.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(b: PetalBadge, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val unlocked = PetalProgressStore.isBadgeUnlocked(b.id)
    Surface(
        shape = CircleShape,
        color = if (unlocked) cs.primaryContainer else cs.surfaceContainerHighest,
        contentColor = if (unlocked) cs.onPrimaryContainer else cs.onSurfaceVariant,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (unlocked) badgeIcon(b.id) else Icons.Rounded.Lock, b.title, Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun PetalBadgeCollectionCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val total = PETAL_BADGES.size
    val got = PETAL_BADGES.count { PetalProgressStore.isBadgeUnlocked(it.id) }
    val p by animateFloatAsState(got / total.toFloat(), MaterialTheme.motionScheme.slowEffectsSpec(), label = "badgeBar")
    Surface(
        shape = RoundedCornerShape(32.dp), color = cs.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().bouncyClickable { onOpen() }
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Badge collection", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("$got of $total unlocked", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Open badges") }
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PETAL_BADGES.take(5).forEach { BadgeChip(it, 48.dp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalBadgesSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var focus by remember { mutableStateOf(PETAL_BADGES.first()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            val got = PETAL_BADGES.count { PetalProgressStore.isBadgeUnlocked(it.id) }
            Text("Badges  $got/${PETAL_BADGES.size}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(12.dp))
            val unlocked = PetalProgressStore.isBadgeUnlocked(focus.id)
            val (cur, goal) = PetalProgressStore.badgeProgress(focus)
            Surface(shape = RoundedCornerShape(28.dp), color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(focus.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(focus.hint, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    if (unlocked) {
                        val pinned = focus.id in PetalProgressStore.showcase
                        FilledTonalButton(onClick = { PetalProgressStore.toggleShowcase(ctx, focus.id) }) {
                            Text(if (pinned) "Unpin from card" else "Pin to card (max 3)")
                        }
                    } else {
                        LinearProgressIndicator(
                            progress = { (cur.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )
                        Text("${fmt(cur)} / ${fmt(goal)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), modifier = Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(PETAL_BADGES) { b ->
                    Column(Modifier.bouncyClickable { focus = b }, horizontalAlignment = Alignment.CenterHorizontally) {
                        BadgeChip(b, 64.dp)
                        Text(b.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Place once on the page. Shows a dialog when a badge was just earned. */
@Composable
fun PetalBadgeUnlockHost() {
    val fresh = PetalProgressStore.newlyUnlocked
    if (fresh.isEmpty()) return
    val badges = PETAL_BADGES.filter { it.id in fresh }
    if (badges.isEmpty()) { PetalProgressStore.consumeNewBadges(); return }
    AlertDialog(
        onDismissRequest = { PetalProgressStore.consumeNewBadges() },
        icon = { Icon(badgeIcon(badges.first().id), null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (badges.size == 1) "Badge unlocked" else "${badges.size} badges unlocked") },
        text = { Text(badges.joinToString("\n") { "${it.title}: ${it.hint}" }) },
        confirmButton = { TextButton(onClick = { PetalProgressStore.consumeNewBadges() }) { Text("Nice") } }
    )
}

/** Opt-in card. Never shows an ad by itself. Hidden when the build has no ad provider (FOSS). */
@Composable
fun PetalSupportCard(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val provider = PetalAds.provider
    if (!provider.isAvailable) return
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    var consent by remember { mutableStateOf(sp.getBoolean("pp_ads_consent", false)) }
    val target = PetalUnlockable.LIQUID_GLASS
    val unlocked = PetalProgressStore.isUnlocked(ctx, target)
    val got = PetalProgressStore.unlockProgress(ctx, target)

    Surface(shape = RoundedCornerShape(32.dp), color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Support Petal", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                "Watching a short ad is optional. It earns XP and unlocks cosmetic extras. Core browser features are always free.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (unlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(target.title, fontWeight = FontWeight.SemiBold)
                    Text(if (unlocked) "Unlocked" else "$got / ${target.adsNeeded} ads watched, or reach level ${target.levelNeeded}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!consent) {
                Text("Ads load from a third party. Nothing loads until you agree.", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { sp.edit().putBoolean("pp_ads_consent", true).apply(); consent = true }) { Text("I agree, enable ads") }
            } else {
                Button(onClick = {
                    val reason = PetalProgressStore.adBlockReason(ctx)
                    val act = ctx.findActivity()
                    when {
                        reason != null -> onMessage(reason)
                        act == null -> onMessage("Can't show an ad here.")
                        else -> provider.show(act) { watched ->
                            if (watched && PetalProgressStore.onAdCompleted(ctx)) onMessage("Thank you! +${PetalXp.AD_XP} XP")
                            else onMessage("No reward. Watch the full ad to earn it.")
                        }
                    }
                }) { Text("Watch an ad (+${PetalXp.AD_XP} XP)") }
            }
        }
    }
}
