package com.petal.browser.unit;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PasswordBreachAuditManager checks passwords safely against HaveIBeenPwned API using k-anonymity SHA-1 prefixes.
 * Plain passwords are NEVER sent over the network.
 */
public class PasswordBreachAuditManager {

    private static final String TAG = "PasswordBreachAudit";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface BreachCallback {
        void onCheckCompleted(boolean isPwned, int breachCount);
    }

    /**
     * Checks if password SHA-1 hash exists in HIBP breach database using 5-character k-Anonymity prefix.
     * Endpoint: https://api.pwnedpasswords.com/range/{prefix}
     */
    public static void checkPassword(final String plainPassword, final BreachCallback callback) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            if (callback != null) callback.onCheckCompleted(false, 0);
            return;
        }

        executor.execute(() -> {
            boolean isPwned = false;
            int breachCount = 0;
            HttpURLConnection connection = null;

            try {
                // 1. Hash password with SHA-1
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] bytes = md.digest(plainPassword.getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) {
                    sb.append(String.format("%02X", b));
                }
                String sha1Hash = sb.toString().toUpperCase();

                // 2. Split into 5-char prefix and 35-char suffix
                String prefix = sha1Hash.substring(0, 5);
                String suffix = sha1Hash.substring(5);

                // 3. Query HIBP range API with prefix ONLY
                URL url = new URL("https://api.pwnedpasswords.com/range/" + prefix);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(":");
                        if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(suffix)) {
                            isPwned = true;
                            breachCount = Integer.parseInt(parts[1].trim());
                            break;
                        }
                    }
                    reader.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "HIBP password breach audit failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }

            final boolean finalIsPwned = isPwned;
            final int finalBreachCount = breachCount;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onCheckCompleted(finalIsPwned, finalBreachCount)
                );
            }
        });
    }
}
