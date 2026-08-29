package com.petal.browser.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import com.petal.browser.R;

public class AdapterProfileList extends ArrayAdapter<String> {
    private final Context context;
    private final int layoutResId;
    private final List<String> list;

    public AdapterProfileList(Context context, List<String> list) {
        super(context, R.layout.item_list, list);
        this.context = context;
        this.layoutResId = R.layout.item_menu;
        this.list = list;
    }

    @SuppressWarnings("NullableProblems")
    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        Holder holder;
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(layoutResId, parent, false);
            holder = new Holder();
            holder.domain = view.findViewById(R.id.menuEntry);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }
        holder.domain.setText(list.get(position));
        return view;
    }

    private static class Holder {
        TextView domain;
    }
}
