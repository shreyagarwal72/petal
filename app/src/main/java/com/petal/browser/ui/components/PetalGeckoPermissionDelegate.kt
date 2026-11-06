package com.petal.browser.ui.components

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
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

    @JvmStatic
    fun clearOrigin(origin: String) {
        activeSensors.remove(origin)
    }

    @JvmStatic
    fun clearAll() {
        activeSensors.clear()
    }
}

/**
 * PetalGeckoPermissionDelegate
 * ─────────────────────────────────────────────────────────────────────────────
 * Universal GeckoSession.PermissionDelegate for Petal Browser tabs.
 * Routes all web content permission requests (Geolocation, Camera, Microphone,
 * Notifications, DRM Protected Media, Storage Access, Autoplay, XR, Local Network)
 * through Petal's unified Live Liquid Glass and Material 3 Expressive overlay interface.
 */
class PetalGeckoPermissionDelegate(
    private val context: Context,
    private val activitySupplier: () -> Activity?
) : GeckoSession.PermissionDelegate {

    private val sp by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val activity = activitySupplier()
        if (activity == null) {
            callback.reject()
            return
        }
        PetalRuntimePermissionBridge.requestPermissions(activity, permissions, callback)
    }

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

        val hasVideo = !video.isNullOrEmpty()
        val hasAudio = !audio.isNullOrEmpty()
        if (!hasVideo && !hasAudio) {
            callback.reject()
            return
        }

        val host = try { Uri.parse(uri).host ?: uri } catch (_: Exception) { uri }
        val origin = try {
            val parsed = Uri.parse(uri)
            val scheme = parsed.scheme ?: "https"
            val authority = parsed.authority ?: host
            "$scheme://$authority"
        } catch (_: Exception) { uri }

        if (hasVideo && hasAudio) {
            val cameraRule = sp.getString("perm_camera_$origin", null)
            val micRule = sp.getString("perm_microphone_$origin", null)

            if ("allow" == cameraRule && "allow" == micRule) {
                PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, true)
                PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, true)
                callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                return
            } else if ("block" == cameraRule || "block" == micRule) {
                PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, false)
                PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, false)
                callback.reject()
                return
            }

            activity.runOnUiThread {
                PetalPermissionDialogBridge.showPermissionPrompt(
                    activity,
                    PetalPermissionType.CAMERA_AND_MICROPHONE,
                    uri,
                    onAllow = { remember ->
                        if (remember) {
                            sp.edit()
                                .putString("perm_camera_$origin", "allow")
                                .putString("perm_microphone_$origin", "allow")
                                .apply()
                        }
                        PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, true)
                        PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, true)
                        callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                    },
                    onDeny = { remember ->
                        if (remember) {
                            sp.edit()
                                .putString("perm_camera_$origin", "block")
                                .putString("perm_microphone_$origin", "block")
                                .apply()
                        }
                        PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, false)
                        PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, false)
                        callback.reject()
                    }
                )
            }
            return
        }

        val permissionType = if (hasVideo) PetalPermissionType.CAMERA else PetalPermissionType.MICROPHONE
        val prefKey = "perm_${permissionType.name.lowercase()}_$origin"
        val savedRule = sp.getString(prefKey, null)

        if ("allow" == savedRule) {
            PetalActiveMediaSensors.setRecording(origin, permissionType, true)
            callback.grant(video?.firstOrNull(), audio?.firstOrNull())
            return
        } else if ("block" == savedRule) {
            PetalActiveMediaSensors.setRecording(origin, permissionType, false)
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
                    PetalActiveMediaSensors.setRecording(origin, permissionType, true)
                    callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                },
                onDeny = { remember ->
                    if (remember) sp.edit().putString(prefKey, "block").apply()
                    PetalActiveMediaSensors.setRecording(origin, permissionType, false)
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
        val origin = try {
            val parsed = Uri.parse(perm.uri)
            val scheme = parsed.scheme ?: "https"
            val authority = parsed.authority ?: host
            "$scheme://$authority"
        } catch (_: Exception) { perm.uri }

        // Explicit autoplay handling
        when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> {
                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                return result
            }
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> {
                val prefKey = "perm_autoplay_audible_$origin"
                val savedRule = sp.getString(prefKey, null)
                if ("allow" == savedRule) {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                } else {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
                return result
            }
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> {
                val drmEnabled = sp.getBoolean("sp_drm", true)
                if (!drmEnabled) {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                } else {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }
                return result
            }
        }

        val (permTypeKey, dialogType) = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "location" to PetalPermissionType.LOCATION
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notification" to PetalPermissionType.NOTIFICATION
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "persistent_storage" to PetalPermissionType.STORAGE
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "storage_access" to PetalPermissionType.STORAGE
            GeckoSession.PermissionDelegate.PERMISSION_XR -> "xr" to PetalPermissionType.XR
            GeckoSession.PermissionDelegate.PERMISSION_TRACKING -> "tracking" to PetalPermissionType.TRACKING
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> "local_device" to PetalPermissionType.LOCAL_NETWORK
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> "local_network" to PetalPermissionType.LOCAL_NETWORK
            else -> {
                // Deny any other unrecognized permission without showing a storage prompt
                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                return result
            }
        }

        val prefKey = "perm_${permTypeKey}_$origin"
        val sessionKey = "perm_session_${permTypeKey}_$origin"
        val savedRule = sp.getString(prefKey, null)
        val sessionRule = sp.getString(sessionKey, null)

        if ("allow" == sessionRule || "allow" == savedRule) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            return result
        } else if ("block" == savedRule) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            return result
        }

        activity.runOnUiThread {
            PetalPermissionDialogBridge.showPermissionPrompt(
                activity,
                dialogType,
                perm.uri,
                onAllow = { remember ->
                    if (remember) {
                        sp.edit().putString(prefKey, "allow").apply()
                    } else {
                        sp.edit().putString(sessionKey, "allow").apply()
                    }
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                },
                onDeny = { remember ->
                    if (remember) sp.edit().putString(prefKey, "block").apply()
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                }
            )
        }
        return result
    }
}
