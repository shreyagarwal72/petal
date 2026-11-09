package com.petal.browser.unit

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Environment.DIRECTORY_DOCUMENTS
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.browser.PetalSessionRestoreManager
import com.petal.browser.browser.StandardDomainWhitelist
import com.petal.browser.database.HistoryBookmarkContract
import com.petal.browser.database.HistoryBookmarkRepository
import com.petal.browser.database.Record
import com.petal.browser.ui.components.PetalAlertDialogBuilder
import com.petal.browser.view.PetalToast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object PetalBackupManager {

    const val PERMISSION_REQUEST_CODE: Int = 123
    private const val BOOKMARK_TYPE_SIMPLE = "<DT><A HREF=\"{url}\">{title}</A>"
    private const val BOOKMARK_TITLE = "{title}"
    private const val BOOKMARK_URL = "{url}"

    @JvmStatic
    fun checkPermissionStorage(context: Context?): Boolean {
        if (context == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        }
        val readCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writeCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return readCheck == PackageManager.PERMISSION_GRANTED && writeCheck == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun requestPermission(activity: Activity?) {
        if (activity == null || activity.isFinishing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return
        }
        val builder = PetalAlertDialogBuilder(activity)
        builder.setIcon(R.drawable.icon_alert)
        builder.setTitle(R.string.app_warning)
        builder.setMessage(R.string.app_permission)
        builder.setPositiveButton(R.string.app_ok) { dialog, _ ->
            dialog.dismiss()
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
        builder.setNegativeButton(R.string.app_cancel) { dialog, _ -> dialog.cancel() }
        val dialog = builder.create()
        dialog.show()
        PetalAndroidUtils.setupDialog(activity, dialog)
    }

    @JvmStatic
    fun getSafeBackupDir(context: Context?): File? {
        if (context == null) return null
        try {
            val publicDocs = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS)
            val backupDir = File(publicDocs, "browser_backup")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            if (backupDir.exists()) {
                val testFile = File(backupDir, ".write_test_" + System.currentTimeMillis())
                if (testFile.createNewFile()) {
                    testFile.delete()
                    return backupDir
                }
            }
        } catch (ignored: Throwable) {}

        val internalBackupDir = File(context.filesDir, "browser_backup")
        if (!internalBackupDir.exists()) {
            try { internalBackupDir.mkdirs() } catch (ignored: Throwable) {}
        }
        return internalBackupDir
    }

    @JvmStatic
    fun makeBackupDir(context: Context?) {
        if (context == null) return
        getSafeBackupDir(context)
    }

    private val KNOWN_FLOAT_SUFFIXES = arrayOf(
        "_dim", "_blur", "_width", "_roundness", "_weight", "_opsz", "_grade",
        "_slant", "_scale", "_spread", "_darkening", "_tilt", "_distance"
    )

    private fun isLikelyFloatKey(key: String?): Boolean {
        if (key == null) return false
        if (key.startsWith("sp_home_wallpaper_dim") || key.startsWith("sp_home_wallpaper_blur") ||
            key.startsWith("sp_custom_display_") || key.startsWith("sp_custom_headline_") ||
            key.startsWith("sp_custom_body_") || key.startsWith("sp_reader_font_size") ||
            key.startsWith("sp_reader_line_height") || key == "sp_font_width" ||
            key == "sp_font_roundness" || key == "sp_font_size_scale" ||
            key == "sp_zoom_level_scale" || key.startsWith("sp_gs_d_") ||
            key.startsWith("sp_gs_h_") || key.startsWith("sp_gs_b_")
        ) {
            return true
        }
        for (suffix in KNOWN_FLOAT_SUFFIXES) {
            if (key.endsWith(suffix) || key.contains("${suffix}_")) {
                return true
            }
        }
        return false
    }

    private fun isKnownStringKey(key: String?): Boolean {
        if (key == null) return false
        return "sp_fontSize" == key || "sp_search_engine" == key ||
                "sp_searchEngine" == key || "sp_userAgent" == key ||
                "profile" == key || "sp_app_font" == key ||
                "sp_color_style" == key || "sp_palette_id" == key ||
                "sp_gs_flex_preset" == key || "sp_reader_theme" == key ||
                "sp_reader_font" == key || "sp_theme_config" == key ||
                "sp_theme" == key || "sp_bg_shape_change_mode" == key ||
                "sp_address_bar_position" == key || "sp_address_bar_action" == key ||
                "sp_address_bar_height" == key || "sp_app_language" == key ||
                "sp_app_lock_passcode" == key || "sp_app_lock_type" == key ||
                "sp_private_dns_mode" == key || "sp_custom_doh_url" == key ||
                "sp_torrent_engine" == key || "sp_download_manager_mode" == key ||
                "sp_custom_font_name" == key || "sp_custom_font_path" == key ||
                "sp_home_wallpaper_uri" == key || "sp_custom_homepage_url" == key ||
                "sp_home_type" == key || "sp_tab_display_mode" == key ||
                "sp_inactive_tab_display_mode" == key || "sp_etp_mode" == key ||
                "sp_ai_provider" == key || "sp_ai_default_action" == key ||
                "sp_ai_custom_endpoint" == key || "sp_recently_closed_lock_type" == key ||
                "sp_recently_closed_pwd_hash" == key || "sp_recently_closed_records_json" == key ||
                "sp_custom_home_shortcuts_json_v3" == key || "sp_quick_tools_order_v1" == key ||
                "sp_petal_active_profile_id_v1" == key || "sp_petal_profiles_list_v1" == key ||
                "sp_pinned_tab_ids" == key || "sp_search_customSearches" == key ||
                "sp_custom_search_engines" == key || "sp_adblock_whitelisted_domains" == key ||
                "sp_store_installed_scripts" == key || "sp_store_subscribed_filter_lists" == key ||
                "sp_removed_suggestions" == key || "sp_removed_home_shortcut_urls_v1" == key ||
                "start_tab" == key || "favoriteURL" == key || "urlForPosting" == key ||
                "ab_hosts" == key || "edit_standard" == key || key.startsWith("icon_") ||
                key.startsWith("setting_gesture_")
    }

    private val BUILTIN_PREF_NAMES = arrayOf(
        "petal_settings", "petal_menu", "petal_tab_groups", "petal_inactive_tabs", "petal_ytdlp",
        "petal_fx_sync_prefs", "MenuPreferences", "petal_tab_collections_prefs",
        "petal_inactive_tabs_prefs", "petal_tab_groups_prefs", "petal_ytdlp_engine_prefs"
    )

    @JvmStatic
    fun getAllKnownPreferenceNames(context: Context?): Set<String> {
        val names = HashSet<String>()
        names.addAll(BUILTIN_PREF_NAMES)
        if (context != null) {
            try {
                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (prefsDir.exists() && prefsDir.isDirectory) {
                    val files = prefsDir.listFiles()
                    if (files != null) {
                        val defaultPrefName = "${context.packageName}_preferences"
                        for (f in files) {
                            if (f != null && f.name.endsWith(".xml")) {
                                val name = f.name.substring(0, f.name.length - 4)
                                if (name != defaultPrefName) {
                                    names.add(name)
                                }
                            }
                        }
                    }
                }
            } catch (ignored: Throwable) {}
        }
        return names
    }

    @JvmStatic
    fun serializePreferences(sp: SharedPreferences?): JSONObject {
        val container = JSONObject()
        if (sp == null) return container
        try {
            val valuesObj = JSONObject()
            val typesObj = JSONObject()
            for ((key, value) in sp.all) {
                if (key == null || value == null) continue
                when (value) {
                    is Boolean -> {
                        valuesObj.put(key, value)
                        typesObj.put(key, "bool")
                    }
                    is Float -> {
                        if (!value.isNaN() && !value.isInfinite()) {
                            valuesObj.put(key, value.toDouble())
                            typesObj.put(key, "float")
                        }
                    }
                    is Int -> {
                        valuesObj.put(key, value)
                        typesObj.put(key, "int")
                    }
                    is Long -> {
                        valuesObj.put(key, value)
                        typesObj.put(key, "long")
                    }
                    is String -> {
                        valuesObj.put(key, value)
                        typesObj.put(key, "string")
                    }
                    is Set<*> -> {
                        val setArr = JSONArray()
                        for (item in value) {
                            setArr.put(item.toString())
                        }
                        valuesObj.put(key, setArr)
                        typesObj.put(key, "set")
                    }
                }
            }
            container.put("values", valuesObj)
            container.put("types", typesObj)
        } catch (e: Exception) {
            Log.e("Petal", "serializePreferences error", e)
        }
        return container
    }

    @JvmStatic
    fun restorePreferencesFromJsonObject(sp: SharedPreferences?, obj: JSONObject?) {
        if (sp == null || obj == null) return
        try {
            val valuesObj = if (obj.has("values")) obj.optJSONObject("values") else obj
            val typesObj = obj.optJSONObject("types")
            if (valuesObj == null) return

            val editor = sp.edit()
            val keys = valuesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = valuesObj.opt(key)
                if (value == null || value === JSONObject.NULL) continue

                val explicitType = typesObj?.optString(key, null)

                if ("float" == explicitType || (explicitType == null && isLikelyFloatKey(key))) {
                    if (value is Number) {
                        editor.putFloat(key, value.toFloat())
                    } else if (value is String) {
                        try {
                            editor.putFloat(key, value.toFloat())
                        } catch (ignored: Exception) {
                            editor.putString(key, value)
                        }
                    }
                } else if ("string" == explicitType || (explicitType == null && isKnownStringKey(key))) {
                    editor.putString(key, value.toString())
                } else if ("bool" == explicitType || value is Boolean) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Number -> editor.putBoolean(key, value.toInt() != 0)
                        else -> editor.putBoolean(key, value.toString().toBoolean())
                    }
                } else if ("int" == explicitType) {
                    if (value is Number) {
                        editor.putInt(key, value.toInt())
                    } else {
                        try {
                            editor.putInt(key, value.toString().toInt())
                        } catch (ignored: Exception) {}
                    }
                } else if ("long" == explicitType) {
                    if (value is Number) {
                        editor.putLong(key, value.toLong())
                    } else {
                        try {
                            editor.putLong(key, value.toString().toLong())
                        } catch (ignored: Exception) {}
                    }
                } else if (value is Int) {
                    editor.putInt(key, value)
                } else if (value is Long) {
                    editor.putLong(key, value)
                } else if (value is Double) {
                    editor.putFloat(key, value.toFloat())
                } else if (value is Float) {
                    editor.putFloat(key, value)
                } else if (value is String) {
                    editor.putString(key, value)
                } else if (value is JSONArray) {
                    val set = HashSet<String>()
                    for (i in 0 until value.length()) {
                        set.add(value.optString(i))
                    }
                    editor.putStringSet(key, set)
                }
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e("Petal", "restorePreferencesFromJsonObject error", e)
        }
    }

    @JvmStatic
    fun buildBackupJson(
        context: Context,
        backupBookmarks: Boolean,
        backupHistory: Boolean,
        backupStartSites: Boolean,
        backupTabSessions: Boolean,
        backupSavedSites: Boolean,
        backupSettings: Boolean
    ): JSONObject {
        val backupJson = JSONObject()
        try {
            backupJson.put("version", 2)
            backupJson.put("timestamp", System.currentTimeMillis())

            if (backupBookmarks) {
                try {
                    val action = HistoryBookmarkRepository(context)
                    action.open(false)
                    val bookmarks = action.listBookmark(context, false, 0)
                    action.close()

                    val bookmarksArray = JSONArray()
                    for (r in bookmarks) {
                        val obj = JSONObject()
                        obj.put("title", r.title ?: "")
                        obj.put("url", r.url ?: "")
                        val bTime = if (r.iconColor > 0) r.iconColor else (if (r.time > 0) r.time else System.currentTimeMillis())
                        obj.put("time", bTime)
                        obj.put("iconColor", if (r.iconColor > 0) r.iconColor else bTime)
                        bookmarksArray.put(obj)
                    }
                    backupJson.put("bookmarks", bookmarksArray)
                } catch (e: Exception) {
                    Log.e("Petal", "Error extracting bookmarks for JSON backup", e)
                }
            }

            if (backupHistory) {
                try {
                    val action = HistoryBookmarkRepository(context)
                    action.open(false)
                    val history = action.listHistory(context)
                    action.close()

                    val historyArray = JSONArray()
                    for (r in history) {
                        val obj = JSONObject()
                        obj.put("title", r.title ?: "")
                        obj.put("url", r.url ?: "")
                        obj.put("time", r.time)
                        historyArray.put(obj)
                    }
                    backupJson.put("history", historyArray)
                } catch (e: Exception) {
                    Log.e("Petal", "Error extracting history for JSON backup", e)
                }
            }

            if (backupStartSites) {
                try {
                    val action = HistoryBookmarkRepository(context)
                    action.open(false)
                    val startSites = action.listStartSites()
                    action.close()

                    val startArray = JSONArray()
                    for (r in startSites) {
                        val obj = JSONObject()
                        obj.put("title", r.title ?: "")
                        obj.put("url", r.url ?: "")
                        obj.put("filename", r.filename ?: "")
                        obj.put("ordinal", r.ordinal)
                        startArray.put(obj)
                    }
                    backupJson.put("start_sites", startArray)
                } catch (e: Exception) {
                    Log.e("Petal", "Error extracting start sites for JSON backup", e)
                }
            }

            if (backupTabSessions) {
                try {
                    val sessionJson = PetalSessionRestoreManager.exportSessionForBackup(context)
                    if (!sessionJson.isNullOrBlank()) {
                        backupJson.put("tab_sessions", sessionJson)
                    }
                } catch (e: Exception) {
                    Log.e("Petal", "Error extracting tab sessions for JSON backup", e)
                }
            }

            if (backupSavedSites) {
                try {
                    val action = HistoryBookmarkRepository(context)
                    action.open(false)
                    val domains = action.listDomains(HistoryBookmarkContract.TABLE_STANDARD)
                    val trusted = action.listDomains(HistoryBookmarkContract.TABLE_TRUSTED)
                    val protect = action.listDomains(HistoryBookmarkContract.TABLE_PROTECTED)
                    action.close()

                    val sitesArray = JSONArray()
                    for (domain in domains) {
                        sitesArray.put(domain)
                    }
                    backupJson.put("saved_sites", sitesArray)

                    val trustedArray = JSONArray()
                    for (domain in trusted) {
                        trustedArray.put(domain)
                    }
                    backupJson.put("trusted_sites", trustedArray)

                    val protectArray = JSONArray()
                    for (domain in protect) {
                        protectArray.put(domain)
                    }
                    backupJson.put("protected_sites", protectArray)
                } catch (e: Exception) {
                    Log.e("Petal", "Error extracting saved sites for JSON backup", e)
                }
            }

            if (backupSettings) {
                try {
                    val defaultSp = PreferenceManager.getDefaultSharedPreferences(context)
                    val defaultSettingsObj = serializePreferences(defaultSp)
                    backupJson.put("settings", defaultSettingsObj)

                    val customPrefsObj = JSONObject()
                    for (prefName in getAllKnownPreferenceNames(context)) {
                        try {
                            val customSp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                            if (customSp != null && customSp.all.isNotEmpty()) {
                                customPrefsObj.put(prefName, serializePreferences(customSp))
                            }
                        } catch (ex: Exception) {
                            Log.w("Petal", "Failed to backup SharedPreferences namespace: $prefName", ex)
                        }
                    }
                    if (customPrefsObj.length() > 0) {
                        backupJson.put("custom_preferences", customPrefsObj)
                    }
                } catch (e: Exception) {
                    Log.e("Petal", "Error extracting settings for JSON backup", e)
                }
            }
        } catch (e: Exception) {
            Log.e("Petal", "buildBackupJson failed", e)
        }
        return backupJson
    }

    @JvmStatic
    fun backupToJson(
        context: Activity,
        backupBookmarks: Boolean,
        backupHistory: Boolean,
        backupSavedSites: Boolean,
        backupSettings: Boolean
    ) {
        backupToDownloadManager(context, backupBookmarks, backupHistory, true, true, backupSavedSites, backupSettings)
    }

    @JvmStatic
    fun backupToJson(
        context: Activity,
        backupBookmarks: Boolean,
        backupHistory: Boolean,
        backupStartSites: Boolean,
        backupTabSessions: Boolean,
        backupSavedSites: Boolean,
        backupSettings: Boolean
    ) {
        backupToDownloadManager(context, backupBookmarks, backupHistory, backupStartSites, backupTabSessions, backupSavedSites, backupSettings)
    }

    @JvmStatic
    fun backupToDownloadManager(
        context: Context,
        backupBookmarks: Boolean,
        backupHistory: Boolean,
        backupStartSites: Boolean,
        backupTabSessions: Boolean,
        backupSavedSites: Boolean,
        backupSettings: Boolean
    ) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executor.execute {
            try {
                val backupJson = buildBackupJson(context, backupBookmarks, backupHistory, backupStartSites, backupTabSessions, backupSavedSites, backupSettings)
                val dataBytes = backupJson.toString(2).toByteArray(StandardCharsets.UTF_8)

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "petal_backup_$timeStamp.json"

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null && !downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                var targetFile: File? = null
                var directWriteSuccess = false

                if (downloadDir != null && downloadDir.exists()) {
                    targetFile = File(downloadDir, fileName)
                    try {
                        FileOutputStream(targetFile, false).use { fos ->
                            fos.write(dataBytes)
                            fos.flush()
                            try {
                                fos.fd.sync()
                            } catch (ignored: Exception) {}
                            directWriteSuccess = true
                        }
                    } catch (writeEx: Throwable) {
                        Log.w("Petal", "Direct Downloads folder write failed, falling back to MediaStore or app directory: " + writeEx.message)
                        directWriteSuccess = false
                    }
                }

                if (!directWriteSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                                os.write(dataBytes)
                                os.flush()
                                directWriteSuccess = true
                            }
                        }
                    } catch (msEx: Throwable) {
                        Log.w("Petal", "MediaStore Downloads write failed: " + msEx.message)
                    }
                }

                if (!directWriteSuccess) {
                    val safeDir = getSafeBackupDir(context)
                    targetFile = File(safeDir, fileName)
                    FileOutputStream(targetFile, false).use { fos ->
                        fos.write(dataBytes)
                        fos.flush()
                    }
                }

                if (targetFile != null && targetFile.exists()) {
                    MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("application/json"), null)
                }

                handler.post {
                    PetalToast.show(context, "Backup downloaded to Downloads: $fileName")
                }
            } catch (e: Exception) {
                Log.e("Petal", "backupToDownloadManager error", e)
                handler.post {
                    PetalToast.show(context, "Backup failed: ${e.message}")
                }
            }
        }
    }

    @JvmStatic
    fun backupToUri(
        context: Context,
        uri: Uri,
        backupBookmarks: Boolean,
        backupHistory: Boolean,
        backupSavedSites: Boolean,
        backupSettings: Boolean
    ) {
        backupToUri(context, uri, backupBookmarks, backupHistory, true, true, backupSavedSites, backupSettings)
    }

    @JvmStatic
    fun backupToUri(
        context: Context,
        uri: Uri,
        backupBookmarks: Boolean,
        backupHistory: Boolean,
        backupStartSites: Boolean,
        backupTabSessions: Boolean,
        backupSavedSites: Boolean,
        backupSettings: Boolean
    ) {
        var destinationStream: OutputStream? = null
        try {
            try {
                destinationStream = context.contentResolver.openOutputStream(uri, "wt")
            } catch (t1: Throwable) {
                try {
                    destinationStream = context.contentResolver.openOutputStream(uri, "w")
                } catch (t2: Throwable) {
                    destinationStream = context.contentResolver.openOutputStream(uri)
                }
            }
        } catch (outerEx: Throwable) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "wt")
                if (pfd != null) {
                    destinationStream = ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                }
            } catch (ignored: Throwable) {}
        }

        if (destinationStream == null) {
            Handler(Looper.getMainLooper()).post {
                PetalToast.show(context, "Backup failed: cannot open file for writing")
            }
            return
        }

        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        val finalStream = destinationStream
        executor.execute {
            try {
                finalStream.use { stream ->
                    val backupJson = buildBackupJson(context, backupBookmarks, backupHistory, backupStartSites, backupTabSessions, backupSavedSites, backupSettings)
                    val dataBytes = backupJson.toString(2).toByteArray(StandardCharsets.UTF_8)
                    Log.i("Petal", "Writing backup payload: ${dataBytes.size} bytes to URI: $uri")

                    BufferedOutputStream(stream, 65536).use { bos ->
                        bos.write(dataBytes)
                        bos.flush()
                    }

                    Log.i("Petal", "Backup written successfully (${dataBytes.size} bytes)")
                    handler.post {
                        PetalToast.show(
                            context,
                            "${context.getString(R.string.app_done)}: Backup saved (${dataBytes.size} bytes)"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("Petal", "backupToUri error", e)
                handler.post { PetalToast.show(context, "Backup failed: ${e.message}") }
            }
        }
    }

    @JvmStatic
    fun restoreFromUri(
        context: Context,
        uri: Uri,
        restoreBookmarks: Boolean,
        restoreHistory: Boolean,
        restoreSavedSites: Boolean,
        restoreSettings: Boolean
    ) {
        restoreFromUri(context, uri, restoreBookmarks, restoreHistory, true, true, restoreSavedSites, restoreSettings)
    }

    @JvmStatic
    fun restoreFromUri(
        context: Context,
        uri: Uri,
        restoreBookmarks: Boolean,
        restoreHistory: Boolean,
        restoreStartSites: Boolean,
        restoreTabSessions: Boolean,
        restoreSavedSites: Boolean,
        restoreSettings: Boolean
    ) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executor.execute {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    handler.post { PetalToast.show(context, "Failed to open selected file") }
                    return@execute
                }

                val reader = BufferedReader(InputStreamReader(inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val backupJson = JSONObject(sb.toString())

                if (restoreBookmarks && backupJson.has("bookmarks")) {
                    try {
                        val bookmarksArray = backupJson.getJSONArray("bookmarks")
                        val action = HistoryBookmarkRepository(context)
                        action.open(true)
                        for (i in 0 until bookmarksArray.length()) {
                            val obj = bookmarksArray.getJSONObject(i)
                            var title = obj.optString("title", "")
                            if (title.isEmpty()) title = obj.optString("name", "")
                            var url = obj.optString("url", "")
                            if (url.isEmpty()) url = obj.optString("href", "")
                            if (url.isEmpty()) url = obj.optString("link", "")
                            val time = obj.optLong("time", System.currentTimeMillis())
                            val iconColor = obj.optLong("iconColor", if (time > 0) time else 1L)
                            if (title.isEmpty()) title = url
                            if (url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true) &&
                                !action.checkUrl(url, HistoryBookmarkContract.TABLE_BOOKMARK)
                            ) {
                                val record = Record()
                                record.title = title
                                record.url = url
                                record.time = time
                                record.iconColor = if (iconColor > 0) iconColor else (if (time > 0) time else 1L)
                                action.addBookmark(record)
                            }
                        }
                        action.close()
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring bookmarks", e)
                    }
                }

                if (restoreHistory && backupJson.has("history")) {
                    try {
                        val historyArray = backupJson.getJSONArray("history")
                        val action = HistoryBookmarkRepository(context)
                        action.open(true)
                        for (i in 0 until historyArray.length()) {
                            val obj = historyArray.getJSONObject(i)
                            var title = obj.optString("title", "")
                            if (title.isEmpty()) title = obj.optString("name", "")
                            var url = obj.optString("url", "")
                            if (url.isEmpty()) url = obj.optString("href", "")
                            if (url.isEmpty()) url = obj.optString("link", "")
                            val time = obj.optLong("time", System.currentTimeMillis())
                            if (title.isEmpty()) title = url
                            if (url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true) &&
                                !action.checkUrl(url, HistoryBookmarkContract.TABLE_HISTORY)
                            ) {
                                action.addHistory(Record(title, url, time, 0L))
                            }
                        }
                        action.close()
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring history", e)
                    }
                }

                if (restoreStartSites && backupJson.has("start_sites")) {
                    try {
                        val startArray = backupJson.getJSONArray("start_sites")
                        val action = HistoryBookmarkRepository(context)
                        action.open(true)
                        for (i in 0 until startArray.length()) {
                            val obj = startArray.getJSONObject(i)
                            val title = obj.optString("title", "")
                            val url = obj.optString("url", "")
                            val filename = obj.optString("filename", "")
                            val ordinal = obj.optInt("ordinal", i)
                            if (url.isNotEmpty() && !action.checkStartSite(url)) {
                                val record = Record()
                                record.title = title
                                record.url = url
                                record.filename = filename
                                record.ordinal = ordinal
                                action.addStartSite(record)
                            }
                        }
                        action.close()
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring start sites", e)
                    }
                }

                if (restoreTabSessions && backupJson.has("tab_sessions")) {
                    try {
                        var sessionJson: String? = null
                        val sessionObj = backupJson.opt("tab_sessions")
                        if (sessionObj is JSONObject || sessionObj is JSONArray) {
                            sessionJson = sessionObj.toString()
                        } else if (sessionObj is String) {
                            sessionJson = sessionObj
                        }
                        if (!sessionJson.isNullOrBlank()) {
                            PetalSessionRestoreManager.importSessionFromBackup(context, sessionJson)
                        }
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring tab session", e)
                    }
                }

                if (restoreSavedSites) {
                    if (backupJson.has("saved_sites")) {
                        try {
                            val sitesArray = backupJson.getJSONArray("saved_sites")
                            val action = HistoryBookmarkRepository(context)
                            val listStandard = StandardDomainWhitelist(context)
                            action.open(true)
                            for (i in 0 until sitesArray.length()) {
                                val domain = sitesArray.optString(i, "")
                                if (domain.isNotEmpty() && !action.checkDomain(domain, HistoryBookmarkContract.TABLE_STANDARD)) {
                                    listStandard.addDomain(domain)
                                }
                            }
                            action.close()
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring saved sites", e)
                        }
                    }
                    if (backupJson.has("trusted_sites")) {
                        try {
                            val trustedArray = backupJson.getJSONArray("trusted_sites")
                            val action = HistoryBookmarkRepository(context)
                            action.open(true)
                            for (i in 0 until trustedArray.length()) {
                                val domain = trustedArray.optString(i, "")
                                if (domain.isNotEmpty() && !action.checkDomain(domain, HistoryBookmarkContract.TABLE_TRUSTED)) {
                                    action.addDomain(domain, HistoryBookmarkContract.TABLE_TRUSTED)
                                }
                            }
                            action.close()
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring trusted sites", e)
                        }
                    }
                    if (backupJson.has("protected_sites")) {
                        try {
                            val protectArray = backupJson.getJSONArray("protected_sites")
                            val action = HistoryBookmarkRepository(context)
                            action.open(true)
                            for (i in 0 until protectArray.length()) {
                                val domain = protectArray.optString(i, "")
                                if (domain.isNotEmpty() && !action.checkDomain(domain, HistoryBookmarkContract.TABLE_PROTECTED)) {
                                    action.addDomain(domain, HistoryBookmarkContract.TABLE_PROTECTED)
                                }
                            }
                            action.close()
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring protected sites", e)
                        }
                    }
                }

                if (restoreSettings) {
                    if (backupJson.has("settings")) {
                        try {
                            val settingsObj = backupJson.getJSONObject("settings")
                            val defaultSp = PreferenceManager.getDefaultSharedPreferences(context)
                            restorePreferencesFromJsonObject(defaultSp, settingsObj)
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring settings", e)
                        }
                    }
                    if (backupJson.has("custom_preferences")) {
                        try {
                            val customPrefsObj = backupJson.getJSONObject("custom_preferences")
                            val pKeys = customPrefsObj.keys()
                            while (pKeys.hasNext()) {
                                val prefName = pKeys.next()
                                val prefJson = customPrefsObj.optJSONObject(prefName)
                                if (prefJson != null) {
                                    val customSp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                                    restorePreferencesFromJsonObject(customSp, prefJson)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring custom preferences", e)
                        }
                    }
                }

                handler.post {
                    PetalToast.show(
                        context,
                        "${context.getString(R.string.app_done)}: ${context.getString(R.string.settings_data_restore)}"
                    )
                }
            } catch (e: Exception) {
                Log.e("Petal", "restoreFromUri error", e)
                handler.post {
                    PetalToast.show(context, "Restore failed: ${e.message}")
                }
            }
        }
    }

    @JvmStatic
    fun restoreFromJson(
        context: Activity,
        restoreBookmarks: Boolean,
        restoreHistory: Boolean,
        restoreSavedSites: Boolean,
        restoreSettings: Boolean
    ) {
        restoreFromJson(context, restoreBookmarks, restoreHistory, true, true, restoreSavedSites, restoreSettings)
    }

    @JvmStatic
    fun restoreFromJson(
        context: Activity,
        restoreBookmarks: Boolean,
        restoreHistory: Boolean,
        restoreStartSites: Boolean,
        restoreTabSessions: Boolean,
        restoreSavedSites: Boolean,
        restoreSettings: Boolean
    ) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executor.execute {
            try {
                val backupDir = getSafeBackupDir(context)
                var jsonFile = if (backupDir != null) File(backupDir, "petal_browser_backup.json") else null
                if (jsonFile == null || !jsonFile.exists()) {
                    val fallbackFile = File(File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS), "browser_backup"), "petal_browser_backup.json")
                    if (fallbackFile.exists()) {
                        jsonFile = fallbackFile
                    }
                }
                if (jsonFile == null || !jsonFile.exists()) {
                    handler.post { PetalToast.show(context, "No backup file found at Documents/browser_backup/petal_browser_backup.json") }
                    return@execute
                }

                val reader = BufferedReader(FileReader(jsonFile))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val backupJson = JSONObject(sb.toString())

                if (restoreBookmarks && backupJson.has("bookmarks")) {
                    try {
                        val bookmarksArray = backupJson.getJSONArray("bookmarks")
                        val action = HistoryBookmarkRepository(context)
                        action.open(true)
                        for (i in 0 until bookmarksArray.length()) {
                            val obj = bookmarksArray.getJSONObject(i)
                            var title = obj.optString("title", "")
                            if (title.isEmpty()) title = obj.optString("name", "")
                            var url = obj.optString("url", "")
                            if (url.isEmpty()) url = obj.optString("href", "")
                            if (url.isEmpty()) url = obj.optString("link", "")
                            val time = obj.optLong("time", System.currentTimeMillis())
                            val iconColor = obj.optLong("iconColor", if (time > 0) time else 1L)
                            if (title.isEmpty()) title = url
                            if (url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true) &&
                                !action.checkUrl(url, HistoryBookmarkContract.TABLE_BOOKMARK)
                            ) {
                                val record = Record()
                                record.title = title
                                record.url = url
                                record.time = time
                                record.iconColor = if (iconColor > 0) iconColor else (if (time > 0) time else 1L)
                                action.addBookmark(record)
                            }
                        }
                        action.close()
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring bookmarks", e)
                    }
                }

                if (restoreHistory && backupJson.has("history")) {
                    try {
                        val historyArray = backupJson.getJSONArray("history")
                        val action = HistoryBookmarkRepository(context)
                        action.open(true)
                        for (i in 0 until historyArray.length()) {
                            val obj = historyArray.getJSONObject(i)
                            var title = obj.optString("title", "")
                            if (title.isEmpty()) title = obj.optString("name", "")
                            var url = obj.optString("url", "")
                            if (url.isEmpty()) url = obj.optString("href", "")
                            if (url.isEmpty()) url = obj.optString("link", "")
                            val time = obj.optLong("time", System.currentTimeMillis())
                            if (title.isEmpty()) title = url
                            if (url.isNotEmpty() && !url.equals("about:blank", ignoreCase = true) &&
                                !action.checkUrl(url, HistoryBookmarkContract.TABLE_HISTORY)
                            ) {
                                action.addHistory(Record(title, url, time, 0L))
                            }
                        }
                        action.close()
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring history", e)
                    }
                }

                if (restoreStartSites && backupJson.has("start_sites")) {
                    try {
                        val startArray = backupJson.getJSONArray("start_sites")
                        val action = HistoryBookmarkRepository(context)
                        action.open(true)
                        for (i in 0 until startArray.length()) {
                            val obj = startArray.getJSONObject(i)
                            val title = obj.optString("title", "")
                            val url = obj.optString("url", "")
                            val filename = obj.optString("filename", "")
                            val ordinal = obj.optInt("ordinal", i)
                            if (url.isNotEmpty() && !action.checkStartSite(url)) {
                                val record = Record()
                                record.title = title
                                record.url = url
                                record.filename = filename
                                record.ordinal = ordinal
                                action.addStartSite(record)
                            }
                        }
                        action.close()
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring start sites", e)
                    }
                }

                if (restoreTabSessions && backupJson.has("tab_sessions")) {
                    try {
                        var sessionJson: String? = null
                        val sessionObj = backupJson.opt("tab_sessions")
                        if (sessionObj is JSONObject || sessionObj is JSONArray) {
                            sessionJson = sessionObj.toString()
                        } else if (sessionObj is String) {
                            sessionJson = sessionObj
                        }
                        if (!sessionJson.isNullOrBlank()) {
                            PetalSessionRestoreManager.importSessionFromBackup(context, sessionJson)
                        }
                    } catch (e: Exception) {
                        Log.e("Petal", "Error restoring tab session", e)
                    }
                }

                if (restoreSavedSites) {
                    if (backupJson.has("saved_sites")) {
                        try {
                            val sitesArray = backupJson.getJSONArray("saved_sites")
                            val action = HistoryBookmarkRepository(context)
                            val listStandard = StandardDomainWhitelist(context)
                            action.open(true)
                            for (i in 0 until sitesArray.length()) {
                                val domain = sitesArray.optString(i, "")
                                if (domain.isNotEmpty() && !action.checkDomain(domain, HistoryBookmarkContract.TABLE_STANDARD)) {
                                    listStandard.addDomain(domain)
                                }
                            }
                            action.close()
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring saved sites", e)
                        }
                    }
                    if (backupJson.has("trusted_sites")) {
                        try {
                            val trustedArray = backupJson.getJSONArray("trusted_sites")
                            val action = HistoryBookmarkRepository(context)
                            action.open(true)
                            for (i in 0 until trustedArray.length()) {
                                val domain = trustedArray.optString(i, "")
                                if (domain.isNotEmpty() && !action.checkDomain(domain, HistoryBookmarkContract.TABLE_TRUSTED)) {
                                    action.addDomain(domain, HistoryBookmarkContract.TABLE_TRUSTED)
                                }
                            }
                            action.close()
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring trusted sites", e)
                        }
                    }
                    if (backupJson.has("protected_sites")) {
                        try {
                            val protectArray = backupJson.getJSONArray("protected_sites")
                            val action = HistoryBookmarkRepository(context)
                            action.open(true)
                            for (i in 0 until protectArray.length()) {
                                val domain = protectArray.optString(i, "")
                                if (domain.isNotEmpty() && !action.checkDomain(domain, HistoryBookmarkContract.TABLE_PROTECTED)) {
                                    action.addDomain(domain, HistoryBookmarkContract.TABLE_PROTECTED)
                                }
                            }
                            action.close()
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring protected sites", e)
                        }
                    }
                }

                if (restoreSettings) {
                    if (backupJson.has("settings")) {
                        try {
                            val settingsObj = backupJson.getJSONObject("settings")
                            val sp = PreferenceManager.getDefaultSharedPreferences(context)
                            restorePreferencesFromJsonObject(sp, settingsObj)
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring settings", e)
                        }
                    }
                    if (backupJson.has("custom_preferences")) {
                        try {
                            val customPrefsObj = backupJson.getJSONObject("custom_preferences")
                            val pKeys = customPrefsObj.keys()
                            while (pKeys.hasNext()) {
                                val prefName = pKeys.next()
                                val prefJson = customPrefsObj.optJSONObject(prefName)
                                if (prefJson != null) {
                                    val customSp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                                    restorePreferencesFromJsonObject(customSp, prefJson)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Petal", "Error restoring custom preferences", e)
                        }
                    }
                }

                handler.post {
                    PetalToast.show(
                        context,
                        "${context.getString(R.string.app_done)}: ${context.getString(R.string.settings_data_restore)}"
                    )
                }
            } catch (e: Exception) {
                Log.e("Petal", "restoreFromJson error", e)
                handler.post {
                    PetalToast.show(context, "Restore failed: ${e.message}")
                }
            }
        }
    }

    @JvmStatic
    fun backupData(context: Activity, i: Int) {
        backupToJson(context, true, true, true, true)
    }

    @JvmStatic
    fun backupData(context: Context?, i: Int) {
        if (context == null) return
        backupToDownloadManager(context.applicationContext, true, true, true, true, true, true)
    }

    @JvmStatic
    fun performAutoVersionBackup(context: Context?) {
        if (context == null) return
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                val sp = PreferenceManager.getDefaultSharedPreferences(context)
                var currentVersionCode = 0
                try {
                    currentVersionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                } catch (ignored: Exception) {}

                val lastVersionCode = sp.getInt("sp_last_app_version_code", 0)
                if (currentVersionCode > 0 && currentVersionCode != lastVersionCode) {
                    sp.edit().putInt("sp_last_app_version_code", currentVersionCode).apply()
                }

                val backupJson = JSONObject()
                backupJson.put("version", 1)
                backupJson.put("app_version_code", currentVersionCode)
                backupJson.put("timestamp", System.currentTimeMillis())

                val action = HistoryBookmarkRepository(context)
                action.open(false)
                val bookmarks = action.listBookmark(context, false, 0)
                val history = action.listHistory(context)
                val startSites = action.listStartSites()
                val sessionJson = PetalSessionRestoreManager.exportSessionForBackup(context)
                val domains = action.listDomains(HistoryBookmarkContract.TABLE_STANDARD)
                val trusted = action.listDomains(HistoryBookmarkContract.TABLE_TRUSTED)
                val protect = action.listDomains(HistoryBookmarkContract.TABLE_PROTECTED)
                action.close()

                val bookmarksArray = JSONArray()
                for (r in bookmarks) {
                    val obj = JSONObject()
                    obj.put("title", r.title ?: "")
                    obj.put("url", r.url ?: "")
                    val bTime = if (r.iconColor > 0) r.iconColor else (if (r.time > 0) r.time else System.currentTimeMillis())
                    obj.put("time", bTime)
                    obj.put("iconColor", if (r.iconColor > 0) r.iconColor else bTime)
                    bookmarksArray.put(obj)
                }
                backupJson.put("bookmarks", bookmarksArray)

                val historyArray = JSONArray()
                for (r in history) {
                    val obj = JSONObject()
                    obj.put("title", r.title ?: "")
                    obj.put("url", r.url ?: "")
                    obj.put("time", r.time)
                    historyArray.put(obj)
                }
                backupJson.put("history", historyArray)

                val startArray = JSONArray()
                for (r in startSites) {
                    val obj = JSONObject()
                    obj.put("title", r.title ?: "")
                    obj.put("url", r.url ?: "")
                    obj.put("filename", r.filename ?: "")
                    obj.put("ordinal", r.ordinal)
                    startArray.put(obj)
                }
                backupJson.put("start_sites", startArray)

                if (!sessionJson.isNullOrBlank()) {
                    backupJson.put("tab_sessions", sessionJson)
                }

                val sitesArray = JSONArray()
                for (domain in domains) {
                    sitesArray.put(domain)
                }
                backupJson.put("saved_sites", sitesArray)

                val trustedArray = JSONArray()
                for (domain in trusted) {
                    trustedArray.put(domain)
                }
                backupJson.put("trusted_sites", trustedArray)

                val protectArray = JSONArray()
                for (domain in protect) {
                    protectArray.put(domain)
                }
                backupJson.put("protected_sites", protectArray)

                backupJson.put("settings", serializePreferences(sp))

                val customPrefsObj = JSONObject()
                for (prefName in getAllKnownPreferenceNames(context)) {
                    try {
                        val customSp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        if (customSp != null && customSp.all.isNotEmpty()) {
                            customPrefsObj.put(prefName, serializePreferences(customSp))
                        }
                    } catch (ex: Exception) {
                        Log.w("Petal", "Failed to backup custom pref namespace in auto backup: $prefName", ex)
                    }
                }
                if (customPrefsObj.length() > 0) {
                    backupJson.put("custom_preferences", customPrefsObj)
                }

                val backupDir = getSafeBackupDir(context)
                val jsonFile = File(backupDir, "petal_downgrade_snapshot.json")

                val dataBytes = backupJson.toString(2).toByteArray(StandardCharsets.UTF_8)
                FileOutputStream(jsonFile, false).use { fos ->
                    fos.write(dataBytes)
                    fos.flush()
                    try {
                        fos.fd.sync()
                    } catch (ignored: Exception) {}
                }
                Log.i("Petal", "Automatic downgrade protection backup saved: ${jsonFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("Petal", "Failed to save automatic version snapshot: ${e.message}")
            }
        }
    }

    @JvmStatic
    fun restoreData(context: Activity, i: Int) {
        restoreFromJson(context, true, true, true, true)
    }

    @JvmStatic
    fun exportList(context: Context?) {}

    @JvmStatic
    fun importList(context: Context?) {}

    @JvmStatic
    fun exportHistory(context: Context?) {}

    @JvmStatic
    fun importHistory(context: Context?) {}
}
