package com.petal.browser.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.petal.browser.R;
import com.petal.browser.database.FaviconHelper;
import com.petal.browser.database.Record;
import com.petal.browser.unit.HelperUnit;


public class AdapterSearch extends BaseAdapter implements Filterable {
    private final Context context;
    private final int layoutResId;
    private final List<String> liveSuggestions = new ArrayList<>();
    private final List<CompleteItem> originalList;
    private List<CompleteItem> resultList;
    private int count = 0;
    private final CompleteFilter filter = new CompleteFilter();

    public synchronized void setLiveSuggestions(List<String> suggestions) {
        liveSuggestions.clear();
        if (suggestions != null) {
            liveSuggestions.addAll(suggestions);
        }
        filter.refilter();
    }

    public AdapterSearch(Context context, int layoutResId, List<Record> recordList) {
        this.context = context;
        this.layoutResId = layoutResId;
        this.originalList = new ArrayList<>();
        this.resultList = new ArrayList<>();
        getRecordList(recordList);
    }

    private void getRecordList(List<Record> recordList) {
        for (Record record : recordList) {
            if (record.getTitle() != null
                    && !record.getTitle().isEmpty()
                    && record.getURL() != null
                    && !record.getURL().isEmpty()) {
                originalList.add(new CompleteItem(record.getTitle(), record.getURL()));
            }
        }

        Set<CompleteItem> set = new HashSet<>(originalList);
        originalList.clear();
        originalList.addAll(set);
    }

    @Override
    public int getCount() {
        if (count > 0) {
            return resultList.size();
        } else {
            return 0;
        }
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    @Override
    public Object getItem(int position) {
        return resultList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        Holder holder;

        if (view == null) {
            view = LayoutInflater.from(context).inflate(layoutResId, null, false);
            holder = new Holder();
            holder.titleView = view.findViewById(R.id.titleView);
            holder.urlView = view.findViewById(R.id.dateView);
            holder.favicon = view.findViewById(R.id.item_icon);
            holder.albumCardView = view.findViewById(R.id.item_CardViewItem);
            view.setTag(holder);
        } else {
            holder = (Holder) view.getTag();
        }

        CompleteItem item = resultList.get(position);
        holder.titleView.setText(item.title);
        holder.urlView.setText(item.url);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true);
        int color = typedValue.data;
        holder.albumCardView.setCardBackgroundColor(color);

        holder.favicon.setImageResource(R.drawable.icon_image_broken);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String s = sp.getString("searchInput", "");
        if (!s.isEmpty()) {
            HelperUnit.setHighLightedTextSearch(context, holder.urlView, s);
            HelperUnit.setHighLightedTextSearch(context, holder.titleView, s);
        }
        return view;
    }

    private static class CompleteItem {
        private final String title;
        private final String url;
        private int index = Integer.MAX_VALUE;

        private CompleteItem(String title, String url) {
            this.title = title;
            this.url = url;
        }

        String getTitle() {
            return title;
        }

        String getURL() {
            return url;
        }

        int getIndex() {
            return index;
        }

        void setIndex(int index) {
            this.index = index;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof CompleteItem)) {
                return false;
            }
            CompleteItem item = (CompleteItem) object;
            return item.getTitle().equals(title) && item.getURL().equals(url);
        }

        @Override
        public int hashCode() {
            if (title == null || url == null) {
                return 0;
            }
            return title.hashCode() & url.hashCode();
        }
    }

    private static class Holder {
        private ImageView favicon;
        private TextView titleView;
        private TextView urlView;
        private MaterialCardView albumCardView;
    }

    private class CompleteFilter extends Filter {
        private CharSequence lastConstraint = "";

        public void refilter() {
            filter(lastConstraint);
        }

        @Override
        protected FilterResults performFiltering(CharSequence prefix) {
            if (prefix == null) {
                return new FilterResults();
            }
            lastConstraint = prefix;

            List<CompleteItem> workList = new ArrayList<>();
            Set<String> addedTitles = new HashSet<>();

            // 1. Add Live Google Search Recommendations
            synchronized (AdapterSearch.this) {
                for (String suggestion : liveSuggestions) {
                    if (suggestion != null && !suggestion.trim().isEmpty() && !addedTitles.contains(suggestion.toLowerCase())) {
                        CompleteItem sugItem = new CompleteItem(suggestion, suggestion);
                        sugItem.setIndex(-1); // Top priority
                        workList.add(sugItem);
                        addedTitles.add(suggestion.toLowerCase());
                    }
                }
            }

            // 2. Add matching local history & bookmarks
            for (CompleteItem item : originalList) {
                String titleLower = item.getTitle() != null ? item.getTitle().toLowerCase() : "";
                String urlLower = item.getURL() != null ? item.getURL().toLowerCase() : "";
                String prefixLower = prefix.toString().toLowerCase();

                if (titleLower.contains(prefixLower) || urlLower.contains(prefixLower)) {
                    if (!addedTitles.contains(titleLower)) {
                        int index = titleLower.indexOf(prefixLower);
                        if (index < 0) index = urlLower.indexOf(prefixLower);
                        item.setIndex(index >= 0 ? index + 10 : 100);
                        workList.add(item);
                        addedTitles.add(titleLower);
                    }
                }
            }

            workList.sort(Comparator.comparingInt(CompleteItem::getIndex));
            FilterResults results = new FilterResults();
            results.values = workList;
            results.count = workList.size();
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            count = results != null ? results.count : 0;
            if (results != null && results.count > 0) {
                resultList = (List<CompleteItem>) results.values;
                notifyDataSetChanged();
            } else {
                resultList = new ArrayList<>();
                notifyDataSetInvalidated();
            }
        }
    }
}