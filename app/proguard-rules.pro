# Petal release shrinking (R8).
#
# Libraries with their own consumer rules (GeckoView, Gson, OkHttp, PDFBox, Hilt, AndroidX,
# Compose, Play services) need nothing here. What follows covers the code that is reached by
# name at runtime: Gson models, JavaScript bridges, reflection into Mozilla Components and
# GeckoView, and yt-dlp's JSON mapping.

# Keep line numbers so release crash traces stay readable (with build/outputs/mapping).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures, inner classes and annotations: Gson's TypeToken and Kotlin metadata.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# ---- Petal ----------------------------------------------------------------------------------
# Gson (menu items, tab groups, collections, credentials, inactive tabs, quick tools...)
# reads and writes fields by name, @JavascriptInterface methods are called by name from page
# scripts, and enums are restored from stored names. Classes nothing uses can still go.
-keep,allowshrinking class com.petal.browser.** { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- Mozilla Android Components -------------------------------------------------------------
# Ships no consumer rules. Petal reads GeckoEngineSession's geckoSession by reflection
# (PetalGeckoView.extractGeckoSession), so nothing here may be renamed.
-keep,allowshrinking class mozilla.components.** { *; }

# ---- GeckoView ------------------------------------------------------------------------------
# Native code calls into these by name; ContextElement.textContent is read by reflection.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }

# GeckoView's DebugConfig.fromFile parses YAML with SnakeYAML on every start. R8 moves the
# renamed SnakeYAML classes into the root package, where Class.getPackage() is null on
# Android, so its static initialiser throws NullPointerException (Package.getName) and the
# app dies in PetalApplication.onCreate. Keep SnakeYAML and its package names intact.
-keep class org.yaml.snakeyaml.** { *; }
-keeppackagenames org.yaml.snakeyaml.**
-dontwarn org.yaml.snakeyaml.**
-dontwarn java.beans.**

# ---- yt-dlp (youtubedl-android) -------------------------------------------------------------
# Maps yt-dlp's JSON output onto its model classes with Jackson.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**

# ---- Media3 ---------------------------------------------------------------------------------
# The animated wallpaper switches PlayerView's surface type by reflection.
-keep class androidx.media3.ui.PlayerView {
    *** setVideoSurfaceViewType(int);
    *** surfaceType;
}

# ---- Fetch2 (downloads) ---------------------------------------------------------------------
-keep class com.tonyodev.fetch2.** { *; }
-keep class com.tonyodev.fetch2core.** { *; }
-keep class com.tonyodev.fetch2okhttp.** { *; }

# ---- PDFBox ---------------------------------------------------------------------------------
# JPEG 2000 support is an optional dependency that is not bundled.
-dontwarn com.gemalto.jp2.**

# ---- Shizuku --------------------------------------------------------------------------------
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# ---- Google AdMob --------------------------------------------------------------------------
-keep public class com.google.android.gms.ads.** {
    public *;
}
-keep public class com.google.ads.** {
    public *;
}
-dontwarn com.google.android.gms.ads.**

# Keep WorkManager components instantiated via reflection (WorkDatabase and workers used by Unity Ads)
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class * extends androidx.work.InputMerger { <init>(); }
-dontwarn androidx.work.impl.**

# ---- Mozilla Telemetry / Nimbus -------------------------------------------------------------
-dontwarn mozilla.telemetry.glean.**
-dontwarn org.mozilla.experiments.nimbus.**
-dontwarn mozilla.components.service.nimbus.**

# ---- youtubedl-android, aria2c, ffmpeg & Python JNI -------------------------
-keep class com.yausername.** { *; }
-keepclassmembers class com.yausername.** { *; }
-keep interface com.yausername.** { *; }
-dontwarn com.yausername.**

-keep class com.chaquo.python.** { *; }
-keepclassmembers class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**



