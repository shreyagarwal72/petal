package com.petal.browser.browser

import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.feature.tabs.TabsUseCases

/** Android Components browser features that need an Activity and FragmentManager host. */
class PetalBrowserFeatures(private val activity: FragmentActivity) {
    private val store: BrowserStore =
        com.petal.browser.engine.gecko.PetalEngineStore.getStore(activity)

    private val prompts = PromptFeature(
        activity = activity,
        store = store,
        fragmentManager = activity.supportFragmentManager,
        tabsUseCases = TabsUseCases(store),
        fileUploadsDirCleaner =
            com.petal.browser.engine.gecko.PetalEngineStore.getFileUploadsDirCleaner(activity),
        onNeedToRequestPermissions = { permissions ->
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_PROMPT_PERMISSIONS)
        }
    )

    private val sitePermissions = SitePermissionsFeature(
        context = activity,
        fragmentManager = activity.supportFragmentManager,
        onNeedToRequestPermissions = { permissions ->
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_SITE_PERMISSIONS)
        },
        onShouldShowRequestPermissionRationale = activity::shouldShowRequestPermissionRationale,
        store = store
    )

    fun start() {
        prompts.start()
        sitePermissions.start()
    }

    fun stop() {
        prompts.stop()
        sitePermissions.stop()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean =
        prompts.onActivityResult(requestCode, data, resultCode)

    fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PROMPT_PERMISSIONS -> prompts.onPermissionsResult(permissions, grantResults)
            REQUEST_SITE_PERMISSIONS -> sitePermissions.onPermissionsResult(permissions, grantResults)
        }
    }

    fun onBackPressed(): Boolean = prompts.onBackPressed()

    private companion object {
        const val REQUEST_PROMPT_PERMISSIONS = 17320
        const val REQUEST_SITE_PERMISSIONS = 17321
    }
}
