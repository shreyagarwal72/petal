package com.petal.browser.compose.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.petal.browser.BuildConfig
import com.petal.browser.island.IslandSettings
import com.petal.browser.island.PetalIslandAccessibilityService
import com.petal.browser.live.LiveEventAction
import com.petal.browser.live.LiveEventCategory
import com.petal.browser.live.LiveEventKind
import com.petal.browser.live.PetalLiveActionBus
import com.petal.browser.live.PetalLiveEvents
import com.petal.browser.ui.containment.PetalGroup
import com.petal.browser.ui.containment.PetalGroupRow
import com.petal.browser.ui.containment.PetalSettingsToggleRow

/**
 * NowBar "where it shows" and "what it shows" settings:
 *  - master switch for events
 *  - placement: Auto / Accessibility (status bar) / Over other apps / In-app only
 *  - draw inside the status bar
 *  - one switch per event group
 *  - a Test button that fires a few sample events
 *
 * The accessibility option only appears in non-Play builds, where the service is declared.
 */
@Composable
fun NowBarEventsSettingsSection() {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var master by remember { mutableStateOf(sp.getBoolean(PetalLiveEvents.KEY_MASTER, true)) }
    var placement by remember { mutableStateOf(sp.getString(IslandSettings.KEY_PLACEMENT, IslandSettings.PLACE_AUTO) ?: IslandSettings.PLACE_AUTO) }
    var anchor by remember { mutableStateOf(sp.getBoolean(IslandSettings.KEY_ANCHOR_STATUS_BAR, true)) }
    var a11yOn by remember { mutableStateOf(PetalIslandAccessibilityService.isEnabledInSystem(context)) }
    var showA11yDialog by remember { mutableStateOf(false) }

    // Re-check after the user comes back from system Accessibility settings.
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                a11yOn = PetalIslandAccessibilityService.isEnabledInSystem(context)
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    SectionTitle("NowBar Events")
    PetalGroup(rowCount = 1) { _, position ->
        PetalSettingsToggleRow(
            title = "Show live events",
            subtitle = "Downloads, backups, milestones, ad blocker, setting changes and more",
            icon = Icons.Rounded.Notifications,
            checked = master,
            position = position,
            onCheckedChange = {
                master = it
                sp.edit().putBoolean(PetalLiveEvents.KEY_MASTER, it).apply()
            }
        )
    }

    Spacer(Modifier.height(10.dp))
    SectionTitle("Where the NowBar shows")

    val isPlay = BuildConfig.IS_PLAY_STORE_BUILD || !BuildConfig.ENABLE_ACCESSIBILITY_ISLAND
    // (mode, title, subtitle, icon)
    val modes = buildList {
        add(Mode(IslandSettings.PLACE_AUTO, "Automatic", "Best available: status bar, then over apps, then in Petal", Icons.Rounded.PhoneAndroid))
        if (!isPlay) {
            add(
                Mode(
                    IslandSettings.PLACE_ACCESSIBILITY, "Status bar (Accessibility)",
                    if (a11yOn) "On. Draws inside the status bar, over every app"
                    else "Tap to turn on. Draws inside the status bar, over every app",
                    Icons.Rounded.VerticalAlignTop
                )
            )
            add(Mode(IslandSettings.PLACE_OVERLAY, "Over other apps", "Needs 'Display over other apps'. Hidden while Petal is open", Icons.Rounded.Layers))
        }
        add(Mode(IslandSettings.PLACE_IN_APP, "Inside Petal only", "Never shows over other apps", Icons.Rounded.PhoneAndroid))
    }

    fun choose(mode: String) {
        placement = mode
        sp.edit().putString(IslandSettings.KEY_PLACEMENT, mode).apply()
        if (mode == IslandSettings.PLACE_ACCESSIBILITY && !a11yOn) showA11yDialog = true
    }

    PetalGroup(rowCount = modes.size) { index, position ->
        val m = modes[index]
        PetalGroupRow(
            icon = m.icon,
            title = m.title,
            subtitle = m.subtitle,
            position = position,
            onClick = { choose(m.key) },
            trailing = { RadioButton(selected = placement == m.key, onClick = { choose(m.key) }) }
        )
    }

    Spacer(Modifier.height(10.dp))
    PetalGroup(rowCount = 1) { _, position ->
        PetalSettingsToggleRow(
            title = "Draw inside the status bar",
            subtitle = "Off = sit just below the status bar",
            icon = Icons.Rounded.VerticalAlignTop,
            checked = anchor,
            position = position,
            onCheckedChange = {
                anchor = it
                sp.edit().putBoolean(IslandSettings.KEY_ANCHOR_STATUS_BAR, it).apply()
            }
        )
    }

    Spacer(Modifier.height(10.dp))
    SectionTitle("What it shows")
    val categories = LiveEventCategory.values()
    val states = remember { categories.map { mutableStateOf(sp.getBoolean(it.prefKey, true)) } }
    PetalGroup(rowCount = categories.size) { index, position ->
        val c = categories[index]
        var on by states[index]
        PetalSettingsToggleRow(
            title = c.label,
            subtitle = if (master) "Show these on the NowBar" else "Turn on 'Show live events' first",
            icon = Icons.Rounded.Notifications,
            checked = on,
            enabled = master,
            position = position,
            onCheckedChange = {
                on = it
                sp.edit().putBoolean(c.prefKey, it).apply()
            }
        )
    }

    Spacer(Modifier.height(10.dp))
    PetalGroup(rowCount = 1) { _, position ->
        PetalGroupRow(
            icon = Icons.Rounded.PlayArrow,
            title = "Test the NowBar",
            subtitle = "Shows a few sample events so you can check placement",
            position = position,
            onClick = { fireTestEvents(context) }
        )
    }

    if (showA11yDialog) {
        AlertDialog(
            onDismissRequest = { showA11yDialog = false },
            title = { Text("Turn on the status bar NowBar") },
            text = {
                Text(
                    "Android needs you to switch this on yourself: Accessibility → Installed apps → Petal NowBar.\n\n" +
                        "Petal uses it only to draw the NowBar in the status bar. It cannot read your screen, " +
                        "what you type or your passwords, and nothing is sent anywhere.\n\n" +
                        "If the switch is greyed out (apps installed outside the Play Store): App info → ⋮ → " +
                        "Allow restricted settings, then try again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showA11yDialog = false
                    try { context.startActivity(PetalIslandAccessibilityService.settingsIntent()) } catch (_: Throwable) {}
                }) { Text("Open Accessibility settings") }
            },
            dismissButton = { TextButton(onClick = { showA11yDialog = false }) { Text("Not now") } }
        )
    }
}

private class Mode(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

/** Fires a few sample events: a progress card, an ad blocker card with a switch, and a result. */
private fun fireTestEvents(context: Context) {
    val bus = PetalLiveActionBus
    bus.postBackupProgress("Backing up", "Test event", 45)
    bus.postAdBlockState(true, 0, null, onToggle = {}, openSettings = null)
    bus.postSimple(
        LiveEventKind.SCREENSHOT_SAVED, "test_screenshot", "Screenshot saved", "Pictures/Petal",
        action = LiveEventAction("Open", true) {}
    )
}
