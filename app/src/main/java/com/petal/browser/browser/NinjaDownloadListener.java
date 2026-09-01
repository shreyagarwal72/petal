package com.petal.browser.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebView;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

import com.petal.browser.R;
import com.petal.browser.unit.BrowserUnit;
import com.petal.browser.unit.HelperUnit;
import com.petal.browser.view.NinjaToast;

public class NinjaDownloadListener implements DownloadListener {
    private final Context context;
    private final WebView webView;
    public NinjaDownloadListener(Context context, WebView webView) {
        super();
        this.context = context;
        this.webView = webView;
    }
    private String getExtension(String mimeType) {
        if (mimeType == null) return "bin";
        if (mimeType.contains("pdf")) return "pdf";
        if (mimeType.contains("image/png")) return "png";
        if (mimeType.contains("image/jpeg")) return "jpg";
        if (mimeType.contains("zip")) return "zip";
        return "bin";
    }
    private String getExtensionFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return "bin";
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            hex.append(String.format("%02X", bytes[i]));
        }
        String magic = hex.toString();
        if (magic.startsWith("25504446")) return "pdf";
        if (magic.startsWith("89504E47")) return "png";
        if (magic.startsWith("FFD8FF"))   return "jpg";
        if (magic.startsWith("47494638")) return "gif";
        if (magic.startsWith("504B0304")) return "zip";
        return "bin";
    }

    private static String lastHandledUrl = null;
    private static long lastHandledTime = 0L;

    @Override
    public void onDownloadStart(final String url, String userAgent, final String contentDisposition, final String mimeType, long contentLength) {
        final String downloadUrl = (url != null) ? url : "";
        long currentTime = System.currentTimeMillis();
        if (downloadUrl.equals(lastHandledUrl) && (currentTime - lastHandledTime) < 1500L) {
            return;
        }
        lastHandledUrl = downloadUrl;
        lastHandledTime = currentTime;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String generatedFileName = HelperUnit.domain(webView.getUrl()) + "_" + timestamp + "." + getExtension(mimeType);

        if (downloadUrl.startsWith("blob:")) {
            // Previously this showed a Snackbar confirmation *before* fetching the blob, using
            // a filename generated purely from the (often empty/generic) mimeType Android hands
            // us for blob: URLs - that path always fell back to "bin" for anything that wasn't
            // pdf/png/jpg/zip, so e.g. a "Download" button that hands the WebView a .kt (or any
            // other source/text) file always showed a different-looking Snackbar prompt, and
            // then saved as a misleadingly-named ".bin" file - easy to mistake for "it didn't
            // download". Now we silently fetch+encode the blob first (cheap, same-origin, no
            // network round trip) so we can recover the real suggested filename from the
            // triggering <a download="..."> element and the blob's actual MIME type, and only
            // then show the SAME AlertDialog-based confirmation used for normal downloads
            // (see WebAppInterface#processBlob), with the correct name/extension and file size.
            String jsCode = "javascript: (function() {" +
                    "   var xhr = new XMLHttpRequest();" +
                    "   xhr.open('GET', '" + downloadUrl + "', true);" +
                    "   xhr.responseType = 'blob';" +
                    "   xhr.onload = function() {" +
                    "       if (xhr.status === 200) {" +
                    "           var blob = xhr.response;" +
                    "           var reader = new FileReader();" +
                    "           reader.onloadend = function() {" +
                    "               var base64data = reader.result;" +
                    "               var suggestedName = '';" +
                    "               try {" +
                    "                   var anchors = document.querySelectorAll('a[href=\"' + '" + downloadUrl + "' + '\"]');" +
                    "                   for (var i = 0; i < anchors.length; i++) {" +
                    "                       if (anchors[i].download) { suggestedName = anchors[i].download; break; }" +
                    "                   }" +
                    "               } catch (e) {}" +
                    "               var resolvedMime = blob.type || '" + mimeType + "';" +
                    "               AndroidInterface.processBlob(base64data, resolvedMime, suggestedName);" +
                    "           };" +
                    "           reader.readAsDataURL(blob);" +
                    "       }" +
                    "   };" +
                    "   xhr.send();" +
                    "})();";
            webView.evaluateJavascript(jsCode, null);
        } else if (downloadUrl.startsWith("data:")) {

            int commaIndex = downloadUrl.indexOf(",");
            if (commaIndex == -1) throw new IllegalArgumentException("Ungültige Data-URL");
            String base64Data = downloadUrl.substring(commaIndex + 1);
            byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
            String realExtension = getExtensionFromBytes(decodedBytes);
            String finalFileName = generatedFileName;
            if (finalFileName.contains(".")) {
                finalFileName = finalFileName.substring(0, finalFileName.lastIndexOf(".")) + "." + realExtension;
            } else {
                finalFileName = finalFileName + "." + realExtension;
            }

            String d = webView.getContext().getString(R.string.dialog_title_download) + " - " + finalFileName;
            HelperUnit.showCustomSnackbarWithTwoActions(
                    webView.getContext(), webView, null,
                    webView.getTitle(), d, downloadUrl,
                    R.drawable.icon_check, () -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            try {
                                String finalFileName2 = generatedFileName;
                                if (finalFileName2.contains(".")) {
                                    finalFileName2 = finalFileName2.substring(0, finalFileName2.lastIndexOf(".")) + "." + realExtension;
                                } else {
                                    finalFileName2 = finalFileName2 + "." + realExtension;
                                }
                                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                                if (!downloadDir.exists()) downloadDir.mkdirs();
                                File file = new File(downloadDir, finalFileName2);
                                try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(file.toPath()))) {
                                    bos.write(decodedBytes);
                                    bos.flush();
                                }
                                MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
                                try {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                                        if (dm != null) {
                                            dm.addCompletedDownload(file.getName(), file.getName(), true, mimeType != null ? mimeType : "*/*", file.getAbsolutePath(), file.length(), true);
                                        }
                                    }
                                } catch (Throwable ignored) {}
                                webView.post(() -> {
                                    String text = webView.getContext().getString(R.string.app_done) + ". " + webView.getContext().getString(R.string.menu_download) + "?";
                                    Snackbar snackbar = Snackbar.make(webView, text, Snackbar.LENGTH_SHORT);
                                    HelperUnit.makeSnackbarRound(snackbar);
                                    snackbar.setAction(context.getString(R.string.app_ok), (v -> {
                                         if (context instanceof com.petal.browser.activity.BrowserActivity) {
                                             ((com.petal.browser.activity.BrowserActivity) context).showDownloads();
                                         } else {
                                             try {
                                                 context.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                             } catch (Exception ignored) {}
                                         }
                                     }));
                                    snackbar.show();
                                });
                            } catch (Exception e) {
                                webView.post(() -> {
                                    String textToShow = context.getString(R.string.app_error) + ": " + e.getMessage();
                                    NinjaToast.show(webView.getContext(), textToShow);
                                });
                            }
                        });
                        return true;
                    },
                    R.drawable.icon_close, () -> true
            );
        } else {
            if (context instanceof android.app.Activity && com.petal.browser.torrent.PetalTorrentEngineManager.handleTorrentOrMagnet((android.app.Activity) context, downloadUrl, null, mimeType)) {
                return;
            }
            com.petal.browser.ui.components.PetalDownloadDialogBridge.showDownloadConfirmation(
                context,
                downloadUrl,
                contentDisposition,
                mimeType,
                contentLength,
                confirmedFileName -> {
                    BrowserUnit.download(context, downloadUrl, confirmedFileName, mimeType);
                    return kotlin.Unit.INSTANCE;
                }
            );
        }
    }
}