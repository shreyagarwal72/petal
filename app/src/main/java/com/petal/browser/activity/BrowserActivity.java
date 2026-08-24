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
import androidx.appcompat.app.AppCompatDelegate;
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
import android.graphics.Bitmap;
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
import android.speech.RecognizerIntent;
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
    /**
     * True during the very first onResume() that immediately follows onCreate().
     * dispatchIntent() is called at the end of onCreate() (after all tabs are ready),
     * so we must skip the redundant call in onResume() to avoid a double-dispatch
     * or, worse, a no-op because setAction("") already cleared the intent action.
     */
    private boolean suppressResumeDispatch = false;
    /**
     * A widget action (ACTION_OPEN_SEARCH / _AI_SEARCH / _VOICE) waiting to run once the
     * window has genuine input focus. See {@link #runOrDeferPendingWidgetAction()}.
     */
    private Runnable pendingWidgetAction = null;
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
        String themeConfig = sp.getString("sp_theme_config", "FOLLOW_SYSTEM");
        if ("LIGHT".equals(themeConfig)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if ("DARK".equals(themeConfig)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

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

        if (sp.getBoolean("sp_biometric_lock", false)) {
            com.petal.browser.security.BiometricLockManager.authenticate(
                this,
                "Petal Browser Locked",
                "Authenticate using biometric or PIN to continue",
                new Runnable() {
                    @Override
                    public void run() {
                        // Success: user authenticated
                    }
                },
                new java.util.function.Consumer<String>() {
                    @Override
                    public void accept(String error) {
                        Toast.makeText(BrowserActivity.this, "Biometric authentication required: " + error, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            );
        }

        try {
            new BannerBlock(context);
        } catch (Exception ignored) {}
        HelperUnit.initTheme(activity);
        com.petal.browser.unit.BackupUnit.performAutoVersionBackup(this);

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

            v.setPadding(systemBars.left, 0, systemBars.right, isKeyboardVisible ? keyboardHeight : 0);

            View addressBar = findViewById(R.id.compose_address_bar);
            if (addressBar != null) {
                addressBar.setPadding(0, systemBars.top, 0, 0);
            }

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
        // NOTE: dispatchIntent() is deferred to AFTER tab session restoration below.
        // Calling it here would run before ninjaWebView / currentAlbumController are
        // initialized, so ACTION_VIEW would consume the intent (setAction("")) without
        // actually loading the URL — causing the "only opens on 2nd launch" bug.

        if (sp.getBoolean("sp_check_update_on_launch", true)) {
            com.petal.browser.unit.UpdateUnit.checkForUpdates(this, true);
        }

        // Chrome-style Tab Session Restoration & Rehydration
        try {
            java.util.List<com.petal.browser.unit.TabSessionManager.TabStateRecord> savedSession =
                    com.petal.browser.unit.TabSessionManager.loadSession(this);
            if (savedSession != null && !savedSession.isEmpty()) {
                NinjaWebView activeRestoredWebView = null;
                for (int i = 0; i < savedSession.size(); i++) {
                    com.petal.browser.unit.TabSessionManager.TabStateRecord record = savedSession.get(i);
                    boolean isForegroundTab = record.isActive || (i == 0 && BrowserContainer.size() == 0);
                    NinjaWebView restoredWebView = new NinjaWebView(context);
                    restoredWebView.initPreferences(record.url);

                    // Must be set before loadUrl(): browserController is a static field
                    // that is null on a cold process start, and loadUrl() can trigger
                    // Chromium's onProgressChanged callback (-> updateTitle()) almost
                    // immediately on the main looper, before this restoration loop or
                    // showAlbum() would otherwise get a chance to set it. Without this,
                    // restoring the active tab on app relaunch can NPE crash the app.
                    if (isForegroundTab) {
                        restoredWebView.setBrowserController(this);
                    }

                    if (record.url != null && !record.url.isEmpty()) {
                        restoredWebView.loadUrl(record.url);
                    } else {
                        restoredWebView.loadUrl("about:blank");
                    }

                    if (record.title != null && !record.title.isEmpty()) {
                        restoredWebView.setAlbumTitle(record.title, record.url);
                    }
                    if (record.scrollX > 0 || record.scrollY > 0) {
                        restoredWebView.post(() -> restoredWebView.scrollTo(record.scrollX, record.scrollY));
                    }

                    BrowserContainer.add(restoredWebView);
                    if (isForegroundTab) {
                        activeRestoredWebView = restoredWebView;
                    } else {
                        restoredWebView.deactivate();
                    }
                }
                if (activeRestoredWebView != null) {
                    showAlbum(activeRestoredWebView);
                } else if (BrowserContainer.size() > 0 && BrowserContainer.get(0) instanceof NinjaWebView) {
                    showAlbum(BrowserContainer.get(0));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring tab session", e);
        }

        // Fallback: restore legacy URLs if TabSessionManager had no saved records
        if (BrowserContainer.size() < 1 && (sp.getBoolean("sp_restoreTabs", false)
                || sp.getBoolean("sp_reloadTabs", false)
                || sp.getBoolean("restoreOnRestart", false))) {
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

        // If still no open tab, open default page
        if (BrowserContainer.size() < 1) {
            addAlbum(getString(R.string.app_name), sp.getString("favoriteURL", "about:blank"), true);
        }

        // Now that ninjaWebView and currentAlbumController are fully initialized,
        // dispatch any incoming intent (e.g. ACTION_VIEW from an external link).
        // We flag suppressResumeDispatch so onResume() won't double-dispatch it.
        suppressResumeDispatch = true;
        dispatchIntent(getIntent());

        // Welcome and Search Engine dialogs are displayed in onStart() to ensure the Activity window and decor view are fully attached.
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            com.petal.browser.account.GoogleAccountManager.INSTANCE.init(this);
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
                        return kotlin.Unit.INSTANCE;
                    });
                } catch (Exception ignored) {}
            } else if (!sp.getBoolean("sp_search_engine_chosen", false)) {
                com.petal.browser.ui.components.PetalSearchEngineBridge.showSearchEngineDialog(this, null);
            }
        }
    }

    private static final int VOICE_SEARCH_REQUEST_CODE = 1002;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE_SEARCH_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String query = matches.get(0);
                if (query != null && !query.trim().isEmpty()) {
                    String targetUrl = BrowserUnit.queryWrapper(this, query.trim());
                    addAlbum(null, targetUrl, true);
                }
            }
            return;
        }
        if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else if (data.getClipData() != null) {
                    final int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
        }
        mFilePathCallback.onReceiveValue(results);
        mFilePathCallback = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatchIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        applyAddressBarPosition();
        if (ninjaWebView != null) {
            ninjaWebView.onResume();
            ninjaWebView.resumeTimers();
        }
        if (sp.getBoolean("sp_camera", false)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
            }
        }
        if (sp.getInt("restart_changed", 1) == 1) {
            triggerRebirth(context);
        }
        if (sp.getBoolean("pdf_create", false)) {
            sp.edit().putBoolean("pdf_create", false).apply();
            String text = getString(R.string.app_done) + ". " + getString(R.string.menu_download) +"?";
            Snackbar snackbar = Snackbar.make(ninjaWebView, text, Snackbar.LENGTH_SHORT);
            HelperUnit.makeSnackbarRound(snackbar);
            snackbar.setAction(context.getString(R.string.app_ok), v -> showDownloads());
            snackbar.show();
        }
        // Skip the first post-onCreate resume — dispatchIntent() already ran at the
        // end of onCreate() once all tabs were ready. Every subsequent resume (coming
        // back from another app, screen-off, etc.) should still dispatch normally.
        // (Widget actions no longer need special handling here — dispatchIntent()
        // now defers them itself via contentFrame.post(), regardless of whether it
        // was called from onCreate() or onNewIntent().)
        if (suppressResumeDispatch) {
            suppressResumeDispatch = false;
        } else {
            dispatchIntent(getIntent());
        }
        View bottomNavContainer = findViewById(R.id.bottom_nav_container);
        if (bottomNavContainer != null) {
            bottomNavContainer.setTranslationY(0f);
            bottomNavContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (isMediaBound) {
                try {
                    unbindService(mediaConnection);
                    isMediaBound = false;
                } catch (Exception ignored) {}
            }
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(1);
            }
            BrowserContainer.clear();
            if (sp != null && sp.getBoolean("sp_clear_quit", false)) {
                BrowserUnit.clearBrowserData(this);
            }
            if (sp != null && sp.getBoolean("sp_backup_quit", false)) {
                Fragment_settings_Backup.backup(activity);
            }
            if (sp != null && (!sp.getBoolean("sp_reloadTabs", false) || sp.getInt("restart_changed", 1) == 1)) {
                sp.edit().putString("openTabs", "").apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in BrowserActivity.onDestroy", e);
        }
        super.onDestroy();
    }

    private long lastBackPressTime = 0;

    /**
     * The actual back-navigation decision logic. Shared between the legacy KEYCODE_BACK
     * path (3-button nav / hardware back) and the OnBackPressedCallback path (gesture
     * nav / predictive back) below, so both routes behave identically.
     */
    private void performBackNavigation() {
        View currentFocus = getCurrentFocus();
        boolean isKeyboardVisible = false;
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mainView);
            if (insets != null) {
                isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            }
        }
        if (isKeyboardVisible) {
            HelperUnit.hideSoftKeyboard(this, currentFocus);
            return;
        }

        if (fullscreenHolder != null || customView != null || videoView != null) {
            Log.v(TAG, "Petal in fullscreen mode");
        } else if (dialogOverview != null && dialogOverview.isShowing()) {
            hideOverview();
        } else if (searchOnSiteLayout != null && searchOnSiteLayout.getVisibility() == VISIBLE){
            searchOnSiteInput.setText("");
            searchOnSiteLayout.setVisibility(GONE);
            appBar.setVisibility(VISIBLE);
        } else if (ninjaWebView != null && (ninjaWebView.canGoBack() || (ninjaWebView.copyBackForwardList() != null && ninjaWebView.copyBackForwardList().getCurrentIndex() > 0))){
            sp.edit().putBoolean("backPressed", true).apply();
            ninjaWebView.goBack();
        } else {
            String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
            if (currentUrl != null && !isHomePage(currentUrl)) {
                ninjaWebView.loadUrl("about:blank");
                showAlbum(currentAlbumController, "about:blank");
            } else {
                boolean requireDoubleBack = sp.getBoolean("sp_double_back_exit", true);
                if (!requireDoubleBack) {
                    finish();
                } else {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastBackPressTime < 2000) {
                        finish();
                    } else {
                        lastBackPressTime = currentTime;
                        NinjaToast.show(BrowserActivity.this, "Press back again to exit Petal");
                    }
                }
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                showOverflow(null, null, 0, ninjaWebView != null ? ninjaWebView.getTitle() : "", ninjaWebView != null ? ninjaWebView.getUrl() : "", null, null, 0);
                return true;
            case KeyEvent.KEYCODE_BACK:
                performBackNavigation();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        try {
            boolean isPipSupported = getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
            boolean isAutoPipEnabled = sp.getBoolean("sp_auto_pip", true);
            boolean isPipPermissionAsked = sp.getBoolean("sp_pip_asked", false);
            boolean hasMediaPlaying = isMediaPlaying || customView != null || fullscreenHolder != null || videoView != null;

            if (isPipSupported && hasMediaPlaying) {
                if (!isPipPermissionAsked) {
                    sp.edit().putBoolean("sp_pip_asked", true).apply();
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Picture-in-Picture Permission")
                            .setMessage("Would you like Petal Browser to automatically enter Picture-in-Picture mode when minimizing the app during video playback?")
                            .setPositiveButton("Allow", (dialog, which) -> {
                                sp.edit().putBoolean("sp_auto_pip", true).apply();
                                triggerSystemPipMode();
                            })
                            .setNegativeButton("Don't Allow", (dialog, which) -> {
                                sp.edit().putBoolean("sp_auto_pip", false).apply();
                            })
                            .show();
                } else if (isAutoPipEnabled) {
                    triggerSystemPipMode();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerSystemPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                android.app.PictureInPictureParams.Builder pipBuilder = new android.app.PictureInPictureParams.Builder();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    pipBuilder.setAutoEnterEnabled(true);
                }
                View targetView = customView != null ? customView : (videoView != null ? videoView : (ninjaWebView != null ? ninjaWebView : contentFrame));
                if (targetView != null && targetView.getWidth() > 0 && targetView.getHeight() > 0) {
                    int width = targetView.getWidth();
                    int height = targetView.getHeight();
                    float ratio = (float) width / (float) height;
                    if (ratio > 2.39f) ratio = 2.39f;
                    if (ratio < 0.418f) ratio = 0.418f;
                    android.util.Rational aspectRatio = new android.util.Rational((int) (ratio * 1000), 1000);
                    pipBuilder.setAspectRatio(aspectRatio);

                    android.graphics.Rect rect = new android.graphics.Rect();
                    targetView.getGlobalVisibleRect(rect);
                    if (!rect.isEmpty()) {
                        pipBuilder.setSourceRectHint(rect);
                    }
                }
                enterPictureInPictureMode(pipBuilder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        try {
            View composeAddressBar = findViewById(R.id.compose_address_bar);
            View bottomNavContainer = findViewById(R.id.bottom_nav_container);
            View refreshBarCompose = findViewById(R.id.refresh_bar_compose);
            View mainProgressBar = findViewById(R.id.main_progress_bar_compose);

            if (isInPictureInPictureMode) {
                if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
                if (bottomNavContainer != null) bottomNavContainer.setVisibility(GONE);
                if (refreshBarCompose != null) refreshBarCompose.setVisibility(GONE);
                if (mainProgressBar != null) mainProgressBar.setVisibility(GONE);
                if (appBar != null) appBar.setVisibility(GONE);
            } else {
                if (composeAddressBar != null) composeAddressBar.setVisibility(VISIBLE);
                if (bottomNavContainer != null) bottomNavContainer.setVisibility(VISIBLE);
                if (refreshBarCompose != null) refreshBarCompose.setVisibility(VISIBLE);
                if (mainProgressBar != null) mainProgressBar.setVisibility(VISIBLE);
                if (appBar != null && currentAlbumController != null && !isHomePage(ninjaWebView != null ? ninjaWebView.getUrl() : "")) {
                    appBar.setVisibility(VISIBLE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onTabUrlStarted(NinjaWebView webView, String url) {
        runOnUiThread(() -> {
            if (webView != ninjaWebView) return;

            // If this WebView is already the one attached and visible (i.e. we're just
            // navigating within the currently-shown tab, not switching tabs or coming
            // from the home screen), do NOT tear down and rebuild the content view.
            // Removing/re-adding the WebView to contentFrame while it is mid-navigation
            // kills its rendering surface, which causes pages to hang on a blank
            // screen with the loading spinner never resolving.
            boolean alreadyAttached = currentAlbumController == webView
                    && contentFrame != null
                    && contentFrame.getChildCount() == 1
                    && contentFrame.getChildAt(0) == webView;

            if (alreadyAttached) {
                updateAddressBar();
                updatePersistentBottomNav();
            } else {
                showAlbum(currentAlbumController, url);
            }
        });
    }

    @Override
    public synchronized void showAlbum(AlbumController controller) {
        showAlbum(controller, null);
    }

    public synchronized void showAlbum(AlbumController controller, String overrideUrl) {
        View av = (View) controller;
        if (currentAlbumController != null) currentAlbumController.deactivate();
        currentAlbumController = controller;
        if (currentAlbumController instanceof NinjaWebView) {
            ninjaWebView = (NinjaWebView) currentAlbumController;
        }
        currentAlbumController.activate();
        contentFrame.removeAllViews();

        String url = overrideUrl != null ? overrideUrl : (ninjaWebView != null ? ninjaWebView.getUrl() : "");
        boolean isIncognitoTab = ninjaWebView != null && ninjaWebView.isIncognito();
        if (isIncognitoTab) {
            com.petal.browser.compose.incognito.PetalIncognitoSessionManager.enableIncognitoSecurity(this);
        } else {
            com.petal.browser.compose.incognito.PetalIncognitoSessionManager.disableIncognitoSecurity(this);
        }
        com.petal.browser.compose.incognito.PetalIncognitoSessionManager.syncIncognitoState(this);

        if (isIncognitoTab && (isHomePage(url) || "petal://incognito".equalsIgnoreCase(url))) {
            View incognitoHome = com.petal.browser.compose.incognito.PetalIncognitoBridge.createIncognitoHomeView(
                this,
                () -> {
                    try {
                        showOmniboxPage("");
                    } catch (Exception ignored) {}
                },
                () -> closeAllIncognitoTabs()
            );
            contentFrame.addView(incognitoHome);
            if (appBar != null) appBar.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
        } else if (isHomePage(url)) {
            View composeView = PetalComposeBridge.createComposeHomeView(this, BrowserContainer.size(), new PetalHomeActionHandler() {
                @Override
                public void onSearch(String query) {
                    if (query != null && !query.trim().isEmpty()) {
                        String targetUrl = BrowserUnit.queryWrapper(BrowserActivity.this, query.trim());
                        if (ninjaWebView != null) {
                            ninjaWebView.loadUrl(targetUrl);
                            showAlbum(currentAlbumController, targetUrl);
                        }
                    } else {
                        try {
                            showOmniboxPage("");
                        } catch (Exception ignored) {}
                    }
                }

                @Override
                public void onOpenUrl(String u) {
                    if (u != null && u.contains("category=api_integrations")) {
                        openApiIntegrationsHub();
                        return;
                    }
                    if (ninjaWebView != null) {
                        String targetUrl = u;
                        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                            targetUrl = BrowserUnit.queryWrapper(BrowserActivity.this, u);
                        }
                        ninjaWebView.loadUrl(targetUrl);
                        showAlbum(currentAlbumController, targetUrl);
                    }
                }

                @Override
                public void onAddShortcut() {
                    runOnUiThread(() -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(BrowserActivity.this);
                        builder.setTitle("Add Custom Shortcut");
                        LinearLayout layout = new LinearLayout(BrowserActivity.this);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(48, 24, 48, 24);

                        final EditText inputTitle = new EditText(BrowserActivity.this);
                        inputTitle.setHint("Shortcut Name (e.g. Google)");
                        layout.addView(inputTitle);

                        final EditText inputUrl = new EditText(BrowserActivity.this);
                        inputUrl.setHint("Website URL (e.g. https://google.com)");
                        if (ninjaWebView != null && ninjaWebView.getUrl() != null && !isHomePage(ninjaWebView.getUrl())) {
                            inputUrl.setText(ninjaWebView.getUrl());
                            if (ninjaWebView.getTitle() != null) {
                                inputTitle.setText(ninjaWebView.getTitle());
                            }
                        }
                        layout.addView(inputUrl);

                        builder.setView(layout);
                        builder.setPositiveButton("Add", (dialog, which) -> {
                            String title = inputTitle.getText().toString().trim();
                            String url = inputUrl.getText().toString().trim();
                            if (!url.isEmpty()) {
                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    url = "https://" + url;
                                }
                                if (title.isEmpty()) title = HelperUnit.domain(url);

                                try {
                                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(BrowserActivity.this);
                                    String jsonStr = sp.getString("sp_custom_home_shortcuts_json_v3", null);
                                    org.json.JSONArray array = jsonStr != null ? new org.json.JSONArray(jsonStr) : new org.json.JSONArray();
                                    org.json.JSONObject newObj = new org.json.JSONObject();
                                    newObj.put("label", title);
                                    newObj.put("url", url);
                                    newObj.put("siteId", "globe");
                                    newObj.put("color", "#4285F4");
                                    if (array.length() >= 5) {
                                        array.put(4, newObj);
                                    } else {
                                        array.put(newObj);
                                    }
                                    sp.edit().putString("sp_custom_home_shortcuts_json_v3", array.toString()).apply();
                                    updateOmniBox();
                                } catch (Exception ignored) {}
                            }
                        });
                        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                        builder.show();
                    });
                }

                @Override
                public void onNewTab() {
                    addAlbum(getString(R.string.app_name), "about:blank", true);
                }

                @Override
                public void onOpenBookmarks() {
                    showOverview();
                }

                @Override
                public void onOpenHistory() {
                    showOverview();
                }

                @Override
                public void onOpenDownloads() {
                    try {
                        captureBrowserMainPreview();
                        contentFrame.removeAllViews();
                        hideRefreshAndProgressOverlays();
                        View downloadView = PetalDownloadBridge.createDownloadView(BrowserActivity.this, () -> {
                            showAlbum(currentAlbumController);
                            return kotlin.Unit.INSTANCE;
                        });
                        contentFrame.addView(downloadView);
                    } catch (Exception ignored) {}
                }

                @Override
                public void onOpenSettings() {
                    showOverflow(null, null, 0, ninjaWebView != null ? ninjaWebView.getTitle() : "", ninjaWebView != null ? ninjaWebView.getUrl() : "", null, null, 0);
                }

                @Override
                public void onOpenTabsOverview() {
                    showOverview();
                }

                @Override
                public void onOpenAccountSync() {
                    showAccountSyncScreen();
                }
            });
            composeView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ));
            contentFrame.addView(composeView);
            if (appBar != null) appBar.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
        } else {
            av.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ));
            contentFrame.addView(av);
            if (appBar != null) appBar.setVisibility(VISIBLE);
            View downloadBanner = findViewById(R.id.download_banner_compose);
            if (downloadBanner != null) downloadBanner.setVisibility(VISIBLE);
            if (ninjaWebView != null) {
                // Refresh this tab's thumbnail cache entry on switch, so cards that were
                // last captured a while ago (e.g. after background JS updated the page)
                // don't show a stale preview when the tab manager is next opened.
                ninjaWebView.updatePreviewCache();
                ninjaWebView.setOnScrollChangeListener(new NinjaWebView.OnScrollChangeListener() {
                    @Override
                    public void onScrollDown() {
                        View bottomNavContainer = findViewById(R.id.bottom_nav_container);
                        if (bottomNavContainer != null && bottomNavContainer.getVisibility() == VISIBLE) {
                            bottomNavContainer.animate()
                                .translationY(bottomNavContainer.getHeight())
                                .setDuration(220)
                                .start();
                        }
                    }

                    @Override
                    public void onScrollUp() {
                        View bottomNavContainer = findViewById(R.id.bottom_nav_container);
                        if (bottomNavContainer != null && bottomNavContainer.getVisibility() == VISIBLE) {
                            bottomNavContainer.animate()
                                .translationY(0f)
                                .setDuration(220)
                                .start();
                        }
                    }
                });
            }
        }
        updateOmniBox();
        updatePersistentBottomNav();
        View refreshBarCompose = findViewById(R.id.refresh_bar_compose);
        if (refreshBarCompose != null) {
            refreshBarCompose.bringToFront();
            refreshBarCompose.requestLayout();
        }
    }

    public void updatePersistentBottomNav() {
        try {
            androidx.compose.ui.platform.ComposeView bottomNavCompose = findViewById(R.id.bottom_nav_compose);
            if (bottomNavCompose != null) {
                bottomNavCompose.setVisibility(VISIBLE);
                String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                boolean isHome = isHomePage(currentUrl);
                boolean isIncognito = ninjaWebView != null && ninjaWebView.isIncognito();
                int currentTabCount = isIncognito ? BrowserContainer.getIncognitoCount() : BrowserContainer.getNormalCount();
                com.petal.browser.ui.components.PetalNavTab activeTab = isHome ? com.petal.browser.ui.components.PetalNavTab.HOME : com.petal.browser.ui.components.PetalNavTab.TABS;

                com.petal.browser.compose.home.PetalBottomNavBridge.bindBottomNav(
                    bottomNavCompose,
                    this,
                    activeTab,
                    currentTabCount,
                    isIncognito,
                    new com.petal.browser.compose.home.PetalBottomNavHandler() {
                        @Override
                        public void onHomeClick() {
                            if (ninjaWebView != null) {
                                ninjaWebView.loadUrl("about:blank");
                                showAlbum(currentAlbumController, "about:blank");
                            }
                        }

                        @Override
                        public void onNewTabClick() {
                            addAlbum(getString(R.string.app_name), "about:blank", true);
                        }

                        @Override
                        public void onTabsClick() {
                            showOverview();
                        }

                        @Override
                        public void onMenuClick() {
                            View navView = findViewById(R.id.bottom_nav_compose);
                            showOverflow(null, navView, 0, ninjaWebView != null ? ninjaWebView.getTitle() : "", ninjaWebView != null ? ninjaWebView.getUrl() : "", null, null, 0);
                        }
                    }
                );
                bottomNavCompose.bringToFront();
            }
        } catch (Exception ignored) {}
        applyAddressBarPosition();
    }

    public void applyAddressBarPosition() {
        try {
            String pos = sp.getString("sp_address_bar_position", "TOP");
            boolean isBottom = "BOTTOM".equalsIgnoreCase(pos);

            View addressBar = findViewById(R.id.compose_address_bar);
            View progressBarCompose = findViewById(R.id.main_progress_bar_compose);
            View mainContent = findViewById(R.id.main_content);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            View fabBubble = findViewById(R.id.fab_bubble);

            if (addressBar != null && mainContent != null && addressBar.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams addrParams = (RelativeLayout.LayoutParams) addressBar.getLayoutParams();
                RelativeLayout.LayoutParams contentParams = (RelativeLayout.LayoutParams) mainContent.getLayoutParams();
                RelativeLayout.LayoutParams progComposeParams = progressBarCompose != null && progressBarCompose.getLayoutParams() instanceof RelativeLayout.LayoutParams ? (RelativeLayout.LayoutParams) progressBarCompose.getLayoutParams() : null;

                if (isBottom) {
                    addrParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                    addrParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    if (bottomNav != null) {
                        addrParams.addRule(RelativeLayout.ABOVE, R.id.bottom_nav_compose);
                    } else {
                        addrParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                    }
                    addrParams.topMargin = 0;
                    addrParams.bottomMargin = (int) HelperUnit.convertDpToPixel(4f, context);

                    if (progComposeParams != null) {
                        progComposeParams.removeRule(RelativeLayout.BELOW);
                        progComposeParams.addRule(RelativeLayout.ABOVE, R.id.compose_address_bar);
                    }

                    contentParams.removeRule(RelativeLayout.BELOW);
                    contentParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    contentParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                    // Content is anchored to the address bar only. The progress bar overlays
                    // it and must never affect main_content's position (its height changes
                    // on every load, which raced the WebView's own layout pass).
                    contentParams.addRule(RelativeLayout.ABOVE, R.id.compose_address_bar);

                    if (fabBubble != null && fabBubble.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                        RelativeLayout.LayoutParams bubbleParams = (RelativeLayout.LayoutParams) fabBubble.getLayoutParams();
                        bubbleParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                        bubbleParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                        bubbleParams.bottomMargin = (int) HelperUnit.convertDpToPixel(140f, context);
                        bubbleParams.topMargin = 0;
                        fabBubble.setLayoutParams(bubbleParams);
                    }
                } else {
                    addrParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    addrParams.removeRule(RelativeLayout.ABOVE);
                    addrParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                    addrParams.topMargin = 0;
                    addrParams.bottomMargin = 0;

                    if (progComposeParams != null) {
                        progComposeParams.removeRule(RelativeLayout.ABOVE);
                        progComposeParams.addRule(RelativeLayout.BELOW, R.id.compose_address_bar);
                    }

                    contentParams.removeRule(RelativeLayout.ABOVE);
                    contentParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                    contentParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                    contentParams.addRule(RelativeLayout.BELOW, R.id.compose_address_bar);

                    if (fabBubble != null && fabBubble.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                        RelativeLayout.LayoutParams bubbleParams = (RelativeLayout.LayoutParams) fabBubble.getLayoutParams();
                        bubbleParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                        bubbleParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                        bubbleParams.topMargin = (int) HelperUnit.convertDpToPixel(16f, context);
                        bubbleParams.bottomMargin = 0;
                        fabBubble.setLayoutParams(bubbleParams);
                    }
                }

                // Bottom Nav Container is ALWAYS anchored at the bottom of the screen
                View bottomNavContainer = findViewById(R.id.bottom_nav_container);
                if (bottomNavContainer != null && bottomNavContainer.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                    RelativeLayout.LayoutParams containerParams = (RelativeLayout.LayoutParams) bottomNavContainer.getLayoutParams();
                    containerParams.removeRule(RelativeLayout.ABOVE);
                    containerParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                    bottomNavContainer.setLayoutParams(containerParams);
                    bottomNavContainer.bringToFront();
                }

                boolean isFloating = sp.getBoolean("sp_floating_tab_bar", true);
                if (bottomNav != null && bottomNav.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                    RelativeLayout.LayoutParams navParams = (RelativeLayout.LayoutParams) bottomNav.getLayoutParams();
                    navParams.removeRule(RelativeLayout.ABOVE);
                    navParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                    navParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
                    navParams.bottomMargin = isFloating ? (int) HelperUnit.convertDpToPixel(6f, context) : 0;
                    bottomNav.setLayoutParams(navParams);
                    bottomNav.bringToFront();
                }

                // Do not resize/pad pages that don't have bottom navbar or when floating navbar is active
                boolean hasNav = bottomNav != null && bottomNav.getVisibility() == VISIBLE;
                int paddingBottom = (hasNav && !isFloating) ? (int) HelperUnit.convertDpToPixel(80f, context) : 0;
                mainContent.setPadding(0, 0, 0, paddingBottom);

                addressBar.setLayoutParams(addrParams);
                if (progressBarCompose != null) progressBarCompose.setLayoutParams(progComposeParams);
                mainContent.setLayoutParams(contentParams);

                addressBar.bringToFront();
                addressBar.requestLayout();
                mainContent.requestLayout();

                // Progress bar must be explicitly raised above main_content too — elevation
                // alone was not reliably winning the draw order on-device.
                if (progressBarCompose != null) {
                    progressBarCompose.bringToFront();
                    progressBarCompose.requestLayout();
                }

                View refreshBarComposeView = findViewById(R.id.refresh_bar_compose);
                if (refreshBarComposeView != null) {
                    refreshBarComposeView.bringToFront();
                    refreshBarComposeView.requestLayout();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying address bar position", e);
        }
    }

    @Override
    public synchronized void removeAlbum(final AlbumController controller) {
        if (BrowserContainer.size() <= 1) {
            String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
            String homeUrl = sp.getString("favoriteURL", "about:blank");
            if (currentUrl != null && !isHomePage(currentUrl) && !currentUrl.equals(homeUrl)) {
                ninjaWebView.loadUrl(homeUrl);
                showAlbum(currentAlbumController, homeUrl);
            } else {
                doubleTapsQuit();
            }
            updateOmniBox();
            updatePersistentBottomNav();
            saveOpenedTabs();
            // Ensure user stays on the tab switcher menu view when 0/1 tabs remain
            showOverview();
        } else {
            // closeTabConfirmation() runs okAction asynchronously (it waits for the
            // user to tap "OK" on a Snackbar) when the "confirm before closing tab"
            // preference is on. The tab bar refresh must happen inside the callback,
            // after the tab is actually removed - otherwise it fired immediately and
            // showed a stale tab count/list until the user confirmed.
            closeTabConfirmation(() -> {
                AlbumController predecessor;
                if (controller == currentAlbumController) predecessor = ((NinjaWebView) controller).getPredecessor();
                else predecessor = currentAlbumController;
                //if not the current TAB is being closed return to current TAB
                tab_container.removeView(controller.getAlbumView());
                int index = BrowserContainer.indexOf(controller);
                BrowserContainer.remove(controller);
                if ((predecessor != null) && (BrowserContainer.indexOf(predecessor) != -1)) {
                    //if predecessor is stored and has not been closed in the meantime
                    showAlbum(predecessor);
                } else {
                    if (index >= BrowserContainer.size()) index = BrowserContainer.size() - 1;
                    showAlbum(BrowserContainer.get(index));
                }
                updateOmniBox();
                updatePersistentBottomNav();
                saveOpenedTabs();
                com.petal.browser.compose.incognito.PetalIncognitoSessionManager.syncIncognitoState(BrowserActivity.this);
            });
        }
    }

    public synchronized void removeAlbumSilently(final AlbumController controller) {
        if (controller == null) return;
        try {
            if (tab_container != null && controller.getAlbumView() != null) {
                tab_container.removeView(controller.getAlbumView());
            }
            boolean isClosingCurrent = (controller == currentAlbumController);
            BrowserContainer.remove(controller);
            if (isClosingCurrent && BrowserContainer.size() > 0) {
                showAlbum(BrowserContainer.get(Math.max(0, BrowserContainer.size() - 1)));
            }
            updatePersistentBottomNav();
            saveOpenedTabs();
        } catch (Exception e) {
            Log.e(TAG, "Error removing album silently", e);
        }
    }

    @Override
    public synchronized void updateProgress(int progress) {
        androidx.compose.ui.platform.ComposeView progressBarCompose = findViewById(R.id.main_progress_bar_compose);
        String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
        boolean isInternalPage = currentUrl != null && (
            currentUrl.startsWith("petal://settings") ||
            currentUrl.startsWith("petal://history") ||
            currentUrl.startsWith("petal://account") ||
            currentUrl.startsWith("petal://downloads") ||
            currentUrl.startsWith("about:blank") ||
            isHomePage(currentUrl)
        );

        if (progressBarCompose != null) {
            // Keep the ComposeView itself permanently VISIBLE and let its internal
            // AnimatedVisibility state (below) control what's actually drawn. This
            // used to call setVisibility(GONE) here, which - once set - a ComposeView
            // never composes again, so the very next real page load had nothing left
            // to make visible and the progress bar stayed missing for the rest of the
            // session (root cause of the "progress bar is missing" bug).
            if (progressBarCompose.getVisibility() != VISIBLE) {
                progressBarCompose.setVisibility(VISIBLE);
            }
            if (!refreshState.isRefreshing() && !isInternalPage) {
                com.petal.browser.ui.components.PetalProgressBarBridge.updateProgress(progressBarCompose, progress);
            } else {
                com.petal.browser.ui.components.PetalProgressBarBridge.hide(progressBarCompose);
            }
        }

        if (progressBar != null) {
            if (!isInternalPage) {
                progressBar.setProgressCompat(progress, true);
                if (progress < 100) {
                    progressBar.setVisibility(VISIBLE);
                } else {
                    progressBar.setVisibility(GONE);
                }
            } else {
                progressBar.setVisibility(GONE);
            }
            if (progress >= 100) {
                updateOmniBox();
                saveOpenedTabs();
                FaviconHelper.setFavicon(context, contentView, ninjaWebView.getUrl(), R.id.menu_icon, R.drawable.icon_image_broken);
                final Handler handler = new Handler();
                handler.postDelayed(() -> FaviconHelper.setFavicon(context, contentView, ninjaWebView.getUrl(), R.id.menu_icon, R.drawable.icon_image_broken), 500);
            }
        }
    }

    @Override
    public void showFileChooser(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        if (mFilePathCallback != null) {
            mFilePathCallback.onReceiveValue(null);
        }
        mFilePathCallback = filePathCallback;

        Intent chooserIntent = null;
        if (fileChooserParams != null) {
            try {
                chooserIntent = fileChooserParams.createIntent();
            } catch (Exception ignored) {}
        }

        if (chooserIntent == null) {
            Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentSelectionIntent.setType("*/*");
            if (fileChooserParams != null && fileChooserParams.getAcceptTypes() != null && fileChooserParams.getAcceptTypes().length > 0) {
                String[] acceptTypes = fileChooserParams.getAcceptTypes();
                if (acceptTypes.length == 1 && !acceptTypes[0].trim().isEmpty()) {
                    contentSelectionIntent.setType(acceptTypes[0]);
                } else if (acceptTypes.length > 1) {
                    contentSelectionIntent.setType("*/*");
                    contentSelectionIntent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes);
                }
            }
            if (fileChooserParams != null && fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }

            chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "File Chooser");
        }

        try {
            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);
        } catch (Exception e) {
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
                mFilePathCallback = null;
            }
        }
    }

    @Override
    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (view == null) return;
        if (customView != null && callback != null) {
            callback.onCustomViewHidden();
            return;
        }

        customView = view;
        fullscreenHolder = new FrameLayout(context);
        fullscreenHolder.addView(
                customView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

        FrameLayout decorView = (FrameLayout) getWindow().getDecorView();
        decorView.addView(
                fullscreenHolder,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));

        customView.setKeepScreenOn(true);
        ((View) currentAlbumController).setVisibility(GONE);
        setCustomFullscreen(true);

        if (view instanceof FrameLayout) {
            if (((FrameLayout) view).getFocusedChild() instanceof VideoView) {
                videoView = (VideoView) ((FrameLayout) view).getFocusedChild();
                videoView.setOnErrorListener(new VideoCompletionListener());
                videoView.setOnCompletionListener(new VideoCompletionListener());
            }
        }
    }

    @Override
    public void onHideCustomView() {
        FrameLayout decorView = (FrameLayout) getWindow().getDecorView();
        decorView.removeView(fullscreenHolder);
        customView.setKeepScreenOn(false);
        ((View) currentAlbumController).setVisibility(VISIBLE);
        setCustomFullscreen(false);
        fullscreenHolder = null;
        customView = null;
        if (videoView != null) {
            videoView.setOnErrorListener(null);
            videoView.setOnCompletionListener(null);
            videoView = null; }
        contentFrame.requestFocus();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initOverview() {
        if (dialogOverview == null) return;
        listView = dialogOverview.findViewById(R.id.list_overView);
        AtomicInteger intPage = new AtomicInteger();

        try {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.colorPrimaryInverse, typedValue, true);
            int color = typedValue.data;
            TypedValue typedValue2 = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue2, true);
            int color2 = typedValue2.data;

            if (bottom_navigation != null) {
                BadgeDrawable badge = bottom_navigation.getOrCreateBadge(R.id.page_0);
                if (badge != null) {
                    badge.setBackgroundColor(color);
                    badge.setBadgeTextColor(color2);
                    badge.setHorizontalOffset(0);
                    badge.setVerticalOffset(0);
                    if (BrowserContainer.size() > 1) {
                        badge.setNumber(BrowserContainer.size());
                    }
                }
            }
        } catch (Exception ignored) {}

        NavigationBarView.OnItemSelectedListener navListener = menuItem -> {

            if (menuItem.getItemId() == R.id.page_0) {
                if (fab_overview != null) fab_overview.setImageResource(R.drawable.icon_tab);
                overViewTab = getString(R.string.album_title_tab);
                intPage.set(R.id.page_0);
                if (listView != null) listView.setVisibility(GONE);
                if (tab_container != null) tab_container.setVisibility(VISIBLE);}

            else if (menuItem.getItemId() == R.id.page_2) {
                try {
                    RecordAction action = new RecordAction(context);
                    action.open(true);
                    String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    if (fab_overview != null) {
                        if (currentUrl != null && !currentUrl.isEmpty() && action.checkUrl(currentUrl, RecordUnit.TABLE_BOOKMARK)) {
                            fab_overview.setImageResource(R.drawable.icon_bookmark_added);
                        } else {
                            fab_overview.setImageResource(R.drawable.icon_bookmark);
                        }
                    }
                    action.close();
                } catch (Exception e) {Log.i(TAG, "dialogCustomSearches:" + e);}
                overViewTab = getString(R.string.album_title_bookmarks);
                intPage.set(R.id.page_2);
                if (listView != null) listView.setVisibility(VISIBLE);
                if (tab_container != null) tab_container.setVisibility(GONE);

                RecordAction action = new RecordAction(context);
                action.open(false);
                final List<Record> list;
                list = action.listBookmark(activity, filter, filterBy);
                action.close();
                adapter = new AdapterRecord(context, list);
                if (listView != null) {
                    listView.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                    filter = false;
                    listView.setOnItemClickListener((parent, view, position, id) -> {
                        if (ninjaWebView != null) ninjaWebView.loadUrl(list.get(position).getURL());
                        else addAlbum(getString(R.string.app_name), list.get(position).getURL(), true);
                        hideOverview();
                    });
                    listView.setOnItemLongClickListener((parent, view, position, id) -> {
                        showOverflow(dialogOverview, listView, 3, list.get(position).getTitle(), list.get(position).getURL(), adapter, list, position);
                        return true;
                    });
                } }
            else if (menuItem.getItemId() == R.id.page_3) {
                if (fab_overview != null) fab_overview.setImageResource(R.drawable.icon_history);
                overViewTab = getString(R.string.album_title_history);
                intPage.set(R.id.page_3);
                listView.setVisibility(VISIBLE);
                tab_container.setVisibility(GONE);

                RecordAction action = new RecordAction(context);
                action.open(false);
                final List<Record> list;
                list = action.listHistory(context);
                action.close();
                //noinspection NullableProblems
                adapter = new AdapterRecord(context, list) {
                    @Override
                    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        TextView record_item_time = v.findViewById(R.id.dateView);
                        record_item_time.setVisibility(VISIBLE);
                        return v;
                    }
                };
                listView.setAdapter(adapter);
                adapter.notifyDataSetChanged();
                listView.setOnItemClickListener((parent, view, position, id) -> {
                    ninjaWebView.loadUrl(list.get(position).getURL());
                    hideOverview();
                });
                listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    showOverflow(dialogOverview, listView, 4, list.get(position).getTitle(), list.get(position).getURL(), adapter, list, position);
                    return true;
                }); }
            else if (menuItem.getItemId() == R.id.page_incognito) {
                addAlbum("Incognito Tab", sp.getString("favoriteURL", "about:blank"), true, true);
                hideOverview();
            }
            else if (menuItem.getItemId() == R.id.page_4) {
                PopupMenu popup = new PopupMenu(this, bottom_navigation.findViewById(R.id.page_2));
                popup.setForceShowIcon(true);
                popup.setOnDismissListener(menu -> setSelectedTab());
                if (bottom_navigation.getSelectedItemId() == R.id.page_0)
                    popup.inflate(R.menu.menu_help);
                else if (bottom_navigation.getSelectedItemId() == R.id.page_2)
                    popup.inflate(R.menu.menu_list_bookmark);
                else if (bottom_navigation.getSelectedItemId() == R.id.page_3)
                    popup.inflate(R.menu.menu_list_history);

                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.menu_delete) {
                        Snackbar snackbarBottom = Snackbar.make(bottom_navigation, R.string.hint_database, Snackbar.LENGTH_SHORT);
                        HelperUnit.makeSnackbarRound(snackbarBottom);
                        snackbarBottom.setAction(context.getString(R.string.app_ok), (v -> {
                            if (overViewTab.equals(getString(R.string.album_title_bookmarks))) {
                                BrowserUnit.clearBookmark(context);
                                bottom_navigation.setSelectedItemId(R.id.page_2); }
                            else if (overViewTab.equals(getString(R.string.album_title_history))) {
                                BrowserUnit.clearHistory(context);
                                bottom_navigation.setSelectedItemId(R.id.page_3); }
                        }));
                        snackbarBottom.show();
                    } else if (item.getItemId() == R.id.menu_sortName) {
                        sp.edit().putString("sort_bookmark", "title").apply();
                        sp.edit().putBoolean("sort_bookmarkDomain", false).apply();
                        bottom_navigation.setSelectedItemId(R.id.page_2);
                    } else if (item.getItemId() == R.id.menu_sortIcon) {
                        sp.edit().putString("sort_bookmark", "time").apply();
                        sp.edit().putBoolean("sort_bookmarkDomain", false).apply();
                        bottom_navigation.setSelectedItemId(R.id.page_2);
                    } else if (item.getItemId() == R.id.menu_sortDate) {
                        sp.edit().putBoolean("sort_historyDomain", false).apply();
                        bottom_navigation.setSelectedItemId(R.id.page_3);
                    } else if (item.getItemId() == R.id.menu_sortDomain) {
                        if (overViewTab.equals(getString(R.string.album_title_bookmarks))) {
                            sp.edit().putBoolean("sort_bookmarkDomain", true).apply();
                            bottom_navigation.setSelectedItemId(R.id.page_2); }
                        else if (overViewTab.equals(getString(R.string.album_title_history))) {
                            sp.edit().putBoolean("sort_historyDomain", true).apply();
                            bottom_navigation.setSelectedItemId(R.id.page_3);
                        }
                    } else if (item.getItemId() == R.id.menu_filter) {
                        showDialogFilter();
                    } else if (item.getItemId() == R.id.menu_help) {
                        if (ninjaWebView != null) {
                            ninjaWebView.loadUrl("about:blank");
                            showAlbum(currentAlbumController, "about:blank");
                        }
                    }
                    return true;
                });
                popup.show();
            }

            return true;
        };
        bottom_navigation.setOnItemSelectedListener(navListener);
        bottom_navigation.findViewById(R.id.page_2).setOnLongClickListener(v -> {
            showDialogFilter();
            return true;
        });
        setSelectedTab();
        initOmniBox();

        fab_menu = findViewById(R.id.fab_menu);
        if (fab_menu != null) {
            HelperUnit.applyBouncyTouchFeedback(fab_menu);
            fab_menu.setOnClickListener(view -> {
                String title = ninjaWebView != null ? ninjaWebView.getTitle() : "";
                String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                showOverflow(null, null, 0, title, url, null, null, 0);
            });
            fab_menu.setOnLongClickListener(view -> {
                String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                performGesture("setting_gesture_tabButton", url);
                return true;
            });
        }

        FloatingActionButton fab_share = findViewById(R.id.fab_share);
        if (fab_share != null) {
            HelperUnit.applyBouncyTouchFeedback(fab_share);
            fab_share.setOnClickListener(v -> {
                if (ninjaWebView != null && ninjaWebView.getUrl() != null) {
                    shareLink(ninjaWebView.getTitle(), ninjaWebView.getUrl());
                }
            });
        }

        FloatingActionButton fab_undo = findViewById(R.id.fab_undo);
        if (fab_undo != null) {
            HelperUnit.applyBouncyTouchFeedback(fab_undo);
            fab_undo.setOnClickListener(v -> {
                if (ninjaWebView != null && ninjaWebView.canGoBack()) {
                    ninjaWebView.goBack();
                } else {
                    NinjaToast.show(BrowserActivity.this, "Nothing to undo");
                }
            });
        }

        fab_overview = findViewById(R.id.fab_overview);
        list_search = dialogViewSearch.findViewById(R.id.list_search);
        progressBar = findViewById(R.id.main_progress_bar);
        androidx.compose.ui.platform.ComposeView progressBarComposeView = findViewById(R.id.main_progress_bar_compose);
        if (progressBarComposeView != null) {
            androidx.compose.ui.platform.ComposeView fancyProgress = com.petal.browser.ui.components.PetalProgressBarBridge.createProgressView(this);
            ViewGroup parent = (ViewGroup) progressBarComposeView.getParent();
            if (parent != null) {
                int index = parent.indexOfChild(progressBarComposeView);
                parent.removeView(progressBarComposeView);
                fancyProgress.setId(R.id.main_progress_bar_compose);
                fancyProgress.setLayoutParams(progressBarComposeView.getLayoutParams());
                parent.addView(fancyProgress, index);
            }
        }
        badgeDrawable = BadgeDrawable.create(context);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorPrimaryInverse, typedValue, true);
        int color = typedValue.data;
        TypedValue typedValue2 = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue2, true);
        int color2 = typedValue2.data;
        badgeDrawable.setBackgroundColor(color);
        badgeDrawable.setBadgeTextColor(color2);

        if (fab_overview != null) {
            fab_overview.setOnTouchListener(new SwipeTouchListener(context) {
                public void onSwipeTop() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_tb_up", url);
                    hideOverview();
                }
                public void onSwipeBottom() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_tb_down", url);
                    hideOverview();
                }
                public void onSwipeRight() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_tb_right", url);
                    hideOverview();
                }
                public void onSwipeLeft() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_tb_left", url);
                    hideOverview();
                }
            });
        }

        if (fab_menu != null) {
            fab_menu.setOnTouchListener(new SwipeTouchListener(context) {
                public void onSwipeTop() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_nav_up", url);
                    hideOverflow();
                }
                public void onSwipeBottom() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_nav_down", url);
                    hideOverflow();
                }
                public void onSwipeRight() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_nav_right", url);
                    hideOverflow();
                }
                public void onSwipeLeft() {
                    String url = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    performGesture("setting_gesture_nav_left", url);
                    hideOverflow();
                }
            });
        }

        TextInputLayout search_textField  = dialogViewSearch.findViewById(R.id.search_textField);
        if (search_textField != null) {
            search_textField.setStartIconOnClickListener(v -> {
                if (search_input != null && Objects.requireNonNull(search_input.getText()).toString().isEmpty()) {
                    hideSearch();
                } else if (search_input != null) {
                    search_input.setText("");
                }
            });
            search_textField.setEndIconOnLongClickListener(v -> {
                String query = (search_input != null && search_input.getText() != null) ? search_input.getText().toString().trim() : "";
                if (!query.isEmpty() && (ninjaWebView == null || !query.equals(ninjaWebView.getUrl()))) {
                    showDialogCustomSearches(query);
                } else {
                    NinjaToast.show(this, R.string.toast_input_empty);
                }
                return false;
            });
            search_textField.setEndIconOnClickListener(v -> {
                String query = (search_input != null && search_input.getText() != null) ? search_input.getText().toString().trim() : "";
                handleFinalSearch(query);
            });
        }
        if (search_input != null) {
            search_input.setOnEditorActionListener((v, actionId, event) -> {
                String query = (search_input.getText() != null) ? search_input.getText().toString().trim() : "";
                handleFinalSearch(query);
                return true;
            });

            search_input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String liveText = s.toString().trim();
                    boolean hasText = !liveText.isEmpty();
                    if (search_textField != null) {
                        if (hasText) {
                            TypedValue typedValue = new TypedValue();
                            context.getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue, true);
                            int color = typedValue.data;
                            search_textField.setStartIconTintList(ColorStateList.valueOf(color));
                            search_textField.setEndIconTintList(ColorStateList.valueOf(color));
                        } else {
                            search_textField.setStartIconTintList(ColorStateList.valueOf(Color.GRAY));
                            search_textField.setEndIconTintList(ColorStateList.valueOf(Color.GRAY));
                        }
                    }
                    if (adapterSearch != null && adapterSearch.getFilter() != null) {
                        adapterSearch.getFilter().filter(s);
                    }
                    sp.edit().putString("searchInput", s.toString()).apply();

                    boolean enableLiveSuggestions = sp.getBoolean("sp_enable_live_suggestions", true);
                    if (hasText && adapterSearch != null && enableLiveSuggestions) {
                        String searchEngine = sp.getString("sp_search_engine", "0");
                        if ("1".equals(searchEngine)) { // DuckDuckGo
                            com.petal.browser.unit.SearchSuggestionsManager.fetchDuckDuckGoSuggestions(liveText, suggestions -> {
                                if (adapterSearch != null) adapterSearch.setLiveSuggestions(suggestions);
                            });
                        } else if ("2".equals(searchEngine)) { // Bing
                            com.petal.browser.unit.SearchSuggestionsManager.fetchBingSuggestions(liveText, suggestions -> {
                                if (adapterSearch != null) adapterSearch.setLiveSuggestions(suggestions);
                            });
                        } else {
                            com.petal.browser.unit.SearchSuggestionsManager.fetchSuggestions(liveText, suggestions -> {
                                if (adapterSearch != null) adapterSearch.setLiveSuggestions(suggestions);
                            });
                        }
                    } else if (adapterSearch != null) {
                        adapterSearch.setLiveSuggestions(null);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        if (fab_overview != null) {
            HelperUnit.applyBouncyTouchFeedback(fab_overview);
            fab_overview.setOnClickListener(v -> showOverview());
            fab_overview.setOnLongClickListener(v -> {
                performGesture("setting_gesture_overViewButton", ninjaWebView != null ? ninjaWebView.getUrl() : "");
                return true;
            });
        }
    }

    @SuppressLint({"ClickableViewAccessibility", "UnsafeOptInUsageError"})
    private void initOmniBox() {
        search_input = dialogViewSearch.findViewById(R.id.search_input);
        contentView = findViewById(android.R.id.content);
        composeAddressBar = findViewById(R.id.compose_address_bar);

        View fab_bubble = findViewById(R.id.fab_bubble);
        if (fab_bubble != null) {
            HelperUnit.applyBouncyTouchFeedback(fab_bubble);
            fab_bubble.setOnClickListener(v -> animateAddressBarCollapse(false));
        }

        updateAddressBar();
    }

    private void handleFinalSearch(String query) {
        if (query != null && !query.trim().isEmpty()) {
            hideSearch();
            String targetUrl = com.petal.browser.unit.BrowserUnit.queryWrapper(this, query.trim());
            if (ninjaWebView != null) {
                ninjaWebView.loadUrl(targetUrl);
                showAlbum(currentAlbumController, targetUrl);
            }
        } else {
            NinjaToast.show(this, R.string.toast_input_empty);
        }
    }

    private boolean isHomePage(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String clean = url.trim().toLowerCase(java.util.Locale.ROOT);
        if (clean.equals("about:blank") || clean.equals("about:home") || clean.equals("petal://home") || clean.equals("petal://start") || clean.contains("petal_home.html")) {
            return true;
        }
        return clean.startsWith("file:///android_asset/");
    }

    private androidx.compose.ui.platform.ComposeView composeAddressBar;

    public void updateAddressBar() {
        if (composeAddressBar == null) {
            composeAddressBar = findViewById(R.id.compose_address_bar);
        }
        if (composeAddressBar == null) return;

        String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
        String currentTitle = ninjaWebView != null ? ninjaWebView.getTitle() : "";

        if (isHomePage(currentUrl)) {
            composeAddressBar.setVisibility(GONE);
            return;
        } else {
            composeAddressBar.setVisibility(VISIBLE);
            composeAddressBar.bringToFront();
            composeAddressBar.setTranslationY(0f);
        }

        boolean isIncognito = ninjaWebView != null && ninjaWebView.isIncognito();
        boolean canGoBack = ninjaWebView != null && ninjaWebView.canGoBack();
        boolean isLoading = ninjaWebView != null && ninjaWebView.getProgress() < 100;

        com.petal.browser.compose.home.PetalAddressBarBridge.bindAddressBar(
                composeAddressBar,
                this,
                currentUrl != null ? currentUrl : "",
                currentTitle != null ? currentTitle : "",
                isIncognito,
                isLoading,
                canGoBack,
                () -> {
                    if (ninjaWebView != null && ninjaWebView.canGoBack()) {
                        ninjaWebView.goBack();
                    } else if (ninjaWebView != null) {
                        ninjaWebView.loadUrl("about:blank");
                        showAlbum(currentAlbumController, "about:blank");
                    }
                },
                () -> {
                    String shareUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    if (shareUrl != null && !shareUrl.isEmpty() && !isHomePage(shareUrl)) {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, shareUrl);
                        startActivity(Intent.createChooser(shareIntent, "Share Link"));
                    }
                },
                () -> {
                    String cUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
                    if (cUrl == null || cUrl.equalsIgnoreCase("about:blank") || cUrl.startsWith("about:") || isHomePage(cUrl)) {
                        cUrl = "";
                    }
                    showOmniboxPage(cUrl);
                },
                () -> {
                    com.petal.browser.ui.components.PetalSiteInfoBridge.showSiteInfoBottomSheet(
                        this,
                        ninjaWebView,
                        () -> {
                            if (ninjaWebView != null) ninjaWebView.reload();
                        }
                    );
                },
                this::showAiResearchSheet
        );
        if (refreshState != null && refreshState.isRefreshing()) {
            refreshState.setRefreshing(false);
            refreshState.setPullProgress(0f);
        }
    }

    private boolean isAiResearchExtracting = false;
    private final android.os.Handler aiResearchTimeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable aiResearchTimeoutRunnable;

    public void showAiResearchSheet() {
        if (ninjaWebView == null) return;

        // Guard against rapid repeated taps queuing up multiple evaluateJavascript
        // calls on the WebView - this is what made the browser appear to hang.
        if (isAiResearchExtracting) return;

        final String currentUrl = ninjaWebView.getUrl();
        final String currentTitle = ninjaWebView.getTitle() != null ? ninjaWebView.getTitle() : "";

        if (!com.petal.browser.compose.ai.PetalAiResearchEngine.INSTANCE.isProperWebSite(currentUrl)) {
            return;
        }

        isAiResearchExtracting = true;
        NinjaToast.show(BrowserActivity.this, "Analyzing page\u2026");

        // Safety timeout: if the page's JS engine is busy or the callback never
        // fires (huge/complex DOM, stalled page, cross-origin edge cases), don't
        // leave the UI stuck with no feedback - bail out after a few seconds.
        aiResearchTimeoutRunnable = () -> {
            if (isAiResearchExtracting) {
                isAiResearchExtracting = false;
                NinjaToast.show(BrowserActivity.this, "Petal AI timed out reading this page. Please try again.");
            }
        };
        aiResearchTimeoutHandler.postDelayed(aiResearchTimeoutRunnable, 6000);

        // Truncate inside the page's own JS (before crossing the JS bridge) so
        // very large pages don't serialize megabytes of text back to Java -
        // that marshalling cost was the main cause of the perceived freeze.
        ninjaWebView.evaluateJavascript(
            "(function() { " +
            "  try { " +
            "    var title = document.title || ''; " +
            "    var metaDesc = (document.querySelector('meta[name=\"description\"]') || {}).content || ''; " +
            "    var bodyText = document.body ? document.body.innerText : ''; " +
            "    if (bodyText && bodyText.length > 16000) { bodyText = bodyText.substring(0, 16000); } " +
            "    return title + '\\n' + metaDesc + '\\n' + bodyText; " +
            "  } catch (e) { return title || ''; } " +
            "})();",
            value -> {
                aiResearchTimeoutHandler.removeCallbacks(aiResearchTimeoutRunnable);
                if (!isAiResearchExtracting) return; // already timed out, ignore late callback
                isAiResearchExtracting = false;

                String cleanText = value != null ? value : "";
                if (cleanText.startsWith("\"") && cleanText.endsWith("\"") && cleanText.length() >= 2) {
                    cleanText = cleanText.substring(1, cleanText.length() - 1);
                }
                cleanText = cleanText.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                if (cleanText.length() > 15000) {
                    cleanText = cleanText.substring(0, 15000);
                }

                com.petal.browser.ui.components.PetalAiResearchBridge.showAiFeature(
                    BrowserActivity.this,
                    currentTitle,
                    currentUrl,
                    cleanText
                );
            }
        );
    }

    private boolean isAddressBarCollapsed = false;

    public void animateAddressBarCollapse(boolean collapse) {
        String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
        View fab_bubble = findViewById(R.id.fab_bubble);
        if (composeAddressBar == null) composeAddressBar = findViewById(R.id.compose_address_bar);
        View progressBarCompose = findViewById(R.id.main_progress_bar_compose);
        View progressBar = findViewById(R.id.main_progress_bar);
        View refreshBarCompose = findViewById(R.id.refresh_bar_compose);

        if (getIntent() != null && getIntent().getBooleanExtra("pwa_mode", false)) {
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            if (fab_bubble != null) fab_bubble.setVisibility(GONE);
            if (contentFrame != null) contentFrame.setTranslationY(0f);
            return;
        }

        if (isHomePage(currentUrl)) {
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            if (fab_bubble != null) fab_bubble.setVisibility(GONE);
            if (contentFrame != null) contentFrame.setTranslationY(0f);
            if (progressBarCompose != null) progressBarCompose.setTranslationY(0f);
            if (progressBar != null) progressBar.setTranslationY(0f);
            if (refreshBarCompose != null) refreshBarCompose.setTranslationY(0f);
            isAddressBarCollapsed = false;
            return;
        }

        View bottomNav = findViewById(R.id.bottom_nav_compose);

        if (composeAddressBar == null) return;

        if (collapse && !isAddressBarCollapsed) {
            isAddressBarCollapsed = true;
            String pos = sp.getString("sp_address_bar_position", "TOP");
            boolean isBottom = "BOTTOM".equalsIgnoreCase(pos);
            float barHeight = composeAddressBar.getHeight() > 0 ? composeAddressBar.getHeight() : HelperUnit.convertDpToPixel(56f, context);
            float targetY = isBottom ? (barHeight + HelperUnit.convertDpToPixel(40f, context)) : -(barHeight + HelperUnit.convertDpToPixel(40f, context));
            float contentTargetY = isBottom ? 0f : -barHeight;

            springTranslateY(composeAddressBar, targetY, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);

            if (bottomNav != null) {
                float bottomNavTargetY = HelperUnit.convertDpToPixel(120f, context);
                springTranslateY(bottomNav, bottomNavTargetY, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);
            }

            if (contentFrame != null) {
                springTranslateY(contentFrame, contentTargetY, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }
            if (progressBarCompose != null) {
                springTranslateY(progressBarCompose, contentTargetY, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }
            if (progressBar != null) {
                springTranslateY(progressBar, contentTargetY, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }
            if (refreshBarCompose != null) {
                springTranslateY(refreshBarCompose, contentTargetY, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }

            if (fab_bubble != null) {
                fab_bubble.setVisibility(VISIBLE);
                fab_bubble.setScaleX(0f);
                fab_bubble.setScaleY(0f);
                fab_bubble.setAlpha(0f);
                springScale(fab_bubble, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
                springAlpha(fab_bubble, 1f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }

        } else if (!collapse && isAddressBarCollapsed) {
            isAddressBarCollapsed = false;
            composeAddressBar.setVisibility(VISIBLE);

            springTranslateY(composeAddressBar, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);

            if (bottomNav != null) {
                springTranslateY(bottomNav, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);
            }

            if (contentFrame != null) {
                springTranslateY(contentFrame, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }
            if (progressBarCompose != null) {
                springTranslateY(progressBarCompose, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }
            if (progressBar != null) {
                springTranslateY(progressBar, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }
            if (refreshBarCompose != null) {
                springTranslateY(refreshBarCompose, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }

            if (fab_bubble != null) {
                springScale(fab_bubble, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
                springAlpha(fab_bubble, 0f, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
                fab_bubble.postDelayed(() -> { if (!isAddressBarCollapsed) fab_bubble.setVisibility(GONE); }, 350);
            }
        }
    }

    private void springTranslateY(View view, float endValue, float stiffness, float dampingRatio) {
        new androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y)
                .setSpring(new androidx.dynamicanimation.animation.SpringForce(endValue)
                        .setStiffness(stiffness)
                        .setDampingRatio(dampingRatio))
                .start();
    }

    private void springScale(View view, float endValue, float stiffness, float dampingRatio) {
        new androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X)
                .setSpring(new androidx.dynamicanimation.animation.SpringForce(endValue)
                        .setStiffness(stiffness)
                        .setDampingRatio(dampingRatio))
                .start();
        new androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y)
                .setSpring(new androidx.dynamicanimation.animation.SpringForce(endValue)
                        .setStiffness(stiffness)
                        .setDampingRatio(dampingRatio))
                .start();
    }

    private void springAlpha(View view, float endValue, float stiffness, float dampingRatio) {
        new androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.ALPHA)
                .setSpring(new androidx.dynamicanimation.animation.SpringForce(endValue)
                        .setStiffness(stiffness)
                        .setDampingRatio(dampingRatio))
                .start();
    }

    private void updateOmniBox() {
        if (ninjaWebView == null) return;
        updateAddressBar();

        String url = ninjaWebView.getUrl();
        View progressBarCompose = findViewById(R.id.main_progress_bar_compose);
        View progressBarView = findViewById(R.id.main_progress_bar);
        if (isHomePage(url)) {
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            View fab_bubble = findViewById(R.id.fab_bubble);
            if (fab_bubble != null) fab_bubble.setVisibility(GONE);
            if (contentFrame != null) contentFrame.setTranslationY(0f);
            if (progressBarCompose != null) progressBarCompose.setTranslationY(0f);
            if (progressBarView != null) progressBarView.setTranslationY(0f);
            isAddressBarCollapsed = false;
        } else {
            if (composeAddressBar != null) {
                composeAddressBar.setVisibility(VISIBLE);
                composeAddressBar.setTranslationY(0f);
            }
            if (contentFrame != null) contentFrame.setTranslationY(0f);
            if (progressBarCompose != null) progressBarCompose.setTranslationY(0f);
            if (progressBarView != null) progressBarView.setTranslationY(0f);
            View fab_bubble = findViewById(R.id.fab_bubble);
            if (fab_bubble != null) fab_bubble.setVisibility(GONE);
            isAddressBarCollapsed = false;
        }

        if (url != null) {
            ninjaWebView.initPreferences(url);
            if (ninjaWebView.isForeground()) {
                if (progressBar != null) progressBar.setVisibility(GONE);
                if (fab_menu != null) setProfileIcon(fab_menu, url);
            }
        }
    }

    private void initSearchOnSite () {
        searchOnSiteLayout = findViewById(R.id.searchOnSiteLayout);
        searchOnSiteInput = findViewById(R.id.searchOnSite_input);
        Button searchOnSite_buttonClose = findViewById(R.id.searchOnSite_buttonClose);
        TextInputLayout searchOnSite_textField = findViewById(R.id.searchOnSite_textField);
        if (searchOnSite_buttonClose != null) {
            searchOnSite_buttonClose.setOnClickListener(v -> {
                if (searchOnSiteInput != null && searchOnSiteInput.getText().length() > 0) {
                    searchOnSiteInput.setText("");
                    if (ninjaWebView != null) ninjaWebView.clearMatches();
                } else {
                    if (searchOnSiteInput != null) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(searchOnSiteInput.getWindowToken(), 0);
                    }
                    if (searchOnSiteLayout != null) searchOnSiteLayout.setVisibility(GONE);
                    if (appBar != null) appBar.setVisibility(VISIBLE);
                }
            });
        }
        if (searchOnSite_textField != null) {
            searchOnSite_textField.setStartIconOnClickListener(v -> {
                if (ninjaWebView != null) ninjaWebView.findNext(false);
            });
            searchOnSite_textField.setEndIconOnClickListener(v -> {
                if (ninjaWebView != null) ninjaWebView.findNext(true);
            });
        }
        if (searchOnSiteInput != null) {
            searchOnSiteInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override
                public void afterTextChanged(Editable s) {
                    if (ninjaWebView != null) {
                        String query = s.toString();
                        if (query.isEmpty()) {
                            ninjaWebView.clearMatches();
                        } else {
                            ninjaWebView.findAllAsync(query);
                        }
                    }
                }
            });
        }
    }

    private void initPullToRefresh() {
        androidx.compose.ui.platform.ComposeView refreshBarCompose = findViewById(R.id.refresh_bar_compose);
        View addressBarForMargin = findViewById(R.id.compose_address_bar);
        if (refreshBarCompose != null) {
            // Remove from RelativeLayout if present and re-add directly to window overlay so WebView hardware layer can never obscure it
            android.view.ViewParent parent = refreshBarCompose.getParent();
            if (parent instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) parent).removeView(refreshBarCompose);
            }
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            // 56dp was a guess and didn't match the address bar's real height on this
            // device, so the spinner landed on top of the wavy progress line instead of
            // below it. Measure the actual address bar height once it's laid out, and
            // keep it in sync if that height ever changes (font scale, theme, etc.).
            params.topMargin = (int) HelperUnit.convertDpToPixel(56f, this);
            addContentView(refreshBarCompose, params);
            com.petal.browser.compose.composable.PetalRefreshBarBridge.bindRefreshBar(refreshBarCompose, this, refreshState);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                refreshBarCompose.setElevation(200f);
                refreshBarCompose.setTranslationZ(200f);
            }
            refreshBarCompose.bringToFront();

            if (addressBarForMargin != null) {
                final android.widget.FrameLayout.LayoutParams finalParams = params;
                final androidx.compose.ui.platform.ComposeView finalRefreshBar = refreshBarCompose;
                addressBarForMargin.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int realHeight = v.getHeight();
                    if (realHeight > 0 && finalParams.topMargin != realHeight) {
                        finalParams.topMargin = realHeight;
                        finalRefreshBar.setLayoutParams(finalParams);
                    }
                });
            }
        }

        androidx.compose.ui.platform.ComposeView downloadBannerCompose = findViewById(R.id.download_banner_compose);
        if (downloadBannerCompose != null) {
            com.petal.browser.compose.downloads.PetalDownloadBannerBridge.bindDownloadBanner(
                downloadBannerCompose,
                this,
                this::showDownloads
            );
        }

        if (contentFrame == null) return;

        // Works the same for every page hosted in main_content: a normal web
        // page (NinjaWebView), the home screen, or settings/downloads - all of
        // them get swapped into this same container, and PullToRefreshFrameLayout
        // intercepts the drag regardless of what's currently inside it.
        contentFrame.setPullDistanceDp(300f);
        contentFrame.setCanPull(() -> {
            // If internal native Compose views (Settings, History, Downloads, Account) are swapped into contentFrame, disable pull to refresh
            if (contentFrame != null && contentFrame.getChildCount() > 0) {
                for (int i = 0; i < contentFrame.getChildCount(); i++) {
                    View child = contentFrame.getChildAt(i);
                    if (child != ninjaWebView) {
                        return false;
                    }
                }
            }
            String currentUrl = ninjaWebView != null ? ninjaWebView.getUrl() : null;
            if (currentUrl == null) currentUrl = "";
            boolean isInternalPage = isHomePage(currentUrl) ||
                    currentUrl.startsWith("petal://") ||
                    currentUrl.contains("petal://settings") ||
                    currentUrl.contains("petal://history") ||
                    currentUrl.contains("petal://downloads") ||
                    currentUrl.contains("petal://account") ||
                    currentUrl.contains("petal://incognito");
            boolean isScrolledToTop = ninjaWebView != null && ninjaWebView.getScrollY() <= 0;
            return !isInternalPage && isScrolledToTop && !refreshState.isRefreshing();
        });

        final androidx.compose.ui.platform.ComposeView finalRefreshView = refreshBarCompose;
        contentFrame.setOnPullListener(progress -> {
            if (finalRefreshView != null && progress > 0f) {
                finalRefreshView.bringToFront();
            }
            float prevProgress = refreshState.getPullProgress();
            refreshState.setPullProgress(progress);
            if (progress >= 0.75f && prevProgress < 0.75f) {
                com.petal.browser.haptics.PetalHapticEngine.getInstance(BrowserActivity.this)
                    .playIfEnabled(BrowserActivity.this, com.petal.browser.haptics.PetalHapticEngine.Pattern.TICK, 0.6f);
            }
            if (ninjaWebView != null) {
                if (progress > 0f) {
                    if (ninjaWebView.getLayerType() != View.LAYER_TYPE_SOFTWARE) {
                        ninjaWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    }
                } else if (!refreshState.isRefreshing()) {
                    if (ninjaWebView.getLayerType() != View.LAYER_TYPE_HARDWARE) {
                        ninjaWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    }
                }
            }
        });

        contentFrame.setOnReleaseListener(triggered -> {
            if (!triggered) {
                refreshState.setPullProgress(0f);
                if (ninjaWebView != null && ninjaWebView.getLayerType() != View.LAYER_TYPE_HARDWARE) {
                    ninjaWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }
                return;
            }
            com.petal.browser.haptics.PetalHapticEngine.getInstance(BrowserActivity.this)
                .playIfEnabled(BrowserActivity.this, com.petal.browser.haptics.PetalHapticEngine.Pattern.CLICK, 0.75f);
            refreshState.setRefreshing(true);
            refreshState.setPullProgress(1.0f);
            if (ninjaWebView != null) {
                if (ninjaWebView.getLayerType() != View.LAYER_TYPE_SOFTWARE) {
                    ninjaWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                }
                ninjaWebView.reload();
            } else {
                resetRefreshState();
            }
        });
    }

    /**
     * Hides both the pull-to-refresh spinner and the top page-loading progress bar.
     * Call this whenever an internal, non-webpage screen (Settings, Downloads,
     * History, Account Sync, Omnibox, the home screen, etc.) is swapped into
     * contentFrame, so neither overlay - both of which live outside the normal
     * view hierarchy so the WebView can never obscure them - can be left showing
     * (or showing stale progress) on top of a screen that isn't an actual webpage.
     */
    private void hideRefreshAndProgressOverlays() {
        if (refreshState != null) {
            refreshState.setRefreshing(false);
            refreshState.setPullProgress(0f);
        }
        androidx.compose.ui.platform.ComposeView progressBarCompose = findViewById(R.id.main_progress_bar_compose);
        if (progressBarCompose != null) {
            com.petal.browser.ui.components.PetalProgressBarBridge.hide(progressBarCompose);
        }
        View downloadBanner = findViewById(R.id.download_banner_compose);
        if (downloadBanner != null) {
            downloadBanner.setVisibility(GONE);
        }
    }

    public void resetRefreshState() {
        runOnUiThread(() -> {
            if (refreshState != null) {
                refreshState.setRefreshing(false);
                refreshState.setPullProgress(0f);
            }
            if (ninjaWebView != null && ninjaWebView.getLayerType() != View.LAYER_TYPE_HARDWARE) {
                ninjaWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        });
    }
    public void initSearch() {
        RecordAction action = new RecordAction(this);
        List<Record> list = action.listEntries(activity);
        adapterSearch = new AdapterSearch(this, R.layout.item_list, list);
        list_search.setAdapter(adapterSearch);
        list_search.setTextFilterEnabled(true);
        adapterSearch.notifyDataSetChanged();
        list_search.setSelection(adapter.getCount() - 1);
        list_search.setOnItemClickListener((parent, view, position, id) -> {
            hideSearch();
            String url = ((TextView) view.findViewById(R.id.dateView)).getText().toString();
            ninjaWebView.loadUrl(url);
        });
        list_search.setOnItemLongClickListener((adapterView, view, i, l) -> {
            String title = ((TextView) view.findViewById(R.id.titleView)).getText().toString();
            String url = ((TextView) view.findViewById(R.id.dateView)).getText().toString();
            showOverflow(dialogSearch, list_search, 2, title, url, null, null, 0);
            return true;
        });
    }

    @Override
    public void showOverview() {
        try {
            captureBrowserMainPreview();
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            com.petal.browser.ui.components.PetalTabSwitcherBridge.showTabSwitcherSheet(
                this,
                currentAlbumController,
                album -> {
                    showAlbum(album);
                    return kotlin.Unit.INSTANCE;
                },
                album -> {
                    removeAlbumSilently(album);
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    BrowserContainer.clear();
                    addAlbum(getString(R.string.app_name), sp.getString("favoriteURL", "about:blank"), true);
                    saveOpenedTabs();
                    return kotlin.Unit.INSTANCE;
                },
                () -> {
                    addAlbum(getString(R.string.app_name), sp.getString("favoriteURL", "about:blank"), true);
                    return kotlin.Unit.INSTANCE;
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error showing tabs overview", e);
        }
    }

    public void hideSearch() {
        dialogSearch.cancel();
        try {dialogCustomSearches.cancel();} catch (Exception e) {Log.i(TAG, "dialogCustomSearches:" + e);}
    }

    public void hideOverview() {
        dialogOverview.cancel();
    }

    private void setSelectedTab() {
        if (overViewTab.equals(getString(R.string.album_title_tab))) bottom_navigation.setSelectedItemId(R.id.page_0);
        else if (overViewTab.equals(getString(R.string.album_title_bookmarks))) bottom_navigation.setSelectedItemId(R.id.page_2);
        else if (overViewTab.equals(getString(R.string.album_title_history))) bottom_navigation.setSelectedItemId(R.id.page_3);
    }

    public void hideOverflow () {
        try {dialog_overflow.cancel();} catch (Exception e) {Log.i(TAG, "Overflow already closed:" + e);}
    }

    // Hilfsmethode, um nur ausgewählte Items aus dem lokalen Speicher zu holen
    private List<MenuItem> loadSelectedFromStorage() {
        SharedPreferences prefs = getSharedPreferences(Settings_Menu.PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(Settings_Menu.KEY_LIST, null);
        List<MenuItem> selected = new ArrayList<>();
        if (json != null) {
            Type type = new TypeToken<ArrayList<MenuItem>>() {}.getType();
            List<MenuItem> masterList = new Gson().fromJson(json, type);
            for (MenuItem item : masterList) {
                if (item.isSelected()) {
                    selected.add(item);
                }
            }
        }
        return selected;
    }

    public void removeItemByName(String name, List<MenuItem> selectedItemsList, AdapterMenu adapter) {
        int indexToRemove = -1;
        // 1. Position des Elements in der aktuellen Grid-Liste finden
        for (int i = 0; i < selectedItemsList.size(); i++) {
            if (selectedItemsList.get(i).getTitle().equalsIgnoreCase(name)) {
                indexToRemove = i;
                break;
            }
        }
        // Wenn das Element im aktuellen Grid existiert
        if (indexToRemove != -1) {
            // 2. Aus der Liste für die Anzeige entfernen
            selectedItemsList.remove(indexToRemove);
            // 3. Den Adapter über das Entfernen informieren (zeigt eine schöne Animation)
            adapter.notifyItemRemoved(indexToRemove);
        }
    }

    public void showOverflowMenu(View anchorView) {
        boolean isBookmarked = false;
        if (ninjaWebView != null && ninjaWebView.getUrl() != null) {
            RecordAction action = new RecordAction(this);
            action.open(false);
            isBookmarked = action.checkBookmark(ninjaWebView.getUrl());
            action.close();
        }
        boolean canGoBack = ninjaWebView != null && ninjaWebView.canGoBack();
        boolean canGoForward = ninjaWebView != null && ninjaWebView.canGoForward();
        String profile = NinjaWebView.getProfile();
        boolean isDesktopSite = sp.getBoolean(profile + "_desktop", false);
        boolean isAdBlock = sp.getBoolean("sp_ad_block", sp.getBoolean(profile + "_adBlock", true));

        boolean isMediaActive = isMediaPlaying || (customView != null || fullscreenHolder != null || videoView != null);

        com.petal.browser.ui.components.PetalOverflowBridge.showOverflowMenu(
            this,
            ninjaWebView != null && ninjaWebView.getTitle() != null ? ninjaWebView.getTitle() : "",
            ninjaWebView != null && ninjaWebView.getUrl() != null ? ninjaWebView.getUrl() : "",
            isBookmarked,
            canGoBack,
            canGoForward,
            isDesktopSite,
            isAdBlock,
            isMediaActive,
            new com.petal.browser.ui.components.PetalOverflowMenuActionHandler() {
                @Override
                public void onGoBack() {
                    if (ninjaWebView != null && ninjaWebView.canGoBack()) {
                        ninjaWebView.goBack();
                    }
                }

                @Override
                public void onGoForward() {
                    if (ninjaWebView != null && ninjaWebView.canGoForward()) {
                        ninjaWebView.goForward();
                    }
                }

                @Override
                public void onToggleBookmark() {
                    if (ninjaWebView != null && ninjaWebView.getUrl() != null) {
                        saveBookmark(ninjaWebView.getTitle(), ninjaWebView.getUrl());
                    }
                }

                @Override
                public void onOpenDownloadsShortcut() {
                    showDownloads();
                }

                @Override
                public void onOpenPageInfo() {
                    if (ninjaWebView != null && fab_menu != null) {
                        showDialogFastToggle(HelperUnit.domain(ninjaWebView.getUrl()), ninjaWebView.getUrl(), fab_menu);
                    }
                }

                @Override
                public void onReload() {
                    if (ninjaWebView != null) {
                        ninjaWebView.reload();
                    }
                }

                @Override
                public void onToggleDesktopSite(boolean enabled) {
                    sp.edit().putBoolean(profile + "_desktop", enabled).apply();
                    sp.edit().putBoolean("profileStandard_desktop", enabled).apply();
                    if (ninjaWebView != null) {
                        ninjaWebView.setDesktopMode(enabled);
                    }
                    NinjaToast.show(BrowserActivity.this, enabled ? "Desktop site requested" : "Mobile site requested");
                }

                @Override
                public void onToggleAdBlock(boolean enabled) {
                    sp.edit().putBoolean("sp_ad_block", enabled)
                            .putBoolean(profile + "_adBlock", enabled)
                            .putBoolean("profileStandard_adBlock", enabled)
                            .apply();
                    if (ninjaWebView != null) {
                        ninjaWebView.initPreferences(ninjaWebView.getUrl());
                        ninjaWebView.reload();
                    }
                    NinjaToast.show(BrowserActivity.this, enabled ? "AdBlocker Enabled" : "AdBlocker Disabled");
                }

                @Override
                public void onNewTab() {
                    addAlbum(getString(R.string.app_name), sp.getString("favoriteURL", "about:blank"), true);
                }

                @Override
                public void onNewIncognitoTab() {
                    addAlbum("Incognito Tab", sp.getString("favoriteURL", "about:blank"), true, true);
                    NinjaToast.show(BrowserActivity.this, "Opened Incognito Tab");
                }

                @Override
                public void onOpenHistory() {
                    showHistoryScreen();
                }

                @Override
                public void onDeleteBrowsingData() {
                    startActivity(new Intent(BrowserActivity.this, com.petal.browser.activity.Settings_Delete.class));
                }

                @Override
                public void onOpenDownloads() {
                    showDownloads();
                }

                @Override
                public void onOpenBookmarks() {
                    showBookmarksPage();
                }

                @Override
                public void onInstallPwa() {
                    savePageOffline();
                }

                @Override
                public void onSearchOnSite() {
                    searchOnSite();
                }

                @Override
                public void onPrintPdf() {
                    try {
                        createWebPrintJob(ninjaWebView);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onSavePage() {
                    try {
                        saveBookmark(ninjaWebView != null ? ninjaWebView.getTitle() : "", ninjaWebView != null ? ninjaWebView.getUrl() : "");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onShareLink() {
                    if (ninjaWebView != null) {
                        shareLink(ninjaWebView.getTitle(), ninjaWebView.getUrl());
                    }
                }

                @Override
                public void onViewSource() {
                    if (ninjaWebView != null && ninjaWebView.getUrl() != null) {
                        ninjaWebView.loadUrl("view-source:" + ninjaWebView.getUrl());
                    }
                }

                @Override
                public void onOpenSettings() {
                    openSettingsScreen();
                }

                @Override
                public void onOpenPetalAi() {
                    com.petal.browser.ui.components.PetalAiSearchBridge.showAiSearchResult(BrowserActivity.this, "");
                }

                @Override
                public void onTriggerMediaMode() {
                    boolean isPipSupported = getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
                    boolean isAutoPipEnabled = sp.getBoolean("sp_auto_pip", true);
                    boolean isBgPlayEnabled = sp.getBoolean("sp_background_play", false);

                    if (isPipSupported && isAutoPipEnabled) {
                        triggerSystemPipMode();
                    } else if (isBgPlayEnabled) {
                        NinjaToast.show(BrowserActivity.this, "Playing media in background mode");
                        moveTaskToBack(true);
                    } else {
                        com.petal.browser.media.PetalMediaBridge.enterPipIfSupported(BrowserActivity.this, customView);
                    }
                }
            }
        );
    }

    /**
     * Chrome-style full-screen Omnibox search page, replacing both the legacy
     * AlertDialog-based dialogSearch and the old bottom-sheet PetalOmniboxOverlay.
     * Mounts into contentFrame exactly like showDownloads()/showHistoryScreen(), so it
     * gets the same predictive-back gesture handling and "last page" underlay preview
     * as every other full-screen surface instead of living in a separate dialog window.
     */
    public void showOmniboxPage(String initialQuery) {
        try {
            if (BrowserContainer.size() == 0) {
                addAlbum(getString(R.string.app_name), sp.getString("favoriteURL", "about:blank"), true);
            }
            captureBrowserMainPreview();
            contentFrame.removeAllViews();
            if (appBar != null) appBar.setVisibility(GONE);
            LinearLayout appBar_buttons = findViewById(R.id.appBar_buttons);
            if (appBar_buttons != null) appBar_buttons.setVisibility(GONE);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            if (composeAddressBar == null) composeAddressBar = findViewById(R.id.compose_address_bar);
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            View fab_bubble_omnibox = findViewById(R.id.fab_bubble);
            if (fab_bubble_omnibox != null) fab_bubble_omnibox.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
            String pageTitle = ninjaWebView != null ? ninjaWebView.getTitle() : "";
            String pageUrl = ninjaWebView != null ? ninjaWebView.getUrl() : "";
            Bitmap favicon = ninjaWebView != null ? ninjaWebView.getFavicon() : null;

            View omniboxView = com.petal.browser.ui.components.PetalOmniboxBridge.createOmniboxView(
                BrowserActivity.this,
                initialQuery != null ? initialQuery : "",
                pageTitle != null ? pageTitle : "",
                pageUrl != null ? pageUrl : "",
                favicon,
                () -> {
                    showAlbum(currentAlbumController);
                    return kotlin.Unit.INSTANCE;
                },
                result -> {
                    if (result != null && !result.trim().isEmpty()) {
                        String targetUrl = com.petal.browser.unit.BrowserUnit.queryWrapper(BrowserActivity.this, result.trim());
                        if (currentAlbumController != null && ninjaWebView != null) {
                            ninjaWebView.loadUrl(targetUrl);
                            showAlbum(currentAlbumController, targetUrl);
                        } else if (BrowserContainer.size() > 0) {
                            AlbumController controller = BrowserContainer.get(0);
                            if (controller instanceof NinjaWebView) {
                                ((NinjaWebView) controller).loadUrl(targetUrl);
                            }
                            showAlbum(controller, targetUrl);
                        } else {
                            addAlbum(null, targetUrl, true);
                        }
                    }
                    return kotlin.Unit.INSTANCE;
                }
            );
            contentFrame.addView(omniboxView);
        } catch (Exception e) {
            Log.e(TAG, "Error showing omnibox page", e);
        }
    }

    public void showDownloads() {
        try {
            captureBrowserMainPreview();
            contentFrame.removeAllViews();
            if (appBar != null) appBar.setVisibility(GONE);
            LinearLayout appBar_buttons = findViewById(R.id.appBar_buttons);
            if (appBar_buttons != null) appBar_buttons.setVisibility(GONE);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            if (composeAddressBar == null) composeAddressBar = findViewById(R.id.compose_address_bar);
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            View fab_bubble_downloads = findViewById(R.id.fab_bubble);
            if (fab_bubble_downloads != null) fab_bubble_downloads.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
            View downloadView = PetalDownloadBridge.createDownloadView(BrowserActivity.this, () -> {
                showAlbum(currentAlbumController);
                return kotlin.Unit.INSTANCE;
            });
            contentFrame.addView(downloadView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showHistoryScreen() {
        try {
            captureBrowserMainPreview();
            contentFrame.removeAllViews();
            if (appBar != null) appBar.setVisibility(GONE);
            LinearLayout appBar_buttons = findViewById(R.id.appBar_buttons);
            if (appBar_buttons != null) appBar_buttons.setVisibility(GONE);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            if (composeAddressBar == null) composeAddressBar = findViewById(R.id.compose_address_bar);
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            View fab_bubble_history = findViewById(R.id.fab_bubble);
            if (fab_bubble_history != null) fab_bubble_history.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
            View historyView = com.petal.browser.compose.history.PetalHistoryBridge.createHistoryView(
                BrowserActivity.this,
                url -> {
                    if (ninjaWebView != null) {
                        ninjaWebView.loadUrl(url);
                    }
                    showAlbum(currentAlbumController, url);
                },
                () -> startActivity(new Intent(BrowserActivity.this, com.petal.browser.activity.Settings_Delete.class)),
                () -> {
                    showAlbum(currentAlbumController);
                    return kotlin.Unit.INSTANCE;
                }
            );
            contentFrame.addView(historyView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showBookmarksPage() {
        try {
            captureBrowserMainPreview();
            contentFrame.removeAllViews();
            if (appBar != null) appBar.setVisibility(GONE);
            LinearLayout appBar_buttons = findViewById(R.id.appBar_buttons);
            if (appBar_buttons != null) appBar_buttons.setVisibility(GONE);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            if (composeAddressBar == null) composeAddressBar = findViewById(R.id.compose_address_bar);
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            View fab_bubble_bm = findViewById(R.id.fab_bubble);
            if (fab_bubble_bm != null) fab_bubble_bm.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
            View bookmarksView = com.petal.browser.compose.bookmarks.PetalBookmarksBridge.createBookmarksView(
                BrowserActivity.this,
                url -> {
                    if (ninjaWebView != null) {
                        ninjaWebView.loadUrl(url);
                    }
                    showAlbum(currentAlbumController, url);
                },
                () -> {
                    showAlbum(currentAlbumController);
                    return kotlin.Unit.INSTANCE;
                }
            );
            contentFrame.addView(bookmarksView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showBookmarksSheet() {
        showBookmarksPage();
    }

    public void showBookmarks() {
        showBookmarksPage();
    }

    public void savePageOffline() {
        if (ninjaWebView == null || ninjaWebView.getUrl() == null) return;
        String url = ninjaWebView.getUrl();
        if (url.startsWith("about:") || url.startsWith("petal://") || url.startsWith("file://")) {
            NinjaToast.show(this, "Cannot save internal page offline");
            return;
        }

        java.io.File offlineDir = new java.io.File(getExternalFilesDir(null), "OfflinePages");
        if (!offlineDir.exists()) {
            offlineDir.mkdirs();
        }

        String rawTitle = (ninjaWebView.getTitle() != null && !ninjaWebView.getTitle().trim().isEmpty())
                ? ninjaWebView.getTitle()
                : HelperUnit.domain(url);
        String sanitizedTitle = rawTitle.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = sanitizedTitle + "_" + System.currentTimeMillis() + ".mhtml";
        java.io.File archiveFile = new java.io.File(offlineDir, fileName);

        try {
            ninjaWebView.saveWebArchive(archiveFile.getAbsolutePath(), false, value -> {
                if (value != null) {
                    NinjaToast.show(BrowserActivity.this, "Saved website to view offline!");
                    com.petal.browser.compose.downloads.PetalLiveAlertManager.trackOfflinePage(
                            BrowserActivity.this, rawTitle, url, archiveFile.getAbsolutePath());
                } else {
                    NinjaToast.show(BrowserActivity.this, "Failed to save page offline");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error saving web archive offline", e);
            NinjaToast.show(BrowserActivity.this, "Failed to save page offline");
        }
    }

    public void showAccountSyncScreen() {
        try {
            captureBrowserMainPreview();
            contentFrame.removeAllViews();
            if (appBar != null) appBar.setVisibility(GONE);
            LinearLayout appBar_buttons = findViewById(R.id.appBar_buttons);
            if (appBar_buttons != null) appBar_buttons.setVisibility(GONE);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            if (composeAddressBar == null) composeAddressBar = findViewById(R.id.compose_address_bar);
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            View fab_bubble_account = findViewById(R.id.fab_bubble);
            if (fab_bubble_account != null) fab_bubble_account.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
            View accountSyncView = com.petal.browser.account.PetalAccountSyncBridge.createAccountSyncView(
                BrowserActivity.this,
                () -> {
                    showAlbum(currentAlbumController);
                    return kotlin.Unit.INSTANCE;
                },
                shortcut -> {
                    showAlbum(currentAlbumController);
                    if (shortcut != null && shortcut.getUrl() != null && ninjaWebView != null) {
                        ninjaWebView.loadUrl(shortcut.getUrl());
                    }
                    return kotlin.Unit.INSTANCE;
                }
            );
            contentFrame.addView(accountSyncView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void captureBrowserMainPreview() {
        // No-op preview snapshot placeholder for screen transition previews
    }

    public void showOverflow(Dialog dialog, View view, int hideMenu, String title, String url, final AdapterRecord adapterRecord, List<Record> recordList, int location) {
        showOverflowMenu(view != null ? view : findViewById(R.id.bottom_nav_compose));
    }

    public void showDialogFastToggle(String title, String url, FloatingActionButton floatingActionButton) {

        listStandard = new List_standard(context);
        ninjaWebView = (NinjaWebView) currentAlbumController;

        String profile;
        if (listStandard.isWhite(url)) {
            profile = HelperUnit.domain(url);
        } else {
            profile = sp.getString("profile", "profileStandard");
        }

        if (url != null) {
            MaterialAlertDialogBuilder builderFastToggle = new MaterialAlertDialogBuilder(context);
            View dialogViewFastToggle = View.inflate(context, R.layout.dialog_fast_toggle, null);
            builderFastToggle.setView(dialogViewFastToggle);
            AlertDialog dialogFastToggle = builderFastToggle.create();
            HelperUnit.setupDialog(context, dialogFastToggle);

            LinearLayout textGroup = dialogViewFastToggle.findViewById(R.id.textGroup);
            TextView overflowURL = dialogViewFastToggle.findViewById(R.id.textGroup_menuURL);
            overflowURL.setText(url);
            HelperUnit.setHighLightedText(context, overflowURL, url, HelperUnit.domain(url));
            TextView overflowTitle = dialogViewFastToggle.findViewById(R.id.textGroup_menuTitle);
            overflowTitle.setText(title);
            FaviconHelper.setFavicon(context, dialogViewFastToggle, url, R.id.menu_icon, R.drawable.icon_image_broken);
            textGroup.setOnClickListener(v ->
                    HelperUnit.showCustomSnackbarWithTwoActions(
                    this, dialogViewFastToggle, null,
                    title, "", url,
                    R.drawable.icon_share, () -> {
                        shareLink(title, url);
                        return true;
                    },
                    R.drawable.icon_close, () -> true
            ));

            FloatingActionButton buttonProfile = dialogViewFastToggle.findViewById(R.id.buttonProfile);
            setProfileIcon(buttonProfile, url);
            buttonProfile.setOnClickListener(v -> {
                String cat = "    ¯\\_(ツ)_/¯    ";
                Snackbar snackbar = Snackbar.make(dialogViewFastToggle, cat, Snackbar.LENGTH_LONG);
                HelperUnit.makeSnackbarRound(snackbar);
                snackbar.show();
            });
            buttonProfile.setOnLongClickListener(v -> {
                sp.edit().putString("profile", "profileStandard").apply();
                setProfileIcon(buttonProfile, url);
                dialogFastToggle.cancel();
                if (!listStandard.isWhite(url)){
                    ninjaWebView.reload();
                }
                return true;
            });

            Button ib_save = dialogViewFastToggle.findViewById(R.id.ib_save);
            Button ib_delete = dialogViewFastToggle.findViewById(R.id.ib_delete);

            if (listStandard.isWhite(url)) {
                ib_save.setVisibility(GONE);
                ib_delete.setVisibility(VISIBLE);
            } else {
                ib_save.setVisibility(VISIBLE);
                ib_delete.setVisibility(GONE);
            }

            RelativeLayout checkbox_reset = dialogViewFastToggle.findViewById(R.id.checkbox_reset);
            ImageView icon_standard = dialogViewFastToggle.findViewById(R.id.icon_standard);

            if (sp.getBoolean("sp_standard_always", true)) {
                icon_standard.setImageResource(R.drawable.icon_check);
            } else {
                icon_standard.setImageResource(R.drawable.icon_close);
            }

            if (sp.getBoolean("sp_standard_restart", true)) {
                icon_standard.setImageResource(R.drawable.icon_restart);
            }

            checkbox_reset.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(context, checkbox_reset);
                popupMenu.getMenuInflater().inflate(R.menu.menu_standard, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == R.id.menu_standardAlways) {
                        sp.edit().putBoolean("sp_standard_always", true).apply();
                        sp.edit().putBoolean("sp_standard_restart", false).apply();
                        icon_standard.setImageResource(R.drawable.icon_check);
                    } else if (menuItem.getItemId() == R.id.menu_standardNever) {
                        sp.edit().putBoolean("sp_standard_always", false).apply();
                        sp.edit().putBoolean("sp_standard_restart", false).apply();
                        icon_standard.setImageResource(R.drawable.icon_close);
                    } else if (menuItem.getItemId() == R.id.menu_standardRestart) {
                        sp.edit().putBoolean("sp_standard_always", false).apply();
                        sp.edit().putBoolean("sp_standard_restart", true).apply();
                        icon_standard.setImageResource(R.drawable.icon_restart);
                    }
                    return true;
                });
                // Showing the popup menu
                popupMenu.show();
            });

            Button checkbox_redirect = dialogViewFastToggle.findViewById(R.id.item_checkBox);
            checkbox_redirect.setOnClickListener(v -> new CustomRedirectsDialog().show(getSupportFragmentManager(),"redirect"));

            CheckBox checkbox_screenOn = dialogViewFastToggle.findViewById(R.id.checkbox_screenOn);
            checkbox_screenOn.setChecked(sp.getBoolean("sp_screenOn", false));
            checkbox_screenOn.setOnClickListener(v -> {
                sp.edit().putBoolean("sp_screenOn", checkbox_screenOn.isChecked()).apply();
                checkbox_screenOn.setChecked(sp.getBoolean("sp_screenOn", true));
                dialogFastToggle.cancel();
                triggerRebirth(context);
            });

            CheckBox checkbox_links = dialogViewFastToggle.findViewById(R.id.checkbox_links);
            checkbox_links.setChecked(sp.getBoolean("sp_tabBackground", false));
            checkbox_links.setOnClickListener(v -> {
                sp.edit().putBoolean("sp_tabBackground", checkbox_links.isChecked()).apply();
                checkbox_links.setChecked(sp.getBoolean("sp_tabBackground", true));
            });

            TextView titleViewSettings = dialogViewFastToggle.findViewById(R.id.titleViewSettings);
            String s = context.getString(R.string.app_name) + " " + context.getString(R.string.setting_label);
            titleViewSettings.setText(s);

            CheckBox checkbox_image = dialogViewFastToggle.findViewById(R.id.checkbox_image);
            checkbox_image.setChecked(sp.getBoolean(profile + "_images", false));
            checkbox_image.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_images", checkbox_image.isChecked()).apply();
                }  else if (profile.equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_images", checkbox_image.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_images", checkbox_image.isChecked()).apply();
                }
            });

            CheckBox checkbox_java = dialogViewFastToggle.findViewById(R.id.checkbox_java);
            checkbox_java.setChecked(sp.getBoolean(profile + "_javascript", false));
            checkbox_java.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_javascript", checkbox_java.isChecked()).apply();
                } else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_javascript", checkbox_java.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_javascript", checkbox_java.isChecked()).apply();
                }
            });

            CheckBox checkbox_javaPopUp = dialogViewFastToggle.findViewById(R.id.checkbox_javaPopUp);
            checkbox_javaPopUp.setChecked(sp.getBoolean(profile + "_javascriptPopUp", false));
            checkbox_javaPopUp.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_javascriptPopUp", checkbox_javaPopUp.isChecked()).apply();
                } else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_javascriptPopUp", checkbox_javaPopUp.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_javascriptPopUp", checkbox_javaPopUp.isChecked()).apply();
                }
            });

            CheckBox checkbox_cookies = dialogViewFastToggle.findViewById(R.id.checkbox_cookies);
            checkbox_cookies.setChecked(sp.getBoolean(profile + "_cookies", false));
            checkbox_cookies.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_cookies", checkbox_cookies.isChecked()).apply();
                } else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_cookies", checkbox_cookies.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_cookies", checkbox_cookies.isChecked()).apply();
                }
            });

            CheckBox checkbox_cookiesThirdParty = dialogViewFastToggle.findViewById(R.id.checkbox_cookiesThirdParty);
            checkbox_cookiesThirdParty.setChecked(sp.getBoolean(profile + "_cookiesThirdParty", false));
            checkbox_cookiesThirdParty.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_cookiesThirdParty", checkbox_cookiesThirdParty.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_cookiesThirdParty", checkbox_cookiesThirdParty.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_cookiesThirdParty", checkbox_cookiesThirdParty.isChecked()).apply();
                }
            });

            CheckBox checkbox_cookiesBanner = dialogViewFastToggle.findViewById(R.id.checkbox_cookiesBanner);
            checkbox_cookiesBanner.setChecked(sp.getBoolean(profile + "_deny_cookie_banners", true));
            checkbox_cookiesBanner.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_deny_cookie_banners", checkbox_cookiesBanner.isChecked()).apply();
                } else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_deny_cookie_banners", checkbox_cookiesBanner.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_deny_cookie_banners", checkbox_cookiesBanner.isChecked()).apply();
                }
            });

            CheckBox checkbox_fingerPrint = dialogViewFastToggle.findViewById(R.id.checkbox_fingerPrint);
            checkbox_fingerPrint.setChecked(sp.getBoolean(profile + "_fingerPrintProtection", true));
            checkbox_fingerPrint.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_fingerPrintProtection", checkbox_fingerPrint.isChecked()).apply();
                } else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_fingerPrintProtection", checkbox_fingerPrint.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_fingerPrintProtection", checkbox_fingerPrint.isChecked()).apply();
                }
            });

            CheckBox checkbox_adBlock = dialogViewFastToggle.findViewById(R.id.checkbox_adBlock);
            checkbox_adBlock.setChecked(sp.getBoolean(profile + "_adBlock", true));
            checkbox_adBlock.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_adBlock", checkbox_adBlock.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_adBlock", checkbox_adBlock.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_adBlock", checkbox_adBlock.isChecked()).apply();
                }
            });

            CheckBox checkbox_trackingURL = dialogViewFastToggle.findViewById(R.id.checkbox_trackingURL);
            checkbox_trackingURL.setChecked(sp.getBoolean(profile + "_trackingULS", true));
            checkbox_trackingURL.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_trackingULS", checkbox_trackingURL.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_trackingULS", checkbox_trackingURL.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_trackingULS", checkbox_trackingURL.isChecked()).apply();
                }
            });

            CheckBox checkbox_saveData = dialogViewFastToggle.findViewById(R.id.checkbox_saveData);
            checkbox_saveData.setChecked(sp.getBoolean(profile + "_saveData", true));
            checkbox_saveData.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_saveData", checkbox_saveData.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_saveData", checkbox_saveData.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_saveData", checkbox_saveData.isChecked()).apply();
                }
            });

            CheckBox checkbox_history = dialogViewFastToggle.findViewById(R.id.checkbox_history);
            checkbox_history.setChecked(sp.getBoolean(profile + "_saveHistory", true));
            checkbox_history.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_saveHistory", checkbox_history.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_saveHistory", checkbox_history.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_saveHistory", checkbox_history.isChecked()).apply();
                }
            });

            CheckBox checkbox_location = dialogViewFastToggle.findViewById(R.id.checkbox_location);
            checkbox_location.setChecked(sp.getBoolean(profile + "_location", false));
            checkbox_location.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_location", checkbox_location.isChecked()).apply();
                } else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_location", checkbox_location.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_location", checkbox_location.isChecked()).apply();
                }
            });

            CheckBox checkbox_mic = dialogViewFastToggle.findViewById(R.id.checkbox_mic);
            checkbox_mic.setChecked(sp.getBoolean(profile + "_microphone", false));
            checkbox_mic.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_microphone", checkbox_mic.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_microphone", checkbox_mic.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_microphone", checkbox_mic.isChecked()).apply();
                }
            });

            CheckBox checkbox_camera = dialogViewFastToggle.findViewById(R.id.checkbox_camera);
            checkbox_camera.setChecked(sp.getBoolean(profile + "_camera", false));
            checkbox_camera.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_camera", checkbox_camera.isChecked()).apply();
                } else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_camera", checkbox_camera.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_camera", checkbox_camera.isChecked()).apply();
                }
            });

            CheckBox checkbox_dom = dialogViewFastToggle.findViewById(R.id.checkbox_dom);
            checkbox_dom.setChecked(sp.getBoolean(profile + "_dom", false));
            checkbox_dom.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_dom", checkbox_dom.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_dom", checkbox_dom.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_dom", checkbox_dom.isChecked()).apply();
                }
            });

            RelativeLayout layout_nightView = dialogViewFastToggle.findViewById(R.id.layout_nightView);
            CheckBox checkbox_nightView = dialogViewFastToggle.findViewById(R.id.checkbox_nightView);
            int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if ((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) && !sp.getString("sp_theme", "1").equals("2")) {
                layout_nightView.setVisibility(VISIBLE);
            } else  {
                layout_nightView.setVisibility(GONE);
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                checkbox_nightView.setChecked(sp.getBoolean(profile + "_night", true));
                checkbox_nightView.setOnClickListener(v -> {
                    if (listStandard.isWhite(url)){
                        sp.edit().putBoolean(profile + "_night", checkbox_nightView.isChecked()).apply();
                    }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                        ninjaWebView.setProfileChanged();
                        setProfileIcon(buttonProfile, url);
                        sp.edit().putBoolean(NinjaWebView.getProfile() + "_night", checkbox_nightView.isChecked()).apply();
                    } else {
                        sp.edit().putBoolean(NinjaWebView.getProfile() + "_night", checkbox_nightView.isChecked()).apply();
                    }
                });
            }

            CheckBox checkbox_desktop = dialogViewFastToggle.findViewById(R.id.checkbox_desktop);
            checkbox_desktop.setChecked(sp.getBoolean(profile + "_desktop", false));
            checkbox_desktop.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_desktop", checkbox_desktop.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_desktop", checkbox_desktop.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_desktop", checkbox_desktop.isChecked()).apply();
                }
            });

            CheckBox checkbox_drm = dialogViewFastToggle.findViewById(R.id.checkbox_drm);
            checkbox_drm.setChecked(sp.getBoolean(profile + "_drm", true));
            checkbox_drm.setOnClickListener(v -> {
                if (listStandard.isWhite(url)){
                    sp.edit().putBoolean(profile + "_drm", checkbox_drm.isChecked()).apply();
                }  else if (NinjaWebView.getProfile().equals("profileStandard")) {
                    ninjaWebView.setProfileChanged();
                    setProfileIcon(buttonProfile, url);
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_drm", checkbox_drm.isChecked()).apply();
                } else {
                    sp.edit().putBoolean(NinjaWebView.getProfile() + "_drm", checkbox_drm.isChecked()).apply();
                }
            });

            HelperUnit.applyBouncyTouchFeedback(ib_save);
            ib_save.setOnClickListener(v -> {
                listStandard.removeDomain(HelperUnit.domain(url));
                listStandard.addDomain(HelperUnit.domain(url));
                String profileToSave = HelperUnit.domain(url);
                sp.edit()
                        .putBoolean(profileToSave + "_saveData", checkbox_saveData.isChecked())
                        .putBoolean(profileToSave + "_images", checkbox_image.isChecked())
                        .putBoolean(profileToSave + "_adBlock", checkbox_adBlock.isChecked())
                        .putBoolean(profileToSave + "_trackingULS", checkbox_trackingURL.isChecked())
                        .putBoolean(profileToSave + "_location", checkbox_location.isChecked())
                        .putBoolean(profileToSave + "_fingerPrintProtection", checkbox_fingerPrint.isChecked())
                        .putBoolean(profileToSave + "_cookies", checkbox_cookies.isChecked())
                        .putBoolean(profileToSave + "_cookiesThirdParty", checkbox_cookiesThirdParty.isChecked())
                        .putBoolean(profileToSave + "_deny_cookie_banners", checkbox_cookiesBanner.isChecked())
                        .putBoolean(profileToSave + "_javascript", checkbox_java.isChecked())
                        .putBoolean(profileToSave + "_javascriptPopUp", checkbox_javaPopUp.isChecked())
                        .putBoolean(profileToSave + "_saveHistory", checkbox_history.isChecked())
                        .putBoolean(profileToSave + "_camera", checkbox_camera.isChecked())
                        .putBoolean(profileToSave + "_microphone", checkbox_mic.isChecked())
                        .putBoolean(profileToSave + "_dom", checkbox_dom.isChecked())
                        .putBoolean(profileToSave + "_night", checkbox_nightView.isChecked())
                        .putBoolean(profileToSave + "_desktop", checkbox_desktop.isChecked()).apply();
                if (sp.getBoolean("sp_standard_always", true)) {
                    sp.edit().putString("profile", "profileStandard").apply();
                    setProfileIcon(buttonProfile, url);
                }
                setProfileIcon(buttonProfile, url);
                dialogFastToggle.cancel();
                ninjaWebView.reload();
            });

            HelperUnit.applyBouncyTouchFeedback(ib_delete, 0.88f);
            ib_delete.setOnClickListener(view -> {
                listStandard.removeDomain(HelperUnit.domain(url));
                String profileToSave = HelperUnit.domain(url);
                sp.edit()
                        .remove(profileToSave + "_saveData")
                        .remove(profileToSave + "_images")
                        .remove(profileToSave + "_adBlock")
                        .remove(profileToSave + "_trackingULS")
                        .remove(profileToSave + "_location")
                        .remove(profileToSave + "_fingerPrintProtection")
                        .remove(profileToSave + "_cookies")
                        .remove(profileToSave + "_cookiesThirdParty")
                        .remove(profileToSave + "_deny_cookie_banners")
                        .remove(profileToSave + "_javascript")
                        .remove(profileToSave + "_javascriptPopUp")
                        .remove(profileToSave + "_saveHistory")
                        .remove(profileToSave + "_camera")
                        .remove(profileToSave + "_microphone")
                        .remove(profileToSave + "_dom")
                        .remove(profileToSave + "_night")
                        .remove(profileToSave + "_desktop").apply();
                if (sp.getBoolean("sp_standard_always", true)) {
                    sp.edit().putString("profile", "profileStandard").apply();
                    setProfileIcon(buttonProfile, url);
                }
                setProfileIcon(buttonProfile, url);
                dialogFastToggle.cancel();
                ninjaWebView.reload();
            });

            Button ib_reload = dialogViewFastToggle.findViewById(R.id.ib_reload);
            HelperUnit.applyBouncyTouchFeedback(ib_reload);
            ib_reload.setOnClickListener(view -> {
                if (ninjaWebView != null) {
                    dialogFastToggle.cancel();
                    ninjaWebView.reload();
                }
            });

            Button ib_settings = dialogViewFastToggle.findViewById(R.id.ib_settings);
            HelperUnit.applyBouncyTouchFeedback(ib_settings);
            ib_settings.setOnClickListener(view -> {
                if (ninjaWebView != null) {
                    dialogFastToggle.cancel();
                    Intent settings = new Intent(BrowserActivity.this, Settings_Activity.class);
                    startActivity(settings);
                }
            });

            Button button_help = dialogViewFastToggle.findViewById(R.id.button_help);
            HelperUnit.applyBouncyTouchFeedback(button_help);
            button_help.setOnClickListener(view -> {
                dialogFastToggle.cancel();
                if (ninjaWebView != null) {
                    ninjaWebView.loadUrl("about:blank");
                    showAlbum(currentAlbumController, "about:blank");
                }
            });
            dialogFastToggle.setOnDismissListener(dialogInterface -> setProfileIcon(floatingActionButton,url));
            dialogFastToggle.show();

            if (SDK_INT >= Build.VERSION_CODES.TIRAMISU && sp.getBoolean("sp_tabBackground", false)) {
                int notificationAllowed = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS);
                if (notificationAllowed != PackageManager.PERMISSION_GRANTED) {
                    HelperUnit.showCustomSnackbarWithTwoActions(
                            context, dialogViewFastToggle, null,
                            getString(R.string.dialog_backGround), getString(R.string.app_permission), "",
                            R.drawable.icon_check, () -> {
                                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1234567);
                                return true;
                            },
                            R.drawable.icon_close, () -> true
                    );
                }
            }
        } else {
            NinjaToast.show(context, getString(R.string.app_error));
        }
    }
    
    public void setProfileIcon (FloatingActionButton floatingActionButton, String url) {
        String profile = sp.getString("profile", "profileStandard");
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.colorError, typedValue, true);
        int color = typedValue.data;
        if (profile.equals("profileStandard")) {
            floatingActionButton.setImageResource(R.drawable.icon_profile_standard);
            fab_menu.setImageResource(R.drawable.icon_profile_standard);
        } else {
            floatingActionButton.setImageResource(R.drawable.icon_profile_changed);
            fab_menu.setImageResource(R.drawable.icon_profile_changed);
        }
        listStandard = new List_standard(context);
        if (listStandard.isWhite(url)) {
            floatingActionButton.getDrawable().mutate().setTint(color);
            fab_menu.getDrawable().mutate().setTint(color);
        }
    }

    private void showDialogFilter() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.dialog_menu, null);
        builder.setTitle(R.string.setting_filter);
        builder.setIcon(R.drawable.icon_filter);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(context, dialog);
        CardView cardView = dialogView.findViewById(R.id.item_CardViewItem);
        cardView.setVisibility(GONE);

        GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
        final List<GridItem> gridList = new LinkedList<>();
        sp.edit().putString("showFilterDialogX", "true").apply();
        HelperUnit.addFilterItems(activity, gridList);

        GridAdapter gridAdapter = new GridAdapter(context, gridList);
        menu_grid.setNumColumns(2);
        menu_grid.setHorizontalSpacing(20);
        menu_grid.setVerticalSpacing(20);
        menu_grid.setAdapter(gridAdapter);

        if (menu_grid.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) menu_grid.getLayoutParams();
            p.setMargins(56, 56, 56, 56);
            menu_grid.requestLayout();
        }

        gridAdapter.notifyDataSetChanged();
        menu_grid.setOnItemClickListener((parent, view, position, id) -> {
            filter = true;
            filterBy = gridList.get(position).getData();
            dialog.cancel();
            bottom_navigation.setSelectedItemId(R.id.page_2);
        });
        dialog.setOnCancelListener(dialogInterface -> sp.edit().putString("showFilterDialogX", "false").apply());
    }

    private void showDialogCustomSearches(String url) {
        search_input.clearFocus();
        if (dialogOverview.isShowing()) {
            dialogOverview.cancel();
        }
        ninjaWebView.stopLoading();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        View dialogView = View.inflate(context, R.layout.custom_redirects_list, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.redirects_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        ArrayList<CustomRedirect> redirects = new ArrayList<>();
        try {
            redirects = CustomSearchesHelper.getRedirects(sp);
        } catch (JSONException e) {
            Log.e("Searches parsing", e.toString());
        }
        AdapterCustomSearches adapter = new AdapterCustomSearches(context, url, redirects);
        recyclerView.setAdapter(adapter);
        if (url.length() > 150) {
            url = url.substring(0, 150) + " [...]";
        }
        String text = "-> " + url;
        builder.setTitle(R.string.custom_searches_title);
        builder.setMessage(text);
        builder.setIcon(R.drawable.icon_search);
        builder.setNegativeButton(R.string.create_new, ((dialogInterface, i) -> {
            MaterialAlertDialogBuilder builderAddCustom = new MaterialAlertDialogBuilder(context);
            View dialogViewAddCustom = View.inflate(context, R.layout.create_new_searches, null);
            TextInputEditText source = dialogViewAddCustom.findViewById(R.id.source);
            TextInputEditText target = dialogViewAddCustom.findViewById(R.id.target);
            builderAddCustom.setTitle(R.string.custom_searches_title);
            builderAddCustom.setIcon(R.drawable.icon_search);
            builderAddCustom.setPositiveButton(R.string.app_cancel, null);
            builderAddCustom.setNegativeButton(R.string.app_ok, ((dialogInterface2, i2) -> {
                String sourceText = Objects.requireNonNull(source.getText()).toString();
                String targetText = Objects.requireNonNull(target.getText()).toString();
                if (targetText.isEmpty() || sourceText.isEmpty()) return;
                adapter.addRedirect(new CustomRedirect(sourceText, targetText));
                try {
                    CustomSearchesHelper.saveRedirects(adapter.getRedirects());
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }));
            builderAddCustom.setView(dialogViewAddCustom);
            AlertDialog dialogCustomSearchesNew = builderAddCustom.create();
            dialogCustomSearchesNew.show();
            HelperUnit.setupDialog(context, dialogCustomSearchesNew);
        }));
        builder.setPositiveButton(R.string.app_cancel, ((dialogInterface, i) -> dialogCustomSearches.cancel()));
        builder.setView(dialogView);
        dialogCustomSearches = builder.create();
        dialogCustomSearches.show();
        dialogCustomSearches.setCancelable(false);
        HelperUnit.setupDialog(context, dialogCustomSearches);
    }
    private void doubleTapsQuit() {
        if (!sp.getBoolean("sp_close_browser_confirm", true)) {
            finishAndRemoveTask();
        } else {
            com.petal.browser.ui.components.PetalConfirmSheetBridge.showQuitBrowserConfirmation(this, this::finishAndRemoveTask);
        }
    }
    private void saveOpenedTabs() {
        ArrayList<String> openTabs = new ArrayList<>();
        for (int i = 0; i < BrowserContainer.size(); i++) {
            if (currentAlbumController == BrowserContainer.get(i))
                openTabs.add(0, ((NinjaWebView) (BrowserContainer.get(i))).getUrl());
            else openTabs.add(((NinjaWebView) (BrowserContainer.get(i))).getUrl());
        }
        sp.edit().putString("openTabs", TextUtils.join("‚‗‚", openTabs)).apply();
        com.petal.browser.unit.TabSessionManager.saveSession(this);
    }
    private void setCustomFullscreen(boolean fullscreen) {
        if (fullscreen) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                final WindowInsetsController insetsController = getWindow().getInsetsController();
                if (insetsController != null) {
                    insetsController.hide(WindowInsets.Type.statusBars());
                    insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
            else getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); }
        else {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                final WindowInsetsController insetsController = getWindow().getInsetsController();
                if (insetsController != null) {
                    insetsController.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
            }
            else getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN); }
    }
    private void copyLink(String url) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("text", url);
        Objects.requireNonNull(clipboard).setPrimaryClip(clip);
        NinjaToast.show(this, getString(R.string.app_done));
    }

    public void shareLink(String title, String url) {

        hideOverview();
        List_standard listStandard = new List_standard(context);
        String profile = sp.getString("profile", "profileStandard");
        if (listStandard.isWhite(url)) profile = HelperUnit.domain(url);

        boolean removeTracking = sp.getBoolean(profile + "_trackingULS", true);

        if (removeTracking && url.contains("?") && url.contains("/")) {

            String lastIndex = url.substring(url.lastIndexOf("/"));
            String tracking = url.substring(url.lastIndexOf("?"));
            String urlClean = url.replace(tracking, "");

            if (lastIndex.contains(tracking)) {

                String m = context.getString(R.string.dialog_tracking) + " \"" + tracking + "\"" + "?";

                if (m.length() > 150) {
                    m = m.substring(0, 150) + " [...]?\"";
                }

                GridItem item_01 = new GridItem(context.getString(R.string.app_ok), R.drawable.icon_check);
                GridItem item_02 = new GridItem( context.getString(R.string.app_no), R.drawable.icon_close);
                GridItem item_03 = new GridItem( context.getString(R.string.menu_edit), R.drawable.icon_edit);

                View dialogView = View.inflate(context, R.layout.dialog_menu, null);
                MaterialAlertDialogBuilder builderTrack = new MaterialAlertDialogBuilder(context);

                LinearLayout textGroup = dialogView.findViewById(R.id.textGroup);
                TextView overflowURL = dialogView.findViewById(R.id.overflowURL);
                overflowURL.setText(url);
                TextView overflowMessage = dialogView.findViewById(R.id.overflowMessage);
                overflowMessage.setText(m);
                HelperUnit.setHighLightedText(context, overflowURL, url, HelperUnit.domain(url));
                TextView menuTitle = dialogView.findViewById(R.id.overflowTitle);
                menuTitle.setText(HelperUnit.domain(url));
                textGroup.setOnClickListener(v ->
                        HelperUnit.showCustomSnackbarWithTwoActions(
                                context, dialogView, null,
                                title, "", url,
                                R.drawable.icon_share, () -> {
                                    shareLink(title, url);
                                    return true;
                                },
                                R.drawable.icon_close, () -> true
                        ));

                FloatingActionButton buttonProfile = dialogView.findViewById(R.id.buttonProfile);
                NinjaWebView.getBrowserController().setProfileIcon(buttonProfile, url);
                FaviconHelper.setFavicon(context, dialogView, url, R.id.menu_icon, R.drawable.icon_image_broken);
                buttonProfile.setOnClickListener(v -> showDialogFastToggle(title,url, buttonProfile));
                buttonProfile.setOnLongClickListener(v -> {
                    sp.edit().putString("profile", "profileStandard").apply();
                    NinjaWebView.getBrowserController().setProfileIcon(buttonProfile, url);
                    if (!listStandard.isWhite(url)){
                        ninjaWebView.reload();
                    }return false;
                });
                builderTrack.setView(dialogView);

                AlertDialog dialogTrack = builderTrack.create();
                dialogTrack.show();
                HelperUnit.setupDialog(context, dialogTrack);

                GridView menu_grid = dialogView.findViewById(R.id.menu_grid);
                final List<GridItem> gridList = new LinkedList<>();
                gridList.add(gridList.size(), item_01);
                gridList.add(gridList.size(), item_02);
                gridList.add(gridList.size(), item_03);
                GridAdapter gridAdapter = new GridAdapter(context, gridList);
                menu_grid.setAdapter(gridAdapter);
                gridAdapter.notifyDataSetChanged();
                menu_grid.setOnItemClickListener((parent, view, position, id) -> {
                    switch (position) {

                        case 0:
                            dialogTrack.cancel();
                            Intent sharingIntentClean;
                            sharingIntentClean = new Intent(Intent.ACTION_SEND);
                            sharingIntentClean.setType("text/plain");
                            sharingIntentClean.putExtra(Intent.EXTRA_SUBJECT, title);
                            sharingIntentClean.putExtra(Intent.EXTRA_TEXT, urlClean);
                            context.startActivity(Intent.createChooser(sharingIntentClean, (context.getString(R.string.menu_share_link))));
                            break;
                        case 1:
                            dialogTrack.cancel();
                            Intent sharingIntent;
                            sharingIntent = new Intent(Intent.ACTION_SEND);
                            sharingIntent.setType("text/plain");
                            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
                            sharingIntent.putExtra(Intent.EXTRA_TEXT, url);
                            context.startActivity(Intent.createChooser(sharingIntent, (context.getString(R.string.menu_share_link))));
                            break;
                        case 2:
                            dialogTrack.cancel();
                            View dialogEdit = View.inflate(context, R.layout.dialog_edit, null);
                            TextInputLayout editBottomLayout = dialogEdit.findViewById(R.id.editBottomLayout);
                            TextInputLayout editTopLayout = dialogEdit.findViewById(R.id.editTopLayout);
                            editBottomLayout.setHint(activity.getString(R.string.dialog_URL_hint));
                            editTopLayout.setVisibility(GONE);
                            EditText input = dialogEdit.findViewById(R.id.editBottom);
                            input.setText(url);
                            HelperUnit.showSoftKeyboard(input);

                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                            builder.setTitle(context.getString(R.string.menu_edit));
                            builder.setIcon(R.drawable.icon_tracking);
                            builder.setView(dialogEdit);
                            Dialog dialog = builder.create();

                            Button ib_cancel = dialogEdit.findViewById(R.id.editCancel);
                            HelperUnit.applyBouncyTouchFeedback(ib_cancel);
                            ib_cancel.setOnClickListener(v -> dialog.cancel());
                            Button ib_ok = dialogEdit.findViewById(R.id.editOK);
                            HelperUnit.applyBouncyTouchFeedback(ib_ok);
                            ib_ok.setOnClickListener(v -> {
                                dialog.dismiss();
                                String newValue = Objects.requireNonNull(input.getText()).toString();
                                Intent sharingIntentEdit;
                                sharingIntentEdit = new Intent(Intent.ACTION_SEND);
                                sharingIntentEdit.setType("text/plain");
                                sharingIntentEdit.putExtra(Intent.EXTRA_SUBJECT, title);
                                sharingIntentEdit.putExtra(Intent.EXTRA_TEXT, newValue);
                                context.startActivity(Intent.createChooser(sharingIntentEdit, (context.getString(R.string.menu_share_link))));
                            });
                            dialog.show();
                            HelperUnit.setupDialog(context, dialog);
                            break;
                    }
                });
            }
        } else {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            sharingIntent.putExtra(Intent.EXTRA_TEXT, url);
            context.startActivity(Intent.createChooser(sharingIntent, (context.getString(R.string.menu_share_link))));
        }
    }

    private void postLink(String data, Dialog dialogParent) {
        String urlForPosting = sp.getString("urlForPosting", "");

        if (!urlForPosting.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("text", data);
            Objects.requireNonNull(clipboard).setPrimaryClip(clip);
            NinjaToast.show(this, getString(R.string.app_done));
            addAlbum("", urlForPosting, true);
        } else {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            View dialogViewSubMenu = View.inflate(context, R.layout.dialog_edit, null);
            TextInputLayout editBottomLayout = dialogViewSubMenu.findViewById(R.id.editBottomLayout);
            TextInputLayout editTopLayout = dialogViewSubMenu.findViewById(R.id.editTopLayout);
            editBottomLayout.setHint(activity.getString(R.string.dialog_URL_hint));
            editTopLayout.setVisibility(GONE);

            builder.setView(dialogViewSubMenu);
            builder.setTitle(activity.getString(R.string.dialog_postOnWebsite));
            builder.setMessage(getString(R.string.dialog_postOnWebsiteHint));
            builder.setIcon(R.drawable.icon_post);

            Dialog dialog = builder.create();
            dialog.show();
            HelperUnit.setupDialog(context, dialog);

            Button ib_cancel = dialogViewSubMenu.findViewById(R.id.editCancel);
            HelperUnit.applyBouncyTouchFeedback(ib_cancel);
            ib_cancel.setOnClickListener(v -> dialog.cancel());
            Button ib_ok = dialogViewSubMenu.findViewById(R.id.editOK);
            HelperUnit.applyBouncyTouchFeedback(ib_ok);
            ib_ok.setOnClickListener(v -> {
                EditText editBottom = dialogViewSubMenu.findViewById(R.id.editBottom);
                String shareTop = editBottom.getText().toString().trim();
                sp.edit().putString("urlForPosting", shareTop).apply();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("text", data);
                Objects.requireNonNull(clipboard).setPrimaryClip(clip);
                NinjaToast.show(this, getString(R.string.app_done));
                addAlbum("", shareTop, true);
                dialog.cancel();
                try {
                    dialogParent.cancel();
                } catch (Exception e) {
                    Log.i(TAG, "shouldOverrideUrlLoading Exception:" + e);
                }
            });
        }
    }
    private void searchOnSite() {
        if (appBar != null) appBar.setVisibility(GONE);
        if (searchOnSiteLayout != null) {
            searchOnSiteLayout.setVisibility(VISIBLE);
            if (searchOnSiteInput != null) {
                searchOnSiteInput.requestFocus();
                HelperUnit.showSoftKeyboard(searchOnSiteInput);
            }
        }
    }
    private void saveBookmark(String title, String url) {
        if (url == null || url.trim().isEmpty() || isHomePage(url)) {
            NinjaToast.show(this, "Home page cannot be bookmarked");
            return;
        }
        RecordAction action = new RecordAction(context);
        action.open(true);
        String message = context.getString(R.string.app_error) + ": " + context.getString(R.string.app_error_save);
        if (action.checkUrl(url, RecordUnit.TABLE_BOOKMARK))
            NinjaToast.show(this, message);
        else {
            action.addBookmark(new Record(title, url, 0, 0));
            NinjaToast.show(this, R.string.app_done); }
        action.close();
    }

    private void performGesture(String gesture, String url) {
        String gestureAction = Objects.requireNonNull(sp.getString(gesture, "0"));
        switch (gestureAction) {
            case "01":
                break;
            case "02":
                if (ninjaWebView.canGoForward()) {
                    ninjaWebView.stopLoading();
                    WebBackForwardList mWebBackForwardList = ninjaWebView.copyBackForwardList();
                    String historyUrl = mWebBackForwardList.getItemAtIndex(mWebBackForwardList.getCurrentIndex() + 1).getUrl();
                    ninjaWebView.initPreferences(historyUrl);
                    ninjaWebView.goForward();
                }
                else NinjaToast.show(this, R.string.toast_webview_forward);
                break;
            case "03":
                if (fullscreenHolder != null || customView != null || videoView != null) {
                    Log.v(TAG, "Petal in fullscreen mode");
                } else if (ninjaWebView.canGoBack()){
                    sp.edit().putBoolean("backPressed", true).apply();
                    ninjaWebView.goBack();
                } else removeAlbum(currentAlbumController);
                break;
            case "04":
                ninjaWebView.pageUp(true);
                break;
            case "05":
                ninjaWebView.pageDown(true);
                break;
            case "06":
                showAlbum(nextAlbumController(false));
                break;
            case "07":
                showAlbum(nextAlbumController(true));
                break;
            case "08":
                showOverview();
                break;
            case "09":
                addAlbum(getString(R.string.app_name), Objects.requireNonNull(sp.getString("favoriteURL", "about:blank")), true);
                break;
            case "10":
                removeAlbum(currentAlbumController);
                break;
            case "11":
                overViewTab = getString(R.string.album_title_tab);
                setSelectedTab();
                showOverview();
                break;
            case "12":
                shareLink(ninjaWebView.getTitle(), Objects.requireNonNull(ninjaWebView.getUrl()));
                break;
            case "13":
                searchOnSite();
                break;
            case "14":
                saveBookmark(ninjaWebView.getTitle(), url);
                break;
            case "16":
                ninjaWebView.reload();
                break;
            case "17":
                ninjaWebView.loadUrl(Objects.requireNonNull(sp.getString("favoriteURL", "about:blank")));
                showAlbum(currentAlbumController, sp.getString("favoriteURL", "about:blank"));
                break;
            case "18":
                bottom_navigation.setSelectedItemId(R.id.page_2);
                showOverview();
                showDialogFilter();
                break;
            case "19":
                showDialogFastToggle(ninjaWebView.getTitle(), ninjaWebView.getUrl(), fab_menu);
                break;
            case "22":
                sp.edit().putBoolean("sp_screenOn", !sp.getBoolean("sp_screenOn", false)).apply();
                triggerRebirth(context);
                break;
            case "24":
                copyLink(ninjaWebView.getUrl());
                break;
            case "25":
                Intent settings = new Intent(BrowserActivity.this, Settings_Activity.class);
                startActivity(settings);
                break;
            case "26":
                doubleTapsQuit();
                break;
            case "27":
                sp.edit().putString("profile", "profileStandard").apply();
                ninjaWebView.reload();
                break;
            case "29":
                showDownloads();
                break;
            case "30":
                overViewTab = getString(R.string.album_title_bookmarks);
                setSelectedTab();
                showOverview();
                break;
            case "31":
                overViewTab = getString(R.string.album_title_history);
                setSelectedTab();
                showOverview();
                break;
            case "32":
                ninjaWebView.loadUrl(sp.getString("favoriteURL", "about:blank"));
                showAlbum(currentAlbumController, sp.getString("favoriteURL", "about:blank"));
                break;
        }
    }

    private void closeTabConfirmation(final Runnable okAction) {
        if (!sp.getBoolean("sp_close_tab_confirm", false)) {
            okAction.run();
        } else {
            String tabTitle = ninjaWebView != null ? ninjaWebView.getTitle() : "";
            com.petal.browser.ui.components.PetalConfirmSheetBridge.showTabCloseConfirmation(this, tabTitle, okAction);
        }
    }
    private File copyHtmlToCache(Context context, Uri uri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            File cacheFile = new File(context.getCacheDir(), "temp_preview.html");
            FileOutputStream outputStream = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
            return cacheFile;
        } catch (Exception e) {
            String text = context.getString(R.string.app_error) + ": " + e;
            NinjaToast.show(context, text);
            return null;
        }
    }

    public void closeAllIncognitoTabs() {
        try {
            List<AlbumController> toRemove = new ArrayList<>();
            for (AlbumController album : BrowserContainer.list()) {
                if (album instanceof NinjaWebView && ((NinjaWebView) album).isIncognito()) {
                    toRemove.add(album);
                }
            }
            for (AlbumController album : toRemove) {
                removeAlbum(album);
            }
            com.petal.browser.compose.incognito.PetalIncognitoSessionManager.setIncognitoTabCount(this, 0);
            NinjaToast.show(this, "Closed all Incognito tabs");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void dispatchIntent(Intent intent) {
        if (intent == null) return;

        // Tapping a download notification (see PetalLiveAlertManager) launches
        // BrowserActivity with this extra so it lands on the Download Manager
        // screen instead of just reopening to whatever was last on screen.
        if (intent.getBooleanExtra("open_downloads", false)) {
            intent.removeExtra("open_downloads");
            showDownloads();
            return;
        }

        boolean isPwaMode = intent.getBooleanExtra("pwa_mode", false);
        if (isPwaMode) {
            View composeAddr = findViewById(R.id.compose_address_bar);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (composeAddr != null) composeAddr.setVisibility(GONE);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
        }

        String action = intent.getAction();
        if (com.petal.browser.compose.incognito.PetalIncognitoSessionManager.ACTION_CLOSE_INCOGNITO.equals(action)) {
            closeAllIncognitoTabs();
            intent.setAction("");
            return;
        }
        String url = intent.getStringExtra(Intent.EXTRA_TEXT);
        Uri dataUri = intent.getData();
        String mimeType = intent.getType();
        if ("".equals(action)) {
            Log.i(TAG, "resumed FOSS browser");
        } else if (filePathCallback != null) {
            filePathCallback = null;
            getIntent().setAction("");
        } else if (Intent.ACTION_VIEW.equals(action) && dataUri != null) {
            String scheme = dataUri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)) {
                sp.edit().putBoolean("show_overview", false).apply();
                getIntent().setAction("");
                addAlbum(null, dataUri.toString(), true);
                if (isPwaMode) {
                    View composeAddr = findViewById(R.id.compose_address_bar);
                    View bottomNav = findViewById(R.id.bottom_nav_compose);
                    if (composeAddr != null) composeAddr.setVisibility(GONE);
                    if (bottomNav != null) bottomNav.setVisibility(GONE);
                } else if (currentAlbumController != null) {
                    showAlbum(currentAlbumController, dataUri.toString());
                }
                return;
            }
            String fileName = null;
            // 1. Echten Dateinamen aus der URI ermitteln
            if ("content".equals(dataUri.getScheme())) {
                try (Cursor cursor = getContentResolver().query(dataUri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex); // z.B. "notizen.org"
                        }
                    }
                } catch (Exception e) {
                    NinjaToast.show(context, getString(R.string.app_error));
                }
            } else if ("file".equals(dataUri.getScheme())) {
                fileName = dataUri.getLastPathSegment();
            }
            // 2. Dateiendung prüfen und filtern
            if (fileName != null) {
                String extension = "";
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot >= 0) {
                    extension = fileName.substring(lastDot + 1).toLowerCase();
                }
                // Liste aller erlaubten Text- und Code-Endungen
                List<String> allowedExtensions = Arrays.asList(
                        "html", "txt", "xml", "json", "java", "md", "js", "css", "sh", "py", "org", "gpx"
                );
                if (!allowedExtensions.contains(extension)) {
                    Toast.makeText(this, getString(R.string.dialog_supported), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            String filePath = dataUri.getPath();
            // Liefert den Pfad (z. B. /storage/emulated/0/Download/file.txt)
            // Falls der Pfad über einen ContentProvider verschlüsselt ist, nutzen wir die URI als Identifikator
            String displayPath = filePath != null ? filePath : dataUri.toString();
            // Die virtuelle oder echte Datei-URL für die WebView (wichtig für webView.getUrl())
            String virtualFileUrl = filePath != null ? "file://" + filePath : dataUri.toString();
            String fileContent = readTextFromUri(this, dataUri);
            if (!fileContent.trim().isEmpty()) {
                if (mimeType != null && mimeType.contains("html")) {
                    // HTML über die sichere Cache-Methode laden (damit CSS/Bilder funktionieren)
                    File localHtmlFile = copyHtmlToCache(this, dataUri);
                    if (localHtmlFile != null && localHtmlFile.exists()) {
                        addAlbum(fileName, "file://" + localHtmlFile.getAbsolutePath(), true);
                        ninjaWebView.loadUrl("file://" + localHtmlFile.getAbsolutePath());
                    } else {
                        addAlbum(fileName, "about:blank" , true);
                        ninjaWebView.loadDataWithBaseURL(null, fileContent, "text/html", "UTF-8", null);
                    }
                } else {
                    // UNIVERSAL-METHODE für XML, JSON, TXT, JAVA, MD, etc.
                    String langClass = "language-txt";
                    String formattedContent = fileContent;
                    // Mime-Type oder Inhalts-Erkennung für das Syntax-Highlighting
                    if (mimeType != null && (mimeType.contains("xml") || fileContent.trim().startsWith("<"))) {
                        langClass = "language-xml";
                    } else if (mimeType != null && (mimeType.contains("json") || mimeType.contains("javascript"))
                            || fileContent.trim().startsWith("{") || fileContent.trim().startsWith("[")) {
                        langClass = "language-json";
                        try {
                            if (fileContent.trim().startsWith("{")) {
                                org.json.JSONObject jsonObject = new org.json.JSONObject(fileContent);
                                formattedContent = jsonObject.toString(2);
                            } else if (fileContent.trim().startsWith("[")) {
                                org.json.JSONArray jsonArray = new org.json.JSONArray(fileContent);
                                formattedContent = jsonArray.toString(2);
                            }
                        } catch (Exception ignored) {
                            NinjaToast.show(context, getString(R.string.app_error));
                        }
                    } else {
                        assert fileName != null;
                        if (fileName.endsWith(".java")) {
                            langClass = "language-java";
                        } else if (fileName.endsWith(".md")) {
                            langClass = "language-markdown";
                        }
                    }
                    // HTML-Sonderzeichen maskieren
                    String escapedContent = formattedContent.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    // Das universelle HTML-Gerüst mit Dateiname, Pfad und responsivem Code-Block
                    // NEU: Das <title>-Tag sorgt dafür, dass ninjaWebView.getTitle() den Dateinamen liefert
                    String htmlWrapper = "<html><head>"
                            + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                            + "<title>" + fileName + "</title>"
                            + "<link rel='stylesheet' href='https://cloudflare.com' />"
                            + "<style>"
                            + "  body { margin: 0; padding: 15px; background: #fafafa; font-family: sans-serif; color: #333; }"
                            + "  .file-info { background: #eaeaea; padding: 10px; border-radius: 5px; font-size: 12px; margin-bottom: 15px; border-left: 4px solid #007bb6; word-break: break-all; }"
                            + "  .file-info b { color: #111; }"
                            + "  pre, code { font-family: monospace !important; font-size: 13px !important; white-space: pre-wrap !important; word-wrap: break-word !important; }"
                            + "</style></head><body>"
                            + "<div class='file-info'>"
                            + "  <b>Datei:</b> " + fileName + "<br/>"
                            + "  <b>Pfad:</b> " + displayPath
                            + "</div>"
                            + "<pre class='" + langClass + "'><code class='" + langClass + "'>"
                            + escapedContent
                            + "</code></pre>"
                            + "<script src='https://cloudflare.com'></script>"
                            + "<script src='https://cloudflare.com'></script>"
                            + "</body></html>";
                    addAlbum(fileName, virtualFileUrl, true);
                    ninjaWebView.getSettings().setDefaultTextEncodingName("utf-8");
                    // WICHTIG: virtualFileUrl als BaseURL übergeben zwingt webView.getUrl() diesen Pfad anzuzeigen
                    ninjaWebView.loadDataWithBaseURL(virtualFileUrl, htmlWrapper, "text/html", "UTF-8", null);
                }
            } else {
                sp.edit().putBoolean("show_overview", false).apply();
                getIntent().setAction("");
                addAlbum(null, Objects.requireNonNull(getIntent().getData()).toString(), true);
                BrowserUnit.openInBackground(activity, ninjaWebView);
            }
        } else if ("postLink".equals(action)) {
            sp.edit().putBoolean("show_overview", false).apply();
            getIntent().setAction("");
            postLink(url, null);
        } else if ("customSearches".equals(action)) {
            sp.edit().putBoolean("show_overview", false).apply();
            getIntent().setAction("");
            if (BrowserContainer.size() == 0) {
                addAlbum(null, "", true);
            }
            assert url != null;
            showDialogCustomSearches(url);
        } else if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_PROCESS_TEXT)) {
            sp.edit().putBoolean("show_overview", false).apply();
            getIntent().setAction("");
            CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            assert text != null;
            url = text.toString();
            addAlbum(null, url, true);
        } else if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_WEB_SEARCH)) {
            sp.edit().putBoolean("show_overview", false).apply();
            getIntent().setAction("");
            url = Objects.requireNonNull(intent.getStringExtra(SearchManager.QUERY));
            addAlbum(null, url, true);
        } else if (url != null && Intent.ACTION_SEND.equals(action)) {
            sp.edit().putBoolean("show_overview", false).apply();
            getIntent().setAction("");
            addAlbum(null, url, true);
        } else if (com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_SEARCH.equals(action)
                || com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_AI_SEARCH.equals(action)) {
            try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); } catch (Exception ignored) {}
            getIntent().setAction("");
            sp.edit().putBoolean("show_overview", false).apply();
            pendingWidgetAction = () -> {
                showOmniboxPage("");
            };
            runOrDeferPendingWidgetAction();
        } else if (com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_VOICE.equals(action)) {
            try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); } catch (Exception ignored) {}
            getIntent().setAction("");
            sp.edit().putBoolean("show_overview", false).apply();
            pendingWidgetAction = () -> {
                addAlbum(null, "", true);
                try {
                    com.petal.browser.ui.components.PetalVoiceSearchBridge.showVoiceSearchSheet(this, result -> {
                        if (result != null && !result.trim().isEmpty()) {
                            String targetUrl = BrowserUnit.queryWrapper(BrowserActivity.this, result.trim());
                            if (ninjaWebView != null) {
                                ninjaWebView.loadUrl(targetUrl);
                                showAlbum(currentAlbumController, targetUrl);
                            } else {
                                addAlbum(null, targetUrl, true);
                            }
                        }
                        return kotlin.Unit.INSTANCE;
                    });
                } catch (Exception e) {
                    showOmniboxPage("");
                }
            };
            runOrDeferPendingWidgetAction();
        } else if (com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_INCOGNITO.equals(action)) {
            try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); } catch (Exception ignored) {}
            getIntent().setAction("");
            sp.edit().putBoolean("show_overview", false).apply();
            pendingWidgetAction = () -> {
                addAlbum(null, "petal://home", true, true);
            };
            runOrDeferPendingWidgetAction();
        } else if (com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_BOOKMARKS.equals(action)) {
            try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); } catch (Exception ignored) {}
            getIntent().setAction("");
            sp.edit().putBoolean("show_overview", false).apply();
            pendingWidgetAction = () -> {
                showBookmarksSheet();
            };
            runOrDeferPendingWidgetAction();
        } else if (com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_DOWNLOADS.equals(action)) {
            try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); } catch (Exception ignored) {}
            getIntent().setAction("");
            sp.edit().putBoolean("show_overview", false).apply();
            pendingWidgetAction = () -> {
                showDownloads();
            };
            runOrDeferPendingWidgetAction();
        } else if (com.petal.browser.widget.PetalSearchWidgetProvider.ACTION_OPEN_NEW_TAB.equals(action)) {
            try { overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); } catch (Exception ignored) {}
            getIntent().setAction("");
            sp.edit().putBoolean("show_overview", false).apply();
            pendingWidgetAction = () -> {
                addAlbum(null, "petal://home", true);
            };
            runOrDeferPendingWidgetAction();
        }
    }

    /**
     * Runs (or defers) a widget action queued up in {@link #pendingWidgetAction}.
     * <p>
     * A plain {@code contentFrame.post(...)} isn't reliable enough on a true cold start:
     * {@code dispatchIntent()} runs at the end of {@code onCreate()}, but {@code onStart()}
     * — called right after — can synchronously show the one-time Welcome dialog and/or the
     * "choose a search engine" dialog (see onStart() above). Those are shown *after* this
     * method queues the post, so the omnibox can end up added to contentFrame while it's
     * still visually buried underneath one of those modal dialogs; once the user dismisses
     * it, nothing re-triggers the omnibox, so they land on the plain home/tab page instead —
     * exactly the "widget opens the home page on first launch" bug.
     * <p>
     * Deferring to {@link #onWindowFocusChanged(boolean)} instead fixes this: the window
     * only regains real input focus once every startup dialog has actually been dismissed,
     * cold start or not, so the omnibox reliably shows on top of whatever's current rather
     * than getting shown-then-hidden underneath a dialog.
     */
    private void runOrDeferPendingWidgetAction() {
        if (contentFrame == null) return;
        if (hasWindowFocus()) {
            // Already focused and interactive (e.g. the widget was tapped while Petal was
            // already in the foreground) — nothing is going to steal focus afterward, so
            // just run on the next frame instead of waiting for a focus change that may
            // never come.
            contentFrame.post(this::consumePendingWidgetAction);
        }
        // Otherwise leave it queued; onWindowFocusChanged(true) will run it.
    }

    private void consumePendingWidgetAction() {
        Runnable action = pendingWidgetAction;
        pendingWidgetAction = null;
        if (action != null) {
            action.run();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && pendingWidgetAction != null && contentFrame != null) {
            contentFrame.post(this::consumePendingWidgetAction);
        }
    }
    private String readTextFromUri(Context context, Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                inputStream.close();
            }
        } catch (Exception ignored) {}
        return stringBuilder.toString();
    }

    private void setWebView(String title, final String url, final boolean foreground) {
        setWebView(title, url, foreground, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setWebView(String title, final String url, final boolean foreground, final boolean isIncognito) {
        ninjaWebView = new NinjaWebView(context);
        if (isIncognito) {
            ninjaWebView.setIncognito(true);
        }

        com.petal.browser.media.PetalMediaBridge bridge = new com.petal.browser.media.PetalMediaBridge(
                context,
                ninjaWebView,
                new com.petal.browser.media.PetalMediaBridge.MediaStateListener() {
                    @Override
                    public void onMediaPlay(String title, long positionMs, long durationMs) {
                        isMediaPlaying = true;
                        if (mediaService != null) {
                            mediaService.updateMediaState(title, ninjaWebView.getTitle(), true, positionMs, durationMs);
                        }
                    }

                    @Override
                    public void onMediaPause(long positionMs, long durationMs) {
                        isMediaPlaying = false;
                        if (mediaService != null) {
                            mediaService.updateMediaState(ninjaWebView.getTitle(), ninjaWebView.getTitle(), false, positionMs, durationMs);
                        }
                    }

                    @Override
                    public void onMediaProgress(long positionMs, long durationMs) {
                        // Progress update handler
                    }

                    @Override
                    public void onMediaPlayingStateChanged(boolean playing) {
                        isMediaPlaying = playing;
                    }
                }
        );
        ninjaWebView.setMediaBridge(bridge);

        com.petal.browser.pwa.PetalPwaManager pwaManager = new com.petal.browser.pwa.PetalPwaManager(
                context,
                ninjaWebView,
                manifest -> runOnUiThread(() -> {
                    // Show PWA installation banner/notification when detected
                })
        );
        ninjaWebView.setPwaManager(pwaManager);
        ninjaWebView.setOnScrollChangeListener(new NinjaWebView.OnScrollChangeListener() {
            @Override
            public void onScrollDown() {
                runOnUiThread(() -> animateAddressBarCollapse(true));
            }

            @Override
            public void onScrollUp() {
                runOnUiThread(() -> animateAddressBarCollapse(false));
            }
        });

        ninjaWebView.setOnLongClickListener(v -> {
            WebView.HitTestResult result = ninjaWebView.getHitTestResult();
            int type = result.getType();

            if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                final String imageURL = result.getExtra();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                com.petal.browser.compose.menu.PetalLinkContextMenuBridge.show(
                    BrowserActivity.this,
                    HelperUnit.domain(imageURL),
                    imageURL,
                    imageURL,
                    new com.petal.browser.compose.menu.PetalLinkContextMenuHandler() {
                        @Override
                        public void onOpenInNewTab() {
                            addAlbum(getString(R.string.app_name), imageURL, false);
                        }

                        @Override
                        public void onOpenInNewTabInGroup() {
                            addAlbum(getString(R.string.app_name), imageURL, false);
                        }

                        @Override
                        public void onOpenInIncognitoTab() {
                            addAlbum(getString(R.string.app_name), imageURL, false);
                        }

                        @Override
                        public void onOpenInNewWindow() {
                            addAlbum(getString(R.string.app_name), imageURL, true);
                        }

                        @Override
                        public void onPreviewPage() {
                            addAlbum(getString(R.string.app_name), imageURL, true);
                        }

                        @Override
                        public void onCopyLinkAddress() {
                            HelperUnit.copy(BrowserActivity.this, imageURL);
                            NinjaToast.show(BrowserActivity.this, "Image URL copied");
                        }

                        @Override
                        public void onCopyLinkText() {
                            HelperUnit.copy(BrowserActivity.this, HelperUnit.domain(imageURL));
                            NinjaToast.show(BrowserActivity.this, "Domain copied");
                        }

                        @Override
                        public void onDownloadLink() {
                            try {
                                String fileName = android.webkit.URLUtil.guessFileName(imageURL, null, null);
                                com.petal.browser.unit.BrowserUnit.download(BrowserActivity.this, imageURL, fileName, null);
                                NinjaToast.show(BrowserActivity.this, "Download started");
                            } catch (Exception e) {
                                NinjaToast.show(BrowserActivity.this, "Failed to start download");
                            }
                        }

                        @Override
                        public void onDownloadImage() {
                            if (imageURL != null && !imageURL.trim().isEmpty()) {
                                com.petal.browser.unit.ImageActionHelper.downloadImage(BrowserActivity.this, imageURL);
                            } else {
                                NinjaToast.show(BrowserActivity.this, "No valid image URL found");
                            }
                        }

                        @Override
                        public void onAddToReadingList() {
                            try {
                                RecordAction action = new RecordAction(BrowserActivity.this);
                                action.open(true);
                                action.addBookmark(new Record(HelperUnit.domain(imageURL), imageURL, System.currentTimeMillis(), 0));
                                action.close();
                                NinjaToast.show(BrowserActivity.this, "Added to reading list");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onScanImage() {
                            if (imageURL != null && !imageURL.trim().isEmpty()) {
                                com.petal.browser.compose.mlkit.PetalImageScannerBridge.show(BrowserActivity.this, imageURL);
                            } else {
                                NinjaToast.show(BrowserActivity.this, "No valid image URL found");
                            }
                        }

                        @Override
                        public void onShareImage() {
                            if (imageURL != null && !imageURL.trim().isEmpty()) {
                                com.petal.browser.unit.ImageActionHelper.shareImage(BrowserActivity.this, imageURL);
                            } else {
                                NinjaToast.show(BrowserActivity.this, "No valid image URL found");
                            }
                        }

                        @Override
                        public void onShareLink() {
                            shareLink(HelperUnit.domain(imageURL), imageURL);
                        }
                    }
                );
                return true;
            }
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                final String urlResult = result.getExtra();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                com.petal.browser.compose.menu.PetalLinkContextMenuBridge.show(
                    BrowserActivity.this,
                    HelperUnit.domain(urlResult),
                    urlResult,
                    urlResult + "/favicon.ico",
                    new com.petal.browser.compose.menu.PetalLinkContextMenuHandler() {
                        @Override
                        public void onOpenInNewTab() {
                            addAlbum(getString(R.string.app_name), urlResult, false);
                        }

                        @Override
                        public void onOpenInNewTabInGroup() {
                            addAlbum(getString(R.string.app_name), urlResult, false);
                        }

                        @Override
                        public void onOpenInIncognitoTab() {
                            addAlbum(getString(R.string.app_name), urlResult, false);
                        }

                        @Override
                        public void onOpenInNewWindow() {
                            addAlbum(getString(R.string.app_name), urlResult, true);
                        }

                        @Override
                        public void onPreviewPage() {
                            addAlbum(getString(R.string.app_name), urlResult, true);
                        }

                        @Override
                        public void onCopyLinkAddress() {
                            HelperUnit.copy(BrowserActivity.this, urlResult);
                            NinjaToast.show(BrowserActivity.this, "Link copied");
                        }

                        @Override
                        public void onCopyLinkText() {
                            HelperUnit.copy(BrowserActivity.this, HelperUnit.domain(urlResult));
                            NinjaToast.show(BrowserActivity.this, "Link text copied");
                        }

                        @Override
                        public void onDownloadLink() {
                            try {
                                String fileName = android.webkit.URLUtil.guessFileName(urlResult, null, null);
                                com.petal.browser.unit.BrowserUnit.download(BrowserActivity.this, urlResult, fileName, null);
                                NinjaToast.show(BrowserActivity.this, "Download started");
                            } catch (Exception e) {
                                NinjaToast.show(BrowserActivity.this, "Failed to start download");
                            }
                        }

                        @Override
                        public void onDownloadImage() {
                            if (urlResult != null && !urlResult.trim().isEmpty()) {
                                com.petal.browser.unit.ImageActionHelper.downloadImage(BrowserActivity.this, urlResult);
                            }
                        }

                        @Override
                        public void onAddToReadingList() {
                            try {
                                RecordAction action = new RecordAction(BrowserActivity.this);
                                action.open(true);
                                action.addBookmark(new Record(HelperUnit.domain(urlResult), urlResult, System.currentTimeMillis(), 0));
                                action.close();
                                NinjaToast.show(BrowserActivity.this, "Added to reading list");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onScanImage() {
                            if (urlResult != null && !urlResult.trim().isEmpty()) {
                                com.petal.browser.compose.mlkit.PetalImageScannerBridge.show(BrowserActivity.this, urlResult);
                            }
                        }

                        @Override
                        public void onShareImage() {
                            if (urlResult != null && !urlResult.trim().isEmpty()) {
                                com.petal.browser.unit.ImageActionHelper.shareImage(BrowserActivity.this, urlResult);
                            }
                        }

                        @Override
                        public void onShareLink() {
                            shareLink(HelperUnit.domain(urlResult), urlResult);
                        }
                    }
                );
                return true;
            }
            return false;
        });

        // Must be set before loadUrl() below: browserController is a static field that
        // can still be null (e.g. right after a cold process start) when this runs, and
        // loadUrl() can trigger Chromium's onProgressChanged (-> updateTitle()) almost
        // immediately on the main looper - before it would otherwise be set further down.
        if (foreground) {
            ninjaWebView.setBrowserController(this);
        }

        if (title == null) title = getString(R.string.app_name);
        if (url == null) {
            ninjaWebView.setAlbumTitle(title, "about:blank");
            ninjaWebView.loadUrl("about:blank");
        } else {
            ninjaWebView.setAlbumTitle(title, url);
            if (url.trim().isEmpty()) ninjaWebView.loadUrl("about:blank");
            else ninjaWebView.loadUrl(url);
        }

        if (currentAlbumController != null) {
            ninjaWebView.setPredecessor(currentAlbumController);
            //save currentAlbumController and use when TAB is closed via Back button
            int index = BrowserContainer.indexOf(currentAlbumController) + 1;
            BrowserContainer.add(ninjaWebView, index);
        }
        else BrowserContainer.add(ninjaWebView);

        if (!foreground) ninjaWebView.deactivate();
        else {
            hideOverview();
            ninjaWebView.setBrowserController(this);
            ninjaWebView.activate();
            if (dialogOverview != null) dialogOverview.cancel();
            showAlbum(ninjaWebView);
        }
        try {
            View albumView = ninjaWebView.getAlbumView();
            if (albumView != null && tab_container != null) {
                if (albumView.getParent() != null) {
                    ((ViewGroup) albumView.getParent()).removeView(albumView);
                }
                tab_container.addView(albumView, WRAP_CONTENT, WRAP_CONTENT);
            }
        } catch (Exception ignored) {}
        updateOmniBox();
        updatePersistentBottomNav();
    }

    public synchronized void addAlbum(String title, final String url, final boolean foreground) {
        setWebView(title, url, foreground, false);
    }

    public synchronized void addAlbum(String title, final String url, final boolean foreground, final boolean isIncognito) {
        setWebView(title, url, foreground, isIncognito);
    }

    private void triggerRebirth(Context context) {
        sp.edit().putInt("restart_changed", 0).apply();
        sp.edit().putBoolean("restoreOnRestart", true).apply();
        Snackbar snackbar = Snackbar.make(ninjaWebView, R.string.toast_restart, Snackbar.LENGTH_SHORT);
        HelperUnit.makeSnackbarRound(snackbar);
        snackbar.setAction(context.getString(R.string.app_ok), (v -> {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
            assert intent != null;
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            context.startActivity(mainIntent);
            System.exit(0);
        }));
        snackbar.show();
    }

    public void installPwaShortcut() {
        if (ninjaWebView != null && ninjaWebView.getPwaManager() != null) {
            ninjaWebView.getPwaManager().installCurrentPwa(this);
            return;
        }
        try {
            if (ninjaWebView == null || ninjaWebView.getUrl() == null) return;
            String url = ninjaWebView.getUrl();
            String title = ninjaWebView.getTitle() != null && !ninjaWebView.getTitle().isEmpty() ? ninjaWebView.getTitle() : HelperUnit.domain(url);

            Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            shortcutIntent.setComponent(new android.content.ComponentName(this, BrowserActivity.class));

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.content.pm.ShortcutManager shortcutManager = getSystemService(android.content.pm.ShortcutManager.class);
                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                    android.content.pm.ShortcutInfo pinShortcutInfo = new android.content.pm.ShortcutInfo.Builder(this, "pwa_" + Math.abs(url.hashCode()))
                            .setShortLabel(title)
                            .setLongLabel(title)
                            .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
                            .setIntent(shortcutIntent)
                            .build();

                    shortcutManager.requestPinShortcut(pinShortcutInfo, null);
                    NinjaToast.show(this, "Added to Home screen");
                    return;
                }
            }
            Intent addIntent = new Intent();
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher));
            addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            sendBroadcast(addIntent);
            NinjaToast.show(this, "Added to Home screen");
        } catch (Exception e) {
            e.printStackTrace();
            NinjaToast.show(this, "Failed to create PWA shortcut");
        }
    }

    public void openApiIntegrationsHub() {
        openSettingsScreen(com.petal.browser.compose.settings.SettingsCategory.API_INTEGRATIONS);
    }

    private void openSettingsScreen() {
        openSettingsScreen(com.petal.browser.compose.settings.SettingsCategory.OVERVIEW);
    }

    private void openSettingsScreen(com.petal.browser.compose.settings.SettingsCategory initialCategory) {
        try {
            captureBrowserMainPreview();
            contentFrame.removeAllViews();
            if (appBar != null) appBar.setVisibility(GONE);
            LinearLayout appBar_buttons = findViewById(R.id.appBar_buttons);
            if (appBar_buttons != null) appBar_buttons.setVisibility(GONE);
            View bottomNav = findViewById(R.id.bottom_nav_compose);
            if (bottomNav != null) bottomNav.setVisibility(GONE);
            if (composeAddressBar == null) composeAddressBar = findViewById(R.id.compose_address_bar);
            if (composeAddressBar != null) composeAddressBar.setVisibility(GONE);
            View fab_bubble_settings = findViewById(R.id.fab_bubble);
            if (fab_bubble_settings != null) fab_bubble_settings.setVisibility(GONE);
            hideRefreshAndProgressOverlays();
            View settingsView = com.petal.browser.compose.settings.PetalSettingsBridge.createSettingsView(BrowserActivity.this, initialCategory, () -> {
                showAlbum(currentAlbumController);
                return kotlin.Unit.INSTANCE;
            });
            android.view.animation.AlphaAnimation fadeIn = new android.view.animation.AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(240);
            settingsView.startAnimation(fadeIn);
            contentFrame.addView(settingsView);
        } catch (Exception e) {
            startActivity(new Intent(BrowserActivity.this, Settings_Activity.class));
        }
    }

    public static View getView() {
        return ninjaWebView != null ? ninjaWebView.getRootView() : null;
    }

    public void createWebPrintJob(WebView webView) {
        if (webView == null) return;
        try {
            PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (printManager != null) {
                String jobName = getString(R.string.app_name) + " Document";
                PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
                printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        boolean backgroundPlay = sp != null && sp.getBoolean("sp_background_play", false);
        if (!backgroundPlay && ninjaWebView != null) {
            try {
                ninjaWebView.onPause();
                ninjaWebView.pauseTimers();
            } catch (Exception ignored) {}
        }
        try {
            com.petal.browser.unit.TabSessionManager.saveSession(this);
        } catch (Exception e) {
            Log.e(TAG, "Error saving tab session in onPause", e);
        }
    }
}