package com.petal.browser.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.petal.browser.R;
import com.petal.browser.unit.HelperUnit;

public class WebAppInterface {
    private final Context mContext;
    public WebAppInterface(Context context) {
        this.mContext = context;
    }
    @JavascriptInterface
    public void processBlob(String base64Data, String mimeType, String suggestedFileName) {
        try {
            // "data:application/pdf;base64," Prefix herausschneiden, falls vorhanden
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }
            // Base64 zu Bytes dekodieren
            byte[] fileBytes = Base64.decode(base64Data, Base64.DEFAULT);

            String finalFileName = dedupeFileName(resolveFinalFileName(suggestedFileName, fileBytes, mimeType));

            // Show the same AlertDialog-based confirmation used for regular http(s) downloads
            // (instead of the old pre-fetch Snackbar), now with the real filename/size known.
            com.petal.browser.ui.components.PetalDownloadDialogBridge.showBlobDownloadConfirmation(
                    mContext,
                    finalFileName,
                    fileBytes.length,
                    () -> {
                        writeBlobToDownloads(finalFileName, fileBytes);
                        return kotlin.Unit.INSTANCE;
                    },
                    () -> kotlin.Unit.INSTANCE
            );
        } catch (IllegalArgumentException e) {
            Toast.makeText(mContext, mContext.getString(R.string.app_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void writeBlobToDownloads(String fileName, byte[] fileBytes) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();
            File file = new File(downloadDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(fileBytes);
                fos.flush();
            }
            showSnackbar();
        } catch (IOException e) {
            Toast.makeText(mContext, mContext.getString(R.string.app_error), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Figures out a sensible filename for a blob-fetched download.
     * Prefers the page's own suggested name (the clicked &lt;a download="..."&gt; value) since
     * that's the only place a name like "Main.kt" actually exists - blob: URLs themselves carry
     * no filename, and the mimeType Android reports for them is frequently empty/generic. Only
     * falls back to guessing an extension from the bytes/mimeType, and never defaults straight
     * to ".bin" for content that is clearly text (source files, JSON, CSV, etc.).
     */
    private String resolveFinalFileName(String suggestedFileName, byte[] bytes, String mimeType) {
        String sanitized = sanitizeFileName(suggestedFileName);
        if (sanitized != null && sanitized.contains(".")) {
            return sanitized;
        }
        String base = (sanitized != null && !sanitized.isEmpty())
                ? sanitized
                : ("download_" + System.currentTimeMillis());
        String ext = extensionFromBytes(bytes);
        if (ext == null) {
            ext = extensionFromMimeType(mimeType);
        }
        if (ext == null) {
            ext = looksLikeText(bytes) ? "txt" : "bin";
        }
        return base + "." + ext;
    }

    private String sanitizeFileName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        trimmed = trimmed.replace("\\", "/");
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash >= 0) trimmed = trimmed.substring(lastSlash + 1);
        trimmed = trimmed.replace("..", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extensionFromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) return null;
        String stripped = mimeType.split(";")[0].trim();
        String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(stripped);
        return (ext != null && !ext.isEmpty()) ? ext : null;
    }

    private String extensionFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return null;
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
        return null;
    }

    /** Simple heuristic: mostly-printable bytes with no NUL bytes reads as text (source code, JSON, CSV, ...). */
    private boolean looksLikeText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        int sampleLen = Math.min(bytes.length, 512);
        int printable = 0;
        for (int i = 0; i < sampleLen; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0) return false;
            if (b == 9 || b == 10 || b == 13 || (b >= 32 && b < 127) || b >= 128) {
                printable++;
            }
        }
        return printable >= sampleLen * 0.95;
    }

    /** Avoids silently overwriting an existing file of the same name, matching the http(s) download engine's behavior. */
    private String dedupeFileName(String fileName) {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File target = new File(downloadDir, fileName);
        if (!target.exists()) return fileName;
        String name = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            name = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        int counter = 1;
        File candidate = target;
        while (candidate.exists()) {
            candidate = new File(downloadDir, name + "(" + counter + ")" + extension);
            counter++;
        }
        return candidate.getName();
    }
    // Hilfsmethode für die Snackbar im UI-Thread
    private void showSnackbar() {
        if (mContext instanceof Activity) {
            Activity activity = (Activity) mContext;
            activity.runOnUiThread(() -> {
                View rootView = activity.findViewById(android.R.id.content);
                if (rootView != null) {
                    String text = mContext.getString(R.string.app_done) + ". " + mContext.getString(R.string.menu_download) +"?";
                    Snackbar snackbar = Snackbar.make(rootView, text, Snackbar.LENGTH_SHORT);
                    snackbar.setAction(mContext.getString(R.string.app_ok), v -> {
                        if (mContext instanceof com.petal.browser.activity.BrowserActivity) {
                            ((com.petal.browser.activity.BrowserActivity) mContext).showDownloads();
                        } else {
                            try {
                                mContext.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception ignored) {}
                        }
                    });
                    snackbar.show();
                }
            });
        }
    }
}

