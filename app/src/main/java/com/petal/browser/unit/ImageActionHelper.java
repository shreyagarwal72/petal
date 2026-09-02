package com.petal.browser.unit;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.webkit.URLUtil;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.petal.browser.view.NinjaToast;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class ImageActionHelper {

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build();

    public static void downloadImage(Context context, String imageUrl) {
        if (context == null || imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }

        try {
            String fileName = URLUtil.guessFileName(imageUrl, null, "image/jpeg");
            if (!fileName.contains(".")) {
                fileName += ".jpg";
            }

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imageUrl));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                request.allowScanningByMediaScanner();
                downloadManager.enqueue(request);
                NinjaToast.show(context, "Image download started");
            } else {
                // Handle Base64 or local URIs directly
                downloadImageDirectly(context, imageUrl, fileName);
            }
        } catch (Exception e) {
            NinjaToast.show(context, "Failed to download image: " + e.getLocalizedMessage());
        }
    }

    private static void downloadImageDirectly(Context context, String imageUrl, String fileName) {
        new Thread(() -> {
            try {
                byte[] bytes = getImageBytes(context, imageUrl);
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) downloadsDir.mkdirs();
                File destFile = new File(downloadsDir, fileName);
                FileOutputStream fos = new FileOutputStream(destFile);
                fos.write(bytes);
                fos.flush();
                fos.close();

                // Scan file to MediaStore
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(destFile));
                context.sendBroadcast(intent);

                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> NinjaToast.show(context, "Image saved to Downloads"));
                }
            } catch (Exception e) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> NinjaToast.show(context, "Failed to save image"));
                }
            }
        }).start();
    }

    public static void shareImage(Context context, String imageUrl) {
        if (context == null || imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }

        NinjaToast.show(context, "Preparing image for sharing...");

        new Thread(() -> {
            try {
                byte[] bytes = getImageBytes(context, imageUrl);
                File cacheDir = new File(context.getCacheDir(), "shared_images");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File shareFile = new File(cacheDir, "shared_image_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fos = new FileOutputStream(shareFile);
                fos.write(bytes);
                fos.flush();
                fos.close();

                Uri contentUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    shareFile
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(shareIntent, "Share Image");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            } catch (Exception e) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> NinjaToast.show(context, "Failed to share image"));
                }
            }
        }).start();
    }

    public static byte[] getImageBytes(Context context, String imageUrl) throws Exception {
        if (imageUrl.startsWith("data:image")) {
            String base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1);
            return android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
        } else if (imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
            Uri uri = Uri.parse(imageUrl);
            InputStream is = context.getContentResolver().openInputStream(uri);
            byte[] bytes = readAllBytesCompat(is);
            is.close();
            return bytes;
        } else {
            Request request = new Request.Builder().url(imageUrl).build();
            okhttp3.Response response = okHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) throw new RuntimeException("HTTP " + response.code());
            byte[] bytes = response.body().bytes();
            response.close();
            return bytes;
        }
    }

    private static byte[] readAllBytesCompat(InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
