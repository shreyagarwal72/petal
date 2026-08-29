package com.petal.browser.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.util.ArrayList;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.objects.CustomRedirect;
import com.petal.browser.objects.CustomSearchesHelper;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;

public class AdapterCustomSearches extends RecyclerView.Adapter<RedirectsViewHolder> {
    final private ArrayList<CustomRedirect> redirects;
    private final Context context;
    private final String url;

    public AdapterCustomSearches(Context context, String url, ArrayList<CustomRedirect> redirects) {
        super();
        this.redirects = redirects;
        this.context = context;
        this.url = url;
    }

    @NonNull
    @Override
    public RedirectsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_list, parent, false);
        return new RedirectsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RedirectsViewHolder holder, int position) {
        CustomRedirect current = redirects.get(position);
        TextView source = holder.itemView.findViewById(R.id.titleView);
        TextView target = holder.itemView.findViewById(R.id.dateView);
        ImageView remove = holder.itemView.findViewById(R.id.iconView);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true);
        int color = typedValue.data;
        CardView albumCardView = holder.itemView.findViewById(R.id.item_CardViewItem);
        albumCardView.setCardBackgroundColor(color);
        CardView cardView = holder.itemView.findViewById(R.id.item_cardView);
        cardView.setVisibility(GONE);



        remove.setVisibility(VISIBLE);
        source.setText(current.getSource());
        target.setText(current.getTarget());
        HelperUnit.setHighLightedText(context, target, current.getTarget(), HelperUnit.domain(current.getTarget()));

        remove.setOnClickListener(v -> {
            Snackbar snackbar = Snackbar.make(holder.itemView, R.string.hint_database, Snackbar.LENGTH_SHORT);
            HelperUnit.makeSnackbarRound(snackbar);
            snackbar.setAction(context.getString(R.string.app_ok), (v2 -> {
                redirects.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, getItemCount());
                try {
                    CustomSearchesHelper.saveRedirects(redirects);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }));
            snackbar.show();
        });
        holder.itemView.setOnClickListener(v -> {
            NinjaWebView.getBrowserController().hideSearch();
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            sp.edit().putString("sp_search_customSearches", current.getTarget()).apply();
            String t = BrowserUnit.queryWrapper(context, url);

            if (BrowserUnit.isURL(t)) {
                BrowserUnit.intentURL(context, Uri.parse(t));
                sp.edit().putString("sp_search_customSearches", "").apply();
            } else {
                NinjaToast.show(BrowserActivity.getAppContext(), R.string.app_error);
            }
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
        notifyItemInserted(getItemCount() - 1);
    }
}

