package com.petal.browser.browser

import androidx.fragment.app.FragmentActivity
import mozilla.components.browser.state.store.BrowserStore
import androidx.core.app.ActivityCompat
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.session.PictureInPictureFeature
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.tabs.TabsUseCases

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
    private val prompts = PromptFeature(
        activity = activity,
        store = store,
        fragmentManager = activity.supportFragmentManager,
        tabsUseCases = TabsUseCases(store),
        isSuggestEmailMaskEnabled = { false },
        isEmailMaskFeatureEnabled = { false },
        fileUploadsDirCleaner = com.petal.browser.engine.gecko.PetalEngineStore
            .getFileUploadsDirCleaner(activity),
        onNeedToRequestPermissions = { permissions ->
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_PROMPT_PERMISSIONS)
        },
        androidPhotoPicker = null
    )
    fun start() {
        sitePermissions.start()
        mediaFullscreen.start()
        prompts.start()
    }

    fun stop() {
        sitePermissions.stop()
        mediaFullscreen.stop()
        prompts.stop()
    }

    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_SITE_PERMISSIONS -> sitePermissions.onPermissionsResult(permissions, grantResults)
            REQUEST_PROMPT_PERMISSIONS -> prompts.onPermissionsResult(permissions, grantResults)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean =
        prompts.onActivityResult(requestCode, data, resultCode)

    fun onPictureInPictureModeChanged(enabled: Boolean) {
        pictureInPicture.onPictureInPictureModeChanged(enabled)
    }

    private companion object {
        const val REQUEST_SITE_PERMISSIONS = 17321
        const val REQUEST_PROMPT_PERMISSIONS = 17322
    }
}
