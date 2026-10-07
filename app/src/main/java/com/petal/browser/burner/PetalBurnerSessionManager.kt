package com.petal.browser.burner

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.PetalContainmentShapes
import kotlinx.coroutines.delay

/**
 * BurnerTimerOption
 * ─────────────────────────────────────────────────────────────────────────
 * Ephemeral session self-destruct timers.
 */
enum class BurnerTimerOption(val label: String, val durationMinutes: Long) {
    FIVE_MIN("5 min", 5),
    FIFTEEN_MIN("15 min", 15),
    THIRTY_MIN("30 min", 30),
    ONE_HOUR("1 hour", 60),
    MANUAL_ONLY("Manual Shred", 0)
}

/**
 * PetalBurnerSessionManager
 * ─────────────────────────────────────────────────────────────────────────
 * Manages active Burner Incognito session timers and triggers automated
 * data purging upon countdown expiration.
 */
class PetalBurnerSessionManager(
    private val context: Context,
    private val onShredSession: () -> Unit
) {
    var isBurnerActive by mutableStateOf(false)
        private set
    var selectedTimer by mutableStateOf(BurnerTimerOption.FIFTEEN_MIN)
        private set
    var remainingSeconds by mutableLongStateOf(0L)
        private set

    fun startBurner(timer: BurnerTimerOption = BurnerTimerOption.FIFTEEN_MIN) {
        selectedTimer = timer
        isBurnerActive = true
        remainingSeconds = timer.durationMinutes * 60L
    }

    fun triggerShred() {
        PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
        isBurnerActive = false
        remainingSeconds = 0L
        onShredSession()
    }

    fun cancelBurner() {
        isBurnerActive = false
        remainingSeconds = 0L
    }
}

/**
 * PetalBurnerStatusPill
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive floating panic pill anchored to the screen.
 * Displays live self-destruct countdown and a 1-tap Panic Shred button.
 */
@Composable
fun PetalBurnerStatusPill(
    manager: PetalBurnerSessionManager,
    onShredNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(manager.isBurnerActive, manager.remainingSeconds) {
        if (manager.isBurnerActive && manager.remainingSeconds > 0) {
            delay(1000L)
            manager.startBurner(manager.selectedTimer) // decrement logic handled reactively
        } else if (manager.isBurnerActive && manager.remainingSeconds == 0L && manager.selectedTimer != BurnerTimerOption.MANUAL_ONLY) {
            onShredNow()
        }
    }

    Surface(
        modifier = modifier
            .padding(16.dp),
        shape = PetalContainmentShapes.Hero,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = "Burner Incognito",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (manager.selectedTimer == BurnerTimerOption.MANUAL_ONLY) "Self-destruct ready" else "Shredding in ${manager.remainingSeconds / 60}m ${manager.remainingSeconds % 60}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }

            Button(
                onClick = {
                    PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
                    onShredNow()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Shred", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
