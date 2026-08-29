package com.petal.browser.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import com.petal.browser.R;
import com.petal.browser.browser.AlbumController;
import com.petal.browser.browser.BrowserContainer;
import com.petal.browser.browser.BrowserController;
import com.petal.browser.unit.HelperUnit;

public class AdapterTabs {

    private final Context context;
    private final AlbumController albumController;
    private View albumView;
    private TextView albumTitle;
    private TextView albumUrl;
    private BrowserController browserController;
    private MaterialCardView albumCardView;

    AdapterTabs(Context context, AlbumController albumController, BrowserController browserController) {
        this.context = context;
        this.albumController = albumController;
        this.browserController = browserController;
        initUI();
    }

    View getAlbumView() {
        return albumView;
    }
    public Object getUrl() {
        return albumUrl.getText().toString();
    }

    void setAlbumTitle(String title, String url) {
        String displayTitle = (title == null || title.isEmpty() || title.equalsIgnoreCase("about:blank") || title.equalsIgnoreCase("Petal Start")) ? "Petal Home" : title;
        String displayUrl = (url == null || url.isEmpty() || url.equalsIgnoreCase("about:blank")) ? "Petal Home" : url;
        albumTitle.setText(displayTitle);
        albumUrl.setText(displayUrl);
        HelperUnit.setHighLightedText(context, albumUrl, displayUrl, HelperUnit.domain(displayUrl));
    }

    void setBrowserController(BrowserController browserController) {
        this.browserController = browserController;
    }

    @SuppressLint("InflateParams")
    private void initUI() {
        albumView = LayoutInflater.from(context).inflate(R.layout.item_list, null, false);
        albumCardView = albumView.findViewById(R.id.item_CardViewItem);
        albumTitle = albumView.findViewById(R.id.titleView);
        albumUrl = albumView.findViewById(R.id.dateView);
        ImageView albumClose = albumView.findViewById(R.id.iconView);
        albumClose.setImageResource(R.drawable.icon_tab_remove);
        albumClose.setVisibility(View.VISIBLE);
        albumClose.setOnClickListener(view -> {
            browserController.removeAlbum(albumController);
            browserController.showOverview();
        });
        assert albumCardView != null;
        albumView.setOnLongClickListener(v -> {
            browserController.showOverflow(null, albumCardView, 1, albumTitle.getText().toString(), albumUrl.getText().toString(), null, null, 0);
            return true;
        });
    }

    public void activate() {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorPrimaryInverse, typedValue, true);
        int color = typedValue.data;
        context.getTheme().resolveAttribute(R.attr.colorSurface, typedValue, true);
        albumCardView.setCardBackgroundColor(color);
        albumTitle.setTypeface(null, Typeface.BOLD);
        albumView.setOnClickListener(view -> {
            albumCardView.setCardBackgroundColor(color);
            browserController.hideOverview();
        });
    }

    void deactivate() {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHighest, typedValue, true);
        int color = typedValue.data;
        albumCardView.setCardBackgroundColor(color);
        albumTitle.setTypeface(null, Typeface.NORMAL);
        albumView.setOnClickListener(view -> {
            browserController.showAlbum(albumController);
            browserController.hideOverview();
        });
    }
}