package com.petal.browser.compose.find

import com.petal.browser.ui.theme.ExpressiveButtonShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.ui.components.bouncyClickable
import androidx.compose.ui.res.stringResource
import com.petal.browser.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.preference.PreferenceManager
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.glass.LocalPetalGlassRevision
import com.petal.browser.ui.glass.liquidGlass
import com.petal.browser.ui.glass.rememberGlassBar

/**
 * PetalFindInPageBar
 *
 * Full Material 3 Expressive UI & UX component for Find in Page.
 * Styled with expressive pill shapes, tonal elevation, search match navigation,
 * clear action, and smooth entry/exit animations.
 */
@Composable
fun PetalFindInPageBar(
    visible: Boolean,
    query: String,
    currentMatch: Int = 0,
    totalMatches: Int = 0,
    onQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    // The find bar takes the address bar's slot, so it follows the address bar's position,
    // height, shape, insets and glass (same live glass that samples the page).
    val context = LocalContext.current
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val isBottom = "BOTTOM".equals(sp.getString("sp_address_bar_position", "TOP"), ignoreCase = true)
    val isCompact = "COMPACT".equals(sp.getString("sp_address_bar_height", "COMPACT"), ignoreCase = true)

    LocalPetalGlassRevision.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_address_bar")
    val glassSource = LocalView.current.rootView.findViewById<android.view.View>(R.id.main_content)
    val glassBar = rememberGlassBar(if (isGlass) glassSource else null)
    val baseGlassColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val rawStatusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusBarTopInset = if (rawStatusTop == 0.dp) 0.dp else WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()

    val barShape = if (isBottom) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    } else {
        RoundedCornerShape(if (isCompact) 24.dp else 28.dp)
    }
    val vGap = if (isCompact) 3.dp else 5.dp

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { if (isBottom) it else -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { if (isBottom) it else -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isBottom) Modifier
                    else Modifier.padding(start = 12.dp, end = 12.dp, top = statusBarTopInset + vGap, bottom = vGap)
                )
                .then(if (isGlass) Modifier.liquidGlass(barShape, baseGlassColor, glassBar) else Modifier),
            shape = barShape,
            color = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (isGlass) 0.dp else if (isBottom) 2.dp else 4.dp,
            shadowElevation = if (isGlass) 0.dp else if (isBottom) 2.dp else 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search icon badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ExpressiveButtonShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text Input Field & Match Counter Badge
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.ui_find_in_page_2),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onFindNext() })
                        )
                    }

                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (totalMatches > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = if (totalMatches > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Text(
                                text = "$currentMatch/$totalMatches",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Navigation Controls (Previous, Next)
                FilledTonalIconButton(
                    shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                    onClick = onFindPrevious,
                    enabled = query.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.ui_previous_match),
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalIconButton(
                    shape = com.petal.browser.ui.theme.ExpressiveButtonShape,
                    onClick = onFindNext,
                    enabled = query.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.ui_next_match),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(ExpressiveButtonShape)
                        .bouncyClickable(onClick = onClose)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.ui_close_find_in_page),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Java interop bridge to set up the Find in Page ComposeView from BrowserActivity.
 */
object PetalFindInPageBridge {
    @JvmStatic
    @JvmOverloads
    fun setupFindInPage(
        composeView: androidx.compose.ui.platform.ComposeView,
        visible: Boolean,
        query: String,
        currentMatch: Int = 0,
        totalMatches: Int = 0,
        onQueryChange: java.util.function.Consumer<String>,
        onFindNext: Runnable,
        onFindPrevious: Runnable,
        onClose: Runnable
    ) {
        composeView.setContent {
            com.petal.browser.ui.theme.PetalExpressiveTheme {
                PetalFindInPageBar(
                    visible = visible,
                    query = query,
                    currentMatch = currentMatch,
                    totalMatches = totalMatches,
                    onQueryChange = { onQueryChange.accept(it) },
                    onFindNext = { onFindNext.run() },
                    onFindPrevious = { onFindPrevious.run() },
                    onClose = { onClose.run() }
                )
            }
        }
    }
}

