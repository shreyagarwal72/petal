package com.petal.browser.view;

import static android.view.View.GONE;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

import com.petal.browser.R;

public class AdapterSettingsMenu extends RecyclerView.Adapter<AdapterSettingsMenu.ViewHolder> {

    private final List<MenuItem> itemList;

    public AdapterSettingsMenu(List<MenuItem> itemList) {
        this.itemList = itemList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checkbox, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MenuItem item = itemList.get(position);

        holder.textView.setText(item.getTitle());
        holder.imageView.setImageResource(item.getIconResId());

        // 1. WICHTIG: Eventuelle alte Listener entfernen, bevor der Status gesetzt wird
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.itemView.setOnClickListener(null);

        // 2. Visuellen Status setzen
        holder.checkBox.setChecked(item.isSelected());
        if (item.isSelected()) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // Sanftes Blau
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE);
        }

        // 3. Listener für die Checkbox (falls direkt auf das Häkchen getippt wird)
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSelected(isChecked);
            // Hintergrundfarbe der Karte sofort anpassen
            if (isChecked) {
                holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
            } else {
                holder.cardView.setCardBackgroundColor(Color.WHITE);
            }
        });

        // 4. Listener für die gesamte Zeile (schaltet den Status um)
        holder.itemView.setOnClickListener(v -> {
            boolean nextState = !item.isSelected();
            item.setSelected(nextState);
            // Nutzt die aktuelle Position des ViewHolders für ein sauberes UI-Update
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        final ImageView imageView;
        final CheckBox checkBox;
        final CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.titleView);
            imageView = itemView.findViewById(R.id.item_icon);
            checkBox = itemView.findViewById(R.id.item_checkBox);
            cardView = itemView.findViewById(R.id.item_cardView);

            TextView tv = itemView.findViewById(R.id.dateView);
            tv.setVisibility(GONE);
        }
    }
}
