/*
    This file is part of the browser WebApp.

    browser WebApp is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    browser WebApp is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with the browser webview app.

    If not, see <http://www.gnu.org/licenses/>.
 */

package com.petal.browser.unit;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.NestedScrollView;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import com.petal.browser.R;
import com.petal.browser.activity.BrowserActivity;
import com.petal.browser.activity.Settings_Menu;
import com.petal.browser.browser.BrowserController;
import com.petal.browser.browser.DataURIParser;
import com.petal.browser.browser.List_standard;
import com.petal.browser.database.FaviconHelper;
import com.petal.browser.view.GridItem;
import com.petal.browser.view.MenuItem;
import com.petal.browser.view.NinjaToast;
import com.petal.browser.view.NinjaWebView;

public class HelperUnit {

    private static final int REQUEST_CODE_ASK_PERMISSIONS_1 = 1234;
    private static final int REQUEST_CODE_ASK_PERMISSIONS_2 = 12345;
    private static final int REQUEST_CODE_ASK_PERMISSIONS_3 = 123456;
    private static SharedPreferences sp;

    public static void grantPermissionsLoc(final Activity activity) {
        int hasACCESS_FINE_LOCATION = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        if (hasACCESS_FINE_LOCATION != PackageManager.PERMISSION_GRANTED) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            builder.setIcon(R.drawable.icon_alert);
            builder.setTitle(R.string.setting_title_location);
            builder.setMessage(R.string.app_permission);
            builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_ASK_PERMISSIONS_1));
            builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
            AlertDialog dialog = builder.create();
            dialog.show();
            HelperUnit.setupDialog(activity, dialog);
        }
    }

    public static void grantPermissionsCamera(final Activity activity) {
        int camera = activity.checkSelfPermission(Manifest.permission.CAMERA);
        if (camera != PackageManager.PERMISSION_GRANTED) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            builder.setIcon(R.drawable.icon_alert);
            builder.setTitle(R.string.setting_title_camera);
            builder.setMessage(R.string.app_permission);
            builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_ASK_PERMISSIONS_2));
            builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
            AlertDialog dialog = builder.create();
            dialog.show();
            HelperUnit.setupDialog(activity, dialog);
        }
    }

    public static void grantPermissionsMic(final Activity activity) {
        int mic = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        if (mic != PackageManager.PERMISSION_GRANTED) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            builder.setIcon(R.drawable.icon_alert);
            builder.setTitle(R.string.setting_title_microphone);
            builder.setMessage(R.string.app_permission);
            builder.setPositiveButton(R.string.app_ok, (dialog, whichButton) -> activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_ASK_PERMISSIONS_3));
            builder.setNegativeButton(R.string.app_cancel, (dialog, whichButton) -> dialog.cancel());
            AlertDialog dialog = builder.create();
            dialog.show();
            HelperUnit.setupDialog(activity, dialog);
        }
    }

    public static void saveAs(final Activity activity, final String url, final String name, Dialog dialogParent) {
        if (BackupUnit.checkPermissionStorage(activity)) {

            try {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

                View dialogView = View.inflate(activity, R.layout.dialog_edit, null);
                TextInputLayout editBottomLayout = dialogView.findViewById(R.id.editBottomLayout);
                TextInputLayout editTopLayout = dialogView.findViewById(R.id.editTopLayout);
                editBottomLayout.setHint(activity.getString(R.string.dialog_extension_hint));
                editTopLayout.setHint(activity.getString(R.string.dialog_title_hint));
                EditText editTop = dialogView.findViewById(R.id.editTop);
                EditText editBottom = dialogView.findViewById(R.id.editBottom);

                String filename = name != null ? name : URLUtil.guessFileName(url, null, null);
                String extension = filename.substring(filename.lastIndexOf("."));
                String prefix = filename.substring(0, filename.lastIndexOf("."));

                editTop.setText(prefix);
                if (extension.length() <= 8) editBottom.setText(extension);

                builder.setTitle(R.string.menu_save_as);
                builder.setIcon(R.drawable.icon_menu_save);
                builder.setView(dialogView);

                AlertDialog dialog = builder.create();
                dialog.show();
                HelperUnit.setupDialog(activity, dialog);

                Button ib_cancel = dialogView.findViewById(R.id.editCancel);
                ib_cancel.setOnClickListener(view -> dialog.cancel());
                Button ib_ok = dialogView.findViewById(R.id.editOK);
                ib_ok.setOnClickListener(view12 -> {

                    String title = editTop.getText().toString().trim();
                    String extension1 = editBottom.getText().toString().trim();
                    String finalFileName = title + extension1;

                    if (title.isEmpty() || !extension1.startsWith(".")) {
                        NinjaToast.show(activity, activity.getString(R.string.toast_input_empty));
                    } else {
                        try {
                            if (url.startsWith("data:")) {
                                DataURIParser dataURIParser = new DataURIParser(url);
                                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), finalFileName);
                                FileOutputStream fos = new FileOutputStream(file);
                                fos.write(dataURIParser.getImagedata());
                                fos.flush();
                                fos.close();
                                String text = activity.getString(R.string.app_done) + ". " + activity.getString(R.string.menu_download) +"?";
                                Snackbar snackbar = Snackbar.make(BrowserActivity.getView(), text, Snackbar.LENGTH_LONG);
                                snackbar.setAction(activity.getString(R.string.app_ok), v -> {
                                    if (activity instanceof com.petal.browser.activity.BrowserActivity) {
                                        ((com.petal.browser.activity.BrowserActivity) activity).showDownloads();
                                    }
                                });
                                snackbar.show();
                            } else {
                                String userAgent = WebSettings.getDefaultUserAgent(activity);
                                CookieManager cookieManager = CookieManager.getInstance();
                                String cookie = cookieManager.getCookie(url);
                                java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
                                extraHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
                                extraHeaders.put("Accept-Language", Locale.getDefault().toLanguageTag());
                                extraHeaders.put("Accept-Encoding", "gzip, deflate, br, identity");
                                extraHeaders.put("Connection", "keep-alive");
                                extraHeaders.put("Cache-Control", "no-transform");
                                extraHeaders.put("Referer", url);

                                com.petal.browser.download.PetalDownloadEngine.getInstance(activity).enqueueDownload(
                                        activity, url, finalFileName, userAgent, cookie, extraHeaders,
                                        (fetchId, resolvedName) -> com.petal.browser.compose.downloads.PetalLiveAlertManager.trackDownload(
                                                activity, fetchId.longValue(), resolvedName)
                                );
                            }
                        } catch (Exception e) {
                            System.out.println("Error Downloading File: " + e);
                            Toast.makeText(activity, activity.getString(R.string.app_error) + e.toString().substring(e.toString().indexOf(":")), Toast.LENGTH_LONG).show();
                            Log.i(TAG, "shouldOverrideUrlLoading Exception:" + e);
                        }
                        try {
                            dialog.cancel();
                        } catch (Exception e) {
                            Log.i("Petal", "SaveAs:" + e);
                        }
                        dialogParent.cancel();
                    }
                });
            } catch (Exception e) {
                Log.i(TAG, "SaveAs:" + e);
            }
        } else BackupUnit.requestPermission(activity);
    }

    public static void createShortcut(Context context, String title, String url) {
        Icon icon;
        FaviconHelper faviconHelper = new FaviconHelper(context);
        Bitmap favicon = faviconHelper.getFavicon(url);
        if (favicon != null && !favicon.isRecycled()) {
            icon = Icon.createWithBitmap(favicon);
        } else {
            int appIconResId = context.getApplicationInfo().icon;
            if (appIconResId == 0) {
                appIconResId = android.R.drawable.sym_def_app_icon;
            }
            icon = Icon.createWithResource(context, appIconResId);
        }

        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        assert shortcutManager != null;
        if (shortcutManager.isRequestPinShortcutSupported()) {
            ShortcutInfo pinShortcutInfo =
                    new ShortcutInfo.Builder(context, url)
                            .setShortLabel(title)
                            .setLongLabel(title)
                            .setIcon(icon)
                            .setIntent(new Intent(context, BrowserActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)))
                            .build();
            shortcutManager.requestPinShortcut(pinShortcutInfo, null);
        } else System.out.println("failed_to_add");
    }

    public static String domain(String url) {
        if (url == null) {
            return "";
        } else {
            try {
                return Objects.requireNonNull(Uri.parse(url).getHost()).replace("www.", "").trim();
            } catch (Exception e) {
                return "";
            }
        }
    }
    public static void initTheme(Activity context) {
        try {
            sp = PreferenceManager.getDefaultSharedPreferences(context);
            context.setTheme(R.style.AppTheme);
            boolean isDynamicSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S;
            if (sp.getBoolean("useDynamicColor", isDynamicSupported) && isDynamicSupported) {
                com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(context);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addFilterItems(Activity activity, List<GridItem> gridList) {
        GridItem item_01 = new GridItem(sp.getString("icon_01", activity.getResources().getString(R.string.color_red)), 11);
        GridItem item_02 = new GridItem(sp.getString("icon_02", activity.getResources().getString(R.string.color_pink)), 10);
        GridItem item_03 = new GridItem(sp.getString("icon_03", activity.getResources().getString(R.string.color_purple)), 9);
        GridItem item_04 = new GridItem(sp.getString("icon_04", activity.getResources().getString(R.string.color_blue)), 8);
        GridItem item_05 = new GridItem(sp.getString("icon_05", activity.getResources().getString(R.string.color_teal)), 7);
        GridItem item_06 = new GridItem(sp.getString("icon_06", activity.getResources().getString(R.string.color_green)), 6);
        GridItem item_07 = new GridItem(sp.getString("icon_07", activity.getResources().getString(R.string.color_lime)), 5);
        GridItem item_08 = new GridItem(sp.getString("icon_08", activity.getResources().getString(R.string.color_yellow)), 4);
        GridItem item_09 = new GridItem(sp.getString("icon_09", activity.getResources().getString(R.string.color_orange)), 3);
        GridItem item_10 = new GridItem(sp.getString("icon_10", activity.getResources().getString(R.string.color_brown)), 2);
        GridItem item_11 = new GridItem(sp.getString("icon_11", activity.getResources().getString(R.string.color_grey)), 1);
        GridItem item_12 = new GridItem(sp.getString("icon_12", activity.getResources().getString(R.string.setting_theme_system)), 0);

        if (sp.getBoolean("filter_01", true)) gridList.add(gridList.size(), item_01);
        if (sp.getBoolean("filter_02", true)) gridList.add(gridList.size(), item_02);
        if (sp.getBoolean("filter_03", true)) gridList.add(gridList.size(), item_03);
        if (sp.getBoolean("filter_04", true)) gridList.add(gridList.size(), item_04);
        if (sp.getBoolean("filter_05", true)) gridList.add(gridList.size(), item_05);
        if (sp.getBoolean("filter_06", true)) gridList.add(gridList.size(), item_06);
        if (sp.getBoolean("filter_07", true)) gridList.add(gridList.size(), item_07);
        if (sp.getBoolean("filter_08", true)) gridList.add(gridList.size(), item_08);
        if (sp.getBoolean("filter_09", true)) gridList.add(gridList.size(), item_09);
        if (sp.getBoolean("filter_10", true)) gridList.add(gridList.size(), item_10);
        if (sp.getBoolean("filter_11", true)) gridList.add(gridList.size(), item_11);
        if (sp.getBoolean("filter_12", true)) gridList.add(gridList.size(), item_12);
    }

    public static void setFilterIcons(Context context, MaterialCardView ib_icon, long newIcon) {
        newIcon = newIcon & 15;
        if (newIcon == 11) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.red, null));
        else if (newIcon == 10) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.pink, null));
        else if (newIcon == 9) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.purple, null));
        else if (newIcon == 8) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.blue, null));
        else if (newIcon == 7) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.teal, null));
        else if (newIcon == 6) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.green, null));
        else if (newIcon == 5) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.lime, null));
        else if (newIcon == 4) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.yellow, null));
        else if (newIcon == 3) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.orange, null));
        else if (newIcon == 2) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.brown, null));
        else if (newIcon == 1) ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.getResources(), R.color.grey, null));
        else if (newIcon == 0) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.colorSecondaryContainer, typedValue, true);
            int color = typedValue.data;
            ib_icon.setCardBackgroundColor(color);
        }
    }

    public static void saveDataURI(Activity activity, DataURIParser dataUriParser, Dialog dialogParent) {

        byte[] imagedata = dataUriParser.getImagedata();
        String filename = dataUriParser.getFilename();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        View dialogView = View.inflate(activity, R.layout.dialog_edit, null);
        TextInputLayout editBottomLayout = dialogView.findViewById(R.id.editBottomLayout);
        TextInputLayout editTopLayout = dialogView.findViewById(R.id.editTopLayout);
        editBottomLayout.setHint(activity.getString(R.string.dialog_extension_hint));
        editTopLayout.setHint(activity.getString(R.string.dialog_title_hint));
        EditText editTop = dialogView.findViewById(R.id.editTop);
        EditText editBottom = dialogView.findViewById(R.id.editBottom);
        editTop.setText(filename.substring(0, filename.indexOf(".")));
        String extension = filename.substring(filename.lastIndexOf("."));
        if (extension.length() <= 8) {
            editBottom.setText(extension);
        }
        builder.setView(dialogView);
        builder.setTitle(R.string.menu_save_as);
        builder.setIcon(R.drawable.icon_menu_save);

        AlertDialog dialog = builder.create();
        dialog.show();
        HelperUnit.setupDialog(activity, dialog);

        Button ib_cancel = dialogView.findViewById(R.id.editCancel);
        ib_cancel.setOnClickListener(view -> dialog.cancel());
        Button ib_ok = dialogView.findViewById(R.id.editOK);
        ib_ok.setOnClickListener(view12 -> {

            String title = editTop.getText().toString().trim();
            String extension1 = editBottom.getText().toString().trim();
            String filename1 = title + extension1;

            if (title.isEmpty() || !extension1.startsWith(".")) {
                NinjaToast.show(activity, activity.getString(R.string.toast_input_empty));
            } else {
                if (BackupUnit.checkPermissionStorage(activity)) {
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename1);
                    try {
                        try(FileOutputStream fos = new FileOutputStream(file)) {
                            fos.write(imagedata);
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "Error Downloading File:" + e);
                    }
                } else {
                    BackupUnit.requestPermission(activity);
                }
                dialog.cancel();
                dialogParent.cancel();
            }
        });
    }
    public static void showSoftKeyboard(EditText editText) {
        editText.requestFocus();
        new Handler().postDelayed(() -> {
            editText.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0f, 0f, 0));
            editText.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0f, 0f, 0));
            editText.selectAll();
        }, 500);
    }

    public static void hideSoftKeyboard(Context context, View view) {
        if (context == null) return;
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            } else if (context instanceof android.app.Activity) {
                View currentFocus = ((android.app.Activity) context).getCurrentFocus();
                if (currentFocus != null) {
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
            }
        }
    }

    public static void setupDialog(Context context, Dialog dialog) {
        try {
            if (dialog == null) return;
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
            int primaryColor = typedValue.data;

            TypedValue surfaceValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHigh, surfaceValue, true);
            if (surfaceValue.data == 0) {
                context.getTheme().resolveAttribute(R.attr.colorSurface, surfaceValue, true);
            }
            int surfaceColor = surfaceValue.data;

            ImageView imageView = dialog.findViewById(android.R.id.icon);
            if (imageView != null && primaryColor != 0) {
                imageView.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN);
            }

            if (dialog.getWindow() != null) {
                android.graphics.drawable.GradientDrawable backgroundDrawable = new android.graphics.drawable.GradientDrawable();
                backgroundDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                backgroundDrawable.setColor(surfaceColor != 0 ? surfaceColor : android.graphics.Color.parseColor("#1C1B1F"));
                backgroundDrawable.setCornerRadius(HelperUnit.convertDpToPixel(28f, context));
                dialog.getWindow().setBackgroundDrawable(backgroundDrawable);
            }
        } catch (Exception ignored) {}
    }

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent px equivalent to dp depending on device density
     */
    public static int convertDpToPixel(float dp, Context context){
        return Math.round(dp * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static void setHighLightedText(Context context, TextView tv, String url, String textToHighlight) {
        String tvt = tv.getText().toString().toLowerCase();
        int ofe = tvt.indexOf(textToHighlight.toLowerCase());
        Spannable wordToSpan = new SpannableString(tv.getText());
        List_standard listStandard = new List_standard(context);
        for (int ofs = 0; ofs < tvt.length() && ofe != -1; ofs = ofe + 1) {
            ofe = tvt.indexOf(textToHighlight, ofs);
            if (ofe == -1)
                break;
            else {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(R.attr.colorError, typedValue, true);
                int color = typedValue.data;
                TypedValue typedValue2 = new TypedValue();
                context.getTheme().resolveAttribute(R.attr.colorOnSurface, typedValue2, true);
                int color2 = typedValue2.data;
                wordToSpan.setSpan(new ForegroundColorSpan(color2), ofe, ofe + textToHighlight.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(wordToSpan, TextView.BufferType.SPANNABLE);
                try {
                    if ( listStandard.isWhite(url)) {
                        wordToSpan.setSpan(new ForegroundColorSpan(color), ofe, ofe + textToHighlight.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE);
                    }
                } catch (Exception e) {
                    Log.i(TAG, "Error loading lists:" + e);
                }
            }
        }
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setSingleLine(true);
    }

    public static void setHighLightedTextSearch (Context context, TextView tv, String textToHighlight) {
        String tvt = tv.getText().toString().toLowerCase();
        int ofe = tvt.indexOf(textToHighlight.toLowerCase());
        Spannable wordToSpan = new SpannableString(tv.getText());
        for (int ofs = 0; ofs < tvt.length() && ofe != -1; ofs = ofe + 1) {
            ofe = tvt.indexOf(textToHighlight, ofs);
            if (ofe == -1)
                break;
            else {
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(R.attr.colorError, typedValue, true);
                int color = typedValue.data;
                // set color here
                wordToSpan.setSpan(new ForegroundColorSpan(color), ofe, ofe + textToHighlight.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(wordToSpan, TextView.BufferType.SPANNABLE);
            }
        }
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setSingleLine(true);
    }
    public static void initAndLoadMenu(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Settings_Menu.PREF_NAME, Context.MODE_PRIVATE);
        String json = sharedPreferences.getString(Settings_Menu.KEY_LIST, null);
        Type type = new TypeToken<ArrayList<MenuItem>>() {}.getType();
        List<MenuItem> list = new Gson().fromJson(json, type);
        if (list == null || list.isEmpty()) {
            list = new ArrayList<>();
            list.add(new MenuItem(context.getString(R.string.menu_openFav), R.drawable.icon_fav, true));
            list.add(new MenuItem(context.getString(R.string.menu_fav), R.drawable.icon_fav_plus, true));
            list.add(new MenuItem(context.getString(R.string.main_menu_new_tabOpen), R.drawable.icon_tab_plus, true));
            list.add(new MenuItem(context.getString(R.string.main_menu_new_tab), R.drawable.icon_tab_background, true));
            list.add(new MenuItem(context.getString(R.string.menu_closeTab), R.drawable.icon_tab_remove, true));
            list.add(new MenuItem(context.getString(R.string.menu_other_searchSite), R.drawable.icon_search_site, true));
            list.add(new MenuItem(context.getString(R.string.menu_reload), R.drawable.icon_refresh, true));
            list.add(new MenuItem(context.getString(R.string.menu_share_link), R.drawable.icon_share, true));
            list.add(new MenuItem(context.getString(R.string.menu_shareClipboard), R.drawable.icon_clipboard, true));
            list.add(new MenuItem(context.getString(R.string.dialog_postOnWebsite), R.drawable.icon_post, true));
            list.add(new MenuItem(context.getString(R.string.menu_save_bookmark), R.drawable.icon_bookmark, true));
            list.add(new MenuItem(context.getString(R.string.menu_save_pdf), R.drawable.icon_file, true));
            list.add(new MenuItem(context.getString(R.string.menu_save_as), R.drawable.icon_menu_save, true));
            list.add(new MenuItem(context.getString(R.string.menu_shareOpenWith), R.drawable.icon_share_open_with, true));
            list.add(new MenuItem(context.getString(R.string.menu_sc), R.drawable.icon_home, true));
            list.add(new MenuItem(context.getString(R.string.menu_download), R.drawable.icon_download, true));
            list.add(new MenuItem(context.getString(R.string.setting_label), R.drawable.icon_settings, true));
            list.add(new MenuItem(context.getString(R.string.menu_restart), R.drawable.icon_restart, true));
            list.add(new MenuItem(context.getString(R.string.menu_quit), R.drawable.icon_close, true));
            list.add(new MenuItem(context.getString(R.string.menu_edit), R.drawable.icon_edit, true));
            list.add(new MenuItem(context.getString(R.string.menu_delete), R.drawable.icon_delete, true));
            list.add(new MenuItem(context.getString(R.string.menu_delete_entry), R.drawable.icon_delete_alt, true));
            list.add(new MenuItem(context.getString(R.string.app_help), R.drawable.icon_help, true));
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Settings_Menu.KEY_LIST, new Gson().toJson(list));
            editor.apply();
        }
    }
    public static void showCustomSnackbarWithTwoActions(
            Context context,
            View parentView,
            View anchorView,
            String title,
            String text,
            String link,
            int firstIconResId,
            BooleanSupplier firstActionListener,
            int secondIconResId,
            BooleanSupplier secondActionListener) {

        SpannableStringBuilder snackbarText = new SpannableStringBuilder();
        if (title != null && !title.isEmpty()) {
            snackbarText.append(title);
            snackbarText.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        if (text != null && !text.isEmpty()) {
            if (snackbarText.length() > 0) {
                snackbarText.append("\n\n");
            }
            snackbarText.append(text);
        }

        // Pull colors straight from the active app theme (day/night, and any
        // custom/dynamic theme the user has picked) instead of hardcoding hex
        // values, so this box always matches the rest of the browser chrome.
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorPrimaryInverse, typedValue, true);
        int color = typedValue.data;

        TypedValue surfaceValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorSurfaceContainerHigh, surfaceValue, true);
        int backgroundColor = surfaceValue.data;

        TypedValue onSurfaceValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorOnSurface, onSurfaceValue, true);
        ColorStateList contentColor = ColorStateList.valueOf(onSurfaceValue.data);

        LayoutInflater inflater = LayoutInflater.from(context);
        @SuppressLint("InflateParams")
        View customView = inflater.inflate(R.layout.custom_snackbar, null);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(backgroundColor);
        shape.setCornerRadius(60f);
        customView.setBackground(shape);
        TextView textView = customView.findViewById(R.id.custom_snackbar_text);

        if (link != null && !link.isEmpty()) {
            if (snackbarText.length() > 0) {
                snackbarText.append("\n\n");
            }
            snackbarText.append(link);
            int linkStart = snackbarText.length() - link.length();
            int linkEnd = snackbarText.length();

            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {}

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(color);
                    ds.setUnderlineText(true);
                }
            };
            if (!link.startsWith("blob:") && !link.startsWith("data:")) {
                snackbarText.setSpan(clickableSpan, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        Snackbar snackbar = Snackbar.make(parentView, "", Snackbar.LENGTH_LONG);
        if (anchorView != null) {snackbar.setAnchorView(anchorView);}

        ViewGroup layout = (ViewGroup) snackbar.getView();
        layout.removeAllViews();
        layout.setPadding(0, 0, 0, 0);
        layout.setBackgroundColor(Color.TRANSPARENT);

        NestedScrollView scrollView = customView.findViewById(R.id.snackbar_scrollview);

        if (textView != null) {
            textView.setText(snackbarText);
            textView.setTextColor(contentColor.getDefaultColor());
            textView.setTextIsSelectable(true);

            if (scrollView != null) {
                int screenHeight = textView.getContext().getResources().getDisplayMetrics().heightPixels;
                int maxPx = (screenHeight * 2) / 3;
                scrollView.post(() -> {
                    if (scrollView.getHeight() > maxPx) {
                        scrollView.getLayoutParams().height = maxPx;
                        scrollView.requestLayout();
                    }
                });
            }

            if (link != null && !link.isEmpty() && !link.startsWith("data:")) {
                textView.setOnClickListener(v -> {
                    BrowserController browserController = NinjaWebView.getBrowserController();
                    browserController.hideOverflow();
                    browserController.showOverflow(null, textView, 1, title, link, null, null, 0);
                    snackbar.dismiss();
                });
            }
        }

        MaterialButton firstButton = customView.findViewById(R.id.custom_btn_one);
        if (firstButton != null && firstIconResId != 0) {
            firstButton.setIconResource(firstIconResId);
            firstButton.setIconTint(contentColor);
            if (firstActionListener != null) {
                firstButton.setOnClickListener(v -> {
                    if (firstActionListener.getAsBoolean()) {
                        snackbar.dismiss();
                    }
                });
            }
        }

        MaterialButton secondButton = customView.findViewById(R.id.custom_btn_two);
        if (secondButton != null && secondIconResId != 0) {
            secondButton.setIconResource(secondIconResId);
            secondButton.setIconTint(contentColor);
            if (secondActionListener != null) {
                secondButton.setOnClickListener(v -> {
                    if (secondActionListener.getAsBoolean()) {
                        snackbar.dismiss();
                    }
                });
            }
        }

        layout.addView(customView, 0);
        snackbar.show();
    }
    public static void makeSnackbarRound (Snackbar snackbar) {
        View snackbarView = snackbar.getView();
        Context context = snackbarView.getContext();

        TypedValue surfaceValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, surfaceValue, true);
        if (surfaceValue.data == 0) {
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, surfaceValue, true);
        }
        int backgroundColor = surfaceValue.data != 0 ? surfaceValue.data : Color.parseColor("#2B2D30");

        TypedValue onSurfaceValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, onSurfaceValue, true);
        int textColor = onSurfaceValue.data != 0 ? onSurfaceValue.data : Color.WHITE;

        TextView textView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setMaxLines(30);
            textView.setTextColor(textColor);
            textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        TextView actionView = snackbarView.findViewById(com.google.android.material.R.id.snackbar_action);
        if (actionView != null) {
            TypedValue primaryValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.colorPrimary, primaryValue, true);
            if (primaryValue.data == 0) {
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondary, primaryValue, true);
            }
            if (primaryValue.data != 0) {
                actionView.setTextColor(primaryValue.data);
                actionView.setTypeface(Typeface.DEFAULT_BOLD);
            }
        }

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        float radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, context.getResources().getDisplayMetrics());
        background.setCornerRadius(radiusPx);
        background.setColor(backgroundColor);
        snackbarView.setBackground(background);
        snackbarView.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, context.getResources().getDisplayMetrics()));
        snackbar.setTextMaxLines(100);
    }

    public static void copy(Context context, String text) {
        if (context == null || text == null) return;
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("text", text);
                clipboard.setPrimaryClip(clip);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Applies bouncy scale-down and spring-back touch feedback to any Android View.
     */
    public static void applyBouncyTouchFeedback(android.view.View view, float scaleDown) {
        if (view == null) return;
        view.setOnTouchListener(new android.view.View.OnTouchListener() {
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate()
                                .scaleX(scaleDown)
                                .scaleY(scaleDown)
                                .setDuration(100)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(200)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f))
                                .start();
                        break;
                }
                return false;
            }
        });
    }

    public static void applyBouncyTouchFeedback(android.view.View view) {
        applyBouncyTouchFeedback(view, 0.94f);
    }
}