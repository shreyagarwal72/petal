package com.petal.browser.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.content.res.ResourcesCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.google.android.material.checkbox.MaterialCheckBox
import com.petal.browser.R

class EditTextSwitchPreference(context: Context, attrs: AttributeSet?) : EditTextPreference(context, attrs) {

    private var editTextSwitchKey: String? = null
    private var editTextSwitchKeyDefaultValue: Boolean = false
    private var switchAttached: Boolean = false

    init {
        if (attrs != null) {
            val valueArray: TypedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.EditTextSwitchPreference, 0, 0)
            editTextSwitchKey = valueArray.getString(R.styleable.EditTextSwitchPreference_editTextSwitchKey)
            editTextSwitchKeyDefaultValue = valueArray.getBoolean(R.styleable.EditTextSwitchPreference_editTextSwitchKeyDefaultValue, false)
            valueArray.recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val context = context
        super.onBindViewHolder(holder)
        val rootView = holder.itemView as ViewGroup

        val key = editTextSwitchKey
        if (!switchAttached && key != null) {
            val onOffSwitch = MaterialCheckBox(context)
            rootView.addView(onOffSwitch)
            switchAttached = true
            onOffSwitch.isChecked = sp.getBoolean(key, editTextSwitchKeyDefaultValue)
            val checkedChangeListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
                sp.edit().putBoolean(key, isChecked).apply()
            }
            onOffSwitch.setOnCheckedChangeListener(checkedChangeListener)
            checkedChangeListener.onCheckedChanged(onOffSwitch, onOffSwitch.isChecked)

            onOffSwitch.setOnCheckedChangeListener { _, isChecked ->
                sp.edit().putBoolean(key, isChecked).apply()
                if (key == "sp_autofill") {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true)
                    val color = typedValue.data
                    if (isChecked) {
                        onOffSwitch.setButtonDrawable(R.drawable.icon_preview)
                        holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.color_white, null))
                    } else {
                        onOffSwitch.setButtonDrawable(R.drawable.icon_cancel)
                        holder.itemView.setBackgroundColor(color)
                    }
                }
            }

            if (key == "sp_autofill") {
                val isChecked = sp.getBoolean(key, editTextSwitchKeyDefaultValue)
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true)
                val color = typedValue.data
                if (isChecked) {
                    onOffSwitch.setButtonDrawable(R.drawable.icon_preview)
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.color_white, null))
                } else {
                    onOffSwitch.setButtonDrawable(R.drawable.icon_cancel)
                    holder.itemView.setBackgroundColor(color)
                }
            }
        }
    }
}
