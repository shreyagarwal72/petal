package com.petal.browser.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.google.android.material.checkbox.MaterialCheckBox
import com.petal.browser.R

class ListSwitchPreference(context: Context, attrs: AttributeSet?) : ListPreference(context, attrs) {

    private var listSwitchKey: String? = null
    private var listSwitchKeyDefaultValue: Boolean = false
    private var switchAttached: Boolean = false

    init {
        if (attrs != null) {
            val valueArray: TypedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.ListSwitchPreference, 0, 0)
            listSwitchKey = valueArray.getString(R.styleable.ListSwitchPreference_listSwitchKey)
            listSwitchKeyDefaultValue = valueArray.getBoolean(R.styleable.ListSwitchPreference_listSwitchKeyDefaultValue, false)
            valueArray.recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        super.onBindViewHolder(holder)
        val rootView = holder.itemView as ViewGroup

        val key = listSwitchKey
        if (!switchAttached && key != null) {
            val onOffSwitch = MaterialCheckBox(context)
            rootView.addView(onOffSwitch)
            switchAttached = true
            onOffSwitch.isChecked = sp.getBoolean(key, listSwitchKeyDefaultValue)
            val checkedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
                sp.edit().putBoolean(key, isChecked).apply()
            }
            onOffSwitch.setOnCheckedChangeListener(checkedChangeListener)
            checkedChangeListener.onCheckedChanged(onOffSwitch, onOffSwitch.isChecked)
        }
    }
}
