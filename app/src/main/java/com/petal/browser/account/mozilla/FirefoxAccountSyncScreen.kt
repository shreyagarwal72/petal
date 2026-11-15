package com.petal.browser.account.mozilla

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.petal.browser.account.PetalAccountScreen
import com.petal.browser.compose.home.PetalShortcut

/**
 * Kept as the stable entry point used by the root app overlay and Settings. The page itself is now the
 * unified account screen (profile + Mozilla sign-in/sync + security + data) in [PetalAccountScreen].
 */
@Composable
fun FirefoxAccountSyncScreen(
    backgroundSnapshot: androidx.compose.ui.graphics.ImageBitmap? = null,
    onBack: () -> Unit = {},
    onOpenOAuth: (PetalShortcut) -> Unit = {},
    modifier: Modifier = Modifier
) {
    PetalAccountScreen(
        backgroundSnapshot = backgroundSnapshot,
        onBack = onBack,
        onOpenOAuth = onOpenOAuth,
        modifier = modifier
    )
}
