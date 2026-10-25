package com.petal.browser.media.sniffer

import android.content.Context
import com.petal.browser.extensions.PetalBuiltInExtensionManager

/**
 * Installs the passive network-level sniffer early (called from Application start-up).
 *
 * The extension and its native-message bridge are owned by [PetalBuiltInExtensionManager].
 * This installer used to ship its own copy that force-enabled the extension on every launch,
 * racing the manager (which applies the user's on/off toggle) and replacing its message
 * delegate with a narrower one that ignored REQUEST_DOWNLOAD / GET_NATIVE_PLAYER_STATE.
 */
object PetalMediaGrabberInstaller {
    private const val ID = "petal-media-grabber@petalbrowser.app"

    fun install(context: Context) {
        PetalBuiltInExtensionManager.ensureInstalled(context, ID)
    }
}
