package com.petal.browser.browser

import androidx.fragment.app.FragmentActivity
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.session.PictureInPictureFeature

/** Android Components browser features that need an Activity and FragmentManager host. */
class PetalBrowserFeatures(private val activity: FragmentActivity) {
    private val store: BrowserStore =
        com.petal.browser.engine.gecko.PetalEngineStore.getStore(activity)

    private val mediaFullscreen = MediaSessionFullscreenFeature(activity, store, null)
    private val pictureInPicture = PictureInPictureFeature(store, activity)
    private val promptBridge = com.petal.browser.engine.gecko.PetalBrowserPromptBridge(activity, store)

    fun start() {
        mediaFullscreen.start()
        promptBridge.start()
    }

    fun stop() {
        mediaFullscreen.stop()
        promptBridge.stop()
    }

    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // Handled via PetalRuntimePermissionBridge / PetalBrowserPromptBridge.
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean = false

    fun onPictureInPictureModeChanged(enabled: Boolean) {
        pictureInPicture.onPictureInPictureModeChanged(enabled)
    }
}
