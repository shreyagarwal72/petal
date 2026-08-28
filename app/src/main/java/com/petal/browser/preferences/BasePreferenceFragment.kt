package com.petal.browser.preferences

import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.petal.browser.R
import com.petal.browser.unit.HelperUnit
import java.util.Arrays

open class BasePreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference -> showListPreference(preference)
            is EditTextPreference -> showEditTextPreference(preference)
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    open fun showListPreference(preference: ListPreference) {
        val entryValues = preference.entryValues ?: return
        val selectionIndex = Arrays.asList(*entryValues).indexOf(preference.value)
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(preference.title)
        builder.setNegativeButton(R.string.app_cancel, null)
        builder.setSingleChoiceItems(preference.entries, selectionIndex) { dialog, index ->
            val newValue = preference.entryValues[index].toString()
            if (preference.callChangeListener(newValue)) {
                preference.value = newValue
            }
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        HelperUnit.setupDialog(requireContext(), dialog)
    }

    open fun showEditTextPreference(preference: EditTextPreference) {
        val dialogView = View.inflate(context, R.layout.dialog_edit_text, null)
        val input = dialogView.findViewById<TextInputEditText>(R.id.textInput)
        input.setText(preference.text)

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(preference.title)
        builder.setIcon(R.drawable.icon_preview)
        builder.setView(dialogView)
        builder.setPositiveButton(R.string.app_ok) { _, _ ->
            val newValue = input.text.toString()
            if (preference.callChangeListener(newValue)) {
                preference.text = newValue
            }
        }
        builder.setNegativeButton(R.string.app_cancel, null)
        val dialog = builder.create()
        dialog.show()
        HelperUnit.setupDialog(requireContext(), dialog)
    }
}
