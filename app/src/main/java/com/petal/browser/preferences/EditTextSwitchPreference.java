package com.petal.browser.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.checkbox.MaterialCheckBox;

import com.petal.browser.R;

public class EditTextSwitchPreference extends EditTextPreference {

    private String EditTextSwitchKey;
    private boolean EditTextSwitchKeyDefaultValue;
    private boolean switchAttached = false;

    public EditTextSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        EditTextSwitchKey = null;
        EditTextSwitchKeyDefaultValue = false;
        TypedArray valueArray;
        if (attrs != null) {
            valueArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.EditTextSwitchPreference, 0, 0);
            EditTextSwitchKey = valueArray.getString(R.styleable.EditTextSwitchPreference_editTextSwitchKey);
            EditTextSwitchKeyDefaultValue = valueArray.getBoolean(R.styleable.EditTextSwitchPreference_editTextSwitchKeyDefaultValue, false);
            valueArray.recycle();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        final ViewGroup rootView;
        final MaterialCheckBox onOffSwitch;
        final CompoundButton.OnCheckedChangeListener checkedChangeListener;
        Context context = getContext();
        super.onBindViewHolder(holder);
        rootView = (ViewGroup) holder.itemView;

        if (!switchAttached && (EditTextSwitchKey != null)) {
            onOffSwitch = new MaterialCheckBox(context);
            rootView.addView(onOffSwitch);
            switchAttached = true;
            onOffSwitch.setChecked(sp.getBoolean(EditTextSwitchKey, EditTextSwitchKeyDefaultValue));
            checkedChangeListener = (buttonView, isChecked) -> {
                if (EditTextSwitchKey != null) {
                    sp.edit().putBoolean(EditTextSwitchKey, isChecked).apply();
                }
            };
            onOffSwitch.setOnCheckedChangeListener(checkedChangeListener);
            checkedChangeListener.onCheckedChanged(onOffSwitch, onOffSwitch.isChecked());
        }

        if ((EditTextSwitchKey != null)) {
            switch (EditTextSwitchKey) {
                case "filter_01":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.red, null));
                    break;
                case "filter_02":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.pink, null));
                    break;
                case "filter_03":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
                    break;
                case "filter_04":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.blue, null));
                    break;
                case "filter_05":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.teal, null));
                    break;
                case "filter_06":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.green, null));
                    break;
                case "filter_07":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.lime, null));
                    break;
                case "filter_08":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.yellow, null));
                    break;
                case "filter_09":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.orange, null));
                    break;
                case "filter_10":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.brown, null));
                    break;
                case "filter_11":
                    holder.itemView.setBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.grey, null));
                    break;
                case "filter_12":
                    TypedValue typedValue = new TypedValue();
                    context.getTheme().resolveAttribute(R.attr.colorSurfaceVariant, typedValue, true);
                    int color = typedValue.data;
                    holder.itemView.setBackgroundColor(color);
                    break;
            }
        }
    }
}