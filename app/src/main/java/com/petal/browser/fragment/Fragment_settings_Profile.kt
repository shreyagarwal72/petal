package com.petal.browser.fragment

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.browser.BannerBlock
import com.petal.browser.preferences.BasePreferenceFragment

class Fragment_settings_Profile : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        setPreferencesFromResource(R.xml.preference_profile_standard, rootKey)
        PreferenceManager.setDefaultValues(context, R.xml.preference_profile_standard, false)
        initSummary(preferenceScreen)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key != null) {
            val pref = findPreference<Preference>(key)
            if (pref != null) {
                updatePrefSummary(pref)
            }
            if (key == "profileStandard_adBlock") {
                if (sp?.getBoolean("profileStandard_adBlock", false) == true) {
                    BannerBlock.downloadBanners(activity)
                }
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

    private fun updatePrefSummary(p: Preference) {
        if (p is ListPreference) {
            p.summary = p.entry
        }
        if (p is EditTextPreference) {
            if (p.title?.toString()?.lowercase()?.contains("password") == true) {
                p.summary = "******"
            } else {
                p.summary = p.text
            }
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
