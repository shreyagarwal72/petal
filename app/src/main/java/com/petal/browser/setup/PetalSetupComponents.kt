package com.petal.browser.setup

import com.petal.browser.ui.components.PetalSpring

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.PetalMaterialShapes
import com.petal.browser.ui.theme.PetalPalette
import com.petal.browser.ui.theme.toShape

/** Segmented step indicator: the active segment stretches, finished ones fill with the primary colour. */
@Composable
internal fun SetupProgressBar(stage: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until count) {
            val weight by animateFloatAsState(
                targetValue = if (i == stage) 2.6f else 1f,
                animationSpec = PetalSpring.calm(),
                label = "setupSegmentWeight"
            )
            val color by animateColorAsState(
                targetValue = if (i <= stage) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), animationSpec = PetalSpring.calm(),
                label = "setupSegmentColor"
            )
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Shaped icon badge + title + body used at the top of every page after the welcome page. */
@Composable
internal fun SetupPageHeader(
    icon: ImageVector,
    badgeShape: Shape,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(badgeShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun SetupSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Colour swatch that morphs from a circle to a cookie when selected. */
@Composable
internal fun PaletteSwatch(
    palette: PetalPalette,
    label: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    val shape: Shape = if (selected) PetalMaterialShapes.Cookie9Sided.toShape() else CircleShape
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = PetalSpring.calm(),
        label = "paletteSwatchScale"
    )
    Column(
        modifier = Modifier
            .width(72.dp)
            .alpha(if (dimmed) 0.5f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(palette.seed),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(24.dp), tint = Color.White)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Tiny schematic of the browser chrome, used for the address-bar position picker. */
@Composable
internal fun AddressBarSchematic(atBottom: Boolean, selected: Boolean) {
    val bar = MaterialTheme.colorScheme.primary
    val bg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        Modifier
            .size(width = 56.dp, height = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(6.dp)
    ) {
        if (!atBottom) Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(bar))
        Spacer(Modifier.weight(1f))
        if (atBottom) Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(bar))
    }
}

/** Live mini-browser. Reflects theme, address-bar side, floating dock and ad-blocker choices as they change. */
@Composable
internal fun MiniBrowserPreview(
    addressAtBottom: Boolean,
    floatingTabs: Boolean,
    adBlock: Boolean,
    homeLabel: String,
    bookmarksLabel: String,
    tabsLabel: String,
    searchHint: String,
    pageTitle: String,
    shieldLabel: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dockInset by animateDpAsState(
        targetValue = if (floatingTabs) 14.dp else 0.dp,
        animationSpec = PetalSpring.calm(),
        label = "miniDockInset"
    )
    val dockCorner by animateDpAsState(
        targetValue = if (floatingTabs) 22.dp else 10.dp,
        animationSpec = PetalSpring.calm(),
        label = "miniDockCorner"
    )

    @Composable
    fun AddressBar() {
        Surface(shape = CircleShape, color = cs.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                Text(
                    searchHint,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (adBlock) {
                    Icon(Icons.Rounded.Shield, shieldLabel, Modifier.size(16.dp), tint = cs.primary)
                }
            }
        }
    }

    @Composable
    fun PageBody(mod: Modifier) {
        Surface(shape = RoundedCornerShape(22.dp), color = cs.primaryContainer.copy(alpha = 0.55f), modifier = mod.fillMaxWidth()) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(46.dp).clip(PetalMaterialShapes.Flower.toShape()).background(cs.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Favorite, null, Modifier.size(20.dp), tint = cs.onPrimary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        pageTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(Modifier.fillMaxWidth(0.85f).height(7.dp).clip(CircleShape).background(cs.onPrimaryContainer.copy(alpha = 0.22f)))
                    Box(Modifier.fillMaxWidth(0.55f).height(7.dp).clip(CircleShape).background(cs.onPrimaryContainer.copy(alpha = 0.15f)))
                }
            }
        }
    }

    @Composable
    fun Dock() {
        Surface(
            shape = RoundedCornerShape(dockCorner),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = dockInset)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Icons.Rounded.Home to homeLabel,
                    Icons.Rounded.Bookmarks to bookmarksLabel,
                    Icons.Rounded.Tab to tabsLabel
                ).forEachIndexed { i, (icon, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, Modifier.size(18.dp), tint = if (i == 0) cs.primary else cs.onSurfaceVariant)
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (i == 0) cs.primary else cs.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(236.dp),
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        androidx.compose.animation.Crossfade(targetState = addressAtBottom, label = "miniPreviewLayout") { bottom ->
            Column(Modifier.fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!bottom) AddressBar()
                PageBody(Modifier.weight(1f))
                if (bottom) AddressBar()
                Dock()
            }
        }
    }
}
