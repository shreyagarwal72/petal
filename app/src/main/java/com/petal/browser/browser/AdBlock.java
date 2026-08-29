package com.petal.browser.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class AdBlock {
    private static final String FILE = "hosts.txt";
    private static final Set<String> hosts = new HashSet<>();
    @SuppressLint("ConstantLocale")
    private static final Locale locale = Locale.getDefault();

    private static final String[] AD_HOST_PATTERNS = new String[] {
        "doubleclick.net", "google-analytics.com", "googlesyndication.com",
        "adservice.google.com", "adnxs.com", "popads.net", "popcash.net",
        "adform.net", "taboola.com", "outbrain.com", "adroll.com", "criteo.com",
        "rubiconproject.com", "pubmatic.com", "smartadserver.com", "zedo.com",
        "amazon-adsystem.com", "adk2.com", "propellerads.com", "exoclick.com",
        "scorecardresearch.com", "quantserve.com", "openx.net", "monetag.com",
        "hilltopads.com", "adcash.com", "adsterra.com", "a-ads.com", "mgid.com",
        "revcontent.com", "juicyads.com", "trafficjunky.com", "coinhive.com",
        "statcounter.com", "hotjar.com", "mixpanel.com", "segment.io", "clarity.ms",
        "pixel.facebook.com", "adservice", "popunder", "popups", "tracking",
        // uBlock Origin Core Filters & Trackers
        "ublockorigin", "gorhill", "easylist", "easyprivacy", "adguard",
        "uBlock-filters", "uBlock-unbreak", "uBlock-badware", "uBlock-privacy",
        "uBlock-quick-fixes", "analytics", "telemetry", "tracking", "tracker"
    };

    private static final String[] AD_PATH_PATTERNS = new String[] {
        "/pagead/", "/adserv", "/ads/", "/ad_banner", "/popunder", "/popup.js",
        "adsterra", "popcash", "popads", "analytics.js", "gtag/js", "fbevents.js",
        "adsbygoogle.js", "ad_status"
    };

    public AdBlock(Context context) {
        if (hosts.isEmpty()) {
            new Thread(() -> {
                try {
                    File file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/" + FILE);
                    if (!file.exists()) {
                        AssetManager manager = context.getAssets();
                        copyFile(manager.open(FILE), Files.newOutputStream(file.toPath()));
                    }
                    loadHosts(context);
                } catch (Exception e) {
                    Log.e("AdBlock", "Error loading hosts asynchronously", e);
                }
            }).start();
        }
    }

    public static String getHostsDate(Context context) {
        File file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/" + FILE);
        String date = "";
        if (!file.exists()) {
            return "";
        }

        try {
            FileReader in = new FileReader(file);
            BufferedReader reader = new BufferedReader(in);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Date:")) {
                    date = "hosts.txt " + line.substring(2);
                    in.close();
                    break;
                }
            }
            in.close();

        } catch (IOException i) {
            Log.w("browser", "Error getting hosts date", i);
        }
        return date;
    }

    private static void loadHosts(final Context context) {
        Thread thread = new Thread(() -> {
            try {
                File file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/" + FILE);
                FileReader in = new FileReader(file);
                BufferedReader reader = new BufferedReader(in);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) continue;
                    hosts.add(line.toLowerCase(locale));
                }
                in.close();
            } catch (IOException i) {
                Log.w("browser", "Error loading adBlockHosts", i);
            }
        });
        thread.start();
    }

    public static void downloadHosts(final Context context) {
        Thread thread = new Thread(() -> {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String hostURL = sp.getString("ab_hosts", "https://raw.githubusercontent.com/gorhill/uBlock/master/assets/ublock/filters.txt");

            try {
                URL url = new URL(hostURL);
                Log.d("browser", "Download AdBlock hosts");
                URLConnection connection = url.openConnection();
                connection.setReadTimeout(5000);
                connection.setConnectTimeout(10000);

                InputStream is = connection.getInputStream();
                BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                File tempfile = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/temp.txt");

                if (tempfile.exists()) {
                    tempfile.delete();
                }
                tempfile.createNewFile();

                FileOutputStream outStream = new FileOutputStream(tempfile);
                byte[] buff = new byte[5 * 1024];

                int len;
                while ((len = inStream.read(buff)) != -1) {
                    outStream.write(buff, 0, len);
                }

                outStream.flush();
                outStream.close();
                inStream.close();

                FileReader in = new FileReader(tempfile);
                BufferedReader reader = new BufferedReader(in);
                File outfile = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/" + FILE);
                FileWriter out = new FileWriter(outfile);
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("0.0.0.0 ")) {
                        line = line.substring(8);
                    }
                    out.write(line + "\n");
                }
                in.close();
                out.close();

                tempfile.delete();

                hosts.clear();
                loadHosts(context);
                Log.w("browser", "AdBlock hosts updated");

            } catch (IOException i) {
                Log.w("browser", "Error updating AdBlock hosts", i);
            }
        });
        thread.start();
    }

    private static String getDomain(String url) throws URISyntaxException {
        url = url.toLowerCase(locale);

        int index = url.indexOf('/', 8);
        if (index != -1) {
            url = url.substring(0, index);
        }

        URI uri = new URI(url);
        String domain = uri.getHost();
        if (domain == null) {
            return url;
        }
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static String getAdHidingScript() {
        return "javascript:(function() {" +
            "if (window.__petal_adblock_injected__) return;" +
            "window.__petal_adblock_injected__ = true;" +
            "const selectors = ['.ad-container', '.ad-banner', '.ad-wrapper', '.ad-slot', '.ad-unit', '.ad-box', " +
            "'[id*=\"google_ads\"]', '[id*=\"taboola\"]', '[id*=\"outbrain\"]', '[class*=\"sponsored\"]', " +
            "'iframe[src*=\"ads\"]', 'iframe[src*=\"doubleclick\"]', 'iframe[src*=\"adnxs\"]', " +
            "'.popunder', '.popup-overlay', '.adsterra_tag', '[class*=\"adsterra\"]', '[id*=\"adsterra\"]', " +
            "'.top-ad', '.bottom-ad', '.sidebar-ad', '.header-ad', 'ins.adsbygoogle', '.native-ad'];" +
            "function hideAds() {" +
            "  try {" +
            "    const elements = document.querySelectorAll(selectors.join(', '));" +
            "    elements.forEach(el => {" +
            "      el.style.setProperty('display', 'none', 'important');" +
            "      el.style.setProperty('visibility', 'hidden', 'important');" +
            "      el.style.setProperty('height', '0px', 'important');" +
            "      el.style.setProperty('opacity', '0', 'important');" +
            "      el.style.setProperty('pointer-events', 'none', 'important');" +
            "    });" +
            "  } catch (e) {}" +
            "}" +
            "hideAds();" +
            "if (document.body) {" +
            "  const observer = new MutationObserver(() => hideAds());" +
            "  observer.observe(document.body, { childList: true, subtree: true });" +
            "} else {" +
            "  document.addEventListener('DOMContentLoaded', () => {" +
            "    hideAds();" +
            "    if (document.body) {" +
            "      const observer = new MutationObserver(() => hideAds());" +
            "      observer.observe(document.body, { childList: true, subtree: true });" +
            "    }" +
            "  });" +
            "}" +
            "try {" +
            "  const origOpen = window.open;" +
            "  window.open = function(url, target, features) {" +
            "    if (!url || url === 'about:blank' || /ad|pop|banner|track|redirect/i.test(url)) return null;" +
            "    return origOpen.apply(this, arguments);" +
            "  };" +
            "} catch (e) {}" +
            "})()";
    }

    public boolean isAd(String url) {
        if (url == null || url.length() < 8) return false;
        String lowerUrl = url.toLowerCase(locale);

        String domain;
        try {
            domain = getDomain(url);
        } catch (URISyntaxException u) {
            return false;
        }

        String lowerDomain = domain.toLowerCase(locale);
        if (hosts.contains(lowerDomain)) return true;

        int dot = lowerDomain.indexOf('.');
        if (dot != -1 && dot < lowerDomain.length() - 1) {
            String parentDomain = lowerDomain.substring(dot + 1);
            if (hosts.contains(parentDomain)) return true;
        }

        for (String pattern : AD_HOST_PATTERNS) {
            if (lowerUrl.contains(pattern)) return true;
        }

        for (String pathPattern : AD_PATH_PATTERNS) {
            if (lowerUrl.contains(pathPattern)) return true;
        }

        return false;
    }
}
