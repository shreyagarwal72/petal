package com.petal.browser.view;

import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Objects;

import com.petal.browser.R;
import com.petal.browser.objects.CustomRedirect;
import com.petal.browser.objects.CustomRedirectsHelper;
import com.petal.browser.unit.HelperUnit;

public class AdapterCustomRedirect extends RecyclerView.Adapter<RedirectsViewHolder> {
    final private ArrayList<CustomRedirect> redirects;
    private final Context context;

    public AdapterCustomRedirect(ArrayList<CustomRedirect> redirects, Context context) {
        super();
        this.redirects = redirects;
        this.context = context;
    }

    @NonNull
    @Override
    public RedirectsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_checkbox, parent, false);
        return new RedirectsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RedirectsViewHolder holder, int position) {

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        CustomRedirect current = redirects.get(position);
        TextView source = holder.itemView.findViewById(R.id.titleView);
        TextView target = holder.itemView.findViewById(R.id.dateView);
        ImageView remove = holder.itemView.findViewById(R.id.item_icon);
        CardView albumCardView = holder.itemView.findViewById(R.id.item_CardViewItem);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true);
        int color = typedValue.data;
        albumCardView.setCardBackgroundColor(color);

        CheckBox checkbox_redirect = holder.itemView.findViewById(R.id.item_checkBox);
        checkbox_redirect.setChecked(sp.getBoolean(current.getSource(), false));
        checkbox_redirect.setOnClickListener(v -> {
            sp.edit().putBoolean(current.getSource(), checkbox_redirect.isChecked()).apply();
            checkbox_redirect.setChecked(sp.getBoolean(current.getSource(), true));
        });

        remove.setVisibility(VISIBLE);
        source.setText(current.getSource());
        target.setText(current.getTarget());
        remove.setOnClickListener(v -> {
            Snackbar snackbar = Snackbar.make(holder.itemView, R.string.hint_database, Snackbar.LENGTH_SHORT);
            HelperUnit.makeSnackbarRound(snackbar);
            snackbar.setAction(context.getString(R.string.app_ok), (v2 -> {
                redirects.remove(position);
                sp.edit().remove(redirects.get(position).getSource()).apply();
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, getItemCount());
                try {
                    CustomRedirectsHelper.saveRedirects(redirects);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }));
            snackbar.show();
        });

        LinearLayout textGroup = holder.itemView.findViewById(R.id.textGroup);
        textGroup.setOnClickListener((iV) -> {

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            View dialogView = View.inflate(context, R.layout.create_new_redirect, null);
            TextInputEditText source2 = dialogView.findViewById(R.id.source);
            TextInputEditText target2 = dialogView.findViewById(R.id.target);
            source2.setText(source.getText());
            target2.setText(target.getText());

            builder.setTitle(R.string.privacy_redirect_title);
            builder.setIcon(R.drawable.icon_redirect);
            builder.setNegativeButton(R.string.app_cancel, null);
            builder.setPositiveButton(R.string.app_ok, ((dialogInterface, i) -> {
                String sourceText = Objects.requireNonNull(source2.getText()).toString();
                String targetText = Objects.requireNonNull(target2.getText()).toString();
                if (targetText.isEmpty() && sourceText.isEmpty()) {
                    NinjaToast.show(context, R.string.toast_input_empty);
                    return;
                }
                this.addRedirect(new CustomRedirect(sourceText, targetText));
                try {
                    CustomRedirectsHelper.saveRedirects(this.getRedirects());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }));
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            dialog.show();
            HelperUnit.setupDialog(context, dialog);
        });

    }

    @Override
    public int getItemCount() {
        return redirects.size();
    }

    public ArrayList<CustomRedirect> getRedirects() {
        return redirects;
    }

    public void addRedirect(CustomRedirect redirect) {
        redirects.add(redirect);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putBoolean(redirect.getSource(), true).apply();
        notifyItemInserted(getItemCount() - 1);
    }
}

