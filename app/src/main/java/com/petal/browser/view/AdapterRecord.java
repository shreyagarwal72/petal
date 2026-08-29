package com.petal.browser.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import com.petal.browser.R;
import com.petal.browser.database.FaviconHelper;
import com.petal.browser.database.Record;
import com.petal.browser.unit.HelperUnit;

public class AdapterRecord extends ArrayAdapter<Record> {
    private final Context context;
    private final int layoutResId;
    private final List<Record> list;

    private static class Holder {
        TextView title;
        TextView time;
        ImageView favicon;
        MaterialCardView cardView;
    }

    public AdapterRecord(Context context, List<Record> list) {
        super(context, R.layout.item_list, list);
        this.context = context;
        this.layoutResId = R.layout.item_list;
        this.list = list;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Holder holder;
        View view = convertView;

        if (view == null) {
            view = LayoutInflater.from(context).inflate(layoutResId, parent, false);
            holder = new Holder();
            holder.title = view.findViewById(R.id.titleView);
            holder.time = view.findViewById(R.id.dateView);
            holder.favicon = view.findViewById(R.id.item_icon);
            holder.cardView = view.findViewById(R.id.item_CardViewItem);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }

        Record record = list.get(position);
        long filter = record.getIconColor();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
        String text;

        if (record.getTime() > 1) {
            text = sdf.format(record.getTime()) + ": " + record.getURL();
        } else {
            text = record.getURL();
        }

        holder.title.setText(record.getTitle());
        holder.time.setText(text);

        if (filter == 11) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.red, null));
        } else if (filter == 10) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.pink, null));
        } else if (filter == 9) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
        } else if (filter == 8) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.blue, null));
        } else if (filter == 7) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.teal, null));
        } else if (filter == 6) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.green, null));
        } else if (filter == 5) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.lime, null));
        } else if (filter == 4) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.yellow, null));
        } else if (filter == 3) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.orange, null));
        } else if (filter == 2) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.brown, null));
        } else if (filter == 1) {
            holder.cardView.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.grey, null));
        } else {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true);
            int color = typedValue.data;
            holder.cardView.setCardBackgroundColor(color);
        }

        try(FaviconHelper faviconHelper = new FaviconHelper(context)) {
            Bitmap bitmap = faviconHelper.getFavicon(record.getURL());
            if (bitmap != null) {
                holder.favicon.setImageBitmap(bitmap);
            } else {
                holder.favicon.setImageResource(R.drawable.icon_image_broken);
            }
        }

        HelperUnit.setHighLightedText(context, holder.time, record.getURL(), HelperUnit.domain(record.getURL()));
        return view;
    }
}