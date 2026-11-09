package com.petal.browser.activity;
import static android.content.ContentValues.TAG;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import androidx.activity.BackEventCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AppCompatDelegate;
import android.app.Dialog;
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
import androidx.activity.OnBackPressedCallback;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.PopupWindow;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import com.petal.browser.compose.downloads.PetalDownloadBridge;
import com.petal.browser.compose.home.PetalComposeBridge;
import com.petal.browser.compose.home.PetalHomeActionHandler;
import com.petal.browser.logger.PetalAppLogger;
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
import android.webkit.WebHistoryItem;
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
import com.petal.browser.activity.MainActivity;
import com.petal.browser.browser.AlbumController;
import com.petal.browser.browser.BrowserContainer;
import com.petal.browser.browser.BrowserController;
import com.petal.browser.browser.DataURIParser;
import com.petal.browser.browser.StandardDomainWhitelist;
import com.petal.browser.database.FaviconHelper;
import com.petal.browser.database.HistoryBookmarkContract;
import com.petal.browser.database.HistoryBookmarkRepository;
import com.petal.browser.database.Record;
import com.petal.browser.dialogs.CustomRedirectsDialog;
import com.petal.browser.objects.CustomRedirect;
import com.petal.browser.objects.CustomSearchesHelper;
import com.petal.browser.unit.PetalAndroidUtils;
import com.petal.browser.unit.PetalBackupManager;
import com.petal.browser.unit.PetalBrowserUtils;
import com.petal.browser.view.CustomSearchAdapter;
import com.petal.browser.view.GridAdapter;
import com.petal.browser.view.GridItem;
import com.petal.browser.view.MenuItem;
import com.petal.browser.view.PetalToast;
import com.petal.browser.view.RecordListAdapter;
import com.petal.browser.view.SwipeTouchListener;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * BrowserActivity (Hollowed-Out Trampoline Shell)
 * ─────────────────────────────────────────────────────────────────────────────
 * Under Option B (Pure Jetpack Compose), all browser UI, tab coordination, and
 * website rendering are hosted by [MainActivity] and [PetalRootApp].
 *
 * This class is maintained exclusively as a lightweight trampoline forwarding
 * external launches, shortcuts, and legacy intents to [MainActivity], and as an
 * interface stub preserving binary compatibility with legacy delegates.
 */
@AndroidEntryPoint
public class BrowserActivity extends AppCompatActivity implements BrowserController {

    // Menus
    public static final int INPUT_FILE_REQUEST_CODE = 1;
    public RecordListAdapter adapter;
    public ImageButton fab_overview;
    public ListView listView;
    // Views
    public TextInputEditText search_input;
    public TextView appBar_title;
    public View findInPageCompose;
    public boolean isFindInPageShowing = false;
    public String findInPageQuery = "";
    public int findInPageCurrentMatch = 0;
    public int findInPageTotalMatches = 0;
    public View customView;
    public WebChromeClient.CustomViewCallback customViewCallback;
    public com.petal.browser.media.PetalVideoPlayerOverlayBridge videoOverlayBridge;
    public VideoView videoView;
    public boolean isMediaPlaying = false;
    public FloatingActionButton fab_menu;
    public BadgeDrawable badgeDrawable;
    // Layouts
    public LinearProgressIndicator progressBar;
    public android.widget.FrameLayout contentFrame;
    public boolean isOverlayScreenShowing = false;
    public boolean isCustomFullscreenState = false;
    /**
     * True while a Compose overlay that is attached directly to the window decor view (e.g.
     * App Lock, via PetalAppLockBridge) owns the current back gesture. Unlike
     * isOverlayScreenShowing, this overlay lives outside contentFrame, so it is tracked
     * separately and must NOT be wired into performBackNavigation's isOverlayScreenShowing
     * branch (that branch calls removeOverlayViews()/showAlbum(), which would be
     * wrong here). It exists solely to stop the Activity-level predictive back animator from
     * running underneath the Compose-level one.
     */
    public boolean isDecorOverlayShowing = false;
    /**
     * Optional action to run instead of showAlbum() when performBackNavigation exits an overlay
     * screen. Used when child overlays need to return to their parent overlay screen.
     * Must be set BEFORE presentComposeScreen() and cleared by performBackNavigation().
     */
    public Runnable pendingOverlayBackAction = null;
    /** Container-transform state for the omnibox page (screen coords of the tapped bar). */
    private android.graphics.Rect pendingContainerOrigin = null;
    private android.graphics.Rect activeContainerOrigin = null;
    private float pendingContainerRadius = -1f;
    private float activeContainerRadius = -1f;
    /** Screenshot of the screen the omnibox opened from; shown behind the page only while it animates. */
    private android.graphics.Bitmap pendingContainerSnapshot = null;
    private android.graphics.Bitmap activeContainerSnapshot = null;
    private View activeContainerView = null;
    private boolean containerExitInFlight = false;
    private com.petal.browser.browser.PetalBrowserFeatures petalBrowserFeatures;
    public LinearLayout tab_container;
    public FrameLayout fullscreenHolder;
    public com.petal.browser.compose.composable.PetalRefreshBarState refreshState = new com.petal.browser.compose.composable.PetalRefreshBarState();
    public ListView list_search;
    public androidx.dynamicanimation.animation.SpringAnimation contentPaddingAnimator = null;
    /** Offset (px) still applied to the page views while the content spring settles. */
    public float contentOffsetApplied = 0f;
    // Others
    public BottomNavigationView bottom_navigation;
    public String overViewTab;
    public Activity activity;
    // Non-static instance context so internal methods resolve without static activity leakage
    public final Context context = this;
    public SharedPreferences sp;
    public StandardDomainWhitelist listStandard;
    public long newIcon;
    public long filterBy;
    public boolean filter;
    public ValueCallback<Uri[]> filePathCallback = null;
    public int statusBarTopInset = 0;
    public AlbumController currentAlbumController = null;
    private boolean cameraPermissionRequested = false;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private boolean restartPromptDismissedInSession = false;
    private long lastOnPausePreviewCaptureTime = 0L;
    private com.petal.browser.browser.PetalTabViewController singleTabViewController;
    private mozilla.components.lib.state.Store.Subscription browserStoreSubscription;
    public ValueCallback<Uri[]> mFilePathCallback;
    public String mCameraPhotoPath = null;
    public com.petal.browser.media.PetalMediaSessionService mediaService;
    public boolean isMediaBound = false;
    /**
     * True during the very first onResume() that immediately follows onCreate().
     * dispatchIntent() is called at the end of onCreate() (after all tabs are ready),
     * so we must skip the redundant call in onResume() to avoid a double-dispatch
     * or, worse, a no-op because setAction("") already cleared the intent action.
     */
    public boolean suppressResumeDispatch = false;
    public boolean isAppLockLocked = false;
    private boolean launchRippleTriggered = false;
    /** True while the launch ripple is waiting to be fired from the splash-screen exit animation. */
    private boolean splashRipplePending = false;
    /** True when the Compose launch splash (PetalSplashScreen) plays right after the system splash. */
    private boolean splashHandsOffToComposeSplash = false;
    /**
     * A widget action (ACTION_OPEN_SEARCH / _AI_SEARCH / _VOICE) waiting to run once the
     * window has genuine input focus. See {@link #runOrDeferPendingWidgetAction()}.
     */
    private Runnable pendingWidgetAction = null;
    public AlertDialog dialogOverview;
    private AlertDialog dialog_overflow;
    private AlertDialog dialogCustomSearches;
    private CardView appBar;
    private View contentView;
    // ---------------------------------------------------------------------
    // Predictive back (native window) for the primary browsing surface.
    // Ported from RvSystem-Monitor's aospSharedAxisPopExit transition
    // (ui/navigation/Transitions.kt): a plain full-width slide + scale-down
    // to 0.85, no fade, no preview underlay, no corner-radius clipping. This
    // keeps the native browsing surface's gesture feel identical to every
    // Compose screen in the app (Settings, Bookmarks, History, etc.), which
    // now use the same values via PetalScreenWrapper.
    // ---------------------------------------------------------------------
    private View predictiveBackRoot;
    private OnBackPressedCallback browserBackCallback;
    private boolean predictiveBackGestureActive = false;
    private float predictiveBackProgress = 0f;
    private com.petal.browser.motion.PetalWebPredictiveMotion webPredictiveMotion;
    private com.petal.browser.ui.components.PetalPredictiveBackPill predictiveBackPill;
    private com.petal.browser.browser.rules.PetalBackTarget currentWebBackTarget = com.petal.browser.browser.rules.PetalBackTarget.DELEGATE_TO_SYSTEM;
    // Which edge the in-flight gesture started from - must be remembered so the
    // cancel/commit settle animation keeps sliding the same direction the finger
    // was already moving in. Previously this was hardcoded to EDGE_LEFT in the
    // settle animator, which snapped the root view to the wrong side (a visible
    // direction-reversal glitch) whenever the user swiped from the right edge.
    private int predictiveBackSwipeEdge = BackEventCompat.EDGE_LEFT;
    private ValueAnimator predictiveBackSettleAnimator;
    // Mirrors aospSharedAxisPopExit's targetScale = 0.85f
    private static final float PB_MAX_SCALE_DELTA = 0.15f;
    // Mirrors RvSystem's M3EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private final PathInterpolator predictiveBackEasing = new PathInterpolator(0.2f, 0f, 0f, 1f);
    // Mirrors RvSystem's AOSP_TRANSITION_DURATION
    private static final int PB_TRANSITION_DURATION_MS = 350;
    /**
     * Set to true in handleOnBackStarted when isOverlayScreenShowing is true,
     * so handleOnBackPressed knows the gesture was started over an overlay screen.
     * The Compose PredictiveBackHandler inside the overlay will animate its own exit
     * and call onBack() which runs showAlbum() (clearing isOverlayScreenShowing).
     * handleOnBackPressed must NOT call performBackNavigation() in this case —
     * it must only call resetPredictiveBackVisuals() to clear the root-view transform.
     */
    private boolean predictiveBackStartedOnOverlay = false;
    // ---------------------------------------------------------------------
    // Live predictive-back underlay: re-reveal the REAL tab surface behind a dismissing
    // overlay instead of sliding a PixelCopy screenshot of it.
    // ---------------------------------------------------------------------
    private View liveUnderlayView;
        // The selected predictive back style (Petal / AOSP / MIUIX / Scale / Classic) publishes the
        // pose of the revealed layer: {scale, translationX, translationY, dimAlpha, blurDp}.
            // Depth blur + dim. Has no visible effect on a SurfaceView-backed
            // GeckoView; works when the surface is TextureView-backed (glass backend).
            // Committed back runs showAlbum(), which makes the surface visible and clears
            // isOverlayScreenShowing. Cancelled (or nested) back leaves the overlay up: hide again.
    /**
     * Custom splash exit for the cookie + pinwheel launch mark (Material 3 Expressive, Orion Store style).
     * <ul>
     *   <li>Launch splash ON: the mark eases up slightly while the whole splash dissolves, revealing
     *       PetalSplashScreen underneath, whose wordmark springs in as the cookie fades.</li>
     *   <li>Launch splash OFF: the mark opens up (scales to 1.6x on the M3 emphasized-accelerate curve)
     *       while the splash fades, revealing the home screen / page directly.</li>
     * </ul>
     * The launch ripple, when enabled, still radiates from the icon's centre in both cases.
     */
            // If anything in the custom exit animation throws, the splash view would otherwise stay on
            // top of the app forever and the window would look blank/black. Always drop it.
        // Android 16 can hand back a splash provider whose icon view has already
        // been detached during the exit callback. Treat it as unavailable and
        // let the ripple fall back to the window centre instead of crashing.
        // Safety net so the splash can never get stuck if the animation is cancelled.
    /** Restores input to the active browser surface after a decor-level transient UI closes. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Intent target = new Intent(this, MainActivity.class);
        Intent incoming = getIntent();
        if (incoming != null) {
            target.setAction(incoming.getAction());
            target.setData(incoming.getData());
            if (incoming.getExtras() != null) {
                target.putExtras(incoming.getExtras());
            }
            target.setFlags(incoming.getFlags());
        }
        startActivity(target);
        finish();
        super.onCreate(savedInstanceState);
    }

    public com.petal.browser.browser.PetalTabViewController getOrCreateTabViewController() {
        if (singleTabViewController == null) {
            singleTabViewController = new com.petal.browser.browser.PetalTabViewController(this);
        }
        return singleTabViewController;
    }

    public com.petal.browser.browser.AlbumController getCurrentAlbumController() {
        return currentAlbumController != null ? currentAlbumController : getOrCreateTabViewController();
    }

    public com.petal.browser.browser.PetalTabSurface getCurrentTabSurface() {
        return getOrCreateTabViewController();
    }

    public static Context getAppContext() {
        return com.petal.browser.PetalApplication.getAppContext();
    }

    public void observePetalTabSurface(com.petal.browser.browser.PetalTabViewController surface) { }
    public void reloadCurrentBrowserTab() { }
    public com.petal.browser.browser.PetalTabViewController getGeckoView() { return null; }
    public void setCurrentAlbumController(AlbumController controller) { }
    public static boolean isExternalOrWidgetLaunch(Intent intent) { return false; }
    public static boolean shouldSkipLaunchSplash(Intent intent) { return false; }
    public com.petal.browser.media.PetalMediaBridge getActiveMediaBridge() { return null; }
    public void onServiceConnected(ComponentName name, IBinder service) { }
    public void onPlay() { }
    @Override public void onPause() { }
    @Override public void onStop() { }
    public void onSeekTo(long positionMs) { }
    public void onSpeedToggle(float newSpeed) { }
    public void onMuteToggle() { }
    public void onSkip(int deltaSeconds) { }
    public void onServiceDisconnected(ComponentName name) { }
    public boolean canBrowserGoBack() { return false; }
    public boolean canNinjaGoBack() { return false; }
    public void handleBackPress() { }
    public boolean hasLiveUnderlay() { return false; }
    public void onGestureStart(int swipeEdge) { }
    public void onGestureProgress(float progress, int swipeEdge) { }
    public void onGestureEnd(boolean committed) { }
    public AlbumController nextAlbumController(boolean next) { return null; }
    public boolean onError(MediaPlayer mp, int what, int extra) { return false; }
    public void onCompletion(MediaPlayer mp) { }
    @Override public void attachBaseContext(Context newBase) { }
    public void restoreBrowserInputFocus() { }
    public void run() { }
    public void accept(String error) { }
    public void handleOnBackStarted(@NonNull androidx.activity.BackEventCompat backEvent) { }
    public void handleOnBackProgressed(@NonNull androidx.activity.BackEventCompat backEvent) { }
    public void handleOnBackPressed() { }
    public void handleOnBackCancelled() { }
    public void onChildViewAdded(View parent, View child) { }
    public void onChildViewRemoved(View parent, View child) { }
    public void onAppLaunchSplashFinished() { }
    public boolean isPageLoading() { return false; }
    public void maybePromptDefaultBrowser() { }
    @Override public void onStart() { }
    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) { }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { }
    @Override public void onNewIntent(Intent intent) { }
    @Override public void onResume() { }
    @Override public void onDestroy() { }
    @Override public void onTrimMemory(int level) { }
    public boolean isPetalHomeSurfaceShowing() { return false; }
    public void performBackNavigation() { }
    public void beginPredictiveBackGesture() { }
    public void applyPredictiveBackTransform(float progress, int swipeEdge) { }
    public void settlePredictiveBackGesture(boolean committed) { }
    public void onAnimationEnd(android.animation.Animator animation) { }
    public com.petal.browser.browser.rules.PetalBackTarget resolveCurrentBackTarget() { return null; }
    public void updateBackCallbackState() { }
    public void resetPredictiveBackVisuals() { }
    public void presentComposeScreen(View screen) { }
    public void presentComposeScreen(View screen, boolean animate) { }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return false; }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) { return false; }
    @Override public boolean onGenericMotionEvent(MotionEvent event) { return false; }
    @Override public void onUserLeaveHint() { }
    public void updatePipParams(boolean enableAutoEnter) { }
    public void triggerSystemPipMode() { }
    public void evaluateJavascriptInActiveTab(String script, android.webkit.ValueCallback<String> callback) { }
    @Override public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) { }
    public void onTabUrlStarted(com.petal.browser.browser.AlbumController webView, String url) { }
    public void clearContentFrameKeepingTabs() { }
    public void onGlobalLayout() { }
    @Override public synchronized void showAlbum(AlbumController controller) { }
    public synchronized void selectTab(AlbumController controller) { }
    public synchronized void closeTab(AlbumController controller) { }
    public synchronized void showAlbum(AlbumController controller, String overrideUrl) { }
    public void onSearch(String query) { }
    public void onOpenUrl(String u) { }
    public void onAddShortcut() { }
    public void onNewTab() { }
    public void onOpenBookmarks() { }
    public void onOpenHistory() { }
    public void onOpenDownloads() { }
    public void onOpenSettings() { }
    public void onOpenTabsOverview() { }
    public void onOpenAccountSync() { }
    public void updatePersistentBottomNav() { }
    public void onHomeClick() { }
    public void onNewTabClick() { }
    public void onTabsClick() { }
    public void onMenuClick() { }
    public void onSearchClick() { }
    public void onSwipeTabLeft() { }
    public void onSwipeTabRight() { }
    public boolean isAddressBarCollapsed() { return false; }
    public boolean isBarAnimationRunning() { return false; }
    public boolean areBarsAnimating() { return false; }
    public void applyImmersiveFullScreenMode() { }
    public void onTabSelect(AlbumController controller) { }
    public void onTabClose(AlbumController controller) { }
    public void onNewTab(boolean incognito) { }
    public AlbumController currentController() { return null; }
    @Override public void onConfigurationChanged(android.content.res.Configuration newConfig) { }
    public void applyAddressBarPosition() { }
    @Override public synchronized void removeAlbum(final AlbumController controller) { }
    public synchronized void removeAlbumSilently(final AlbumController controller) { }
    public synchronized void switchAdjacentTab(int direction) { }
    @Override public synchronized void updateProgress(int progress) { }
    @Override public void showFileChooser(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) { }
    public void launchSystemFileChooserFallback(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) { }
    @Override public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) { }
    @Override public void onHideCustomView() { }
    public void initOverview() { }
    public void onSwipeTop() { }
    public void onSwipeBottom() { }
    public void onSwipeRight() { }
    public void onSwipeLeft() { }
    public void initOmniBox() { }
    public void performSearchWithFixerPrompt(String query, boolean isIncognitoTab, java.util.function.Consumer<String> onReadyToNavigate) { }
    public void handleFinalSearch(String query) { }
    public boolean isActualWebPage(String url) { return false; }
    public boolean isCurrentTabHomeOrBlank() { return true; }
    public void updateAddressBar() { }
    public void setReaderModeEligible(com.petal.browser.browser.AlbumController controller, boolean eligible) { }
    public void setReaderModeEligibleForUrl(String url, boolean eligible) { }
    public void showAiResearchSheet() { }
    public void updateVideoDimensions(int width, int height) { }
    public void onScrollOffsetDelta(float deltaY) { }
    public void settlePillFraction(float target) { }
    public void animateAddressBarCollapse(boolean collapse) { }
    public void springTranslateY(View view, float endValue, float stiffness, float dampingRatio) { }
    public void springScale(View view, float endValue, float stiffness, float dampingRatio) { }
    public void springAlpha(View view, float endValue, float stiffness, float dampingRatio) { }
    public void updateOmniBox() { }
    public void updateFindInPageCompose() { }
    public void closeFindInPage() { }
    public void setupComposeViewOwners(View view) { }
    public void initPullToRefresh() { }
    public void onDownloadTogglePause(long downloadId) { }
    public void onDownloadCancel(long downloadId) { }
    public void onMediaTogglePlayPause() { }
    public void onMediaSkip(int seconds) { }
    public void onOpenDownloadedFile(String localUri, String fileName) { }
    public void onOpenBrowser() { }
    public void onOpenProfile() { }
    public void onOpenPasswords() { }
    public void onCopyText(String text, String label) { }
    public void hideRefreshAndProgressOverlays() { }
    public void onActivePageLoadFinished() { }
    public void resetRefreshState() { }
    public void initSearch() { }
    @Override public void showOverview() { }
    public void hideSearch() { }
    public void hideOverview() { }
    public void setSelectedTab() { }
    public void hideOverflow () { }
    public void showOverflowMenu(View anchorView) { }
    public void showQuickTools() { }
    public void showOmniboxPage(String initialQuery) { }
    public void showDownloads() { }
    public void showHistoryScreen() { }
    public void showDeleteScreen() { }
    public void showBookmarksPage() { }
    public void showBookmarksSheet() { }
    public void showBookmarks() { }
    public void showExtensionsScreen() { }
    public void showSafeLockerScreen() { }
    public void showSafeLocker() { }
    public void savePageOffline() { }
    public void showReaderMode() { }
    public void showSendToDevice() { }
    public void showAccountSyncScreen() { }
    public void captureBrowserMainPreview() { }
    public void showOverflow(Dialog dialog, View view, int hideMenu, String title, String url, final RecordListAdapter adapterRecord, List<Record> recordList, int location) { }
    public void showDialogFastToggle(String title, String url, FloatingActionButton floatingActionButton) { }
    public void setProfileIcon (FloatingActionButton floatingActionButton, String url) { }
    public void showDialogFilter() { }
    public void showDialogCustomSearches(String url) { }
    public void doubleTapsQuit() { }
    public void setCustomFullscreen(boolean fullscreen) { }
    public void copyLink(String url) { }
    public void shareLink(String title, String url) { }
    public void postLink(String data, Dialog dialogParent) { }
    public void searchOnSite() { }
    public void saveBookmark(String title, String url) { }
    public boolean toggleBookmark(String title, String url) { return false; }
    public void performGesture(String gesture, String url) { }
    public void closeTabConfirmation(final Runnable okAction) { }
    public void closeAllIncognitoTabs() { }
    public boolean isActionDispatchable(Intent intent) { return false; }
    public void dispatchIntent(Intent intent) { }
    public void runOrDeferPendingWidgetAction() { }
    public void consumePendingWidgetAction() { }
    @Override public void onWindowFocusChanged(boolean hasFocus) { }
    public void openNewTab(String title, final String url, final boolean foreground) { }
    public void openNewTab(String title, final String url, final boolean foreground, final boolean isIncognito) { }
    public void setWebView(String title, final String url, final boolean foreground) { }
    public void setWebView(String title, final String url, final boolean foreground, final boolean isIncognito) { }
    public void setWebView(String title, final String url, final boolean foreground, final boolean isIncognito, final boolean isPopup) { }
    public void openNewTab(String title, final String url, final boolean foreground, final boolean isIncognito, final boolean isPopup) { }
    public void saveOpenedTabs() { }
    public synchronized AlbumController restoreTab(String title, String url, int index, boolean isIncognito, String groupId, String groupTitle, String groupColorHex, boolean keepOverviewOpen) { return null; }
    public synchronized void addAlbum(String title, final String url, final boolean foreground) { }
    public synchronized void addAlbum(String title, final String url, final boolean foreground, final boolean isIncognito) { }
    public synchronized AlbumController addAlbumForPopup(String title, final boolean isIncognito) { return null; }
    public synchronized void adoptPreparedWindow( com.petal.browser.browser.PetalTabViewController source, mozilla.components.concept.engine.window.WindowRequest request) { }
    public synchronized void closeContentWindow( com.petal.browser.browser.PetalTabViewController source) { }
    public synchronized void adoptPopupGeckoSession(org.mozilla.geckoview.GeckoSession popupSession, String targetUrl, boolean isIncognito) { }
    public synchronized void adoptPopupGeckoSession(org.mozilla.geckoview.GeckoSession popupSession, String targetUrl, boolean isIncognito, boolean activate) { }
    public synchronized void addAlbumInGroup(String title, final String url, final boolean foreground, final String groupId, final String groupTitle) { }
    public void triggerRebirth(Context context) { }
    public String resolveCurrentTabUrl() { return ""; }
    public void installPwaShortcut() { }
    public void openApiIntegrationsHub() { }
    public void openFlagsHubScreen() { }
    public void openSettingsScreen() { }
    public void openSettingsScreen(com.petal.browser.compose.settings.SettingsCategory initialCategory) { }
    public static View getView() { return null; }
    public void createWebPrintJob(WebView webView) { }
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, android.os.CancellationSignal cancellationSignal, LayoutResultCallback callback, android.os.Bundle extras) { }
    public void onWrite(android.print.PageRange[] pages, android.os.ParcelFileDescriptor destination, android.os.CancellationSignal cancellationSignal, WriteResultCallback callback) { }
    @Override public void onLowMemory() { }
}