package com.petal.browser.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

import com.petal.browser.R;

public class AdapterMenu extends RecyclerView.Adapter<AdapterMenu.ViewHolder> {

    private final List<MenuItem> gridItems;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MenuItem item);
    }

    public AdapterMenu(List<MenuItem> gridItems, OnItemClickListener listener) {
        this.gridItems = gridItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_overflow, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MenuItem item = gridItems.get(position);
        holder.titleTextView.setText(item.getTitle());
        holder.iconImageView.setImageResource(item.getIconResId());
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return gridItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconImageView;
        final TextView titleTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.gridIcon);
            titleTextView = itemView.findViewById(R.id.gridTitle);
        }
    }
}
