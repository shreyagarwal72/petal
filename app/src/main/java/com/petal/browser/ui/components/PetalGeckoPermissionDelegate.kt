package com.petal.browser.ui.components

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.petal.browser.unit.HelperUnit
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active hardware camera & microphone streams across web origins.
 */
object PetalActiveMediaSensors {
    private val activeSensors = ConcurrentHashMap<String, MutableSet<PetalPermissionType>>()

    @JvmStatic
    fun setRecording(origin: String, type: PetalPermissionType, isRecording: Boolean) {
        val set = activeSensors.getOrPut(origin) { Collections.newSetFromMap(ConcurrentHashMap()) }
        if (isRecording) set.add(type) else set.remove(type)
    }

    @JvmStatic
    fun isRecording(origin: String, type: PetalPermissionType): Boolean {
        return activeSensors[origin]?.contains(type) == true
    }

    @JvmStatic
    fun hasAnyActive(origin: String): Boolean {
        return !activeSensors[origin].isNullOrEmpty()
    }
}

/**
 * PetalGeckoPermissionDelegate
 * ─────────────────────────────────────────────────────────────────────────────
 * Universal GeckoSession.PermissionDelegate for Petal Browser tabs.
 * Routes all web content permission requests (Geolocation, Camera, Microphone,
 * Notifications, DRM Protected Media, Storage Access) through Petal's unified
 * Live Liquid Glass and Material 3 Expressive overlay interface.
 */
class PetalGeckoPermissionDelegate(
    private val context: Context,
    private val activitySupplier: () -> Activity?
) : GeckoSession.PermissionDelegate {

    private val sp by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        val activity = activitySupplier()
        if (activity == null) {
            callback.reject()
            return
        }

        val permissionType = if (!video.isNullOrEmpty()) {
            PetalPermissionType.CAMERA
        } else {
            PetalPermissionType.MICROPHONE
        }

        val host = try { Uri.parse(uri).host ?: uri } catch (_: Exception) { uri }
        val origin = try {
            val parsed = Uri.parse(uri)
            val scheme = parsed.scheme ?: "https"
            val authority = parsed.authority ?: host
            "$scheme://$authority"
        } catch (_: Exception) { uri }

        val prefKey = "perm_${permissionType.name.lowercase()}_$origin"
        val savedRule = sp.getString(prefKey, null)

        if ("allow" == savedRule) {
            if (!video.isNullOrEmpty()) {
                HelperUnit.grantPermissionsCamera(activity)
                PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, true)
            }
            if (!audio.isNullOrEmpty()) {
                HelperUnit.grantPermissionsMic(activity)
                PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, true)
            }
            callback.grant(video?.firstOrNull(), audio?.firstOrNull())
            return
        } else if ("block" == savedRule) {
            callback.reject()
            return
        }

        activity.runOnUiThread {
            PetalPermissionDialogBridge.showPermissionPrompt(
                activity,
                permissionType,
                uri,
                onAllow = { remember ->
                    if (remember) sp.edit().putString(prefKey, "allow").apply()
                    if (!video.isNullOrEmpty()) {
                        HelperUnit.grantPermissionsCamera(activity)
                        PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, true)
                    }
                    if (!audio.isNullOrEmpty()) {
                        HelperUnit.grantPermissionsMic(activity)
                        PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, true)
                    }
                    callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                },
                onDeny = { remember ->
                    if (remember) sp.edit().putString(prefKey, "block").apply()
                    callback.reject()
                }
            )
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        val result = GeckoResult<Int>()
        val activity = activitySupplier()
        if (activity == null) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            return result
        }

        val host = try { Uri.parse(perm.uri).host ?: perm.uri } catch (_: Exception) { perm.uri }
        val permType = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "location"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notification"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "media_key"
            else -> "other_${perm.permission}"
        }

        val origin = try {
            val parsed = Uri.parse(perm.uri)
            val scheme = parsed.scheme ?: "https"
            val authority = parsed.authority ?: host
            "$scheme://$authority"
        } catch (_: Exception) { perm.uri }

        val prefKey = "perm_${permType}_$origin"
        val sessionKey = "perm_session_${permType}_$origin"
        val savedRule = sp.getString(prefKey, null)
        val sessionRule = sp.getString(sessionKey, null)

        if ("allow" == sessionRule || "allow" == savedRule) {
            if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION) {
                HelperUnit.grantPermissionsLoc(activity)
            }
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            return result
        } else if ("block" == savedRule) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            return result
        }

        when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> {
                activity.runOnUiThread {
                    PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        PetalPermissionType.LOCATION,
                        perm.uri,
                        onAllow = { remember ->
                            if (remember) {
                                sp.edit().putString(prefKey, "allow").apply()
                            } else {
                                sp.edit().putString(sessionKey, "allow").apply()
                            }
                            HelperUnit.grantPermissionsLoc(activity)
                            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                        },
                        onDeny = { remember ->
                            if (remember) sp.edit().putString(prefKey, "block").apply()
                            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                        }
                    )
                }
            }
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> {
                activity.runOnUiThread {
                    PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        PetalPermissionType.NOTIFICATION,
                        perm.uri,
                        onAllow = { remember ->
                            if (remember) sp.edit().putString(prefKey, "allow").apply()
                            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                        },
                        onDeny = { remember ->
                            if (remember) sp.edit().putString(prefKey, "block").apply()
                            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                        }
                    )
                }
            }
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> {
                val drmEnabled = sp.getBoolean("sp_drm", true)
                if (!drmEnabled) {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                } else {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }
            }
            else -> {
                activity.runOnUiThread {
                    PetalPermissionDialogBridge.showPermissionPrompt(
                        activity,
                        PetalPermissionType.STORAGE,
                        perm.uri,
                        onAllow = { remember ->
                            if (remember) sp.edit().putString(prefKey, "allow").apply()
                            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                        },
                        onDeny = { remember ->
                            if (remember) sp.edit().putString(prefKey, "block").apply()
                            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                        }
                    )
                }
            }
        }
        return result
    }
}
