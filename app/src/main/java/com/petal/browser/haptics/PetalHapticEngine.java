package com.petal.browser.haptics;

import android.content.Context;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.NonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Petal Haptic Engine
 * Adapted from Ever-Haptics for high-precision tactile feedback on Android.
 */
public class PetalHapticEngine {

    public enum Pattern {
        CLICK,
        TICK,
        HEAVY_CLICK,
        DOUBLE_CLICK,
        SOFT_BUMP,
        DOUBLE_TICK
    }

    private static final int INTENSITY_BUCKETS = 16;
    private static final float MIN_AUDIBLE_INTENSITY = 0.02f;

    private static PetalHapticEngine sInstance;

    private final Vibrator vibrator;
    private final boolean hasVibrator;
    private final boolean hasAmplitudeControl;
    private final VibrationAttributes touchAttrs;

    private final ConcurrentHashMap<Integer, VibrationEffect> effectCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Pattern, AtomicLong> lastPlayMs = new ConcurrentHashMap<>();

    private PetalHapticEngine(Context context) {
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager != null ? manager.getDefaultVibrator() : (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        }

        hasVibrator = vibrator != null && vibrator.hasVibrator();
        hasAmplitudeControl = hasVibrator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            touchAttrs = new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();
        } else {
            touchAttrs = null;
        }
    }

    public static synchronized PetalHapticEngine getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PetalHapticEngine(context);
        }
        return sInstance;
    }

    public boolean play(@NonNull Pattern pattern, float intensity, long throttleMs) {
        if (!hasVibrator) return false;
        if (intensity <= MIN_AUDIBLE_INTENSITY) return false;

        if (throttleMs > 0L) {
            long now = System.currentTimeMillis();
            AtomicLong lastMs = lastPlayMs.computeIfAbsent(pattern, k -> new AtomicLong(0L));
            long prev = lastMs.get();
            if (now - prev < throttleMs) return false;
            lastMs.compareAndSet(prev, now);
        }

        float clamped = Math.max(0f, Math.min(1f, intensity));
        VibrationEffect effect = effectFor(pattern, clamped);
        if (effect != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && touchAttrs != null) {
                    vibrator.vibrate(effect, touchAttrs);
                    return true;
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(effect);
                    return true;
                } else {
                    vibrator.vibrate(40);
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // Multi-tiered fallback for devices lacking hardware primitive composition
        try {
            long durationMs = (pattern == Pattern.DOUBLE_CLICK || pattern == Pattern.HEAVY_CLICK) ? 45L : 25L;
            int amplitude = Math.max(1, Math.min(255, (int) (clamped * 255f)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect fallbackEffect = VibrationEffect.createOneShot(durationMs, amplitude);
                vibrator.vibrate(fallbackEffect);
            } else {
                vibrator.vibrate(durationMs);
            }
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean play(@NonNull Pattern pattern, float intensity) {
        return play(pattern, intensity, 0L);
    }

    public boolean hasVibrator() {
        return hasVibrator;
    }

    public boolean hasAmplitudeControl() {
        return hasAmplitudeControl;
    }

    public static boolean isHapticsEnabled(Context context) {
        if (context == null) return true;
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("sp_touch_haptics", true);
    }

    public boolean playIfEnabled(Context context, @NonNull Pattern pattern, float intensity, long throttleMs) {
        if (!isHapticsEnabled(context)) return false;
        return play(pattern, intensity, throttleMs);
    }

    public boolean playIfEnabled(Context context, @NonNull Pattern pattern, float intensity) {
        return playIfEnabled(context, pattern, intensity, 0L);
    }

    public void playOneShot(long durationMs, int amplitude) {
        if (!hasVibrator) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int amp = Math.max(1, Math.min(255, amplitude));
                VibrationEffect effect = VibrationEffect.createOneShot(durationMs, amp);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && touchAttrs != null) {
                    vibrator.vibrate(effect, touchAttrs);
                } else {
                    vibrator.vibrate(effect);
                }
            } else {
                vibrator.vibrate(durationMs);
            }
        } catch (Throwable ignored) {}
    }

    public void playStrong(@NonNull Pattern pattern) {
        play(pattern, 1.0f, 0L);
    }

    public void playClick(Context context) {
        playIfEnabled(context, Pattern.CLICK, 0.75f, 0L);
    }

    public void playTick(Context context) {
        playIfEnabled(context, Pattern.TICK, 0.5f, 0L);
    }

    private VibrationEffect effectFor(Pattern pattern, float intensity) {
        int bucket = Math.max(0, Math.min(INTENSITY_BUCKETS - 1, (int) ((intensity * (INTENSITY_BUCKETS - 1)) + 0.5f)));
        int key = pattern.ordinal() * INTENSITY_BUCKETS + bucket;

        VibrationEffect cached = effectCache.get(key);
        if (cached != null) return cached;

        float bucketIntensity = (float) bucket / (INTENSITY_BUCKETS - 1);
        VibrationEffect built = buildEffect(pattern, bucketIntensity);
        if (built != null) {
            effectCache.putIfAbsent(key, built);
        }
        return built;
    }

    private VibrationEffect buildEffect(Pattern pattern, float intensity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null) {
                int primitive = getPrimitiveForPattern(pattern);
                if (vibrator.areAllPrimitivesSupported(primitive)) {
                    VibrationEffect.Composition composition = VibrationEffect.startComposition();
                    switch (pattern) {
                        case CLICK:
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity);
                            break;
                        case TICK:
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity);
                            break;
                        case HEAVY_CLICK:
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity);
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, Math.max(0f, Math.min(1f, intensity * 0.6f)), 40);
                            break;
                        case DOUBLE_CLICK:
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity);
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, intensity, 80);
                            break;
                        case SOFT_BUMP:
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, intensity);
                            break;
                        case DOUBLE_TICK:
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity);
                            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, intensity, 60);
                            break;
                    }
                    return composition.compose();
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int effectId;
                switch (pattern) {
                    case HEAVY_CLICK:
                        effectId = VibrationEffect.EFFECT_HEAVY_CLICK;
                        break;
                    case DOUBLE_CLICK:
                    case DOUBLE_TICK:
                        effectId = VibrationEffect.EFFECT_DOUBLE_CLICK;
                        break;
                    case TICK:
                    case SOFT_BUMP:
                        effectId = VibrationEffect.EFFECT_TICK;
                        break;
                    case CLICK:
                    default:
                        effectId = VibrationEffect.EFFECT_CLICK;
                        break;
                }
                return VibrationEffect.createPredefined(effectId);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int amplitude = (int) (intensity * 255);
                return VibrationEffect.createOneShot(30, Math.max(1, amplitude));
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private int getPrimitiveForPattern(Pattern pattern) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            switch (pattern) {
                case TICK:
                    return VibrationEffect.Composition.PRIMITIVE_TICK;
                case SOFT_BUMP:
                    return VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
                case CLICK:
                case HEAVY_CLICK:
                case DOUBLE_CLICK:
                case DOUBLE_TICK:
                default:
                    return VibrationEffect.Composition.PRIMITIVE_CLICK;
            }
        }
        return 0;
    }
}
