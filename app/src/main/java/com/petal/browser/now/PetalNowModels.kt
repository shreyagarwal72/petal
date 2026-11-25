package com.petal.browser.now

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.petal.browser.live.LiveEventCategory

enum class PetalNowDisplayState {
    COLLAPSED,
    COMPACT,
    EXPANDED,
}

enum class PetalNowCardKind {
    ADBLOCK,
    MEDIA,
    DOWNLOAD,
    BACKUP,
    SYNC,
    EXTENSION,
    UPDATE,
    PERMISSION,
    CREDENTIAL,
    TAB_STATE,
    NETWORK,
    FILE_VIEWER,
    ACHIEVEMENT,
    TOOL,
    RESTING_HOME,
    GENERIC,
}

enum class PetalNowAccentRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL,
}

enum class PetalNowBadgeShapeKind {
    CIRCLE,
    COOKIE_7,
    FLOWER,
    SUNNY,
    BUN,
    GEM,
    CLOVER_4,
    GHOSTISH,
    PUFFY,
}

@Immutable
data class PetalNowAction(
    val id: String,
    val label: String,
    val primary: Boolean = false,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

@Immutable
data class PetalNowToggle(
    val label: String,
    val subtitle: String? = null,
    val checked: Boolean,
    val onChange: (Boolean) -> Unit,
)

@Immutable
data class PetalNowProgress(
    val fraction: Float?,
    val percent: Int = -1,
    val isWavy: Boolean = false,
    val isPaused: Boolean = false,
    val isIndeterminate: Boolean = false,
    val trailingLabel: String? = null,
)

@Immutable
data class PetalNowStatChip(
    val label: String,
    val value: String,
)

@Immutable
data class PetalNowEntry(
    val slotKey: String,
    val familyKey: String,
    val kind: PetalNowCardKind,
    val category: LiveEventCategory,
    val categoryTag: String,
    val priority: Int,
    val title: String,
    val compactText: String,
    val collapsedBadgeText: String? = null,
    val subtitle: String = "",
    val icon: ImageVector,
    val badgeShape: PetalNowBadgeShapeKind = PetalNowBadgeShapeKind.CIRCLE,
    val secondaryBadgeShape: PetalNowBadgeShapeKind = badgeShape,
    val stateFlipActive: Boolean? = null,
    val accentRole: PetalNowAccentRole = PetalNowAccentRole.PRIMARY,
    val accentOverrideColor: Color? = null,
    val albumArt: Bitmap? = null,
    val isAudioPlaying: Boolean = false,
    val progress: PetalNowProgress? = null,
    val toggle: PetalNowToggle? = null,
    val stats: List<PetalNowStatChip> = emptyList(),
    val actions: List<PetalNowAction> = emptyList(),
    val canSwipeDismiss: Boolean = true,
    val rawBusId: String? = null,
    val defaultState: PetalNowDisplayState = PetalNowDisplayState.COMPACT,
    val isHeroResting: Boolean = false,
)

@Immutable
data class PetalNowSlots(
    val primary: PetalNowEntry? = null,
    val satellite: PetalNowEntry? = null,
) {
    val isEmpty: Boolean get() = primary == null
}
