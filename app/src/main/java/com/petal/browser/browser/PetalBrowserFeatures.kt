package com.petal.browser.browser

import androidx.fragment.app.FragmentActivity
import mozilla.components.browser.state.store.BrowserStore
import androidx.core.app.ActivityCompat
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.session.PictureInPictureFeature
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.tabs.TabsUseCases

/** Android Components browser features that need an Activity and FragmentManager host. */
class PetalBrowserFeatures(private val activity: FragmentActivity) {
    private val store: BrowserStore =
        com.petal.browser.engine.gecko.PetalEngineStore.getStore(activity)

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
        // Suppress stock Mozilla DialogFragments (Prompts, Choices)
        // so only Petal's Live Liquid Glass & Material 3 Expressive overlays / bottom sheets render
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentAttached(
                    fm: androidx.fragment.app.FragmentManager,
                    f: androidx.fragment.app.Fragment,
                    context: android.content.Context
                ) {
                    val name = f.javaClass.name
                    val simpleName = f.javaClass.simpleName
                    if (name.contains("mozilla.components.feature.prompts") ||
                        simpleName.contains("PromptDialog", ignoreCase = true) ||
                        simpleName.contains("ChoiceDialog", ignoreCase = true)
                    ) {
                        try {
                            if (f is androidx.fragment.app.DialogFragment) {
                                f.dismissAllowingStateLoss()
                            }
                        } catch (_: Throwable) {}
                    }
                }
            },
            true
        )
        mediaFullscreen.start()
        prompts.start()
    }

    fun stop() {
        mediaFullscreen.stop()
        prompts.stop()
    }

    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PROMPT_PERMISSIONS -> prompts.onPermissionsResult(permissions, grantResults)
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean =
        prompts.onActivityResult(requestCode, data, resultCode)

    fun onPictureInPictureModeChanged(enabled: Boolean) {
        pictureInPicture.onPictureInPictureModeChanged(enabled)
    }

    private companion object {
        const val REQUEST_PROMPT_PERMISSIONS = 17322
    }
}
