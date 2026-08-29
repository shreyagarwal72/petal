package com.petal.browser.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import java.util.List;

import com.petal.browser.R;

public class GridAdapter extends BaseAdapter {
    private final List<GridItem> list;
    private final Context context;

    public GridAdapter(Context context, List<GridItem> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        Holder holder;
        View view = convertView;

        if (view == null) {

            GridItem item = list.get(position);
            String text = item.getTitle();

            view = LayoutInflater.from(context).inflate(R.layout.item_menu, parent, false);
            holder = new Holder();
            holder.title = view.findViewById(R.id.menuEntry);
            holder.title.setText(text);
            holder.cardView = view.findViewById(R.id.menuCardView);
            holder.iconMenu = view.findViewById(R.id.iconMenu);

            try {
                holder.iconMenu.setImageResource(item.getData());
            } catch (Exception e) {
                Log.i("Petal", "Exception:" + e);
            }

            if (sp.getString("showFilterDialogX", "false").equals("true")) {
                if (text.equals(sp.getString("icon_01", context.getResources().getString(R.string.color_red)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.red, null));
                else if (text.equals(sp.getString("icon_02", context.getResources().getString(R.string.color_pink)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.pink, null));
                else if (text.equals(sp.getString("icon_03", context.getResources().getString(R.string.color_purple)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
                else if (text.equals(sp.getString("icon_04", context.getResources().getString(R.string.color_blue)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.blue, null));
                else if (text.equals(sp.getString("icon_05", context.getResources().getString(R.string.color_teal)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.teal, null));
                else if (text.equals(sp.getString("icon_06", context.getResources().getString(R.string.color_green)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.green, null));
                else if (text.equals(sp.getString("icon_07", context.getResources().getString(R.string.color_lime)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.lime, null));
                else if (text.equals(sp.getString("icon_08", context.getResources().getString(R.string.color_yellow)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.yellow, null));
                else if (text.equals(sp.getString("icon_09", context.getResources().getString(R.string.color_orange)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.orange, null));
                else if (text.equals(sp.getString("icon_10", context.getResources().getString(R.string.color_brown)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.brown, null));
                else if (text.equals(sp.getString("icon_11", context.getResources().getString(R.string.color_grey)))) holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.grey, null));
                else if (text.equals(sp.getString("icon_12", context.getResources().getString(R.string.setting_theme_system)))) {
                    TypedValue typedValue = new TypedValue();
                    context.getTheme().resolveAttribute(R.attr.colorSecondaryContainer, typedValue, true);
                    int color = typedValue.data;
                    holder.cardView.setCardBackgroundColor(color);
                }
            } else {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(android.R.color.transparent, typedValue, true);
                int color = typedValue.data;
                holder.iconMenu.setVisibility(View.VISIBLE);
                holder.cardView.setCardBackgroundColor(color);
            }
            view.setTag(holder);
        }
        return view;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int arg0) {
        return list.get(arg0);
    }

    @Override
    public long getItemId(int arg0) {
        return arg0;
    }

    private static class Holder {
        TextView title;
        CardView cardView;
        ImageView iconMenu;
    }
}
