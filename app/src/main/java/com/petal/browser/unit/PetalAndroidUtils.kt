package com.petal.browser.unit

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.LocaleList
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.LocaleListCompat
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.browser.DataURIParser
import com.petal.browser.browser.StandardDomainWhitelist
import com.petal.browser.compose.downloads.PetalFetchDownloadBridge
import com.petal.browser.database.FaviconHelper
import com.petal.browser.download.PetalGeckoCookieBridge
import com.petal.browser.download.SafeDownloadValues
import com.petal.browser.ui.components.PetalAlertDialogBuilder
import com.petal.browser.ui.theme.PetalFontHelper
import com.petal.browser.ui.theme.PetalViewThemeBridge
import com.petal.browser.view.GridItem
import com.petal.browser.view.MenuItem
import com.petal.browser.view.PetalToast
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.function.BooleanSupplier

object PetalAndroidUtils {

    private const val TAG = "PetalAndroidUtils"
    private const val REQUEST_CODE_ASK_PERMISSIONS_1 = 1234
    private const val REQUEST_CODE_ASK_PERMISSIONS_2 = 12345
    private const val REQUEST_CODE_ASK_PERMISSIONS_3 = 123456

    private var sp: SharedPreferences? = null

    /** Tracks the single currently-visible Snackbar so we can dismiss it before showing another. */
    private var sActiveSnackbar: WeakReference<Snackbar>? = null

    private fun trackAndShow(snackbar: Snackbar) {
        val prev = sActiveSnackbar?.get()
        if (prev != null && prev.isShownOrQueued) {
            prev.dismiss()
        }
        sActiveSnackbar = WeakReference(snackbar)
        snackbar.show()
    }

    @JvmStatic
    fun grantPermissionsLoc(activity: Activity) {
        val hasAccessFineLocation = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (hasAccessFineLocation != PackageManager.PERMISSION_GRANTED) {
            val builder = PetalAlertDialogBuilder(activity)
            builder.setIcon(R.drawable.icon_alert)
            builder.setTitle(R.string.setting_title_location)
            builder.setMessage(R.string.app_permission)
            builder.setPositiveButton(R.string.app_ok) { _, _ ->
                activity.requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_CODE_ASK_PERMISSIONS_1
                )
            }
            builder.setNegativeButton(R.string.app_cancel) { dialog, _ -> dialog.cancel() }
            val dialog = builder.create()
            dialog.show()
            setupDialog(activity, dialog)
        }
    }

    @JvmStatic
    fun grantPermissionsCamera(activity: Activity) {
        val camera = activity.checkSelfPermission(Manifest.permission.CAMERA)
        if (camera != PackageManager.PERMISSION_GRANTED) {
            val builder = PetalAlertDialogBuilder(activity)
            builder.setIcon(R.drawable.icon_alert)
            builder.setTitle(R.string.setting_title_camera)
            builder.setMessage(R.string.app_permission)
            builder.setPositiveButton(R.string.app_ok) { _, _ ->
                activity.requestPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CODE_ASK_PERMISSIONS_2
                )
            }
            builder.setNegativeButton(R.string.app_cancel) { dialog, _ -> dialog.cancel() }
            val dialog = builder.create()
            dialog.show()
            setupDialog(activity, dialog)
        }
    }

    @JvmStatic
    fun grantPermissionsMic(activity: Activity) {
        val mic = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        if (mic != PackageManager.PERMISSION_GRANTED) {
            val builder = PetalAlertDialogBuilder(activity)
            builder.setIcon(R.drawable.icon_alert)
            builder.setTitle(R.string.setting_title_microphone)
            builder.setMessage(R.string.app_permission)
            builder.setPositiveButton(R.string.app_ok) { _, _ ->
                activity.requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_CODE_ASK_PERMISSIONS_3
                )
            }
            builder.setNegativeButton(R.string.app_cancel) { dialog, _ -> dialog.cancel() }
            val dialog = builder.create()
            dialog.show()
            setupDialog(activity, dialog)
        }
    }

    @JvmStatic
    fun saveAs(activity: Activity, url: String?, name: String?, dialogParent: Dialog) {
        if (PetalBackupManager.checkPermissionStorage(activity)) {
            try {
                val builder = PetalAlertDialogBuilder(activity)
                val dialogView = View.inflate(activity, R.layout.dialog_edit, null)
                val editBottomLayout = dialogView.findViewById<TextInputLayout>(R.id.editBottomLayout)
                val editTopLayout = dialogView.findViewById<TextInputLayout>(R.id.editTopLayout)
                editBottomLayout.hint = activity.getString(R.string.dialog_extension_hint)
                editTopLayout.hint = activity.getString(R.string.dialog_title_hint)
                val editTop = dialogView.findViewById<EditText>(R.id.editTop)
                val editBottom = dialogView.findViewById<EditText>(R.id.editBottom)

                val filename = name ?: resolveFileName(url, null, null)
                val lastDot = filename.lastIndexOf(".")
                val prefix = if (lastDot > 0) filename.substring(0, lastDot) else filename
                val extension = if (lastDot > 0) filename.substring(lastDot) else ""

                editTop.setText(prefix)
                editBottom.setText(extension)

                builder.setTitle(R.string.menu_save_as)
                builder.setIcon(R.drawable.icon_menu_save)
                builder.setView(dialogView)

                val dialog = builder.create()
                dialog.show()
                setupDialog(activity, dialog)

                val ibCancel = dialogView.findViewById<Button>(R.id.editCancel)
                ibCancel.setOnClickListener { dialog.cancel() }
                val ibOk = dialogView.findViewById<Button>(R.id.editOK)
                ibOk.setOnClickListener {
                    val title = editTop.text.toString().trim()
                    val extension1 = editBottom.text.toString().trim()
                    val finalFileName = title + extension1

                    if (title.isEmpty() || !extension1.startsWith(".")) {
                        PetalToast.show(activity, activity.getString(R.string.toast_input_empty))
                    } else {
                        try {
                            if (url != null && url.startsWith("data:")) {
                                val dataURIParser = DataURIParser(url)
                                val file = File(
                                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                    finalFileName
                                )
                                FileOutputStream(file).use { fos ->
                                    fos.write(dataURIParser.imagedata)
                                    fos.flush()
                                }
                                val text = activity.getString(R.string.app_done) + ". " + activity.getString(R.string.menu_download) + "?"
                                PetalToast.show(activity, text, PetalToast.LENGTH_LONG, activity.getString(R.string.app_ok)) {
                                    if (activity is MainActivity) {
                                        activity.showDownloads()
                                    }
                                }
                            } else if (url != null) {
                                val userAgent = WebSettings.getDefaultUserAgent(activity)
                                val cookie = PetalGeckoCookieBridge.getCookies(url, false)
                                val extraHeaders = HashMap<String, String>().apply {
                                    put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                                    put("Accept-Language", getAcceptLanguage(activity))
                                    put("Connection", "keep-alive")
                                    put("Cache-Control", "no-transform")
                                    put("Referer", url)
                                }
                                PetalFetchDownloadBridge.enqueueMediaDownload(
                                    activity, url, finalFileName, null, userAgent, cookie, extraHeaders, null, null
                                )
                            }
                        } catch (e: Exception) {
                            println("Error Downloading File: $e")
                            PetalToast.show(
                                activity,
                                activity.getString(R.string.app_error) + e.toString().substring(e.toString().indexOf(":")),
                                Toast.LENGTH_LONG
                            )
                            Log.i(TAG, "shouldOverrideUrlLoading Exception:$e")
                        }
                        try {
                            dialog.cancel()
                        } catch (e: Exception) {
                            Log.i("Petal", "SaveAs:$e")
                        }
                        dialogParent.cancel()
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "SaveAs:$e")
            }
        } else {
            PetalBackupManager.requestPermission(activity)
        }
    }

    @JvmStatic
    fun createShortcut(context: Context, title: String, url: String) {
        val faviconHelper = FaviconHelper(context)
        val favicon = faviconHelper.getFavicon(url)
        val icon: Icon = if (favicon != null && !favicon.isRecycled) {
            Icon.createWithBitmap(favicon)
        } else {
            var appIconResId = context.applicationInfo.icon
            if (appIconResId == 0) {
                appIconResId = android.R.drawable.sym_def_app_icon
            }
            Icon.createWithResource(context, appIconResId)
        }

        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
            val pinShortcutInfo = ShortcutInfo.Builder(context, url)
                .setShortLabel(title)
                .setLongLabel(title)
                .setIcon(icon)
                .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)))
                .build()
            shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        } else {
            println("failed_to_add")
        }
    }

    @JvmStatic
    fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val net = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(net) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun resolveFileName(url: String?, contentDisposition: String?, mimeType: String?): String {
        if (url.isNullOrBlank()) return "download"
        return SafeDownloadValues.fileName(url, contentDisposition, mimeType)
    }

    @JvmStatic
    fun domain(url: String?): String {
        if (url == null) return ""
        return try {
            Uri.parse(url).host?.replace("www.", "")?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @JvmStatic
    fun initTheme(context: Activity) {
        try {
            sp = PreferenceManager.getDefaultSharedPreferences(context)
            context.setTheme(R.style.AppTheme)
            val bridged = PetalViewThemeBridge.apply(context)
            val isDynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            if (!bridged && (sp?.getBoolean("useDynamicColor", isDynamicSupported) == true) && isDynamicSupported &&
                !PetalViewThemeBridge.hasScheme(context)
            ) {
                DynamicColors.applyToActivityIfAvailable(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun addFilterItems(activity: Activity, gridList: MutableList<GridItem>) {
        val currentSp = sp ?: PreferenceManager.getDefaultSharedPreferences(activity)
        val item01 = GridItem(currentSp.getString("icon_01", activity.resources.getString(R.string.color_red)) ?: "", 11)
        val item02 = GridItem(currentSp.getString("icon_02", activity.resources.getString(R.string.color_pink)) ?: "", 10)
        val item03 = GridItem(currentSp.getString("icon_03", activity.resources.getString(R.string.color_purple)) ?: "", 9)
        val item04 = GridItem(currentSp.getString("icon_04", activity.resources.getString(R.string.color_blue)) ?: "", 8)
        val item05 = GridItem(currentSp.getString("icon_05", activity.resources.getString(R.string.color_teal)) ?: "", 7)
        val item06 = GridItem(currentSp.getString("icon_06", activity.resources.getString(R.string.color_green)) ?: "", 6)
        val item07 = GridItem(currentSp.getString("icon_07", activity.resources.getString(R.string.color_lime)) ?: "", 5)
        val item08 = GridItem(currentSp.getString("icon_08", activity.resources.getString(R.string.color_yellow)) ?: "", 4)
        val item09 = GridItem(currentSp.getString("icon_09", activity.resources.getString(R.string.color_orange)) ?: "", 3)
        val item10 = GridItem(currentSp.getString("icon_10", activity.resources.getString(R.string.color_brown)) ?: "", 2)
        val item11 = GridItem(currentSp.getString("icon_11", activity.resources.getString(R.string.color_grey)) ?: "", 1)
        val item12 = GridItem(currentSp.getString("icon_12", activity.resources.getString(R.string.setting_theme_system)) ?: "", 0)

        if (currentSp.getBoolean("filter_01", true)) gridList.add(item01)
        if (currentSp.getBoolean("filter_02", true)) gridList.add(item02)
        if (currentSp.getBoolean("filter_03", true)) gridList.add(item03)
        if (currentSp.getBoolean("filter_04", true)) gridList.add(item04)
        if (currentSp.getBoolean("filter_05", true)) gridList.add(item05)
        if (currentSp.getBoolean("filter_06", true)) gridList.add(item06)
        if (currentSp.getBoolean("filter_07", true)) gridList.add(item07)
        if (currentSp.getBoolean("filter_08", true)) gridList.add(item08)
        if (currentSp.getBoolean("filter_09", true)) gridList.add(item09)
        if (currentSp.getBoolean("filter_10", true)) gridList.add(item10)
        if (currentSp.getBoolean("filter_11", true)) gridList.add(item11)
        if (currentSp.getBoolean("filter_12", true)) gridList.add(item12)
    }

    @JvmStatic
    fun setFilterIcons(context: Context, ib_icon: MaterialCardView, newIcon: Long) {
        val icon = (newIcon and 15L).toInt()
        when (icon) {
            11 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.red, null))
            10 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.pink, null))
            9 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.purple, null))
            8 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.blue, null))
            7 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.teal, null))
            6 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.green, null))
            5 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.lime, null))
            4 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.yellow, null))
            3 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.orange, null))
            2 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.brown, null))
            1 -> ib_icon.setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.grey, null))
            0 -> {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.colorSecondaryContainer, typedValue, true)
                ib_icon.setCardBackgroundColor(typedValue.data)
            }
        }
    }

    @JvmStatic
    fun saveDataURI(activity: Activity, dataUriParser: DataURIParser, dialogParent: Dialog) {
        val imagedata = dataUriParser.imagedata
        val filename = dataUriParser.filename

        val builder = PetalAlertDialogBuilder(activity)
        val dialogView = View.inflate(activity, R.layout.dialog_edit, null)
        val editBottomLayout = dialogView.findViewById<TextInputLayout>(R.id.editBottomLayout)
        val editTopLayout = dialogView.findViewById<TextInputLayout>(R.id.editTopLayout)
        editBottomLayout.hint = activity.getString(R.string.dialog_extension_hint)
        editTopLayout.hint = activity.getString(R.string.dialog_title_hint)
        val editTop = dialogView.findViewById<EditText>(R.id.editTop)
        val editBottom = dialogView.findViewById<EditText>(R.id.editBottom)
        val lastDot = filename?.lastIndexOf(".") ?: -1
        val prefix = if (filename != null && lastDot > 0) filename.substring(0, lastDot) else (filename ?: "download")
        val extension = if (filename != null && lastDot > 0) filename.substring(lastDot) else ""

        editTop.setText(prefix)
        editBottom.setText(extension)
        builder.setView(dialogView)
        builder.setTitle(R.string.menu_save_as)
        builder.setIcon(R.drawable.icon_menu_save)

        val dialog = builder.create()
        dialog.show()
        setupDialog(activity, dialog)

        val ibCancel = dialogView.findViewById<Button>(R.id.editCancel)
        ibCancel.setOnClickListener { dialog.cancel() }
        val ibOk = dialogView.findViewById<Button>(R.id.editOK)
        ibOk.setOnClickListener {
            val title = editTop.text.toString().trim()
            val extension1 = editBottom.text.toString().trim()
            val filename1 = title + extension1

            if (title.isEmpty() || !extension1.startsWith(".")) {
                PetalToast.show(activity, activity.getString(R.string.toast_input_empty))
            } else {
                if (PetalBackupManager.checkPermissionStorage(activity)) {
                    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename1)
                    try {
                        FileOutputStream(file).use { fos ->
                            fos.write(imagedata)
                        }
                    } catch (e: Exception) {
                        Log.i(TAG, "Error Downloading File:$e")
                    }
                } else {
                    PetalBackupManager.requestPermission(activity)
                }
                dialog.cancel()
                dialogParent.cancel()
            }
        }
    }

    @JvmStatic
    fun showSoftKeyboard(editText: EditText) {
        editText.requestFocus()
        Handler().postDelayed({
            editText.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0f, 0f, 0))
            editText.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0f, 0f, 0))
            editText.selectAll()
        }, 500)
    }

    @JvmStatic
    fun hideSoftKeyboard(context: Context?, view: View?) {
        if (context == null) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } else if (context is Activity) {
            val currentFocus = context.currentFocus
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            }
        }
    }

    @JvmStatic
    fun setupDialog(context: Context, dialog: Dialog?) {
        try {
            if (dialog == null) return
            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
            val primaryColor = typedValue.data

            val surfaceValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorSurfaceContainerHigh, surfaceValue, true)
            if (surfaceValue.data == 0) {
                context.theme.resolveAttribute(R.attr.colorSurface, surfaceValue, true)
            }
            val surfaceColor = surfaceValue.data

            val imageView = dialog.findViewById<ImageView>(android.R.id.icon)
            if (imageView != null && primaryColor != 0) {
                imageView.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)
            }

            dialog.window?.let { window ->
                val backgroundDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (surfaceColor != 0) surfaceColor else Color.parseColor("#1C1B1F"))
                    cornerRadius = convertDpToPixel(28f, context).toFloat()
                }
                window.setBackgroundDrawable(backgroundDrawable)
                window.setDimAmount(0.32f)
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.decorView.elevation = convertDpToPixel(12f, context).toFloat()
            }
        } catch (ignored: Exception) {}
    }

    @JvmStatic
    fun convertDpToPixel(dp: Float, context: Context): Int {
        return Math.round(dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT))
    }

    @JvmStatic
    fun setHighLightedText(context: Context, tv: TextView?, url: String?, textToHighlight: String) {
        if (tv == null) return
        val tvt = tv.text.toString().lowercase(Locale.ROOT)
        var ofe = tvt.indexOf(textToHighlight.lowercase(Locale.ROOT))
        val wordToSpan: Spannable = SpannableString(tv.text)
        val listStandard = StandardDomainWhitelist(context)
        var ofs = 0
        while (ofs < tvt.length && ofe != -1) {
            ofe = tvt.indexOf(textToHighlight, ofs)
            if (ofe == -1) {
                break
            } else {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.colorError, typedValue, true)
                val color = typedValue.data
                val typedValue2 = TypedValue()
                context.theme.resolveAttribute(R.attr.colorOnSurface, typedValue2, true)
                val color2 = typedValue2.data
                wordToSpan.setSpan(ForegroundColorSpan(color2), ofe, ofe + textToHighlight.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
                try {
                    if (url != null && listStandard.isWhite(url)) {
                        wordToSpan.setSpan(ForegroundColorSpan(color), ofe, ofe + textToHighlight.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "Error loading lists:$e")
                }
            }
            ofs = ofe + 1
        }
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.isSingleLine = true
    }

    @JvmStatic
    fun setHighLightedTextSearch(context: Context, tv: TextView?, textToHighlight: String) {
        if (tv == null) return
        val tvt = tv.text.toString().lowercase(Locale.ROOT)
        var ofe = tvt.indexOf(textToHighlight.lowercase(Locale.ROOT))
        val wordToSpan: Spannable = SpannableString(tv.text)
        var ofs = 0
        while (ofs < tvt.length && ofe != -1) {
            ofe = tvt.indexOf(textToHighlight, ofs)
            if (ofe == -1) {
                break
            } else {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.colorError, typedValue, true)
                val color = typedValue.data
                wordToSpan.setSpan(ForegroundColorSpan(color), ofe, ofe + textToHighlight.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                tv.setText(wordToSpan, TextView.BufferType.SPANNABLE)
            }
            ofs = ofe + 1
        }
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.isSingleLine = true
    }

    @JvmStatic
    fun initAndLoadMenu(context: Context) {
        val sharedPreferences = context.getSharedPreferences("MenuPreferences", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("MenuList", null)
        val type = object : TypeToken<ArrayList<MenuItem>>() {}.type
        var list: MutableList<MenuItem>? = Gson().fromJson(json, type)
        if (list == null || list.isEmpty()) {
            list = ArrayList()
            list.add(MenuItem(context.getString(R.string.menu_openFav), R.drawable.icon_fav, true))
            list.add(MenuItem(context.getString(R.string.menu_fav), R.drawable.icon_fav_plus, true))
            list.add(MenuItem(context.getString(R.string.main_menu_new_tabOpen), R.drawable.icon_tab_plus, true))
            list.add(MenuItem(context.getString(R.string.main_menu_new_tab), R.drawable.icon_tab_background, true))
            list.add(MenuItem(context.getString(R.string.menu_closeTab), R.drawable.icon_tab_remove, true))
            list.add(MenuItem(context.getString(R.string.menu_other_searchSite), R.drawable.icon_search_site, true))
            list.add(MenuItem(context.getString(R.string.menu_reload), R.drawable.icon_refresh, true))
            list.add(MenuItem(context.getString(R.string.menu_share_link), R.drawable.icon_share, true))
            list.add(MenuItem(context.getString(R.string.menu_shareClipboard), R.drawable.icon_clipboard, true))
            list.add(MenuItem(context.getString(R.string.dialog_postOnWebsite), R.drawable.icon_post, true))
            list.add(MenuItem(context.getString(R.string.menu_save_bookmark), R.drawable.icon_bookmark, true))
            list.add(MenuItem(context.getString(R.string.menu_save_pdf), R.drawable.icon_file, true))
            list.add(MenuItem(context.getString(R.string.menu_save_as), R.drawable.icon_menu_save, true))
            list.add(MenuItem(context.getString(R.string.menu_shareOpenWith), R.drawable.icon_share_open_with, true))
            list.add(MenuItem(context.getString(R.string.menu_sc), R.drawable.icon_home, true))
            list.add(MenuItem(context.getString(R.string.menu_download), R.drawable.icon_download, true))
            list.add(MenuItem(context.getString(R.string.setting_label), R.drawable.icon_settings, true))
            list.add(MenuItem(context.getString(R.string.menu_restart), R.drawable.icon_restart, true))
            list.add(MenuItem(context.getString(R.string.menu_quit), R.drawable.icon_close, true))
            list.add(MenuItem(context.getString(R.string.menu_edit), R.drawable.icon_edit, true))
            list.add(MenuItem(context.getString(R.string.menu_delete), R.drawable.icon_delete, true))
            list.add(MenuItem(context.getString(R.string.menu_delete_entry), R.drawable.icon_delete, true))
            list.add(MenuItem(context.getString(R.string.app_help), R.drawable.icon_help, true))
            sharedPreferences.edit().putString("MenuList", Gson().toJson(list)).apply()
        }
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    fun showCustomSnackbarWithTwoActions(
        context: Context,
        parentView: View,
        anchorView: View?,
        title: String?,
        text: String?,
        link: String?,
        firstIconResId: Int,
        firstActionListener: BooleanSupplier?,
        secondIconResId: Int,
        secondActionListener: BooleanSupplier?
    ) {
        val snackbarText = SpannableStringBuilder()
        if (!title.isNullOrEmpty()) {
            snackbarText.append(title)
            snackbarText.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!text.isNullOrEmpty()) {
            if (snackbarText.isNotEmpty()) {
                snackbarText.append("\n\n")
            }
            snackbarText.append(text)
        }

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorPrimaryInverse, typedValue, true)
        val color = typedValue.data

        val surfaceValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorSurfaceContainerHigh, surfaceValue, true)
        val backgroundColor = surfaceValue.data

        val onSurfaceValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorOnSurface, onSurfaceValue, true)
        val contentColor = ColorStateList.valueOf(onSurfaceValue.data)

        val inflater = LayoutInflater.from(context)
        val customView = inflater.inflate(R.layout.custom_snackbar, null)

        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor)
            cornerRadius = 60f
        }
        customView.background = shape
        val textView = customView.findViewById<TextView>(R.id.custom_snackbar_text)

        if (!link.isNullOrEmpty()) {
            if (snackbarText.isNotEmpty()) {
                snackbarText.append("\n\n")
            }
            snackbarText.append(link)
            val linkStart = snackbarText.length - link.length
            val linkEnd = snackbarText.length

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {}
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = color
                    ds.isUnderlineText = true
                }
            }
            if (!link.startsWith("blob:") && !link.startsWith("data:")) {
                snackbarText.setSpan(clickableSpan, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        val snackbar = Snackbar.make(parentView, "", Snackbar.LENGTH_LONG)
        if (anchorView != null) {
            snackbar.anchorView = anchorView
        }

        val layout = snackbar.view as ViewGroup
        layout.removeAllViews()
        layout.setPadding(0, 0, 0, 0)
        layout.setBackgroundColor(Color.TRANSPARENT)

        val scrollView = customView.findViewById<NestedScrollView>(R.id.snackbar_scrollview)

        if (textView != null) {
            textView.text = snackbarText
            textView.setTextColor(contentColor.defaultColor)
            textView.setTextIsSelectable(true)

            if (scrollView != null) {
                val screenHeight = textView.context.resources.displayMetrics.heightPixels
                val maxPx = (screenHeight * 2) / 3
                scrollView.post {
                    if (scrollView.height > maxPx) {
                        scrollView.layoutParams.height = maxPx
                        scrollView.requestLayout()
                    }
                }
            }

            if (!link.isNullOrEmpty() && !link.startsWith("data:")) {
                textView.setOnClickListener { v ->
                    PetalBrowserUtils.intentURL(v.context, Uri.parse(link))
                    snackbar.dismiss()
                }
            }
        }

        val firstButton = customView.findViewById<MaterialButton>(R.id.custom_btn_one)
        if (firstButton != null && firstIconResId != 0) {
            firstButton.setIconResource(firstIconResId)
            firstButton.iconTint = contentColor
            if (firstActionListener != null) {
                firstButton.setOnClickListener {
                    if (firstActionListener.asBoolean) {
                        snackbar.dismiss()
                    }
                }
            }
        }

        val secondButton = customView.findViewById<MaterialButton>(R.id.custom_btn_two)
        if (secondButton != null && secondIconResId != 0) {
            secondButton.setIconResource(secondIconResId)
            secondButton.iconTint = contentColor
            if (secondActionListener != null) {
                secondButton.setOnClickListener {
                    if (secondActionListener.asBoolean) {
                        snackbar.dismiss()
                    }
                }
            }
        }

        layout.addView(customView, 0)
        val rawParams = snackbar.view.layoutParams
        if (rawParams is ViewGroup.MarginLayoutParams) {
            rawParams.leftMargin = convertDpToPixel(16f, parentView.context)
            rawParams.rightMargin = convertDpToPixel(16f, parentView.context)
            rawParams.bottomMargin = convertDpToPixel(8f, parentView.context)
            snackbar.view.layoutParams = rawParams
        }
        snackbar.view.elevation = convertDpToPixel(8f, parentView.context).toFloat()
        trackAndShow(snackbar)
    }

    @JvmStatic
    fun makePetalSnackbar(parent: View, text: CharSequence, duration: Int): Snackbar {
        sActiveSnackbar?.get()?.let { prev ->
            if (prev.isShownOrQueued) prev.dismiss()
            sActiveSnackbar = null
        }
        val snackbar = Snackbar.make(parent, text, duration)
        makeSnackbarRound(snackbar)
        sActiveSnackbar = WeakReference(snackbar)
        return snackbar
    }

    @JvmStatic
    fun makePetalSnackbar(parent: View, textResId: Int, duration: Int): Snackbar {
        sActiveSnackbar?.get()?.let { prev ->
            if (prev.isShownOrQueued) prev.dismiss()
            sActiveSnackbar = null
        }
        val snackbar = Snackbar.make(parent, textResId, duration)
        makeSnackbarRound(snackbar)
        sActiveSnackbar = WeakReference(snackbar)
        return snackbar
    }

    @JvmStatic
    fun makeSnackbarRound(snackbar: Snackbar) {
        val snackbarView = snackbar.view
        val context = snackbarView.context

        val surfaceValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceInverse, surfaceValue, true)
        if (surfaceValue.data == 0) {
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHighest, surfaceValue, true)
        }
        val backgroundColor = if (surfaceValue.data != 0) surfaceValue.data else Color.parseColor("#2F2F33")

        val onSurfaceValue = TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceInverse, onSurfaceValue, true)
        if (onSurfaceValue.data == 0) {
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, onSurfaceValue, true)
        }
        val textColor = if (onSurfaceValue.data != 0) onSurfaceValue.data else Color.WHITE

        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        if (textView != null) {
            textView.maxLines = 4
            textView.ellipsize = TextUtils.TruncateAt.END
            textView.setTextColor(textColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            textView.typeface = PetalFontHelper.getAppTypeface(context)
            textView.gravity = Gravity.CENTER_VERTICAL
        }

        val actionView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
        if (actionView != null) {
            val primaryInverse = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryInverse, primaryInverse, true)
            if (primaryInverse.data == 0) {
                context.theme.resolveAttribute(android.R.attr.colorPrimary, primaryInverse, true)
            }
            val actionColor = if (primaryInverse.data != 0) primaryInverse.data else Color.parseColor("#FFB1C8")
            actionView.setTextColor(actionColor)
            actionView.typeface = Typeface.create(PetalFontHelper.getAppTypeface(context), Typeface.BOLD)
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, context.resources.displayMetrics)
            cornerRadius = radiusPx
            setColor(backgroundColor)
        }
        snackbarView.background = background
        snackbarView.elevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics)

        val horizontal = convertDpToPixel(20f, context)
        val bottom = convertDpToPixel(24f, context)
        val rawParams = snackbarView.layoutParams
        if (rawParams is ViewGroup.MarginLayoutParams) {
            rawParams.leftMargin = horizontal
            rawParams.rightMargin = horizontal
            rawParams.bottomMargin = bottom
            snackbarView.layoutParams = rawParams
        }
        snackbar.setTextMaxLines(4)
    }

    @JvmStatic
    fun copy(context: Context?, text: String?) {
        if (context == null || text == null) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmOverloads
    @JvmStatic
    fun applyBouncyTouchFeedback(view: View?, scaleDown: Float = 0.94f) {
        if (view == null) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scaleDown)
                        .scaleY(scaleDown)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(2.0f))
                        .start()
                }
            }
            false
        }
    }

    @JvmStatic
    fun applyLanguage(context: Context?): Context? {
        if (context == null) return null

        var locale: Locale? = null
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            locale = appLocales.get(0)
        }

        if (locale == null) {
            val currentSp = PreferenceManager.getDefaultSharedPreferences(context)
            val lang = currentSp.getString("sp_app_language", "system")
            if (lang != null && lang != "system") {
                locale = if ("hi-Latn".equals(lang, ignoreCase = true) || "hinglish".equals(lang, ignoreCase = true)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Locale.Builder().setLanguage("hi").setScript("Latn").build()
                    } else {
                        Locale.forLanguageTag("hi-Latn")
                    }
                } else {
                    Locale.forLanguageTag(lang)
                }
            }
        }

        if (locale != null) {
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale, Locale.ENGLISH))
            } else {
                config.setLocale(locale)
            }
            config.setLayoutDirection(locale)
            return context.createConfigurationContext(config)
        }
        return context
    }

    @JvmStatic
    fun setAppLanguage(context: Context?, langTag: String) {
        if (context == null) return
        val currentSp = PreferenceManager.getDefaultSharedPreferences(context)
        currentSp.edit().putString("sp_app_language", langTag).apply()

        val localeList = if ("system" == langTag) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)

        val targetLocale: Locale = if ("system" == langTag) {
            Locale.getDefault()
        } else if ("hi-Latn".equals(langTag, ignoreCase = true) || "hinglish".equals(langTag, ignoreCase = true)) {
            val loc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Locale.Builder().setLanguage("hi").setScript("Latn").build()
            } else {
                Locale.forLanguageTag("hi-Latn")
            }
            Locale.setDefault(loc)
            loc
        } else {
            val loc = Locale.forLanguageTag(langTag)
            Locale.setDefault(loc)
            loc
        }

        try {
            val res = context.resources
            val config = Configuration(res.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(targetLocale, Locale.ENGLISH))
            } else {
                config.setLocale(targetLocale)
            }
            config.setLayoutDirection(targetLocale)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)

            val appCtx = context.applicationContext
            if (appCtx != null && appCtx != context) {
                val appRes = appCtx.resources
                @Suppress("DEPRECATION")
                appRes.updateConfiguration(config, appRes.displayMetrics)
            }
        } catch (ignored: Exception) {}

        var activity: Activity? = null
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                activity = current
                break
            }
            current = current.baseContext
        }
        activity?.let { finalActivity ->
            finalActivity.window.decorView.post {
                try {
                    finalActivity.recreate()
                } catch (ignored: Exception) {}
            }
        }
    }

    @JvmStatic
    fun getAcceptLanguage(context: Context?): String {
        if (context == null) {
            return Locale.getDefault().toLanguageTag()
        }
        return try {
            val currentSp = PreferenceManager.getDefaultSharedPreferences(context)
            val matchWebsiteLanguage = currentSp.getBoolean("sp_match_website_language", true)
            if (!matchWebsiteLanguage) {
                return Locale.getDefault().toLanguageTag()
            }
            val appLang = currentSp.getString("sp_app_language", "system")
            if (appLang == null || "system".equals(appLang, ignoreCase = true)) {
                return Locale.getDefault().toLanguageTag() + ",en;q=0.8"
            }
            if ("hi-Latn".equals(appLang, ignoreCase = true) || "hinglish".equals(appLang, ignoreCase = true)) {
                return "hi-Latn,hi;q=0.9,en;q=0.8"
            }
            if ("zh-rTW".equals(appLang, ignoreCase = true) || "zh-TW".equals(appLang, ignoreCase = true)) {
                return "zh-TW,zh;q=0.9,en;q=0.8"
            }
            "$appLang,en;q=0.8"
        } catch (e: Exception) {
            Locale.getDefault().toLanguageTag()
        }
    }

    @JvmStatic
    fun getSafeString(sp: SharedPreferences?, key: String, defaultValue: String): String {
        if (sp == null) return defaultValue
        return try {
            sp.getString(key, defaultValue) ?: defaultValue
        } catch (e: ClassCastException) {
            try {
                val value = sp.all[key]
                value?.toString() ?: defaultValue
            } catch (ex: Exception) {
                defaultValue
            }
        }
    }

    @JvmStatic
    fun getSafeBoolean(sp: SharedPreferences?, key: String, defaultValue: Boolean): Boolean {
        if (sp == null) return defaultValue
        return try {
            sp.getBoolean(key, defaultValue)
        } catch (e: ClassCastException) {
            try {
                val value = sp.all[key]
                when (value) {
                    is Boolean -> value
                    is String -> value.toBoolean()
                    is Number -> value.toInt() != 0
                    else -> defaultValue
                }
            } catch (ex: Exception) {
                defaultValue
            }
        }
    }

    @JvmStatic
    fun getSafeInt(sp: SharedPreferences?, key: String, defaultValue: Int): Int {
        if (sp == null) return defaultValue
        return try {
            sp.getInt(key, defaultValue)
        } catch (e: ClassCastException) {
            try {
                val value = sp.all[key]
                when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull() ?: defaultValue
                    else -> defaultValue
                }
            } catch (ex: Exception) {
                defaultValue
            }
        }
    }

    @JvmStatic
    fun getSafeFloat(sp: SharedPreferences?, key: String, defaultValue: Float): Float {
        if (sp == null) return defaultValue
        return try {
            sp.getFloat(key, defaultValue)
        } catch (e: ClassCastException) {
            try {
                val value = sp.all[key]
                when (value) {
                    is Number -> value.toFloat()
                    is String -> value.toFloatOrNull() ?: defaultValue
                    else -> defaultValue
                }
            } catch (ex: Exception) {
                defaultValue
            }
        }
    }

    @JvmStatic
    fun getStatusBarHeight(context: Context?): Int {
        if (context == null) return 0
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        if (result <= 0) {
            result = convertDpToPixel(24f, context)
        }
        return result
    }
}
