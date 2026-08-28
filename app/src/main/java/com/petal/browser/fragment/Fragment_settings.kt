package com.petal.browser.fragment

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.activity.Settings_Backup
import com.petal.browser.activity.Settings_Delete
import com.petal.browser.activity.Settings_Filter
import com.petal.browser.activity.Settings_Gesture
import com.petal.browser.activity.Settings_Menu
import com.petal.browser.activity.Settings_Profile
import com.petal.browser.activity.Settings_ProfileList
import com.petal.browser.browser.AdBlock
import com.petal.browser.preferences.BasePreferenceFragment

class Fragment_settings : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var spAdBlock: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_setting, rootKey)
        val context = requireContext()
        initSummary(preferenceScreen)

        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        spAdBlock = findPreference("ab_hosts")
        spAdBlock?.summary = AdBlock.getHostsDate(context)

        findPreference<Preference>("settings_profile")?.setOnPreferenceClickListener {
            val intent = Intent(activity, Settings_Profile::class.java)
            requireActivity().startActivity(intent)
            false
        }

        findPreference<Preference>("edit_standard")?.setOnPreferenceClickListener {
            sp.edit().putString("listToLoad", "standard").apply()
            val intent = Intent(activity, Settings_ProfileList::class.java)
            requireActivity().startActivity(intent)
            false
        }

        findPreference<Preference>("settings_menu")?.setOnPreferenceClickListener {
            val intent = Intent(activity, Settings_Menu::class.java)
            requireActivity().startActivity(intent)
            false
        }

        findPreference<Preference>("settings_gestures")?.setOnPreferenceClickListener {
            val intent = Intent(activity, Settings_Gesture::class.java)
            requireActivity().startActivity(intent)
            false
        }

        findPreference<Preference>("settings_filter")?.setOnPreferenceClickListener {
            val intent = Intent(activity, Settings_Filter::class.java)
            requireActivity().startActivity(intent)
            false
        }

        findPreference<Preference>("settings_backup")?.setOnPreferenceClickListener {
            val intent = Intent(activity, Settings_Backup::class.java)
            requireActivity().startActivity(intent)
            false
        }

        findPreference<Preference>("settings_delete")?.setOnPreferenceClickListener {
            val intent = Intent(activity, Settings_Delete::class.java)
            requireActivity().startActivity(intent)
            false
        }
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key != null) {
            updatePrefSummary(findPreference(key))
            if (key == "ab_hosts") {
                spAdBlock?.summary = AdBlock.getHostsDate(context)
            }
        }
    }

    private fun initSummary(p: Preference) {
        if (p is PreferenceGroup) {
            for (i in 0 until p.preferenceCount) {
                initSummary(p.getPreference(i))
            }
        } else {
            updatePrefSummary(p)
        }
    }

    private fun updatePrefSummary(p: Preference?) {
        if (p is ListPreference) {
            p.summary = p.entry
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }
}
