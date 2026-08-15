package com.petal.browser.activity;

import static android.content.ContentValues.TAG;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.Manifest;
import android.animation.ObjectAnimator;
import androidx.dynamicanimation.animation.SpringForce;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.widget.PopupWindow;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;

import com.petal.browser.compose.downloads.PetalDownloadBridge;
import com.petal.browser.compose.home.PetalComposeBridge;
import com.petal.browser.compose.home.PetalHomeActionHandler;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.webkit.WebViewFeature;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.petal.browser.R;
import com.petal.browser.browser.AlbumController;
import com.petal.browser.browser.BannerBlock;
import com.petal.browser.browser.BrowserContainer;
import com.petal.browser.browser.BrowserController;
import com.petal.browser.browser.DataURIParser;
import com.petal.browser.browser.List_standard;
import com.petal.browser.database.FaviconHelper;
import com.petal.browser.database.Record;
import com.petal.browser.database.RecordAction;
import com.petal.browser.dialogs.CustomRedirectsDialog;
import com.petal.browser.fragment.Fragment_settings_Backup;
import com.petal.browser.objects.CustomRedirect;
import com.petal.browser.objects.CustomSearchesHelper;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.unit.RecordUnit;
import com.petal.browser.view.AdapterCustomSearches;
import com.petal.browser.view.AdapterMenu;
import com.petal.browser.view.AdapterSearch;
import com.petal.browser.view.GridAdapter;
import com.petal.browser.view.GridItem;
import com.petal.browser.view.MenuItem;
import com.petal.browser.view.NinjaToast;
import com.petal.browser.view.NinjaWebView;
import com.petal.browser.view.AdapterRecord;
import com.petal.browser.view.SwipeTouchListener;

public class BrowserActivity extends AppCompatActivity implements BrowserController {

    // Menus
    private static final int INPUT_FILE_REQUEST_CODE = 1;
    private AdapterRecord adapter;
    private ImageButton fab_overview;
    private ListView listView;

    // Views
    private TextInputEditText search_input;
    private TextView appBar_title;
    private EditText searchOnSiteInput;
    @SuppressLint("StaticFieldLeak")
    private static NinjaWebView ninjaWebView;
    private View customView;
    private VideoView videoView;
    private boolean isMediaPlaying = false;
    private FloatingActionButton fab_menu;
    private BadgeDrawable badgeDrawable;
    private AdapterSearch adapterSearch;
    private MaterialCardView searchOnSiteLayout;

    // Layouts
    private LinearProgressIndicator progressBar;
    private com.petal.browser.ui.components.PullToRefreshFrameLayout contentFrame;
    private LinearLayout tab_container;
    private FrameLayout fullscreenHolder;
    private com.petal.browser.compose.composable.PetalRefreshBarState refreshState = new com.petal.browser.compose.composable.PetalRefreshBarState();
    private ListView list_search;

    // Others
    private BottomNavigationView bottom_navigation;
    private String overViewTab;
    private Activity activity;
    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private SharedPreferences sp;
    private List_standard listStandard;
    private long newIcon;
    private long filterBy;
    private boolean filter;
    private ValueCallback<Uri[]> filePathCallback = null;
    private AlbumController currentAlbumController = null;
    private ValueCallback<Uri[]> mFilePathCallback;
    private com.petal.browser.media.PetalMediaSessionService mediaService;
    private boolean isMediaBound = false;
    private final ServiceConnection mediaConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            com.petal.browser.media.PetalMediaSessionService.LocalBinder binder = (com.petal.browser.media.PetalMediaSessionService.LocalBinder) service;
            mediaService = binder.getService();
            isMediaBound = true;
            if (mediaService != null) {
                mediaService.setMediaControlListener(new com.petal.browser.media.PetalMediaSessionService.MediaControlListener() {
                    @Override
                    public void onPlay() {
                        if (ninjaWebView != null && ninjaWebView.getMediaBridge() != null) {
                            ninjaWebView.getMediaBridge().playMedia();
                        }
                    }

                    @Override
                    public void onPause() {
                        if (ninjaWebView != null && ninjaWebView.getMediaBridge() != null) {
                            ninjaWebView.getMediaBridge().pauseMedia();
                        }
                    }

                    @Override
                    public void onStop() {
                        if (ninjaWebView != null && ninjaWebView.getMediaBridge() != null) {
                            ninjaWebView.getMediaBridge().pauseMedia();
                        }
                    }

                    @Override
                    public void onSeekTo(long positionMs) {
                        if (ninjaWebView != null && ninjaWebView.getMediaBridge() != null) {
                            ninjaWebView.getMediaBridge().seekMediaTo(positionMs);
                        }
                    }
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaService = null;
            isMediaBound = false;
        }
    };

    public static Context getAppContext() {
        return context;
    }
    private AlertDialog dialogOverview;

    private AlertDialog dialog_overflow;
    private AlertDialog dialogSearch;
    private View dialogViewSearch;
    private AlertDialog dialogCustomSearches;
    private CardView appBar;
    private View contentView;

    private AlbumController nextAlbumController(boolean next) {
        if (BrowserContainer.size() <= 1) return currentAlbumController;
        List<AlbumController> list = BrowserContainer.list();
        int index = list.indexOf(currentAlbumController);
        if (next) {
            index++;
            if (index >= list.size()) index = 0; }
        else {
            index--;
            if (index < 0) index = list.size() - 1; }
        return list.get(index);
    }

    private class VideoCompletionListener implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            return false;
        }
        @Override
        public void onCompletion(MediaPlayer mp) {
            onHideCustomView();
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(newBase);
        String lang = sp.getString("sp_app_language", "system");
        if (lang != null && !lang.equals("system")) {
            Locale locale = Locale.forLanguageTag(lang);
            Locale.setDefault(locale);
            android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
            config.setLocale(locale);
            newBase = newBase.createConfigurationContext(config);
        }
        super.attachBaseContext(newBase);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        context = this;
        activity = this;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channelDownloads = new NotificationChannel("download_channel", "Downloads", NotificationManager.IMPORTANCE_HIGH);
                channelDownloads.setDescription("Live real-time alerts for active downloads");
                nm.createNotificationChannel(channelDownloads);
                NotificationChannel channelGeneral = new NotificationChannel("1", "General", NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(channelGeneral);
            }
        }
        
        sp = PreferenceManager.getDefaultSharedPreferences(context);

        try {
            Intent mediaServiceIntent = new Intent(this, com.petal.browser.media.PetalMediaSessionService.class);
            bindService(mediaServiceIntent, mediaConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Error binding PetalMediaSessionService", e);
        }

        try {
            new BannerBlock(context);
        } catch (Exception ignored) {}
        HelperUnit.initTheme(activity);

        if (sp.getBoolean("sp_screenOn", false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (sp.getBoolean("sp_standard_restart", false)) sp.edit().putString("profile", "profileStandard").apply();

        sp.edit()
                .putInt("restart_changed", 0)
                .putBoolean("pdf_create", false)
                .putBoolean("show_overview", true)
                .putString("openBackground_dialog", "show").apply();

        if (Objects.requireNonNull(sp.getString("start_tab", "3")).equals("4")) {
            overViewTab = getString(R.string.album_title_history);
        } else {
            overViewTab = getString(R.string.album_title_bookmarks);
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        contentFrame = findViewById(R.id.main_content);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_background));
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(false);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, isKeyboardVisible ? keyboardHeight : 0);

            View bottomNavContainer = findViewById(R.id.bottom_nav_container);
            if (bottomNavContainer != null) {
                bottomNavContainer.setPadding(0, 0, 0, isKeyboardVisible ? 0 : systemBars.bottom);
                bottomNavContainer.setVisibility(isKeyboardVisible ? View.GONE : View.VISIBLE);
            }
            return insets;
        });

        MaterialAlertDialogBuilder builderOverview = new MaterialAlertDialogBuilder(context);
        View dialogViewOverview = View.inflate(context, R.layout.dialog_overview, null);
        builderOverview.setView(dialogViewOverview);
        dialogOverview = builderOverview.create();
        bottom_navigation = dialogViewOverview.findViewById(R.id.bottom_navigation);
        tab_container = dialogViewOverview.findViewById(R.id.listTabs);
        HelperUnit.setupDialog(context, dialogOverview);

        MaterialAlertDialogBuilder builderSearch = new MaterialAlertDialogBuilder(context);
        dialogViewSearch = View.inflate(context, R.layout.dialog_search, null);
        builderSearch.setView(dialogViewSearch);
        dialogSearch = builderSearch.create();
        HelperUnit.setupDialog(context, dialogSearch);

        BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String text = getString(R.string.app_done) + ". " + getString(R.string.menu_download) + "?";
                    View anchor = contentFrame != null ? contentFrame : getWindow().getDecorView();
                    Snackbar snackbar = Snackbar.make(anchor, text, Snackbar.LENGTH_LONG);
                    HelperUnit.makeSnackbarRound(snackbar);
                    snackbar.setAction(context.getString(R.string.app_ok), v -> showDownloads());
                    snackbar.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

        initOmniBox();
        initSearchOnSite();
        initPullToRefresh();
        initOverview();
        hideSearch();
        dispatchIntent(getIntent());

        if (sp.getBoolean("sp_check_update_on_launch", true)) {
            com.petal.browser.unit.UpdateUnit.checkForUpdates(this, true);
        }

        //restore open Tabs from shared preferences if app got killed
        if (sp.getBoolean("sp_restoreTabs", false)
                || sp.getBoolean("sp_reloadTabs", false)
                || sp.getBoolean("restoreOnRestart", false)) {
            String saveDefaultProfile = sp.getString("profile", "profileStandard");
            ArrayList<String> openTabs;
            openTabs = new ArrayList<>(Arrays.asList(TextUtils.split(sp.getString("openTabs", ""), "‚‗‚")));
            if (!openTabs.isEmpty()) {
                for (int counter = 0; counter < openTabs.size(); counter++) {
                    addAlbum(getString(R.string.app_name), openTabs.get(counter), BrowserContainer.size() < 1);
                }
            }
            sp.edit().putString("profile", saveDefaultProfile).apply();
            sp.edit().putBoolean("restoreOnRestart", false).apply();
        }
        //if still no open Tab open default page
        if (BrowserContainer.size() < 1) {
            addAlbum(getString(R.string.app_name), sp.getString("favoriteURL", "about:blank"), true);
        }

        // Welcome and Search Engine dialogs are displayed in onStart() to ensure the Activity window and decor view are fully attached.
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            com.petal.browser.account.GoogleAccountManager.INSTANCE.init(this);
            com.petal.browser.account.GoogleAccountManager.checkAndSyncGoogleAccount(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (sp != null) {
            if (!sp.getBoolean("sp_welcome_shown", false)) {
                sp.edit().putBoolean("sp_welcome_shown", true).apply();
                try {
                    com.petal.browser.ui.components.PetalWelcomeBridge.showWelcomeDialog(this, () -> {
                        if (!sp.getBoolean("sp_search_engine_chosen", false)) {
                            com.petal.browser.ui.components.PetalSearchEngineBridge.showSearchEngineDialog(BrowserActivity.this, null);
                        }
                        return kotlin.Unit.INS