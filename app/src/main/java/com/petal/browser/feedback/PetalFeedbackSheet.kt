package com.petal.browser.feedback

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.appicon.PetalAppIcons
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.ExpressiveSplitButton
import com.petal.browser.ui.components.PetalSpring
import com.petal.browser.ui.components.SplitButtonVariant
import com.petal.browser.ui.containment.*
import com.petal.browser.ui.kit.TextButton
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.theme.ExpressiveButtonShape
import com.petal.browser.ui.theme.PetalText
import java.text.NumberFormat

/**
 * Unified Material 3 Expressive + Liquid Glass Feedback & Stars Sheet for Google Play Store and GitHub.
 *
 * Uses [PetalSheet], [PetalSheetCard], [petalSheetSurface], [ExpressiveSplitButton], and
 * [PetalPopupMenu] so both container backdrop blur/refraction and split-button menus adapt
 * seamlessly to Liquid Glass and M3E themes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalFeedbackSheet(
    onDismiss: () -> Unit,
    isAutoPrompt: Boolean = false,
    initialStars: Int = 0,
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity: Activity? = remember(context) { PetalAppIcons.findActivity(context) }
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val haptics = remember(context) { PetalHapticEngine.getInstance(context) }
    val cs = MaterialTheme.colorScheme
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    var selectedStars by remember { mutableIntStateOf(initialStars.coerceIn(0, 5)) }
    var feedbackNote by remember { mutableStateOf("") }
    var playRated by remember { mutableStateOf(sp.getBoolean(PetalFeedbackManager.PREF_PLAY_RATED, false)) }
    var githubStarred by remember { mutableStateOf(sp.getBoolean(PetalFeedbackManager.PREF_GITHUB_STARRED, false)) }
    var githubStarsCount by remember { mutableStateOf(PetalFeedbackManager.cachedGitHubStars(sp)) }

    var playMenuExpanded by remember { mutableStateOf(false) }
    var githubMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isAutoPrompt) {
            PetalFeedbackManager.markPromptShown(sp)
        }
        val fresh = PetalFeedbackManager.fetchGitHubStarsIfNeeded(sp)
        if (fresh != null) {
            githubStarsCount = fresh
        }
    }

    val starLabels = remember {
        listOf(
            "Needs work",
            "Could be better",
            "Decent",
            "Really good!",
            "Love it!"
        )
    }

    PetalSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PetalSheetHeader(
                title = if (isAutoPrompt) "Enjoying Petal?" else "Rate & Star Petal",
                subtitle = "Review on Google Play or star shreyagarwal72/petal on GitHub",
                leading = {
                    val badgeContainer = cs.primaryContainer
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(ExpressiveButtonShape)
                            .background(badgeContainer)
                            .then(
                                if (isGlass) Modifier.liquidGlassChrome(
                                    shape = ExpressiveButtonShape,
                                    enabled = true,
                                    sheenIntensity = 0.55f,
                                    tintColor = badgeContainer
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selectedStars >= 4) Icons.Rounded.AutoAwesome else Icons.Rounded.Star,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                },
                trailing = {
                    githubStarsCount?.let { count ->
                        Row(
                            modifier = Modifier
                                .petalSheetSurface(
                                    shape = CircleShape,
                                    color = cs.tertiaryContainer,
                                    sheen = 0.45f
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = cs.onTertiaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = NumberFormat.getInstance().format(count),
                                style = PetalText.badge,
                                color = cs.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )

            // 1..5 Star Rating Card
            PetalSheetCard(
                color = cs.surfaceContainerHighest,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your rating",
                        style = PetalText.sectionHeader,
                        color = cs.onSurface
                    )
                    if (selectedStars in 1..5) {
                        Surface(
                            shape = CircleShape,
                            color = if (PetalFeedbackManager.isPositiveRating(selectedStars)) cs.primaryContainer else cs.secondaryContainer,
                            contentColor = if (PetalFeedbackManager.isPositiveRating(selectedStars)) cs.onPrimaryContainer else cs.onSecondaryContainer
                        ) {
                            Text(
                                text = "${selectedStars}★ · ${starLabels[selectedStars - 1]}",
                                style = PetalText.badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..5).forEach { star ->
                        val active = star <= selectedStars
                        val tileContainer by animateColorAsState(
                            targetValue = if (active) cs.primaryContainer else cs.surfaceContainerHigh,
                            animationSpec = PetalSpring.quick(),
                            label = "star_bg_$star"
                        )
                        val starTint by animateColorAsState(
                            targetValue = if (active) cs.primary else cs.onSurfaceVariant,
                            animationSpec = PetalSpring.quick(),
                            label = "star_fg_$star"
                        )
                        val starScale by animateFloatAsState(
                            targetValue = if (star == selectedStars) 1.06f else 1f,
                            animationSpec = PetalSpring.quick(),
                            label = "star_scale_$star"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                }
                                .petalSheetSurface(
                                    shape = ExpressiveButtonShape,
                                    color = tileContainer,
                                    onClick = {
                                        selectedStars = star
                                        haptics.play(PetalHapticEngine.Pattern.CLICK, 0.75f)
                                    },
                                    role = Role.Button,
                                    sheen = if (active) 0.55f else 0.25f
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                contentDescription = "$star stars",
                                tint = starTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = selectedStars in 1..3,
                    enter = fadeIn(PetalSpring.calm()) + expandVertically(PetalSpring.calm()),
                    exit = fadeOut(PetalSpring.calm()) + shrinkVertically(PetalSpring.calm())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tell us what felt rough so we can fix it directly (Play Store review remains available in the menu too).",
                            style = PetalText.rowSubtitle,
                            color = cs.onSurfaceVariant
                        )
                        TextField(
                            value = feedbackNote,
                            onValueChange = { feedbackNote = it },
                            label = { Text("Optional feedback note") },
                            placeholder = { Text("Slow page, UI glitch, missing feature...") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = if (isGlass) petalGroupSurfaceColor() else cs.surfaceContainerHigh,
                                unfocusedContainerColor = if (isGlass) petalGroupSurfaceColor() else cs.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            glass = false
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selectedStars >= 4,
                    enter = fadeIn(PetalSpring.calm()) + expandVertically(PetalSpring.calm()),
                    exit = fadeOut(PetalSpring.calm()) + shrinkVertically(PetalSpring.calm())
                ) {
                    Text(
                        text = "Thank you! Your stars help others discover Petal on Google Play and support open-source development on GitHub.",
                        style = PetalText.rowSubtitle,
                        color = cs.primary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            // Platform Action Cards with ExpressiveSplitButton + PetalPopupMenu
            PetalSheetCard(
                color = cs.surfaceContainerHighest,
                contentPadding = PaddingValues(14.dp)
            ) {
                // Google Play Store Split Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PetalSheetSectionLabel("Google Play & Feedback", modifier = Modifier.padding(0.dp))
                    if (playRated) {
                        Surface(
                            shape = CircleShape,
                            color = cs.primaryContainer,
                            contentColor = cs.onPrimaryContainer
                        ) {
                            Text(
                                text = "Thanked",
                                style = PetalText.badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    val isLowStar = selectedStars in 1..3
                    ExpressiveSplitButton(
                        label = if (isLowStar) "Send Private Feedback" else "Rate on Play Store",
                        icon = if (isLowStar) Icons.Rounded.Mail else Icons.Rounded.Star,
                        variant = SplitButtonVariant.FILLED,
                        isMenuExpanded = playMenuExpanded,
                        onPrimaryClick = {
                            val stars = if (selectedStars in 1..5) selectedStars else 5
                            PetalFeedbackManager.markPlayRated(sp, stars)
                            playRated = true
                            if (isLowStar) {
                                PetalFeedbackManager.sendFeedbackReport(context, stars, feedbackNote)
                                onMessage("Opening feedback composer with diagnostics")
                                onDismiss()
                            } else {
                                PetalFeedbackManager.requestPlayReview(activity, context) { openedFallback ->
                                    if (openedFallback) {
                                        onMessage("Opening Petal on Google Play")
                                    } else {
                                        onMessage("Thanks for rating Petal!")
                                    }
                                }
                                onDismiss()
                            }
                        },
                        onMenuClick = { playMenuExpanded = true },
                        modifier = Modifier.wrapContentWidth()
                    )

                    PetalPopupMenu(
                        expanded = playMenuExpanded,
                        onDismissRequest = { playMenuExpanded = false }
                    ) {
                        PetalPopupMenuItem(
                            text = { Text("Open Play Store Listing", style = PetalText.button) },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                            onClick = {
                                playMenuExpanded = false
                                PetalFeedbackManager.markPlayRated(sp, if (selectedStars in 1..5) selectedStars else 5)
                                playRated = true
                                PetalFeedbackManager.openPlayStoreListing(context)
                                onDismiss()
                            }
                        )
                        PetalPopupMenuItem(
                            text = { Text("Send Feedback & Diagnostics", style = PetalText.button) },
                            leadingIcon = { Icon(Icons.Rounded.Mail, contentDescription = null) },
                            onClick = {
                                playMenuExpanded = false
                                PetalFeedbackManager.sendFeedbackReport(context, selectedStars, feedbackNote)
                                onDismiss()
                            }
                        )
                        if (isAutoPrompt) {
                            PetalMenuDivider()
                            PetalPopupMenuItem(
                                text = { Text("Don't ask again", style = PetalText.button) },
                                leadingIcon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
                                destructive = true,
                                onClick = {
                                    playMenuExpanded = false
                                    PetalFeedbackManager.markOptOut(sp)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // GitHub Stars Split Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PetalSheetSectionLabel("GitHub Stars", modifier = Modifier.padding(0.dp))
                    Text(
                        text = if (githubStarsCount != null) {
                            "★ ${NumberFormat.getInstance().format(githubStarsCount)} stars · shreyagarwal72/petal"
                        } else {
                            "shreyagarwal72/petal"
                        },
                        style = PetalText.caption,
                        color = cs.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSplitButton(
                        label = when {
                            githubStarred -> "Starred on GitHub"
                            githubStarsCount != null -> "Star on GitHub (★ $githubStarsCount)"
                            else -> "Star on GitHub"
                        },
                        icon = if (githubStarred) Icons.Rounded.Verified else Icons.Rounded.Code,
                        variant = SplitButtonVariant.TONAL,
                        isMenuExpanded = githubMenuExpanded,
                        onPrimaryClick = {
                            PetalFeedbackManager.markGitHubStarred(sp)
                            githubStarred = true
                            PetalFeedbackManager.openGitHubRepo(context)
                            onMessage("Opening Petal on GitHub — tap ★ Star to support!")
                            onDismiss()
                        },
                        onMenuClick = { githubMenuExpanded = true },
                        modifier = Modifier.wrapContentWidth()
                    )

                    PetalPopupMenu(
                        expanded = githubMenuExpanded,
                        onDismissRequest = { githubMenuExpanded = false }
                    ) {
                        PetalPopupMenuItem(
                            text = { Text("Open Repository", style = PetalText.button) },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                            onClick = {
                                githubMenuExpanded = false
                                PetalFeedbackManager.openGitHubRepo(context)
                                onDismiss()
                            }
                        )
                        PetalPopupMenuItem(
                            text = { Text("View Releases", style = PetalText.button) },
                            leadingIcon = { Icon(Icons.Rounded.RocketLaunch, contentDescription = null) },
                            onClick = {
                                githubMenuExpanded = false
                                PetalFeedbackManager.openGitHubReleases(context)
                                onDismiss()
                            }
                        )
                        PetalPopupMenuItem(
                            text = { Text("Open Issue Tracker", style = PetalText.button) },
                            leadingIcon = { Icon(Icons.Rounded.BugReport, contentDescription = null) },
                            onClick = {
                                githubMenuExpanded = false
                                PetalFeedbackManager.openGitHubIssues(context)
                                onDismiss()
                            }
                        )
                        PetalMenuDivider()
                        PetalPopupMenuItem(
                            text = { Text("Copy GitHub URL", style = PetalText.button) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                            onClick = {
                                githubMenuExpanded = false
                                PetalFeedbackManager.copyGitHubUrl(context)
                                onMessage("Copied GitHub repository link")
                            }
                        )
                    }
                }
            }

            if (isAutoPrompt) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            PetalFeedbackManager.markOptOut(sp)
                            onDismiss()
                        }
                    ) {
                        Text("Don't ask again")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Remind me later")
                    }
                }
            }
        }
    }
}
