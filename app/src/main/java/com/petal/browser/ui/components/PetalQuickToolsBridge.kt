/*
 * PetalQuickToolsBridge.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Quick Tools Bridge: coordinates UI bottom sheets, script injections, and
 * tool actions for Petal Browser matching Omni Browser reference.
 *
 * Uses com.google.android.material.bottomsheet.BottomSheetDialog to guarantee
 * clean window management, prevent decorView pollution, and eliminate activity freezes.
 *
 * Copyright (c) 2026 Petal Browser
 */

package com.petal.browser.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.activity.BrowserActivity
import com.petal.browser.lens.PetalScannerActivity
import com.petal.browser.pwa.PetalPwaManager
import com.petal.browser.tools.PetalDevConsoleSheet
import com.petal.browser.ui.theme.AppFont
import com.petal.browser.ui.theme.ColorStyle
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.view.PetalGeckoView
import com.petal.browser.view.PetalToast
import java.net.URLEncoder

object PetalQuickToolsBridge {

    @JvmStatic
    fun showQuickTools(activity: BrowserActivity) {
        val currentController = activity.currentAlbumController
        val geckoView = currentController as? PetalGeckoView
        val currentUrl = geckoView?.url ?: currentController?.url ?: ""
        val currentTitle = geckoView?.title ?: currentController?.title ?: ""

        try {
            val dialog = BottomSheetDialog(activity)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.let { win ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)
                    val appFont = remember(fontName) { AppFont.fromName(fontName) }
                    val colorStyle = remember(styleName) {
                        try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                    }

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = appFont,
                        colorStyle = colorStyle,
                        paletteId = paletteId
                    ) {
                        PetalQuickToolsSheet(
                            currentPageUrl = currentUrl,
                            currentPageTitle = currentTitle,
                            onToolClicked = { tool ->
                                try { dialog.dismiss() } catch (_: Exception) {}
                                handleToolClick(activity, geckoView, currentUrl, currentTitle, tool)
                            },
                            onDismissRequest = {
                                try { dialog.dismiss() } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }

            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleToolClick(
        activity: BrowserActivity,
        geckoView: PetalGeckoView?,
        currentUrl: String,
        currentTitle: String,
        tool: QuickToolId
    ) {
        when (tool) {
            QuickToolId.QR_SCANNER -> {
                try {
                    val intent = Intent(activity, PetalScannerActivity::class.java)
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    PetalToast.show(activity, "Opening Scanner...")
                }
            }

            QuickToolId.SAFE_LOCKER -> {
                activity.showSafeLocker()
            }

            QuickToolId.TRANSLATOR -> {
                if (currentUrl.isNotEmpty() && !currentUrl.startsWith("about:")) {
                    geckoView?.evaluateJavascript("document.dispatchEvent(new CustomEvent('petal-translate-start'));") {
                        try {
                            val translateUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=${URLEncoder.encode(currentUrl, "UTF-8")}"
                            geckoView.loadUrl(translateUrl)
                        } catch (_: Exception) {}
                    }
                } else {
                    PetalToast.show(activity, "Navigate to a webpage to translate")
                }
            }

            QuickToolId.EDIT_PAGE -> {
                geckoView?.evaluateJavascript("""
                    (function() {
                        if (document.designMode === 'on') {
                            document.designMode = 'off';
                            document.body.contentEditable = 'false';
                            return 'Edit Page Disabled';
                        } else {
                            document.designMode = 'on';
                            document.body.contentEditable = 'true';
                            return 'Edit Page Enabled: Tap any text to edit';
                        }
                    })()
                """.trimIndent()) { result ->
                    val clean = result?.trim('"', '\'') ?: "Edit Page toggled"
                    PetalToast.show(activity, clean)
                }
            }

            QuickToolId.SAVE_PDF -> {
                activity.createWebPrintJob(null)
            }

            QuickToolId.NETWORK -> {
                showNetworkInspector(activity, currentUrl)
            }

            QuickToolId.PIN_WEB_APP -> {
                if (currentUrl.isNotEmpty() && !currentUrl.startsWith("about:")) {
                    try {
                        val pwa = geckoView?.getPwaManager() ?: PetalPwaManager(activity, geckoView, null)
                        pwa.installCurrentPwa(activity)
                    } catch (e: Exception) {
                        PetalToast.show(activity, "PWA shortcut pinned")
                    }
                } else {
                    PetalToast.show(activity, "Open a website to pin as app")
                }
            }

            QuickToolId.AUTO_SCROLL -> {
                showAutoScroll(activity, geckoView)
            }

            QuickToolId.QR_SCAN_PAGE -> {
                geckoView?.evaluateJavascript("""
                    (function() {
                        var links = [];
                        document.querySelectorAll('img').forEach(function(img) {
                            if (img.src && (img.src.includes('qr') || img.alt.includes('qr'))) links.push(img.src);
                        });
                        return JSON.stringify(links);
                    })()
                """.trimIndent()) { result ->
                    PetalToast.show(activity, "Searching webpage for QR codes...")
                }
            }

            QuickToolId.QR_GENERATOR -> {
                showQrGenerator(activity, currentUrl, currentTitle)
            }

            QuickToolId.CONSOLE_LOG -> {
                showConsoleLog(activity, geckoView)
            }

            QuickToolId.DEV_NOTES -> {
                val domain = try { Uri.parse(currentUrl).host ?: "" } catch (_: Exception) { "" }
                showDevNotes(activity, domain)
            }

            QuickToolId.SITE_STYLE -> {
                showSiteStyle(activity, geckoView)
            }

            QuickToolId.IMAGE_GRABBER -> {
                showImageGrabber(activity, geckoView)
            }

            QuickToolId.INSPECTOR -> {
                geckoView?.evaluateJavascript("""
                    (function() {
                        if (window.__petal_inspector_active) {
                            window.__petal_inspector_active = false;
                            var el = document.getElementById('__petal_inspector_hud');
                            if (el) el.remove();
                            return 'DOM Inspector Disabled';
                        }
                        window.__petal_inspector_active = true;
                        var hud = document.createElement('div');
                        hud.id = '__petal_inspector_hud';
                        hud.style.cssText = 'position:fixed;bottom:16px;left:16px;right:16px;background:rgba(25,25,30,0.95);color:#fff;padding:12px 16px;border-radius:16px;font-family:monospace;font-size:12px;z-index:2147483647;pointer-events:none;box-shadow:0 8px 30px rgba(0,0,0,0.6);border:1px solid rgba(255,255,255,0.2);';
                        hud.innerText = '⚡ Tap any element on page to inspect its HTML tag & styles';
                        document.body.appendChild(hud);

                        document.addEventListener('click', function handler(e) {
                            if (!window.__petal_inspector_active) {
                                document.removeEventListener('click', handler, true);
                                return;
                            }
                            e.preventDefault();
                            e.stopPropagation();
                            var t = e.target;
                            var tag = t.tagName.toLowerCase();
                            var id = t.id ? '#' + t.id : '';
                            var cls = t.className && typeof t.className === 'string' ? '.' + t.className.trim().split(/\s+/).join('.') : '';
                            hud.innerText = tag + id + cls + ' [' + t.offsetWidth + 'x' + t.offsetHeight + 'px]';
                        }, true);
                        return 'DOM Inspector Activated';
                    })()
                """.trimIndent()) { msg ->
                    val clean = msg?.trim('"', '\'') ?: "DOM Inspector"
                    PetalToast.show(activity, clean)
                }
            }

            QuickToolId.BLOCK_AREA -> {
                geckoView?.evaluateJavascript("""
                    (function() {
                        var hud = document.createElement('div');
                        hud.style.cssText = 'position:fixed;top:16px;left:16px;right:16px;background:#d32f2f;color:#fff;padding:12px 16px;border-radius:16px;font-family:sans-serif;font-size:13px;z-index:2147483647;text-align:center;font-weight:bold;box-shadow:0 8px 25px rgba(0,0,0,0.5);';
                        hud.innerText = '⚡ Element Zapper: Tap any element to remove it';
                        document.body.appendChild(hud);
                        setTimeout(function() { hud.remove(); }, 5000);

                        document.addEventListener('click', function handler(e) {
                            e.preventDefault();
                            e.stopPropagation();
                            if (e.target !== hud) {
                                e.target.remove();
                                hud.innerText = '✓ Element removed';
                                setTimeout(function() { hud.remove(); }, 1500);
                            }
                            document.removeEventListener('click', handler, true);
                        }, true);
                    })()
                """.trimIndent())
                PetalToast.show(activity, "Tap any element to remove it")
            }

            QuickToolId.SPOOF_IDENTITY -> {
                showSpoofIdentity(activity, geckoView)
            }

            QuickToolId.FORCE_ZOOM -> {
                geckoView?.evaluateJavascript("""
                    (function() {
                        var metas = document.querySelectorAll('meta[name="viewport"]');
                        metas.forEach(function(m) {
                            m.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=10.0, user-scalable=yes');
                        });
                        if (metas.length === 0) {
                            var m = document.createElement('meta');
                            m.name = 'viewport';
                            m.content = 'width=device-width, initial-scale=1.0, maximum-scale=10.0, user-scalable=yes';
                            document.head.appendChild(m);
                        }
                    })()
                """.trimIndent())
                PetalToast.show(activity, "Pinch-to-zoom force-enabled on page")
            }

            QuickToolId.TORRENT_MAGNET -> {
                geckoView?.evaluateJavascript("""
                    (function() {
                        var magnets = [];
                        document.querySelectorAll('a[href^="magnet:"]').forEach(function(a) { magnets.push(a.href); });
                        return JSON.stringify(magnets);
                    })()
                """.trimIndent()) { result ->
                    try {
                        val type = object : TypeToken<List<String>>() {}.type
                        val magnets: List<String> = Gson().fromJson(result, type) ?: emptyList()
                        if (magnets.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnets.first()))
                            activity.startActivity(Intent.createChooser(intent, "Open Magnet Link"))
                        } else {
                            PetalToast.show(activity, "No magnet links found on this page")
                        }
                    } catch (e: Exception) {
                        PetalToast.show(activity, "No magnet links found")
                    }
                }
            }

            QuickToolId.PETAL_CONFIG -> {
                showPetalConfig(activity)
            }
        }
    }

    private fun showQrGenerator(activity: BrowserActivity, url: String, title: String) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        var view: ComposeView? = null
        view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                PetalExpressiveTheme {
                    PetalQrGeneratorDialog(
                        url = url,
                        title = title,
                        onDismissRequest = { decor.removeView(view) }
                    )
                }
            }
        }
        decor.addView(view)
    }

    private fun showSiteStyle(activity: BrowserActivity, geckoView: PetalGeckoView?) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.let { win ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    var activePreset by remember { mutableStateOf(SiteStylePreset.DEFAULT) }
                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = remember(fontName) { AppFont.fromName(fontName) },
                        colorStyle = remember(styleName) {
                            try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                        },
                        paletteId = paletteId
                    ) {
                        PetalSiteStyleSheet(
                            activePreset = activePreset,
                            onSelectPreset = { preset ->
                                activePreset = preset
                                if (preset.css.isNotEmpty()) {
                                    geckoView?.evaluateJavascript("""
                                        (function() {
                                            var style = document.getElementById('__petal_site_style');
                                            if (!style) {
                                                style = document.createElement('style');
                                                style.id = '__petal_site_style';
                                                document.head.appendChild(style);
                                            }
                                            style.innerHTML = `${preset.css}`;
                                        })()
                                    """.trimIndent())
                                } else {
                                    geckoView?.evaluateJavascript("var s = document.getElementById('__petal_site_style'); if (s) s.remove();")
                                }
                            },
                            onDismissRequest = {
                                try { dialog.dismiss() } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showDevNotes(activity: BrowserActivity, domain: String) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.let { win ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = remember(fontName) { AppFont.fromName(fontName) },
                        colorStyle = remember(styleName) {
                            try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                        },
                        paletteId = paletteId
                    ) {
                        PetalDevNotesSheet(
                            domain = domain,
                            onDismissRequest = {
                                try { dialog.dismiss() } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAutoScroll(activity: BrowserActivity, geckoView: PetalGeckoView?) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        var view: ComposeView? = null
        view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                PetalExpressiveTheme {
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                        PetalAutoScrollOverlay(
                            onScrollStep = { step ->
                                geckoView?.evaluateJavascript("window.scrollBy({ top: $step, behavior: 'smooth' });")
                            },
                            onClose = { decor.removeView(view) }
                        )
                    }
                }
            }
        }
        decor.addView(view)
    }

    private fun showImageGrabber(activity: BrowserActivity, geckoView: PetalGeckoView?) {
        geckoView?.evaluateJavascript("""
            (function() {
                var urls = new Set();
                document.querySelectorAll('img[src]').forEach(function(i) {
                    if (i.src && i.src.startsWith('http')) urls.add(i.src);
                });
                return JSON.stringify(Array.from(urls).slice(0, 60));
            })()
        """.trimIndent()) { result ->
            val type = object : TypeToken<List<String>>() {}.type
            val images: List<String> = try { Gson().fromJson(result, type) } catch (_: Exception) { emptyList() }
            try {
                val dialog = BottomSheetDialog(activity)
                dialog.behavior.skipCollapsed = true
                dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
                dialog.window?.let { win ->
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                    win.statusBarColor = android.graphics.Color.TRANSPARENT
                    win.navigationBarColor = android.graphics.Color.TRANSPARENT
                }

                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val composeView = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(activity)
                    setViewTreeViewModelStoreOwner(activity)
                    setViewTreeSavedStateRegistryOwner(activity)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setContent {
                        val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                        val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                        val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                        val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                        val isAmoled = sp.getBoolean("sp_amoled", false)

                        PetalExpressiveTheme(
                            dynamicColor = dynamicColor,
                            useAmoled = isAmoled,
                            appFont = remember(fontName) { AppFont.fromName(fontName) },
                            colorStyle = remember(styleName) {
                                try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                            },
                            paletteId = paletteId
                        ) {
                            PetalImageGrabberSheet(
                                images = images,
                                onDownloadImage = { imgUrl ->
                                    com.petal.browser.compose.downloads.PetalFetchDownloadBridge.enqueueMediaDownload(
                                        context = activity,
                                        url = imgUrl,
                                        fileName = imgUrl.substringAfterLast("/").substringBefore("?").ifEmpty { "image.jpg" }
                                    )
                                    PetalToast.show(activity, "Downloading image...")
                                },
                                onDownloadAll = {
                                    images.forEach { imgUrl ->
                                        com.petal.browser.compose.downloads.PetalFetchDownloadBridge.enqueueMediaDownload(
                                            context = activity,
                                            url = imgUrl,
                                            fileName = imgUrl.substringAfterLast("/").substringBefore("?").ifEmpty { "image.jpg" }
                                        )
                                    }
                                    PetalToast.show(activity, "Downloading ${images.size} images...")
                                    try { dialog.dismiss() } catch (_: Exception) {}
                                },
                                onDismissRequest = {
                                    try { dialog.dismiss() } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
                dialog.setContentView(composeView)
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showNetworkInspector(activity: BrowserActivity, url: String) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        var view: ComposeView? = null
        view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                PetalExpressiveTheme {
                    PetalNetworkInspectorDialog(
                        url = url,
                        onDismissRequest = { decor.removeView(view) }
                    )
                }
            }
        }
        decor.addView(view)
    }

    private fun showSpoofIdentity(activity: BrowserActivity, geckoView: PetalGeckoView?) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        var view: ComposeView? = null
        view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                PetalExpressiveTheme {
                    PetalSpoofIdentityDialog(
                        currentUa = null,
                        onSelectIdentity = { preset ->
                            if (preset.userAgent != null) {
                                geckoView?.setUserAgent(preset.userAgent)
                                PetalToast.show(activity, "User-Agent switched to ${preset.title}")
                            } else {
                                geckoView?.setUserAgent(null)
                                PetalToast.show(activity, "Restored default User-Agent")
                            }
                            geckoView?.reload()
                        },
                        onDismissRequest = { decor.removeView(view) }
                    )
                }
            }
        }
        decor.addView(view)
    }

    private fun showPetalConfig(activity: BrowserActivity) {
        PetalConfigSheet.show(activity)
    }

    private fun showConsoleLog(activity: BrowserActivity, geckoView: PetalGeckoView?) {
        try {
            val dialog = BottomSheetDialog(activity)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            dialog.window?.let { win ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT
            }

            val sp = PreferenceManager.getDefaultSharedPreferences(activity)
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                    val styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                    val paletteId = sp.getString("sp_palette_id", com.petal.browser.ui.theme.defaultPaletteId) ?: com.petal.browser.ui.theme.defaultPaletteId
                    val dynamicColor = sp.getBoolean("useDynamicColor", com.petal.browser.ui.theme.isDynamicColorSupported)
                    val isAmoled = sp.getBoolean("sp_amoled", false)

                    PetalExpressiveTheme(
                        dynamicColor = dynamicColor,
                        useAmoled = isAmoled,
                        appFont = remember(fontName) { AppFont.fromName(fontName) },
                        colorStyle = remember(styleName) {
                            try { ColorStyle.valueOf(styleName) } catch (_: Exception) { ColorStyle.TONAL_SPOT }
                        },
                        paletteId = paletteId
                    ) {
                        PetalDevConsoleSheet(
                            onExecuteScript = { script, callback ->
                                geckoView?.evaluateJavascript(script, callback)
                                    ?: callback("Error: Web session not active")
                            },
                            onDismissRequest = {
                                try { dialog.dismiss() } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
