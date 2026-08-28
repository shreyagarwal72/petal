package com.petal.browser.fragment

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.petal.browser.R
import com.petal.browser.preferences.BasePreferenceFragment
import com.petal.browser.unit.BackupUnit
import com.petal.browser.unit.HelperUnit
import com.petal.browser.view.NinjaToast
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

class Fragment_settings_Backup : BasePreferenceFragment() {

    private lateinit var sd: File
    private lateinit var data: File
    private var appContext: Context? = null
    private var currentActivity: Activity? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_backup, rootKey)
        appContext = context
        currentActivity = activity

        sd = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        data = Environment.getDataDirectory()
        val databaseApp = "//data//" + requireActivity().packageName + "//databases//Ninja4.db"
        val databaseBackup = "browser_backup//database.db"
        val sourceDB = File(data, databaseApp)
        val fileBackupDB = File(sd, databaseBackup)

        val preferenceApp = "//data//" + requireActivity().packageName + "//shared_prefs//" + requireActivity().packageName + "_preferences.xml"
        val preferenceBackup = "browser_backup//settings.xml"
        val sourceSettings = File(data, preferenceApp)
        val fileBackupSettings = File(sd, preferenceBackup)

        findPreference<androidx.preference.Preference>("export_databases")?.setOnPreferenceClickListener {
            try {
                if (fileBackupDB.exists()) fileBackupDB.delete()
                val target = File(sd, "browser_backup")
                if (!target.exists()) target.mkdir()
                if (sourceDB.exists()) {
                    val src = sourceDB.toPath()
                    val dest = fileBackupDB.toPath()
                    Files.copy(src, dest)
                }
                NinjaToast.show(requireContext(), R.string.toast_export_successful)
            } catch (e: Exception) {
                NinjaToast.show(requireContext(), R.string.toast_error)
            }
            false
        }

        findPreference<androidx.preference.Preference>("import_databases")?.setOnPreferenceClickListener {
            try {
                if (fileBackupDB.exists()) {
                    if (sourceDB.exists()) sourceDB.delete()
                    val src = fileBackupDB.toPath()
                    val dest = sourceDB.toPath()
                    Files.copy(src, dest)
                    NinjaToast.show(requireContext(), R.string.toast_import_successful)
                } else {
                    NinjaToast.show(requireContext(), R.string.toast_error)
                }
            } catch (e: Exception) {
                NinjaToast.show(requireContext(), R.string.toast_error)
            }
            false
        }

        findPreference<androidx.preference.Preference>("export_settings")?.setOnPreferenceClickListener {
            try {
                if (fileBackupSettings.exists()) fileBackupSettings.delete()
                val target = File(sd, "browser_backup")
                if (!target.exists()) target.mkdir()
                if (sourceSettings.exists()) {
                    val src = sourceSettings.toPath()
                    val dest = fileBackupSettings.toPath()
                    Files.copy(src, dest)
                }
                NinjaToast.show(requireContext(), R.string.toast_export_successful)
            } catch (e: Exception) {
                NinjaToast.show(requireContext(), R.string.toast_error)
            }
            false
        }

        findPreference<androidx.preference.Preference>("import_settings")?.setOnPreferenceClickListener {
            try {
                if (fileBackupSettings.exists()) {
                    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val editor = sp.edit()

                    val dbFactory = DocumentBuilderFactory.newInstance()
                    val dBuilder = dbFactory.newDocumentBuilder()
                    val doc = dBuilder.parse(fileBackupSettings)
                    doc.documentElement.normalize()

                    val nList = doc.getElementsByTagName("map").item(0).childNodes

                    for (temp in 0 until nList.length) {
                        val nNode = nList.item(temp)
                        if (nNode.nodeType == Node.ELEMENT_NODE) {
                            val eElement = nNode as Element
                            val type = eElement.nodeName
                            val name = eElement.getAttribute("name")
                            val value = eElement.getAttribute("value")

                            when (type) {
                                "string" -> editor.putString(name, eElement.textContent)
                                "boolean" -> editor.putBoolean(name, value.toBoolean())
                                "int" -> editor.putInt(name, value.toInt())
                                "long" -> editor.putLong(name, value.toLong())
                                "float" -> editor.putFloat(name, value.toFloat())
                            }
                        }
                    }
                    editor.apply()
                    NinjaToast.show(requireContext(), R.string.toast_import_successful)
                } else {
                    NinjaToast.show(requireContext(), R.string.toast_error)
                }
            } catch (e: Exception) {
                NinjaToast.show(requireContext(), R.string.toast_error)
            }
            false
        }

        findPreference<androidx.preference.Preference>("export_bookmarks")?.setOnPreferenceClickListener {
            BackupUnit.exportBookmarks(requireActivity())
            false
        }

        findPreference<androidx.preference.Preference>("import_bookmarks")?.setOnPreferenceClickListener {
            BackupUnit.importBookmarks(requireActivity())
            false
        }
    }
}
