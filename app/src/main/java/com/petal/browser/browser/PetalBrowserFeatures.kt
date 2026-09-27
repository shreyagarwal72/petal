package com.petal.browser.browser

import androidx.fragment.app.FragmentActivity
import mozilla.components.browser.state.store.BrowserStore
import androidx.core.app.ActivityCompat
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.session.PictureInPictureFeature

/** Android Components browser features that need an Activity and FragmentManager host. */
class PetalBrowserFeatures(private val activity: FragmentActivity) {
    private val store: BrowserStore =
        com.petal.browser.engine.gecko.PetalEngineStore.getStore(activity)

    private val sitePermissions = SitePermissionsFeature(
        context = activity,
        fragmentManager = activity.supportFragmentManager,
        onNeedToRequestPermissions = { permissions ->
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_SITE_PERMISSIONS)
        },
        onShouldShowRequestPermissionRationale = activity::shouldShowRequestPermissionRationale,
        store = store
    )

    private val mediaFullscreen = MediaSessionFullscreenFeature(activity, store, null)
    private val pictureInPicture = PictureInPictureFeature(store, activity)
    fun start() {
        sitePermissions.start()
        mediaFullscreen.start()
    }

    fun stop() {
        sitePermissions.stop()
        mediaFullscreen.stop()
    }

    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_SITE_PERMISSIONS -> sitePermissions.onPermissionsResult(permissions, grantResults)
        }
    }

    fun onPictureInPictureModeChanged(enabled: Boolean) {
        pictureInPicture.onPictureInPictureModeChanged(enabled)
    }

    private companion object {
        const val REQUEST_SITE_PERMISSIONS = 17321
    }
}
