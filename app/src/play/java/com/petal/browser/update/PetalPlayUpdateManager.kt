package com.petal.browser.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.preference.PreferenceManager
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class PetalPlayUpdateManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PetalPlayUpdate"
        const val UPDATE_REQUEST_CODE = 53012

        @Volatile
        private var instance: PetalPlayUpdateManager? = null

        @JvmStatic
        fun getInstance(context: Context): PetalPlayUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: PetalPlayUpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private var currentActivity: ComponentActivity? = null
    private var isListenerRegistered = false

    private val installStateUpdatedListener = InstallStateUpdatedListener { state: InstallState ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                Log.d(TAG, "Flexible update downloaded successfully")
                currentActivity?.let { activity ->
                    PetalPlayUpdatePopupBridge.showUpdateDownloadedDialog(
                        activity = activity,
                        onRestartNow = {
                            completeUpdate()
                        }
                    )
                }
            }
            InstallStatus.FAILED -> {
                Log.w(TAG, "Flexible update install failed: ${state.installErrorCode()}")
            }
            InstallStatus.CANCELED -> {
                Log.d(TAG, "Flexible update install canceled")
            }
            else -> {
                Log.d(TAG, "Flexible update status: ${state.installStatus()}")
            }
        }
    }

    fun registerListener(activity: ComponentActivity) {
        currentActivity = activity
        if (!isListenerRegistered) {
            try {
                appUpdateManager.registerListener(installStateUpdatedListener)
                isListenerRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register install state listener", e)
            }
        }
    }

    fun unregisterListener() {
        if (isListenerRegistered) {
            try {
                appUpdateManager.unregisterListener(installStateUpdatedListener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister install state listener", e)
            }
            isListenerRegistered = false
        }
        currentActivity = null
    }

    /**
     * Checks for Play Store updates.
     * @param activity ComponentActivity instance for UI lifecycle binding.
     * @param isLaunchCheck true if triggered automatically at app launch.
     */
    fun checkForUpdates(activity: ComponentActivity, isLaunchCheck: Boolean = false) {
        if (isLaunchCheck) {
            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val checkEnabled = sp.getBoolean("sp_check_update_on_launch", true)
            if (!checkEnabled) {
                Log.d(TAG, "Check update on launch is disabled in settings")
                return
            }
        }

        registerListener(activity)

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            handleAppUpdateInfo(activity, appUpdateInfo, isLaunchCheck)
        }.addOnFailureListener { e ->
            Log.w(TAG, "Failed to check Play Store update info: ${e.message}")
            if (!isLaunchCheck) {
                openPlayStore(activity)
            }
        }
    }

    /**
     * Call on Activity onResume to handle pending downloaded updates or in-progress updates.
     */
    fun onResume(activity: ComponentActivity) {
        currentActivity = activity
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                PetalPlayUpdatePopupBridge.showUpdateDownloadedDialog(
                    activity = activity,
                    onRestartNow = {
                        completeUpdate()
                    }
                )
            }
        }
    }

    private fun handleAppUpdateInfo(
        activity: ComponentActivity,
        appUpdateInfo: AppUpdateInfo,
        isLaunchCheck: Boolean
    ) {
        if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
            PetalPlayUpdatePopupBridge.showUpdateDownloadedDialog(
                activity = activity,
                onRestartNow = {
                    completeUpdate()
                }
            )
            return
        }

        if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            val isFlexibleAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            val isImmediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            PetalPlayUpdatePopupBridge.showUpdateAvailableDialog(
                activity = activity,
                onUpdateNow = {
                    startUpdateFlow(activity, appUpdateInfo, isFlexibleAllowed, isImmediateAllowed)
                }
            )
        } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            try {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    UPDATE_REQUEST_CODE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume developer triggered update", e)
            }
        } else {
            if (!isLaunchCheck) {
                Toast.makeText(activity, "Petal is up to date", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startUpdateFlow(
        activity: Activity,
        appUpdateInfo: AppUpdateInfo,
        isFlexibleAllowed: Boolean,
        isImmediateAllowed: Boolean
    ) {
        try {
            val updateType = if (isFlexibleAllowed) AppUpdateType.FLEXIBLE else AppUpdateType.IMMEDIATE
            val updateOptions = AppUpdateOptions.newBuilder(updateType).build()
            val started = appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                updateOptions,
                UPDATE_REQUEST_CODE
            )
            if (!started) {
                openPlayStore(activity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Play Store in-app update flow, opening Play Store", e)
            openPlayStore(activity)
        }
    }

    fun completeUpdate() {
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error completing update", e)
        }
    }

    fun openPlayStore(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
