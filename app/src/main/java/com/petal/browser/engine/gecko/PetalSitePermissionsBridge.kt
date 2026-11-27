package com.petal.browser.engine.gecko

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.petal.browser.ui.components.PetalActiveMediaSensors
import com.petal.browser.ui.components.PetalPermissionDialogBridge
import com.petal.browser.ui.components.PetalPermissionType
import com.petal.browser.ui.components.PetalRuntimePermissionBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.concept.engine.permission.SitePermissionsStorage.Permission
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import mozilla.components.feature.sitepermissions.SitePermissionsRules
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalSitePermissionsBridge
 * ─────────────────────────────────────────────────────────────────────────────
 * Official Mozilla Android Components 159.0.1 Site Permissions integration.
 * Connects [OnDiskSitePermissionsStorage] and [SitePermissionsRules] with Petal's
 * Material 3 Expressive and Live Liquid Glass permission prompter sheets.
 */
object PetalSitePermissionsBridge {

    private const val PREF_MIGRATED = "sp_site_permissions_migrated_v1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var storageInstance: OnDiskSitePermissionsStorage? = null

    // Session grants for temporary non-remembered decisions or incognito tabs:
    // Key format: "$isPrivate#$origin#$permKey" to prevent private-tab grants from leaking into normal tabs.
    private val sessionGrants = ConcurrentHashMap<String, SitePermissions.Status>()

    data class SitePermissionSummary(
        val permission: Permission,
        val status: SitePermissions.Status
    )

    suspend fun getSitePermissionsSummary(
        context: Context,
        url: String,
        isPrivate: Boolean
    ): List<SitePermissionSummary> {
        if (url.isBlank() || (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))) {
            return emptyList()
        }
        val origin = extractOrigin(url)
        val stored = if (isPrivate) null else {
            try {
                getStorage(context).findSitePermissionsBy(origin = origin, includeTemporary = false, private = false)
            } catch (_: Throwable) {
                null
            }
        }
        val prefix = "$isPrivate#$origin#"
        val checks = listOf(
            "camera" to Permission.CAMERA,
            "microphone" to Permission.MICROPHONE,
            "location" to Permission.LOCATION,
            "notification" to Permission.NOTIFICATION,
            "persistent_storage" to Permission.LOCAL_STORAGE,
            "storage_access" to Permission.STORAGE_ACCESS,
            "local_device" to Permission.LOCAL_DEVICE_ACCESS,
            "local_network" to Permission.LOCAL_NETWORK_ACCESS
        )
        val out = ArrayList<SitePermissionSummary>()
        for ((key, perm) in checks) {
            val status = sessionGrants["$prefix$key"]
                ?: stored?.get(perm)
                ?: SitePermissions.Status.NO_DECISION
            if (status != SitePermissions.Status.NO_DECISION) {
                out.add(SitePermissionSummary(perm, status))
            }
        }
        return out
    }

    fun clearSessionGrants(isPrivate: Boolean? = null, origin: String? = null) {
        if (isPrivate == null && origin == null) {
            sessionGrants.clear()
            return
        }
        sessionGrants.keys.removeIf { key ->
            val parts = key.split("#")
            val keyPrivate = parts.getOrNull(0)?.toBooleanStrictOrNull()
            val keyOrigin = parts.getOrNull(1)
            (isPrivate == null || keyPrivate == isPrivate) && (origin == null || keyOrigin == origin)
        }
    }

    fun getStorage(context: Context): OnDiskSitePermissionsStorage {
        return storageInstance ?: synchronized(this) {
            storageInstance ?: OnDiskSitePermissionsStorage(context.applicationContext, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)).also {
                storageInstance = it
                migrateLegacyPreferences(context.applicationContext, it)
            }
        }
    }

    val defaultRules = SitePermissionsRules(
        camera = SitePermissionsRules.Action.ASK_TO_ALLOW,
        location = SitePermissionsRules.Action.ASK_TO_ALLOW,
        notification = SitePermissionsRules.Action.ASK_TO_ALLOW,
        microphone = SitePermissionsRules.Action.ASK_TO_ALLOW,
        autoplayAudible = SitePermissionsRules.AutoplayAction.BLOCKED,
        autoplayInaudible = SitePermissionsRules.AutoplayAction.ALLOWED,
        persistentStorage = SitePermissionsRules.Action.ASK_TO_ALLOW,
        mediaKeySystemAccess = SitePermissionsRules.Action.ASK_TO_ALLOW,
        crossOriginStorageAccess = SitePermissionsRules.Action.ASK_TO_ALLOW,
        localDeviceAccess = SitePermissionsRules.Action.ASK_TO_ALLOW,
        localNetworkAccess = SitePermissionsRules.Action.ASK_TO_ALLOW
    )

    private fun extractOrigin(uriStr: String): String {
        return try {
            val parsed = Uri.parse(uriStr)
            val scheme = parsed.scheme ?: "https"
            val host = parsed.host ?: uriStr
            val port = parsed.port
            if (port > 0 && port != 80 && port != 443) "$scheme://$host:$port" else "$scheme://$host"
        } catch (_: Throwable) {
            uriStr
        }
    }

    private fun migrateLegacyPreferences(context: Context, storage: OnDiskSitePermissionsStorage) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        if (sp.getBoolean(PREF_MIGRATED, false)) return

        scope.launch {
            try {
                val allEntries = sp.all
                for ((key, value) in allEntries) {
                    if (key.startsWith("perm_") && value is String) {
                        val status = when (value) {
                            "allow" -> SitePermissions.Status.ALLOWED
                            "block" -> SitePermissions.Status.BLOCKED
                            else -> SitePermissions.Status.NO_DECISION
                        }
                        if (status != SitePermissions.Status.NO_DECISION) {
                            val rem = key.removePrefix("perm_")
                            val firstUnder = rem.indexOf('_')
                            if (firstUnder > 0) {
                                val typeStr = rem.substring(0, firstUnder)
                                val origin = rem.substring(firstUnder + 1)
                                val permission = when (typeStr) {
                                    "camera" -> Permission.CAMERA
                                    "microphone", "mic" -> Permission.MICROPHONE
                                    "location" -> Permission.LOCATION
                                    "notification" -> Permission.NOTIFICATION
                                    "persistent_storage", "storage" -> Permission.LOCAL_STORAGE
                                    "storage_access" -> Permission.STORAGE_ACCESS
                                    "media_key_system_access", "drm" -> Permission.MEDIA_KEY_SYSTEM_ACCESS
                                    else -> null
                                }
                                if (permission != null && origin.isNotBlank()) {
                                    try {
                                        val existing = storage.findSitePermissionsBy(origin, false, false) ?: SitePermissions(origin)
                                        val updated = when (permission) {
                                            Permission.CAMERA -> existing.copy(camera = status)
                                            Permission.MICROPHONE -> existing.copy(microphone = status)
                                            Permission.LOCATION -> existing.copy(location = status)
                                            Permission.NOTIFICATION -> existing.copy(notification = status)
                                            Permission.LOCAL_STORAGE -> existing.copy(localStorage = status)
                                            Permission.STORAGE_ACCESS -> existing.copy(crossOriginStorageAccess = status)
                                            Permission.MEDIA_KEY_SYSTEM_ACCESS -> existing.copy(mediaKeySystemAccess = status)
                                            else -> existing
                                        }
                                        storage.update(updated, false)
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                }
                sp.edit().putBoolean(PREF_MIGRATED, true).apply()
            } catch (_: Throwable) {}
        }
    }

    fun handleAndroidPermissionsRequest(
        activitySupplier: () -> Activity?,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback
    ) {
        val act = activitySupplier()
        if (act != null) {
            PetalRuntimePermissionBridge.requestPermissions(act, permissions, callback)
        } else {
            callback.reject()
        }
    }

    fun handleMediaPermissionRequest(
        context: Context,
        activitySupplier: () -> Activity?,
        isPrivate: Boolean,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback
    ) {
        val act = activitySupplier()
        if (act == null) {
            callback.reject()
            return
        }

        val hasVideo = !video.isNullOrEmpty()
        val hasAudio = !audio.isNullOrEmpty()
        if (!hasVideo && !hasAudio) {
            callback.reject()
            return
        }

        val origin = extractOrigin(uri)
        val storage = getStorage(context)

        scope.launch {
            try {
            val sitePerms = storage.findSitePermissionsBy(origin = origin, includeTemporary = false, private = isPrivate)
            val sessionCam = sessionGrants["$isPrivate#$origin#camera"]
            val sessionMic = sessionGrants["$isPrivate#$origin#microphone"]

            val camStatus = sessionCam ?: sitePerms?.camera ?: SitePermissions.Status.NO_DECISION
            val micStatus = sessionMic ?: sitePerms?.microphone ?: SitePermissions.Status.NO_DECISION

            if (hasVideo && hasAudio) {
                if (camStatus == SitePermissions.Status.ALLOWED && micStatus == SitePermissions.Status.ALLOWED) {
                    PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, true)
                    PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, true)
                    act.runOnUiThread { callback.grant(video?.firstOrNull(), audio?.firstOrNull()) }
                    return@launch
                } else if (camStatus == SitePermissions.Status.BLOCKED || micStatus == SitePermissions.Status.BLOCKED) {
                    PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, false)
                    PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, false)
                    act.runOnUiThread { callback.reject() }
                    return@launch
                }

                act.runOnUiThread {
                    PetalPermissionDialogBridge.showPermissionPrompt(
                        act,
                        PetalPermissionType.CAMERA_AND_MICROPHONE,
                        uri,
                        onAllow = { remember ->
                            if (remember && !isPrivate) {
                                scope.launch {
                                    val current = storage.findSitePermissionsBy(origin = origin, includeTemporary = false, private = false) ?: SitePermissions(origin)
                                    storage.update(current.copy(camera = SitePermissions.Status.ALLOWED, microphone = SitePermissions.Status.ALLOWED), false)
                                }
                            } else {
                                sessionGrants["$isPrivate#$origin#camera"] = SitePermissions.Status.ALLOWED
                                sessionGrants["$isPrivate#$origin#microphone"] = SitePermissions.Status.ALLOWED
                            }
                            PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, true)
                            PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, true)
                            act.runOnUiThread { callback.grant(video?.firstOrNull(), audio?.firstOrNull()) }
                        },
                        onDeny = { remember ->
                            val newCam = if (camStatus == SitePermissions.Status.ALLOWED) SitePermissions.Status.ALLOWED else SitePermissions.Status.BLOCKED
                            val newMic = if (micStatus == SitePermissions.Status.ALLOWED) SitePermissions.Status.ALLOWED else SitePermissions.Status.BLOCKED
                            if (remember && !isPrivate) {
                                scope.launch {
                                    val current = storage.findSitePermissionsBy(origin = origin, includeTemporary = false, private = false) ?: SitePermissions(origin)
                                    storage.update(current.copy(camera = newCam, microphone = newMic), false)
                                }
                            } else {
                                if (camStatus != SitePermissions.Status.ALLOWED) {
                                    sessionGrants["$isPrivate#$origin#camera"] = SitePermissions.Status.BLOCKED
                                }
                                if (micStatus != SitePermissions.Status.ALLOWED) {
                                    sessionGrants["$isPrivate#$origin#microphone"] = SitePermissions.Status.BLOCKED
                                }
                            }
                            PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.CAMERA, false)
                            PetalActiveMediaSensors.setRecording(origin, PetalPermissionType.MICROPHONE, false)
                            act.runOnUiThread { callback.reject() }
                        }
                    )
                }
                return@launch
            }

            val permType = if (hasVideo) PetalPermissionType.CAMERA else PetalPermissionType.MICROPHONE
            val permKey = if (hasVideo) "camera" else "microphone"
            val status = if (hasVideo) camStatus else micStatus

            if (status == SitePermissions.Status.ALLOWED) {
                PetalActiveMediaSensors.setRecording(origin, permType, true)
                act.runOnUiThread { callback.grant(video?.firstOrNull(), audio?.firstOrNull()) }
                return@launch
            } else if (status == SitePermissions.Status.BLOCKED) {
                PetalActiveMediaSensors.setRecording(origin, permType, false)
                act.runOnUiThread { callback.reject() }
                return@launch
            }

            act.runOnUiThread {
                PetalPermissionDialogBridge.showPermissionPrompt(
                    act,
                    permType,
                    uri,
                    onAllow = { remember ->
                        if (remember && !isPrivate) {
                            scope.launch {
                                val current = storage.findSitePermissionsBy(origin, false, false) ?: SitePermissions(origin)
                                val updated = if (hasVideo) current.copy(camera = SitePermissions.Status.ALLOWED) else current.copy(microphone = SitePermissions.Status.ALLOWED)
                                storage.update(updated, false)
                            }
                        } else {
                            sessionGrants["$isPrivate#$origin#$permKey"] = SitePermissions.Status.ALLOWED
                        }
                        PetalActiveMediaSensors.setRecording(origin, permType, true)
                        act.runOnUiThread { callback.grant(video?.firstOrNull(), audio?.firstOrNull()) }
                    },
                    onDeny = { remember ->
                        if (remember && !isPrivate) {
                            scope.launch {
                                val current = storage.findSitePermissionsBy(origin, false, false) ?: SitePermissions(origin)
                                val updated = if (hasVideo) current.copy(camera = SitePermissions.Status.BLOCKED) else current.copy(microphone = SitePermissions.Status.BLOCKED)
                                storage.update(updated, false)
                            }
                        } else {
                            sessionGrants["$isPrivate#$origin#$permKey"] = SitePermissions.Status.BLOCKED
                        }
                        PetalActiveMediaSensors.setRecording(origin, permType, false)
                        act.runOnUiThread { callback.reject() }
                    }
                )
            }
            } catch (t: Throwable) {
                android.util.Log.w("PetalSitePermissions", "Media permission lookup failed: ${t.message}")
                act.runOnUiThread { callback.reject() }
            }
        }
    }

    fun handleContentPermissionRequest(
        context: Context,
        activitySupplier: () -> Activity?,
        isPrivate: Boolean,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int> {
        val result = GeckoResult<Int>()
        val act = activitySupplier()
        if (act == null) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            return result
        }

        val origin = extractOrigin(perm.uri)
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        // Autoplay and DRM bypass rules
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
                result.complete(if (drmEnabled) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                return result
            }
        }

        val (permKey, dialogType, acPermission) = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> Triple("location", PetalPermissionType.LOCATION, Permission.LOCATION)
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> Triple("notification", PetalPermissionType.NOTIFICATION, Permission.NOTIFICATION)
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> Triple("persistent_storage", PetalPermissionType.STORAGE, Permission.LOCAL_STORAGE)
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> Triple("storage_access", PetalPermissionType.STORAGE, Permission.STORAGE_ACCESS)
            GeckoSession.PermissionDelegate.PERMISSION_XR -> Triple("xr", PetalPermissionType.XR, null)
            GeckoSession.PermissionDelegate.PERMISSION_TRACKING -> Triple("tracking", PetalPermissionType.TRACKING, null)
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> Triple("local_device", PetalPermissionType.LOCAL_NETWORK, Permission.LOCAL_DEVICE_ACCESS)
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> Triple("local_network", PetalPermissionType.LOCAL_NETWORK, Permission.LOCAL_NETWORK_ACCESS)
            else -> {
                result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                return result
            }
        }

        val storage = getStorage(context)
        val sessionStatus = sessionGrants["$isPrivate#$origin#$permKey"]

        scope.launch {
            try {
            val sitePerms = storage.findSitePermissionsBy(origin = origin, includeTemporary = false, private = isPrivate)
            val storedStatus = acPermission?.let { sitePerms?.get(it) } ?: SitePermissions.Status.NO_DECISION
            val effectiveStatus = sessionStatus ?: storedStatus

            if (effectiveStatus == SitePermissions.Status.ALLOWED) {
                act.runOnUiThread { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) }
                return@launch
            } else if (effectiveStatus == SitePermissions.Status.BLOCKED) {
                act.runOnUiThread { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
                return@launch
            }

            act.runOnUiThread {
                PetalPermissionDialogBridge.showPermissionPrompt(
                    act,
                    dialogType,
                    perm.uri,
                    onAllow = { remember ->
                        if (remember && !isPrivate && acPermission != null) {
                            scope.launch {
                                val current = storage.findSitePermissionsBy(origin = origin, includeTemporary = false, private = false) ?: SitePermissions(origin)
                                val updated = when (acPermission) {
                                    Permission.LOCATION -> current.copy(location = SitePermissions.Status.ALLOWED)
                                    Permission.NOTIFICATION -> current.copy(notification = SitePermissions.Status.ALLOWED)
                                    Permission.LOCAL_STORAGE -> current.copy(localStorage = SitePermissions.Status.ALLOWED)
                                    Permission.STORAGE_ACCESS -> current.copy(crossOriginStorageAccess = SitePermissions.Status.ALLOWED)
                                    Permission.LOCAL_DEVICE_ACCESS -> current.copy(localDeviceAccess = SitePermissions.Status.ALLOWED)
                                    Permission.LOCAL_NETWORK_ACCESS -> current.copy(localNetworkAccess = SitePermissions.Status.ALLOWED)
                                    else -> current
                                }
                                storage.update(updated, false)
                            }
                        } else {
                            sessionGrants["$isPrivate#$origin#$permKey"] = SitePermissions.Status.ALLOWED
                        }
                        act.runOnUiThread { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) }
                    },
                    onDeny = { remember ->
                        if (remember && !isPrivate && acPermission != null) {
                            scope.launch {
                                val current = storage.findSitePermissionsBy(origin = origin, includeTemporary = false, private = false) ?: SitePermissions(origin)
                                val updated = when (acPermission) {
                                    Permission.LOCATION -> current.copy(location = SitePermissions.Status.BLOCKED)
                                    Permission.NOTIFICATION -> current.copy(notification = SitePermissions.Status.BLOCKED)
                                    Permission.LOCAL_STORAGE -> current.copy(localStorage = SitePermissions.Status.BLOCKED)
                                    Permission.STORAGE_ACCESS -> current.copy(crossOriginStorageAccess = SitePermissions.Status.BLOCKED)
                                    Permission.LOCAL_DEVICE_ACCESS -> current.copy(localDeviceAccess = SitePermissions.Status.BLOCKED)
                                    Permission.LOCAL_NETWORK_ACCESS -> current.copy(localNetworkAccess = SitePermissions.Status.BLOCKED)
                                    else -> current
                                }
                                storage.update(updated, false)
                            }
                        } else {
                            sessionGrants["$isPrivate#$origin#$permKey"] = SitePermissions.Status.BLOCKED
                        }
                        act.runOnUiThread { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
                    }
                )
            }
            } catch (t: Throwable) {
                android.util.Log.w("PetalSitePermissions", "Content permission lookup failed: ${t.message}")
                act.runOnUiThread { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
            }
        }

        return result
    }
}
